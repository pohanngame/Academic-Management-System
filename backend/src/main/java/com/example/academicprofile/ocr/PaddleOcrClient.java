package com.example.academicprofile.ocr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Component
public class PaddleOcrClient {

    private final OcrProperties properties;
    private final ObjectMapper objectMapper;

    public PaddleOcrClient(OcrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String recognize(Path filePath, String mimeType) {
        if (!properties.isEnabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PaddleOCR service is disabled");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PaddleOCR service URL is not configured");
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .writeTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();

        try {
            RequestBody fileBody = RequestBody.create(Files.readAllBytes(filePath), MediaType.parse(mimeType));
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filePath.getFileName().toString(), fileBody)
                    .build();
            Request request = new Request.Builder()
                    .url(normalizedBaseUrl() + "/ocr")
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                            "PaddleOCR request failed: HTTP " + response.code());
                }
                String text = parseText(responseBody);
                if (!StringUtils.hasText(text)) {
                    throw new BusinessException("PaddleOCR returned empty text");
                }
                return text;
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PaddleOCR service unavailable: " + ex.getMessage());
        }
    }

    private String parseText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.isTextual()) {
            return root.asText();
        }
        for (String field : List.of("text", "result", "data")) {
            JsonNode node = root.get(field);
            if (node != null && node.isTextual()) {
                return node.asText();
            }
        }
        List<String> lines = new ArrayList<>();
        collectText(root, lines);
        return String.join("\n", lines);
    }

    private void collectText(JsonNode node, List<String> lines) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode text = node.get("text");
            if (text != null && text.isTextual() && StringUtils.hasText(text.asText())) {
                lines.add(text.asText());
            }
            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), lines));
        } else if (node.isArray()) {
            node.forEach(child -> collectText(child, lines));
        }
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
