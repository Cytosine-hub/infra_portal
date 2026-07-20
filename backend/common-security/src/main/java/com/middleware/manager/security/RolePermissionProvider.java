package com.middleware.manager.security;

import com.middleware.manager.domain.RoleEntity;

public interface RolePermissionProvider {
    RoleEntity getByAuthority(String authority);

    boolean isAdmin(RoleEntity role);

    boolean isCategoryAdmin(RoleEntity role);

    boolean canManageCategory(RoleEntity role, String category);

    boolean canReviewCategory(RoleEntity role, String category);
}
