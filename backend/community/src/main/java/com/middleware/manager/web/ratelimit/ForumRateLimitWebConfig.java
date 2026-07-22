package com.middleware.manager.web.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ForumRateLimitWebConfig implements WebMvcConfigurer {

    private final ForumRateLimitInterceptor interceptor;

    public ForumRateLimitWebConfig(ForumRateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/forum/posts/*");
    }
}
