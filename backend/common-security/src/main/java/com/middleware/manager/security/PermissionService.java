package com.middleware.manager.security;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    private final RolePermissionProvider roleService;

    public PermissionService(RolePermissionProvider roleService) {
        this.roleService = roleService;
    }

    public RoleEntity getCurrentRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            RoleEntity role = roleService.getByAuthority(authority.getAuthority());
            if (role != null) return role;
        }
        return null;
    }

    public boolean isAdmin(Authentication authentication) {
        return roleService.isAdmin(getCurrentRole(authentication));
    }

    public boolean isCategoryAdmin(Authentication authentication) {
        return roleService.isCategoryAdmin(getCurrentRole(authentication));
    }

    public boolean canManageCategory(Authentication authentication, String category) {
        return roleService.canManageCategory(getCurrentRole(authentication), category);
    }

    public String getManagedCategory(Authentication authentication) {
        RoleEntity role = getCurrentRole(authentication);
        return role != null ? role.getManagedCategory() : null;
    }

    public String requireManagedCategory(Authentication authentication) {
        String category = getManagedCategory(authentication);
        if (category == null) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
        return category;
    }

    public boolean canReview(Authentication authentication, String category) {
        return roleService.canReviewCategory(getCurrentRole(authentication), category);
    }

    public boolean canReviewAny(Authentication authentication) {
        RoleEntity role = getCurrentRole(authentication);
        return role != null && (roleService.isAdmin(role) || roleService.isCategoryAdmin(role));
    }
}
