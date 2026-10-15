package com.example.academicprofile.ai;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.word.WordTemplateService;
import com.example.academicprofile.word.WordTemplateService.WordTemplateInspection;
import com.example.academicprofile.word.WordOutputTemplateType;
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
    public static final String WORD_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final AiGenerationTaskMapper taskMapper;
    private final AiGenerationResultMapper resultMapper;
    private final AiProperties properties;
    private final AiSettingsService settingsService;
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
            AiSettingsService settingsService,
            AiDataCollector dataCollector,
            AiChatClient chatClient,
            ObjectMapper objectMapper,
            FileStorageService fileStorageService,
            WordTemplateService wordTemplateService,
            PdfConversionClient pdfConversionClient) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.properties = properties;
        this.settingsService = settingsService;
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
        return createTask(new CurrentUser(-1L, teacherId, "", "", "TEACHER", 0L), request);
    }

    @Transactional
    public AiGenerationTaskResponse createTask(CurrentUser actor, AiGenerationRequest request) {
        Long teacherId = actor.teacherId();
        AiRuntimeConfig runtimeConfig = settingsService.requireRuntimeConfig(teacherId);
        List<String> modules = validateModules(request.selectedModules());
        String requirement = request.templateRequirement() == null ? "" : request.templateRequirement().trim();
        WordOutputTemplateType templateType = WordOutputTemplateType.resolve(
                request.templateType(), request.templateFileId());
        WordTemplateInspection templateInspection = null;
        if (templateType == WordOutputTemplateType.CUSTOM) {
            if (request.templateFileId() == null) {
                throw new BusinessException("请选择已上传的自定义 Word 模板");
            }
            FileDownload templateDownload = fileStorageService.readActiveAiWordTemplate(
                    teacherId,
                    request.templateFileId());
            templateInspection = inspectTemplate(templateDownload);
        } else {
            if (request.templateFileId() != null) {
                throw new BusinessException("系统模板不能指定自定义文件");
            }
            if (!StringUtils.hasText(requirement)) {
                throw new BusinessException("使用系统模板时，请填写本次生成要求");
            }
        }
        AiDataCollector.CollectionResult collected = dataCollector.collect(actor, modules, request);
        Map<String, Object> data = collected.data();
        String templateStructure = templateInspection == null
                ? wordTemplateService.structureFor(templateType)
                : templateInspection.structureText();
        String prompt = buildPrompt(requirement, modules, data, templateStructure, request.outputMode());
        validatePromptLength(prompt);

        AiGenerationTask task = new AiGenerationTask();
        task.setTeacherId(teacherId);
        task.setTemplateFileId(request.templateFileId());
        task.setTemplateType(templateType.name());
        task.setTemplateRequirement(requirement);
        task.setSelectedModules(toJson(modules));
        task.setSourceManifest(toJson(collected.sources().stream()
                .map(source -> source.withRuntime(runtimeConfig))
                .toList()));
        task.setProvider(runtimeConfig.providerName());
        task.setModelName(runtimeConfig.modelName());
        task.setStatus(STATUS_RUNNING);
        task.setDeleted(Boolean.FALSE);
        taskMapper.insert(task);

        try {
            String draft = chatClient.generate(prompt, runtimeConfig);
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
            throw new BusinessException("请先确认 AI 草稿，再导出 Word 或 PDF");
        }
        WordOutputTemplateType templateType = WordOutputTemplateType.resolve(
                task.getTemplateType(), task.getTemplateFileId());
        if (templateType == WordOutputTemplateType.CUSTOM) {
            FileDownload templateDownload = fileStorageService.readReferencedAiWordTemplate(
                    teacherId,
                    task.getTemplateFileId());
            try {
                byte[] content = wordTemplateService.render(
                        templateDownload.resource().getInputStream(),
                        result.getConfirmedContent());
                return content;
            } catch (IOException ex) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "无法读取历史任务使用的 Word 模板");
            }
        }
        return wordTemplateService.renderBuiltIn(
                templateType,
                result.getConfirmedContent(),
                task.getProvider() + " / " + task.getModelName());
    }

    private String buildPrompt(
            String requirement,
            List<String> modules,
            Map<String, Object> data,
            String templateStructure,
            String outputMode) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("请根据教师资料生成正式、清晰、可直接写入 Word 的纯文本内容。\n");
            prompt.append("只能使用提供的教师资料，不得编造事实，不要输出 Markdown 代码块。\n");
            String mode = outputMode == null ? "AI_DRAFT" : outputMode;
            if ("DETAIL".equals(mode)) {
                prompt.append("输出方式：按所选字段逐条列出明细。\n");
            } else if ("COUNT".equals(mode)) {
                prompt.append("输出方式：仅按资料模块给出准确数量统计，并保持简洁。\n");
            } else {
                prompt.append("输出方式：形成连贯的 AI 整理稿。\n");
            }
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
        modules.put("timetableOccupancy", "课表占用");
        return modules;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to save AI task metadata");
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
