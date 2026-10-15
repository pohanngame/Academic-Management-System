package com.example.academicprofile.timetable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 与教务（强智 jsxsd）及统一认证交互。
 * 只在单次请求内使用会话；不写日志、不落库、不回显 Cookie 或票据。
 */
@Component
public class JsxsdClient {

    private static final int MAX_HTML_CHARS = 8 * 1024 * 1024;
    /** safeSecure 取小图解码后的最后 16 字节。 */
    private static final int SAFE_SECURE_BYTES = 16;
    private static final Pattern INPUT_PATTERN = Pattern.compile(
            "<input[^>]*name=[\"']?([A-Za-z0-9_]+)[\"']?[^>]*value=[\"']([^\"']*)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SALT_PATTERN = Pattern.compile(
            "id=[\"']pwdEncryptSalt[\"'][^>]*value=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    /** 定位包含 pwdEncryptSalt 的账号登录表单；lt/execution 只能取自它。 */
    private static final Pattern FORM_PATTERN = Pattern.compile(
            "<form\\b[^>]*>(.*?)</form>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NEED_CAPTCHA_PATTERN = Pattern.compile(
            "\\btrue\\b", Pattern.CASE_INSENSITIVE);
    /** 总表页的时间模式下拉框；已选值才是 kbjcmsid 的合法取值。 */
    private static final Pattern KBJCMSID_SELECT_PATTERN =
            Pattern.compile("<select[^>]*id=[\"']?kbjcmsid[\"']?[^>]*>(.*?)</select>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OPTION_TAG_PATTERN =
            Pattern.compile("<option([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPTION_VALUE_PATTERN =
            Pattern.compile("value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECTED_ATTR_PATTERN =
            Pattern.compile("\\bselected\\b", Pattern.CASE_INSENSITIVE);
    /** 换票跳转链最多跟多少跳。 */
    private static final int MAX_REDIRECTS = 10;
    /** 未登录时 sso.jsp 返回 200 + 这行脚本，而不是 HTTP 302。 */
    private static final Pattern SCRIPT_REDIRECT_PATTERN = Pattern.compile(
            "window\\.location\\.href\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private final TimetableProperties properties;
    private final JsxsdPasswordCipher passwordCipher;
    private final ObjectMapper objectMapper;

    public JsxsdClient(
            TimetableProperties properties,
            JsxsdPasswordCipher passwordCipher,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.passwordCipher = passwordCipher;
        this.objectMapper = objectMapper;
    }

    /** 打开认证页，取出 pwdEncryptSalt、lt、execution 与会话 Cookie。 */
    public TimetableSyncAttemptStore.JsxsdAuthPage openAuthPage() {
        requireEnabled();
        OkHttpClient client = newClient();
        Request request = new Request.Builder()
                .url(properties.authLoginUrl())
                .get()
                .header("User-Agent", "academic-profile-timetable-sync/1.0")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs login page returned HTTP " + response.code());
            }
            String html = readBody(response);
            Map<String, String> inputs = parseHiddenInputs(html);
            String salt = extractSalt(html);
            if (!StringUtils.hasText(salt)) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs login page did not provide pwdEncryptSalt");
            }
            return new TimetableSyncAttemptStore.JsxsdAuthPage(
                    properties.authLoginUrl(),
                    salt,
                    inputs.getOrDefault("lt", ""),
                    inputs.getOrDefault("execution", ""),
                    collectJar(response));
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs login page is unreachable");
        }
    }

    /** 按账号判断是否需要验证；需要时走滑块，不取 getCaptcha.htl。 */
    public CaptchaChallenge checkCaptcha(
            TimetableSyncAttemptStore.JsxsdAuthPage authPage, String username) {
        requireEnabled();
        OkHttpClient client = newClient();
        String checkUrl = trimTrailingSlash(properties.getAuthBaseUrl())
                + "/authserver/checkNeedCaptcha.htl?username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8);
        Request check = new Request.Builder()
                .url(checkUrl)
                .get()
                .header("Cookie", authPage.cookies().headerFor(checkUrl))
                .header("User-Agent", "academic-profile-timetable-sync/1.0")
                .build();
        try (Response response = client.newCall(check).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs captcha check returned HTTP " + response.code());
            }
            boolean required = NEED_CAPTCHA_PATTERN.matcher(body).find()
                    && !body.replace(" ", "").contains("false");
            if (!required) {
                return CaptchaChallenge.notRequired();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs captcha check failed");
        }
        return openSliderCaptcha(client, authPage);
    }

    /**
     * 打开滑块验证，取背景图与小图。
     * safeSecure 是小图解码后的最后 16 字节，只在本次尝试的内存里用到。
     */
    private CaptchaChallenge openSliderCaptcha(
            OkHttpClient client, TimetableSyncAttemptStore.JsxsdAuthPage authPage) {
        String url = trimTrailingSlash(properties.getAuthBaseUrl())
                + "/authserver/common/openSliderCaptcha.htl?ts=" + System.currentTimeMillis();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Cookie", authPage.cookies().headerFor(url))
                .header("User-Agent", "academic-profile-timetable-sync/1.0")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs slider captcha is unavailable");
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            String background = textOrNull(root, "bigImage");
            String piece = textOrNull(root, "smallImage");
            if (background == null || piece == null) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs slider captcha did not provide both images");
            }
            return CaptchaChallenge.slider(
                    background, piece, safeSecureFrom(piece));
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs slider captcha is unavailable");
        }
    }

