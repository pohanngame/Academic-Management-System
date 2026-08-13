package com.example.academicprofile.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.example.academicprofile.access.ResourceAuthorizationService;
import com.example.academicprofile.access.ResourcePolicyService;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.word.WordTemplateService;
import com.example.academicprofile.word.WordTemplateServiceTest;
import com.example.academicprofile.word.WordTemplateValidationCode;
import com.example.academicprofile.word.WordTemplateValidationException;

class FileStorageServiceTest {

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @TempDir
    Path tempDir;

    private FileMetadataMapper fileMetadataMapper;
    private TeacherProfileService teacherProfileService;
    private PaperMapper paperMapper;
    private EncryptedFileCodec encryptedFiles;
    private FileStorageService service;
    private FileStorageProperties properties;

    @BeforeEach
    void setUp() {
        fileMetadataMapper = mock(FileMetadataMapper.class);
        teacherProfileService = mock(TeacherProfileService.class);
        paperMapper = mock(PaperMapper.class);
        properties = new FileStorageProperties();
        properties.setUploadDir(tempDir.toString());
        properties.setTempDir(tempDir.resolve("runtime").toString());
        properties.setEncryptionKeyFile(tempDir.resolve("secrets/file-encryption.key").toString());
        encryptedFiles = new EncryptedFileCodec(new FileEncryptionKeyManager(properties));
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
                new WordTemplateService(),
                encryptedFiles,
                mock(ResourceAuthorizationService.class),
                mock(ResourcePolicyService.class));
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
    void missingPlaceholderIsReportedWithoutSaving() throws IOException {
        byte[] invalidDocx;
        try (org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("missing placeholder");
            document.write(output);
            invalidDocx = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "invalid.docx", DOCX_MIME, invalidDocx);

        WordTemplateUploadResult result = service.uploadAiWordTemplate(1L, file, false);

        assertEquals(WordTemplateValidationCode.PLACEHOLDER_MISSING, result.validation().code());
        assertEquals(null, result.metadata());
        verify(fileMetadataMapper, never()).insert(any(FileMetadata.class));
        assertEquals(0, regularFileCount());
    }

    @Test
    void confirmedMissingPlaceholderIsNormalizedThenSaved() throws IOException {
        byte[] withoutPlaceholder;
        try (org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("普通模板固定文字");
            document.write(output);
            withoutPlaceholder = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "ordinary.docx", DOCX_MIME, withoutPlaceholder);

        WordTemplateUploadResult result = service.uploadAiWordTemplate(1L, file, true);

        assertTrue(result.validation().valid());
        assertTrue(result.validation().autoAppended());
        assertEquals("文档末尾", result.validation().insertionLocation());
        verify(fileMetadataMapper).insert(any(FileMetadata.class));
        assertTrue(Files.isRegularFile(tempDir.resolve(result.metadata().getStoragePath())));
    }

    @Test
    void wordTemplateRejectsUnsupportedExtensionAndConfiguredSizeLimitBeforePersistence() {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "legacy.doc", DOCX_MIME, new byte[] {1, 2, 3});
        properties.setMaxUploadSize(org.springframework.util.unit.DataSize.ofBytes(4));
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.docx", DOCX_MIME, new byte[] {1, 2, 3, 4, 5});

        WordTemplateValidationException unsupported = assertThrows(
                WordTemplateValidationException.class,
                () -> service.uploadAiWordTemplate(1L, disguised, false));
        WordTemplateValidationException tooLarge = assertThrows(
                WordTemplateValidationException.class,
                () -> service.uploadAiWordTemplate(1L, oversized, false));

        assertEquals(WordTemplateValidationCode.UNSUPPORTED_FILE_TYPE, unsupported.code());
        assertEquals(WordTemplateValidationCode.FILE_TOO_LARGE, tooLarge.code());
        verify(fileMetadataMapper, never()).insert(any(FileMetadata.class));
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

    @Test
    void ordinaryAttachmentStillRejectsFilesOverTenMiB() {
        Paper paper = new Paper();
        paper.setId(10L);
        paper.setTeacherId(1L);
        when(paperMapper.selectById(10L)).thenReturn(paper);
        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                new byte[10 * 1024 * 1024 + 1]);

