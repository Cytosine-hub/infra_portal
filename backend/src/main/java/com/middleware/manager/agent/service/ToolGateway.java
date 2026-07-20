package com.middleware.manager.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.agent.domain.AgentToolInvocation;
import com.middleware.manager.agent.tool.Tool;
import com.middleware.manager.agent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class ToolGateway {
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String TOOL_EXECUTION_FAILED = "TOOL_EXECUTION_FAILED";
    private static final String MASKED_VALUE = "***";
    private static final int MAX_AUDIT_STRING_LENGTH = 4000;
    private static final List<String> SENSITIVE_KEYS = List.of(
            "password", "passwd", "token", "secret", "apikey", "api_key", "authorization", "credential"
    );

    private final AgentToolInvocationService invocationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolGateway(AgentToolInvocationService invocationService) {
        this.invocationService = invocationService;
    }

    public ToolResult call(Tool tool, Map<String, Object> args, Long sessionId, Long actorId, String stepName) {
        long start = System.currentTimeMillis();
        ToolResult result;
        try {
            String output = tool.call(args);
            long latencyMs = System.currentTimeMillis() - start;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("text", output);
            result = ToolResult.success(output, data, latencyMs);
            audit(tool, args, sessionId, actorId, stepName, result, STATUS_SUCCESS);
            return result;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Tool call failed tool={}, step={}, sessionId={}: {}",
                    tool.name(), stepName, sessionId, e.getMessage());
            result = ToolResult.failure(TOOL_EXECUTION_FAILED, "工具执行失败，请查看后台日志", latencyMs);
            audit(tool, args, sessionId, actorId, stepName, result, STATUS_FAILED);
            return result;
        }
    }

    private void audit(Tool tool, Map<String, Object> args, Long sessionId, Long actorId, String stepName,
                       ToolResult result, String status) {
        try {
            AgentToolInvocation invocation = new AgentToolInvocation();
            invocation.setSessionId(sessionId);
            invocation.setStepName(stepName);
            invocation.setToolName(tool.name());
            invocation.setRequestJson(toJson(redactForAudit(args)));
            invocation.setResponseJson(toJson(redactForAudit(result.toMap())));
            invocation.setStatus(status);
            invocation.setErrorCode(result.getErrorCode());
            invocation.setErrorMessage(result.isSuccess() ? null : result.getSummary());
            invocation.setLatencyMs(result.getLatencyMs());
            invocation.setCreatedBy(actorId);
            invocation.setCreatedAt(LocalDateTime.now());
            invocationService.record(invocation);
        } catch (Exception e) {
            log.warn("Agent tool invocation audit failed: {}", e.getMessage());
        }
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private Object redactForAudit(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                redacted.put(key, isSensitiveKey(key) ? MASKED_VALUE : redactForAudit(entry.getValue()));
            }
            return redacted;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::redactForAudit).toList();
        }
        if (value instanceof String text && text.length() > MAX_AUDIT_STRING_LENGTH) {
            return text.substring(0, MAX_AUDIT_STRING_LENGTH) + "...";
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }
}
