package com.example.academicprofile.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.security.CurrentUser;

@SpringBootTest
class AchievementMutationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AchievementMutationService mutations;

    private Long ownerUser;
    private Long ownerTeacher;
    private Long otherUser;
    private Long otherTeacher;
    private CurrentUser actor;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        ownerUser = user("mutation-owner-" + suffix);
        ownerTeacher = teacher(ownerUser, "Mutation Owner");
        otherUser = user("mutation-other-" + suffix);
        otherTeacher = teacher(otherUser, "Mutation Other");
        actor = new CurrentUser(ownerUser, ownerTeacher, "owner", "owner@example.test", "TEACHER", 1L);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM resource_access_audit WHERE owner_teacher_id IN (?,?)", ownerTeacher, otherTeacher);
        jdbc.update("DELETE FROM resource_access_grant WHERE owner_teacher_id IN (?,?)", ownerTeacher, otherTeacher);
        jdbc.update("DELETE FROM resource_access_request_item WHERE request_id IN (SELECT id FROM resource_access_request WHERE owner_teacher_id IN (?,?))", ownerTeacher, otherTeacher);
        jdbc.update("DELETE FROM resource_access_request WHERE owner_teacher_id IN (?,?)", ownerTeacher, otherTeacher);
        jdbc.update("DELETE FROM resource_access_policy WHERE owner_teacher_id IN (?,?)", ownerTeacher, otherTeacher);
        jdbc.update("DELETE FROM file_metadata WHERE teacher_id IN (?,?)", ownerTeacher, otherTeacher);
        jdbc.update("DELETE FROM paper WHERE teacher_id IN (?,?)", ownerTeacher, otherTeacher);
        jdbc.update("DELETE FROM academic_role_assignment WHERE user_id IN (?,?)", ownerUser, otherUser);
        jdbc.update("DELETE FROM teacher_profile WHERE id IN (?,?)", ownerTeacher, otherTeacher);
        jdbc.update("DELETE FROM `user` WHERE id IN (?,?)", ownerUser, otherUser);
    }

    @Test
    void zeroSortedRowsCanBeNormalizedAndMovedPersistently() {
        Long first = paper(ownerTeacher, "first", 0);
        Long second = paper(ownerTeacher, "second", 0);
        Long third = paper(ownerTeacher, "third", 0);

        mutations.reorder(actor, AchievementRecordType.PAPERS, List.of(first, third, second));

        assertThat(jdbc.queryForList("SELECT id FROM paper WHERE teacher_id=? ORDER BY sort_order,id",
                Long.class, ownerTeacher)).containsExactly(first, third, second);
    }

    @Test
    void mixedOwnerBatchRollsBackCompletely() {
        Long own = paper(ownerTeacher, "own", 1);
        Long foreign = paper(otherTeacher, "foreign", 1);
        policy(ownerTeacher, "PAPER", own);
        policy(otherTeacher, "PAPER", foreign);

        assertThatThrownBy(() -> mutations.update(actor, AchievementRecordType.PAPERS,
                new AchievementBatchRequests.Update(List.of(own, foreign), false, false, null, null)))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT is_public FROM paper WHERE id=?", Boolean.class, own)).isTrue();
    }

    @Test
    void deletingPaperRetiresParentAttachmentPoliciesAndActiveGrantsButKeepsAudit() {
        Long paper = paper(ownerTeacher, "with attachment", 1);
        policy(ownerTeacher, "PAPER", paper);
        jdbc.update("INSERT INTO file_metadata (teacher_id,original_name,stored_name,storage_path,file_ext,mime_type,file_size,business_type,business_id,deleted) VALUES (?,?,?,?,?,?,?,?,?,0)",
                ownerTeacher, "proof.pdf", "cipher.bin", "synthetic/cipher.bin", "pdf", "application/pdf", 10, "PAPER", paper);
        Long file = jdbc.queryForObject("SELECT id FROM file_metadata WHERE teacher_id=? ORDER BY id DESC LIMIT 1", Long.class, ownerTeacher);
        policy(ownerTeacher, "FILE_ATTACHMENT", file);
        jdbc.update("INSERT INTO resource_access_request (requester_teacher_id,owner_teacher_id,resource_type,permission_type,purpose,status,requested_expires_at) VALUES (?,?,?,?,?,'APPROVED',DATE_ADD(NOW(), INTERVAL 30 DAY))",
                otherTeacher, ownerTeacher, "PAPER", "DOWNLOAD", "synthetic test");
        Long request = jdbc.queryForObject("SELECT id FROM resource_access_request WHERE owner_teacher_id=? ORDER BY id DESC LIMIT 1", Long.class, ownerTeacher);
        jdbc.update("INSERT INTO resource_access_grant (request_id,requester_teacher_id,owner_teacher_id,permission_type,resource_type,resource_id,purpose,effective_at,expires_at) VALUES (?,?,?,?,?,?,?,NOW(),DATE_ADD(NOW(), INTERVAL 30 DAY))",
                request, otherTeacher, ownerTeacher, "DOWNLOAD", "PAPER", paper, "synthetic test");
        jdbc.update("INSERT INTO resource_access_grant (request_id,requester_teacher_id,owner_teacher_id,permission_type,resource_type,resource_id,purpose,effective_at,expires_at) VALUES (?,?,?,?,?,?,?,NOW(),DATE_ADD(NOW(), INTERVAL 30 DAY))",
                request, otherTeacher, ownerTeacher, "DOWNLOAD", "FILE_ATTACHMENT", file, "synthetic test");
        jdbc.update("INSERT INTO resource_access_audit (actor_user_id,actor_teacher_id,owner_teacher_id,operation_type,resource_type,resource_id,success) VALUES (?,?,?,?,?,?,1)",
                ownerUser, ownerTeacher, ownerTeacher, "DETAIL", "PAPER", paper);

        mutations.delete(actor, AchievementRecordType.PAPERS, List.of(paper));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM paper WHERE id=?", Integer.class, paper)).isZero();
        assertThat(jdbc.queryForObject("SELECT deleted FROM file_metadata WHERE id=?", Boolean.class, file)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_access_policy WHERE (resource_type='PAPER' AND resource_id=?) OR (resource_type='FILE_ATTACHMENT' AND resource_id=?)", Integer.class, paper, file)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_access_grant WHERE request_id=? AND revoked_at IS NOT NULL", Integer.class, request)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_access_audit WHERE resource_type='PAPER' AND resource_id=?", Integer.class, paper)).isEqualTo(1);
    }

    private Long user(String username) {
        jdbc.update("INSERT INTO `user` (username,email,password_hash,role,status,auth_version) VALUES (?,?, 'unused','TEACHER','ACTIVE',1)", username, username + "@example.test");
        return jdbc.queryForObject("SELECT id FROM `user` WHERE username=?", Long.class, username);
    }
    private Long teacher(Long user, String name) {
        jdbc.update("INSERT INTO teacher_profile (user_id,display_name) VALUES (?,?)", user, name);
        return jdbc.queryForObject("SELECT id FROM teacher_profile WHERE user_id=?", Long.class, user);
    }
    private Long paper(Long teacher, String title, int order) {
        jdbc.update("INSERT INTO paper (teacher_id,title,sort_order,is_public) VALUES (?,?,?,1)", teacher, title, order);
        return jdbc.queryForObject("SELECT id FROM paper WHERE teacher_id=? AND title=?", Long.class, teacher, title);
    }
    private void policy(Long owner, String type, Long id) {
        jdbc.update("INSERT INTO resource_access_policy (owner_teacher_id,resource_type,resource_id,view_allowed,download_allowed,ai_use_allowed) VALUES (?,?,?,1,1,1)", owner, type, id);
    }
}
