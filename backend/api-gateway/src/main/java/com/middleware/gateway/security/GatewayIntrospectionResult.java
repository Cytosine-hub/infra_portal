package com.middleware.gateway.security;

import java.util.List;

public record GatewayIntrospectionResult(
        boolean valid,
        String username,
        String displayName,
        List<String> roles,
        String category,
        boolean categoryAdmin) {

    public GatewayIntrospectionResult {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public static GatewayIntrospectionResult invalid() {
        return new GatewayIntrospectionResult(false, null, null, List.of(), null, false);
    }
}
