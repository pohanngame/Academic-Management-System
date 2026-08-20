package com.example.academicprofile.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.academicprofile.access.ResourcePolicyService;
import com.example.academicprofile.access.ResourceType;
import com.example.academicprofile.achievement.CompetitionCategory;
import com.example.academicprofile.file.FileBusinessType;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileStorageService;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OcrCertificateStagingIntegrationTest {
    private static final Path TEST_DATA = createTestDataDirectory();

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FileStorageService files;
    @Autowired private OcrService ocr;
    @Autowired private OcrTaskMapper tasks;
    @Autowired private OcrResultMapper results;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper json;
    @SpyBean private ResourcePolicyService policies;
    @MockBean private PaddleOcrClient paddle;

    private Long ownerUserId;
    private Long ownerTeacherId;
    private Long otherUserId;
    private Long otherTeacherId;
    private Long adminUserId;
    private Long adminTeacherId;

    @DynamicPropertySource
    static void isolatedFiles(DynamicPropertyRegistry properties) {
        properties.add("app.files.upload-dir", () -> TEST_DATA.resolve("uploads").toString());
        properties.add("app.files.temp-dir", () -> TEST_DATA.resolve("temp").toString());
        properties.add("app.files.encryption-key-file",
                () -> TEST_DATA.resolve("secrets/file-encryption.key").toString());
        properties.add("app.security.jwt-secret", () -> "ocr-staging-integration-signing-key-32");
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        ownerUserId = createUser("ocr-owner-" + suffix, "TEACHER");
        ownerTeacherId = createTeacher(ownerUserId, "OCR Owner");
        otherUserId = createUser("ocr-other-" + suffix, "TEACHER");
        otherTeacherId = createTeacher(otherUserId, "OCR Other");
        adminUserId = createUser("ocr-admin-" + suffix, "SUPER_ADMIN");
        adminTeacherId = createTeacher(adminUserId, "OCR Admin");
        when(paddle.recognize(org.mockito.ArgumentMatchers.any(Path.class), anyString()))
                .thenReturn("证书名称：合成测试证书\n颁发机构：测试机构\n颁发日期：2026-08-20");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM resource_access_policy WHERE owner_teacher_id IN (?, ?, ?)",
                ownerTeacherId, otherTeacherId, adminTeacherId);
        jdbc.update("DELETE FROM ocr_result WHERE teacher_id IN (?, ?, ?)",
                ownerTeacherId, otherTeacherId, adminTeacherId);
        jdbc.update("DELETE FROM ocr_task WHERE teacher_id IN (?, ?, ?)",
                ownerTeacherId, otherTeacherId, adminTeacherId);
        jdbc.update("DELETE FROM file_metadata WHERE teacher_id IN (?, ?, ?)",
                ownerTeacherId, otherTeacherId, adminTeacherId);
        jdbc.update("DELETE FROM certificate WHERE teacher_id IN (?, ?, ?)",
                ownerTeacherId, otherTeacherId, adminTeacherId);
        for (Long userId : List.of(ownerUserId, otherUserId, adminUserId)) {
            jdbc.update("DELETE FROM academic_role_assignment WHERE user_id = ?", userId);
        }
        jdbc.update("DELETE FROM teacher_profile WHERE id IN (?, ?, ?)",
                ownerTeacherId, otherTeacherId, adminTeacherId);
        for (Long userId : List.of(ownerUserId, otherUserId, adminUserId)) {
            jdbc.update("DELETE FROM `user` WHERE id = ?", userId);
        }
    }

    @AfterAll
    static void removeTestDataDirectory() throws Exception {
        if (!TEST_DATA.getFileName().toString().startsWith("restore-test-ocr-staging-")) return;
        try (var paths = Files.walk(TEST_DATA)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }

    @Test
    void confirmCreatesOneCertificateLinksEncryptedStagingAndIsIdempotent() throws Exception {
        Staged staged = stagedTask(ownerTeacherId, "certificate.png", "image/png");
        FileMetadata encryptedStaging = files.getOwnedFileMetadata(ownerTeacherId, staged.fileId());
        assertThat(Files.readAllBytes(TEST_DATA.resolve("uploads").resolve(encryptedStaging.getStoragePath())))
                .isNotEqualTo(imageBytes("certificate.png"));

        OcrConfirmResponse first = ocr.confirmTask(ownerTeacherId, staged.taskId(),
                new OcrConfirmRequest(List.of(staged.resultId()), CompetitionCategory.SECOND_CLASS));
        OcrConfirmResponse duplicate = ocr.confirmTask(ownerTeacherId, staged.taskId(),
                new OcrConfirmRequest(List.of(staged.resultId()), CompetitionCategory.SECOND_CLASS));

        assertThat(first.confirmedCount()).isEqualTo(1);
        assertThat(duplicate.confirmedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM certificate WHERE teacher_id=?",
                Integer.class, ownerTeacherId)).isEqualTo(1);
        Long certificateId = jdbc.queryForObject(
                "SELECT created_record_id FROM ocr_result WHERE id=?", Long.class, staged.resultId());
        assertThat(jdbc.queryForObject(
                "SELECT business_type FROM file_metadata WHERE id=?", String.class, staged.fileId()))
                .isEqualTo(FileBusinessType.CERTIFICATE.name());
        assertThat(jdbc.queryForObject(
                "SELECT business_id FROM file_metadata WHERE id=?", Long.class, staged.fileId()))
                .isEqualTo(certificateId);
        assertThat(jdbc.queryForObject(
                "SELECT ciphertext_status FROM file_metadata WHERE id=?", String.class, staged.fileId()))
                .isEqualTo("ENCRYPTED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM resource_access_policy
                WHERE owner_teacher_id=? AND resource_type='FILE_ATTACHMENT' AND resource_id=?
                """, Integer.class, ownerTeacherId, staged.fileId())).isEqualTo(1);
    }

    @Test
    void attachmentPolicyFailureRollsBackCertificateAssociationResultAndPolicies() {
        Staged staged = stagedTask(ownerTeacherId, "rollback.png", "image/png");
        doThrow(new IllegalStateException("synthetic policy failure"))
                .when(policies).createDefault(eq(ownerTeacherId), eq(ResourceType.FILE_ATTACHMENT), eq(staged.fileId()));

        try {
            assertThatThrownBy(() -> ocr.confirmTask(ownerTeacherId, staged.taskId(),
                    new OcrConfirmRequest(List.of(staged.resultId()), CompetitionCategory.SECOND_CLASS)))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            doCallRealMethod().when(policies)
                    .createDefault(eq(ownerTeacherId), eq(ResourceType.FILE_ATTACHMENT), eq(staged.fileId()));
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM certificate WHERE teacher_id=?",
                Integer.class, ownerTeacherId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM ocr_result WHERE id=?",
                String.class, staged.resultId())).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT created_record_id FROM ocr_result WHERE id=?",
                Long.class, staged.resultId())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM ocr_task WHERE id=?",
                String.class, staged.taskId())).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT business_type FROM file_metadata WHERE id=?",
                String.class, staged.fileId())).isEqualTo(FileBusinessType.CERTIFICATE_OCR_STAGING.name());
        assertThat(jdbc.queryForObject("SELECT business_id FROM file_metadata WHERE id=?",
                Long.class, staged.fileId())).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_access_policy WHERE owner_teacher_id=?",
                Integer.class, ownerTeacherId)).isZero();
    }

    @Test
    void crossTeacherAndSuperAdminCannotOperateTeacherStagingTask() throws Exception {
        Staged staged = stagedTask(ownerTeacherId, "private.png", "image/png");
        byte[] body = json.writeValueAsBytes(java.util.Map.of(
                "resultIds", List.of(staged.resultId()), "competitionCategory", "SECOND_CLASS"));

        mvc.perform(post("/api/ocr/tasks/{id}/confirm", staged.taskId())
                        .header("Authorization", bearer(otherUserId, otherTeacherId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/ocr/tasks/{id}", staged.taskId())
                        .header("Authorization", bearer(otherUserId, otherTeacherId, "TEACHER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/ocr/tasks/{id}", staged.taskId())
                        .header("Authorization", bearer(adminUserId, adminTeacherId, "SUPER_ADMIN")))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT status FROM ocr_task WHERE id=?",
                String.class, staged.taskId())).isEqualTo("SUCCEEDED");
    }

    @Test
    void cancelledAndExpiredStagingFilesAreInaccessible() throws Exception {
        Staged cancelled = stagedTask(ownerTeacherId, "cancel.png", "image/png");
        mvc.perform(delete("/api/ocr/tasks/{id}", cancelled.taskId())
                        .header("Authorization", ownerBearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/files/{id}/download", cancelled.fileId())
                        .header("Authorization", ownerBearer()))
                .andExpect(status().isNotFound());

        Staged expired = stagedTask(ownerTeacherId, "expired.png", "image/png");
        jdbc.update("UPDATE ocr_task SET created_at=? WHERE id=?",
                LocalDateTime.now().minusDays(2), expired.taskId());
        ocr.cleanupExpiredCertificateStaging();
        mvc.perform(get("/api/files/{id}/download", expired.fileId())
                        .header("Authorization", ownerBearer()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT deleted FROM file_metadata WHERE id=?",
                Boolean.class, expired.fileId())).isTrue();
    }

    @Test
    void quickUploadAcceptsSupportedExtensionsAndRejectsUnsupportedExtension() throws Exception {
        for (UploadCase upload : List.of(
                new UploadCase("certificate.pdf", "application/pdf", validPdf()),
                new UploadCase("certificate.jpg", "image/jpeg", imageBytes("jpg")),
                new UploadCase("certificate.jpeg", "image/jpeg", imageBytes("jpeg")),
                new UploadCase("certificate.png", "image/png", imageBytes("png")),
                new UploadCase("certificate.webp", "image/webp", imageBytes("webp")))) {
            mvc.perform(multipart("/api/ocr/certificates/quick")
                            .file(new MockMultipartFile("file", upload.name(), upload.mime(), upload.bytes()))
                            .header("Authorization", ownerBearer()))
                    .andExpect(status().isOk());
        }
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM file_metadata WHERE teacher_id=?",
                Integer.class, ownerTeacherId);
        mvc.perform(multipart("/api/ocr/certificates/quick")
                        .file(new MockMultipartFile("file", "certificate.gif", "image/gif", imageBytes("gif")))
                        .header("Authorization", ownerBearer()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_metadata WHERE teacher_id=?",
                Integer.class, ownerTeacherId)).isEqualTo(before);
    }

    private Staged stagedTask(Long teacherId, String name, String mime) {
        FileMetadata file = files.uploadCertificateOcrStaging(teacherId,
                new MockMultipartFile("file", name, mime, imageBytes(name)));
        OcrTask task = new OcrTask();
        task.setTeacherId(teacherId);
        task.setFileId(file.getId());
        task.setTargetType(OcrTargetType.CERTIFICATE.name());
        task.setStatus("SUCCEEDED");
        task.setRecognitionMode("OCR");
        task.setResultCount(1);
        tasks.insert(task);
        OcrResult result = new OcrResult();
        result.setTaskId(task.getId());
        result.setTeacherId(teacherId);
        result.setTargetType(OcrTargetType.CERTIFICATE.name());
        result.setStatus("DRAFT");
        result.setCertificateName("合成测试证书");
        result.setIssuingAuthority("测试机构");
        results.insert(result);
        return new Staged(task.getId(), result.getId(), file.getId());
    }

    private Long createUser(String username, String role) {
        jdbc.update("""
                INSERT INTO `user` (username, email, password_hash, role, status, auth_version)
                VALUES (?, ?, 'unused-test-hash', ?, 'ACTIVE', 1)
                """, username, username + "@example.test", role);
        return jdbc.queryForObject("SELECT id FROM `user` WHERE username=?", Long.class, username);
    }

    private Long createTeacher(Long userId, String displayName) {
        jdbc.update("INSERT INTO teacher_profile (user_id, display_name) VALUES (?, ?)", userId, displayName);
        if (!"SUPER_ADMIN".equals(jdbc.queryForObject("SELECT role FROM `user` WHERE id=?", String.class, userId))) {
            jdbc.update("""
                    INSERT INTO academic_role_assignment (user_id, role_code, major_id)
                    VALUES (?, 'FULL_TIME_TEACHER', NULL)
                    """, userId);
        }
        return jdbc.queryForObject("SELECT id FROM teacher_profile WHERE user_id=?", Long.class, userId);
    }

    private String ownerBearer() {
        return bearer(ownerUserId, ownerTeacherId, "TEACHER");
    }

    private String bearer(Long userId, Long teacherId, String role) {
        return "Bearer " + jwt.generateToken(new CurrentUser(
                userId, teacherId, "test", "test@example.test", role, 1L));
    }

    private static byte[] validPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] imageBytes(String marker) {
        return ("synthetic-image-" + marker).getBytes(StandardCharsets.UTF_8);
    }

    private static Path createTestDataDirectory() {
        try {
            return Files.createTempDirectory("restore-test-ocr-staging-");
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private record Staged(Long taskId, Long resultId, Long fileId) { }
    private record UploadCase(String name, String mime, byte[] bytes) { }
}
