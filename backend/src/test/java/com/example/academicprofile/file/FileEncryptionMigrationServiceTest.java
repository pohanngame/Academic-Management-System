package com.example.academicprofile.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class FileEncryptionMigrationServiceTest {
    @TempDir
    Path temp;

    @Test
    void migratesPlaintextOnlyAfterVerifiedCiphertextAndCanResume() throws Exception {
        Path uploads = Files.createDirectories(temp.resolve("uploads"));
        Path legacy = uploads.resolve("1/paper/legacy.pdf");
        Files.createDirectories(legacy.getParent());
        byte[] original = "%PDF synthetic migration".getBytes(StandardCharsets.UTF_8);
        Files.write(legacy, original);

        FileMetadata metadata = new FileMetadata();
        metadata.setId(1L);
        metadata.setBusinessType(FileBusinessType.PAPER.name());
        metadata.setStoragePath("1/paper/legacy.pdf");
        metadata.setStoredName("legacy.pdf");
        metadata.setCiphertextStatus("PLAINTEXT");
        metadata.setDeleted(false);

        FileMetadataMapper mapper = mock(FileMetadataMapper.class);
        AtomicInteger selects = new AtomicInteger();
        when(mapper.selectList(any())).thenAnswer(invocation -> switch (selects.getAndIncrement()) {
            case 0 -> List.of();
            case 1 -> List.of(metadata);
            case 2 -> List.of(metadata);
            default -> List.of();
        });
        when(mapper.claimEncryptionMigration(1L)).thenReturn(1, 0);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        FileStorageProperties properties = new FileStorageProperties();
        properties.setUploadDir(uploads.toString());
        properties.setEncryptionKeyFile(temp.resolve("file-encryption.key").toString());
        EncryptedFileCodec codec = new EncryptedFileCodec(new FileEncryptionKeyManager(properties));
        FileEncryptionMigrationService service =
                new FileEncryptionMigrationService(mapper, properties, codec, transactions);

        FileEncryptionMigrationService.MigrationResult result = service.migrateAll();

        assertThat(result.discovered()).isEqualTo(1);
        assertThat(result.migrated()).isEqualTo(1);
        assertThat(Files.exists(legacy)).isFalse();
        Path ciphertext = uploads.resolve(metadata.getStoragePath());
        assertThat(ciphertext.getFileName().toString()).endsWith(".bin");
        assertThat(Files.readAllBytes(ciphertext)).isNotEqualTo(original);
        assertThat(codec.decrypt(ciphertext, metadata).readAllBytes()).isEqualTo(original);
        assertThat(metadata.getLegacyStoragePath()).isNull();

        assertThat(service.migrateAll().migrated()).isZero();
    }
}
