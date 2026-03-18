package com.mapin.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class OpenAiConfig {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Bean
    public OpenAIClient openAIClient() {
        String resolvedKey = resolveApiKey();
        return OpenAIOkHttpClient.builder()
                .apiKey(resolvedKey)
                .build();
    }

    private String resolveApiKey() {
        String key = apiKey;
        if (key == null || key.isBlank()) {
            key = System.getenv("OPENAI_API_KEY");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY가 설정되지 않았습니다. .env 또는 환경변수에 값을 넣어주세요.");
        }
        return key;
    }
}
