package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterStandard {
    private Long id;
    private String title;
    private String category;
    private String software;
    private Long softwareTypeId;
    private String softwareVersion;
    private String code;
    private String status;
    private String version;
    private String content;
    private String renderedContent;
    private String previousContent;
    private String previousRenderedContent;
    private LocalDateTime publishedAt;
    private Long pendingReviewRecordId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
