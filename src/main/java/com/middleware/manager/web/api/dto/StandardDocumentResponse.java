package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.StandardDocument;

import java.time.LocalDateTime;

public class StandardDocumentResponse {
    private Long id;
    private String title;
    private String documentType;
    private String status;
    private String summary;
    private Long relatedStandardDocumentId;
    private Long softwareTypeId;
    private String category;
    private String software;
    private String softwareVersion;
    private String standardVersion;
    private String code;
    private String content;
    private String renderedContent;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StandardDocumentResponse from(StandardDocument document, String renderedContent) {
        StandardDocumentResponse response = new StandardDocumentResponse();
        response.setId(document.getId());
        response.setTitle(document.getTitle());
        response.setDocumentType(document.getDocumentType());
        response.setStatus(document.getStatus());
        response.setSummary(document.getSummary());
        response.setRelatedStandardDocumentId(document.getRelatedStandardDocumentId());
        response.setSoftwareTypeId(document.getSoftwareTypeId());
        response.setCategory(document.getCategory());
        response.setSoftware(document.getSoftware());
        response.setSoftwareVersion(document.getSoftwareVersion());
        response.setStandardVersion(document.getStandardVersion());
        response.setCode(document.getCode());
        response.setContent(document.getContent());
        response.setRenderedContent(renderedContent);
        response.setPublishedAt(document.getPublishedAt());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedAt(document.getUpdatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
    public String getRenderedContent() { return renderedContent; }
    public void setRenderedContent(String renderedContent) { this.renderedContent = renderedContent; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
