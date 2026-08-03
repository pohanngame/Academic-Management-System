package com.example.academicprofile.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.academicprofile.access.AccessDtos.AccessAuditResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

class AccessAuditResponseContractTest {
    @Test
    void exposesStableCreatedAtAndOperationTypeFields() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 30, 12, 0);
        AccessAuditResponse response = new AccessAuditResponse(
                1L, 2L, "访问人", 3L, "所有人",
                "PREVIEW", "FILE_ATTACHMENT", 4L, 4L,
                "证明材料.pdf", "教学检查", null, 5L, true, createdAt);

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertThat(json).contains("\"createdAt\"", "\"operationType\":\"PREVIEW\"");
        assertThat(json).contains(
                "\"actorDisplayName\":\"访问人\"",
                "\"ownerDisplayName\":\"所有人\"",
                "\"resourceName\":\"证明材料.pdf\"",
                "\"purpose\":\"教学检查\"");
        assertThat(json).doesNotContain("\"accessedAt\"", "\"actionType\"");
        assertThat(json).doesNotContain(
                "storagePath", "wrappedDek", "plaintextSha256", "ocrText", "prompt");
    }
}
