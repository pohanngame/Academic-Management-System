package com.example.academicprofile.ai;

import java.time.LocalDateTime;

public record AiSettingsResponse(
        boolean configured,
        String providerName,
        String baseUrl,
        String modelName,
        boolean keyConfigured,
        String maskedApiKey,
        LocalDateTime updatedAt) {

    public static AiSettingsResponse empty() {
        return new AiSettingsResponse(false, null, null, null, false, null, null);
    }

    public static AiSettingsResponse from(AiProviderConfig config) {
        String lastFour = config.getApiKeyLastFour();
        return new AiSettingsResponse(
                true,
                config.getProviderName(),
                config.getBaseUrl(),
                config.getModelName(),
                true,
                lastFour == null ? "****" : "****" + lastFour,
                config.getUpdatedAt());
    }
}
