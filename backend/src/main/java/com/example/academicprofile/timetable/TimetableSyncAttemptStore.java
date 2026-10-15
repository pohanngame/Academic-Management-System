package com.example.academicprofile.timetable;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.example.academicprofile.common.exception.BusinessException;

import okhttp3.HttpUrl;

/**
 * 一次教务同步尝试的临时状态：认证页会话 Cookie、pwdEncryptSalt、lt、execution。
 * 只存在于内存，绝不写入数据库、日志、备份或 .env；用完即删，并有过期时间。
 */
@Component
public class TimetableSyncAttemptStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public String create(String username, JsxsdAuthPage authPage) {
        purgeExpired();
        String attemptId = UUID.randomUUID().toString();
        attempts.put(attemptId, new Attempt(username, authPage, null, Instant.now()));
        return attemptId;
    }

    /** 滑块开启后记下 safeSecure；只存本次尝试的内存，用完即删。 */
    public void putSafeSecure(String attemptId, String safeSecure) {
        Attempt current = require(attemptId);
        attempts.put(attemptId, new Attempt(
                current.username(), current.authPage(), safeSecure, current.createdAt()));
    }

    public Attempt require(String attemptId) {
        purgeExpired();
        if (attemptId == null || attemptId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "attemptId is required");
        }
        Attempt attempt = attempts.get(attemptId);
        if (attempt == null) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "The academic affairs login attempt has expired; start again");
        }
        return attempt;
    }

    public void remove(String attemptId) {
        if (attemptId != null) {
            attempts.remove(attemptId);
        }
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        attempts.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    /** toString 不暴露 Cookie 或 safeSecure 等凭据，避免误入日志。 */
    public record Attempt(
            String username,
            JsxsdAuthPage authPage,
            String safeSecure,
            Instant createdAt) {
        @Override
        public String toString() {
            return "Attempt[username=" + username + ", createdAt=" + createdAt + "]";
        }
    }

    /** 认证页返回：登录地址、加密盐、lt、execution、以及认证站 Cookie。 */
    public record JsxsdAuthPage(
            String loginUrl,
            String pwdEncryptSalt,
            String lt,
            String execution,
            CookieJar cookies) {
    }

    /**
     * 按域名分组的 Cookie。认证站与教务站都有名为 JSESSIONID 的 Cookie，
     * 用单个 Map 按名合并会让两者互相覆盖，因此必须按域名隔离。
     * toString 不输出任何 Cookie 值。
     */
    public static final class CookieJar {

        /**
         * 按归属存 Cookie。键是归属域名（带 Domain 的按域，host-only 的按主机）；
         * 值为该归属下按“Path + 名”存的 Cookie，
         * 这样同名 Cookie 在不同 Path 上不会互相覆盖。
         */
        private final Map<String, Map<String, String>> byDomain = new LinkedHashMap<>();

        /**
         * host-only 的归属集合：这些键只能匹配它自己，不能匹配子域。
         * 例如认证站不带 Domain 的 CASTGC 只属于 authserver.dgpt.edu.cn，
         * 绝不能升到 dgpt.edu.cn 后又被教务同名 Cookie 覆盖。
         */
        private final java.util.Set<String> hostOnly = new java.util.LinkedHashSet<>();

        /**
         * 按域名取 Cookie，拼成该域名专属的请求头。
         * 带 Domain 的 Cookie 按其域及其子域发送；
         * host-only 的 Cookie 只发给完全同名的主机。
         * 父域先放、主机自身后放，后者同名时覆盖前者。
         */
        public String headerFor(String url) {
            HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
            if (parsed == null) {
                return "";
            }
            String host = parsed.host().toLowerCase(java.util.Locale.ROOT);
            String path = parsed.encodedPath();
            // 先放父域（更宽泛），再放主机自身，后者同名时覆盖前者。
            Map<String, String> ordered = new LinkedHashMap<>();
            byDomain.forEach((domain, cookies) -> {
                if (cookiesDomainMatches(domain, host, hostOnly.contains(domain))) {
                    cookies.forEach((key, value) -> {
                        String cookiePath = pathOf(key);
                        if (pathMatches(cookiePath, path)) {
                            ordered.put(nameOf(key), value);
                        }
                    });
                }
            });
            if (ordered.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            ordered.forEach((name, value) -> {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(name).append('=').append(value);
            });
            return builder.toString();
        }

        private static final String DEFAULT_PATH = "/";

        /** 存储键：`Path` + `\n` + 名，便于同名 Cookie 按 Path 分开。 */
        private static String keyOf(String path, String name) {
            return path + "\n" + name;
        }

        private static String pathOf(String key) {
            int split = key.indexOf('\n');
            return split < 0 ? DEFAULT_PATH : key.substring(0, split);
        }

        private static String nameOf(String key) {
            int split = key.indexOf('\n');
            return split < 0 ? key : key.substring(split + 1);
        }

        /** RFC 6265 的 path-match：目录前缀匹配，`/` 匹配全部。 */
        private static boolean pathMatches(String cookiePath, String requestPath) {
            if (cookiePath.equals(requestPath)) {
                return true;
            }
            if (!requestPath.startsWith(cookiePath)) {
                return false;
            }
            return cookiePath.endsWith("/")
                    || requestPath.charAt(cookiePath.length()) == '/';
        }

        /**
         * host-only 的键只能精确匹配；带 Domain 的键匹配自身及其子域。
         * 仅同后缀但不是子域名（如 xdgpt.edu.cn）不算。
         */
        private static boolean cookiesDomainMatches(
                String domain, String host, boolean domainIsHostOnly) {
            if (domain.equals(host)) {
                return true;
            }
            if (domainIsHostOnly) {
                return false;
            }
            return host.length() > domain.length()
                    && host.endsWith(domain)
                    && host.charAt(host.length() - domain.length() - 1) == '.';
        }

        /** host-only：只归当前主机，默认 Path 为 `/`。 */
        void putHostOnly(String host, String path, String name, String value) {
            if (host == null || host.isEmpty() || name == null || name.isEmpty()) {
                return;
            }
            String normalizedHost = host.toLowerCase(java.util.Locale.ROOT);
            String normalizedPath = path == null || path.isEmpty() ? DEFAULT_PATH : path;
            byDomain.computeIfAbsent(normalizedHost, key -> new LinkedHashMap<>())
                    .put(keyOf(normalizedPath, name), value);
            hostOnly.add(normalizedHost);
        }

        /** 把 Set-Cookie 收进它自己所属的域名，不影响其它域名。 */
        void putAllFrom(String host, Map<String, String> cookies) {
            if (host == null || host.isEmpty() || cookies == null || cookies.isEmpty()) {
                return;
            }
            byDomain.computeIfAbsent(host, key -> new LinkedHashMap<>()).putAll(cookies);
        }

        /** 收单个带 Domain 的 Cookie，值绝不影响其它域名。 */
        void putFrom(String domain, String name, String value) {
            if (domain == null || domain.isEmpty() || name == null || name.isEmpty()) {
                return;
            }
            byDomain.computeIfAbsent(domain, key -> new LinkedHashMap<>())
                    .put(keyOf(DEFAULT_PATH, name), value);
        }

        /** 该 Cookie 名被归档到哪些域名下，供测试断言归属，不返回任何值。 */
        java.util.Set<String> domainsHolding(String name) {
            java.util.Set<String> holders = new java.util.LinkedHashSet<>();
            byDomain.forEach((domain, cookies) -> {
                boolean holds = cookies.keySet().stream()
                        .anyMatch(key -> nameOf(key).equals(name));
                if (holds) {
                    holders.add(domain);
                }
            });
            return holders;
        }

        /** 把另一个 jar 的全部域名 Cookie 并进来（同域同名以后者为准）。 */
        void putAllFromJar(CookieJar other) {
            if (other == null) {
                return;
            }
            other.byDomain.forEach((host, cookies) ->
                    byDomain.computeIfAbsent(host, key -> new LinkedHashMap<>()).putAll(cookies));
            hostOnly.addAll(other.hostOnly);
        }

        /** 含 Cookie 值，严禁进日志。 */
        @Override
        public String toString() {
            return "CookieJar[domains=" + byDomain.size() + "]";
        }
    }

    static HttpUrl parse(String url) {
        HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
        if (parsed == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Academic affairs URL is invalid");
        }
        return parsed;
    }
}
