package com.andewil.audiodownloaderbot.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    @Bean
    HttpClient httpClient(BotProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
