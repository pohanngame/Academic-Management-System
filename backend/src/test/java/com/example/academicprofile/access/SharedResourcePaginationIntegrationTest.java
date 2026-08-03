package com.example.academicprofile.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.academicprofile.security.CurrentUser;

@SpringBootTest
class SharedResourcePaginationIntegrationTest {
    private static final int RESOURCE_COUNT = 205;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SharedResourceService shared;

    private Long userId;
    private Long teacherId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO `user`
                    (username, email, password_hash, role, status, auth_version)
                VALUES (?, ?, 'unused-test-hash', 'TEACHER', 'ACTIVE', 1)
                """, "pagination-" + suffix, "pagination-" + suffix + "@example.test");
        userId = jdbc.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, "pagination-" + suffix);
        jdbc.update("INSERT INTO teacher_profile (user_id, display_name) VALUES (?, 'Pagination')",
                userId);
        teacherId = jdbc.queryForObject(
                "SELECT id FROM teacher_profile WHERE user_id = ?", Long.class, userId);
        for (int index = 0; index < RESOURCE_COUNT; index++) {
            jdbc.update("INSERT INTO paper (teacher_id, title) VALUES (?, ?)",
                    teacherId, "Synthetic paper " + index);
        }
        jdbc.update("""
                INSERT INTO resource_access_policy
                    (owner_teacher_id, resource_type, resource_id,
                     view_allowed, download_allowed, ai_use_allowed)
                SELECT teacher_id, 'PAPER', id, 1, 0, 0
                FROM paper WHERE teacher_id = ?
                """, teacherId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM resource_access_audit WHERE owner_teacher_id = ?", teacherId);
        jdbc.update("DELETE FROM resource_access_policy WHERE owner_teacher_id = ?", teacherId);
        jdbc.update("DELETE FROM paper WHERE teacher_id = ?", teacherId);
        jdbc.update("DELETE FROM academic_role_assignment WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM teacher_profile WHERE id = ?", teacherId);
        jdbc.update("DELETE FROM `user` WHERE id = ?", userId);
    }

    @Test
    void firstPageReadsOnlyRequestedRowsReturnsDatabaseTotalAndAvoidsListAuditNoise() {
        CurrentUser actor = new CurrentUser(
                userId, teacherId, "pagination", "pagination@example.test", "TEACHER", 1L);

        var page = shared.list(actor, teacherId, ResourceType.PAPER, 1, 20);

        assertThat(page.items()).hasSize(20);
        assertThat(page.total()).isEqualTo(RESOURCE_COUNT);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM resource_access_audit WHERE owner_teacher_id = ?",
                Integer.class, teacherId)).isZero();
    }
}
