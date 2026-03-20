package com.mapin.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mapin Pipeline API")
                        .description("유튜브 콘텐츠 수집 · 분석 · 추천 파이프라인")
                        .version("v1"));
    }
}
