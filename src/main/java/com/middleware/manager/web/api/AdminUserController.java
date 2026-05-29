package com.middleware.manager.web.api;

import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.AdminAccountService;
import com.middleware.manager.service.RoleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminAccountService adminAccountService;
    private final RoleService roleService;
    private final PermissionService permissionService;

    public AdminUserController(AdminAccountService adminAccountService,
                               RoleService roleService,
                               PermissionService permissionService) {
        this.adminAccountService = adminAccountService;
        this.roleService = roleService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        return adminAccountService.listUsers().stream()
                .map(this::toUserMap)
                .collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, Object> createUser(@Valid @RequestBody CreateUserRequest request) {
        AdminAccount account = adminAccountService.createUser(
                request.username, request.displayName, request.password, request.role);
        return toUserMap(account);
    }

    @PutMapping("/{id}/role")
    public Map<String, Object> updateRole(@PathVariable Long id,
                                          @Valid @RequestBody UpdateRoleRequest request) {
        AdminAccount account = adminAccountService.updateUserRole(id, request.role);
        return toUserMap(account);
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id,
                              @Valid @RequestBody ResetPasswordRequest request) {
        adminAccountService.resetPassword(id, request.newPassword);
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        adminAccountService.deleteUser(id);
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> listRoles() {
        return roleService.getAllRoles().stream().map(this::toRoleMap).collect(Collectors.toList());
    }

    // ── 角色管理（仅系统管理员）──

    @PostMapping("/roles")
    public Map<String, Object> createRole(@Valid @RequestBody CreateRoleRequest request,
                                          Authentication auth) {
        if (!permissionService.isAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可创建角色");
        }
        RoleEntity role = roleService.createRole(request.displayName, request.authority,
                request.managedCategory, request.categoryAdmin);
        return toRoleMap(role);
    }

    @PutMapping("/roles/{id}")
    public Map<String, Object> updateRole(@PathVariable Long id,
                                          @Valid @RequestBody CreateRoleRequest request,
                                          Authentication auth) {
        if (!permissionService.isAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可修改角色");
        }
        RoleEntity role = roleService.updateRole(id, request.displayName, request.authority,
                request.managedCategory, request.categoryAdmin);
        return toRoleMap(role);
    }

    @DeleteMapping("/roles/{id}")
    public void deleteRole(@PathVariable Long id, Authentication auth) {
        if (!permissionService.isAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可删除角色");
        }
        roleService.deleteRole(id);
    }

    private Map<String, Object> toUserMap(AdminAccount account) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", account.getId());
        map.put("username", account.getUsername());
        map.put("displayName", account.getDisplayName());
        map.put("role", account.getRole());
        map.put("createdAt", account.getCreatedAt());
        return map;
    }

    private Map<String, Object> toRoleMap(RoleEntity role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", role.getId());
        map.put("name", role.getDisplayName());
        map.put("authority", role.getAuthority());
        map.put("category", role.getManagedCategory() != null ? role.getManagedCategory() : "");
        map.put("categoryAdmin", role.isCategoryAdmin());
        map.put("systemRole", role.isSystemRole());
        return map;
    }

    static class CreateUserRequest {
        @NotBlank @Size(min = 2, max = 60)
        public String username;
        public String displayName;
        @NotBlank @Size(min = 6, max = 64)
        public String password;
        @NotBlank
        public String role;
    }

    static class UpdateRoleRequest {
        @NotBlank
        public String role;
    }

    static class ResetPasswordRequest {
        @NotBlank @Size(min = 6, max = 64)
        public String newPassword;
    }

    static class CreateRoleRequest {
        @NotBlank
        public String displayName;
        @NotBlank
        public String authority;
        public String managedCategory;
        public boolean categoryAdmin;
    }
}
