package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRecord {
    private Long id;
    private Long documentId;
    private String documentTitle;
    private String documentType;
    private String category;
    private String software;
    private String documentVersion;
    private String submitterUsername;
    private String submitterDisplayName;
    private String status;
    private LocalDateTime submittedAt;
    private String reviewerUsername;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private String previousContent;
    private String currentContent;
}
