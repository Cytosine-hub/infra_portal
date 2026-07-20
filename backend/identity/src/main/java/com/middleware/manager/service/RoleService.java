package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.RoleMapper;
import com.middleware.manager.security.RolePermissionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RoleService implements RolePermissionProvider {
    private static final String ROLE_SYS_ADMIN = "ROLE_SYS_ADMIN";

    private final RoleMapper mapper;

    // Cache loaded roles for fast lookup
    private volatile Map<String, RoleEntity> byDisplayName = new ConcurrentHashMap<>();
    private volatile Map<String, RoleEntity> byAuthority = new ConcurrentHashMap<>();

    public RoleService(RoleMapper mapper) {
        this.mapper = mapper;
    }

    public void initializeDefaults() {
        seedDefaultRoles();
        refreshCache();
    }

    private void seedDefaultRoles() {
        if (mapper.count() > 0) return;
        String[][] defaults = {
            {"系统管理员", "ROLE_SYS_ADMIN", null, "false", "true"},
            {"中间件管理员", "ROLE_MIDDLEWARE_ADMIN", "中间件", "true", "true"},
            {"数据库管理员", "ROLE_DATABASE_ADMIN", "数据库", "true", "true"},
            {"主机管理员", "ROLE_HOST_ADMIN", "主机", "true", "true"},
            {"网络管理员", "ROLE_NETWORK_ADMIN", "网络", "true", "true"},
            {"网络安全管理员", "ROLE_SECURITY_ADMIN", "安全", "true", "true"},
            {"中间件管理岗", "ROLE_MIDDLEWARE_MGR", "中间件", "false", "true"},
            {"数据库管理岗", "ROLE_DATABASE_MGR", "数据库", "false", "true"},
            {"主机管理岗", "ROLE_HOST_MGR", "主机", "false", "true"},
            {"网络管理岗", "ROLE_NETWORK_MGR", "网络", "false", "true"},
            {"网络安全岗", "ROLE_SECURITY_MGR", "安全", "false", "true"},
            {"开发经理", "ROLE_DEV_MGR", null, "false", "true"},
            {"运维经理", "ROLE_OPS_MGR", null, "false", "true"},
        };
        for (String[] d : defaults) {
            RoleEntity r = new RoleEntity();
            r.setDisplayName(d[0]);
            r.setAuthority(d[1]);
            r.setManagedCategory(d[2]);
            r.setCategoryAdmin(Boolean.parseBoolean(d[3]));
            r.setSystemRole(Boolean.parseBoolean(d[4]));
            r.setCreatedAt(LocalDateTime.now());
            mapper.insert(r);
        }
    }

    public void refreshCache() {
        List<RoleEntity> all = mapper.findAll();
        Map<String, RoleEntity> dnMap = new ConcurrentHashMap<>();
        Map<String, RoleEntity> auMap = new ConcurrentHashMap<>();
        for (RoleEntity r : all) {
            dnMap.put(r.getDisplayName(), r);
            auMap.put(r.getAuthority(), r);
        }
        this.byDisplayName = dnMap;
        this.byAuthority = auMap;
    }

    public RoleEntity getByDisplayName(String displayName) {
        return byDisplayName.get(displayName);
    }

    @Override
    public RoleEntity getByAuthority(String authority) {
        return byAuthority.get(authority);
    }

    public List<RoleEntity> getAllRoles() {
        return mapper.findAll();
    }

    public RoleEntity createRole(String displayName, String authority, String managedCategory, boolean categoryAdmin) {
        RoleEntity r = new RoleEntity();
        r.setDisplayName(displayName);
        r.setAuthority(authority);
        r.setManagedCategory(managedCategory);
        r.setCategoryAdmin(categoryAdmin);
        r.setSystemRole(false);
        r.setCreatedAt(LocalDateTime.now());
        mapper.insert(r);
        refreshCache();
        log.info("角色已创建 authority={}", authority);
        return r;
    }

    public RoleEntity updateRole(Long id, String displayName, String authority, String managedCategory, boolean categoryAdmin) {
        RoleEntity r = mapper.findById(id);
        if (r == null) throw new NotFoundException(ErrorCode.ROLE_NOT_FOUND, ErrorMessages.ROLE_NOT_FOUND);
        r.setDisplayName(displayName);
        r.setAuthority(authority);
        r.setManagedCategory(managedCategory);
        r.setCategoryAdmin(categoryAdmin);
        mapper.update(r);
        refreshCache();
        log.info("角色已更新 id={}", id);
        return r;
    }

    public void deleteRole(Long id) {
        RoleEntity r = mapper.findById(id);
        if (r == null) throw new NotFoundException(ErrorCode.ROLE_NOT_FOUND, ErrorMessages.ROLE_NOT_FOUND);
        if (r.isSystemRole()) throw new BusinessException(ErrorCode.FORBIDDEN, ErrorMessages.ROLE_CANNOT_DELETE_SYSTEM);
        mapper.deleteById(id);
        refreshCache();
        log.info("角色已删除 id={}", id);
    }

    // ── Permission helpers ──

    @Override
    public boolean isAdmin(RoleEntity role) {
        return role != null && ROLE_SYS_ADMIN.equals(role.getAuthority());
    }

    @Override
    public boolean isCategoryAdmin(RoleEntity role) {
        return role != null && role.isCategoryAdmin();
    }

    public boolean hasAdminAccess(RoleEntity role) {
        return role != null && (isAdmin(role) || role.isCategoryAdmin() || role.getManagedCategory() != null);
    }

    @Override
    public boolean canManageCategory(RoleEntity role, String category) {
        if (role == null) return false;
        if (isAdmin(role)) return true;
        if (role.getManagedCategory() == null) return false;
        return role.getManagedCategory().equals(category);
    }

    @Override
    public boolean canReviewCategory(RoleEntity role, String category) {
        if (role == null) return false;
        if (isAdmin(role)) return true;
        if (!role.isCategoryAdmin()) return false;
        return role.getManagedCategory() != null && role.getManagedCategory().equals(category);
    }
}
