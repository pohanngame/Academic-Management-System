package com.example.academicprofile.backup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BackupPasswordRequestTest {

    @Test
    void toStringNeverContainsPassword() {
        String marker = "sensitive-test-marker";
        assertThat(new BackupPasswordRequest(marker).toString())
                .doesNotContain(marker)
                .contains("REDACTED");
    }
}
