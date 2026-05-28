package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.domain.SoftwareType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReleaseResponse {
    private Long id;
    private Long softwareTypeId;
    private String softwareTypeCategory;
    private String softwareTypeName;
    private String middlewareName;
    private String version;
    private String platform;
    private String description;
    private LocalDate releasedAt;
    private boolean published;
    private String downloadToken;
    private String originalFileName;
    private String contentType;
    private long fileSize;
    private long downloadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String downloadUrl;
    private Long standardDocumentId;
    private String standardDocumentTitle;

    public static ReleaseResponse from(ReleaseAsset asset) {
        return from(asset, null);
    }

    public static ReleaseResponse from(ReleaseAsset asset, SoftwareType softwareType) {
        ReleaseResponse response = new ReleaseResponse();
        response.setId(asset.getId());
        response.setSoftwareTypeId(asset.getSoftwareTypeId());
        if (softwareType != null) {
            response.setSoftwareTypeCategory(softwareType.getCategory());
            response.setSoftwareTypeName(softwareType.getName());
        }
        response.setMiddlewareName(asset.getMiddlewareName());
        response.setVersion(asset.getVersion());
        response.setPlatform(asset.getPlatform());
        response.setDescription(asset.getDescription());
        response.setReleasedAt(asset.getReleasedAt());
        response.setPublished(asset.isPublished());
        response.setDownloadToken(asset.getDownloadToken());
        response.setOriginalFileName(asset.getOriginalFileName());
        response.setContentType(asset.getContentType());
        response.setFileSize(asset.getFileSize());
        response.setDownloadCount(asset.getDownloadCount());
        response.setCreatedAt(asset.getCreatedAt());
        response.setUpdatedAt(asset.getUpdatedAt());
        response.setDownloadUrl("/files/" + asset.getDownloadToken());
        response.setStandardDocumentId(asset.getStandardDocumentId());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSoftwareTypeId() {
        return softwareTypeId;
    }

    public void setSoftwareTypeId(Long softwareTypeId) {
        this.softwareTypeId = softwareTypeId;
    }

    public String getSoftwareTypeCategory() {
        return softwareTypeCategory;
    }

    public void setSoftwareTypeCategory(String softwareTypeCategory) {
        this.softwareTypeCategory = softwareTypeCategory;
    }

    public String getSoftwareTypeName() {
        return softwareTypeName;
    }

    public void setSoftwareTypeName(String softwareTypeName) {
        this.softwareTypeName = softwareTypeName;
    }

    public String getMiddlewareName() {
        return middlewareName;
    }

    public void setMiddlewareName(String middlewareName) {
        this.middlewareName = middlewareName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDate releasedAt) {
        this.releasedAt = releasedAt;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public String getDownloadToken() {
        return downloadToken;
    }

    public void setDownloadToken(String downloadToken) {
        this.downloadToken = downloadToken;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(long downloadCount) {
        this.downloadCount = downloadCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Long getStandardDocumentId() { return standardDocumentId; }
    public void setStandardDocumentId(Long standardDocumentId) { this.standardDocumentId = standardDocumentId; }
    public String getStandardDocumentTitle() { return standardDocumentTitle; }
    public void setStandardDocumentTitle(String standardDocumentTitle) { this.standardDocumentTitle = standardDocumentTitle; }
}
