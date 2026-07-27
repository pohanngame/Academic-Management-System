package com.example.academicprofile.backup;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.backup")
public class BackupProperties {

    private Duration dumpTimeout = Duration.ofMinutes(10);
    private long minimumFreeBytes = 100L * 1024 * 1024;
    private long maximumDownloadBytes = 512L * 1024 * 1024;
    private String applicationVersion = "unknown";
    private String gitCommit = "unknown";
    private String mysqldumpExecutable = "mysqldump";

    public Duration getDumpTimeout() { return dumpTimeout; }
    public void setDumpTimeout(Duration dumpTimeout) { this.dumpTimeout = dumpTimeout; }
    public long getMinimumFreeBytes() { return minimumFreeBytes; }
    public void setMinimumFreeBytes(long minimumFreeBytes) { this.minimumFreeBytes = minimumFreeBytes; }
    public long getMaximumDownloadBytes() { return maximumDownloadBytes; }
    public void setMaximumDownloadBytes(long maximumDownloadBytes) {
        this.maximumDownloadBytes = maximumDownloadBytes;
    }
    public String getApplicationVersion() { return applicationVersion; }
    public void setApplicationVersion(String applicationVersion) { this.applicationVersion = applicationVersion; }
    public String getGitCommit() { return gitCommit; }
    public void setGitCommit(String gitCommit) { this.gitCommit = gitCommit; }
    public String getMysqldumpExecutable() { return mysqldumpExecutable; }
    public void setMysqldumpExecutable(String mysqldumpExecutable) { this.mysqldumpExecutable = mysqldumpExecutable; }
}
