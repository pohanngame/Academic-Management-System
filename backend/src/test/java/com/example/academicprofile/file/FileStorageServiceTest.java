package com.example.academicprofile.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.example.academicprofile.achievement.CertificateMapper;
import com.example.academicprofile.achievement.Paper;
import com.example.academicprofile.achievement.PaperMapper;
import com.example.academicprofile.achievement.PatentMapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.word.WordTemplateService;
import com.example.academicprofile.word.WordTemplateServiceTest;

class FileStorageServiceTest {

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @TempDir
    Path tempDir;

    private FileMetadataMapper fileMetadataMapper;
    private TeacherProfileService teacherProfileService;
    private PaperMapper paperMapper;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        fileMetadataMapper = mock(FileMetadataMapper.class);
        teacherProfileService = mock(TeacherProfileService.class);
        paperMapper = mock(PaperMapper.class);
        FileStorageProperties properties = new FileStorageProperties();
        properties.setUploadDir(tempDir.toString());
        TeacherProfile profile = new TeacherProfile();
        profile.setId(1L);
        when(teacherProfileService.getByTeacherId(1L)).thenReturn(profile);
        service = new FileStorageService(
                fileMetadataMapper,
                properties,
                teacherProfileService,
                paperMapper,
                mock(PatentMapper.class),
                mock(CertificateMapper.class),
                new WordTemplateService());
    }

    @Test
    void keepsAttachmentAndWordTemplateWhitelistsIsolated() throws IOException {
        Paper paper = new Paper();
        paper.setId(10L);
        paper.setTeacherId(1L);
        when(paperMapper.selectById(10L)).thenReturn(paper);
        MockMultipartFile docx = new MockMultipartFile(
                "file",
                "template.docx",
                DOCX_MIME,
                WordTemplateServiceTest.documentWithSplitPlaceholder(false));
        MockMultipartFile pdf = new MockMultipartFile(
                "file",
                "paper.pdf",
                "application/pdf",
                "%PDF-1.7 local fixture".getBytes());

        assertThrows(
                BusinessException.class,
                () -> service.uploadFile(1L, FileBusinessType.PAPER, 10L, docx));
        assertThrows(
                BusinessException.class,
                () -> service.uploadFile(1L, FileBusinessType.AI_WORD_TEMPLATE, null, pdf));

        FileMetadata uploadedPdf = service.uploadFile(1L, FileBusinessType.PAPER, 10L, pdf);
        assertEquals("pdf", uploadedPdf.getFileExt());
        assertTrue(Files.isRegularFile(tempDir.resolve(uploadedPdf.getStoragePath())));
    }

    @Test
    void invalidTemplateLeavesNoFileOrMetadata() throws IOException {
        byte[] invalidDocx;
        try (org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("missing placeholder");
            document.write(output);
            invalidDocx = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "invalid.docx", DOCX_MIME, invalidDocx);

        assertThrows(
                BusinessException.class,
                () -> service.uploadFile(1L, FileBusinessType.AI_WORD_TEMPLATE, null, file));

        verify(fileMetadataMapper, never()).insert(any(FileMetadata.class));
        assertEquals(0, regularFileCount());
    }

    @Test
    void databaseFailureCleansOnlyNewlyWrittenFile() throws IOException {
        when(fileMetadataMapper.insert(any(FileMetadata.class)))
                .thenThrow(new IllegalStateException("local database failure"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "template.docx",
                DOCX_MIME,
                WordTemplateServiceTest.documentWithSplitPlaceholder(false));

        assertThrows(
                IllegalStateException.class,
                () -> service.uploadFile(1L, FileBusinessType.AI_WORD_TEMPLATE, null, file));
        assertEquals(0, regularFileCount());
    }

    @Test
    void deletedTemplateIsHiddenFromOrdinaryDownloadButReadableForHistoricalTask() throws IOException {
        byte[] templateBytes = WordTemplateServiceTest.documentWithSplitPlaceholder(false);
        Path storedPath = tempDir.resolve("1/ai_word_template/template.docx");
        Files.createDirectories(storedPath.getParent());
        Files.copy(new ByteArrayInputStream(templateBytes), storedPath);
        FileMetadata metadata = new FileMetadata();
        metadata.setId(20L);
        metadata.setTeacherId(1L);
        metadata.setBusinessType(FileBusinessType.AI_WORD_TEMPLATE.name());
        metadata.setStoragePath("1/ai_word_template/template.docx");
        metadata.setOriginalName("template.docx");
        metadata.setDeleted(Boolean.TRUE);
        when(fileMetadataMapper.selectById(20L)).thenReturn(metadata);

        assertThrows(BusinessException.class, () -> service.downloadFile(1L, 20L));
        assertThrows(BusinessException.class, () -> service.readActiveAiWordTemplate(1L, 20L));
        assertThrows(BusinessException.class, () -> service.readReferencedAiWordTemplate(2L, 20L));

        FileDownload historical = service.readReferencedAiWordTemplate(1L, 20L);
        assertTrue(historical.resource().exists());
    }

    @Test
    void templateReadRejectsWrongBusinessTypeAndMissingContent() {
        FileMetadata wrongType = new FileMetadata();
        wrongType.setId(30L);
        wrongType.setTeacherId(1L);
        wrongType.setBusinessType(FileBusinessType.PAPER.name());
        wrongType.setStoragePath("missing.docx");
        wrongType.setDeleted(Boolean.FALSE);
        FileMetadata missingContent = new FileMetadata();
        missingContent.setId(31L);
        missingContent.setTeacherId(1L);
        missingContent.setBusinessType(FileBusinessType.AI_WORD_TEMPLATE.name());
        missingContent.setStoragePath("missing.docx");
        missingContent.setDeleted(Boolean.FALSE);
        when(fileMetadataMapper.selectById(30L)).thenReturn(wrongType);
        when(fileMetadataMapper.selectById(31L)).thenReturn(missingContent);

        assertThrows(BusinessException.class, () -> service.readActiveAiWordTemplate(1L, 30L));
        assertThrows(BusinessException.class, () -> service.readActiveAiWordTemplate(1L, 31L));
    }

    private long regularFileCount() throws IOException {
        try (var files = Files.walk(tempDir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }
}
