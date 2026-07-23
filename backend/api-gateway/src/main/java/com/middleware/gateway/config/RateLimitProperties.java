package com.middleware.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int windowSeconds = 60;
    private int maxClientKeys = 10_000;
    private int downloadPerWindow = 6;
    private int documentPerWindow = 60;
    private int documentFilePerWindow = 18;
    private int forumPostPerWindow = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getMaxClientKeys() {
        return maxClientKeys;
    }

    public void setMaxClientKeys(int maxClientKeys) {
        this.maxClientKeys = maxClientKeys;
    }

    public int getDownloadPerWindow() {
        return downloadPerWindow;
    }

    public void setDownloadPerWindow(int downloadPerWindow) {
        this.downloadPerWindow = downloadPerWindow;
    }

    public int getDocumentPerWindow() {
        return documentPerWindow;
    }

    public void setDocumentPerWindow(int documentPerWindow) {
        this.documentPerWindow = documentPerWindow;
    }

    public int getDocumentFilePerWindow() {
        return documentFilePerWindow;
    }

    public void setDocumentFilePerWindow(int documentFilePerWindow) {
        this.documentFilePerWindow = documentFilePerWindow;
    }

    public int getForumPostPerWindow() {
        return forumPostPerWindow;
    }

    public void setForumPostPerWindow(int forumPostPerWindow) {
        this.forumPostPerWindow = forumPostPerWindow;
    }
}
