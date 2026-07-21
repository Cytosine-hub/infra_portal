package com.middleware.manager.security;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    public PermissionService() {
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication instanceof GatewayAuthenticationToken
                && hasAuthority(authentication, "ROLE_SYS_ADMIN");
    }

    public boolean isCategoryAdmin(Authentication authentication) {
        return authentication instanceof GatewayAuthenticationToken gatewayAuthentication
                && gatewayAuthentication.isCategoryAdmin();
    }

    public boolean canManageCategory(Authentication authentication, String category) {
        return authentication instanceof GatewayAuthenticationToken
                && (isAdmin(authentication)
                || category != null && category.equals(getManagedCategory(authentication)));
    }

    public String getManagedCategory(Authentication authentication) {
        return authentication instanceof GatewayAuthenticationToken gatewayAuthentication
                ? gatewayAuthentication.getCategory()
                : null;
    }

    public String requireManagedCategory(Authentication authentication) {
        String category = getManagedCategory(authentication);
        if (category == null) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
        return category;
    }

    public boolean canReview(Authentication authentication, String category) {
        return authentication instanceof GatewayAuthenticationToken
                && (isAdmin(authentication)
                || isCategoryAdmin(authentication)
                && category != null
                && category.equals(getManagedCategory(authentication)));
    }

    public boolean canReviewAny(Authentication authentication) {
        return authentication instanceof GatewayAuthenticationToken
                && (isAdmin(authentication) || isCategoryAdmin(authentication));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }
}
