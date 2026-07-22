package com.example.academicprofile.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class AiChatClientLocalMockTest {

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void startLocalOpenAiCompatibleMock() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"本地 OpenAI-compatible mock 内容\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopLocalMock() {
        server.stop(0);
    }

    @Test
    void callsOnlyLocalOpenAiCompatibleEndpoint() throws IOException {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("local-test-key");
        properties.setModel("local-mock-model");
        properties.setMaxTokens(512);
        properties.setTimeoutSeconds(5);
        AiChatClient client = new AiChatClient(properties, new ObjectMapper());

        String generated = client.generate("本地测试提示词");

        assertEquals("本地 OpenAI-compatible mock 内容", generated);
        assertTrue(requestBody.get().contains("local-mock-model"));
        assertTrue(requestBody.get().contains("本地测试提示词"));
        assertEquals(512, new ObjectMapper().readTree(requestBody.get()).path("max_tokens").asInt());
    }

    @Test
    void invalidMaxTokensFallBackToTheSafeDefault() {
        AiProperties properties = new AiProperties();

        properties.setMaxTokens(0);
        assertEquals(AiProperties.DEFAULT_MAX_TOKENS, properties.getMaxTokens());
        properties.setMaxTokens(AiProperties.MAX_SAFE_MAX_TOKENS + 1);
        assertEquals(AiProperties.DEFAULT_MAX_TOKENS, properties.getMaxTokens());
        properties.setMaxTokens(2048);
        assertEquals(2048, properties.getMaxTokens());
    }
}
