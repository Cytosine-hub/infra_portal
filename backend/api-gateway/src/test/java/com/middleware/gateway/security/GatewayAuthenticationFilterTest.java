package com.middleware.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-only-gateway-signing-secret";

    private final GatewaySignatureService signatureService = new GatewaySignatureService(TEST_SECRET);

    @Test
    @DisplayName("TC-GATEWAY-003 客户端伪造身份头先剥离再按 Token 重新注入")
    void forgedIdentityHeadersAreReplaced() {
        GatewayAuthenticationFilter filter = filter(token -> Mono.just(validIdentity()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/admin/releases")
                .header("Authorization", "Bearer valid-token")
                .header(GatewayIdentityHeaders.USER, "mallory")
                .header(GatewayIdentityHeaders.ROLES, "ROLE_SYS_ADMIN")
                .header(GatewayIdentityHeaders.CATEGORY, "安全")
                .header(GatewayIdentityHeaders.SIGNATURE, "forged"));
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getHeaders().getFirst(GatewayIdentityHeaders.USER)).isEqualTo("alice");
        assertThat(forwarded.get().getHeaders().getFirst(GatewayIdentityHeaders.ROLES))
                .isEqualTo("ROLE_MIDDLEWARE_ADMIN");
        assertThat(forwarded.get().getHeaders().getFirst(GatewayIdentityHeaders.CATEGORY)).isEqualTo("中间件");
    }

    @Test
    @DisplayName("TC-GATEWAY-004 受保护路径无 Bearer Token 返回 401")
    void protectedPathWithoutTokenReturnsUnauthorized() {
        AtomicInteger chainCalls = new AtomicInteger();
        GatewayAuthenticationFilter filter = filter(token -> Mono.just(validIdentity()));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/releases"));

        filter.filter(exchange, ignored -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalls).hasValue(0);
    }

    @Test
    @DisplayName("TC-GATEWAY-005 有效 Token 注入完整身份头并生成正确签名")
    void validTokenInjectsCorrectlySignedIdentity() {
        GatewayAuthenticationFilter filter = filter(token -> Mono.just(validIdentity()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/admin/releases")
                .header("Authorization", "Bearer valid-token"));
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        var headers = forwarded.get().getHeaders();
        assertThat(headers.getFirst(GatewayIdentityHeaders.DISPLAY_NAME)).isEqualTo("Alice");
        assertThat(headers.getFirst(GatewayIdentityHeaders.CATEGORY_ADMIN)).isEqualTo("true");
        assertThat(headers.getFirst(GatewayIdentityHeaders.SIGNATURE)).isEqualTo(
                signatureService.signIdentityHeaders("alice", "Alice",
                        "ROLE_MIDDLEWARE_ADMIN", "中间件", "true"));
    }

    @Test
    @DisplayName("TC-GATEWAY-006 公开路径剥离伪造身份头且不调用 introspect")
    void publicPathStripsHeadersWithoutIntrospection() {
        AtomicInteger introspections = new AtomicInteger();
        GatewayAuthenticationFilter filter = filter(token -> {
            introspections.incrementAndGet();
            return Mono.just(validIdentity());
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/public/config")
                .header(GatewayIdentityHeaders.USER, "mallory")
                .header(GatewayIdentityHeaders.ROLES, "ROLE_SYS_ADMIN"));
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(introspections).hasValue(0);
        GatewayIdentityHeaders.ALL.forEach(header ->
                assertThat(forwarded.get().getHeaders().containsKey(header)).isFalse());
    }

    @Test
    @DisplayName("TC-GATEWAY-007 introspect 失败时返回 401")
    void invalidTokenReturnsUnauthorized() {
        GatewayAuthenticationFilter filter = filter(token -> Mono.just(GatewayIntrospectionResult.invalid()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/wiki/pages")
                .header("Authorization", "Bearer expired-token"));

        filter.filter(exchange, ignored -> Mono.error(new AssertionError("chain must not run"))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("TC-GATEWAY-008 同一 Token 的 introspect 结果按短 TTL 缓存")
    void introspectionResultIsCachedByToken() {
        AtomicInteger introspections = new AtomicInteger();
        GatewayAuthenticationFilter filter = filter(token -> {
            introspections.incrementAndGet();
            return Mono.just(validIdentity());
        });

        for (int i = 0; i < 2; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                    .get("/api/admin/releases")
                    .header("Authorization", "Bearer valid-token"));
            filter.filter(exchange, ignored -> Mono.empty()).block();
        }

        assertThat(introspections).hasValue(1);
    }

    @Test
    @DisplayName("TC-GATEWAY-010 客户端不能经网关访问 identity 内部 introspect 接口")
    void externalIntrospectionRequestIsNotForwarded() {
        AtomicInteger introspections = new AtomicInteger();
        AtomicInteger chainCalls = new AtomicInteger();
        GatewayAuthenticationFilter filter = filter(token -> {
            introspections.incrementAndGet();
            return Mono.just(validIdentity());
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/auth/introspect")
                .header("Authorization", "Bearer valid-token")
                .header(GatewayIdentityHeaders.SIGNATURE, "forged"));

        filter.filter(exchange, ignored -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(introspections).hasValue(0);
        assertThat(chainCalls).hasValue(0);
    }

    private GatewayAuthenticationFilter filter(GatewayIntrospectionClient client) {
        return new GatewayAuthenticationFilter(client, signatureService, Duration.ofSeconds(15), 100);
    }

    private GatewayFilterChain capture(AtomicReference<ServerHttpRequest> request) {
        return exchange -> {
            request.set(exchange.getRequest());
            return Mono.empty();
        };
    }

    private GatewayIntrospectionResult validIdentity() {
        return new GatewayIntrospectionResult(true, "alice", "Alice",
                List.of("ROLE_MIDDLEWARE_ADMIN"), "中间件", true);
    }
}
