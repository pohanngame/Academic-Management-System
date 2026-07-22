package com.example.academicprofile.pdf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "app.document-conversion")
public class PdfConversionProperties {

    static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;
    static final int DEFAULT_TIMEOUT_SECONDS = 70;
    static final DataSize DEFAULT_MAX_INPUT_SIZE = DataSize.ofMegabytes(15);
    static final DataSize DEFAULT_MAX_OUTPUT_SIZE = DataSize.ofMegabytes(20);
    private static final DataSize MAX_SAFE_FILE_SIZE = DataSize.ofMegabytes(50);

    private boolean enabled = false;
    private String baseUrl = "http://127.0.0.1:3000";
    private int connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private DataSize maxInputSize = DEFAULT_MAX_INPUT_SIZE;
    private DataSize maxOutputSize = DEFAULT_MAX_OUTPUT_SIZE;

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

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds > 0 && connectTimeoutSeconds <= 30
                ? connectTimeoutSeconds
                : DEFAULT_CONNECT_TIMEOUT_SECONDS;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds > 0 && timeoutSeconds <= 300
                ? timeoutSeconds
                : DEFAULT_TIMEOUT_SECONDS;
    }

    public DataSize getMaxInputSize() {
        return maxInputSize;
    }

    public void setMaxInputSize(DataSize maxInputSize) {
        this.maxInputSize = safeSize(maxInputSize, DEFAULT_MAX_INPUT_SIZE);
    }

    public DataSize getMaxOutputSize() {
        return maxOutputSize;
    }

    public void setMaxOutputSize(DataSize maxOutputSize) {
        this.maxOutputSize = safeSize(maxOutputSize, DEFAULT_MAX_OUTPUT_SIZE);
    }

    private DataSize safeSize(DataSize value, DataSize defaultValue) {
        if (value == null || value.toBytes() <= 0 || value.toBytes() > MAX_SAFE_FILE_SIZE.toBytes()) {
            return defaultValue;
        }
        return value;
    }
}
