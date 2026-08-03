package com.example.academicprofile.publication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.academicprofile.admin.AdminAuditLog;
import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileStorageProperties;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;

@Service
public class CcfReferenceAdminService {
    private final PublicationVenueDatasetMapper datasets;
    private final PublicationVenueReferenceMapper references;
    private final CcfReferenceUploadParser parser;
    private final CcfReferenceImporter importer;
    private final CcfReferenceSpecification specification;
    private final FileStorageProperties fileProperties;
    private final UserAccountService users;
    private final PasswordEncoder passwords;
    private final AdminAuditService audit;

    public CcfReferenceAdminService(PublicationVenueDatasetMapper datasets,
            PublicationVenueReferenceMapper references,
            CcfReferenceUploadParser parser,
            CcfReferenceImporter importer,
            CcfReferenceSpecification specification,
            FileStorageProperties fileProperties,
            UserAccountService users,
            PasswordEncoder passwords,
            AdminAuditService audit) {
        this.datasets = datasets;
        this.references = references;
        this.parser = parser;
        this.importer = importer;
        this.specification = specification;
        this.fileProperties = fileProperties;
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
    }

    public ReferenceDataStatus status() {
        List<PublicationVenueDataset> available = datasets.selectList(null);
        if (available.isEmpty()) {
            return new ReferenceDataStatus(false, null, null, specification.sourcePage(), null,
                    0, 0, 0, null, null);
        }
        if (available.size() != 1) {
            throw new IllegalStateException("CCF reference dataset state is inconsistent");
        }
        PublicationVenueDataset dataset = available.get(0);
        List<PublicationVenueReference> records = references.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PublicationVenueReference>()
                        .eq(PublicationVenueReference::getDatasetId, dataset.getId()));
        int categories = (int) records.stream().map(PublicationVenueReference::getCategoryId).distinct().count();
        int journals = (int) records.stream().filter(ref -> VenueType.JOURNAL.name().equals(ref.getVenueType())).count();
        int conferences = records.size() - journals;
        AdminAuditLog latest = audit.latestSuccessful(CcfReferenceImporter.AUDIT_ACTION);
        return new ReferenceDataStatus(true, dataset.getDataName(), dataset.getDataYear(), dataset.getSourcePage(),
                dataset.getSourceSha256(), categories, journals, conferences, dataset.getCreatedAt(),
                latest == null ? null : latest.getOperatorUserId());
    }

    public CcfReferenceImporter.ImportResult importUpload(CurrentUser operator, MultipartFile file,
            String currentPassword) {
        Path staged = null;
        String sha256 = null;
        try {
            verifyPassword(operator.userId(), currentPassword);
            validateFile(file);
            Path root = Path.of(fileProperties.getTempDir()).toAbsolutePath().normalize().resolve("ccf-reference");
            Files.createDirectories(root);
            setPermissions(root, true);
            staged = Files.createTempFile(root, "ccf-upload-", ".json.partial");
            setPermissions(staged, false);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = copyBounded(file, staged, digest);
            if (size == 0) throw new BusinessException("CCF JSON 不能为空");
            sha256 = HexFormat.of().formatHex(digest.digest());
            if (specification.enforceExpectedSha256() && !specification.expectedSha256().equals(sha256)) {
                throw new BusinessException("CCF JSON 与配置的固定版本 SHA-256 不一致");
            }
            CcfReferenceUploadParser.ParsedDataset parsed;
            try (InputStream input = Files.newInputStream(staged)) {
                parsed = parser.parse(input, specification.expectedYear());
            }
            return importer.importValidated(parsed, sha256, operator.userId());
        } catch (BusinessException ex) {
            recordFailure(operator.userId(), sha256);
            throw ex;
        } catch (IllegalArgumentException ex) {
            recordFailure(operator.userId(), sha256);
            throw new BusinessException(ex.getMessage());
        } catch (Exception ex) {
            recordFailure(operator.userId(), sha256);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "CCF 参考数据导入失败");
        } finally {
            if (staged != null) {
                try { Files.deleteIfExists(staged); } catch (IOException ignored) { }
            }
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("CCF JSON 不能为空");
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new BusinessException("只允许上传 .json 文件");
        }
        if (file.getSize() > specification.maxUploadBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "CCF JSON 超过 5 MiB 限制");
        }
    }

    private long copyBounded(MultipartFile file, Path target, MessageDigest digest) throws IOException {
        long total = 0;
        byte[] buffer = new byte[16 * 1024];
        try (InputStream input = file.getInputStream();
                OutputStream output = Files.newOutputStream(target, StandardOpenOption.WRITE)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > specification.maxUploadBytes()) {
                    throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "CCF JSON 超过 5 MiB 限制");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private void verifyPassword(Long userId, String currentPassword) {
        UserAccount account = users.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Current account not found"));
        if (currentPassword == null || !passwords.matches(currentPassword, account.getPasswordHash())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "当前密码不正确");
        }
    }

    private void recordFailure(Long operatorId, String sha256) {
        try {
            audit.record(operatorId, CcfReferenceImporter.AUDIT_ACTION, null, false,
                    "CCF reference import failed" + (sha256 == null ? "" : ", sha256=" + sha256));
        } catch (RuntimeException ignored) {
            // Audit failure must not replace the controlled import error.
        }
    }

    private void setPermissions(Path path, boolean directory) {
        try {
            Files.setPosixFilePermissions(path, directory
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and non-POSIX file systems rely on the application-owned temporary directory ACL.
        }
    }

    public record ReferenceDataStatus(boolean configured, String dataName, Integer dataYear, String source,
            String sha256, int categoryCount, int journalCount, int conferenceCount,
            java.time.LocalDateTime importedAt, Long importedByUserId) {}
}
