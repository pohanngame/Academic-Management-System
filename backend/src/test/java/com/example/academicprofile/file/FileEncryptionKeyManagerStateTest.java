package com.example.academicprofile.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.academicprofile.common.exception.BusinessException;

class FileEncryptionKeyManagerStateTest {
    @TempDir
    Path temp;

    @Test
    void encryptedRowsWithoutKeyFailWithoutCreatingReplacement() {
        Path key = temp.resolve("missing.key");
        FileMetadataMapper mapper = mock(FileMetadataMapper.class);
        when(mapper.countEncryptedAttachments()).thenReturn(1L);
        when(mapper.selectEncryptionKeyIds()).thenReturn(List.of("old-key-id"));

        assertThatThrownBy(() -> new FileEncryptionKeyManager(properties(key), mapper))
                .isInstanceOf(BusinessException.class);
        assertThat(key).doesNotExist();
    }

    @Test
    void encryptedRowsRequireMatchingExistingKey() throws Exception {
        Path key = temp.resolve("existing.key");
        Files.write(key, new byte[32]);
        FileMetadataMapper mapper = mock(FileMetadataMapper.class);
        when(mapper.countEncryptedAttachments()).thenReturn(1L);
        when(mapper.selectEncryptionKeyIds()).thenReturn(List.of("different-key-id"));

        assertThatThrownBy(() -> new FileEncryptionKeyManager(properties(key), mapper))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void encryptedRowsStartWithMatchingExistingKey() {
        Path key = temp.resolve("matching.key");
        FileMetadataMapper empty = mock(FileMetadataMapper.class);
        when(empty.countEncryptedAttachments()).thenReturn(0L);
        when(empty.selectEncryptionKeyIds()).thenReturn(List.of());
        String keyId = new FileEncryptionKeyManager(properties(key), empty).keyId();
        FileMetadataMapper encrypted = mock(FileMetadataMapper.class);
        when(encrypted.countEncryptedAttachments()).thenReturn(2L);
        when(encrypted.selectEncryptionKeyIds()).thenReturn(List.of(keyId));

        FileEncryptionKeyManager restored =
                new FileEncryptionKeyManager(properties(key), encrypted);

        assertThat(restored.keyId()).isEqualTo(keyId);
    }

    @Test
    void emptyDatabaseMayCreateKey() {
        Path key = temp.resolve("new.key");
        FileMetadataMapper mapper = mock(FileMetadataMapper.class);
        when(mapper.countEncryptedAttachments()).thenReturn(0L);
        when(mapper.selectEncryptionKeyIds()).thenReturn(List.of());

        FileEncryptionKeyManager manager = new FileEncryptionKeyManager(properties(key), mapper);

        assertThat(key).exists();
        assertThat(manager.key()).hasSize(32);
    }

    @Test
    void concurrentEmptyDatabaseInitializationUsesOnePublishedKey() throws Exception {
        Path key = temp.resolve("concurrent.key");
        FileMetadataMapper mapper = mock(FileMetadataMapper.class);
        when(mapper.countEncryptedAttachments()).thenReturn(0L);
        when(mapper.selectEncryptionKeyIds()).thenReturn(List.of());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new FileEncryptionKeyManager(properties(key), mapper));
            var second = executor.submit(() -> new FileEncryptionKeyManager(properties(key), mapper));
            FileEncryptionKeyManager firstManager = first.get(10, TimeUnit.SECONDS);
            FileEncryptionKeyManager secondManager = second.get(10, TimeUnit.SECONDS);
            assertThat(firstManager.keyId()).isEqualTo(secondManager.keyId());
            assertThat(firstManager.key()).isEqualTo(secondManager.key());
        } finally {
            executor.shutdownNow();
        }
    }

    private FileStorageProperties properties(Path key) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setEncryptionKeyFile(key.toString());
        return properties;
    }
}