        assertThrows(
                BusinessException.class,
                () -> service.uploadFile(1L, FileBusinessType.PAPER, 10L, oversized));
        verify(fileMetadataMapper, never()).insert(any(FileMetadata.class));
    }

    @Test
    void readableTempFilePreservesTheValidatedOriginalExtension() {
        byte[] plaintext = "%PDF-1.7 OCR fixture".getBytes();
        Path ciphertext = tempDir.resolve("fixture.bin");
        EncryptedFileCodec.EncryptionResult encryption =
                encryptedFiles.encrypt(new ByteArrayInputStream(plaintext), ciphertext, "fixture.bin");
        FileMetadata metadata = new FileMetadata();
        metadata.setId(40L);
        metadata.setTeacherId(1L);
        metadata.setStoredName("fixture.bin");
        metadata.setStoragePath("fixture.bin");
        metadata.setFileExt("pdf");
        metadata.setEncryptionVersion(EncryptedFileCodec.VERSION);
        metadata.setEncryptionKeyId(encryption.keyId());
        metadata.setWrappedDek(encryption.wrappedDek());
        metadata.setCiphertextStatus("ENCRYPTED");
        metadata.setDeleted(Boolean.FALSE);
        when(fileMetadataMapper.selectById(40L)).thenReturn(metadata);

        Path readable = service.withReadableTempFile(1L, 40L, path -> {
            assertTrue(path.getFileName().toString().endsWith(".pdf"));
            try {
                assertArrayEquals(plaintext, Files.readAllBytes(path));
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
            return path;
        });

        assertFalse(Files.exists(readable));
    }

    @Test
    void readableTempFileRejectsUnsafeDatabaseExtensionBeforeCreatingPlaintext() throws IOException {
        FileMetadata metadata = encryptedMetadata(41L, "dangerous.exe", "exe", "payload".getBytes());
        when(fileMetadataMapper.selectById(41L)).thenReturn(metadata);

        assertThrows(
                BusinessException.class,
                () -> service.withReadableTempFile(1L, 41L, path -> null));

        assertEquals(0, tempPlaintextCount());
    }

    @Test
    void readableTempFileIsDeletedWhenActionFails() throws IOException {
        FileMetadata metadata = encryptedMetadata(42L, "image.png", "png", "image".getBytes());
        when(fileMetadataMapper.selectById(42L)).thenReturn(metadata);
        Path[] observed = new Path[1];

        assertThrows(IllegalStateException.class, () -> service.withReadableTempFile(
                1L, 42L, path -> {
                    observed[0] = path;
                    assertTrue(path.getFileName().toString().endsWith(".png"));
                    throw new IllegalStateException("synthetic action failure");
                }));

        assertFalse(Files.exists(observed[0]));
        assertEquals(0, tempPlaintextCount());
    }

    @Test
    void decryptFailureLeavesNoPlaintextTempFile() throws IOException {
        FileMetadata metadata = encryptedMetadata(43L, "broken.pdf", "pdf", "payload".getBytes());
        Path ciphertext = tempDir.resolve(metadata.getStoragePath());
        byte[] bytes = Files.readAllBytes(ciphertext);
        bytes[bytes.length - 1] ^= 1;
        Files.write(ciphertext, bytes);
        when(fileMetadataMapper.selectById(43L)).thenReturn(metadata);

        assertThrows(
                BusinessException.class,
                () -> service.withReadableTempFile(1L, 43L, path -> null));

        assertEquals(0, tempPlaintextCount());
    }

    private FileMetadata encryptedMetadata(Long id, String name, String ext, byte[] plaintext) {
        String storedName = "fixture-" + id + ".bin";
        Path ciphertext = tempDir.resolve(storedName);
        EncryptedFileCodec.EncryptionResult encryption =
                encryptedFiles.encrypt(new ByteArrayInputStream(plaintext), ciphertext, storedName);
        FileMetadata metadata = new FileMetadata();
        metadata.setId(id);
        metadata.setTeacherId(1L);
        metadata.setOriginalName(name);
        metadata.setStoredName(storedName);
        metadata.setStoragePath(storedName);
        metadata.setFileExt(ext);
        metadata.setEncryptionVersion(EncryptedFileCodec.VERSION);
        metadata.setEncryptionKeyId(encryption.keyId());
        metadata.setWrappedDek(encryption.wrappedDek());
        metadata.setCiphertextStatus("ENCRYPTED");
        metadata.setDeleted(Boolean.FALSE);
        return metadata;
    }

    private long tempPlaintextCount() throws IOException {
        Path runtime = tempDir.resolve("runtime");
        if (!Files.exists(runtime)) {
            return 0;
        }
        try (var paths = Files.list(runtime)) {
            return paths.count();
        }
    }

    private long regularFileCount() throws IOException {
        try (var files = Files.walk(tempDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(tempDir.resolve("secrets/file-encryption.key")))
                    .count();
        }
    }
}
