package com.middleware.manager.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StandardParameterRequest {
    private Long standardDocumentId;

    private Long parameterStandardId;

    @NotBlank
    @Size(max = 80)
    private String code;

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    @Size(max = 500)
    private String value;

    @NotBlank
    @Size(max = 60)
    private String paramType;

    @NotBlank
    @Size(max = 200)
    private String valueRange;

    @Size(max = 500)
    private String description;

    private boolean active = true;

    private boolean deploymentStandard;

    public Long getStandardDocumentId() {
        return standardDocumentId;
    }

    public void setStandardDocumentId(Long standardDocumentId) {
        this.standardDocumentId = standardDocumentId;
    }

    public Long getParameterStandardId() {
        return parameterStandardId;
    }

    public void setParameterStandardId(Long parameterStandardId) {
        this.parameterStandardId = parameterStandardId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    public String getValueRange() {
        return valueRange;
    }

    public void setValueRange(String valueRange) {
        this.valueRange = valueRange;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDeploymentStandard() {
        return deploymentStandard;
    }

    public void setDeploymentStandard(boolean deploymentStandard) {
        this.deploymentStandard = deploymentStandard;
    }
}
