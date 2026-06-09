package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.StandardDocument;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class StandardDocumentResponse {
    private Long id;
    private String title;
    private String documentType;
    private String status;
    private String statusLabel;
    private String version;
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
    private String reviewComment;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String storedFileName;
    private String originalFileName;
    private boolean canEdit;
    private boolean hasDiff;
    private Long pendingReviewRecordId;
    private List<String> availableActions = new ArrayList<>();

    public static StandardDocumentResponse from(StandardDocument document, String renderedContent) {
        StandardDocumentResponse response = new StandardDocumentResponse();
        response.setId(document.getId());
        response.setTitle(document.getTitle());
        response.setDocumentType(document.getDocumentType());
        response.setStatus(document.getStatus());
        response.setStatusLabel(computeStatusLabel(document.getStatus()));
        response.setVersion(document.getVersion());
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
        response.setReviewComment(document.getReviewComment());
        response.setSubmittedAt(document.getSubmittedAt());
        response.setReviewedAt(document.getReviewedAt());
        response.setReviewedBy(document.getReviewedBy());
        response.setPublishedAt(document.getPublishedAt());
        response.setStoredFileName(document.getStoredFileName());
        response.setOriginalFileName(document.getOriginalFileName());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedAt(document.getUpdatedAt());
        String docStatus = document.getStatus() != null ? document.getStatus() : "DRAFT";
        boolean underReview = document.getPendingReviewRecordId() != null;
        response.setCanEdit(!underReview && ("DRAFT".equals(docStatus) || "MODIFYING".equals(docStatus)));
        response.setStatusLabel(underReview ? "审核中" : computeStatusLabel(document.getStatus()));
        response.setHasDiff(document.getPreviousContent() != null);
        response.setPendingReviewRecordId(document.getPendingReviewRecordId());
        response.setAvailableActions(computeAvailableActions(docStatus, underReview));
        return response;
    }

    private static String computeStatusLabel(String status) {
        if (status == null) return "草稿";
        switch (status) {
            case "DRAFT": return "草稿";
            case "PENDING_REVIEW": return "审核中";
            case "PUBLISHED": return "已发布";
            case "MODIFYING": return "修改中";
            default: return status;
        }
    }

    private static List<String> computeAvailableActions(String status, boolean underReview) {
        if (status == null) status = "DRAFT";
        List<String> actions = new ArrayList<>();
        if (underReview) {
            return actions;
        }
        switch (status) {
            case "DRAFT":
                actions.add("submit-review");
                actions.add("edit");
                actions.add("delete");
                break;
            case "PUBLISHED":
                actions.add("start-modify");
                break;
            case "MODIFYING":
                actions.add("submit-review");
                actions.add("edit");
                actions.add("cancel-modify");
                actions.add("delete");
                break;
        }
        return actions;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
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
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
    public boolean isHasDiff() { return hasDiff; }
    public void setHasDiff(boolean hasDiff) { this.hasDiff = hasDiff; }
    public List<String> getAvailableActions() { return availableActions; }
    public void setAvailableActions(List<String> availableActions) { this.availableActions = availableActions; }
    public Long getPendingReviewRecordId() { return pendingReviewRecordId; }
    public void setPendingReviewRecordId(Long pendingReviewRecordId) { this.pendingReviewRecordId = pendingReviewRecordId; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
}
