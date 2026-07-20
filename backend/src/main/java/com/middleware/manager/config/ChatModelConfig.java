package com.middleware.manager.config;

import com.middleware.manager.wiki.service.PooledChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatModelConfig {

    @Value("${app.llm.max-concurrent:3}")
    private int maxConcurrent;

    @Value("${app.llm.timeout-seconds:300}")
    private long timeoutSeconds;

    /**
     * 用 PooledChatModel 包装原始 ChatModel，控制 LLM API 并发数
     */
    @Bean
    @Primary
    public ChatModel pooledChatModel(ChatModel delegate) {
        return new PooledChatModel(delegate, maxConcurrent, timeoutSeconds);
    }
}
