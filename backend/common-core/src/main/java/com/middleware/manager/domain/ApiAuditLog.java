package com.middleware.manager.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiAuditLog {
    private Long id;
    private String username;
    private String method;
    private String path;
    private String queryString;
    private Integer statusCode;
    private String ipAddress;
    private String userAgent;
    private Long durationMs;
    private LocalDateTime createdAt;
}
