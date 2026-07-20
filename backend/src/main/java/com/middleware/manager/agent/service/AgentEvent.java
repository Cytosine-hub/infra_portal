package com.middleware.manager.agent.service;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentEvent {
    private final String type;
    private final Map<String, Object> payload;

    private AgentEvent(String type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
    }

    public static AgentEvent of(String type, Map<String, Object> payload) {
        return new AgentEvent(type, payload);
    }

    public static AgentEvent stepStarted(String stepName, String toolName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepName", stepName);
        payload.put("toolName", toolName);
        return of("step_started", payload);
    }

    public static AgentEvent toolResult(String stepName, String toolName, boolean success,
                                        String summary, long latencyMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepName", stepName);
        payload.put("toolName", toolName);
        payload.put("success", success);
        payload.put("summary", summary);
        payload.put("latencyMs", latencyMs);
        return of("tool_result", payload);
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
