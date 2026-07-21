package com.middleware.manager.security.gateway;

import java.util.List;

public final class GatewayIdentityHeaders {
    public static final String USER = "X-User";
    public static final String DISPLAY_NAME = "X-Display-Name";
    public static final String ROLES = "X-Roles";
    public static final String CATEGORY = "X-Category";
    public static final String CATEGORY_ADMIN = "X-Category-Admin";
    public static final String SIGNATURE = "X-Gateway-Sign";

    public static final List<String> ALL = List.of(
            USER,
            DISPLAY_NAME,
            ROLES,
            CATEGORY,
            CATEGORY_ADMIN,
            SIGNATURE);

    private GatewayIdentityHeaders() {
    }
}
