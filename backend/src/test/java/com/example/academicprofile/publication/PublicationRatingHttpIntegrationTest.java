package com.example.academicprofile.publication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "app.security.jwt-secret=synthetic-rating-test-signing-key-32-chars",
        "app.publication.ccf.enforce-expected-sha256=false"
})
@AutoConfigureMockMvc
class PublicationRatingHttpIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper json;
    @Autowired private CcfReferenceUploadParser ccfParser;
    @Autowired private CcfReferenceImporter ccfImporter;

    private Long ownerUserId;
    private Long ownerTeacherId;
    private Long otherUserId;
    private Long otherTeacherId;
    private Long otherPaperId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM publication_venue_reference");
        jdbc.update("DELETE FROM publication_venue_dataset");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        ownerUserId = user("rating-owner-" + suffix, "TEACHER");
        ownerTeacherId = teacher(ownerUserId, "Rating Owner");
        ccfImporter.importValidated(ccfParser.parse(new ByteArrayInputStream(
                CcfReferenceUploadParserTest.validJson().getBytes(StandardCharsets.UTF_8)), 2026),
                "4".repeat(64), ownerUserId);
        otherUserId = user("rating-other-" + suffix, "TEACHER");
        otherTeacherId = teacher(otherUserId, "Rating Other");
        jdbc.update("INSERT INTO paper (teacher_id, title, publication_name, venue_type) VALUES (?, ?, ?, 'JOURNAL')",
                otherTeacherId, "Other private paper", "ACM Transactions on Computer Systems");
        otherPaperId = jdbc.queryForObject("SELECT id FROM paper WHERE teacher_id=? ORDER BY id DESC LIMIT 1",
                Long.class, otherTeacherId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM resource_access_policy WHERE owner_teacher_id IN (?, ?)", ownerTeacherId, otherTeacherId);
        jdbc.update("DELETE FROM paper WHERE teacher_id IN (?, ?)", ownerTeacherId, otherTeacherId);
        jdbc.update("DELETE FROM academic_role_assignment WHERE user_id IN (?, ?)", ownerUserId, otherUserId);
        jdbc.update("DELETE FROM teacher_profile WHERE id IN (?, ?)", ownerTeacherId, otherTeacherId);
        jdbc.update("DELETE FROM admin_audit_log WHERE operator_user_id IN (?, ?)", ownerUserId, otherUserId);
        jdbc.update("DELETE FROM `user` WHERE id IN (?, ?)", ownerUserId, otherUserId);
        jdbc.update("DELETE FROM publication_venue_reference");
        jdbc.update("DELETE FROM publication_venue_dataset");
    }

    @Test
    void teacherCanSearchPinnedCcfDataAndSaveManualJournalMetrics() throws Exception {
        mvc.perform(get("/api/publication-venues/search")
                        .header("Authorization", bearer(ownerUserId, ownerTeacherId, "TEACHER"))
                        .param("query", "SJ").param("type", "JOURNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.items[0].ccfRank").value("A"))
                .andExpect(jsonPath("$.data.items[0].source").value("CCF_CUSTOMER_PROVIDED_2026"))
                .andExpect(jsonPath("$.data.items[0].datasetId").doesNotExist());

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("title", "Verified journal paper");
        payload.put("publicationName", "Synthetic Journal");
        payload.put("publicationType", "article");
        payload.put("venueType", "JOURNAL");
        payload.put("jcrDataYear", 2026);
        payload.put("journalImpactFactor", 2.5);
        payload.put("jcrQuartile", "Q2");
        payload.put("metricSourceDescription", "Manually verified by owner");
        mvc.perform(post("/api/teacher/papers")
                        .header("Authorization", bearer(ownerUserId, ownerTeacherId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherId").value(ownerTeacherId))
                .andExpect(jsonPath("$.data.metricStatus").value("MANUAL"));
    }

    @Test
    void conferenceCannotSmuggleJournalMetrics() throws Exception {
        mvc.perform(post("/api/teacher/papers")
                        .header("Authorization", bearer(ownerUserId, ownerTeacherId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "title", "Conference paper", "venueType", "CONFERENCE",
                                "jcrQuartile", "Q1", "journalImpactFactor", 1.0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingJournalToConferencePersistsNullForJournalAndCcfSnapshots() throws Exception {
        Long journalReferenceId = jdbc.queryForObject(
                "SELECT id FROM publication_venue_reference WHERE abbreviation='SJ' AND venue_type='JOURNAL' LIMIT 1",
                Long.class);
        Map<String, Object> journal = new java.util.HashMap<>();
        journal.put("title", "Journal converted to conference");
        journal.put("publicationName", "Synthetic Journal");
        journal.put("venueType", "JOURNAL");
        journal.put("ccfReferenceId", journalReferenceId);
        journal.put("ccfConfirmed", true);
        journal.put("issn", "1234-5678");
        journal.put("jcrDataYear", 2026);
        journal.put("journalImpactFactor", 2.5);
        journal.put("jcrQuartile", "Q2");
        mvc.perform(post("/api/teacher/papers")
                        .header("Authorization", bearer(ownerUserId, ownerTeacherId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(journal)))
                .andExpect(status().isOk());
        Long paperId = jdbc.queryForObject(
                "SELECT id FROM paper WHERE teacher_id=? AND title=?", Long.class,
                ownerTeacherId, "Journal converted to conference");

        mvc.perform(put("/api/teacher/papers/{id}", paperId)
                        .header("Authorization", bearer(ownerUserId, ownerTeacherId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "title", "Journal converted to conference",
                                "publicationName", "Synthetic Conference",
                                "venueType", "CONFERENCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venueType").value("CONFERENCE"))
                .andExpect(jsonPath("$.data.ccfReferenceId").doesNotExist())
                .andExpect(jsonPath("$.data.jcrQuartile").doesNotExist());

        Map<String, Object> persisted = jdbc.queryForMap(
                "SELECT venue_type, ccf_reference_id, ccf_rank, issn, jcr_data_year, journal_impact_factor,"
                        + " jcr_quartile, metric_status FROM paper WHERE id=?", paperId);
        org.assertj.core.api.Assertions.assertThat(persisted.get("venue_type")).isEqualTo("CONFERENCE");
        org.assertj.core.api.Assertions.assertThat(persisted)
                .containsEntry("ccf_reference_id", null)
                .containsEntry("ccf_rank", null)
                .containsEntry("issn", null)
                .containsEntry("jcr_data_year", null)
                .containsEntry("journal_impact_factor", null)
                .containsEntry("jcr_quartile", null)
                .containsEntry("metric_status", null);
    }

    @Test
    void batchConfirmationRechecksOwnershipAndLimit() throws Exception {
        Long referenceId = jdbc.queryForObject(
                "SELECT id FROM publication_venue_reference WHERE abbreviation='SJ' AND venue_type='JOURNAL' LIMIT 1",
                Long.class);
        mvc.perform(post("/api/teacher/papers/ratings/confirm")
                        .header("Authorization", bearer(ownerUserId, ownerTeacherId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("items", java.util.List.of(
                                Map.of("paperId", otherPaperId, "referenceId", referenceId))))))
                .andExpect(status().isBadRequest());
        Integer confirmed = jdbc.queryForObject("SELECT COUNT(*) FROM paper WHERE id=? AND ccf_confirmed_at IS NOT NULL",
                Integer.class, otherPaperId);
        org.assertj.core.api.Assertions.assertThat(confirmed).isZero();
    }

    @Test
    void superAdminCannotUseTeacherPaperMutationApi() throws Exception {
        jdbc.update("UPDATE `user` SET role='SUPER_ADMIN', auth_version=2 WHERE id=?", ownerUserId);
        mvc.perform(post("/api/teacher/papers")
                        .header("Authorization", bearer(ownerUserId, ownerTeacherId, "SUPER_ADMIN", 2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"forbidden\"}"))
                .andExpect(status().isForbidden());
    }

    private Long user(String username, String role) {
        jdbc.update("INSERT INTO `user` (username,email,password_hash,role,status,auth_version) VALUES (?,?,?,?,'ACTIVE',1)",
                username, username + "@example.test", "unused-test-hash", role);
        return jdbc.queryForObject("SELECT id FROM `user` WHERE username=?", Long.class, username);
    }

    private Long teacher(Long userId, String name) {
        jdbc.update("INSERT INTO teacher_profile (user_id,display_name) VALUES (?,?)", userId, name);
        jdbc.update("INSERT INTO academic_role_assignment (user_id,role_code,major_id) VALUES (?,'FULL_TIME_TEACHER',NULL)", userId);
        return jdbc.queryForObject("SELECT id FROM teacher_profile WHERE user_id=?", Long.class, userId);
    }

    private String bearer(Long userId, Long teacherId, String role) {
        return bearer(userId, teacherId, role, 1L);
    }

    private String bearer(Long userId, Long teacherId, String role, Long authVersion) {
        return "Bearer " + jwt.generateToken(new CurrentUser(
                userId, teacherId, "test", "test@example.test", role, authVersion));
    }
}
