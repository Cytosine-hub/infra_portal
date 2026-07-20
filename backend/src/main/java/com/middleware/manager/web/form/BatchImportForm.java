package com.middleware.manager.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BatchImportForm {

    @NotBlank(message = "导入目录不能为空")
    @Size(max = 500, message = "导入目录长度不能超过 500")
    private String sourceDirectory;

    private Long softwareTypeId;

    @Size(max = 120, message = "中间件名称长度不能超过 120")
    private String middlewareName;

    @Size(max = 60, message = "平台长度不能超过 60")
    private String platform;

    @Size(max = 2000, message = "描述长度不能超过 2000")
    private String description;

    private boolean published;

    private boolean recursive = true;

    public String getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(String sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

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

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
}
