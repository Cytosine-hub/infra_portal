package com.middleware.manager.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class GatewayAuthenticationToken extends UsernamePasswordAuthenticationToken {
    private final String displayName;
    private final String category;
    private final boolean categoryAdmin;

    private GatewayAuthenticationToken(String username, String displayName,
                                       Collection<SimpleGrantedAuthority> authorities,
                                       String category, boolean categoryAdmin) {
        super(username, null, authorities);
        this.displayName = displayName;
        this.category = category;
        this.categoryAdmin = categoryAdmin;
    }

    public static GatewayAuthenticationToken authenticated(
            String username, String displayName, List<String> roles,
            String category, boolean categoryAdmin) {
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new GatewayAuthenticationToken(
                username, displayName, authorities, category, categoryAdmin);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
        return category;
    }

    public boolean isCategoryAdmin() {
        return categoryAdmin;
    }
}
