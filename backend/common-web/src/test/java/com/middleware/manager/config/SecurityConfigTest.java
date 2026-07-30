package com.middleware.manager.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {

    @Test
    @DisplayName("TC-WEB-004 本地开发默认允许 localhost 和 127.0.0.1 前端来源")
    void defaultOriginsIncludeLocalhostAndLoopbackAddress() {
        CorsConfiguration configuration = SecurityConfig.buildCorsConfiguration(
                SecurityConfig.DEFAULT_CORS_ALLOWED_ORIGINS);

        assertThat(configuration.getAllowedOriginPatterns())
                .contains("http://localhost:5173", "http://127.0.0.1:5173");
    }

    @Test
    @DisplayName("TC-WEB-005 自定义来源会去除空白项和重复项")
    void customOriginsAreNormalized() {
        CorsConfiguration configuration = SecurityConfig.buildCorsConfiguration(
                " https://portal.example.com, ,https://portal.example.com ");

        assertThat(configuration.getAllowedOriginPatterns())
                .containsExactly("https://portal.example.com");
    }
}
