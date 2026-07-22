package com.middleware.manager.web.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DownloadRateLimitWebConfig implements WebMvcConfigurer {

    private final DownloadRateLimitInterceptor interceptor;

    public DownloadRateLimitWebConfig(DownloadRateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/files/**");
    }
}
