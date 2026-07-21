package com.middleware.manager.web.api.dto;

import java.util.List;

public record AuthIntrospectionResponse(
        boolean valid,
        String username,
        String displayName,
        List<String> roles,
        String category,
        boolean categoryAdmin) {

    public AuthIntrospectionResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public static AuthIntrospectionResponse invalid() {
        return new AuthIntrospectionResponse(false, null, null, List.of(), null, false);
    }
}
