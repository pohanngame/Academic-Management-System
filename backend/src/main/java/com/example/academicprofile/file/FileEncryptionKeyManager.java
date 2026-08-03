package com.example.academicprofile.file;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import java.util.List;

import com.example.academicprofile.common.exception.BusinessException;

@Component
@DependsOn("flywayInitializer")
public class FileEncryptionKeyManager {
    private static final int KEY_BYTES = 32;
    private final Path keyFile;
    private final byte[] key;
    private final String keyId;

    public FileEncryptionKeyManager(FileStorageProperties properties) {
        this(properties, null);
    }

    @Autowired
    public FileEncryptionKeyManager(
            FileStorageProperties properties,
            FileMetadataMapper metadata) {
        this.keyFile = Path.of(properties.getEncryptionKeyFile()).toAbsolutePath().normalize();
        long encryptedCount = metadata == null ? 0L : metadata.countEncryptedAttachments();
        List<String> databaseKeyIds = metadata == null ? List.of() : metadata.selectEncryptionKeyIds();
        this.key = loadOrCreate(encryptedCount);
        this.keyId = fingerprint(key);
        if (encryptedCount > 0
                && (databaseKeyIds == null
                    || databaseKeyIds.isEmpty()
                    || databaseKeyIds.stream().anyMatch(id -> !keyId.equals(id)))) {
            throw unavailable();
        }
    }

    public byte[] key() {
        return key.clone();
    }

    public String keyId() {
        return keyId;
    }

    private byte[] loadOrCreate(long encryptedCount) {
        try {
            if (Files.exists(keyFile)) {
                return validate(Files.readAllBytes(keyFile));
            }
            if (encryptedCount > 0) {
                throw unavailable();
            }
            Files.createDirectories(keyFile.getParent());
            Path lockFile = keyFile.resolveSibling(keyFile.getFileName() + ".lock");
            try {
                Files.createFile(lockFile);
                restrict(lockFile);
            } catch (java.nio.file.FileAlreadyExistsException ex) {
                return waitForPublishedKey();
            }
            try {
                if (Files.exists(keyFile)) {
                    return validate(Files.readAllBytes(keyFile));
                }
                return createAndPublish();
            } finally {
                Files.deleteIfExists(lockFile);
            }
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    private byte[] createAndPublish() throws IOException {
            byte[] generated = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(generated);
            Path partial = keyFile.resolveSibling(keyFile.getFileName() + "." + UUID.randomUUID() + ".partial");
            try {
                Files.write(partial, generated, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                restrict(partial);
                try {
                    Files.move(partial, keyFile, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(partial, keyFile);
                }
            } finally {
                Files.deleteIfExists(partial);
            }
            restrict(keyFile);
            return generated;
    }

    private byte[] waitForPublishedKey() {
        for (int attempt = 0; attempt < 500; attempt++) {
            if (Files.exists(keyFile)) {
                try {
                    return validate(Files.readAllBytes(keyFile));
                } catch (IOException ex) {
                    throw unavailable();
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw unavailable();
            }
        }
        throw unavailable();
    }

    private byte[] validate(byte[] value) {
        if (value.length != KEY_BYTES) {
            throw unavailable();
        }
        return value;
    }

    private BusinessException unavailable() {
        return new BusinessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "File encryption key is unavailable; restore the key backup");
    }

    private String fingerprint(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)).substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void restrict(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and non-POSIX filesystems use their platform ACLs.
        }
    }
}
