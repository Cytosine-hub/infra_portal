package com.middleware.gateway.security;

import com.middleware.gateway.config.RateLimitProperties;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

@Component
public class ClientIpRateLimitFilter implements GlobalFilter, Ordered {

    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();
    private static final PathPattern DOWNLOAD_BY_TOKEN = PATH_PATTERN_PARSER.parse("/files/{token}");
    private static final PathPattern DOWNLOAD_BY_NAME =
            PATH_PATTERN_PARSER.parse("/files/{middlewareName}/{fileName}");
    private static final PathPattern IMAGE_FILE = PATH_PATTERN_PARSER.parse("/files/images/**");
    private static final PathPattern DOCUMENT_DETAIL =
            PATH_PATTERN_PARSER.parse("/api/public/standards/{id:[0-9]+}");
    private static final PathPattern DOCUMENT_PREVIEW =
            PATH_PATTERN_PARSER.parse("/api/public/standards/preview");
    private static final PathPattern DOCUMENT_RAW =
            PATH_PATTERN_PARSER.parse("/api/public/standards/raw");
    private static final PathPattern FORUM_POST_DETAIL =
            PATH_PATTERN_PARSER.parse("/api/forum/posts/{id:[0-9]+}");
    private static final String REAL_IP_HEADER = "X-Real-IP";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String UNKNOWN_CLIENT = "unknown";

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<ClientKey, WindowCounter> counters = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public ClientIpRateLimitFilter(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ClientIpRateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        RateLimitGroup group = resolveGroup(exchange);
        if (group == null) {
            return chain.filter(exchange);
        }

        RateLimitDecision decision = acquire(group, resolveClientIp(exchange));
        if (decision.allowed()) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().set(
                HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private RateLimitGroup resolveGroup(ServerWebExchange exchange) {
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return null;
        }

        PathContainer path = PathContainer.parsePath(exchange.getRequest().getURI().getPath());
        if (!IMAGE_FILE.matches(path)
                && (DOWNLOAD_BY_TOKEN.matches(path) || DOWNLOAD_BY_NAME.matches(path))) {
            return new RateLimitGroup("download", properties.getDownloadPerWindow());
        }
        if (DOCUMENT_PREVIEW.matches(path) || DOCUMENT_RAW.matches(path)) {
            return new RateLimitGroup("document-file", properties.getDocumentFilePerWindow());
        }
        if (DOCUMENT_DETAIL.matches(path)) {
            return new RateLimitGroup("document", properties.getDocumentPerWindow());
        }
        if (FORUM_POST_DETAIL.matches(path)) {
            return new RateLimitGroup("forum-post", properties.getForumPostPerWindow());
        }
        return null;
    }

    private synchronized RateLimitDecision acquire(RateLimitGroup group, String clientIp) {
        long epochSecond = clock.instant().getEpochSecond();
        int windowSeconds = Math.max(1, properties.getWindowSeconds());
        long windowId = Math.floorDiv(epochSecond, windowSeconds);
        ClientKey key = new ClientKey(group.name(), clientIp);
        WindowCounter current = counters.get(key);

        if (current == null || current.windowId() != windowId) {
            ensureCapacityFor(key);
            counters.put(key, new WindowCounter(windowId, 1));
            return RateLimitDecision.allow();
        }
        if (current.count() >= Math.max(1, group.limit())) {
            long retryAfter = windowSeconds - Math.floorMod(epochSecond, windowSeconds);
            return RateLimitDecision.reject(retryAfter);
        }

        counters.put(key, new WindowCounter(windowId, current.count() + 1));
        return RateLimitDecision.allow();
    }

    private void ensureCapacityFor(ClientKey key) {
        if (counters.containsKey(key)) {
            return;
        }
        int maximumSize = Math.max(1, properties.getMaxClientKeys());
        while (counters.size() >= maximumSize) {
            ClientKey eldest = counters.keySet().iterator().next();
            counters.remove(eldest);
        }
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNKNOWN_CLIENT;
        }
        if (remoteAddress.getAddress().isLoopbackAddress()) {
            String forwardedClientIp = resolveForwardedClientIp(exchange);
            if (forwardedClientIp != null) {
                return forwardedClientIp;
            }
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private String resolveForwardedClientIp(ServerWebExchange exchange) {
        String realIp = exchange.getRequest().getHeaders().getFirst(REAL_IP_HEADER);
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        String forwardedFor = exchange.getRequest().getHeaders().getFirst(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return null;
    }

    private record RateLimitGroup(String name, int limit) {
    }

    private record ClientKey(String group, String clientIp) {
    }

    private record WindowCounter(long windowId, int count) {
    }

    private record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        private static RateLimitDecision allow() {
            return new RateLimitDecision(true, 0);
        }

        private static RateLimitDecision reject(long retryAfterSeconds) {
            return new RateLimitDecision(false, retryAfterSeconds);
        }
    }
}