    /**
     * safeSecure 是小图解码后的**最后 16 字节**。
     * 解不出来（非法 Base64 或不足 16 字节）时不得静默降级，直接报错。
     */
    private static String safeSecureFrom(String pieceBase64) {
        byte[] decoded;
        try {
            decoded = java.util.Base64.getDecoder().decode(pieceBase64);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs slider captcha returned an invalid image");
        }
        if (decoded.length < SAFE_SECURE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs slider captcha returned an invalid image");
        }
        byte[] tail = java.util.Arrays.copyOfRange(
                decoded, decoded.length - SAFE_SECURE_BYTES, decoded.length);
        return new String(tail, StandardCharsets.ISO_8859_1);
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    /**
     * 校验滑块。只有 errorCode=1 才算通过；其余一律返回失败，
     * 由调用方在登录前拦截。提交体是加密字符串，不含任何明文密码。
     */
    public boolean verifySliderCaptcha(
            TimetableSyncAttemptStore.JsxsdAuthPage authPage,
            String safeSecure,
            Integer canvasWidth,
            Integer move,
            java.util.List<java.util.Map<String, Object>> tracks) {
        requireEnabled();
        if (canvasWidth == null || move == null || tracks == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Slider canvas width, move and tracks are required");
        }
        // 与认证页一致：把三个值拼成 JSON 再加密，只提交单个密文。
        String payload;
        try {
            payload = objectMapper.writeValueAsString(java.util.Map.of(
                    "canvasLength", canvasWidth,
                    "moveLength", move,
                    "tracks", tracks));
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to build the slider captcha payload");
        }
        String encrypted = passwordCipher.encrypt(payload, safeSecure).ciphertext();
        OkHttpClient client = newClient();
        String url = trimTrailingSlash(properties.getAuthBaseUrl())
                + "/authserver/common/verifySliderCaptcha.htl";
        RequestBody form = new FormBody.Builder()
                // 认证页校验滑块用的字段名是 sign，不是 captcha（登录表单的 captcha 另说）。
                .add("sign", encrypted)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(form)
                .header("Cookie", authPage.cookies().headerFor(url))
                .header("User-Agent", "academic-profile-timetable-sync/1.0")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs slider captcha verification returned HTTP "
                                + response.code());
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode code = root == null ? null : root.get("errorCode");
            // 只有 errorCode=1 才算通过，其它一律视为失败。
            return code != null && code.asInt(0) == 1;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs slider captcha verification failed");
        }
    }

    /**
     * 提交门户登录，再经教务 sso.jsp 换票据，返回按域名分组的会话 Cookie。
     * 密码与 Cookie 仅在本方法内存在，不返回给调用方之外的任何地方。
     */
    public TimetableSyncAttemptStore.CookieJar login(
            TimetableSyncAttemptStore.JsxsdAuthPage authPage,
            String username,
            String encryptedPassword,
            String captcha) {
        requireEnabled();
        OkHttpClient client = newClient().newBuilder().followRedirects(false).build();
        TimetableSyncAttemptStore.CookieJar cookies = new TimetableSyncAttemptStore.CookieJar();
        HttpUrl loginUrl = TimetableSyncAttemptStore.parse(authPage.loginUrl());
        cookies.putAllFromJar(authPage.cookies());

        FormBody.Builder form = new FormBody.Builder()
                .add("username", username)
                .add("password", encryptedPassword)
                .add("lt", nullToEmpty(authPage.lt()))
                .add("execution", nullToEmpty(authPage.execution()))
                .add("_eventId", "submit")
                .add("cllt", "userNameLogin")
                .add("dllt", "generalLogin");
        if (StringUtils.hasText(captcha)) {
            form.add("captcha", captcha);
        }
        Request login = new Request.Builder()
                .url(loginUrl)
                .post(form.build())
                .header("Cookie", cookies.headerFor(authPage.loginUrl()))
                .header("User-Agent", "academic-profile-timetable-sync/1.0")
                .header("Referer", authPage.loginUrl())
                .build();
        String chainStart = properties.jwSsoUrl();
        try (Response response = client.newCall(login).execute()) {
            // 登录本身就可能返回 302，其 Location 是跳转链的第一站。
            // 不从这里接着走，会漏掉登录后、换票前那几跳下发的 Cookie。
            String location = response.header("Location");
            if (!response.isSuccessful() && !isRedirect(response.code())) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs login returned HTTP " + response.code());
            }
            collectInto(cookies, response);
            if (location != null && location.contains("error")) {
                // 认证页把失败重定向回登录页；不回显响应体，避免带出凭据。
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Academic affairs rejected the credentials or captcha");
            }
            // 登录 302 时从它的 Location 开始继续逐跳跟随。
            if (isRedirect(response.code()) && StringUtils.hasText(location)) {
                chainStart = resolveLocation(authPage.loginUrl(), location.trim());
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs login is unreachable");
        }

        // 从门户进入教务，统一认证再发一次票据；教务会话独立。
        // 跳转链上每一跳都可能下发 Cookie，必须逐跳收集，不能只留最终响应。
        // 起点可能是登录响应的 Location，也可能是 sso.jsp；沿用同一个跟随逻辑。
        try {
            String finalUrl = followChainWithCookies(client, chainStart, cookies);
            // 登录链没有经过教务入口时，只是停在个人中心，必须再换票。
            HttpUrl stoppedAt = HttpUrl.parse(finalUrl);
            if (stoppedAt == null || !stoppedAt.encodedPath().startsWith("/jsxsd/")) {
                followChainWithCookies(client, properties.jwSsoUrl(), cookies);
            }
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs single sign-on is unreachable");
        }
        return cookies;
    }

    /**
     * 手工跟随跳转链，把每一跳响应的 Set-Cookie 按域名收进 cookies。
     * 同时支持 HTTP 跳转与页面脚本跳转（sso.jsp 未登录时返回 200 + window.location.href）。
     * 关闭自动跳转才能看到中间响应，否则中途下发的 Cookie 会丢。
     */
    private String followChainWithCookies(
            OkHttpClient client,
            String startUrl,
            TimetableSyncAttemptStore.CookieJar cookies) throws IOException {
        String url = startUrl;
        // 上一次访问的地址。带 ticket 的 sso.jsp 有时会先返回“自己”的脚本跳转，
        // 必须允许再访一次才会真正往下跳；只有连续两次同一地址才当自环停下。
        String previousUrl = null;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .header("Cookie", cookies.headerFor(url))
                    .header("User-Agent", "academic-profile-timetable-sync/1.0")
                    .build();
            String next;
            try (Response response = client.newCall(request).execute()) {
                collectInto(cookies, response);

                if (isRedirect(response.code())) {
                    String location = response.header("Location");
                    if (!StringUtils.hasText(location)) {
                        throw new BusinessException(HttpStatus.BAD_GATEWAY,
                                "Academic affairs single sign-on redirect has no location");
                    }
                    next = resolveLocation(url, location.trim());
                } else if (!response.isSuccessful()) {
                    throw new BusinessException(HttpStatus.BAD_GATEWAY,
                            "Academic affairs single sign-on returned HTTP " + response.code());
                } else {
                    // 未登录时教务不返回 302，而是 200 + 脚本跳转；不跟就会停在登录页。
                    String body = response.body() == null ? "" : response.body().string();
                    String scriptTarget = extractScriptRedirect(body);
                    if (scriptTarget == null) {
                        return url;
                    }
                    next = resolveLocation(url, scriptTarget);
                }
            }
            // 连续两次拿到同一地址才是真自环；只重复一次时不拦，否则会错过后续跳转。
            if (next.equals(url) && next.equals(previousUrl)) {
                return url;
            }
            previousUrl = url;
            url = next;
        }
        throw new BusinessException(HttpStatus.BAD_GATEWAY,
                "Academic affairs single sign-on redirected too many times");
    }

    /** 取出页面脚本里的跳转地址；没有则返回 null。 */
    static String extractScriptRedirect(String html) {
        if (!StringUtils.hasText(html)) {
            return null;
        }
        Matcher matcher = SCRIPT_REDIRECT_PATTERN.matcher(html);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    /** Location 可能是相对路径，需要按当前地址补全。 */
    private static String resolveLocation(String currentUrl, String location) {
        HttpUrl base = HttpUrl.parse(currentUrl);
        HttpUrl resolved = base == null ? HttpUrl.parse(location) : base.resolve(location);
        if (resolved == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs single sign-on redirect is invalid");
        }
        return resolved.toString();
    }

    /**
     * 读取教师总表页，取 `#kbjcmsid` 的已选值作为时间模式。
     * 该值随学期页面返回，不能把节次组 0102 当作此参数。
     */
    public String fetchTeacherTablePage(TimetableSyncAttemptStore.CookieJar jwCookies) {
        requireEnabled();
        OkHttpClient client = newClient();
        String url = properties.jwTeacherTablePageUrl();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Cookie", jwCookies.headerFor(url))
                .header("User-Agent", "academic-profile-timetable-sync/1.0")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs teacher table page returned HTTP " + response.code());
            }
            String html = response.body().string();
            // 总表页没有 #kbjcmsid 就是换票失败（例如被打回登录页），不能交给解析。
            if (!KBJCMSID_SELECT_PATTERN.matcher(html).find()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Academic affairs rejected the credentials or captcha");
            }
            return html;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs teacher table page is unreachable");
        }
    }

    /**
     * 从教师总表页 HTML 中取 `#kbjcmsid` 的值。
     * 优先取带 `selected` 的选项；没有选中项且只有一个带 value 的选项时用该选项；
     * 多个选项都没有 `selected` 时仍报错，不取第一个，也不回退成节次组 0102。
     */
    public String extractKbjcmsid(String teacherTablePageHtml) {
        if (!StringUtils.hasText(teacherTablePageHtml)) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs teacher table page did not provide kbjcmsid");
        }
        Matcher select = KBJCMSID_SELECT_PATTERN.matcher(teacherTablePageHtml);
        if (!select.find()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs teacher table page did not provide kbjcmsid");
        }
        Matcher option = OPTION_TAG_PATTERN.matcher(select.group(1));
        String onlyValue = null;
        int valueCount = 0;
        while (option.find()) {
            String attributes = option.group(1);
            Matcher value = OPTION_VALUE_PATTERN.matcher(attributes);
            if (!value.find() || !StringUtils.hasText(value.group(1).trim())) {
                continue;
            }
            String candidate = value.group(1).trim();
            // 明确选中项直接返回：selected 与 value 的先后顺序不固定，两者都支持。
            if (SELECTED_ATTR_PATTERN.matcher(attributes).find()) {
                return candidate;
            }
            valueCount++;
            onlyValue = candidate;
        }
        // 只有唯一一个带 value 的选项时才敢用它；多个候选不猜，也不回退成 0102。
        if (valueCount == 1) {
            return onlyValue;
        }
        throw new BusinessException(HttpStatus.BAD_GATEWAY,
                "Academic affairs teacher table page did not provide kbjcmsid");
    }

    /**
     * POST 教师总表数据，返回一个学期的 #kbtable HTML。
     * `kbjcmsid` 用总表页已选值；只固定 zc1=1、zc2=30；星期与节次留空表示整周。
     */
    public String fetchTeacherTable(
            TimetableSyncAttemptStore.CookieJar jwCookies, String semesterCode, String kbjcmsid) {
        requireEnabled();
        OkHttpClient client = newClient();
        String url = properties.jwTeacherTableUrl();
        FormBody body = new FormBody.Builder()
                .add("xnxqh", semesterCode)
                .add("kbjcmsid", kbjcmsid)
                .add("skyx", "")
                .add("skjsid", "")
                .add("skjs", "")
                .add("zc1", "1")
                .add("zc2", "30")
                .add("skxq1", "")
                .add("skxq2", "")
                .add("jc1", "")
                .add("jc2", "")
                .build();
        Request request = new Request.Builder()
                .url(properties.jwTeacherTableUrl())
                .post(body)
                .header("Cookie", jwCookies.headerFor(url))
                .header("User-Agent", "academic-profile-timetable-sync/1.0")
                .header("Referer", trimTrailingSlash(properties.getJwBaseUrl())
                        + "/jsxsd/kbcx/kbxx_teacher")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs teacher table returned HTTP " + response.code());
            }
            String html = readBody(response);
            if (!html.toLowerCase().contains("kbtable")) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs teacher table did not contain #kbtable; "
                                + "the session may have expired");
            }
            return html;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs teacher table is unreachable");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Timetable sync is disabled");
        }
    }

    private OkHttpClient newClient() {
        Duration timeout = Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds()));
        return new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .followRedirects(true)
                .build();
    }

    private String readBody(Response response) throws IOException {
        if (response.body() == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs returned an empty response");
        }
        String html = response.body().string();
        if (html.length() > MAX_HTML_CHARS) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs response is too large");
        }
        return html;
    }

    /** 从单次响应收集 Cookie，按响应所属域名或当前主机归档。 */
    private void collectInto(
            TimetableSyncAttemptStore.CookieJar jar, Response response) {
        HttpUrl url = response.request().url();
        // 带 Domain 的 Cookie（如 CASTGC; Domain=.dgpt.edu.cn）必须归该域名；
        // 没有 Domain 的是 host-only，只归当前主机，不得升到父域。
        for (String setCookie : response.headers("Set-Cookie")) {
            ParsedCookie parsed = parseSetCookie(setCookie);
            if (parsed == null) {
                continue;
            }
            if (parsed.domain() == null) {
                jar.putHostOnly(url.host(), parsed.path(), parsed.name(), parsed.value());
            } else {
                jar.putFrom(parsed.domain(), parsed.name(), parsed.value());
            }
        }
    }

    /** Set-Cookie 的解析结果；domain 为 null 表示响应头没有 Domain（host-only）。 */
    private record ParsedCookie(String name, String value, String domain, String path) {
    }

    /**
     * 解析单条 Set-Cookie。Domain 前导点去掉后原样保留（一个域名条目），
     * 取值时再按“.域名及其子域名”匹配。
     */
    private static ParsedCookie parseSetCookie(String setCookie) {
        if (!StringUtils.hasText(setCookie)) {
            return null;
        }
        String[] parts = setCookie.split(";");
        int equals = parts[0].indexOf('=');
        if (equals <= 0) {
            return null;
        }
        String name = parts[0].substring(0, equals).trim();
        String value = parts[0].substring(equals + 1).trim();
        if (name.isEmpty() || value.isEmpty()) {
            return null;
        }
        String domain = null;
        String path = null;
        for (int i = 1; i < parts.length; i++) {
            String attribute = parts[i].trim();
            if (attribute.length() > 7 && attribute.regionMatches(true, 0, "Domain=", 0, 7)) {
                domain = normalizeDomain(attribute.substring(7).trim());
            } else if (attribute.length() > 5
                    && attribute.regionMatches(true, 0, "Path=", 0, 5)) {
                String value0 = attribute.substring(5).trim();
                path = value0.isEmpty() ? null : value0;
            }
        }
        return new ParsedCookie(name, value, domain, path);
    }

    /** 去掉前导点与尾部点：`Domain=.dgpt.edu.cn` 按 `dgpt.edu.cn` 存一个条目。 */
    private static String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        String trimmed = domain.trim();
        while (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    private TimetableSyncAttemptStore.CookieJar collectJar(Response response) {
        TimetableSyncAttemptStore.CookieJar jar = new TimetableSyncAttemptStore.CookieJar();
        collectInto(jar, response);
        return jar;
    }

    private Map<String, String> parseHiddenInputs(String html) {
        String form = findAccountLoginForm(html);
        Map<String, String> inputs = new LinkedHashMap<>();
        if (form == null) {
            return inputs;
        }
        Matcher matcher = INPUT_PATTERN.matcher(form);
        while (matcher.find()) {
            inputs.putIfAbsent(matcher.group(1), matcher.group(2));
        }
        return inputs;
    }

    /** 定位含 pwdEncryptSalt 的账号登录表单；找不到返回 null。 */
    private static String findAccountLoginForm(String html) {
        Matcher forms = FORM_PATTERN.matcher(html);
        while (forms.find()) {
            String form = forms.group(1);
            if (SALT_PATTERN.matcher(form).find()) {
                return form;
            }
        }
        return null;
    }

    private String extractSalt(String html) {
        String form = findAccountLoginForm(html);
        if (form == null) {
            return null;
        }
        Matcher matcher = SALT_PATTERN.matcher(form);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public record CaptchaChallenge(
            boolean required,
            String backgroundBase64,
            String pieceBase64,
            String safeSecure) {

        static CaptchaChallenge notRequired() {
            return new CaptchaChallenge(false, null, null, null);
        }

        static CaptchaChallenge slider(String background, String piece, String safeSecure) {
            return new CaptchaChallenge(true, background, piece, safeSecure);
        }
    }
}
