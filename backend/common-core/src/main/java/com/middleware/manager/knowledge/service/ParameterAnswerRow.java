package com.middleware.manager.knowledge.service;

/**
 * 参数查询的行映射。放在 common-core 是因为 Mapper 在 common-core、
 * 而服务在 ai-service，两边都要引用。
 */
public class ParameterAnswerRow {
    private String code;
    private String name;
    private String value;
    private String paramType;
    private String valueRange;
    private String description;
    private boolean deploymentStandard;
    private String software;
    private String standardTitle;
    private String standardVersion;
    private String publishedAt;

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
    public boolean isDeploymentStandard() { return deploymentStandard; }
    public void setDeploymentStandard(boolean deploymentStandard) { this.deploymentStandard = deploymentStandard; }
    public String getSoftware() { return software; }
    public void setSoftware(String software) { this.software = software; }
    public String getStandardTitle() { return standardTitle; }
    public void setStandardTitle(String standardTitle) { this.standardTitle = standardTitle; }
    public String getStandardVersion() { return standardVersion; }
    public void setStandardVersion(String standardVersion) { this.standardVersion = standardVersion; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
}
