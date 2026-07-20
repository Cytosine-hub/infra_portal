package com.middleware.manager.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolInvocation {
    private Long id;
    private Long sessionId;
    private String stepName;
    private String toolName;
    private String requestJson;
    private String responseJson;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long latencyMs;
    private Long createdBy;
    private LocalDateTime createdAt;
}
