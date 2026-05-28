package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StandardDocument {

    private Long id;
    private String title;
    private String documentType = "MANUAL";
    private String status = "DRAFT";
    private String version;
    private Long pendingReviewRecordId;
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
    private String previousContent;
    private String reviewComment;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
