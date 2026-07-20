package com.middleware.manager.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public class ReleaseForm {

    private Long softwareTypeId;

    @NotBlank(message = "中间件名称不能为空")
    @Size(max = 120, message = "中间件名称长度不能超过 120")
    private String middlewareName;

    @NotBlank(message = "版本号不能为空")
    @Size(max = 60, message = "版本号长度不能超过 60")
    private String version;

    @Size(max = 60, message = "平台字段长度不能超过 60")
    private String platform;

    @Size(max = 2000, message = "描述长度不能超过 2000")
    private String description;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate releasedAt;

    private boolean published;

    private Long standardDocumentId;

    private MultipartFile file;

    private boolean standardPackage;

    private Long parameterStandardId;

    public Long getSoftwareTypeId() {
        return softwareTypeId;
    }

    public void setSoftwareTypeId(Long softwareTypeId) {
        this.softwareTypeId = softwareTypeId;
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

    public Long getStandardDocumentId() {
        return standardDocumentId;
    }

    public void setStandardDocumentId(Long standardDocumentId) {
        this.standardDocumentId = standardDocumentId;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public boolean isStandardPackage() {
        return standardPackage;
    }

    public void setStandardPackage(boolean standardPackage) {
        this.standardPackage = standardPackage;
    }

    public Long getParameterStandardId() {
        return parameterStandardId;
    }

    public void setParameterStandardId(Long parameterStandardId) {
        this.parameterStandardId = parameterStandardId;
    }
}
