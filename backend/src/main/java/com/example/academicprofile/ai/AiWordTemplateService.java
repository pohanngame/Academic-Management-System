package com.example.academicprofile.ai;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileDownload;
import com.example.academicprofile.file.FileMetadataResponse;
import com.example.academicprofile.file.FileStorageService;
import com.example.academicprofile.file.WordTemplateUploadResult;
import com.example.academicprofile.word.WordOutputTemplateType;
import com.example.academicprofile.word.WordTemplateService;

@Service
public class AiWordTemplateService {

    public static final String TEST_CONTENT = "这是一段用于检查 Word 排版的测试正文，不会调用第三方 AI。";

    private final FileStorageService files;
    private final WordTemplateService templates;

    public AiWordTemplateService(FileStorageService files, WordTemplateService templates) {
        this.files = files;
        this.templates = templates;
    }

    public AiWordTemplateUploadResponse upload(
            Long teacherId, MultipartFile file, boolean appendMissingPlaceholder) {
        WordTemplateUploadResult result = files.uploadAiWordTemplate(
                teacherId, file, appendMissingPlaceholder);
        return new AiWordTemplateUploadResponse(
                result.metadata() == null ? null : FileMetadataResponse.from(result.metadata()),
                AiWordTemplateValidationResponse.from(result.validation()));
    }

    public AiGeneratedFile example() {
        return new AiGeneratedFile(
                "ai-word-template-example.docx",
                AiGenerationService.WORD_CONTENT_TYPE,
                templates.createExampleTemplate());
    }

    public AiGeneratedFile test(Long teacherId, String templateTypeValue, Long templateFileId) {
        WordOutputTemplateType type = WordOutputTemplateType.resolve(templateTypeValue, templateFileId);
        byte[] content;
        if (type == WordOutputTemplateType.CUSTOM) {
            if (templateFileId == null) {
                throw new BusinessException("请选择要测试的自定义 Word 模板");
            }
            FileDownload template = files.readActiveAiWordTemplate(teacherId, templateFileId);
            try {
                content = templates.render(template.resource().getInputStream(), TEST_CONTENT);
            } catch (IOException ex) {
                throw new BusinessException("无法读取自定义 Word 模板");
            }
        } else {
            if (templateFileId != null) {
                throw new BusinessException("系统模板不能指定自定义文件");
            }
            content = templates.renderBuiltIn(type, TEST_CONTENT, "测试生成（未调用 AI）");
        }
        return new AiGeneratedFile(
                "ai-word-template-test.docx",
                AiGenerationService.WORD_CONTENT_TYPE,
                content);
    }
}
