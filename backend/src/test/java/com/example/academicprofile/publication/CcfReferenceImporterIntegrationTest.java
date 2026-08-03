package com.example.academicprofile.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "app.publication.ccf.enforce-expected-sha256=false")
class CcfReferenceImporterIntegrationTest {
    @Autowired private CcfReferenceImporter importer;
    @Autowired private CcfReferenceUploadParser parser;
    @Autowired private PublicationVenueDatasetMapper datasets;
    @SpyBean private PublicationVenueReferenceMapper references;
    @Autowired private JdbcTemplate jdbc;

    private Long operatorId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM publication_venue_reference");
        jdbc.update("DELETE FROM publication_venue_dataset");
        String username = "ccf-importer-" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO `user` (username,email,password_hash,role,status,auth_version) VALUES (?,?,?,'SUPER_ADMIN','ACTIVE',1)",
                username, username + "@example.test", "unused");
        operatorId = jdbc.queryForObject("SELECT id FROM `user` WHERE username=?", Long.class, username);
    }

    @AfterEach
    void cleanUp() {
        reset(references);
        jdbc.update("DELETE FROM publication_venue_reference");
        jdbc.update("DELETE FROM publication_venue_dataset");
        jdbc.update("DELETE FROM admin_audit_log WHERE operator_user_id=?", operatorId);
        jdbc.update("DELETE FROM `user` WHERE id=?", operatorId);
    }

    @Test
    void validatedImportIsIdempotentAndRecordsSafeMetadata() {
        var parsed = parse(CcfReferenceUploadParserTest.validJson());
        String hash = "1".repeat(64);
        var first = importer.importValidated(parsed, hash, operatorId);
        var second = importer.importValidated(parsed, hash, operatorId);

        assertThat(first.imported()).isTrue();
        assertThat(second.imported()).isFalse();
        assertThat(first.journalCount()).isEqualTo(1);
        assertThat(first.conferenceCount()).isEqualTo(1);
        assertThat(datasets.selectCount(null)).isEqualTo(1);
        assertThat(references.selectCount(null)).isEqualTo(2);
    }

    @Test
    void failedReplacementRollsBackToPreviousQueryableVersion() {
        String oldHash = "2".repeat(64);
        importer.importValidated(parse(CcfReferenceUploadParserTest.validJson()), oldHash, operatorId);
        String replacement = CcfReferenceUploadParserTest.validJson()
                .replace("Synthetic Journal", "Replacement Journal");
        doThrow(new IllegalStateException("synthetic insert failure"))
                .when(references).insert((PublicationVenueReference) argThat((PublicationVenueReference ref) ->
                        "Replacement Journal".equals(ref.getStandardName())));

        assertThatThrownBy(() -> importer.importValidated(parse(replacement), "3".repeat(64), operatorId))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT source_sha256 FROM publication_venue_dataset LIMIT 1", String.class))
                .isEqualTo(oldHash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publication_venue_reference WHERE standard_name='Synthetic Journal'", Integer.class))
                .isEqualTo(1);
    }

    private CcfReferenceUploadParser.ParsedDataset parse(String value) {
        return parser.parse(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)), 2026);
    }
}
