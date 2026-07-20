package com.middleware.manager.wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiSource {
    private Long id;
    private String title;
    private String sourceType;
    private String filePath;
    private String contentHash;
    private String content;
    private String category;
    private String software;
    private Boolean ingested;
    private LocalDateTime ingestedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
}
