package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseAsset {

    private Long id;
    private String middlewareName;
    private Long softwareTypeId;
    private String version;
    private String platform;
    private String description;
    private LocalDate releasedAt;
    private boolean published;
    private String downloadToken;
    private String originalFileName;
    private String storedFileName;
    private String contentType;
    private long fileSize;
    private long downloadCount;
    private Long standardDocumentId;
    private boolean standardPackage;
    private Long parameterStandardId;
    private String packageStatus;
    private String packageError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
