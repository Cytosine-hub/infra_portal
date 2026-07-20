package com.middleware.manager.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j
public class AccessLogFilter extends OncePerRequestFilter {
    private static final int SERVER_ERROR_THRESHOLD = 500;
    private static final int CLIENT_ERROR_THRESHOLD = 400;
    private static final String STATIC_CSS_PATH = "/css/";
    private static final String STATIC_ASSETS_PATH = "/assets/";
    private static final String FAVICON_PATH = "/favicon.ico";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(STATIC_CSS_PATH)
                || path.startsWith(STATIC_ASSETS_PATH)
                || path.equals(FAVICON_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            int status = response.getStatus();
            String message = "access method={} path={} status={} durationMs={} remote={} user={}";
            Object[] args = {
                    request.getMethod(),
                    requestPath(request),
                    status,
                    durationMs,
                    clientIp(request),
                    currentUser()
            };

            if (status >= SERVER_ERROR_THRESHOLD) {
                log.error(message, args);
            } else if (status >= CLIENT_ERROR_THRESHOLD) {
                log.warn(message, args);
            } else {
                log.info(message, args);
            }
        }
    }

    private String requestPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return StringUtils.hasText(query) ? request.getRequestURI() + "?" + query : request.getRequestURI();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp : request.getRemoteAddr();
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "-";
        }
        return authentication.getName();
    }
}
