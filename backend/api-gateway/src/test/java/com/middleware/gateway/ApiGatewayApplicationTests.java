package com.middleware.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;

@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("TC-GATEWAY-001 默认 profile 将中间件命令静态路由到 middleware-service")
    void defaultProfileUsesStaticRouteWithNacosDisabled() {
        RouteDefinition communityRoute = findRoute("community-api");
        RouteDefinition aiRoute = findRoute("ai-api");
        RouteDefinition coreRoute = findRoute("core-api");
        RouteDefinition middlewareRoute = findRoute("middleware-api");

        assertThat(communityRoute.getUri()).isEqualTo(URI.create("http://127.0.0.1:8082"));
        assertThat(communityRoute.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            assertThat(predicate.getArgs()).containsValue("/api/forum/**");
        });
        assertThat(aiRoute.getUri()).isEqualTo(URI.create("http://127.0.0.1:8083"));
        assertThat(aiRoute.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            assertThat(predicate.getArgs()).containsValues(
                    "/api/knowledge/**",
                    "/api/agent/**",
                    "/api/wiki/**",
                    "/api/ops-agent/**",
                    "/api/ops-agent/export/**");
        });
        assertThat(coreRoute.getUri()).isEqualTo(URI.create("http://127.0.0.1:8084"));
        assertThat(coreRoute.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            assertThat(predicate.getArgs()).containsValues(
                    "/api/auth/**",
                    "/api/admin/users/**",
                    "/api/admin/account/**",
                    "/api/admin/settings/**",
                    "/api/admin/releases/**",
                    "/api/admin/software-types/**",
                    "/api/admin/software-type-categories/**",
                    "/api/admin/parameter-standards/**",
                    "/api/admin/standard-documents/**",
                    "/api/admin/standard-parameters/**",
                    "/api/admin/reviews/**",
                    "/api/admin/revisions/**",
                    "/api/admin/images/**",
                    "/api/public/**",
                    "/files/**");
        });
        assertThat(middlewareRoute.getUri()).isEqualTo(URI.create("http://127.0.0.1:8085"));
        assertThat(middlewareRoute.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            assertThat(predicate.getArgs()).containsValue("/api/middleware-commands/**");
        });
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("app.security.introspection-base-url"))
                .isEqualTo("http://127.0.0.1:8084");
        assertThat(environment.getProperty("app.security.introspection-load-balanced", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("app.rate-limit.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("app.rate-limit.window-seconds", Integer.class)).isEqualTo(60);
        assertThat(environment.getProperty("app.rate-limit.max-client-keys", Integer.class)).isEqualTo(10_000);
        assertThat(environment.getProperty("app.rate-limit.download-per-window", Integer.class)).isEqualTo(6);
        assertThat(environment.getProperty("app.rate-limit.document-per-window", Integer.class)).isEqualTo(60);
        assertThat(environment.getProperty("app.rate-limit.document-file-per-window", Integer.class)).isEqualTo(18);
        assertThat(environment.getProperty("app.rate-limit.forum-post-per-window", Integer.class)).isEqualTo(120);
    }

    private RouteDefinition findRoute(String routeId) {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block(Duration.ofSeconds(5));
        assertThat(routes).isNotNull();
        return routes.stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst()
                .orElseThrow();
    }
}
