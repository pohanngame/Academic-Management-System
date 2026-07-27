package com.example.academicprofile.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    public static final int DEFAULT_MAX_TOKENS = 1024;
    public static final int MAX_SAFE_MAX_TOKENS = 4096;
    public static final int DEFAULT_MAX_PROMPT_CHARS = 30_000;
    public static final int MAX_SAFE_MAX_PROMPT_CHARS = 100_000;

    private int maxTokens = DEFAULT_MAX_TOKENS;
    private int maxPromptChars = DEFAULT_MAX_PROMPT_CHARS;
    private int connectTimeoutSeconds = 10;
    private int timeoutSeconds = 60;
    private String encryptionKeyFile = "./data/secrets/ai-config.key";

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = isSafe(maxTokens, MAX_SAFE_MAX_TOKENS) ? maxTokens : DEFAULT_MAX_TOKENS;
    }

    public int getMaxPromptChars() {
        return maxPromptChars;
    }

    public void setMaxPromptChars(int maxPromptChars) {
        this.maxPromptChars = isSafe(maxPromptChars, MAX_SAFE_MAX_PROMPT_CHARS)
                ? maxPromptChars
                : DEFAULT_MAX_PROMPT_CHARS;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = positiveOrDefault(timeoutSeconds, 60);
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = positiveOrDefault(connectTimeoutSeconds, 10);
    }

    public String getEncryptionKeyFile() {
        return encryptionKeyFile;
    }

    public void setEncryptionKeyFile(String encryptionKeyFile) {
        this.encryptionKeyFile = encryptionKeyFile;
    }

    private boolean isSafe(int value, int maximum) {
        return value > 0 && value <= maximum;
    }

    private int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
