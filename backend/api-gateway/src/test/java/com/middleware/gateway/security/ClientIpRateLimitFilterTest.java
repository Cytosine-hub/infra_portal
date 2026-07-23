package com.middleware.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.middleware.gateway.config.RateLimitProperties;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class ClientIpRateLimitFilterTest {

    private static final String TEST_SECRET = "test-only-gateway-signing-secret";
    private static final String IP_A = "192.0.2.10";
    private static final String IP_B = "192.0.2.11";

    @Test
    @DisplayName("TC-01 下载接口在阈值内正常访问")
    void downloadRequestsWithinLimitAreAllowed() {
        ClientIpRateLimitFilter filter = filter(defaultProperties(), fixedClock());

        for (int requestNumber = 1; requestNumber <= 6; requestNumber++) {
            MockServerWebExchange exchange = exchange("/files/download-token", IP_A);

            assertAllowed(filter, exchange);
            assertThat(exchange.getResponse().getHeaders()).doesNotContainKey(HttpHeaders.RETRY_AFTER);
        }
    }

    @Test
    @DisplayName("TC-02 下载接口超过每分钟阈值后被限流")
    void downloadRequestsOverLimitReturnTooManyRequests() {
        ClientIpRateLimitFilter filter = filter(defaultProperties(), fixedClock());

        for (int requestNumber = 1; requestNumber <= 6; requestNumber++) {
            assertAllowed(filter, exchange("/files/download-token", IP_A));
        }

        assertRateLimited(filter, exchange("/files/download-token", IP_A), 60);
        assertRateLimited(filter, exchange("/files/download-token", IP_A), 60);
    }

    @Test
    @DisplayName("TC-03 固定窗口到期后限流计数重置")
    void expiredWindowResetsDownloadCounter() {
        MutableClock clock = mutableClock();
        ClientIpRateLimitFilter filter = filter(defaultProperties(), clock);

        for (int requestNumber = 1; requestNumber <= 6; requestNumber++) {
            assertAllowed(filter, exchange("/files/download-token", IP_A));
        }
        assertRateLimited(filter, exchange("/files/download-token", IP_A), 60);

        clock.advance(Duration.ofSeconds(60));

        for (int requestNumber = 1; requestNumber <= 6; requestNumber++) {
            assertAllowed(filter, exchange("/files/download-token", IP_A));
        }
    }

    @Test
    @DisplayName("TC-04 不同接口组按各自阈值独立限流")
    void endpointGroupsUseIndependentLimits() {
        ClientIpRateLimitFilter filter = filter(defaultProperties(), fixedClock());

        exhaustAndAssertLimited(filter, "/api/public/standards/42", IP_A, 60);
        exhaustAndAssertLimited(filter, "/api/public/standards/preview?storedFileName=test.docx", IP_A, 18);
        exhaustAndAssertLimited(filter, "/api/forum/posts/42", IP_A, 120);
    }

    @Test
    @DisplayName("TC-05 同一接口组不同客户端 IP 分别计数")
    void clientsUseIndependentCounters() {
        ClientIpRateLimitFilter filter = filter(defaultProperties(), fixedClock());

        for (int requestNumber = 1; requestNumber <= 6; requestNumber++) {
            assertAllowed(filter, exchange("/files/download-token", IP_A));
        }
        assertRateLimited(filter, exchange("/files/download-token", IP_A), 60);

        assertAllowed(filter, exchange("/files/download-token", IP_B));
    }

    @Test
    @DisplayName("TC-06 列表、上传、编辑和评论接口不受本次限流影响")
    void unrelatedEndpointsAreNotRateLimited() {
        ClientIpRateLimitFilter filter = filter(defaultProperties(), fixedClock());
        List<RequestSpec> requests = List.of(
                new RequestSpec("GET", "/api/public/standards"),
                new RequestSpec("GET", "/api/public/standards/all"),
                new RequestSpec("GET", "/api/forum/posts"),
                new RequestSpec("POST", "/api/admin/standard-documents"),
                new RequestSpec("PUT", "/api/admin/standard-documents/42"),
                new RequestSpec("POST", "/api/forum/posts/42/comments"));

        for (RequestSpec request : requests) {
            for (int requestNumber = 1; requestNumber <= 130; requestNumber++) {
                MockServerWebExchange exchange = exchange(request.method(), request.path(), IP_A);
                assertAllowed(filter, exchange);
                assertThat(exchange.getResponse().getHeaders()).doesNotContainKey(HttpHeaders.RETRY_AFTER);
            }
        }
    }

    @Test
    @DisplayName("TC-07 未认证请求先由认证过滤器处理")
    void authenticationFilterRunsBeforeRateLimitFilter() {
        RateLimitProperties properties = defaultProperties();
        ClientIpRateLimitFilter rateLimitFilter = filter(properties, fixedClock());
        GatewayAuthenticationFilter authenticationFilter = new GatewayAuthenticationFilter(
                token -> Mono.just(validIdentity()),
                new GatewaySignatureService(TEST_SECRET),
                Duration.ofSeconds(15),
                100);
        AtomicInteger rateLimitCalls = new AtomicInteger();

        assertThat(authenticationFilter.getOrder()).isLessThan(rateLimitFilter.getOrder());

        for (int requestNumber = 1; requestNumber <= 7; requestNumber++) {
            MockServerWebExchange exchange = exchange("/api/admin/releases", IP_A);

            filterThroughAuthentication(authenticationFilter, rateLimitFilter, exchange, rateLimitCalls);

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exchange.getResponse().getHeaders()).doesNotContainKey(HttpHeaders.RETRY_AFTER);
        }
        assertThat(rateLimitCalls).hasValue(0);

        for (int requestNumber = 1; requestNumber <= 6; requestNumber++) {
            MockServerWebExchange exchange = exchange("/files/download-token", IP_A);

            filterThroughAuthentication(authenticationFilter, rateLimitFilter, exchange, rateLimitCalls);

            assertThat(exchange.getResponse().getStatusCode()).isNull();
            assertThat(exchange.getResponse().getHeaders()).doesNotContainKey(HttpHeaders.RETRY_AFTER);
        }

        MockServerWebExchange limitedExchange = exchange("/files/download-token", IP_A);
        filterThroughAuthentication(authenticationFilter, rateLimitFilter, limitedExchange, rateLimitCalls);

        assertThat(limitedExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limitedExchange.getResponse().getHeaders()).containsKey(HttpHeaders.RETRY_AFTER);
        assertThat(rateLimitCalls).hasValue(7);
    }

    @Test
    @DisplayName("TC-08 关闭限流开关后不执行限流")
    void disabledRateLimitAllowsAllConfiguredGroups() {
        RateLimitProperties properties = defaultProperties();
        properties.setEnabled(false);
        ClientIpRateLimitFilter filter = filter(properties, fixedClock());
        List<String> paths = List.of(
                "/files/download-token",
                "/api/public/standards/42",
                "/api/public/standards/raw?storedFileName=test.docx",
                "/api/forum/posts/42");

        for (String path : paths) {
            for (int requestNumber = 1; requestNumber <= 130; requestNumber++) {
                MockServerWebExchange exchange = exchange(path, IP_A);
                assertAllowed(filter, exchange);
                assertThat(exchange.getResponse().getHeaders()).doesNotContainKey(HttpHeaders.RETRY_AFTER);
            }
        }
    }

    @Test
    @DisplayName("TC-GATEWAY-RATE-001 直连客户端伪造转发头不能绕过限流")
    void directClientCannotSpoofForwardedIpHeaders() {
        RateLimitProperties properties = defaultProperties();
        properties.setDownloadPerWindow(1);
        ClientIpRateLimitFilter filter = filter(properties, fixedClock());

        assertAllowed(filter, exchangeWithRealIp("/files/download-token", IP_A, "198.51.100.10"));
        assertRateLimited(filter, exchangeWithRealIp("/files/download-token", IP_A, "198.51.100.11"), 60);
    }

    @Test
    @DisplayName("TC-GATEWAY-RATE-002 本机反向代理可传递不同客户端真实 IP")
    void loopbackProxyCanProvideRealClientIp() {
        RateLimitProperties properties = defaultProperties();
        properties.setDownloadPerWindow(1);
        ClientIpRateLimitFilter filter = filter(properties, fixedClock());

        assertAllowed(filter, exchangeWithRealIp("/files/download-token", "127.0.0.1", IP_A));
        assertAllowed(filter, exchangeWithRealIp("/files/download-token", "127.0.0.1", IP_B));
    }

    private void exhaustAndAssertLimited(ClientIpRateLimitFilter filter, String path, String ip, int limit) {
        for (int requestNumber = 1; requestNumber <= limit; requestNumber++) {
            assertAllowed(filter, exchange(path, ip));
        }
        assertRateLimited(filter, exchange(path, ip), 60);
    }

    private void assertAllowed(ClientIpRateLimitFilter filter, MockServerWebExchange exchange) {
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, ignored -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(chainCalls).hasValue(1);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private void assertRateLimited(ClientIpRateLimitFilter filter, MockServerWebExchange exchange,
                                   int maximumRetryAfter) {
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, ignored -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(chainCalls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Integer.parseInt(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                .isBetween(1, maximumRetryAfter);
    }

    private ClientIpRateLimitFilter filter(RateLimitProperties properties, Clock clock) {
        return new ClientIpRateLimitFilter(properties, clock);
    }

    private void filterThroughAuthentication(GatewayAuthenticationFilter authenticationFilter,
                                             ClientIpRateLimitFilter rateLimitFilter,
                                             MockServerWebExchange exchange,
                                             AtomicInteger rateLimitCalls) {
        authenticationFilter.filter(exchange, authenticatedExchange -> {
            rateLimitCalls.incrementAndGet();
            return rateLimitFilter.filter(authenticatedExchange, ignored -> Mono.empty());
        }).block();
    }

    private RateLimitProperties defaultProperties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxClientKeys(10_000);
        properties.setDownloadPerWindow(6);
        properties.setDocumentPerWindow(60);
        properties.setDocumentFilePerWindow(18);
        properties.setForumPostPerWindow(120);
        return properties;
    }

    private MockServerWebExchange exchange(String path, String ip) {
        return exchange("GET", path, ip);
    }

    private MockServerWebExchange exchange(String method, String path, String ip) {
        try {
            InetSocketAddress remoteAddress = new InetSocketAddress(InetAddress.getByName(ip), 12345);
            MockServerHttpRequest.BaseBuilder<?> request = switch (method) {
                case "POST" -> MockServerHttpRequest.post(path);
                case "PUT" -> MockServerHttpRequest.put(path);
                default -> MockServerHttpRequest.get(path);
            };
            return MockServerWebExchange.from(request.remoteAddress(remoteAddress));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private MockServerWebExchange exchangeWithRealIp(String path, String remoteIp, String realIp) {
        try {
            InetSocketAddress remoteAddress = new InetSocketAddress(InetAddress.getByName(remoteIp), 12345);
            return MockServerWebExchange.from(MockServerHttpRequest.get(path)
                    .remoteAddress(remoteAddress)
                    .header("X-Real-IP", realIp));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC);
    }

    private MutableClock mutableClock() {
        return new MutableClock(Instant.parse("2026-07-23T12:00:00Z"));
    }

    private GatewayIntrospectionResult validIdentity() {
        return new GatewayIntrospectionResult(true, "alice", "Alice",
                List.of("ROLE_MIDDLEWARE_ADMIN"), "中间件", true);
    }

    private record RequestSpec(String method, String path) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
