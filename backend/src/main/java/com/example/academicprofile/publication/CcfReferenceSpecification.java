package com.example.academicprofile.publication;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class CcfReferenceSpecification {
    private static final String METADATA = "reference/ccf/2026/source.properties";
    private final Properties metadata = new Properties();
    private final long maxUploadBytes;
    private final boolean enforceExpectedSha256;

    public CcfReferenceSpecification(
            @Value("${app.publication.ccf.max-upload-bytes:5242880}") long maxUploadBytes,
            @Value("${app.publication.ccf.enforce-expected-sha256:true}") boolean enforceExpectedSha256) {
        this.maxUploadBytes = maxUploadBytes;
        this.enforceExpectedSha256 = enforceExpectedSha256;
        try (InputStream input = new ClassPathResource(METADATA).getInputStream()) {
            metadata.load(input);
        } catch (IOException ex) {
            throw new IllegalStateException("CCF reference metadata is unavailable");
        }
    }

    public long maxUploadBytes() { return maxUploadBytes; }
    public boolean enforceExpectedSha256() { return enforceExpectedSha256; }
    public String datasetKey() { return required("dataset.key"); }
    public String dataName() { return required("dataset.name"); }
    public int expectedYear() { return Integer.parseInt(required("dataset.year")); }
    public String sourceKind() { return required("source.kind"); }
    public String sourcePage() { return required("source.page"); }
    public String expectedSha256() { return required("source.sha256"); }

    private String required(String key) {
        String value = metadata.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("CCF reference metadata is incomplete");
        return value.trim();
    }
}
