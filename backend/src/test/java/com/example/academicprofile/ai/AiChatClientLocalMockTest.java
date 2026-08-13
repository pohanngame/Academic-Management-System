package com.example.academicprofile.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.academicprofile.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class AiChatClientLocalMockTest {

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>(
            "{\"choices\":[{\"message\":{\"content\":\"本地 OpenAI-compatible mock 内容\"}}]}");
    private final AtomicReference<String> redirectLocation = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicInteger responseDelayMillis = new AtomicInteger();

    @BeforeEach
    void startLocalOpenAiCompatibleMock() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (responseDelayMillis.get() > 0) {
                try {
                    Thread.sleep(responseDelayMillis.get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            int status = responseStatus.get();
            String json = responseBody.get();
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            if (redirectLocation.get() != null) {
                exchange.getResponseHeaders().set("Location", redirectLocation.get());
            }
            exchange.sendResponseHeaders(status, response.length);
            try {
                exchange.getResponseBody().write(response);
            } catch (IOException ignored) {
                // The timeout test closes the client connection before the mock writes.
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopLocalMock() {
        server.stop(0);
    }

    @Test
    void callsOnlyLocalOpenAiCompatibleEndpointWithRuntimeConfiguration() throws IOException {
        AiChatClient client = client(5, 5);
        AiRuntimeConfig config = runtimeConfig("local-mock-model", "local-test-key");

        String generated = client.generate("本地测试提示词", config);

        assertEquals("本地 OpenAI-compatible mock 内容", generated);
        assertTrue(requestBody.get().contains("local-mock-model"));
        assertTrue(requestBody.get().contains("本地测试提示词"));
        assertEquals("Bearer local-test-key", authorization.get());
        assertEquals(512, new ObjectMapper().readTree(requestBody.get()).path("max_tokens").asInt());
    }

    @Test
    void connectionTestAcceptsOrdinaryResponse() throws IOException {
        AiChatClient client = client(5, 5);
        AiRuntimeConfig config = runtimeConfig("model", "connection-test-key");

        AiConnectionTestResult result = client.testConnection(config);

        assertTrue(result.success());
        assertEquals("连接测试成功", result.message());
        assertEquals(1, new ObjectMapper().readTree(requestBody.get()).path("max_tokens").asInt());
        assertFalse(result.toString().contains("connection-test-key"));
        assertFalse(config.toString().contains("connection-test-key"));
    }

    @Test
    void connectionTestAcceptsReasoningOnlyAndEmptyContentResponses() {
        AiChatClient client = client(5, 5);
        AiRuntimeConfig config = runtimeConfig("deepseek-v4-flash", "reasoning-test-key");

        setResponse("""
                {"choices":[{"message":{
                  "role":"assistant",
                  "content":"",
                  "reasoning_content":"正在分析"
                }}]}
                """);
        assertTrue(client.testConnection(config).success());

        setResponse("{\"choices\":[{\"message\":{\"content\":null}}]}");
        assertTrue(client.testConnection(config).success());

        setResponse("{\"choices\":[{\"message\":{\"role\":\"assistant\"}}]}");
        assertTrue(client.testConnection(config).success());
    }

    @Test
    void generateStillRejectsEmptyContent() {
        setResponse("""
                {"choices":[{"message":{
                  "role":"assistant",
                  "content":"",
                  "reasoning_content":"正在分析"
                }}]}
                """);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client(5, 5).generate("生成正式内容", runtimeConfig("model", "generation-test-key")));

        assertEquals(
                "AI 模型未返回正文，可能是思考模型耗尽输出额度。请改用非思考模型或联系管理员提高输出上限",
                exception.getMessage());
        assertFalse(exception.getMessage().contains("generation-test-key"));
        assertFalse(exception.getMessage().contains("正在分析"));
    }

    @Test
    void officialDeepSeekV4GenerationUsesNonThinkingMode() {
        AiRuntimeConfig officialDeepSeek = new AiRuntimeConfig(
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-v4-flash",
                "test-key");
        AiRuntimeConfig compatibleProvider = new AiRuntimeConfig(
                "compatible",
                "https://example.com/v1",
                "deepseek-v4-flash",
                "test-key");

        assertTrue(AiChatClient.shouldDisableThinking(officialDeepSeek));
        assertFalse(AiChatClient.shouldDisableThinking(compatibleProvider));
        assertEquals(
                "disabled",
                ((java.util.Map<?, ?>) AiChatClient.requestPayload("prompt", officialDeepSeek, 4096)
                        .get("thinking")).get("type"));
        assertFalse(AiChatClient.requestPayload("prompt", compatibleProvider, 4096)
                .containsKey("thinking"));
    }

    @Test
    void connectionTestRejectsMissingChoicesEmptyChoicesAndMissingMessage() {
        AiChatClient client = client(5, 5);
        AiRuntimeConfig config = runtimeConfig("model", "invalid-structure-key");

        setResponse("{\"id\":\"missing-choices\"}");
        assertInvalidConnectionResult(client.testConnection(config), "missing-choices", "invalid-structure-key");

        setResponse("{\"choices\":[]}");
        assertInvalidConnectionResult(client.testConnection(config), "choices", "invalid-structure-key");

        setResponse("{\"choices\":[{\"finish_reason\":\"length\"}]}");
        assertInvalidConnectionResult(client.testConnection(config), "finish_reason", "invalid-structure-key");
    }

    @Test
    void connectionTestRejectsInvalidJsonWithoutLeakingResponse() {
        setResponse("not-json-sensitive-provider-body");

        AiConnectionTestResult result = client(5, 5)
                .testConnection(runtimeConfig("model", "invalid-json-key"));

        assertInvalidConnectionResult(result, "not-json-sensitive-provider-body", "invalid-json-key");
    }

    @Test
    void connectionTestControlsAuthenticationRateLimitAndServerFailures() {
        AiChatClient client = client(5, 5);
        AiRuntimeConfig config = runtimeConfig("model", "key-not-returned");
        setResponse("{\"error\":{\"message\":\"供应商内部详情不应透传\"}}");

        responseStatus.set(401);
        AiConnectionTestResult unauthorized = client.testConnection(config);
        assertFalse(unauthorized.success());
        assertEquals("AI 服务认证失败，请检查 API Key", unauthorized.message());
        assertFalse(unauthorized.message().contains("key-not-returned"));
        assertFalse(unauthorized.message().contains("供应商内部详情"));

        responseStatus.set(403);
        assertEquals("AI 服务认证失败，请检查 API Key", client.testConnection(config).message());

        responseStatus.set(429);
        assertEquals("AI 服务请求过于频繁，请稍后重试", client.testConnection(config).message());

        responseStatus.set(500);
        AiConnectionTestResult unavailable = client.testConnection(config);
        assertFalse(unavailable.success());
        assertEquals("AI 服务暂时不可用", unavailable.message());
        assertFalse(unavailable.message().contains("供应商内部详情"));
    }

    @Test
    void connectionTestControlsTimeout() {
        responseDelayMillis.set(1500);
        AiConnectionTestResult result = client(1, 1).testConnection(runtimeConfig("model", "timeout-key"));

        assertFalse(result.success());
        assertEquals("AI 服务连接或响应超时", result.message());
        assertFalse(result.message().contains("timeout-key"));
    }

    @Test
    void connectionTestDoesNotFollowRedirects() {
        responseStatus.set(302);
        redirectLocation.set("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect-target");
        setResponse("redirect-body-must-not-leak");

        AiConnectionTestResult result = client(5, 5)
                .testConnection(runtimeConfig("model", "redirect-test-key"));

        assertFalse(result.success());
        assertEquals("AI 服务返回了不允许的重定向", result.message());
        assertFalse(result.message().contains("redirect-body-must-not-leak"));
        assertFalse(result.message().contains("redirect-test-key"));
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

    private AiChatClient client(int connectTimeoutSeconds, int timeoutSeconds) {
        AiProperties properties = new AiProperties();
        properties.setMaxTokens(512);
        properties.setConnectTimeoutSeconds(connectTimeoutSeconds);
        properties.setTimeoutSeconds(timeoutSeconds);
        AiBaseUrlValidator validator = new AiBaseUrlValidator(
                host -> new InetAddress[] { InetAddress.getByName(host) },
                true);
        return new AiChatClient(properties, new ObjectMapper(), validator);
    }

    private AiRuntimeConfig runtimeConfig(String model, String key) {
        return new AiRuntimeConfig(
                "local-mock",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/",
                model,
                key);
    }

    private void setResponse(String body) {
        responseBody.set(body);
    }

    private void assertInvalidConnectionResult(
            AiConnectionTestResult result,
            String responseMarker,
            String apiKey) {
        assertFalse(result.success());
        assertEquals("AI 服务返回了无效响应", result.message());
        assertFalse(result.message().contains(responseMarker));
        assertFalse(result.message().contains(apiKey));
        assertFalse(result.toString().contains(apiKey));
    }
}
