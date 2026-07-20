package com.middleware.manager.wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiPage {
    private Long id;
    private String title;
    private String pageType;
    private String category;
    private String software;
    private String version;
    private String canonicalTitle;
    private String aliasTitles;
    private String content;
    private String summary;
    private String sourceRefs;
    private String status;
    private String contradictionNote;
    private String compiledBy;
    private LocalDateTime compiledAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
