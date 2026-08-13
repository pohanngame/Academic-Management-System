package com.example.academicprofile.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.word.WordTemplateValidationCode;
import com.example.academicprofile.word.WordTemplateValidationResult;

class AiWordTemplateControllerTest {

    private AiWordTemplateService service;
    private AiWordTemplateController controller;

    @BeforeEach
    void setUp() {
        service = mock(AiWordTemplateService.class);
        controller = new AiWordTemplateController(service);
        CurrentUser teacher = new CurrentUser(5L, 42L, "teacher", "teacher@example.test", "TEACHER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(teacher, null));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadAndTestNeverAcceptTeacherIdFromRequest() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "template.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[] {1});
        AiWordTemplateUploadResponse response = new AiWordTemplateUploadResponse(
                null,
                AiWordTemplateValidationResponse.from(new WordTemplateValidationResult(
                        WordTemplateValidationCode.PLACEHOLDER_MISSING,
                        "未检测到正文插入位置", 0, "未指定", false)));
        when(service.upload(42L, file, false)).thenReturn(response);
        when(service.test(42L, "CUSTOM", 9L)).thenReturn(
                new AiGeneratedFile("test.docx", AiGenerationService.WORD_CONTENT_TYPE, new byte[] {1}));

        assertEquals(WordTemplateValidationCode.PLACEHOLDER_MISSING.name(),
                controller.upload(file, false).data().validation().code());
        controller.test("CUSTOM", 9L);

        verify(service).upload(42L, file, false);
        verify(service).test(42L, "CUSTOM", 9L);
    }
}
