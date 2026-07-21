package com.middleware.gateway.security;

import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {
    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();
    private static final PathPattern PUBLIC_API = PATH_PATTERN_PARSER.parse("/api/public/**");
    private static final PathPattern FILES = PATH_PATTERN_PARSER.parse("/files/**");
    private static final PathPattern FORUM = PATH_PATTERN_PARSER.parse("/api/forum/**");
    private static final PathPattern MIDDLEWARE_COMMANDS =
            PATH_PATTERN_PARSER.parse("/api/middleware-commands/**");
    private static final String FORUM_MY_POSTS = "/api/forum/my-posts";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String LOGOUT_PATH = "/api/auth/logout";
    private static final String INTROSPECTION_PATH = "/api/auth/introspect";

    private final GatewayIntrospectionClient introspectionClient;
    private final GatewaySignatureService signatureService;
    private final ExpiringTokenCache cache;

    @Autowired
    public GatewayAuthenticationFilter(
            GatewayIntrospectionClient introspectionClient,
            GatewaySignatureService signatureService,
            @Value("${app.security.introspection-cache-ttl:PT15S}") Duration cacheTtl,
            @Value("${app.security.introspection-cache-max-size:10000}") int cacheMaxSize) {
        this(introspectionClient, signatureService, cacheTtl, cacheMaxSize, Clock.systemUTC());
    }

    GatewayAuthenticationFilter(GatewayIntrospectionClient introspectionClient,
                                GatewaySignatureService signatureService,
                                Duration cacheTtl,
                                int cacheMaxSize,
                                Clock clock) {
        this.introspectionClient = introspectionClient;
        this.signatureService = signatureService;
        this.cache = new ExpiringTokenCache(cacheTtl, cacheMaxSize, clock);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange strippedExchange = stripIdentityHeaders(exchange);
        String path = strippedExchange.getRequest().getURI().getPath();
        HttpMethod method = strippedExchange.getRequest().getMethod();

        if (INTROSPECTION_PATH.equals(path)) {
            return complete(strippedExchange, HttpStatus.NOT_FOUND);
        }
        if (isPublic(method, path)) {
            return chain.filter(strippedExchange);
        }

        String token = bearerToken(strippedExchange.getRequest());
        if (token == null) {
            return complete(strippedExchange, HttpStatus.UNAUTHORIZED);
        }

        GatewayIntrospectionResult cached = cache.get(token);
        Mono<GatewayIntrospectionResult> introspection = cached == null
                ? introspectionClient.introspect(token).doOnNext(result -> cache.put(token, result))
                : Mono.just(cached);
        return introspection
                .switchIfEmpty(Mono.just(GatewayIntrospectionResult.invalid()))
                .onErrorReturn(GatewayIntrospectionResult.invalid())
                .flatMap(result -> forwardAuthenticated(strippedExchange, chain, token, result));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<Void> forwardAuthenticated(ServerWebExchange exchange, GatewayFilterChain chain,
                                            String token, GatewayIntrospectionResult result) {
        if (!isUsable(result)) {
            return complete(exchange, HttpStatus.UNAUTHORIZED);
        }

        String displayName = value(result.displayName());
        String roles = String.join(",", normalizedRoles(result.roles()));
        String category = value(result.category());
        String categoryAdmin = Boolean.toString(result.categoryAdmin());
        String signature = signatureService.signIdentityHeaders(
                result.username(), displayName, roles, category, categoryAdmin);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(GatewayIdentityHeaders.USER, result.username());
                    headers.set(GatewayIdentityHeaders.DISPLAY_NAME, displayName);
                    headers.set(GatewayIdentityHeaders.ROLES, roles);
                    headers.set(GatewayIdentityHeaders.CATEGORY, category);
                    headers.set(GatewayIdentityHeaders.CATEGORY_ADMIN, categoryAdmin);
                    headers.set(GatewayIdentityHeaders.SIGNATURE, signature);
                })
                .build();
        Mono<Void> forwarding = chain.filter(exchange.mutate().request(request).build());
        if (HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && LOGOUT_PATH.equals(exchange.getRequest().getURI().getPath())) {
            return forwarding.doFinally(ignored -> cache.remove(token));
        }
        return forwarding;
    }

    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> GatewayIdentityHeaders.ALL.forEach(headers::remove))
                .build();
        return exchange.mutate().request(request).build();
    }

    private boolean isPublic(HttpMethod method, String path) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        PathContainer pathContainer = PathContainer.parsePath(path);
        if (PUBLIC_API.matches(pathContainer) || FILES.matches(pathContainer)) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && FORUM.matches(pathContainer)
                && !FORUM_MY_POSTS.equals(path)) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && MIDDLEWARE_COMMANDS.matches(pathContainer)) {
            return true;
        }
        return HttpMethod.POST.equals(method) && LOGIN_PATH.equals(path);
    }

    private String bearerToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private boolean isUsable(GatewayIntrospectionResult result) {
        return result != null
                && result.valid()
                && StringUtils.hasText(result.username())
                && isSafeScalar(result.username())
                && isSafeScalar(result.displayName())
                && isSafeScalar(result.category())
                && !normalizedRoles(result.roles()).isEmpty();
    }

    private List<String> normalizedRoles(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(this::isSafeHeaderValue)
                .distinct()
                .toList();
    }

    private boolean isSafeHeaderValue(String value) {
        return !value.contains(",") && !value.contains("\r") && !value.contains("\n");
    }

    private boolean isSafeScalar(String value) {
        return value == null || !value.contains("\r") && !value.contains("\n");
    }

    private Mono<Void> complete(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    private String value(String input) {
        return input == null ? "" : input;
    }

    private static final class ExpiringTokenCache {
        private final Duration ttl;
        private final int maxSize;
        private final Clock clock;
        private final Map<String, CachedIdentity> entries =
                new LinkedHashMap<>(16, 0.75f, true);

        private ExpiringTokenCache(Duration ttl, int maxSize, Clock clock) {
            if (ttl == null || ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("introspection cache TTL must be positive");
            }
            if (maxSize <= 0) {
                throw new IllegalArgumentException("introspection cache max size must be positive");
            }
            this.ttl = ttl;
            this.maxSize = maxSize;
            this.clock = clock;
        }

        private synchronized GatewayIntrospectionResult get(String token) {
            CachedIdentity cached = entries.get(token);
            if (cached == null) {
                return null;
            }
            if (!cached.expiresAt().isAfter(clock.instant())) {
                entries.remove(token);
                return null;
            }
            return cached.identity();
        }

        private synchronized void put(String token, GatewayIntrospectionResult identity) {
            entries.put(token, new CachedIdentity(identity, clock.instant().plus(ttl)));
            while (entries.size() > maxSize) {
                String eldest = entries.keySet().iterator().next();
                entries.remove(eldest);
            }
        }

        private synchronized void remove(String token) {
            entries.remove(token);
        }
    }

    private record CachedIdentity(GatewayIntrospectionResult identity, Instant expiresAt) {
    }
}
