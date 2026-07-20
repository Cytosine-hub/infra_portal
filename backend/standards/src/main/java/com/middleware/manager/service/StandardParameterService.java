package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.ParameterStandardMapper;
import com.middleware.manager.repository.StandardDocumentMapper;
import com.middleware.manager.repository.StandardParameterMapper;
import com.middleware.manager.web.api.dto.StandardParameterRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class StandardParameterService {
    private final StandardParameterMapper standardParameterMapper;
    private final StandardDocumentMapper standardDocumentMapper;
    private final ParameterStandardMapper parameterStandardMapper;

    public StandardParameterService(StandardParameterMapper standardParameterMapper,
                                    StandardDocumentMapper standardDocumentMapper,
                                    ParameterStandardMapper parameterStandardMapper) {
        this.standardParameterMapper = standardParameterMapper;
        this.standardDocumentMapper = standardDocumentMapper;
        this.parameterStandardMapper = parameterStandardMapper;
    }

    public List<StandardParameter> list(String keyword, String category, Boolean active,
                                        Long standardDocumentId, Long parameterStandardId) {
        if (standardDocumentId == null && parameterStandardId == null) {
            return Collections.emptyList();
        }
        return standardParameterMapper.findWithFilter(standardDocumentId, parameterStandardId, keyword, category, active);
    }

    public List<StandardParameter> listActive() {
        return standardParameterMapper.findByActiveTrueOrderByParamTypeAscCodeAsc();
    }

    public List<StandardParameter> listActiveByStandardDocumentId(Long standardDocumentId) {
        if (standardDocumentId == null) {
            return Collections.emptyList();
        }
        return standardParameterMapper.findByStandardDocumentIdAndActiveTrueOrderByParamTypeAscCodeAsc(standardDocumentId);
    }

    public List<StandardParameter> listActiveByParameterStandardId(Long parameterStandardId) {
        if (parameterStandardId == null) {
            return Collections.emptyList();
        }
        return standardParameterMapper.findByParameterStandardIdAndActiveTrueOrderByParamTypeAscCodeAsc(parameterStandardId);
    }

    public StandardParameter get(Long id) {
        StandardParameter parameter = standardParameterMapper.findById(id);
        if (parameter == null) {
            throw new NotFoundException(ErrorCode.STANDARD_PARAMETER_NOT_FOUND, ErrorMessages.STANDARD_PARAMETER_NOT_FOUND);
        }
        return parameter;
    }

    @Transactional
    public StandardParameter create(StandardParameterRequest request) {
        Long standardDocumentId = request.getStandardDocumentId();
        Long parameterStandardId = request.getParameterStandardId();
        resolveBinding(standardDocumentId, parameterStandardId);

        String code = normalizeCode(request.getCode());
        if (standardDocumentId != null) {
            if (standardParameterMapper.existsByStandardDocumentIdAndCodeIgnoreCase(standardDocumentId, code)) {
                throw new BusinessException(ErrorCode.PARAMETER_CODE_DUPLICATE, ErrorMessages.PARAMETER_CODE_DUPLICATE);
            }
        } else {
            if (standardParameterMapper.existsByParameterStandardIdAndCodeIgnoreCase(parameterStandardId, code)) {
                throw new BusinessException(ErrorCode.PARAMETER_CODE_DUPLICATE, ErrorMessages.PARAMETER_CODE_DUPLICATE);
            }
        }

        StandardParameter parameter = new StandardParameter();
        apply(parameter, request, standardDocumentId, parameterStandardId, code);
        parameter.setCreatedAt(LocalDateTime.now());
        parameter.setUpdatedAt(LocalDateTime.now());
        standardParameterMapper.insert(parameter);
        clearRenderedContent(standardDocumentId, parameterStandardId);
        log.info("标准参数已创建 id={}", parameter.getId());
        return parameter;
    }

    @Transactional
    public StandardParameter update(Long id, StandardParameterRequest request) {
        StandardParameter parameter = get(id);
        Long standardDocumentId = request.getStandardDocumentId() != null
                ? request.getStandardDocumentId()
                : parameter.getStandardDocumentId();
        Long parameterStandardId = request.getParameterStandardId() != null
                ? request.getParameterStandardId()
                : parameter.getParameterStandardId();
        resolveBinding(standardDocumentId, parameterStandardId);

        String code = normalizeCode(request.getCode());
        if (standardDocumentId != null) {
            StandardParameter existing = standardParameterMapper.findByStandardDocumentIdAndCodeIgnoreCase(standardDocumentId, code);
            if (existing != null && !existing.getId().equals(id)) {
                throw new BusinessException(ErrorCode.PARAMETER_CODE_DUPLICATE, ErrorMessages.PARAMETER_CODE_DUPLICATE);
            }
        } else {
            StandardParameter existing = standardParameterMapper.findByParameterStandardIdAndCodeIgnoreCase(parameterStandardId, code);
            if (existing != null && !existing.getId().equals(id)) {
                throw new BusinessException(ErrorCode.PARAMETER_CODE_DUPLICATE, ErrorMessages.PARAMETER_CODE_DUPLICATE);
            }
        }

        apply(parameter, request, standardDocumentId, parameterStandardId, code);
        parameter.setUpdatedAt(LocalDateTime.now());
        standardParameterMapper.update(parameter);
        clearRenderedContent(standardDocumentId, parameterStandardId);
        log.info("标准参数已更新 id={}", id);
        return parameter;
    }

    @Transactional
    public void delete(Long id) {
        StandardParameter parameter = get(id);
        Long standardDocumentId = parameter.getStandardDocumentId();
        Long parameterStandardId = parameter.getParameterStandardId();
        standardParameterMapper.deleteById(id);
        clearRenderedContent(standardDocumentId, parameterStandardId);
        log.info("标准参数已删除 id={}", id);
    }

    private void clearRenderedContent(Long standardDocumentId, Long parameterStandardId) {
        if (standardDocumentId != null) {
            com.middleware.manager.domain.StandardDocument doc = standardDocumentMapper.findById(standardDocumentId);
            if (doc != null) {
                doc.setRenderedContent(null);
                doc.setUpdatedAt(LocalDateTime.now());
                standardDocumentMapper.update(doc);
            }
        }
        if (parameterStandardId != null) {
            com.middleware.manager.domain.ParameterStandard ps = parameterStandardMapper.findById(parameterStandardId);
            if (ps != null) {
                ps.setRenderedContent(null);
                ps.setUpdatedAt(LocalDateTime.now());
                parameterStandardMapper.update(ps);
            }
            // Clear renderedContent of all StandardDocuments referencing this ParameterStandard
            List<com.middleware.manager.domain.StandardDocument> relatedDocs =
                    standardDocumentMapper.findByRelatedStandardDocumentId(parameterStandardId);
            for (com.middleware.manager.domain.StandardDocument doc : relatedDocs) {
                if (doc.getRenderedContent() != null) {
                    doc.setRenderedContent(null);
                    doc.setUpdatedAt(LocalDateTime.now());
                    standardDocumentMapper.update(doc);
                }
            }
        }
    }

    private void apply(StandardParameter parameter, StandardParameterRequest request,
                       Long standardDocumentId, Long parameterStandardId, String code) {
        parameter.setStandardDocumentId(standardDocumentId);
        parameter.setParameterStandardId(parameterStandardId);
        parameter.setCode(code);
        parameter.setName(requireText(request.getName(), ErrorMessages.PARAMETER_NAME_REQUIRED));
        parameter.setValue(requireText(request.getValue(), ErrorMessages.PARAMETER_VALUE_REQUIRED));
        parameter.setParamType(requireText(request.getParamType(), ErrorMessages.PARAMETER_TYPE_REQUIRED));
        parameter.setValueRange(requireText(request.getValueRange(), ErrorMessages.PARAMETER_VALUE_RANGE_REQUIRED));
        parameter.setDescription(trimToNull(request.getDescription()));
        parameter.setActive(request.isActive());
        parameter.setDeploymentStandard(request.isDeploymentStandard());
    }

    private void resolveBinding(Long standardDocumentId, Long parameterStandardId) {
        if (standardDocumentId == null && parameterStandardId == null) {
            throw new BusinessException(ErrorCode.PARAMETER_BINDING_INVALID, ErrorMessages.PARAMETER_BINDING_INVALID);
        }
        if (standardDocumentId != null && parameterStandardId != null) {
            throw new BusinessException(ErrorCode.PARAMETER_BINDING_INVALID, ErrorMessages.PARAMETER_BINDING_ONLY_ONE);
        }
        if (standardDocumentId != null && standardDocumentMapper.findById(standardDocumentId) == null) {
            throw new NotFoundException(ErrorCode.DOCUMENT_NOT_FOUND, ErrorMessages.PARAMETER_BINDING_DOCUMENT_NOT_FOUND);
        }
        if (parameterStandardId != null && parameterStandardMapper.findById(parameterStandardId) == null) {
            throw new NotFoundException(ErrorCode.PARAMETER_STANDARD_NOT_FOUND, ErrorMessages.PARAMETER_BINDING_STANDARD_NOT_FOUND);
        }
    }

    private String normalizeCode(String code) {
        return requireText(code, ErrorMessages.PARAMETER_CODE_REQUIRED).trim().toUpperCase();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
