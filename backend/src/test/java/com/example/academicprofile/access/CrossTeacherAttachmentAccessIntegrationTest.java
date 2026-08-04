package com.example.academicprofile.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.academicprofile.ai.AiChatClient;
import com.example.academicprofile.ai.AiRuntimeConfig;
import com.example.academicprofile.ai.AiSettingsService;
import com.example.academicprofile.file.FileBusinessType;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileStorageService;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CrossTeacherAttachmentAccessIntegrationTest {
    private static final Path testData = createTestDataDirectory();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FileStorageService files;
    @Autowired
    private JwtService jwt;
    @Autowired
    private ObjectMapper json;
    @MockBean
    private AiSettingsService aiSettings;
    @MockBean
    private AiChatClient aiChatClient;

    private Long ownerUserId;
    private Long ownerTeacherId;
    private Long requesterUserId;
    private Long requesterTeacherId;
    private Long adminUserId;
    private Long adminTeacherId;
    private Long paperId;
    private Long firstFileId;
    private Long secondFileId;
    private Long majorA;
    private Long majorB;
    private final java.util.List<Long> scopeUserIds = new java.util.ArrayList<>();
    private final java.util.List<Long> scopeTeacherIds = new java.util.ArrayList<>();

    private static Path createTestDataDirectory() {
        try {
            return Files.createTempDirectory("restore-test-cross-teacher-");
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void isolatedFiles(DynamicPropertyRegistry properties) {
        properties.add("app.files.upload-dir", () -> testData.resolve("uploads").toString());
        properties.add("app.files.encryption-key-file",
                () -> testData.resolve("secrets/file-encryption.key").toString());
        properties.add("app.security.jwt-secret",
                () -> "integration-test-signing-key-32-chars");
    }

    @AfterAll
    static void removeTestDataDirectory() throws Exception {
        if (testData == null || !testData.getFileName().toString().startsWith("restore-test-")) {
            return;
        }
        try (var paths = Files.walk(testData)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        ownerUserId = user("owner-" + suffix, "TEACHER");
        ownerTeacherId = teacher(ownerUserId, "Owner");
        requesterUserId = user("requester-" + suffix, "TEACHER");
        requesterTeacherId = teacher(requesterUserId, "Requester");
        adminUserId = user("admin-" + suffix, "SUPER_ADMIN");
        adminTeacherId = teacher(adminUserId, "Admin");
        majorA = major("A-" + suffix, "Major A " + suffix);
        majorB = major("B-" + suffix, "Major B " + suffix);
        jdbc.update("INSERT INTO teacher_major (teacher_id, major_id) VALUES (?, ?)",
                ownerTeacherId, majorA);
        jdbc.update("INSERT INTO paper (teacher_id, title) VALUES (?, 'Synthetic parent')",
                ownerTeacherId);
        paperId = jdbc.queryForObject(
                "SELECT id FROM paper WHERE teacher_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, ownerTeacherId);
        jdbc.update("""
                INSERT INTO resource_access_policy
                    (owner_teacher_id, resource_type, resource_id,
                     view_allowed, download_allowed, ai_use_allowed)
                VALUES (?, 'PAPER', ?, 1, 0, 0)
                """, ownerTeacherId, paperId);
        firstFileId = upload("first.pdf", "first attachment").getId();
        secondFileId = upload("second.pdf", "second attachment").getId();
        jdbc.update("""
                UPDATE resource_access_policy
                SET download_allowed = 1, ai_use_allowed = 1
                WHERE resource_type = 'FILE_ATTACHMENT' AND resource_id IN (?, ?)
                """, firstFileId, secondFileId);
        grantDownload(firstFileId);
        when(aiSettings.requireRuntimeConfig(requesterTeacherId)).thenReturn(
                new AiRuntimeConfig("local-mock", "https://203.0.113.10/v1", "mock-model", "redacted-test-value"));
        when(aiChatClient.generate(any(String.class), any(AiRuntimeConfig.class)))
                .thenReturn("synthetic generated result");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM ai_generation_result WHERE teacher_id IN (?, ?, ?)",
                ownerTeacherId, requesterTeacherId, adminTeacherId);
        jdbc.update("DELETE FROM ai_generation_task WHERE teacher_id IN (?, ?, ?)",
                ownerTeacherId, requesterTeacherId, adminTeacherId);
        jdbc.update("DELETE FROM ocr_result WHERE teacher_id = ?", ownerTeacherId);
        jdbc.update("DELETE FROM ocr_task WHERE teacher_id = ?", ownerTeacherId);
        jdbc.update("DELETE FROM resource_access_audit WHERE owner_teacher_id IN (?, ?, ?)",
                ownerTeacherId, requesterTeacherId, adminTeacherId);
        jdbc.update("DELETE FROM resource_access_grant WHERE owner_teacher_id = ?", ownerTeacherId);
        jdbc.update("DELETE FROM resource_access_request_item WHERE request_id IN "
                + "(SELECT id FROM resource_access_request WHERE owner_teacher_id = ?)", ownerTeacherId);
        jdbc.update("DELETE FROM resource_access_request WHERE owner_teacher_id = ?", ownerTeacherId);
        jdbc.update("DELETE FROM resource_access_policy WHERE owner_teacher_id = ?", ownerTeacherId);
        jdbc.update("DELETE FROM file_metadata WHERE teacher_id = ?", ownerTeacherId);
        jdbc.update("DELETE FROM paper WHERE teacher_id = ?", ownerTeacherId);
        jdbc.update("DELETE FROM teacher_major WHERE teacher_id IN (?, ?, ?)",
                ownerTeacherId, requesterTeacherId, adminTeacherId);
        if (!scopeTeacherIds.isEmpty()) {
            String ids = scopeTeacherIds.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            jdbc.update("DELETE FROM resource_access_audit WHERE owner_teacher_id IN (" + ids
                    + ") OR actor_teacher_id IN (" + ids + ")");
            jdbc.update("DELETE FROM resource_access_grant WHERE owner_teacher_id IN (" + ids
                    + ") OR requester_teacher_id IN (" + ids + ")");
            jdbc.update("DELETE FROM resource_access_request_item WHERE request_id IN "
                    + "(SELECT id FROM resource_access_request WHERE owner_teacher_id IN (" + ids
                    + ") OR requester_teacher_id IN (" + ids + "))");
            jdbc.update("DELETE FROM resource_access_request WHERE owner_teacher_id IN (" + ids
                    + ") OR requester_teacher_id IN (" + ids + ")");
            jdbc.update("DELETE FROM teacher_major WHERE teacher_id IN (" + ids + ")");
        }
        java.util.List<Long> allUsers = new java.util.ArrayList<>(
                java.util.List.of(ownerUserId, requesterUserId, adminUserId));
        allUsers.addAll(scopeUserIds);
        for (Long userId : allUsers) {
            jdbc.update("DELETE FROM academic_role_assignment WHERE user_id = ?", userId);
        }
        if (!scopeTeacherIds.isEmpty()) {
            String ids = scopeTeacherIds.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            jdbc.update("DELETE FROM teacher_profile WHERE id IN (" + ids + ")");
        }
        jdbc.update("DELETE FROM teacher_profile WHERE id IN (?, ?, ?)",
                ownerTeacherId, requesterTeacherId, adminTeacherId);
        for (Long userId : allUsers) {
            jdbc.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        jdbc.update("DELETE FROM major WHERE id IN (?, ?)", majorA, majorB);
        scopeUserIds.clear();
        scopeTeacherIds.clear();
    }

    @Test
    void attachmentGrantIsExactAndDoesNotGrantPreviewOrSiblingFile() throws Exception {
        MvcResult started = mvc.perform(get("/api/files/{id}/download", firstFileId)
                        .header("Authorization", bearer(requesterUserId, requesterTeacherId, "TEACHER")))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().bytes("first attachment".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/files/{id}/preview", firstFileId)
                        .header("Authorization", bearer(requesterUserId, requesterTeacherId, "TEACHER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/files/{id}/download", secondFileId)
                        .header("Authorization", bearer(requesterUserId, requesterTeacherId, "TEACHER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthorizedLegacyAttachmentDoesNotLeakMigrationState() throws Exception {
        jdbc.update("UPDATE file_metadata SET ciphertext_status = 'PLAINTEXT' WHERE id = ?", secondFileId);

        mvc.perform(get("/api/files/{id}/download", secondFileId)
                        .header("Authorization", bearer(requesterUserId, requesterTeacherId, "TEACHER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void superAdminCannotUseBusinessRolePathToReadPrivateAttachment() throws Exception {
        mvc.perform(get("/api/files/{id}/preview", firstFileId)
                .header("Authorization", bearer(adminUserId, adminTeacherId, "SUPER_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sharedAttachmentDetailUsesStrictSafeFieldWhitelist() throws Exception {
        Actor leader = scopedActor("attachment-detail-leader", "MAJOR_LEADER", majorA);

        mvc.perform(get("/api/access/resources/FILE_ATTACHMENT/{id}", firstFileId)
                        .header("Authorization", bearer(
                                leader.userId(), leader.teacherId(), "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstFileId))
                .andExpect(jsonPath("$.data.originalName").value("first.pdf"))
                .andExpect(jsonPath("$.data.fileExt").value("pdf"))
                .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.data.fileSize").isNumber())
                .andExpect(jsonPath("$.data.businessType").value("PAPER"))
                .andExpect(jsonPath("$.data.businessId").value(paperId))
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.storedName").doesNotExist())
                .andExpect(jsonPath("$.data.wrappedDek").doesNotExist())
                .andExpect(jsonPath("$.data.encryptionKeyId").doesNotExist())
                .andExpect(jsonPath("$.data.encryptionVersion").doesNotExist())
                .andExpect(jsonPath("$.data.plaintextSha256").doesNotExist())
                .andExpect(jsonPath("$.data.legacyStoragePath").doesNotExist())
                .andExpect(jsonPath("$.data.ciphertextStatus").doesNotExist())
                .andExpect(jsonPath("$.data.teacherId").doesNotExist());

        mvc.perform(get("/api/access/resources/FILE_ATTACHMENT/{id}", firstFileId)
                        .header("Authorization", bearer(
                                adminUserId, adminTeacherId, "SUPER_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownedAttachmentPoliciesCanPageBeyondTheFirstResult() throws Exception {
        mvc.perform(get("/api/files/attachments/page")
                        .param("page", "1")
                        .param("size", "1")
                        .header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1));
        mvc.perform(get("/api/files/attachments/page")
                        .param("page", "2")
                        .param("size", "1")
                        .header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    @Test
    void managementScopeRequiresActiveCurrentMajorAndHonorsHierarchy() throws Exception {
        Actor sameLeader = scopedActor("same-leader", "MAJOR_LEADER", majorA);
        Actor otherLeader = scopedActor("other-leader", "MAJOR_LEADER", majorB);
        Actor viceDean = scopedActor("vice-dean", "VICE_DEAN", null);
        Actor dean = scopedActor("dean", "DEAN", null);
        Actor secretary = scopedActor("secretary", "PARTY_SECRETARY", null);
        Actor multiRole = scopedActor("multi-role", "MAJOR_LEADER", majorB);
        jdbc.update("""
                INSERT INTO academic_role_assignment (user_id, role_code, major_id)
                VALUES (?, 'VICE_DEAN', NULL)
                """, multiRole.userId());

        expectPaperVisible(sameLeader);
        expectPaperVisible(viceDean);
        expectPaperVisible(dean);
        expectPaperVisible(secretary);
        expectPaperVisible(multiRole);
        expectPaperHidden(otherLeader);
        Integer listAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM resource_access_audit WHERE operation_type = 'LIST'",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(listAudits).isZero();

        jdbc.update("UPDATE major SET active = 0 WHERE id = ?", majorA);
        expectPaperHidden(sameLeader);
        jdbc.update("UPDATE major SET active = 1 WHERE id = ?", majorA);

        jdbc.update("UPDATE teacher_major SET major_id = ? WHERE teacher_id = ?",
                majorB, ownerTeacherId);
        expectPaperHidden(sameLeader);
        jdbc.update("UPDATE teacher_major SET major_id = ? WHERE teacher_id = ?",
                majorA, ownerTeacherId);

        jdbc.update("""
                UPDATE resource_access_policy SET view_allowed = 0
                WHERE resource_type = 'PAPER' AND resource_id = ?
                """, paperId);
        expectPaperFilteredByOwnerPolicy(dean);
        expectPaperFilteredByOwnerPolicy(multiRole);
    }

    @Test
    void capabilitiesAndManagedDirectoryAreServerFiltered() throws Exception {
        Actor ordinary = scopedActor("ordinary-capability", null, null);
        Actor leader = scopedActor("leader-capability", "MAJOR_LEADER", majorA);

        mvc.perform(get("/api/access/capabilities")
                        .header("Authorization", bearer(
                                ordinary.userId(), ordinary.teacherId(), "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canBatchRequest").value(false))
                .andExpect(jsonPath("$.data.canBrowseManagedScope").value(false));
        mvc.perform(get("/api/access/directory")
                        .param("scope", "MANAGED")
                        .header("Authorization", bearer(
                                ordinary.userId(), ordinary.teacherId(), "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mvc.perform(get("/api/access/capabilities")
                        .header("Authorization", bearer(
                                leader.userId(), leader.teacherId(), "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessRoles").isArray())
                .andExpect(jsonPath("$.data.managedMajorIds[0]").value(majorA))
                .andExpect(jsonPath("$.data.canBatchRequest").value(true))
                .andExpect(jsonPath("$.data.canBrowseManagedScope").value(true));
        mvc.perform(get("/api/access/directory")
                        .param("scope", "MANAGED")
                        .header("Authorization", bearer(
                                leader.userId(), leader.teacherId(), "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].teacherId").value(ownerTeacherId))
                .andExpect(jsonPath("$.data.items[0].majorName").exists())
                .andExpect(jsonPath("$.data.items[0].roleCodes").exists());

        jdbc.update("UPDATE major SET active = 0 WHERE id = ?", majorA);
        mvc.perform(get("/api/access/directory")
                        .param("scope", "MANAGED")
                        .header("Authorization", bearer(
                                leader.userId(), leader.teacherId(), "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        jdbc.update("UPDATE major SET active = 1 WHERE id = ?", majorA);

        mvc.perform(get("/api/access/capabilities")
                        .header("Authorization", bearer(
                                adminUserId, adminTeacherId, "SUPER_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void oldJwtImmediatelyFailsAfterOrganizationVersionChange() throws Exception {
        Actor dean = scopedActor("versioned-dean", "DEAN", null);
        String oldToken = bearer(dean.userId(), dean.teacherId(), "TEACHER");
        jdbc.update("UPDATE `user` SET auth_version = auth_version + 1 WHERE id = ?", dean.userId());

        mvc.perform(get("/api/access/resources")
                        .param("ownerTeacherId", ownerTeacherId.toString())
                        .param("resourceType", "PAPER")
                        .header("Authorization", oldToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void httpRequestApprovalUsesOnlyExplicitResourcesAndSupportsRevocationAndExpiry() throws Exception {
        clearWorkflow();

        long viewRequest = createRequest(
                requester(), ownerTeacherId, "PAPER", "VIEW",
                refs(ref("PAPER", paperId)));
        mvc.perform(get("/api/access/requests/pending")
                        .header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(viewRequest));
        decide(owner(), viewRequest, true, refs(ref("PAPER", paperId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/access/resources/PAPER/{id}", paperId)
                        .header("Authorization", requester()))
                .andExpect(status().isOk());

        long downloadRequest = createRequest(
                requester(), ownerTeacherId, "PAPER", "DOWNLOAD",
                refs(ref("FILE_ATTACHMENT", firstFileId), ref("FILE_ATTACHMENT", secondFileId)));
        decide(owner(), downloadRequest, true, refs(ref("FILE_ATTACHMENT", firstFileId)))
                .andExpect(status().isOk());

        setParentDownloadAllowed(true);
        long parentDownloadRequest = createRequest(
                requester(), ownerTeacherId, "PAPER", "DOWNLOAD",
                refs(ref("PAPER", paperId)));
        decide(owner(), parentDownloadRequest, true, refs(ref("PAPER", paperId)))
                .andExpect(status().isOk());

        download(requester(), firstFileId).andExpect(status().isOk());
        download(requester(), firstFileId).andExpect(status().isOk());
        mvc.perform(get("/api/files/{id}/download", secondFileId)
                        .header("Authorization", requester()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/files/{id}/preview", firstFileId)
                        .header("Authorization", requester()))
                .andExpect(status().isNotFound());

        Long grantId = jdbc.queryForObject("""
                SELECT id FROM resource_access_grant
                WHERE request_id = ? AND resource_type = 'FILE_ATTACHMENT'
                """, Long.class, downloadRequest);
        mvc.perform(delete("/api/access/grants/{id}", grantId)
                        .header("Authorization", owner()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/files/{id}/download", firstFileId)
                        .header("Authorization", requester()))
                .andExpect(status().isNotFound());

        long expiringRequest = createRequest(
                requester(), ownerTeacherId, "PAPER", "DOWNLOAD",
                refs(ref("FILE_ATTACHMENT", firstFileId)));
        decide(owner(), expiringRequest, true, refs(ref("FILE_ATTACHMENT", firstFileId)))
                .andExpect(status().isOk());
        jdbc.update("UPDATE resource_access_grant SET expires_at = ? WHERE request_id = ?",
                LocalDateTime.now().minusMinutes(1), expiringRequest);
        mvc.perform(get("/api/files/{id}/download", firstFileId)
                        .header("Authorization", requester()))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvalCandidatesAreLoadedOnceAndFilteredByOwnerPolicy() throws Exception {
        clearWorkflow();
        updateAttachmentPolicy(secondFileId, true, true, false);
        long requestId = createRequest(
                requester(), ownerTeacherId, "PAPER", "AI_USE", List.of());

        mvc.perform(get("/api/access/requests/{id}/candidates", requestId)
                        .header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.resourceType == 'FILE_ATTACHMENT' && "
                        + "@.resourceId == " + firstFileId + ")].allowed").value(true))
                .andExpect(jsonPath("$.data[?(@.resourceType == 'FILE_ATTACHMENT' && "
                        + "@.resourceId == " + secondFileId + ")].allowed").value(false))
                .andExpect(jsonPath("$.data[?(@.resourceType == 'FILE_ATTACHMENT' && "
                        + "@.resourceId == " + secondFileId + ")].unavailableReason")
                        .value("所有人尚未开放该权限"))
                .andExpect(jsonPath("$.data[?(@.resourceType == 'FILE_ATTACHMENT' && "
                        + "@.resourceId == " + firstFileId + ")].displayName")
                        .value("first.pdf"));

        mvc.perform(get("/api/access/requests/{id}/candidates", requestId)
                        .header("Authorization", requester()))
                .andExpect(status().isNotFound());
    }

    @Test
    void attachmentPolicyAndParentViewImmediatelyInvalidateExistingGrants() throws Exception {
        clearWorkflow();
        long downloadRequest = createAndApproveAttachment("DOWNLOAD", firstFileId);

        updateAttachmentPolicy(firstFileId, true, false, true);
        mvc.perform(get("/api/files/{id}/download", firstFileId)
                        .header("Authorization", requester()))
                .andExpect(status().isNotFound());
        assertRevoked(downloadRequest);

        updateAttachmentPolicy(firstFileId, true, true, true);
        long aiRequest = createAndApproveAttachment("AI_USE", firstFileId);
        updateAttachmentPolicy(firstFileId, true, true, false);
        assertRevoked(aiRequest);

        updateAttachmentPolicy(firstFileId, true, true, true);
        long viewRequest = createAndApproveAttachment("VIEW", firstFileId);
        updateParentPolicy(false);
        mvc.perform(get("/api/files/{id}/preview", firstFileId)
                        .header("Authorization", requester()))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(viewRequest).isPositive();
    }

    @Test
    void aiHttpUsesOnlyConfirmedExplicitlyAuthorizedAttachmentAndRecordsSourceAndAudit() throws Exception {
        clearWorkflow();
        insertConfirmedOcr(firstFileId, "selected confirmed OCR marker");
        insertConfirmedOcr(secondFileId, "unselected OCR marker");
        long requestId = createAndApproveAttachment("AI_USE", firstFileId);

        mvc.perform(post("/api/ai/tasks")
                        .header("Authorization", requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(java.util.Map.of(
                                "templateRequirement", "Produce a short test result",
                                "selectedModules", List.of("teacherProfile"),
                                "selectedAttachmentIds", List.of(firstFileId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        verify(aiChatClient, atLeastOnce()).generate(
                org.mockito.ArgumentMatchers.contains("selected confirmed OCR marker"),
                any(AiRuntimeConfig.class));
        verify(aiChatClient, never()).generate(
                org.mockito.ArgumentMatchers.contains("unselected OCR marker"),
                any(AiRuntimeConfig.class));

        String manifest = jdbc.queryForObject("""
                SELECT source_manifest FROM ai_generation_task
                WHERE teacher_id = ? ORDER BY id DESC LIMIT 1
                """, String.class, requesterTeacherId);
        JsonNode source = json.readTree(manifest).get(0);
        org.assertj.core.api.Assertions.assertThat(source.get("teacherId").asLong()).isEqualTo(ownerTeacherId);
        org.assertj.core.api.Assertions.assertThat(source.get("resourceId").asLong()).isEqualTo(paperId);
        org.assertj.core.api.Assertions.assertThat(source.get("fileId").asLong()).isEqualTo(firstFileId);
        org.assertj.core.api.Assertions.assertThat(source.get("grantId").asLong()).isPositive();
        org.assertj.core.api.Assertions.assertThat(source.get("provider").asText()).isEqualTo("local-mock");
        org.assertj.core.api.Assertions.assertThat(source.get("modelName").asText()).isEqualTo("mock-model");
        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM resource_access_audit
                WHERE actor_teacher_id = ? AND owner_teacher_id = ?
                  AND operation_type = 'AI_USE' AND file_id = ? AND success = 1
                """, Integer.class, requesterTeacherId, ownerTeacherId, firstFileId);
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);

        Long grantId = jdbc.queryForObject(
                "SELECT id FROM resource_access_grant WHERE request_id = ?",
                Long.class, requestId);
        mvc.perform(delete("/api/access/grants/{id}", grantId).header("Authorization", owner()))
                .andExpect(status().isOk());
        clearInvocations(aiChatClient);
        mvc.perform(post("/api/ai/tasks")
                        .header("Authorization", requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(java.util.Map.of(
                                "templateRequirement", "Must be denied",
                                "selectedModules", List.of("teacherProfile"),
                                "selectedAttachmentIds", List.of(firstFileId)))))
                .andExpect(status().isNotFound());
        verify(aiChatClient, never()).generate(any(String.class), any(AiRuntimeConfig.class));
    }

    @Test
    void aiHttpRejectsDownloadOnlyExpiredClosedAndUnconfirmedAttachmentsBeforeProviderCall() throws Exception {
        clearWorkflow();
        insertConfirmedOcr(firstFileId, "confirmed text");
        createAndApproveAttachment("DOWNLOAD", firstFileId);

        assertAiDeniedBeforeProvider(firstFileId, status().isNotFound());

        clearWorkflow();
        long aiRequest = createAndApproveAttachment("AI_USE", firstFileId);
        jdbc.update("UPDATE resource_access_grant SET expires_at = ? WHERE request_id = ?",
                LocalDateTime.now().minusMinutes(1), aiRequest);
        assertAiDeniedBeforeProvider(firstFileId, status().isNotFound());

        clearWorkflow();
        createAndApproveAttachment("AI_USE", firstFileId);
        jdbc.update("DELETE FROM ocr_task WHERE file_id = ?", firstFileId);
        assertAiDeniedBeforeProvider(firstFileId, status().isBadRequest());

        insertConfirmedOcr(firstFileId, "confirmed text");
        updateAttachmentPolicy(firstFileId, true, true, false);
        assertAiDeniedBeforeProvider(firstFileId, status().isNotFound());
    }

    @Test
    void batchRequestScopeAndConcurrentDecisionAreEnforcedByHttp() throws Exception {
        clearWorkflow();
        Actor sameMajorOwner = scopedActor("same-owner", null, null);
        jdbc.update("INSERT INTO teacher_major (teacher_id, major_id) VALUES (?, ?)",
                sameMajorOwner.teacherId(), majorA);
        Actor otherMajorOwner = scopedActor("other-owner", null, null);
        jdbc.update("INSERT INTO teacher_major (teacher_id, major_id) VALUES (?, ?)",
                otherMajorOwner.teacherId(), majorB);
        Actor leader = scopedActor("batch-leader", "MAJOR_LEADER", majorA);

        mvc.perform(post("/api/access/requests")
                        .header("Authorization", bearer(leader.userId(), leader.teacherId(), "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(createBody(
                                List.of(ownerTeacherId, sameMajorOwner.teacherId()),
                                "PAPER", "VIEW", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mvc.perform(post("/api/access/requests")
                        .header("Authorization", requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(createBody(
                                List.of(ownerTeacherId, sameMajorOwner.teacherId()),
                                "PAPER", "VIEW", List.of()))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/access/requests")
                        .header("Authorization", bearer(leader.userId(), leader.teacherId(), "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(createBody(
                                List.of(ownerTeacherId, otherMajorOwner.teacherId()),
                                "PAPER", "VIEW", List.of()))))
                .andExpect(status().isForbidden());

        long requestId = createRequest(
                requester(), ownerTeacherId, "PAPER", "VIEW", refs(ref("PAPER", paperId)));
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> approval = () -> mvc.perform(patch("/api/access/requests/{id}/decision", requestId)
                            .header("Authorization", owner())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(java.util.Map.of(
                                    "approve", true,
                                    "approvedResources", refs(ref("PAPER", paperId))))))
                    .andReturn().getResponse().getStatus();
            List<Integer> statuses = pool.invokeAll(List.of(approval, approval)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception ex) {
                            throw new AssertionError(ex);
                        }
                    }).toList();
            org.assertj.core.api.Assertions.assertThat(statuses)
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        Integer grants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM resource_access_grant WHERE request_id = ?",
                Integer.class, requestId);
        org.assertj.core.api.Assertions.assertThat(grants).isEqualTo(1);
    }

    @Test
    void forgedIdentityFieldsAndForeignAttachmentCannotChangeAuthenticatedRequesterOrOwner() throws Exception {
        clearWorkflow();
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>(
                createBody(List.of(ownerTeacherId), "PAPER", "VIEW", refs(ref("PAPER", paperId))));
        body.put("requesterTeacherId", ownerTeacherId);
        body.put("ownerTeacherId", requesterTeacherId);
        body.put("role", "DEAN");
        MvcResult result = mvc.perform(post("/api/access/requests")
                        .header("Authorization", requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requesterTeacherId").value(requesterTeacherId))
                .andExpect(jsonPath("$.data[0].ownerTeacherId").value(ownerTeacherId))
                .andReturn();
        long requestId = json.readTree(result.getResponse().getContentAsByteArray())
                .path("data").get(0).path("id").asLong();
        decide(requester(), requestId, true, refs(ref("PAPER", paperId)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/access/requests")
                        .header("Authorization", requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(createBody(
                                List.of(requesterTeacherId), "PAPER", "DOWNLOAD",
                                refs(ref("FILE_ATTACHMENT", firstFileId))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerAuditEndpointIsIsolatedAndContainsCompletedDownloadDecision() throws Exception {
        MvcResult started = mvc.perform(get("/api/files/{id}/download", firstFileId)
                        .header("Authorization", requester()))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());

        Long grantId = jdbc.queryForObject("""
                SELECT id FROM resource_access_grant
                WHERE requester_teacher_id = ? AND resource_id = ? AND permission_type = 'DOWNLOAD'
                """, Long.class, requesterTeacherId, firstFileId);
        mvc.perform(get("/api/access/audits").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].actorTeacherId").value(requesterTeacherId))
                .andExpect(jsonPath("$.data.records[0].ownerTeacherId").value(ownerTeacherId))
                .andExpect(jsonPath("$.data.records[0].operationType").value("DOWNLOAD"))
                .andExpect(jsonPath("$.data.records[0].resourceId").value(paperId))
                .andExpect(jsonPath("$.data.records[0].fileId").value(firstFileId))
                .andExpect(jsonPath("$.data.records[0].grantId").value(grantId))
                .andExpect(jsonPath("$.data.records[0].actorDisplayName").value("Requester"))
                .andExpect(jsonPath("$.data.records[0].ownerDisplayName").value("Owner"))
                .andExpect(jsonPath("$.data.records[0].resourceName").value("first.pdf"))
                .andExpect(jsonPath("$.data.records[0].purpose")
                        .value("Synthetic integration test"))
                .andExpect(jsonPath("$.data.records[0].success").value(true));
        mvc.perform(get("/api/access/audits").header("Authorization", requester()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/access/audits")
                        .header("Authorization", bearer(adminUserId, adminTeacherId, "SUPER_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniedAttachmentAttemptIsRecordedForTheRealOwner() throws Exception {
        clearWorkflow();

        mvc.perform(get("/api/files/{id}/download", secondFileId)
                        .header("Authorization", requester()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/access/audits").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].ownerTeacherId").value(ownerTeacherId))
                .andExpect(jsonPath("$.data.records[0].actorTeacherId").value(requesterTeacherId))
                .andExpect(jsonPath("$.data.records[0].fileId").value(secondFileId))
                .andExpect(jsonPath("$.data.records[0].success").value(false));
    }

    private Long user(String username, String role) {
        jdbc.update("""
                INSERT INTO `user`
                    (username, email, password_hash, role, status, auth_version)
                VALUES (?, ?, 'unused-test-hash', ?, 'ACTIVE', 1)
                """, username, username + "@example.test", role);
        return jdbc.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, username);
    }

    private Long teacher(Long userId, String name) {
        jdbc.update("INSERT INTO teacher_profile (user_id, display_name) VALUES (?, ?)",
                userId, name);
        jdbc.update("""
                INSERT INTO academic_role_assignment (user_id, role_code, major_id)
                VALUES (?, 'FULL_TIME_TEACHER', NULL)
                """, userId);
        return jdbc.queryForObject(
                "SELECT id FROM teacher_profile WHERE user_id = ?", Long.class, userId);
    }

    private Long major(String code, String name) {
        jdbc.update("INSERT INTO major (code, name, active) VALUES (?, ?, 1)", code, name);
        return jdbc.queryForObject("SELECT id FROM major WHERE code = ?", Long.class, code);
    }

    private Actor scopedActor(String prefix, String role, Long roleMajorId) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Long userId = user(prefix + "-" + suffix, "TEACHER");
        Long teacherId = teacher(userId, prefix);
        jdbc.update("DELETE FROM academic_role_assignment WHERE user_id = ?", userId);
        jdbc.update("""
                INSERT INTO academic_role_assignment (user_id, role_code, major_id)
                VALUES (?, 'FULL_TIME_TEACHER', NULL)
                """, userId);
        if (role != null && !"FULL_TIME_TEACHER".equals(role)) {
            jdbc.update("""
                    INSERT INTO academic_role_assignment (user_id, role_code, major_id)
                    VALUES (?, ?, ?)
                    """, userId, role, roleMajorId);
        }
        scopeUserIds.add(userId);
        scopeTeacherIds.add(teacherId);
        return new Actor(userId, teacherId);
    }

    private void expectPaperVisible(Actor actor) throws Exception {
        mvc.perform(get("/api/access/resources")
                        .param("ownerTeacherId", ownerTeacherId.toString())
                        .param("resourceType", "PAPER")
                        .header("Authorization", bearer(actor.userId(), actor.teacherId(), "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    private void expectPaperHidden(Actor actor) throws Exception {
        mvc.perform(get("/api/access/resources")
                        .param("ownerTeacherId", ownerTeacherId.toString())
                        .param("resourceType", "PAPER")
                        .header("Authorization", bearer(actor.userId(), actor.teacherId(), "TEACHER")))
                .andExpect(status().isNotFound());
    }

    private void expectPaperFilteredByOwnerPolicy(Actor actor) throws Exception {
        mvc.perform(get("/api/access/resources")
                        .param("ownerTeacherId", ownerTeacherId.toString())
                        .param("resourceType", "PAPER")
                        .header("Authorization", bearer(actor.userId(), actor.teacherId(), "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    private FileMetadata upload(String name, String content) {
        return files.uploadFile(
                ownerTeacherId,
                FileBusinessType.PAPER,
                paperId,
                new MockMultipartFile(
                        "file", name, "application/pdf",
                        content.getBytes(StandardCharsets.UTF_8)));
    }

    private void grantDownload(Long fileId) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO resource_access_request
                    (requester_teacher_id, owner_teacher_id, resource_type,
                     permission_type, purpose, status, requested_expires_at, decided_at)
                VALUES (?, ?, 'PAPER', 'DOWNLOAD', 'Synthetic integration test',
                        'APPROVED', ?, ?)
                """, requesterTeacherId, ownerTeacherId, now.plusDays(1), now);
        Long requestId = jdbc.queryForObject("""
                SELECT id FROM resource_access_request
                WHERE requester_teacher_id = ? AND owner_teacher_id = ?
                ORDER BY id DESC LIMIT 1
                """, Long.class, requesterTeacherId, ownerTeacherId);
        jdbc.update("""
                INSERT INTO resource_access_grant
                    (request_id, requester_teacher_id, owner_teacher_id, permission_type,
                     resource_type, resource_id, purpose, effective_at, expires_at)
                VALUES (?, ?, ?, 'DOWNLOAD', 'FILE_ATTACHMENT', ?,
                        'Synthetic integration test', ?, ?)
                """, requestId, requesterTeacherId, ownerTeacherId, fileId,
                now.minusMinutes(1), now.plusDays(1));
    }

    private void clearWorkflow() {
        jdbc.update("DELETE FROM resource_access_audit WHERE owner_teacher_id = ?", ownerTeacherId);
        jdbc.update("DELETE FROM resource_access_grant WHERE owner_teacher_id = ?", ownerTeacherId);
        jdbc.update("DELETE FROM resource_access_request_item WHERE request_id IN "
                + "(SELECT id FROM resource_access_request WHERE owner_teacher_id = ?)", ownerTeacherId);
        jdbc.update("DELETE FROM resource_access_request WHERE owner_teacher_id = ?", ownerTeacherId);
    }

    private long createAndApproveAttachment(String permission, Long fileId) throws Exception {
        long requestId = createRequest(
                requester(), ownerTeacherId, "PAPER", permission,
                refs(ref("FILE_ATTACHMENT", fileId)));
        decide(owner(), requestId, true, refs(ref("FILE_ATTACHMENT", fileId)))
                .andExpect(status().isOk());
        return requestId;
    }

    private long createRequest(
            String token, Long ownerId, String type, String permission,
            List<java.util.Map<String, Object>> resources) throws Exception {
        MvcResult result = mvc.perform(post("/api/access/requests")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(createBody(
                                List.of(ownerId), type, permission, resources))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray())
                .path("data").get(0).path("id").asLong();
    }

    private java.util.Map<String, Object> createBody(
            List<Long> owners, String type, String permission,
            List<java.util.Map<String, Object>> resources) {
        return java.util.Map.of(
                "ownerTeacherIds", owners,
                "resourceType", type,
                "permissionType", permission,
                "purpose", "Synthetic HTTP integration purpose",
                "requestedExpiresAt", LocalDateTime.now().plusDays(7).toString(),
                "resources", resources);
    }

    private org.springframework.test.web.servlet.ResultActions decide(
            String token, long requestId, boolean approve,
            List<java.util.Map<String, Object>> approved) throws Exception {
        return mvc.perform(patch("/api/access/requests/{id}/decision", requestId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(java.util.Map.of(
                        "approve", approve,
                        "approvedResources", approved))));
    }

    private org.springframework.test.web.servlet.ResultActions download(String token, Long fileId)
            throws Exception {
        MvcResult started = mvc.perform(get("/api/files/{id}/download", fileId)
                        .header("Authorization", token))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(started));
    }

    private void updateAttachmentPolicy(
            Long fileId, boolean view, boolean download, boolean ai) throws Exception {
        mvc.perform(put("/api/access/policies")
                        .header("Authorization", owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(java.util.Map.of(
                                "items", List.of(java.util.Map.of(
                                        "resourceType", "FILE_ATTACHMENT",
                                        "resourceId", fileId,
                                        "viewAllowed", view,
                                        "downloadAllowed", download,
                                        "aiUseAllowed", ai))))))
                .andExpect(status().isOk());
    }

    private void updateParentPolicy(boolean view) throws Exception {
        mvc.perform(put("/api/access/policies")
                        .header("Authorization", owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(java.util.Map.of(
                                "items", List.of(java.util.Map.of(
                                        "resourceType", "PAPER",
                                        "resourceId", paperId,
                                        "viewAllowed", view,
                                        "downloadAllowed", false,
                                        "aiUseAllowed", false))))))
                .andExpect(status().isOk());
    }

    private void setParentDownloadAllowed(boolean allowed) throws Exception {
        mvc.perform(put("/api/access/policies")
                        .header("Authorization", owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(java.util.Map.of(
                                "items", List.of(java.util.Map.of(
                                        "resourceType", "PAPER",
                                        "resourceId", paperId,
                                        "viewAllowed", true,
                                        "downloadAllowed", allowed,
                                        "aiUseAllowed", false))))))
                .andExpect(status().isOk());
    }

    private void assertRevoked(long requestId) {
        LocalDateTime revoked = jdbc.queryForObject(
                "SELECT revoked_at FROM resource_access_grant WHERE request_id = ?",
                LocalDateTime.class, requestId);
        org.assertj.core.api.Assertions.assertThat(revoked).isNotNull();
    }

    private void insertConfirmedOcr(Long fileId, String text) {
        jdbc.update("""
                INSERT INTO ocr_task
                    (teacher_id, file_id, target_type, status, raw_text, result_count)
                VALUES (?, ?, 'PAPER', 'CONFIRMED', ?, 1)
                """, ownerTeacherId, fileId, text);
    }

    private void assertAiDeniedBeforeProvider(
            Long fileId,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        clearInvocations(aiChatClient);
        mvc.perform(post("/api/ai/tasks")
                        .header("Authorization", requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(java.util.Map.of(
                                "templateRequirement", "Must be denied",
                                "selectedModules", List.of("teacherProfile"),
                                "selectedAttachmentIds", List.of(fileId)))))
                .andExpect(expectedStatus);
        verify(aiChatClient, never()).generate(any(String.class), any(AiRuntimeConfig.class));
    }

    @SafeVarargs
    private final List<java.util.Map<String, Object>> refs(java.util.Map<String, Object>... refs) {
        return List.of(refs);
    }

    private java.util.Map<String, Object> ref(String type, Long id) {
        return java.util.Map.of("resourceType", type, "resourceId", id);
    }

    private String owner() {
        return bearer(ownerUserId, ownerTeacherId, "TEACHER");
    }

    private String requester() {
        return bearer(requesterUserId, requesterTeacherId, "TEACHER");
    }

    private String bearer(Long userId, Long teacherId, String role) {
        return "Bearer " + jwt.generateToken(new CurrentUser(
                userId, teacherId, "test", "test@example.test", role, 1L));
    }

    private record Actor(Long userId, Long teacherId) {
    }
}
