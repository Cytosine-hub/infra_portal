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
    @DisplayName("TC-GATEWAY-001 默认 profile 将论坛、AI 和平台核心静态路由到独立服务")
    void defaultProfileUsesStaticRouteWithNacosDisabled() {
        RouteDefinition communityRoute = findRoute("community-api");
        RouteDefinition aiRoute = findRoute("ai-api");
        RouteDefinition coreRoute = findRoute("core-api");
        RouteDefinition appRoute = findRoute("app-api");

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
        assertThat(appRoute.getUri()).isEqualTo(URI.create("http://127.0.0.1:8081"));
        assertThat(appRoute.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            assertThat(predicate.getArgs()).containsValue("/api/**");
        });
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("app.security.introspection-base-url"))
                .isEqualTo("http://127.0.0.1:8084");
        assertThat(environment.getProperty("app.security.introspection-load-balanced", Boolean.class))
                .isFalse();
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
