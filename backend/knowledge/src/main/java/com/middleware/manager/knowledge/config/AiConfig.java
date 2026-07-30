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

    @Value("${app.vector.dimension:1024}")
    private int vectorDimension;

    /** RRF 融合的 k 值，越大越平滑；60 是文献常用默认值。 */
    @Value("${app.vector.rrf-k:60}")
    private int rrfK;

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

    public int getVectorDimension() {
        return vectorDimension;
    }

    public int getRrfK() {
        return rrfK;
    }
}
