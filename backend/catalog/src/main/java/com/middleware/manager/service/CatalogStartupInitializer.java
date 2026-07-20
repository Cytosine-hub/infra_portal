package com.middleware.manager.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@ConditionalOnProperty(name = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class CatalogStartupInitializer implements ApplicationRunner {
    private final SoftwareTypeService softwareTypeService;

    public CatalogStartupInitializer(SoftwareTypeService softwareTypeService) {
        this.softwareTypeService = softwareTypeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        softwareTypeService.initializeDefaults();
    }
}
