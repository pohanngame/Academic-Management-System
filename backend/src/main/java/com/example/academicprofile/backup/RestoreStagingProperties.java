package com.example.academicprofile.backup;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.restore")
public class RestoreStagingProperties {

    private String inboxDir = "./data/restore-inbox";
    private long maxUploadBytes = 512L * 1024 * 1024;
    private Duration stagedTtl = Duration.ofHours(24);

    public String getInboxDir() {
        return inboxDir;
    }

    public void setInboxDir(String inboxDir) {
        this.inboxDir = inboxDir;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public Duration getStagedTtl() {
        return stagedTtl;
    }

    public void setStagedTtl(Duration stagedTtl) {
        this.stagedTtl = stagedTtl;
    }
}
