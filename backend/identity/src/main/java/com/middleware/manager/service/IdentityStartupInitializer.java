package com.middleware.manager.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@ConditionalOnProperty(name = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class IdentityStartupInitializer implements ApplicationRunner {
    private final RoleService roleService;
    private final AdminAccountService adminAccountService;

    public IdentityStartupInitializer(RoleService roleService, AdminAccountService adminAccountService) {
        this.roleService = roleService;
        this.adminAccountService = adminAccountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        roleService.initializeDefaults();
        adminAccountService.initializeDefaults();
    }
}
