package com.example.academicprofile.timetable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 教务（强智 jsxsd）与统一认证的地址与超时。这些地址不含任何凭据。 */
@Component
@ConfigurationProperties(prefix = "app.timetable")
public class TimetableProperties {

    private boolean enabled = true;
    private String authBaseUrl = "https://authserver.dgpt.edu.cn";
    private String jwBaseUrl = "https://jw.dgpt.edu.cn";
    private int timeoutSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAuthBaseUrl() {
        return authBaseUrl;
    }

    public void setAuthBaseUrl(String authBaseUrl) {
        this.authBaseUrl = authBaseUrl;
    }

    public String getJwBaseUrl() {
        return jwBaseUrl;
    }

    public void setJwBaseUrl(String jwBaseUrl) {
        this.jwBaseUrl = jwBaseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String authLoginUrl() {
        return trimTrailingSlash(authBaseUrl) + "/authserver/login";
    }

    public String jwSsoUrl() {
        return trimTrailingSlash(jwBaseUrl) + "/jsxsd/sso.jsp";
    }

    public String jwTeacherTablePageUrl() {
        return trimTrailingSlash(jwBaseUrl) + "/jsxsd/kbcx/kbxx_teacher";
    }

    public String jwTeacherTableUrl() {
        return trimTrailingSlash(jwBaseUrl) + "/jsxsd/kbcx/kbxx_teacher_ifr";
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
