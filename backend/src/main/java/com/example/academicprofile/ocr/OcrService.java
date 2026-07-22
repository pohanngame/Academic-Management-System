package com.example.academicprofile.ocr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.achievement.Certificate;
import com.example.academicprofile.achievement.CertificateMapper;
import com.example.academicprofile.achievement.Paper;
import com.example.academicprofile.achievement.PaperMapper;
import com.example.academicprofile.achievement.Patent;
import com.example.academicprofile.achievement.PatentMapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileBusinessType;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OcrService {

    private static final Set<String> OCR_FILE_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp");
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String RESULT_DRAFT = "DRAFT";
    private static final String RESULT_IGNORED = "IGNORED";
    private static final String RESULT_CONFIRMED = "CONFIRMED";

    private final OcrTaskMapper taskMapper;
    private final OcrResultMapper resultMapper;
    private final FileStorageService fileStorageService;
    private final OcrProperties properties;
    private final PaddleOcrClient paddleOcrClient;
    private final OcrCandidateExtractor candidateExtractor;
    private final PaperMapper paperMapper;
    private final PatentMapper patentMapper;
    private final CertificateMapper certificateMapper;
    private final ObjectMapper objectMapper;

    public OcrService(
            OcrTaskMapper taskMapper,
            OcrResultMapper resultMapper,
            FileStorageService fileStorageService,
            OcrProperties properties,
            PaddleOcrClient paddleOcrClient,
            OcrCandidateExtractor candidateExtractor,
            PaperMapper paperMapper,
            PatentMapper patentMapper,
            CertificateMapper certificateMapper,
            ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.fileStorageService = fileStorageService;
        this.properties = properties;
        this.paddleOcrClient = paddleOcrClient;
        this.candidateExtractor = candidateExtractor;
        this.paperMapper = paperMapper;
        this.patentMapper = patentMapper;
        this.certificateMapper = certificateMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OcrTaskResponse createTask(Long teacherId, OcrTaskRequest request) {
        FileMetadata metadata = fileStorageService.getOwnedFileMetadata(teacherId, request.fileId());
        OcrTargetType targetType = request.targetType();
        validateFileForTarget(metadata, targetType);

        OcrTask task = new OcrTask();
        task.setTeacherId(teacherId);
        task.setFileId(metadata.getId());
        task.setTargetType(targetType.name());
        task.setStatus(STATUS_PENDING);
        task.setResultCount(0);
        taskMapper.insert(task);

        runRecognition(task, metadata, targetType);
        return getTask(teacherId, task.getId());
    }

    public List<OcrTaskResponse> listTasks(Long teacherId) {
        return taskMapper.selectList(new LambdaQueryWrapper<OcrTask>()
                        .eq(OcrTask::getTeacherId, teacherId)
                        .orderByDesc(OcrTask::getId))
                .stream()
                .map(OcrTaskResponse::from)
                .toList();
    }

    public OcrTaskResponse getTask(Long teacherId, Long taskId) {
        OcrTask task = getOwnedTask(teacherId, taskId);
        List<OcrResultResponse> results = listResults(teacherId, taskId).stream()
                .map(OcrResultResponse::from)
                .toList();
        return OcrTaskResponse.from(task, results);
    }

    @Transactional
    public OcrResultResponse updateResult(Long teacherId, Long resultId, OcrResultRequest request) {
        OcrResult result = getOwnedResult(teacherId, resultId);
        if (RESULT_CONFIRMED.equals(result.getStatus())) {
            throw new BusinessException("Confirmed OCR result cannot be edited");
        }
        applyRequest(result, request);
        if (RESULT_IGNORED.equals(result.getStatus())) {
            result.setStatus(RESULT_DRAFT);
        }
        result.setResultJson(toResultJson(result));
        resultMapper.updateById(result);
        return OcrResultResponse.from(result);
    }

    @Transactional
    public void ignoreResult(Long teacherId, Long resultId) {
        OcrResult result = getOwnedResult(teacherId, resultId);
        if (RESULT_CONFIRMED.equals(result.getStatus())) {
            throw new BusinessException("Confirmed OCR result cannot be ignored");
        }
        result.setStatus(RESULT_IGNORED);
        resultMapper.updateById(result);
        refreshTaskResultCount(result.getTaskId(), teacherId);
    }

    @Transactional
    public OcrConfirmResponse confirmTask(Long teacherId, Long taskId, OcrConfirmRequest request) {
        OcrTask task = getOwnedTask(teacherId, taskId);
        Set<Long> selectedIds = request.resultIds() == null ? Set.of() : new HashSet<>(request.resultIds());
        List<OcrResult> results = listResults(teacherId, taskId).stream()
                .filter(result -> selectedIds.isEmpty() || selectedIds.contains(result.getId()))
                .filter(result -> RESULT_DRAFT.equals(result.getStatus()))
                .toList();

        int confirmed = 0;
        int skipped = 0;
        for (OcrResult result : results) {
            Long createdId = createBusinessRecord(teacherId, result);
            if (createdId == null) {
                skipped++;
                continue;
            }
            result.setCreatedRecordId(createdId);
            result.setStatus(RESULT_CONFIRMED);
            resultMapper.updateById(result);
            confirmed++;
        }
        task.setStatus(confirmed > 0 ? STATUS_CONFIRMED : task.getStatus());
        taskMapper.updateById(task);
        refreshTaskResultCount(taskId, teacherId);
        return new OcrConfirmResponse(confirmed, skipped);
    }

    private void runRecognition(OcrTask task, FileMetadata metadata, OcrTargetType targetType) {
        task.setStatus(STATUS_RUNNING);
        taskMapper.updateById(task);
        try {
            Path filePath = fileStorageService.resolveOwnedFilePath(task.getTeacherId(), metadata.getId());
            RecognizedText recognizedText = recognize(metadata, filePath);
            task.setRecognitionMode(recognizedText.mode());
            task.setRawText(limit(recognizedText.text(), 200000));
            OcrResult result = candidateExtractor.extract(task.getId(), task.getTeacherId(), targetType, recognizedText.text());
            result.setResultJson(toResultJson(result));
            resultMapper.insert(result);
            task.setStatus(STATUS_SUCCEEDED);
            task.setErrorMessage(null);
            task.setResultCount(1);
            taskMapper.updateById(task);
        } catch (BusinessException ex) {
            markTaskFailed(task, ex.getMessage());
        } catch (RuntimeException ex) {
            markTaskFailed(task, "OCR failed: " + ex.getMessage());
        }
    }

    private RecognizedText recognize(FileMetadata metadata, Path filePath) {
        String ext = metadata.getFileExt().toLowerCase(Locale.ROOT);
        if ("pdf".equals(ext)) {
            String pdfText = extractPdfText(filePath);
            if (StringUtils.hasText(pdfText) && pdfText.trim().length() >= properties.getMinPdfTextLength()) {
                return new RecognizedText("PDF_TEXT", pdfText);
            }
            String ocrText = paddleOcrClient.recognize(filePath, metadata.getMimeType());
            return new RecognizedText("PADDLE_OCR", ocrText);
        }
        String ocrText = paddleOcrClient.recognize(filePath, metadata.getMimeType());
        return new RecognizedText("PADDLE_OCR", ocrText);
    }

    private String extractPdfText(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            throw new BusinessException("Failed to extract PDF text: " + ex.getMessage());
        }
    }

    private void validateFileForTarget(FileMetadata metadata, OcrTargetType targetType) {
        if (!OCR_FILE_EXTENSIONS.contains(metadata.getFileExt().toLowerCase(Locale.ROOT))) {
            throw new BusinessException("Unsupported OCR file type");
        }
        FileBusinessType expected = switch (targetType) {
            case PAPER -> FileBusinessType.PAPER;
            case PATENT -> FileBusinessType.PATENT;
            case CERTIFICATE -> FileBusinessType.CERTIFICATE;
        };
        if (!expected.name().equals(metadata.getBusinessType())) {
            throw new BusinessException("File business type does not match OCR target type");
        }
    }

    private Long createBusinessRecord(Long teacherId, OcrResult result) {
        return switch (OcrTargetType.valueOf(result.getTargetType())) {
            case PAPER -> createPaper(teacherId, result);
            case PATENT -> createPatent(teacherId, result);
            case CERTIFICATE -> createCertificate(teacherId, result);
        };
    }

    private Long createPaper(Long teacherId, OcrResult result) {
        if (!StringUtils.hasText(result.getTitle())) {
            return null;
        }
        Paper item = new Paper();
        item.setTeacherId(teacherId);
        item.setTitle(result.getTitle());
        item.setAuthors(blankToNull(result.getAuthors()));
        item.setPublicationName(blankToNull(result.getPublicationName()));
        item.setPublicationType(blankToNull(result.getPublicationType()));
        item.setPublishYear(result.getPublishYear());
        item.setDoi(blankToNull(result.getDoi()));
        item.setVolume(blankToNull(result.getVolume()));
        item.setIssue(blankToNull(result.getIssue()));
        item.setPages(blankToNull(result.getPages()));
        item.setPublisher(blankToNull(result.getPublisher()));
        item.setUrl(blankToNull(result.getUrl()));
        item.setAbstractText(blankToNull(result.getAbstractText()));
        item.setKeywords(blankToNull(result.getKeywords()));
        item.setSortOrder(0);
        item.setIsPublic(Boolean.FALSE);
        paperMapper.insert(item);
        return item.getId();
    }

    private Long createPatent(Long teacherId, OcrResult result) {
        if (!StringUtils.hasText(result.getPatentName())) {
            return null;
        }
        Patent item = new Patent();
        item.setTeacherId(teacherId);
        item.setPatentName(result.getPatentName());
        item.setPatentNumber(blankToNull(result.getPatentNumber()));
        item.setPatentType(blankToNull(result.getPatentType()));
        item.setStatus(blankToNull(result.getPatentStatus()));
        item.setApplicationDate(result.getApplicationDate());
        item.setAuthorizationDate(result.getAuthorizationDate());
        item.setInventors(blankToNull(result.getInventors()));
        item.setDescription(blankToNull(result.getDescription()));
        item.setSortOrder(0);
        item.setIsPublic(Boolean.FALSE);
        patentMapper.insert(item);
        return item.getId();
    }

    private Long createCertificate(Long teacherId, OcrResult result) {
        if (!StringUtils.hasText(result.getCertificateName())) {
            return null;
        }
        Certificate item = new Certificate();
        item.setTeacherId(teacherId);
        item.setCertificateName(result.getCertificateName());
        item.setCertificateType(blankToNull(result.getCertificateType()));
        item.setIssuingAuthority(blankToNull(result.getIssuingAuthority()));
        item.setIssueDate(result.getIssueDate());
        item.setDescription(blankToNull(result.getDescription()));
        item.setSortOrder(0);
        item.setIsPublic(Boolean.FALSE);
        certificateMapper.insert(item);
        return item.getId();
    }

    private void applyRequest(OcrResult result, OcrResultRequest request) {
        result.setTitle(blankToNull(limit(request.title(), 500)));
        result.setAuthors(blankToNull(limit(request.authors(), 5000)));
        result.setPublicationName(blankToNull(limit(request.publicationName(), 255)));
        result.setPublicationType(blankToNull(limit(request.publicationType(), 64)));
        result.setPublishYear(request.publishYear());
        result.setDoi(blankToNull(limit(request.doi(), 255)));
        result.setVolume(blankToNull(limit(request.volume(), 64)));
        result.setIssue(blankToNull(limit(request.issue(), 64)));
        result.setPages(blankToNull(limit(request.pages(), 64)));
        result.setPublisher(blankToNull(limit(request.publisher(), 255)));
        result.setUrl(blankToNull(limit(request.url(), 1024)));
        result.setAbstractText(blankToNull(limit(request.abstractText(), 20000)));
        result.setKeywords(blankToNull(limit(request.keywords(), 1000)));
        result.setPatentName(blankToNull(limit(request.patentName(), 255)));
        result.setPatentNumber(blankToNull(limit(request.patentNumber(), 128)));
        result.setPatentType(blankToNull(limit(request.patentType(), 128)));
        result.setPatentStatus(blankToNull(limit(request.patentStatus(), 64)));
        result.setApplicationDate(request.applicationDate());
        result.setAuthorizationDate(request.authorizationDate());
        result.setInventors(blankToNull(limit(request.inventors(), 5000)));
        result.setCertificateName(blankToNull(limit(request.certificateName(), 255)));
        result.setCertificateType(blankToNull(limit(request.certificateType(), 128)));
        result.setIssuingAuthority(blankToNull(limit(request.issuingAuthority(), 255)));
        result.setIssueDate(request.issueDate());
        result.setDescription(blankToNull(limit(request.description(), 5000)));
    }

    private OcrTask getOwnedTask(Long teacherId, Long taskId) {
        OcrTask task = taskMapper.selectById(taskId);
        if (task == null || !teacherId.equals(task.getTeacherId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "OCR task not found");
        }
        return task;
    }

    private OcrResult getOwnedResult(Long teacherId, Long resultId) {
        OcrResult result = resultMapper.selectById(resultId);
        if (result == null || !teacherId.equals(result.getTeacherId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "OCR result not found");
        }
        return result;
    }

    private List<OcrResult> listResults(Long teacherId, Long taskId) {
        return resultMapper.selectList(new LambdaQueryWrapper<OcrResult>()
                .eq(OcrResult::getTeacherId, teacherId)
                .eq(OcrResult::getTaskId, taskId)
                .orderByAsc(OcrResult::getId));
    }

    private void refreshTaskResultCount(Long taskId, Long teacherId) {
        OcrTask task = getOwnedTask(teacherId, taskId);
        Long count = resultMapper.selectCount(new LambdaQueryWrapper<OcrResult>()
                .eq(OcrResult::getTeacherId, teacherId)
                .eq(OcrResult::getTaskId, taskId)
                .ne(OcrResult::getStatus, RESULT_IGNORED));
        task.setResultCount(count.intValue());
        taskMapper.updateById(task);
    }

    private void markTaskFailed(OcrTask task, String message) {
        task.setStatus(STATUS_FAILED);
        task.setErrorMessage(limit(message, 1000));
        taskMapper.updateById(task);
    }

    private String toResultJson(OcrResult result) {
        try {
            return objectMapper.writeValueAsString(OcrResultResponse.from(result));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record RecognizedText(String mode, String text) {
    }
}
