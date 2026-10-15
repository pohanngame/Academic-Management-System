package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaRequest;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaResponse;
import com.example.academicprofile.timetable.TimetableDtos.SyncRequest;
import com.example.academicprofile.timetable.TimetableSyncAttemptStore.JsxsdAuthPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * 认证页需要滑块时的行为：
 * 返回背景图 + 小图（不取 getCaptcha.htl）、校验失败不发登录请求、
 * 校验成功后登录请求仍不含原始密码。
 */
class JsxsdSliderCaptchaTest {

    /** 滑块校验接口的完整路径（含 /common/）。 */
    private static final String VERIFY_PATH = "/authserver/common/verifySliderCaptcha.htl";
    /** 是否需要验证码的完整路径（**不带** /common/，实测带 /common/ 会 404）。 */
    private static final String CHECK_PATH = "/authserver/checkNeedCaptcha.htl";
    /** 打开滑块的完整路径（含 /common/）。 */
    private static final String OPEN_SLIDER_PATH = "/authserver/common/openSliderCaptcha.htl";

    private HttpServer server;
    private JsxsdClient client;
    private String authBase;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        authBase = "http://127.0.0.1:" + server.getAddress().getPort();
        TimetableProperties properties = new TimetableProperties();
        properties.setAuthBaseUrl(authBase);
        properties.setJwBaseUrl(authBase);
        client = new JsxsdClient(
                properties,
                new JsxsdPasswordCipher(new java.security.SecureRandom()),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** 小图：任意字节均可，safeSecure 取其最后 16 字节。 */
    private static String base64Image(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) i;
        }
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    /** safeSecure 是 16 字节 AES 密钥；测试里给一个确定的 16 字节值。 */
    private static String safeKey() {
        return "0123456789abcdef";
    }

    private JsxsdAuthPage authPage() {
        return new JsxsdAuthPage(
                authBase + "/authserver/login",
                "0123456789abcdef",
                "lt-value",
                "execution-value",
                new TimetableSyncAttemptStore.CookieJar());
    }

