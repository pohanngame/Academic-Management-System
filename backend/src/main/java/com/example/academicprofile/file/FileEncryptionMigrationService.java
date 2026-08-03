package com.example.academicprofile.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class FileEncryptionMigrationService {
    private final FileMetadataMapper mapper;
    private final FileStorageProperties properties;
    private final EncryptedFileCodec codec;
    private final TransactionTemplate transactions;

    public FileEncryptionMigrationService(
            FileMetadataMapper mapper,
            FileStorageProperties properties,
            EncryptedFileCodec codec,
            TransactionTemplate transactions) {
        this.mapper = mapper;
        this.properties = properties;
        this.codec = codec;
        this.transactions = transactions;
    }

    public MigrationResult migrateAll() {
        cleanupCommittedLegacyFiles();
        List<FileMetadata> pending = mapper.selectList(new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getCiphertextStatus, "PLAINTEXT")
                .ne(FileMetadata::getBusinessType, FileBusinessType.AVATAR.name())
                .eq(FileMetadata::getDeleted, Boolean.FALSE)
                .orderByAsc(FileMetadata::getId));
        int migrated = 0;
        for (FileMetadata metadata : pending) {
            if (mapper.claimEncryptionMigration(metadata.getId()) == 1) {
                metadata.setCiphertextStatus("MIGRATING");
                migrateOne(metadata);
                migrated++;
            }
        }
        cleanupCommittedLegacyFiles();
        return new MigrationResult(pending.size(), migrated);
    }

    private void migrateOne(FileMetadata metadata) {
        Path oldPath = resolve(metadata.getStoragePath());
        if (!Files.isRegularFile(oldPath) || Files.isSymbolicLink(oldPath)) {
            throw failure();
        }
        String newStoredName = UUID.randomUUID() + ".bin";
        Path relative = Path.of(metadata.getStoragePath()).getParent().resolve(newStoredName);
        Path target = resolve(relative.toString());
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        try {
            EncryptedFileCodec.EncryptionResult encryption;
            try (InputStream input = Files.newInputStream(oldPath)) {
                encryption = codec.encrypt(input, partial, newStoredName);
            }
            move(partial, target);
            FileMetadata verification = copyEncryptionMetadata(metadata, encryption, newStoredName, relative, oldPath);
            if (!encryption.plaintextSha256().equals(codec.decryptedSha256(target, verification))) {
                throw failure();
            }
            transactions.executeWithoutResult(status -> mapper.updateById(verification));
        } catch (RuntimeException | IOException ex) {
            delete(partial);
            delete(target);
            mapper.releaseEncryptionMigration(metadata.getId());
            if (ex instanceof BusinessException business) {
                throw business;
            }
            throw failure();
        }
    }

    private FileMetadata copyEncryptionMetadata(
            FileMetadata metadata,
            EncryptedFileCodec.EncryptionResult encryption,
            String storedName,
            Path relative,
            Path oldPath) {
        metadata.setLegacyStoragePath(metadata.getStoragePath());
        metadata.setStoredName(storedName);
        metadata.setStoragePath(relative.toString().replace('\\', '/'));
        metadata.setEncryptionVersion(EncryptedFileCodec.VERSION);
        metadata.setEncryptionKeyId(encryption.keyId());
        metadata.setWrappedDek(encryption.wrappedDek());
        metadata.setCiphertextStatus("ENCRYPTED");
        metadata.setPlaintextSha256(encryption.plaintextSha256());
        return metadata;
    }

    private void cleanupCommittedLegacyFiles() {
        List<FileMetadata> committed = mapper.selectList(new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getCiphertextStatus, "ENCRYPTED")
                .isNotNull(FileMetadata::getLegacyStoragePath));
        for (FileMetadata metadata : committed) {
            Path ciphertext = resolve(metadata.getStoragePath());
            if (!metadata.getPlaintextSha256().equals(codec.decryptedSha256(ciphertext, metadata))) {
                throw failure();
            }
            delete(resolve(metadata.getLegacyStoragePath()));
            mapper.clearLegacyStoragePath(metadata.getId());
            metadata.setLegacyStoragePath(null);
        }
    }

    private Path resolve(String relative) {
        Path root = Path.of(properties.getUploadDir()).toAbsolutePath().normalize();
        Path result = root.resolve(relative).normalize();
        if (!result.startsWith(root)) {
            throw failure();
        }
        return result;
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A later idempotent run retries legacy cleanup.
        }
    }

    private BusinessException failure() {
        return new BusinessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Attachment encryption migration failed; original data was retained");
    }

    public record MigrationResult(int discovered, int migrated) {
    }
}
