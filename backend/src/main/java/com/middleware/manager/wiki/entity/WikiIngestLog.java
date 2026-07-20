package com.middleware.manager.wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiIngestLog {
    private Long id;
    private Long sourceId;
    private Long operatorId;
    private Integer pagesCreated;
    private Integer pagesUpdated;
    private Integer linksCreated;
    private Integer contradictionsFound;
    private String llmModel;
    private Integer llmTokensUsed;
    private Integer durationMs;
    private String status;
    private String errorDetail;
    private LocalDateTime createdAt;
}
