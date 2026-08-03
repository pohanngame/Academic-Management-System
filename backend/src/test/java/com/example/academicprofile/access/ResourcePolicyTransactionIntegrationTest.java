package com.example.academicprofile.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.academicprofile.security.CurrentUser;

@SpringBootTest
class ResourcePolicyTransactionIntegrationTest {
    @Autowired
    private ResourcePolicyService service;
    @Autowired
    private JdbcTemplate jdbc;
    @SpyBean
    private ResourceAccessAuditService audits;

    private Long userId;
    private Long teacherId;
    private Long paperId;
    private CurrentUser actor;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO `user`
                    (username, email, password_hash, role, status, auth_version)
                VALUES (?, ?, 'unused-test-hash', 'TEACHER', 'ACTIVE', 1)
                """, "policy-" + suffix, "policy-" + suffix + "@example.test");
        userId = jdbc.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, "policy-" + suffix);
        jdbc.update("INSERT INTO teacher_profile (user_id, display_name) VALUES (?, ?)",
                userId, "Policy Test");
        teacherId = jdbc.queryForObject(
                "SELECT id FROM teacher_profile WHERE user_id = ?", Long.class, userId);
        jdbc.update("INSERT INTO paper (teacher_id, title) VALUES (?, 'Synthetic paper')", teacherId);
        paperId = jdbc.queryForObject(
                "SELECT id FROM paper WHERE teacher_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, teacherId);
        jdbc.update("""
                INSERT INTO resource_access_policy
                    (owner_teacher_id, resource_type, resource_id,
                     view_allowed, download_allowed, ai_use_allowed)
                VALUES (?, 'PAPER', ?, 1, 0, 0)
                """, teacherId, paperId);
        actor = new CurrentUser(
                userId, teacherId, "policy", "policy@example.test", "TEACHER", 1L);
    }

    @AfterEach
    void cleanUp() {
        if (teacherId != null) {
            jdbc.update("DELETE FROM resource_access_audit WHERE owner_teacher_id = ?", teacherId);
            jdbc.update("DELETE FROM resource_access_policy WHERE owner_teacher_id = ?", teacherId);
            jdbc.update("DELETE FROM paper WHERE teacher_id = ?", teacherId);
            jdbc.update("DELETE FROM academic_role_assignment WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM teacher_profile WHERE id = ?", teacherId);
            jdbc.update("DELETE FROM `user` WHERE id = ?", userId);
        }
    }

    @Test
    void policyChangeAndSuccessAuditCommitTogether() {
        service.updateOwned(actor, List.of(update(false)));

        assertThat(viewAllowed()).isFalse();
        assertThat(auditCount(true)).isEqualTo(1);
    }

    @Test
    void successAuditFailureRollsBackPolicyAndWritesFailureAudit() {
        doAnswer(invocation -> {
            throw new IllegalStateException("synthetic audit failure");
        }).when(audits).recordAction(any(), any(), any(), any(), any(), any(), any(), any(Boolean.class));

        assertThatThrownBy(() -> service.updateOwned(actor, List.of(update(false))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(viewAllowed()).isTrue();
        assertThat(auditCount(true)).isZero();
        assertThat(auditCount(false)).isEqualTo(1);
    }

    private ResourcePolicyService.PolicyUpdate update(boolean viewAllowed) {
        return new ResourcePolicyService.PolicyUpdate(
                ResourceType.PAPER.name(), paperId, viewAllowed, false, false);
    }

    private boolean viewAllowed() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT view_allowed FROM resource_access_policy
                WHERE resource_type = 'PAPER' AND resource_id = ?
                """, Boolean.class, paperId));
    }

    private int auditCount(boolean success) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM resource_access_audit
                WHERE owner_teacher_id = ? AND operation_type = 'POLICY_UPDATE' AND success = ?
                """, Integer.class, teacherId, success);
    }
}
