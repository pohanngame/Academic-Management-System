package com.example.academicprofile.ai;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;

@Component
public class AiBaseUrlValidator {

    static final int MAX_URL_LENGTH = 2048;

    private final AddressResolver addressResolver;
    private final boolean allowUnsafeForTests;

    public AiBaseUrlValidator() {
        this(InetAddress::getAllByName, false);
    }

    AiBaseUrlValidator(AddressResolver addressResolver, boolean allowUnsafeForTests) {
        this.addressResolver = addressResolver;
        this.allowUnsafeForTests = allowUnsafeForTests;
    }

    public String normalizeAndValidate(String value) {
        if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH) {
            throw new BusinessException("API Base URL 长度无效");
        }
        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException ex) {
            throw new BusinessException("API Base URL 格式无效");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !(allowUnsafeForTests && "http".equals(scheme))) {
            throw new BusinessException("API Base URL 必须使用 HTTPS");
        }
        if (uri.isOpaque() || !StringUtils.hasText(uri.getHost())) {
            throw new BusinessException("API Base URL 必须包含有效主机名");
        }
        if (uri.getUserInfo() != null) {
            throw new BusinessException("API Base URL 不得包含用户名或密码");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new BusinessException("API Base URL 不得包含查询参数或片段");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String hostWithoutTrailingDot = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if ("localhost".equals(hostWithoutTrailingDot) || hostWithoutTrailingDot.endsWith(".localhost")) {
            throw new BusinessException("API Base URL 不得指向本机或内网地址");
        }
        validateResolvedAddresses(hostWithoutTrailingDot);

        String path = uri.getPath() == null ? "" : uri.getPath();
        while (path.endsWith("/") && !path.isEmpty()) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            return new URI(scheme, null, hostWithoutTrailingDot, uri.getPort(), path, null, null).toASCIIString();
        } catch (URISyntaxException ex) {
            throw new BusinessException("API Base URL 格式无效");
        }
    }

    public String chatCompletionsUrl(String normalizedBaseUrl) {
        String baseUrl = normalizeAndValidate(normalizedBaseUrl);
        try {
            URI base = new URI(baseUrl);
            String path = (base.getPath() == null ? "" : base.getPath()) + "/chat/completions";
            return new URI(base.getScheme(), null, base.getHost(), base.getPort(), path, null, null).toASCIIString();
        } catch (URISyntaxException ex) {
            throw new BusinessException("API Base URL 格式无效");
        }
    }

    private void validateResolvedAddresses(String host) {
        final InetAddress[] addresses;
        try {
            addresses = addressResolver.resolve(host);
        } catch (UnknownHostException ex) {
            throw new BusinessException("API Base URL 主机无法解析");
        }
        if (addresses == null || addresses.length == 0) {
            throw new BusinessException("API Base URL 主机无法解析");
        }
        for (InetAddress address : addresses) {
            if (!allowUnsafeForTests && isBlocked(address)) {
                throw new BusinessException("API Base URL 不得指向本机或内网地址");
            }
        }
    }

    private boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            return (bytes[0] & 0xfe) == 0xfc;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254);
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
