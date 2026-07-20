package com.middleware.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.modules")
public class ModuleProperties {
    private boolean knowledgeEnabled = true;
    private boolean diagnosticsEnabled = true;

    public boolean isKnowledgeEnabled() {
        return knowledgeEnabled;
    }

    public void setKnowledgeEnabled(boolean knowledgeEnabled) {
        this.knowledgeEnabled = knowledgeEnabled;
    }

    public boolean isDiagnosticsEnabled() {
        return diagnosticsEnabled;
    }

    public void setDiagnosticsEnabled(boolean diagnosticsEnabled) {
        this.diagnosticsEnabled = diagnosticsEnabled;
    }
}
