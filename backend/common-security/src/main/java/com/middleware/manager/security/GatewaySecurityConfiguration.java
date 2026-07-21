package com.middleware.manager.security;

import com.middleware.manager.security.gateway.GatewaySignatureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewaySecurityConfiguration {

    @Bean
    GatewaySignatureService gatewaySignatureService(
            @Value("${app.security.gateway-signing-secret}") String secret) {
        return new GatewaySignatureService(secret);
    }
}
