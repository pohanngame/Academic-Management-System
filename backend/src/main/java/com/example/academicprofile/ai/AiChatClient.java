package com.example.academicprofile.ai;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Component
public class AiChatClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String SYSTEM_PROMPT = """
            你是一个严谨的高校教师个人材料写作助手。
            你只能根据用户提供的教师资料组织内容，不要编造不存在的论文、项目、专利或证书。
            输出应适合放入正式 Word 文档，结构清晰，语言稳重。
            """;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public AiChatClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String generate(String prompt) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI API key is not configured");
        }
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .retryOnConnectionFailure(false)
                    .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .readTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .writeTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .build();
            Map<String, Object> payload = Map.of(
                    "model", properties.getModel(),
                    "max_tokens", properties.getMaxTokens(),
                    "temperature", 0.3,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", prompt)));
            Request request = new Request.Builder()
                    .url(normalizedBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .post(RequestBody.create(objectMapper.writeValueAsBytes(payload), JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                            "AI request failed: HTTP " + response.code() + " " + errorSummary(responseBody));
                }
                String content = extractContent(responseBody);
                if (!StringUtils.hasText(content)) {
                    throw new BusinessException("AI response content is empty");
                }
                return content.trim();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI request failed: " + ex.getMessage());
        }
    }

    static int promptCharacterCount(String prompt) {
        return SYSTEM_PROMPT.length() + (prompt == null ? 0 : prompt.length());
    }

    private String extractContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).get("message");
        return message == null || message.get("content") == null ? null : message.get("content").asText();
    }

    private String errorSummary(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.get("error");
            if (error != null) {
                JsonNode message = error.get("message");
                if (message != null && message.isTextual()) {
                    return limit(message.asText(), 300);
                }
            }
        } catch (IOException ignored) {
        }
        return limit(responseBody, 300);
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
