package com.example.academicprofile.ai;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final AiBaseUrlValidator baseUrlValidator;

    public AiChatClient(
            AiProperties properties,
            ObjectMapper objectMapper,
            AiBaseUrlValidator baseUrlValidator) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.baseUrlValidator = baseUrlValidator;
    }

    public String generate(String prompt, AiRuntimeConfig runtimeConfig) {
        JsonNode response = executeRequest(prompt, runtimeConfig, properties.getMaxTokens());
        return extractGeneratedContent(response);
    }

    public AiConnectionTestResult testConnection(AiRuntimeConfig runtimeConfig) {
        try {
            JsonNode response = executeRequest("请只回复 OK。", runtimeConfig, 1);
            validateConnectionResponse(response);
            return new AiConnectionTestResult(true, "连接测试成功");
        } catch (BusinessException ex) {
            return new AiConnectionTestResult(false, ex.getMessage());
        }
    }

    static int promptCharacterCount(String prompt) {
        return SYSTEM_PROMPT.length() + (prompt == null ? 0 : prompt.length());
    }

    private JsonNode executeRequest(String prompt, AiRuntimeConfig runtimeConfig, int maxTokens) {
        if (runtimeConfig == null || !StringUtils.hasText(runtimeConfig.apiKey())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请先在 AI 模型设置中完成配置");
        }
        String requestUrl = baseUrlValidator.chatCompletionsUrl(runtimeConfig.baseUrl());
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .retryOnConnectionFailure(false)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                    .readTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .writeTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .callTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .build();
            Map<String, Object> payload = requestPayload(prompt, runtimeConfig, maxTokens);
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .header("Authorization", "Bearer " + runtimeConfig.apiKey())
                    .post(RequestBody.create(objectMapper.writeValueAsBytes(payload), JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw controlledHttpFailure(response.code());
                }
                String responseBody = response.body() == null ? "" : response.body().string();
                return parseResponse(responseBody);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SocketTimeoutException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务连接或响应超时");
        } catch (InterruptedIOException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务连接或响应超时");
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "无法连接 AI 服务");
        }
    }

    static Map<String, Object> requestPayload(
            String prompt,
            AiRuntimeConfig runtimeConfig,
            int maxTokens) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", runtimeConfig.modelName());
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", 0.3);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)));
        if (shouldDisableThinking(runtimeConfig)) {
            payload.put("thinking", Map.of("type", "disabled"));
        }
        return payload;
    }

    static boolean shouldDisableThinking(AiRuntimeConfig runtimeConfig) {
        if (runtimeConfig == null || !StringUtils.hasText(runtimeConfig.baseUrl())
                || !StringUtils.hasText(runtimeConfig.modelName())) {
            return false;
        }
        try {
            String host = URI.create(runtimeConfig.baseUrl()).getHost();
            String model = runtimeConfig.modelName().trim().toLowerCase(Locale.ROOT);
            return "api.deepseek.com".equalsIgnoreCase(host)
                    && ("deepseek-v4-flash".equals(model) || "deepseek-v4-pro".equals(model));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private JsonNode parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null) {
                throw invalidResponse();
            }
            return root;
        } catch (IOException ex) {
            throw invalidResponse();
        }
    }

    private void validateConnectionResponse(JsonNode root) {
        extractMessage(root);
    }

    private String extractGeneratedContent(JsonNode root) {
        JsonNode contentNode = extractMessage(root).get("content");
        String content = contentNode == null || contentNode.isNull() ? null : contentNode.asText();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 模型未返回正文，可能是思考模型耗尽输出额度。请改用非思考模型或联系管理员提高输出上限");
        }
        return content.trim();
    }

    private JsonNode extractMessage(JsonNode root) {
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw invalidResponse();
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null || !message.isObject()) {
            throw invalidResponse();
        }
        return message;
    }

    private BusinessException invalidResponse() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务返回了无效响应");
    }

    private BusinessException controlledHttpFailure(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务认证失败，请检查 API Key");
        }
        if (statusCode == 429) {
            return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务请求过于频繁，请稍后重试");
        }
        if (statusCode >= 500) {
            return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用");
        }
        if (statusCode >= 300 && statusCode < 400) {
            return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务返回了不允许的重定向");
        }
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务拒绝了请求");
    }
}
