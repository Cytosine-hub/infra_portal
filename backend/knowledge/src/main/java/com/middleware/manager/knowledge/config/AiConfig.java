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

    /**
     * BM25 分词器类型。中文必须显式指定，否则默认 standard 分词器会把整段中文
     * 当成一个 token，BM25 那一路等于失效。该配置在 collection 创建后不可更改。
     */
    @Value("${app.vector.analyzer:chinese}")
    private String vectorAnalyzer;

    @Value("${app.llm.stream-read-timeout-seconds:600}")
    private long streamReadTimeoutSeconds = 600;

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(streamReadTimeoutSeconds, TimeUnit.SECONDS)
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

    public String getVectorAnalyzer() {
        return vectorAnalyzer;
    }
}
