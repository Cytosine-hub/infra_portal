package com.middleware.manager.wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LintResult {
    private Long id;
    private String lintType;  // ORPHAN, STALE, BROKEN_LINK, CONTRADICTION, GAP
    private Long pageId;
    private String fingerprint;
    private String description;
    private String severity;  // LOW, MEDIUM, HIGH
    private Boolean resolved;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime ignoredUntil;
    private LocalDateTime createdAt;
}
