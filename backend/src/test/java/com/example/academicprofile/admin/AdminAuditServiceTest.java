package com.example.academicprofile.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class AdminAuditServiceTest {

    @Test
    void auditSummaryRedactsSensitiveMarkers() {
        AdminAuditLogMapper mapper = org.mockito.Mockito.mock(AdminAuditLogMapper.class);
        AtomicReference<AdminAuditLog> captured = new AtomicReference<>();
        org.mockito.Mockito.when(mapper.insert(any(AdminAuditLog.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return 1;
        });
        AdminAuditService service = new AdminAuditService(mapper);

        service.record(1L, "BACKUP_FAILED", null, false,
                "Authorization Bearer password API_KEY token");

        assertThat(captured.get().getSummary())
                .doesNotContainIgnoringCase("Authorization")
                .doesNotContainIgnoringCase("Bearer")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("API_KEY")
                .doesNotContainIgnoringCase("token");
    }
}
