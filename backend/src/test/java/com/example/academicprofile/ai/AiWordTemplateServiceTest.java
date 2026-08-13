package com.example.academicprofile.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileBusinessType;
import com.example.academicprofile.file.FileDownload;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileStorageService;
import com.example.academicprofile.word.WordTemplateService;
import com.example.academicprofile.word.WordTemplateServiceTest;

class AiWordTemplateServiceTest {

    private FileStorageService files;
    private AiWordTemplateService service;

    @BeforeEach
    void setUp() {
        files = mock(FileStorageService.class);
        service = new AiWordTemplateService(files, new WordTemplateService());
    }

    @Test
    void exampleAndBuiltInTestGenerationDoNotCallAiOrPersistFiles() throws IOException {
        AiGeneratedFile example = service.example();
        AiGeneratedFile test = service.test(1L, "TEACHER_ACHIEVEMENT", null);

        assertTrue(visibleText(example.content()).contains(WordTemplateService.PLACEHOLDER));
        String testText = visibleText(test.content());
        assertTrue(testText.contains(AiWordTemplateService.TEST_CONTENT));
        assertTrue(testText.contains("教师成果汇总"));
        assertFalse(testText.contains(WordTemplateService.PLACEHOLDER));
    }

    @Test
    void customTestAlwaysUsesCurrentTeacherOwnershipBoundary() throws IOException {
        byte[] template = WordTemplateServiceTest.documentWithSplitPlaceholder(false);
        FileMetadata metadata = new FileMetadata();
        metadata.setId(9L);
        metadata.setTeacherId(7L);
        metadata.setBusinessType(FileBusinessType.AI_WORD_TEMPLATE.name());
        when(files.readActiveAiWordTemplate(7L, 9L))
                .thenReturn(new FileDownload(metadata, new ByteArrayResource(template)));
        when(files.readActiveAiWordTemplate(8L, 9L))
                .thenThrow(new BusinessException("AI Word template not found"));

        assertTrue(visibleText(service.test(7L, "CUSTOM", 9L).content())
                .contains(AiWordTemplateService.TEST_CONTENT));
        assertThrows(BusinessException.class, () -> service.test(8L, "CUSTOM", 9L));
        verify(files).readActiveAiWordTemplate(7L, 9L);
        verify(files).readActiveAiWordTemplate(8L, 9L);
    }

    private String visibleText(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", String::concat);
        }
    }
}