    private void respond(String path, String body) {
        server.createContext(path, exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
    }

    /** 同 respond，但把实际请求的路径记下来，用于断言完整地址。 */
    private void recordJson(List<String> paths, String path, String body) {
        server.createContext(path, exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
    }

    /**
     * 检查验证码的路径恰为不带 /common/ 的地址，
     * 而打开滑块与校验滑块仍走 /common/ 前缀。
     */
    @Test
    void sliderPreparationUsesTheExpectedPaths() throws IOException {
        List<String> paths = new ArrayList<>();
        recordJson(paths, CHECK_PATH, "{\"isNeed\":true}");
        recordJson(paths, OPEN_SLIDER_PATH,
                "{\"bigImage\":\"" + base64Image(64) + "\",\"smallImage\":\""
                        + base64Image(40) + "\"}");

        JsxsdClient.CaptchaChallenge challenge = client.checkCaptcha(authPage(), "jg-account");

        assertThat(challenge.required()).isTrue();
        assertThat(paths).containsExactly(
                "/authserver/checkNeedCaptcha.htl",
                "/authserver/common/openSliderCaptcha.htl");
        // 检查验证码恰为不带 /common/ 的地址（实测带 /common/ 会 404）。
        assertThat(paths.get(0)).isEqualTo("/authserver/checkNeedCaptcha.htl");
        assertThat(paths.get(0)).doesNotContain("/common/");
        // 另两个地址仍含 /common/。
        assertThat(paths.get(1)).contains("/authserver/common/");
        assertThat(VERIFY_PATH).contains("/authserver/common/");
    }

    /** checkNeedCaptcha 说需要验证时，走 openSliderCaptcha 并返回两张图。 */
    @Test
    void sliderChallengeReturnsBothImagesAndNeverCallsGetCaptcha() throws IOException {
        respond(CHECK_PATH, "{\"isNeed\":true}");
        String piece = base64Image(40);
        respond(OPEN_SLIDER_PATH,
                "{\"bigImage\":\"" + base64Image(64) + "\",\"smallImage\":\"" + piece + "\"}");
        // 记录是否有人访问 getCaptcha.htl。
        List<String> visited = new ArrayList<>();
        server.createContext("/authserver/getCaptcha.htl", exchange -> {
            visited.add("getCaptcha");
            byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        JsxsdClient.CaptchaChallenge challenge = client.checkCaptcha(authPage(), "jg-account");

        assertThat(challenge.required()).isTrue();
        assertThat(challenge.backgroundBase64()).isNotBlank();
        assertThat(challenge.pieceBase64()).isEqualTo(piece);
        // safeSecure = 小图解码后的最后 16 字节。
        byte[] decoded = java.util.Base64.getDecoder().decode(piece);
        String expectedSafeSecure = new String(
                java.util.Arrays.copyOfRange(decoded, decoded.length - 16, decoded.length),
                StandardCharsets.ISO_8859_1);
        assertThat(challenge.safeSecure()).isEqualTo(expectedSafeSecure);
        // 绝不能再取 getCaptcha.htl。
        assertThat(visited).isEmpty();
    }

    /** errorCode 不为 1 时校验失败。 */
    @Test
    void sliderVerificationFailsWhenErrorCodeIsNotOne() throws IOException {
        respond(VERIFY_PATH, "{\"errorCode\":0}");

        boolean verified = client.verifySliderCaptcha(
                authPage(), safeKey(), 300, 120, List.of(Map.of("x", 1, "y", 2)));

        assertThat(verified).isFalse();
    }

    /** errorCode=1 时校验通过。 */
    @Test
    void sliderVerificationPassesOnlyOnErrorCodeOne() throws IOException {
        respond(VERIFY_PATH, "{\"errorCode\":1}");

        boolean verified = client.verifySliderCaptcha(
                authPage(), safeKey(), 300, 120, List.of(Map.of("x", 1, "y", 2)));

        assertThat(verified).isTrue();
    }

    /**
     * 校验请求打的是 /authserver/common/verifySliderCaptcha.htl，
     * 不得再请求不带 /common/ 的地址。
     */
    @Test
    void verificationHitsTheCommonVerifyPathOnly() throws IOException {
        List<String> paths = new ArrayList<>();
        server.createContext(VERIFY_PATH, exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = "{\"errorCode\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        boolean verified = client.verifySliderCaptcha(
                authPage(), safeKey(), 300, 120, List.of(Map.of("x", 1)));

        assertThat(verified).isTrue();
        assertThat(paths).hasSize(1);
        assertThat(paths.get(0)).isEqualTo("/authserver/common/verifySliderCaptcha.htl");
    }

    /** 校验请求体用 sign 字段承载密文，且绝不再提交 captcha。 */
    @Test
    void verificationBodyCarriesSignAndNeverCaptcha() throws IOException {
        List<String> bodies = new ArrayList<>();
        server.createContext(VERIFY_PATH, exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            byte[] bytes = "{\"errorCode\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        client.verifySliderCaptcha(authPage(), safeKey(), 300, 120, List.of(Map.of("x", 1)));

        assertThat(bodies).hasSize(1);
        String body = bodies.get(0);
        // 字段名必须是 sign，且不能再出现 captcha。
        assertThat(body).startsWith("sign=");
        assertThat(body).contains("sign=");
        assertThat(body).doesNotContain("captcha");
        // 仍是密文，不含滑块明文。
        assertThat(body).doesNotContain("canvasLength");
        assertThat(body).doesNotContain("moveLength");
        assertThat(body).doesNotContain("\"tracks\"");
    }

    // ---- 服务层：滑块失败不得发登录请求 ----

    private TimetableSyncService newService(JsxsdClient jsxsd) {
        return new TimetableSyncService(
                jsxsd,
                new JsxsdPasswordCipher(new java.security.SecureRandom()),
                mock(KbtableParser.class),
                new TimetableSyncAttemptStore(),
                mock(TimetableTeacherLinkMapper.class),
                mock(TimetableImportBatchMapper.class),
                mock(TimetableCellMapper.class),
                mock(TimetableImportTeacherMapper.class),
                mock(AdminAuditService.class));
    }

    /** 滑块校验失败时，绝不能调用 login。 */
    @Test
    void failedSliderVerificationNeverSendsTheLoginRequest() {
        JsxsdClient jsxsd = mock(JsxsdClient.class);
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString())).thenReturn(
                new JsxsdClient.CaptchaChallenge(true, "YmdpbWFnZQ==", "aW1hZ2U=", "c2FmZQ=="));
        when(jsxsd.verifySliderCaptcha(any(), anyString(), any(), any(), any()))
                .thenReturn(false);
        TimetableSyncService service = newService(jsxsd);

        String attemptId = service.startAttempt(1L, new CaptchaRequest("jg-account")).attemptId();

        assertThatThrownBy(() -> service.sync(1L, new SyncRequest(
                attemptId, "2026-2027-1", "Raw-Password-1", null, 300, 120,
                List.of(Map.of("x", 1)))))
                .isInstanceOf(BusinessException.class);

        verify(jsxsd, never()).login(any(), anyString(), anyString(), any());
        verify(jsxsd, never()).fetchTeacherTablePage(any());
    }

    /** 滑块校验成功后登录；登录请求体里不含原始密码。 */
    @Test
    void successfulSliderVerificationLogsInWithoutTheRawPassword() {
        JsxsdClient jsxsd = mock(JsxsdClient.class);
        String rawPassword = "Raw-Password-1";
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString())).thenReturn(
                new JsxsdClient.CaptchaChallenge(true, "YmdpbWFnZQ==", "aW1hZ2U=", "c2FmZQ=="));
        when(jsxsd.verifySliderCaptcha(any(), anyString(), any(), any(), any()))
                .thenReturn(true);
        // 登录后再让总表页失败，以便断言已经走到了登录这一步。
        when(jsxsd.login(any(), anyString(), anyString(), any())).thenThrow(
                new BusinessException(org.springframework.http.HttpStatus.BAD_GATEWAY, "stop-here"));
        TimetableSyncService service = newService(jsxsd);

        String attemptId = service.startAttempt(1L, new CaptchaRequest("jg-account")).attemptId();

        assertThatThrownBy(() -> service.sync(1L, new SyncRequest(
                attemptId, "2026-2027-1", rawPassword, null, 300, 120,
                List.of(Map.of("x", 1)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("stop-here");

        // 登录确实发生了，且传入的是加密结果而不是原始密码。
        org.mockito.ArgumentCaptor<String> encrypted =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jsxsd).login(any(), eq("jg-account"), encrypted.capture(), any());
        assertThat(encrypted.getValue()).isNotEqualTo(rawPassword);
        assertThat(encrypted.getValue()).doesNotContain(rawPassword);
    }
}
