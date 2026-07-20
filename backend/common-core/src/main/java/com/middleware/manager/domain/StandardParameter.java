package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StandardParameter {
    private Long id;
    private Long standardDocumentId;
    private Long parameterStandardId;
    private String code;
    private String name;
    private String value;
    private String paramType;
    private String valueRange;
    private String description;
    private boolean active;
    private boolean deploymentStandard;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
