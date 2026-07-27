package com.example.academicprofile.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.security.CurrentUser;

@SpringBootTest
class AdminUserTransactionIntegrationTest {

    @Autowired
    private AdminUserService service;
    @Autowired
    private JdbcTemplate jdbc;
    @SpyBean
    private AdminAuditService audit;

    private Long operatorId;
    private Long targetId;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbc.update(
                "INSERT INTO `user` "
                        + "(username, email, password_hash, role, status, auth_version) "
                        + "VALUES (?, ?, ?, 'SUPER_ADMIN', 'ACTIVE', 4)",
                "tx-admin-" + suffix, "tx-admin-" + suffix + "@example.test", "unused-test-hash");
        operatorId = jdbc.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, "tx-admin-" + suffix);
        jdbc.update(
                "INSERT INTO `user` "
                        + "(username, email, password_hash, role, status, auth_version) "
                        + "VALUES (?, ?, ?, 'TEACHER', 'ACTIVE', 7)",
                "tx-target-" + suffix, "tx-target-" + suffix + "@example.test", "unused-test-hash");
        targetId = jdbc.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, "tx-target-" + suffix);
        operator = new CurrentUser(
                operatorId, operatorId, "tx-admin", "tx-admin@example.test", "SUPER_ADMIN", 4L);
    }

    @AfterEach
    void cleanUp() {
        if (operatorId != null) {
            jdbc.update("DELETE FROM admin_audit_log WHERE operator_user_id = ?", operatorId);
        }
        if (targetId != null) {
            jdbc.update("DELETE FROM `user` WHERE id = ?", targetId);
        }
        if (operatorId != null) {
            jdbc.update("DELETE FROM `user` WHERE id = ?", operatorId);
        }
    }

    @Test
    void roleChangeAndSuccessAuditCommitTogether() {
        service.changeRole(operator, targetId, new AdminRoleRequest("SUPER_ADMIN"));

        assertThat(targetState()).containsExactly("SUPER_ADMIN", "ACTIVE", "8");
        assertThat(auditCount("USER_ROLE_CHANGE", true)).isEqualTo(1);
    }

    @Test
    void statusChangeAndSuccessAuditCommitTogether() {
        service.changeStatus(operator, targetId, new AdminStatusRequest("DISABLED"));

        assertThat(targetState()).containsExactly("TEACHER", "DISABLED", "8");
        assertThat(auditCount("USER_STATUS_CHANGE", true)).isEqualTo(1);
    }

    @Test
    void successAuditFailureRollsBackRoleAndAuthVersion() {
        doAnswer(invocation -> {
            if (invocation.getArgument(3, Boolean.class)) {
                throw new IllegalStateException("simulated audit insert failure");
            }
            return invocation.callRealMethod();
        }).when(audit).record(any(), any(), any(), any(Boolean.class), any());

        assertThatThrownBy(() ->
                service.changeRole(operator, targetId, new AdminRoleRequest("SUPER_ADMIN")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(targetState()).containsExactly("TEACHER", "ACTIVE", "7");
        assertThat(auditCount("USER_ROLE_CHANGE", true)).isZero();
        assertThat(auditCount("USER_ROLE_CHANGE", false)).isEqualTo(1);
    }

    @Test
    void rejectedBusinessRuleHasOnlyFailureAudit() {
        assertThatThrownBy(() ->
                service.changeStatus(operator, operatorId, new AdminStatusRequest("DISABLED")))
                .isInstanceOf(BusinessException.class);

        assertThat(operatorState()).containsExactly("SUPER_ADMIN", "ACTIVE", "4");
        assertThat(auditCount("USER_STATUS_CHANGE", true)).isZero();
        assertThat(auditCount("USER_STATUS_CHANGE", false)).isEqualTo(1);
    }

    @Test
    void failureAuditFailureDoesNotReplaceBusinessException() {
        doAnswer(invocation -> {
            if (!invocation.getArgument(3, Boolean.class)) {
                throw new IllegalStateException("simulated failure-audit outage");
            }
            return invocation.callRealMethod();
        }).when(audit).record(any(), any(), any(), any(Boolean.class), any());

        assertThatThrownBy(() ->
                service.changeStatus(operator, operatorId, new AdminStatusRequest("DISABLED")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).contains("cannot disable"));

        assertThat(operatorState()).containsExactly("SUPER_ADMIN", "ACTIVE", "4");
    }

    private java.util.List<String> targetState() {
        return jdbc.queryForObject(
                "SELECT role, status, auth_version FROM `user` WHERE id = ?",
                (rs, row) -> java.util.List.of(
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getString("auth_version")),
                targetId);
    }

    private java.util.List<String> operatorState() {
        return jdbc.queryForObject(
                "SELECT role, status, auth_version FROM `user` WHERE id = ?",
                (rs, row) -> java.util.List.of(
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getString("auth_version")),
                operatorId);
    }

    private int auditCount(String action, boolean success) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_log "
                        + "WHERE operator_user_id = ? AND action_type = ? AND success = ?",
                Integer.class, operatorId, action, success);
    }
}
