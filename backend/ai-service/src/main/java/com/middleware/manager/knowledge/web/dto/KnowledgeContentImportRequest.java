package com.middleware.manager.knowledge.web.dto;

import com.middleware.manager.constant.ErrorMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeContentImportRequest {

    @NotNull(message = ErrorMessages.PARAM_INVALID)
    @Positive(message = ErrorMessages.PARAM_INVALID)
    private Long sourceId;

    @NotBlank(message = ErrorMessages.PARAM_INVALID)
    @Size(max = 40, message = ErrorMessages.PARAM_INVALID)
    private String sourceType;
}
