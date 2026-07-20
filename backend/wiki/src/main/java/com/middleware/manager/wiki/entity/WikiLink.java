package com.middleware.manager.wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiLink {
    private Long id;
    private Long fromPageId;
    private Long toPageId;
    private String linkType;
    private BigDecimal confidence;
    private String context;
    private LocalDateTime createdAt;
}
