package com.example.academicprofile.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileDownload;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileStorageService;
import com.example.academicprofile.pdf.PdfConversionClient;
import com.example.academicprofile.word.WordTemplateService;
import com.example.academicprofile.word.WordTemplateService.WordTemplateInspection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiGenerationService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String RESULT_DRAFT = "DRAFT";
    private static final String RESULT_CONFIRMED = "CONFIRMED";
    private static final String WORD_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final AiGenerationTaskMapper taskMapper;
    private final AiGenerationResultMapper resultMapper;
    private final AiProperties properties;
    private final AiDataCollector dataCollector;
    private final AiChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final WordTemplateService wordTemplateService;
    private final PdfConversionClient pdfConversionClient;

    public AiGenerationService(
            AiGenerationTaskMapper taskMapper,
            AiGenerationResultMapper resultMapper,
            AiProperties properties,
            AiDataCollector dataCollector,
            AiChatClient chatClient,
            ObjectMapper objectMapper,
            FileStorageService fileStorageService,
            WordTemplateService wordTemplateService,
            PdfConversionClient pdfConversionClient) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.properties = properties;
        this.dataCollector = dataCollector;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.fileStorageService = fileStorageService;
        this.wordTemplateService = wordTemplateService;
        this.pdfConversionClient = pdfConversionClient;
    }

    public List<AiModuleDefinition> moduleDefinitions() {
        return moduleMap().entrySet().stream()
                .map(entry -> new AiModuleDefinition(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional
    public AiGenerationTaskResponse createTask(Long teacherId, AiGenerationRequest request) {
        List<String> modules = validateModules(request.selectedModules());
        String requirement = request.templateRequirement() == null ? "" : request.templateRequirement().trim();
        WordTemplateInspection templateInspection = null;
        if (request.templateFileId() == null) {
            if (!StringUtils.hasText(requirement)) {
                throw new BusinessException("Text requirement is required when no Word template is selected");
            }
        } else {
            FileDownload templateDownload = fileStorageService.readActiveAiWordTemplate(
                    teacherId,
                    request.templateFileId());
            templateInspection = inspectTemplate(templateDownload);
        }
        Map<String, Object> data = dataCollector.collect(teacherId, modules);
        String templateStructure = templateInspection == null ? null : templateInspection.structureText();
        String prompt = buildPrompt(requirement, modules, data, templateStructure);
        validatePromptLength(prompt);

        AiGenerationTask task = new AiGenerationTask();
        task.setTeacherId(teacherId);
        task.setTemplateFileId(request.templateFileId());
        task.setTemplateRequirement(requirement);
        task.setSelectedModules(toJson(modules));
        task.setProvider(properties.getProvider());
        task.setModelName(properties.getModel());
        task.setStatus(STATUS_RUNNING);
        task.setDeleted(Boolean.FALSE);
        taskMapper.insert(task);

        try {
            String draft = chatClient.generate(prompt);
            AiGenerationResult result = new AiGenerationResult();
            result.setTaskId(task.getId());
            result.setTeacherId(teacherId);
            result.setStatus(RESULT_DRAFT);
            result.setDraftContent(draft);
            result.setEditedContent(draft);
            result.setDeleted(Boolean.FALSE);
            resultMapper.insert(result);
            task.setStatus(STATUS_SUCCEEDED);
            task.setErrorMessage(null);
            taskMapper.updateById(task);
        } catch (BusinessException ex) {
            task.setStatus(STATUS_FAILED);
            task.setErrorMessage(limit(ex.getMessage(), 1000));
            taskMapper.updateById(task);
        }
        return getTask(teacherId, task.getId());
    }

    public List<AiGenerationTaskResponse> listTasks(Long teacherId) {
        return taskMapper.selectList(new LambdaQueryWrapper<AiGenerationTask>()
                        .eq(AiGenerationTask::getTeacherId, teacherId)
                        .eq(AiGenerationTask::getDeleted, Boolean.FALSE)
                        .orderByDesc(AiGenerationTask::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AiGenerationTaskResponse getTask(Long teacherId, Long taskId) {
        AiGenerationTask task = getOwnedTask(teacherId, taskId);
        return toResponse(task);
    }

    @Transactional
    public AiGenerationResultResponse updateResult(Long teacherId, Long resultId, AiResultUpdateRequest request) {
        AiGenerationResult result = getOwnedResult(teacherId, resultId);
        if (RESULT_CONFIRMED.equals(result.getStatus())) {
            throw new BusinessException("Confirmed AI result cannot be edited");
        }
        result.setEditedContent(request.editedContent().trim());
        resultMapper.updateById(result);
        return AiGenerationResultResponse.from(result);
    }

    @Transactional
    public AiGenerationTaskResponse confirmTask(Long teacherId, Long taskId) {
        AiGenerationTask task = getOwnedTask(teacherId, taskId);
        AiGenerationResult result = findResult(task);
        if (result == null || !RESULT_DRAFT.equals(result.getStatus())) {
            throw new BusinessException("No draft result can be confirmed");
        }
        String content = StringUtils.hasText(result.getEditedContent())
                ? result.getEditedContent()
                : result.getDraftContent();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("AI draft content is empty");
        }
        result.setConfirmedContent(content);
        result.setStatus(RESULT_CONFIRMED);
        result.setConfirmedAt(LocalDateTime.now());
        resultMapper.updateById(result);
        task.setStatus(STATUS_CONFIRMED);
        taskMapper.updateById(task);
        return getTask(teacherId, taskId);
    }

    @Transactional
    public void deleteTask(Long teacherId, Long taskId) {
        AiGenerationTask task = getOwnedTask(teacherId, taskId);
        task.setDeleted(Boolean.TRUE);
        taskMapper.updateById(task);
        AiGenerationResult result = findResult(task);
        if (result != null) {
            result.setDeleted(Boolean.TRUE);
            resultMapper.updateById(result);
        }
    }

    public AiGeneratedFile exportWord(Long teacherId, Long taskId) {
        byte[] content = buildConfirmedWord(teacherId, taskId);
        return new AiGeneratedFile(fileName(taskId, "docx"), WORD_CONTENT_TYPE, content);
    }

    public AiGeneratedFile exportPdf(Long teacherId, Long taskId) {
        byte[] wordContent = buildConfirmedWord(teacherId, taskId);
        byte[] pdfContent = pdfConversionClient.convertDocx(wordContent, taskId);
        return new AiGeneratedFile(fileName(taskId, "pdf"), PDF_CONTENT_TYPE, pdfContent);
    }

    private byte[] buildConfirmedWord(Long teacherId, Long taskId) {
        AiGenerationTask task = getOwnedTask(teacherId, taskId);
        AiGenerationResult result = findResult(task);
        if (result == null || !RESULT_CONFIRMED.equals(result.getStatus())) {
            throw new BusinessException("Please confirm the AI draft before exporting Word or PDF");
        }
        if (task.getTemplateFileId() != null) {
            FileDownload templateDownload = fileStorageService.readReferencedAiWordTemplate(
                    teacherId,
                    task.getTemplateFileId());
            try {
                byte[] content = wordTemplateService.render(
                        templateDownload.resource().getInputStream(),
                        result.getConfirmedContent());
                return content;
            } catch (IOException ex) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read Word template");
            }
        }
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeTitle(document, "AI 生成材料");
            writeMetadata(document, task);
            writeContent(document, result.getConfirmedContent());
            document.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate Word file");
        }
    }

    private String buildPrompt(
            String requirement,
            List<String> modules,
            Map<String, Object> data,
            String templateStructure) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("请根据教师资料生成正式、清晰、可直接写入 Word 的纯文本内容。\n");
            prompt.append("只能使用提供的教师资料，不得编造事实，不要输出 Markdown 代码块。\n");
            if (StringUtils.hasText(templateStructure)) {
                prompt.append("请遵循以下 Word 模板中的静态标题、段落和表格顺序。模板占位符已经移除：\n");
                prompt.append(templateStructure.replace(WordTemplateService.PLACEHOLDER, "")).append("\n");
            }
            if (StringUtils.hasText(requirement)) {
                prompt.append("补充要求：\n").append(requirement).append("\n");
            }
            prompt.append("已选择的数据范围：\n").append(String.join(", ", modules)).append("\n");
            prompt.append("教师资料 JSON：\n");
            prompt.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
            return prompt.toString();
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to prepare AI prompt");
        }
    }

    private WordTemplateInspection inspectTemplate(FileDownload templateDownload) {
        try (InputStream input = templateDownload.resource().getInputStream()) {
            return wordTemplateService.inspect(input);
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Word template content not found");
        }
    }

    private void validatePromptLength(String prompt) {
        int promptChars = AiChatClient.promptCharacterCount(prompt);
        if (promptChars > properties.getMaxPromptChars()) {
            throw new BusinessException(
                    "AI prompt exceeds the " + properties.getMaxPromptChars() + " character limit before AI request");
        }
    }

    private AiGenerationTaskResponse toResponse(AiGenerationTask task) {
        FileMetadata templateFile = task.getTemplateFileId() == null
                ? null
                : fileStorageService.getReferencedAiWordTemplateMetadata(task.getTeacherId(), task.getTemplateFileId());
        return AiGenerationTaskResponse.from(
                task,
                parseModules(task.getSelectedModules()),
                findResult(task),
                templateFile);
    }

    private AiGenerationTask getOwnedTask(Long teacherId, Long taskId) {
        AiGenerationTask task = taskMapper.selectById(taskId);
        if (task == null || !teacherId.equals(task.getTeacherId()) || Boolean.TRUE.equals(task.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "AI generation task not found");
        }
        return task;
    }

    private AiGenerationResult getOwnedResult(Long teacherId, Long resultId) {
        AiGenerationResult result = resultMapper.selectById(resultId);
        if (result == null || !teacherId.equals(result.getTeacherId()) || Boolean.TRUE.equals(result.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "AI generation result not found");
        }
        return result;
    }

    private AiGenerationResult findResult(AiGenerationTask task) {
        return resultMapper.selectOne(new LambdaQueryWrapper<AiGenerationResult>()
                .eq(AiGenerationResult::getTeacherId, task.getTeacherId())
                .eq(AiGenerationResult::getTaskId, task.getId())
                .eq(AiGenerationResult::getDeleted, Boolean.FALSE)
                .last("LIMIT 1"));
    }

    private List<String> validateModules(List<String> modules) {
        if (modules == null || modules.isEmpty()) {
            throw new BusinessException("At least one data module is required");
        }
        Map<String, String> allowed = moduleMap();
        List<String> result = new ArrayList<>();
        for (String module : modules) {
            if (!allowed.containsKey(module)) {
                throw new BusinessException("Unsupported AI data module: " + module);
            }
            if (!result.contains(module)) {
                result.add(module);
            }
        }
        return result;
    }

    private Map<String, String> moduleMap() {
        Map<String, String> modules = new LinkedHashMap<>();
        modules.put("teacherProfile", "教师基础资料");
        modules.put("academicQualifications", "学历");
        modules.put("teachingAreas", "教学方向");
        modules.put("researchAreas", "研究方向");
        modules.put("professionalServices", "专业服务");
        modules.put("workingExperiences", "工作经历");
        modules.put("projects", "科研项目");
        modules.put("teachingCourses", "授课记录");
        modules.put("papers", "论文/学术成果");
        modules.put("patents", "专利");
        modules.put("certificates", "证书");
        return modules;
    }

    private String toJson(List<String> modules) {
        try {
            return objectMapper.writeValueAsString(modules);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to save selected modules");
        }
    }

    private List<String> parseModules(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private void writeTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setText(title);
    }

    private void writeMetadata(XWPFDocument document, AiGenerationTask task) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontSize(10);
        run.setColor("666666");
        run.setText("生成模型：" + task.getProvider() + " / " + task.getModelName());
    }

    private void writeContent(XWPFDocument document, String content) {
        for (String block : content.split("\\R{2,}")) {
            XWPFParagraph paragraph = document.createParagraph();
            for (String line : block.split("\\R")) {
                XWPFRun run = paragraph.createRun();
                run.setFontSize(11);
                run.setText(line);
                run.addBreak();
            }
        }
    }

    private String fileName(Long taskId, String extension) {
        return "ai-generation-" + taskId + "-"
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "." + extension;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
