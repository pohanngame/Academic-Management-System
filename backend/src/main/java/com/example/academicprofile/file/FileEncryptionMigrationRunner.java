package com.example.academicprofile.file;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.files", name = "migration-enabled", havingValue = "true")
public class FileEncryptionMigrationRunner implements ApplicationRunner {
    private final FileEncryptionMigrationService migration;
    private final ConfigurableApplicationContext context;

    public FileEncryptionMigrationRunner(
            FileEncryptionMigrationService migration,
            ConfigurableApplicationContext context) {
        this.migration = migration;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        migration.migrateAll();
        context.close();
    }
}
