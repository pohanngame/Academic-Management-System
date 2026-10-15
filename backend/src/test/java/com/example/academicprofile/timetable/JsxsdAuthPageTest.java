package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.academicprofile.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;

/**
 * 认证页有多个登录表单且字段同名时，lt/execution 只能取自包含
 * `id="pwdEncryptSalt"` 的账号登录表单，不能取页面最后一次（或最前一次）出现的同名值。
 */
class JsxsdAuthPageTest {

    private HttpServer server;
    private JsxsdClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        TimetableProperties properties = new TimetableProperties();
        properties.setAuthBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        client = new JsxsdClient(properties, new JsxsdPasswordCipher(new java.security.SecureRandom()), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respondLoginPage(String body) {
        server.createContext("/authserver/login", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
    }

    /**
     * 样例页：前面的其他登录表单是 e1s1，账号登录表单里是 e2s2；
     * 实际取到的必须是 e2s2（账号表单里的值）。
     */
    @Test
    void ltAndExecutionComeFromTheFormThatCarriesPwdEncryptSalt() throws IOException {
        respondLoginPage("<html><body>"
                + "<form action=\"/otherLogin\" method=\"post\">"
                + "  <input type=\"hidden\" name=\"lt\" value=\"e1s1\">"
                + "  <input type=\"hidden\" name=\"execution\" value=\"e1s1\">"
                + "</form>"
                + "<form action=\"/authserver/login\" method=\"post\">"
                + "  <input type=\"hidden\" name=\"lt\" value=\"e2s2\">"
                + "  <input type=\"hidden\" name=\"execution\" value=\"e2s2\">"
                + "  <input type=\"hidden\" id=\"pwdEncryptSalt\" value=\"somesalt12\">"
                + "</form>"
                + "</body></html>");

        TimetableSyncAttemptStore.JsxsdAuthPage authPage = client.openAuthPage();

        assertThat(authPage.lt()).isEqualTo("e2s2");
        assertThat(authPage.execution()).isEqualTo("e2s2");
        assertThat(authPage.pwdEncryptSalt()).isEqualTo("somesalt12");
    }

    /** 账号表单在后面时也一样；不依赖表单先后顺序。 */
    @Test
    void valuesAreTakenRegardlessOfFormOrder() throws IOException {
        respondLoginPage("<html><body>"
                + "<form action=\"/authserver/login\" method=\"post\">"
                + "  <input type=\"hidden\" id=\"pwdEncryptSalt\" value=\"saltv\">"
                + "  <input type=\"hidden\" name=\"lt\" value=\"good-lt\">"
                + "  <input type=\"hidden\" name=\"execution\" value=\"good-exec\">"
                + "</form>"
                + "<form action=\"/otherLogin\" method=\"post\">"
                + "  <input type=\"hidden\" name=\"lt\" value=\"bad-lt\">"
                + "  <input type=\"hidden\" name=\"execution\" value=\"bad-exec\">"
                + "</form>"
                + "</body></html>");

        TimetableSyncAttemptStore.JsxsdAuthPage authPage = client.openAuthPage();

        assertThat(authPage.lt()).isEqualTo("good-lt");
        assertThat(authPage.execution()).isEqualTo("good-exec");
    }

    /** 整页没有含 pwdEncryptSalt 的表单时，仍按原规则报错。 */
    @Test
    void missingPwdEncryptSaltStillFails() throws IOException {
        respondLoginPage("<html><body>"
                + "<form action=\"/otherLogin\" method=\"post\">"
                + "  <input type=\"hidden\" name=\"lt\" value=\"e1s1\">"
                + "</form>"
                + "</body></html>");

        assertThatThrownBy(() -> client.openAuthPage())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pwdEncryptSalt");
    }

    /** 账号表单里没有 lt/execution 时取空串，不退回其他表单的值。 */
    @Test
    void missingFieldsInTheAccountFormDoNotFallBackToOtherForms() throws IOException {
        respondLoginPage("<html><body>"
                + "<form action=\"/otherLogin\" method=\"post\">"
                + "  <input type=\"hidden\" name=\"lt\" value=\"e1s1\">"
                + "  <input type=\"hidden\" name=\"execution\" value=\"e1s1\">"
                + "</form>"
                + "<form action=\"/authserver/login\" method=\"post\">"
                + "  <input type=\"hidden\" id=\"pwdEncryptSalt\" value=\"somesalt12\">"
                + "</form>"
                + "</body></html>");

        TimetableSyncAttemptStore.JsxsdAuthPage authPage = client.openAuthPage();

        assertThat(authPage.lt()).isEmpty();
        assertThat(authPage.execution()).isEmpty();
    }
}
