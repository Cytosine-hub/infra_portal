package com.middleware.manager.wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestTask {
    private Long id;
    private Long sourceId;
    private String fileName;
    private String status;      // PENDING, PROCESSING, COMPLETED, FAILED
    private Integer progress;   // 0-100
    private String step;
    private Integer totalChunks;
    private Integer completedChunks;
    private Integer pagesCreated;
    private Integer pagesUpdated;
    private String errorMessage;
    private String qualityReport;
    private String sectionFacts;
    private String pagePlan;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
