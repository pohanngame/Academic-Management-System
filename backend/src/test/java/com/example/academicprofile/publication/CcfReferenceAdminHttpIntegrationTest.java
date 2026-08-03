package com.example.academicprofile.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;

import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.security.JwtService;

@SpringBootTest(properties = {
        "app.security.jwt-secret=synthetic-ccf-admin-signing-key-32-chars",
        "app.publication.ccf.enforce-expected-sha256=false"
})
@AutoConfigureMockMvc
class CcfReferenceAdminHttpIntegrationTest {
    private static final Path TEMP_ROOT = temporaryRoot();
    private static final String ADMIN_PASSWORD = "synthetic-current-password";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.files.temp-dir", () -> TEMP_ROOT.toString());
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long adminUserId;
    private Long adminTeacherId;
    private Long teacherUserId;
    private Long teacherId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM publication_venue_reference");
        jdbc.update("DELETE FROM publication_venue_dataset");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        adminUserId = user("ccf-admin-" + suffix, "SUPER_ADMIN", ADMIN_PASSWORD);
        adminTeacherId = teacher(adminUserId, "CCF Admin");
        teacherUserId = user("ccf-teacher-" + suffix, "TEACHER", "synthetic-teacher-password");
        teacherId = teacher(teacherUserId, "CCF Teacher");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM paper WHERE teacher_id IN (?, ?)", adminTeacherId, teacherId);
        jdbc.update("DELETE FROM publication_venue_reference");
        jdbc.update("DELETE FROM publication_venue_dataset");
        jdbc.update("DELETE FROM admin_audit_log WHERE operator_user_id IN (?, ?)", adminUserId, teacherUserId);
        jdbc.update("DELETE FROM academic_role_assignment WHERE user_id IN (?, ?)", adminUserId, teacherUserId);
        jdbc.update("DELETE FROM teacher_profile WHERE id IN (?, ?)", adminTeacherId, teacherId);
        jdbc.update("DELETE FROM `user` WHERE id IN (?, ?)", adminUserId, teacherUserId);
    }

    @AfterAll
    static void removeTempRoot() {
        FileSystemUtils.deleteRecursively(TEMP_ROOT.toFile());
    }

    @Test
    void adminEndpointsRequireAuthenticationAndSuperAdminRole() throws Exception {
        mvc.perform(get("/api/admin/reference-data/ccf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reference-data/ccf").header("Authorization", teacherBearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reference-data/ccf").header("Authorization", adminBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configured").value(false));
    }

    @Test
    void wrongPasswordAndInvalidFilesLeaveNoDatasetOrTemporaryFile() throws Exception {
        mvc.perform(multipart("/api/admin/reference-data/ccf/import")
                        .file(jsonFile("ccf.json", validJson()))
                        .param("currentPassword", "wrong-synthetic-password")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/reference-data/ccf/import")
                        .file(jsonFile("ccf.txt", validJson()))
                        .param("currentPassword", ADMIN_PASSWORD)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/reference-data/ccf/import")
                        .file(new MockMultipartFile("file", "empty.json", MediaType.APPLICATION_JSON_VALUE, new byte[0]))
                        .param("currentPassword", ADMIN_PASSWORD)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publication_venue_dataset", Integer.class)).isZero();
        assertThat(temporaryFiles()).isZero();
    }

    @Test
    void oversizedAndStructurallyInvalidJsonAreRejectedWithoutReplacingOldVersion() throws Exception {
        importValid(validJson());
        String oldHash = jdbc.queryForObject("SELECT source_sha256 FROM publication_venue_dataset LIMIT 1", String.class);
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        mvc.perform(multipart("/api/admin/reference-data/ccf/import")
                        .file(new MockMultipartFile("file", "large.json", MediaType.APPLICATION_JSON_VALUE, tooLarge))
                        .param("currentPassword", ADMIN_PASSWORD).header("Authorization", adminBearer()))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/admin/reference-data/ccf/import")
                        .file(jsonFile("invalid.json", validJson().replace("\"rank\": \"A\"", "\"rank\": \"D\"")))
                        .param("currentPassword", ADMIN_PASSWORD).header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/reference-data/ccf/import")
                        .file(jsonFile("missing-category.json", validJson().replace("\"category_id\": 1", "\"category_id\": 9")))
                        .param("currentPassword", ADMIN_PASSWORD).header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT source_sha256 FROM publication_venue_dataset LIMIT 1", String.class))
                .isEqualTo(oldHash);
        assertThat(temporaryFiles()).isZero();
    }

    @Test
    void validImportReportsHashCountsIsIdempotentAndImmediatelyEnablesTeacherSearch() throws Exception {
        byte[] bytes = validJson().getBytes(StandardCharsets.UTF_8);
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        mvc.perform(importRequest(bytes)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(true))
                .andExpect(jsonPath("$.data.sha256").value(expectedHash))
                .andExpect(jsonPath("$.data.categoryCount").value(1))
                .andExpect(jsonPath("$.data.journalCount").value(1))
                .andExpect(jsonPath("$.data.conferenceCount").value(1));
        mvc.perform(importRequest(bytes)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(false));
        mvc.perform(get("/api/publication-venues/search")
                        .header("Authorization", teacherBearer())
                        .param("query", "SJ").param("type", "JOURNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.items[0].standardName").value("Synthetic Journal"));
        mvc.perform(get("/api/admin/reference-data/ccf").header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sha256").value(expectedHash))
                .andExpect(jsonPath("$.data.importedByUserId").value(adminUserId));
        String auditSummary = jdbc.queryForObject(
                "SELECT summary FROM admin_audit_log WHERE operator_user_id=? AND action_type='CCF_REFERENCE_IMPORT' AND success=1 ORDER BY id DESC LIMIT 1",
                String.class, adminUserId);
        assertThat(auditSummary).doesNotContain(ADMIN_PASSWORD, "Synthetic Journal", "https://invalid.example");
        assertThat(temporaryFiles()).isZero();
    }

    @Test
    void noDatasetReturnsExplicitSafeStateAndReplacementDoesNotChangePaperSnapshot() throws Exception {
        mvc.perform(get("/api/publication-venues/search")
                        .header("Authorization", teacherBearer())
                        .param("query", "SJ").param("type", "JOURNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.message").value("管理员尚未配置CCF参考数据"))
                .andExpect(jsonPath("$.data.items").isEmpty());
        importValid(validJson());
        jdbc.update("INSERT INTO paper (teacher_id,title,publication_name,venue_type,ccf_rank,ccf_category_id,ccf_category_name,ccf_data_year,ccf_source,ccf_match_method,ccf_confirmed_at) VALUES (?,?,?,'JOURNAL','A',1,'人工智能',2026,'CCF_CUSTOMER_PROVIDED_2026','EXACT_NAME',NOW())",
                teacherId, "Snapshot paper", "Synthetic Journal");
        importValid(validJson().replace("Synthetic Journal", "Replacement Journal"));
        assertThat(jdbc.queryForObject("SELECT ccf_rank FROM paper WHERE teacher_id=?", String.class, teacherId))
                .isEqualTo("A");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publication_venue_reference WHERE standard_name='Replacement Journal'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void bundledRealSnapshotIsAbsentFromRuntimeClasspath() {
        assertThat(getClass().getClassLoader().getResource("reference/ccf/2026/CCF_Ranking_2026.json")).isNull();
        assertThat(getClass().getClassLoader().getResource("reference/ccf/2026/ccf-reference.schema.json")).isNotNull();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder importRequest(byte[] bytes) {
        return multipart("/api/admin/reference-data/ccf/import")
                .file(new MockMultipartFile("file", "ccf.json", MediaType.APPLICATION_JSON_VALUE, bytes))
                .param("currentPassword", ADMIN_PASSWORD).header("Authorization", adminBearer());
    }

    private void importValid(String json) throws Exception {
        mvc.perform(importRequest(json.getBytes(StandardCharsets.UTF_8))).andExpect(status().isOk());
    }

    private MockMultipartFile jsonFile(String name, String value) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_JSON_VALUE,
                value.getBytes(StandardCharsets.UTF_8));
    }

    private long temporaryFiles() throws Exception {
        if (!Files.exists(TEMP_ROOT)) return 0;
        try (var stream = Files.walk(TEMP_ROOT)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }

    private Long user(String username, String role, String password) {
        jdbc.update("INSERT INTO `user` (username,email,password_hash,role,status,auth_version) VALUES (?,?,?,?, 'ACTIVE',1)",
                username, username + "@example.test", passwordEncoder.encode(password), role);
        return jdbc.queryForObject("SELECT id FROM `user` WHERE username=?", Long.class, username);
    }

    private Long teacher(Long userId, String name) {
        jdbc.update("INSERT INTO teacher_profile (user_id,display_name) VALUES (?,?)", userId, name);
        jdbc.update("INSERT INTO academic_role_assignment (user_id,role_code,major_id) VALUES (?,'FULL_TIME_TEACHER',NULL)", userId);
        return jdbc.queryForObject("SELECT id FROM teacher_profile WHERE user_id=?", Long.class, userId);
    }

    private String adminBearer() { return bearer(adminUserId, adminTeacherId, "SUPER_ADMIN"); }
    private String teacherBearer() { return bearer(teacherUserId, teacherId, "TEACHER"); }
    private String bearer(Long userId, Long currentTeacherId, String role) {
        return "Bearer " + jwt.generateToken(new CurrentUser(userId, currentTeacherId,
                "test", "test@example.test", role, 1L));
    }

    private String validJson() { return CcfReferenceUploadParserTest.validJson(); }

    private static Path temporaryRoot() {
        try { return Files.createTempDirectory("ccf-reference-admin-test-"); }
        catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
}
