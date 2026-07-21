package com.middleware.gateway.security;

import com.middleware.manager.security.gateway.GatewaySignatureService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GatewayAuthenticationConfiguration {

    @Bean
    GatewaySignatureService gatewaySignatureService(
            @Value("${app.security.gateway-signing-secret}") String secret) {
        return new GatewaySignatureService(secret);
    }

    @Bean
    @Qualifier("gatewayDirectWebClientBuilder")
    WebClient.Builder gatewayDirectWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @LoadBalanced
    @Qualifier("gatewayLoadBalancedWebClientBuilder")
    WebClient.Builder gatewayLoadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
