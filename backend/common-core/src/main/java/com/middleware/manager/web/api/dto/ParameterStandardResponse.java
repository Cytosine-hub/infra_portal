package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.ParameterStandard;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ParameterStandardResponse {
    private Long id;
    private String title;
    private String category;
    private String software;
    private Long softwareTypeId;
    private String softwareVersion;
    private String code;
    private String status;
    private String statusLabel;
    private String version;
    private String content;
    private String renderedContent;
    private LocalDateTime publishedAt;
    private Long pendingReviewRecordId;
    private List<StandardDocumentResponse> relatedDocuments = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ParameterStandardResponse from(ParameterStandard standard, String renderedContent) {
        ParameterStandardResponse response = new ParameterStandardResponse();
        response.setId(standard.getId());
        response.setTitle(standard.getTitle());
        response.setCategory(standard.getCategory());
        response.setSoftware(standard.getSoftware());
        response.setSoftwareTypeId(standard.getSoftwareTypeId());
        response.setSoftwareVersion(standard.getSoftwareVersion());
        response.setCode(standard.getCode());
        response.setStatus(standard.getStatus());
        response.setStatusLabel(computeStatusLabel(standard.getStatus()));
        response.setVersion(standard.getVersion());
        response.setContent(standard.getContent());
        response.setRenderedContent(renderedContent);
        response.setPublishedAt(standard.getPublishedAt());
        response.setPendingReviewRecordId(standard.getPendingReviewRecordId());
        response.setCreatedAt(standard.getCreatedAt());
        response.setUpdatedAt(standard.getUpdatedAt());
        return response;
    }

    private static String computeStatusLabel(String status) {
        if (status == null) return "草稿";
        switch (status) {
            case "DRAFT": return "草稿";
            case "PUBLISHED": return "已发布";
            case "MODIFYING": return "修改中";
            default: return status;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSoftware() { return software; }
    public void setSoftware(String software) { this.software = software; }
    public Long getSoftwareTypeId() { return softwareTypeId; }
    public void setSoftwareTypeId(Long softwareTypeId) { this.softwareTypeId = softwareTypeId; }
    public String getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(String softwareVersion) { this.softwareVersion = softwareVersion; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getRenderedContent() { return renderedContent; }
    public void setRenderedContent(String renderedContent) { this.renderedContent = renderedContent; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Long getPendingReviewRecordId() { return pendingReviewRecordId; }
    public void setPendingReviewRecordId(Long pendingReviewRecordId) { this.pendingReviewRecordId = pendingReviewRecordId; }
    public List<StandardDocumentResponse> getRelatedDocuments() { return relatedDocuments; }
    public void setRelatedDocuments(List<StandardDocumentResponse> relatedDocuments) { this.relatedDocuments = relatedDocuments; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
