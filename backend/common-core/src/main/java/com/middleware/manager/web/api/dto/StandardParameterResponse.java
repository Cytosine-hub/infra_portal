package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.StandardParameter;

import java.time.LocalDateTime;

public class StandardParameterResponse {
    private Long id;
    private Long standardDocumentId;
    private Long parameterStandardId;
    private String code;
    private String name;
    private String value;
    private String paramType;
    private String valueRange;
    private String description;
    private boolean active;
    private boolean deploymentStandard;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StandardParameterResponse from(StandardParameter parameter) {
        StandardParameterResponse response = new StandardParameterResponse();
        response.setId(parameter.getId());
        response.setStandardDocumentId(parameter.getStandardDocumentId());
        response.setParameterStandardId(parameter.getParameterStandardId());
        response.setCode(parameter.getCode());
        response.setName(parameter.getName());
        response.setValue(parameter.getValue());
        response.setParamType(parameter.getParamType());
        response.setValueRange(parameter.getValueRange());
        response.setDescription(parameter.getDescription());
        response.setActive(parameter.isActive());
        response.setDeploymentStandard(parameter.isDeploymentStandard());
        response.setCreatedAt(parameter.getCreatedAt());
        response.setUpdatedAt(parameter.getUpdatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStandardDocumentId() { return standardDocumentId; }
    public void setStandardDocumentId(Long standardDocumentId) { this.standardDocumentId = standardDocumentId; }
    public Long getParameterStandardId() { return parameterStandardId; }
    public void setParameterStandardId(Long parameterStandardId) { this.parameterStandardId = parameterStandardId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getParamType() { return paramType; }
    public void setParamType(String paramType) { this.paramType = paramType; }
    public String getValueRange() { return valueRange; }
    public void setValueRange(String valueRange) { this.valueRange = valueRange; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isDeploymentStandard() { return deploymentStandard; }
    public void setDeploymentStandard(boolean deploymentStandard) { this.deploymentStandard = deploymentStandard; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
