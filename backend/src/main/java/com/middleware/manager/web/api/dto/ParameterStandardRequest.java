package com.middleware.manager.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ParameterStandardRequest {
    @NotBlank
    @Size(max = 160)
    private String title;

    private Long softwareTypeId;

    @Size(max = 80)
    private String softwareVersion;

    @Size(max = 20)
    private String code;

    @NotBlank
    private String content;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getSoftwareTypeId() { return softwareTypeId; }
    public void setSoftwareTypeId(Long softwareTypeId) { this.softwareTypeId = softwareTypeId; }
    public String getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(String softwareVersion) { this.softwareVersion = softwareVersion; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
