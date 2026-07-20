package com.middleware.manager.web.api;

import com.middleware.manager.service.SystemSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicConfigController {
    private final SystemSettingService settingService;

    public PublicConfigController(SystemSettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "knowledgeEnabled", settingService.getBoolean("knowledge-enabled", true),
                "diagnosticsEnabled", settingService.getBoolean("diagnostics-enabled", true),
                "wikiEnabled", settingService.getBoolean("wiki-enabled", true)
        );
    }
}
