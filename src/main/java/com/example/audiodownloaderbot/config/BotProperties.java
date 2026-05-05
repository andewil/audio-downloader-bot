package com.example.audiodownloaderbot.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    private String username;
    private String token;
    private Path downloadDir;
    private Duration requestTimeout = Duration.ofMinutes(2);
    private Duration processingTimeout = Duration.ofMinutes(10);
    private long maxFileSize = 50L * 1024L * 1024L;
    private String ytDlpPath = "yt-dlp";
    private String ffmpegPath = "ffmpeg";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Path getDownloadDir() {
        return downloadDir;
    }

    public void setDownloadDir(Path downloadDir) {
        this.downloadDir = downloadDir;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public String getYtDlpPath() {
        return ytDlpPath;
    }

    public void setYtDlpPath(String ytDlpPath) {
        this.ytDlpPath = ytDlpPath;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }
}
