package com.example.academicprofile.ai;

public record AiRuntimeConfig(
        String providerName,
        String baseUrl,
        String modelName,
        String apiKey) {

    @Override
    public String toString() {
        return "AiRuntimeConfig[providerName=" + providerName
                + ", baseUrl=" + baseUrl
                + ", modelName=" + modelName
                + ", apiKey=[REDACTED]]";
    }
}
