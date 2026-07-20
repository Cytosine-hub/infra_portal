package com.middleware.manager.service;

import com.middleware.manager.config.ApiAuditLogger;
import com.middleware.manager.domain.ApiAuditLog;
import com.middleware.manager.repository.ApiAuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ApiAuditLogService implements ApiAuditLogger {
    private final ApiAuditLogMapper mapper;

    public ApiAuditLogService(ApiAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Async
    @Transactional
    public void log(String username, String method, String path, String queryString,
                    Integer statusCode, String ipAddress, String userAgent, Long durationMs) {
        try {
            ApiAuditLog auditLog = new ApiAuditLog();
            auditLog.setUsername(username);
            auditLog.setMethod(method);
            auditLog.setPath(path);
            auditLog.setQueryString(queryString);
            auditLog.setStatusCode(statusCode);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setDurationMs(durationMs);
            mapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }
}
