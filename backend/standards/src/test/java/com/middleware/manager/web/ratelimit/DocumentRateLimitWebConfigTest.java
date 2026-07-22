package com.middleware.manager.web.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentRateLimitWebConfigTest {

    @Test
    @DisplayName("TC-06 限流拦截器只注册在公开标准文档访问路径 /api/public/standards/*，不影响其他接口")
    void interceptorOnlyAppliesToPublicStandardDocumentPath() {
        DocumentRateLimitInterceptor interceptor = mock(DocumentRateLimitInterceptor.class);
        DocumentRateLimitWebConfig config = new DocumentRateLimitWebConfig(interceptor);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(interceptor)).thenReturn(registration);
        when(registration.addPathPatterns(org.mockito.ArgumentMatchers.<String>any())).thenReturn(registration);

        config.addInterceptors(registry);

        ArgumentCaptor<String[]> patternsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(registration).addPathPatterns(patternsCaptor.capture());
        assertThat(patternsCaptor.getValue()).containsExactly("/api/public/standards/*");
    }
}
