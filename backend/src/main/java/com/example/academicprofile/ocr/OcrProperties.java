package com.example.academicprofile.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {

    private boolean enabled = true;
    private String baseUrl = "http://localhost:8866";
    private int timeoutSeconds = 60;
    private int minPdfTextLength = 80;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMinPdfTextLength() {
        return minPdfTextLength;
    }

    public void setMinPdfTextLength(int minPdfTextLength) {
        this.minPdfTextLength = minPdfTextLength;
    }
}
