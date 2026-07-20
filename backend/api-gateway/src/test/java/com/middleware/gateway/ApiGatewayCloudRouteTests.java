package com.middleware.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("cloud")
@SpringBootTest(properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false"
})
class ApiGatewayCloudRouteTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    @DisplayName("TC-GATEWAY-002 cloud profile 分别路由论坛与其余 API")
    void cloudProfileUsesLoadBalancedAppRoute() {
        Map<String, URI> routes = routeDefinitionLocator.getRouteDefinitions()
                .filter(route -> "community-api".equals(route.getId()) || "app-api".equals(route.getId()))
                .collect(Collectors.toMap(route -> route.getId(), route -> route.getUri()))
                .block(Duration.ofSeconds(5));

        assertThat(routes).containsEntry("community-api", URI.create("lb://community-service"));
        assertThat(routes).containsEntry("app-api", URI.create("lb://middleware-resource-manager-app"));
    }
}
