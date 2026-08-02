package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeImportSource {
    private Long sourceId;
    private String title;
    private String sourceType;
    private String content;
    private String category;
    private String software;
}
