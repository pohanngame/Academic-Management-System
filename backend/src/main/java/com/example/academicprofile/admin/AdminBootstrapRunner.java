package com.example.academicprofile.admin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.admin-bootstrap", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService bootstrapService;
    private final AdminBootstrapProperties properties;
    private final ConfigurableApplicationContext applicationContext;

    public AdminBootstrapRunner(
            AdminBootstrapService bootstrapService,
            AdminBootstrapProperties properties,
            ConfigurableApplicationContext applicationContext) {
        this.bootstrapService = bootstrapService;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            bootstrapService.execute(properties);
            System.out.println("SUPER_ADMIN bootstrap completed.");
        } catch (RuntimeException ex) {
            exitCode = 1;
            System.err.println("SUPER_ADMIN bootstrap failed: " + ex.getMessage());
        }
        int finalExitCode = exitCode;
        int applicationExitCode = SpringApplication.exit(applicationContext, () -> finalExitCode);
        System.exit(applicationExitCode);
    }
}
