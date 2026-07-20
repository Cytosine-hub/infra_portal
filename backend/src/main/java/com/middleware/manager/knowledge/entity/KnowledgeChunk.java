package com.middleware.manager.knowledge.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {
    private Long id;
    private String content;
    private String sourceTitle;
    private String sourceType;
    private Long sourceId;
    private String category;
    private String software;
    private Integer chunkIndex;
    private String vectorId;
    private LocalDateTime createdAt;
}
