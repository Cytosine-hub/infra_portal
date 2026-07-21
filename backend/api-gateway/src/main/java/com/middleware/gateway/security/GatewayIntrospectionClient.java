package com.middleware.gateway.security;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface GatewayIntrospectionClient {
    Mono<GatewayIntrospectionResult> introspect(String token);
}
