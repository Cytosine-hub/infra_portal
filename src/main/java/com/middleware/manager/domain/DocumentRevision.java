package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRevision {
    private Long id;
    private Long documentId;
    private String documentType;
    private String version;
    private String content;
    private String renderedContent;
    private String revisionComment;
    private String revisedBy;
    private LocalDateTime revisedAt;
    private String category;
    private String software;
}
