package com.andewil.audiodownloaderbot.config;

import java.nio.file.Path;
import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime configuration for the Telegram audio downloader bot.
 *
 * <p>Values are loaded from {@code bot.*} Spring properties and can be supplied
 * through environment variables defined in {@code application.yml}.</p>
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    private String username;
    private String token;
    private Path downloadDir;
    private Duration requestTimeout = Duration.ofMinutes(2);
    private Duration processingTimeout = Duration.ofMinutes(10);
    private long maxFileSize = 50L * 1024L * 1024L;
    private String ytDlpPath = "yt-dlp";
    private String ytDlpJsRuntime = "";
    private String ffmpegPath = "ffmpeg";

}
