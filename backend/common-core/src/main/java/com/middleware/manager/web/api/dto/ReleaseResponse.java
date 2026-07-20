package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.domain.SoftwareType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
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
    private boolean standardPackage;
    private Long parameterStandardId;
    private String parameterStandardTitle;
    private String packageStatus;
    private String packageError;

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
        response.setStandardPackage(asset.isStandardPackage());
        response.setParameterStandardId(asset.getParameterStandardId());
        response.setPackageStatus(asset.getPackageStatus());
        response.setPackageError(asset.getPackageError());
        return response;
    }
}
