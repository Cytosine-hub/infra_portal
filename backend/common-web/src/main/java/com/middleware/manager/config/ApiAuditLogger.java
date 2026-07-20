package com.middleware.manager.config;

public interface ApiAuditLogger {
    void log(String username, String method, String path, String queryString,
             Integer statusCode, String ipAddress, String userAgent, Long durationMs);
}
