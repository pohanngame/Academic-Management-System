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

    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-v4-flash";
    private int maxTokens = DEFAULT_MAX_TOKENS;
    private int maxPromptChars = DEFAULT_MAX_PROMPT_CHARS;
    private int timeoutSeconds = 60;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

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
        this.timeoutSeconds = timeoutSeconds;
    }

    private boolean isSafe(int value, int maximum) {
        return value > 0 && value <= maximum;
    }
}
