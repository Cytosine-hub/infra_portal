package com.middleware.manager.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolResult {
    private boolean success;
    private String errorCode;
    private String summary;
    private Object data;
    private double confidence;
    private long latencyMs;

    public static ToolResult success(String summary, Object data, long latencyMs) {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setSummary(summary);
        result.setData(data);
        result.setConfidence(1.0);
        result.setLatencyMs(latencyMs);
        return result;
    }

    public static ToolResult failure(String errorCode, String summary, long latencyMs) {
        ToolResult result = new ToolResult();
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setSummary(summary);
        result.setConfidence(0.0);
        result.setLatencyMs(latencyMs);
        return result;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", success);
        map.put("errorCode", errorCode);
        map.put("summary", summary);
        map.put("data", data);
        map.put("confidence", confidence);
        map.put("latencyMs", latencyMs);
        return map;
    }

    public String toPromptText() {
        if (success) {
            return summary != null ? summary : "";
        }
        return "调用失败: " + (summary != null ? summary : "工具执行失败");
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
