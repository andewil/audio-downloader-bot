package com.andewil.audiodownloaderbot;

import com.andewil.audiodownloaderbot.config.BotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BotProperties.class)
public class AudioDownloaderBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AudioDownloaderBotApplication.class, args);
    }
}
