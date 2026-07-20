package com.middleware.manager.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StandardDocumentRequest {
    @NotBlank
    @Size(max = 160)
    private String title;

    @NotBlank
    @Size(max = 40)
    private String documentType = "MANUAL";

    @Size(max = 500)
    private String summary;

    private Long relatedStandardDocumentId;

    private Long softwareTypeId;

    @Size(max = 60)
    private String category;

    @Size(max = 120)
    private String software;

    @Size(max = 80)
    private String softwareVersion;

    @Size(max = 80)
    private String standardVersion;

    @Size(max = 20)
    private String code;

    private String content;

    @Size(max = 255)
    private String storedFileName;

    @Size(max = 255)
    private String originalFileName;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Long getRelatedStandardDocumentId() { return relatedStandardDocumentId; }
    public void setRelatedStandardDocumentId(Long relatedStandardDocumentId) { this.relatedStandardDocumentId = relatedStandardDocumentId; }
    public Long getSoftwareTypeId() { return softwareTypeId; }
    public void setSoftwareTypeId(Long softwareTypeId) { this.softwareTypeId = softwareTypeId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSoftware() { return software; }
    public void setSoftware(String software) { this.software = software; }
    public String getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(String softwareVersion) { this.softwareVersion = softwareVersion; }
    public String getStandardVersion() { return standardVersion; }
    public void setStandardVersion(String standardVersion) { this.standardVersion = standardVersion; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
}
