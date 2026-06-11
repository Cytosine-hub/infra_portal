package com.middleware.manager.agent.tool;

import org.springframework.security.core.Authentication;

public final class ToolContextHolder {
    private static final ThreadLocal<Authentication> AUTHENTICATION = new ThreadLocal<>();

    private ToolContextHolder() {}

    public static void setAuthentication(Authentication authentication) {
        AUTHENTICATION.set(authentication);
    }

    public static Authentication getAuthentication() {
        return AUTHENTICATION.get();
    }

    public static void clear() {
        AUTHENTICATION.remove();
    }
}
