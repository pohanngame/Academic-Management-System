package com.example.academicprofile.backup;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;

@Component
public class DatabaseBackupSource {

    private final BackupProperties properties;
    private final DataSourceProperties datasource;
    private final JdbcTemplate jdbc;

    public DatabaseBackupSource(
            BackupProperties properties,
            DataSourceProperties datasource,
            DataSource dataSource) {
        this.properties = properties;
        this.datasource = datasource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void dump(Path sqlFile, Path work) throws Exception {
        DatabaseLocation location = parseLocation(datasource.getUrl());
        Path defaults = work.resolve("mysql-client.cnf");
        Path stderr = work.resolve("mysqldump.stderr");
        String config = "[client]\n"
                + "host=\"" + optionValue(location.host()) + "\"\n"
                + "port=" + location.port() + "\n"
                + "user=\"" + optionValue(datasource.getUsername()) + "\"\n"
                + "password=\"" + optionValue(datasource.getPassword()) + "\"\n";
        Files.writeString(defaults, config, StandardCharsets.UTF_8);
        restrictOwnerOnly(defaults);
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    properties.getMysqldumpExecutable(),
                    "--defaults-extra-file=" + defaults,
                    "--single-transaction",
                    "--quick",
                    "--hex-blob",
                    "--skip-lock-tables",
                    "--no-tablespaces",
                    location.database());
            builder.redirectOutput(sqlFile.toFile());
            builder.redirectError(stderr.toFile());
            process = builder.start();
            boolean completed = process.waitFor(properties.getDumpTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new BusinessException(HttpStatus.GATEWAY_TIMEOUT, "Database backup timed out");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(sqlFile) || Files.size(sqlFile) == 0) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Database backup failed");
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(defaults);
            Files.deleteIfExists(stderr);
        }
    }

    public String flywayVersion() {
        try {
            return jdbc.queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
                    String.class);
        } catch (RuntimeException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Flyway schema version is unavailable");
        }
    }

    public String databaseVersion() {
        return jdbc.queryForObject("SELECT VERSION()", String.class);
    }

    private DatabaseLocation parseLocation(String jdbcUrl) {
        try {
            if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.startsWith("jdbc:mysql://")) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String database = uri.getPath();
            if (!StringUtils.hasText(uri.getHost()) || database == null || database.length() < 2) {
                throw new IllegalArgumentException();
            }
            return new DatabaseLocation(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 3306,
                    database.substring(1));
        } catch (RuntimeException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Database backup configuration is invalid");
        }
    }

    private String optionValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private void restrictOwnerOnly(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Production Linux receives explicit 0600; Windows inherits its directory ACL.
        }
    }

    private record DatabaseLocation(String host, int port, String database) {
    }
}
