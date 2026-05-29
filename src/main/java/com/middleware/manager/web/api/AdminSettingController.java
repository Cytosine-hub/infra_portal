package com.middleware.manager.web.api;

import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.SystemSettingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingController {
    private final SystemSettingService settingService;
    private final PermissionService permissionService;

    public AdminSettingController(SystemSettingService settingService, PermissionService permissionService) {
        this.settingService = settingService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public Map<String, String> list(Authentication authentication) {
        requireSysAdmin(authentication);
        return settingService.getAllSettings();
    }

    @PutMapping
    public Map<String, String> update(@RequestBody Map<String, String> settings,
                                      Authentication authentication) {
        requireSysAdmin(authentication);
        settingService.updateSettings(settings);
        return settingService.getAllSettings();
    }

    private void requireSysAdmin(Authentication authentication) {
        if (!permissionService.isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可操作系统设置");
        }
    }
}
