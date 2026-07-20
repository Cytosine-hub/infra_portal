package com.middleware.community.security;

import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.repository.RoleMapper;
import com.middleware.manager.security.RolePermissionProvider;
import org.springframework.stereotype.Service;

@Service
public class CommunityRolePermissionProvider implements RolePermissionProvider {
    private static final String ROLE_SYS_ADMIN = "ROLE_SYS_ADMIN";

    private final RoleMapper roleMapper;

    public CommunityRolePermissionProvider(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public RoleEntity getByAuthority(String authority) {
        return roleMapper.findByAuthority(authority);
    }

    @Override
    public boolean isAdmin(RoleEntity role) {
        return role != null && ROLE_SYS_ADMIN.equals(role.getAuthority());
    }

    @Override
    public boolean isCategoryAdmin(RoleEntity role) {
        return role != null && role.isCategoryAdmin();
    }

    @Override
    public boolean canManageCategory(RoleEntity role, String category) {
        if (role == null) {
            return false;
        }
        if (isAdmin(role)) {
            return true;
        }
        return role.getManagedCategory() != null && role.getManagedCategory().equals(category);
    }

    @Override
    public boolean canReviewCategory(RoleEntity role, String category) {
        if (role == null) {
            return false;
        }
        if (isAdmin(role)) {
            return true;
        }
        return role.isCategoryAdmin()
                && role.getManagedCategory() != null
                && role.getManagedCategory().equals(category);
    }
}
