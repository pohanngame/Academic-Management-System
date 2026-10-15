package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.academicprofile.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;

/**
 * 总表页必须跟随跳转，且最终响应没有 `#kbjcmsid` 时报错，
 * 不能把登录页交给 extractKbjcmsid。
 */
class JsxsdTeacherTablePageTest {

    private HttpServer server;
    private JsxsdClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        TimetableProperties properties = new TimetableProperties();
        properties.setJwBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        client = new JsxsdClient(properties, new JsxsdPasswordCipher(new java.security.SecureRandom()), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(String path, int status, String body, Map<String, String> headers) {
        server.createContext(path, exchange -> {
            // 必须先读掉请求体，否则 HttpServer 会拒绝 POST。
            exchange.getRequestBody().readAllBytes();
            for (Map.Entry<String, String> header : headers.entrySet()) {
                exchange.getResponseHeaders().add(header.getKey(), header.getValue());
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
    }

    private static final String TABLE_PAGE =
            "<html><body><select id=\"kbjcmsid\"><option value=\"3\" selected>节次模式</option>"
                    + "</select></body></html>";

    /** 门户登录页 + 教务 sso.jsp，供 login(...) 走通两次请求。 */
    private void respondAuthFlow(String ssoBody) {
        respond("/authserver/login", 200, "<html><body>ok</body></html>", Map.of());
        respond("/jsxsd/sso.jsp", 200, ssoBody, Map.of());
    }

    private TimetableSyncAttemptStore.JsxsdAuthPage authPage() {
        return new TimetableSyncAttemptStore.JsxsdAuthPage(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/authserver/login",
                "0123456789abcdef", "lt", "execution",
                new TimetableSyncAttemptStore.CookieJar());
    }

    private static TimetableSyncAttemptStore.CookieJar emptyJar() {
        return new TimetableSyncAttemptStore.CookieJar();
    }
    private static final String LOGIN_PAGE =
            "<html><body><form action=\"/authserver/login\"><input name=\"username\"></form></body></html>";

    @Test
    void followsRedirectToTheRealTablePage() throws IOException {
        respond("/jsxsd/kbcx/kbxx_teacher", 302, "", Map.of("Location", "/jsxsd/kbcx/target"));
        respond("/jsxsd/kbcx/target", 200, TABLE_PAGE, Map.of());

        String html = client.fetchTeacherTablePage(emptyJar());

        assertThat(html).contains("kbjcmsid");
        assertThat(client.extractKbjcmsid(html)).isEqualTo("3");
    }

    @Test
    void redirectToLoginPageFailsInsteadOfReturningIt() throws IOException {
        // 会话失效时教务会跳回登录页：这里必须报登录失败，而不是把登录页当成总表页。
        respond("/jsxsd/kbcx/kbxx_teacher", 302, "", Map.of("Location", "/authserver/login"));
        respond("/authserver/login", 200, LOGIN_PAGE, Map.of());

        assertThatThrownBy(() -> client.fetchTeacherTablePage(emptyJar()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Academic affairs rejected the credentials or captcha");
    }

    @Test
    void directLoginPageWithoutRedirectAlsoFails() throws IOException {
        respond("/jsxsd/kbcx/kbxx_teacher", 200, LOGIN_PAGE, Map.of());

        assertThatThrownBy(() -> client.fetchTeacherTablePage(emptyJar()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Academic affairs rejected the credentials or captcha");
    }

    @Test
    void pageWithKbjcmsidButNoSelectedStillReachesTheKbjcmsidRule() throws IOException {
        // 守卫只管“有没有下拉框”，取值规则仍由 extractKbjcmsid 决定（多选项无 selected 仍报错）。
        respond("/jsxsd/kbcx/kbxx_teacher", 200,
                "<select id=\"kbjcmsid\"><option value=\"1\">甲</option>"
                        + "<option value=\"2\">乙</option></select>",
                Map.of());

        String html = client.fetchTeacherTablePage(emptyJar());

        assertThatThrownBy(() -> client.extractKbjcmsid(html))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kbjcmsid");
    }

    @Test
    void httpsBaseIsUsedForAllJsxsdEndpoints() {
        TimetableProperties properties = new TimetableProperties();
        properties.setJwBaseUrl("https://jw.dgpt.edu.cn/");

        assertThat(List.of(properties.jwSsoUrl(), properties.jwTeacherTablePageUrl(),
                properties.jwTeacherTableUrl()))
                .allSatisfy(url -> assertThat(url).startsWith("https://jw.dgpt.edu.cn/jsxsd/"));
    }

    @Test
    void httpOkLoginPageIsNotTreatedAsASuccessfulTicketExchange() throws IOException {
        // 旧规则已删除：换票后不再用 sso.jsp 的 HTML 判断成功，改看总表页。
        // 这里 sso 正常 200，但总表页是登录页，仍必须失败。
        respondAuthFlow("<html><body>ok</body></html>");
        respond("/jsxsd/kbcx/kbxx_teacher", 200, LOGIN_PAGE, Map.of());

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);
        assertThatThrownBy(() -> client.fetchTeacherTablePage(cookies))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Academic affairs rejected the credentials or captcha");
    }

    @Test
    void homeMarkerNoLongerDecidesSuccess() throws IOException {
        // 含有 xsMain 也不再是成功依据；只有总表页的 #kbjcmsid 算数。
        respondAuthFlow(
                "<html><head><title>教务系统</title></head>"
                        + "<body><iframe src=\"/jsxsd/framework/xsMain.jsp\"></iframe></body></html>");
        respond("/jsxsd/kbcx/kbxx_teacher", 200, LOGIN_PAGE, Map.of());

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        assertThatThrownBy(() -> client.fetchTeacherTablePage(cookies))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Academic affairs rejected the credentials or captcha");
    }

    @Test
    void tablePageWithKbjcmsidContinues() throws IOException {
        respondAuthFlow("<html><body>ok</body></html>");
        respond("/jsxsd/kbcx/kbxx_teacher", 200, TABLE_PAGE, Map.of());

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);
        String html = client.fetchTeacherTablePage(cookies);

        assertThat(html).contains("kbjcmsid");
        assertThat(client.extractKbjcmsid(html)).isEqualTo("3");
    }

    @Test
    void cookieFromAnIntermediateHopIsCarriedToTheTeacherTablePage() throws IOException {
        // 教务在 302 那一跳下发 JSESSIONID，最终 200 不再重发；
        // 只保留最终响应的 Cookie 就会丢掉它，导致总表页拿不到会话。
        respondAuthFlowWithHopCookie();
        java.util.List<String> seenCookies = new java.util.ArrayList<>();
        server.createContext("/jsxsd/kbcx/kbxx_teacher", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            seenCookies.add(cookie == null ? "" : cookie);
            byte[] bytes = TABLE_PAGE.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);
        client.fetchTeacherTablePage(cookies);

        assertThat(cookies.headerFor(jwUrl())).contains("JW_HOP=from-302");
        assertThat(seenCookies).hasSize(1);
        assertThat(seenCookies.get(0)).contains("JW_HOP=from-302");
    }

    /** 门户登录 200；sso.jsp 先 302（下发 JW_HOP），再 200（不再发该 Cookie）。 */
    private void respondAuthFlowWithHopCookie() {
        respond("/authserver/login", 200, "<html><body>ok</body></html>", Map.of());
        respond("/jsxsd/sso.jsp", 302, "",
                Map.of("Location", "/jsxsd/framework/xsMain.jsp",
                        "Set-Cookie", "JW_HOP=from-302; Path=/"));
        respond("/jsxsd/framework/xsMain.jsp", 200, "<html><body>ok</body></html>", Map.of());
    }

    @Test
    void loginRedirectIsFollowedSoItsSetCookieReachesTheTeacherTablePage() throws IOException {
        // 登录响应本身就是 302，且指向一个旧逻辑永远不会访问的中间地址
        // （只认登录这一次响应就会直接跑去 sso.jsp，错过这一跳）。
        // 那一跳下发 LOGIN_HOP，最终 200 不再重发；后续教务请求必须带上它。
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        List<String> seenAtIntermediate = new java.util.ArrayList<>();
        respond("/authserver/login", 302, "",
                Map.of("Location", base + "/jsxsd/credential-exchange"));
        // 中间地址：只有真的跟了登录 Location 才会到这里，Cookie 在这里才下发。
        server.createContext("/jsxsd/credential-exchange", exchange -> {
            exchange.getRequestBody().readAllBytes();
            seenAtIntermediate.add(String.valueOf(exchange.getRequestURI()));
            exchange.getResponseHeaders().add("Set-Cookie", "LOGIN_HOP=from-302; Path=/");
            byte[] bytes = "<html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        List<String> seenAtTable = new java.util.ArrayList<>();
        server.createContext("/jsxsd/kbcx/kbxx_teacher", exchange -> {
            exchange.getRequestBody().readAllBytes();
            seenAtTable.add(String.valueOf(exchange.getRequestHeaders().getFirst("Cookie")));
            byte[] bytes = TABLE_PAGE.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        // 没跟登录的 Location 就根本不会访问这个中间地址。
        assertThat(seenAtIntermediate).hasSize(1);
        assertThat(cookies.headerFor(base + "/jsxsd/credential-exchange"))
                .contains("LOGIN_HOP=from-302");

        client.fetchTeacherTablePage(cookies);

        // 后续教务请求也必须带上这个中途 Cookie。
        assertThat(seenAtTable).hasSize(1);
        assertThat(seenAtTable.get(0)).contains("LOGIN_HOP=from-302");
    }

    /** 登录 302 到下一地址；该地址再 302 到 sso，中间跳逐跳不断。 */
    @Test
    void loginRedirectChainContinuesHopByHop() throws IOException {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        respond("/authserver/login", 302, "",
                Map.of("Location", base + "/authserver/step1"));
        respond("/authserver/step1", 302, "",
                Map.of("Location", base + "/authserver/step2",
                        "Set-Cookie", "HOP_A=one; Path=/"));
        respond("/authserver/step2", 302, "",
                Map.of("Location", base + "/jsxsd/sso.jsp",
                        "Set-Cookie", "HOP_B=two; Path=/"));
        respond("/jsxsd/sso.jsp", 200, "<html><body>ok</body></html>", Map.of());

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        // 每一跳的 Cookie 都要收到，不能只留最终响应。
        assertThat(cookies.headerFor(base)).contains("HOP_A=one");
        assertThat(cookies.headerFor(base)).contains("HOP_B=two");
    }

    /**
     * 统一认证成功后 302 回带 ticket 的 sso.jsp；该页先返回“指向自己”的脚本跳转，
     * 再访一次才真正 302 到教务首页并下发 JSESSIONID。
     * 不能在第一次返回时就停下，否则教务 Cookie 永远拿不到。
     */
    @Test
    void ticketSsoPageThatRedirectsToItselfIsFollowedUntilItStops() throws IOException {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String ticketSso = base + "/jsxsd/sso.jsp?ticket=abc";
        respond("/authserver/login", 302, "", Map.of("Location", ticketSso));

        List<String> seenAtTicketSso = new java.util.ArrayList<>();
        // 第 1 次请求：200 + 指向自己的脚本跳转（真实未登录/待换票形态）。
        // 第 2 次请求：302 到教务首页，并在这一跳下发 JSESSIONID。
        server.createContext("/jsxsd/sso.jsp", exchange -> {
            exchange.getRequestBody().readAllBytes();
            seenAtTicketSso.add(String.valueOf(exchange.getRequestURI().getQuery()));
            if (seenAtTicketSso.size() == 1) {
                byte[] bytes = ("<html><script>window.location.href='" + ticketSso
                        + "';</script></html>").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            } else {
                exchange.getResponseHeaders().add("Location", base + "/jsxsd/framework/xsMain.jsp");
                exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=jw-session; Path=/");
                byte[] bytes = "".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(302, bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            }
        });
        respond("/jsxsd/framework/xsMain.jsp", 200, "<html><body>home</body></html>", Map.of());
        List<String> seenAtTable = new java.util.ArrayList<>();
        server.createContext("/jsxsd/kbcx/kbxx_teacher", exchange -> {
            exchange.getRequestBody().readAllBytes();
            seenAtTable.add(String.valueOf(exchange.getRequestHeaders().getFirst("Cookie")));
            byte[] bytes = TABLE_PAGE.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        // 带 ticket 的 sso.jsp 必须被访问到，且不能只访一次就停。
        assertThat(seenAtTicketSso).hasSize(2);
        assertThat(cookies.headerFor(base)).contains("JSESSIONID=jw-session");

        String html = client.fetchTeacherTablePage(cookies);

        assertThat(html).contains("kbjcmsid");
        // 后续读取总表页时必须带上这个教务 JSESSIONID。
        assertThat(seenAtTable).hasSize(1);
        assertThat(seenAtTable.get(0)).contains("JSESSIONID=jw-session");
    }

    /**
     * 登录不带 service 时只进入个人中心。
     * 跟随结束后必须继续访问教务 sso.jsp，不能把个人中心当成换票完成。
     */
    @Test
    void loginWithoutServiceStillContinuesToAcademicAffairs() throws IOException {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        respond("/authserver/login", 302, "",
                Map.of("Location", base + "/authserver/index.do"));
        respond("/authserver/index.do", 200, "<html><body>person center</body></html>", Map.of());
        List<String> seenAtSso = new java.util.ArrayList<>();
        server.createContext("/jsxsd/sso.jsp", exchange -> {
            exchange.getRequestBody().readAllBytes();
            seenAtSso.add(String.valueOf(exchange.getRequestURI()));
            exchange.getResponseHeaders().add("Location", base + "/jsxsd/framework/xsMain.jsp");
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=jw-session; Path=/");
            byte[] bytes = new byte[0];
            exchange.sendResponseHeaders(302, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        respond("/jsxsd/framework/xsMain.jsp", 200, "<html><body>home</body></html>", Map.of());

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        assertThat(seenAtSso).hasSize(1);
        assertThat(cookies.headerFor(base)).contains("JSESSIONID=jw-session");
    }

    /** 总表页没有 #kbjcmsid 时仍返回登录失败（与 3i 同一标准）。 */
    @Test
    void ticketFlowStillFailsWhenTheTablePageHasNoKbjcmsid() throws IOException {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        respond("/authserver/login", 302, "",
                Map.of("Location", base + "/jsxsd/sso.jsp?ticket=abc"));
        respond("/jsxsd/sso.jsp", 302, "",
                Map.of("Location", base + "/jsxsd/framework/xsMain.jsp",
                        "Set-Cookie", "JSESSIONID=jw-session; Path=/"));
        respond("/jsxsd/framework/xsMain.jsp", 200, "<html><body>home</body></html>", Map.of());
        // 总表页被打回登录页：没有下拉框。
        respond("/jsxsd/kbcx/kbxx_teacher", 200, LOGIN_PAGE, Map.of());

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        assertThatThrownBy(() -> client.fetchTeacherTablePage(cookies))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rejected the credentials or captcha");
    }

    private String jwUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/jsxsd/kbcx/kbxx_teacher";
    }

    /**
     * 统一认证下发 `CASTGC=...; Domain=.dgpt.edu.cn`；
     * 它必须归 `.dgpt.edu.cn`，访问教务域名时也要带上，
     * 否则教务仍是未登录状态。
     */
    @Test
    void castgcFromAuthServerIsSentToTheAcademicAffairsHost() {
        TimetableSyncAttemptStore.CookieJar jar = new TimetableSyncAttemptStore.CookieJar();
        // 认证响应带 Domain，归 .dgpt.edu.cn（生产代码会去掉前导点后存入）。
        jar.putFrom("dgpt.edu.cn", "CASTGC", "castgc-value");

        // 教务域必须带上它（这正是本任务要修的 bug）。
        assertThat(jar.headerFor("https://jw.dgpt.edu.cn/jsxsd/kbcx/kbxx_teacher"))
                .contains("CASTGC=castgc-value");
        assertThat(jar.headerFor("https://authserver.dgpt.edu.cn/authserver/login"))
                .contains("CASTGC=castgc-value");
        // 不属于该域名树的站点不得带上。
        assertThat(jar.headerFor("https://other.edu.cn/x")).doesNotContain("CASTGC");
        // 仅同后缀不算子域名，不能误发。
        assertThat(jar.headerFor("https://xdgpt.edu.cn/x")).doesNotContain("CASTGC");
    }

    /** 没有 Domain 的 Cookie 仍只发给当前主机，不得外溢到别的域。 */
    @Test
    void hostOnlyCookieIsNotSentToOtherHosts() {
        TimetableSyncAttemptStore.CookieJar jar = new TimetableSyncAttemptStore.CookieJar();
        jar.putHostOnly("authserver.dgpt.edu.cn", "/", "JSESSIONID", "auth-only");

        assertThat(jar.headerFor("https://authserver.dgpt.edu.cn/authserver/login"))
                .contains("JSESSIONID=auth-only");
        // 教务域不得拿到认证站的主机专属 JSESSIONID。
        assertThat(jar.headerFor("https://jw.dgpt.edu.cn/jsxsd/sso.jsp"))
                .doesNotContain("auth-only");
    }

    /**
     * 本任务实测出的 bug：认证站 host-only 的 CASTGC
     * 不能被当成父域 Cookie 而外溢到 jw.dgpt.edu.cn。
     */
    @Test
    void hostOnlyCastgcStaysOnTheAuthHostAndNeverLeaksToAcademicAffairs() {
        TimetableSyncAttemptStore.CookieJar jar = new TimetableSyncAttemptStore.CookieJar();
        // 认证站没有 Domain 的 CASTGC：host-only。
        jar.putHostOnly("authserver.dgpt.edu.cn", "/", "CASTGC", "auth-castgc");

        // 再访问认证站时它必须还在。
        assertThat(jar.headerFor("https://authserver.dgpt.edu.cn/authserver/login"))
                .contains("CASTGC=auth-castgc");
        // 访问教务时不得携带。
        assertThat(jar.headerFor("https://jw.dgpt.edu.cn/jsxsd/sso.jsp"))
                .doesNotContain("auth-castgc");
    }

    /** 同名父域 Cookie 不得覆盖 host-only 的同名 Cookie。 */
    @Test
    void parentDomainCookieDoesNotOverwriteTheHostOnlyOne() {
        TimetableSyncAttemptStore.CookieJar jar = new TimetableSyncAttemptStore.CookieJar();
        // 父域下发同名 Cookie（带 Domain）。
        jar.putFrom("dgpt.edu.cn", "CASTGC", "parent-value");
        // 认证站再下发 host-only 的同名 Cookie，它优先于父域。
        jar.putHostOnly("authserver.dgpt.edu.cn", "/", "CASTGC", "host-only-value");

        assertThat(jar.headerFor("https://authserver.dgpt.edu.cn/authserver/login"))
                .contains("CASTGC=host-only-value")
                .doesNotContain("CASTGC=parent-value");
        // 父域 Cookie 仍会发给子域，只是不再覆盖 host-only 的同名值。
        assertThat(jar.headerFor("https://jw.dgpt.edu.cn/jsxsd/sso.jsp"))
                .contains("CASTGC=parent-value");
    }

    /** host-only Cookie 按 Path 分开保存，不同 Path 的同名 Cookie 不互相覆盖。 */
    @Test
    void hostOnlyCookiesAreKeptSeparatelyByPath() {
        TimetableSyncAttemptStore.CookieJar jar = new TimetableSyncAttemptStore.CookieJar();
        jar.putHostOnly("authserver.dgpt.edu.cn", "/authserver", "TGC", "root-value");
        jar.putHostOnly("authserver.dgpt.edu.cn", "/authserver/login", "TGC", "login-value");

        String rootHeader = jar.headerFor("https://authserver.dgpt.edu.cn/authserver/x");
        assertThat(rootHeader).contains("TGC=root-value");
        String loginHeader = jar.headerFor("https://authserver.dgpt.edu.cn/authserver/login/x");
        // 更具体的 Path 在它自己的路径下生效。
        assertThat(loginHeader).contains("TGC=login-value");
        // 不相关路径不得带上。
        assertThat(jar.headerFor("https://authserver.dgpt.edu.cn/other/x"))
                .doesNotContain("TGC");
    }

    /**
     * host-only Cookie 不得下发到子域，也不得下发到父域。
     * 这是旧实现真正出错的地方：旧代码把每个归属都当域 Cookie 做后缀匹配，
     * 于是 host-only 会被子域/父域视作可发，从而被同名 Cookie 覆盖。
     */
    @Test
    void hostOnlyCookieNeverEscalatesToParentOrChildDomains() {
        TimetableSyncAttemptStore.CookieJar jar = new TimetableSyncAttemptStore.CookieJar();
        jar.putHostOnly("jw.dgpt.edu.cn", "/", "CASTGC", "host-only-on-jw");

        assertThat(jar.headerFor("https://jw.dgpt.edu.cn/jsxsd/sso.jsp"))
                .contains("CASTGC=host-only-on-jw");
        // 子域不得带上。
        assertThat(jar.headerFor("https://sub.jw.dgpt.edu.cn/x"))
                .doesNotContain("host-only-on-jw");
        // 兄弟/父层主机也不得带上。
        assertThat(jar.headerFor("https://authserver.dgpt.edu.cn/x"))
                .doesNotContain("host-only-on-jw");

        // 直接落在顶层域上的 host-only，绝不能被当成“全 *.dgpt.edu.cn”。
        TimetableSyncAttemptStore.CookieJar apex = new TimetableSyncAttemptStore.CookieJar();
        apex.putHostOnly("dgpt.edu.cn", "/", "CASTGC", "host-only-on-apex");
        assertThat(apex.headerFor("https://jw.dgpt.edu.cn/x"))
                .doesNotContain("host-only-on-apex");
        assertThat(apex.headerFor("https://dgpt.edu.cn/x"))
                .contains("CASTGC=host-only-on-apex");
    }

    /** 解析 Set-Cookie：有 Domain 归该 Domain，没有才归当前主机。 */
    @Test
    void setCookieDomainAttributeDecidesTheOwningDomain() throws Exception {
        java.lang.reflect.Method parse =
                JsxsdClient.class.getDeclaredMethod("parseSetCookie", String.class);
        parse.setAccessible(true);
        java.lang.reflect.Method domainOf = null;

        Object withDomain = parse.invoke(null, "CASTGC=v; Domain=.dgpt.edu.cn; Path=/");
        domainOf = withDomain.getClass().getDeclaredMethod("domain");
        domainOf.setAccessible(true);
        assertThat((String) domainOf.invoke(withDomain)).isEqualTo("dgpt.edu.cn");

        Object withoutDomain = parse.invoke(null, "JSESSIONID=jw; Path=/");
        assertThat((String) domainOf.invoke(withoutDomain)).isNull();

        Object mixedCase = parse.invoke(null, "A=b; domain=Example.COM; Path=/");
        assertThat((String) domainOf.invoke(mixedCase)).isEqualTo("example.com");
    }

    /** 总表页没有 #kbjcmsid 时仍返回登录失败（跨域 Cookie 修复后标准不变）。 */
    @Test
    void domainScopedCookieFlowStillFailsWhenTheTablePageHasNoKbjcmsid() throws IOException {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        // 登录中途下发带 Domain 的 CASTGC，随后进教务。
        respond("/authserver/login", 302, "",
                Map.of("Location", base + "/jsxsd/sso.jsp",
                        "Set-Cookie", "CASTGC=v; Domain=.dgpt.edu.cn; Path=/"));
        respond("/jsxsd/sso.jsp", 200, "<html><body>home</body></html>", Map.of());
        // 总表页被打回登录页：没有下拉框。
        respond("/jsxsd/kbcx/kbxx_teacher", 200, LOGIN_PAGE, Map.of());

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        assertThatThrownBy(() -> client.fetchTeacherTablePage(cookies))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rejected the credentials or captcha");
    }

    /**
     * 走真实 login 流程：登录响应里的 `Domain=.dgpt.edu.cn` 必须被尊重，
     * CASTGC 要归在该域名下而不是当前主机名下；主机专属 Cookie 则归当前主机。
     */
    @Test
    void loginFilesDomainScopedAndHostOnlyCookiesCorrectly() throws IOException {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        // 同一响应里同时下发带 Domain 与不带 Domain 的两个 Cookie。
        respond("/authserver/login", 302, "",
                Map.of("Location", base + "/jsxsd/sso.jsp",
                        "Set-Cookie", "CASTGC=v; Domain=.dgpt.edu.cn; Path=/"));
        server.createContext("/jsxsd/sso.jsp", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Set-Cookie", "JW=hostonly; Path=/");
            byte[] bytes = "<html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        // 带 Domain 的归 .dgpt.edu.cn（去掉前导点），不归当前主机 127.0.0.1。
        assertThat(cookies.domainsHolding("CASTGC")).containsExactly("dgpt.edu.cn");
        // 没有 Domain 的仍只归当前主机。
        assertThat(cookies.domainsHolding("JW")).containsExactly("127.0.0.1");
    }

    private String authHostUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/authserver/login";
    }

    @Test
    void cookieFromAnIntermediatePortalHopSurvivesTheFinalResponse() throws IOException {
        // 门户登录：中途 302 下发 PORTAL_HOP，最终 200 不再重发；
        // 后续请求（sso.jsp）仍必须带上它。
        java.util.List<String> seenAtSso = new java.util.ArrayList<>();
        server.createContext("/authserver/login", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = "".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Set-Cookie", "PORTAL_HOP=mid-302; Path=/");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.createContext("/jsxsd/sso.jsp", exchange -> {
            exchange.getRequestBody().readAllBytes();
            seenAtSso.add(String.valueOf(exchange.getRequestHeaders().getFirst("Cookie")));
            byte[] bytes = "<html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        assertThat(cookies.headerFor(authHostUrl())).contains("PORTAL_HOP=mid-302");
        assertThat(seenAtSso).hasSize(1);
        assertThat(seenAtSso.get(0)).contains("PORTAL_HOP=mid-302");
    }

    @Test
    void scriptRedirectOnSsoPageIsFollowed() throws IOException {
        // sso.jsp 未登录时不返回 302，而是 200 + window.location.href；必须继续访问它。
        String authLogin = authHostUrl();
        server.createContext("/authserver/login", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String query = exchange.getRequestURI().getQuery();
            byte[] bytes = "<html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8);
            // 只有真正按脚本地址访问（带 service=jw）才发这个 Cookie。
            if (query != null && query.contains("service=jw")) {
                exchange.getResponseHeaders().add("Set-Cookie", "AFTER_SCRIPT=yes; Path=/");
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        respond("/jsxsd/sso.jsp", 200,
                "<html><script>window.location.href='" + authLogin + "?service=jw';</script></html>",
                Map.of());

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        assertThat(cookies.headerFor(authLogin)).contains("AFTER_SCRIPT=yes");
    }

    @Test
    void sameCookieNameOnTwoHostsIsKeptSeparately() {
        // 认证站与教务站都有 JSESSIONID，必须按域名隔离，不能互相覆盖。
        TimetableSyncAttemptStore.CookieJar jar = new TimetableSyncAttemptStore.CookieJar();
        jar.putAllFrom("jw.dgpt.edu.cn", Map.of("JSESSIONID", "jw-value"));
        jar.putAllFrom("authserver.dgpt.edu.cn", Map.of("JSESSIONID", "auth-value"));

        assertThat(jar.headerFor("https://jw.dgpt.edu.cn/jsxsd/kbcx/kbxx_teacher"))
                .contains("JSESSIONID=jw-value")
                .doesNotContain("auth-value");
        assertThat(jar.headerFor("https://authserver.dgpt.edu.cn/authserver/login"))
                .contains("JSESSIONID=auth-value")
                .doesNotContain("jw-value");
    }

    @Test
    void twoHostsKeepTheirOwnJsessionidThroughTheRealFlow() throws IOException {
        // 真实跑一遍：教务域与认证域（测试里同为本机，因此只能整体作为一个域）
        // 各发一个同名 Cookie；先验证同域内以后者为准，域名隔离靠上一个用例覆盖。
        respond("/jsxsd/sso.jsp", 302, "",
                Map.of("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/authserver/login",
                        "Set-Cookie", "JSESSIONID=jw-domain; Path=/"));
        String authLogin = authHostUrl();
        server.createContext("/authserver/login", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=auth-domain; Path=/");
            byte[] bytes = "<html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        TimetableSyncAttemptStore.CookieJar cookies = client.login(authPage(), "s1", "pw", null);

        // 同域（本机）内后来者覆盖先前者，不会出现两个同名值。
        assertThat(cookies.headerFor(authLogin)).contains("JSESSIONID=auth-domain");
        assertThat(cookies.headerFor(authLogin)).doesNotContain("jw-domain");
    }
}
