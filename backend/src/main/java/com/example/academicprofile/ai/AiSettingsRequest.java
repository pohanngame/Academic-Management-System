package com.example.academicprofile.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiSettingsRequest(
        @NotBlank @Size(max = 128) String providerName,
        @NotBlank @Size(max = AiBaseUrlValidator.MAX_URL_LENGTH) String baseUrl,
        @NotBlank @Size(max = 255) String modelName,
        @Size(max = 4096) String apiKey) {

    @Override
    public String toString() {
        return "AiSettingsRequest[providerName=" + providerName
                + ", baseUrl=[REDACTED]"
                + ", modelName=" + modelName
                + ", apiKey=[REDACTED]]";
    }
}
