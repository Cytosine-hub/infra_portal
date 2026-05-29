package com.middleware.manager.web.api;

import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.AdminAccountService;
import com.middleware.manager.web.api.dto.AuthResponse;
import com.middleware.manager.web.api.dto.PasswordRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/account")
public class AdminAccountApiController {
    private final AdminAccountService adminAccountService;
    private final PermissionService permissionService;

    public AdminAccountApiController(AdminAccountService adminAccountService, PermissionService permissionService) {
        this.adminAccountService = adminAccountService;
        this.permissionService = permissionService;
    }

    @PostMapping("/password")
    public AuthResponse changePassword(@Valid @RequestBody PasswordRequest request,
                                       Authentication authentication) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match");
        }

        adminAccountService.changePassword(authentication.getName(), request.getCurrentPassword(), request.getNewPassword());
        RoleEntity role = permissionService.getCurrentRole(authentication);
        return new AuthResponse(authentication.getName(), adminAccountService.getDisplayNameByUsername(authentication.getName()), role != null ? role.getDisplayName() : "系统管理员");
    }
}
