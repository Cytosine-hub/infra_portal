package com.middleware.manager.knowledge.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class AiConfig {

    @Value("${app.vector.host}")
    private String vectorHost;

    @Value("${app.vector.port}")
    private int vectorPort;

    @Value("${app.vector.collection}")
    private String vectorCollection;

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String getVectorHost() {
        return vectorHost;
    }

    public int getVectorPort() {
        return vectorPort;
    }

    public String getVectorCollection() {
        return vectorCollection;
    }
}
