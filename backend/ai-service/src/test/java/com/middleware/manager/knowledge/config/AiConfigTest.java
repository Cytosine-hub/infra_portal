package com.middleware.manager.knowledge.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigTest {

    @Test
    @DisplayName("TC-RAG-010 流式模型读取超时应使用统一配置")
    void usesConfiguredStreamReadTimeout() {
        AiConfig config = new AiConfig();
        ReflectionTestUtils.setField(config, "streamReadTimeoutSeconds", 600L);

        assertThat(config.okHttpClient().readTimeoutMillis())
                .isEqualTo(Math.toIntExact(TimeUnit.SECONDS.toMillis(600)));
    }
}
