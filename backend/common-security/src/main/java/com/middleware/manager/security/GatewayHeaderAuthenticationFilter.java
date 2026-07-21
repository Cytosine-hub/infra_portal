package com.middleware.manager.security;

import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> SKIP_PATHS = List.of(
            "/api/public/**",
            "/api/auth/login",
            "/api/auth/introspect",
            "/files/**");

    private final GatewaySignatureService signatureService;

    public GatewayHeaderAuthenticationFilter(GatewaySignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SKIP_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String username = request.getHeader(GatewayIdentityHeaders.USER);
        String displayName = request.getHeader(GatewayIdentityHeaders.DISPLAY_NAME);
        String rolesHeader = request.getHeader(GatewayIdentityHeaders.ROLES);
        String category = request.getHeader(GatewayIdentityHeaders.CATEGORY);
        String categoryAdmin = request.getHeader(GatewayIdentityHeaders.CATEGORY_ADMIN);
        String signature = request.getHeader(GatewayIdentityHeaders.SIGNATURE);

        if (!StringUtils.hasText(username) || !StringUtils.hasText(rolesHeader)
                || !("true".equals(categoryAdmin) || "false".equals(categoryAdmin))) {
            return;
        }
        if (!signatureService.verifyIdentityHeaders(
                username, displayName, rolesHeader, category, categoryAdmin, signature)) {
            return;
        }

        List<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (roles.isEmpty()) {
            return;
        }

        GatewayAuthenticationToken authentication = GatewayAuthenticationToken.authenticated(
                username,
                displayName,
                roles,
                StringUtils.hasText(category) ? category : null,
                Boolean.parseBoolean(categoryAdmin));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
