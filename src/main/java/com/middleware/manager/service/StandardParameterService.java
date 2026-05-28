package com.middleware.manager.service;

import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.repository.ParameterStandardMapper;
import com.middleware.manager.repository.StandardDocumentMapper;
import com.middleware.manager.repository.StandardParameterMapper;
import com.middleware.manager.web.api.dto.StandardParameterRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
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
        return standardParameterMapper.findByActiveTrueOrderByCategoryAscCodeAsc();
    }

    public List<StandardParameter> listActiveByStandardDocumentId(Long standardDocumentId) {
        if (standardDocumentId == null) {
            return Collections.emptyList();
        }
        return standardParameterMapper.findByStandardDocumentIdAndActiveTrueOrderByCategoryAscCodeAsc(standardDocumentId);
    }

    public List<StandardParameter> listActiveByParameterStandardId(Long parameterStandardId) {
        if (parameterStandardId == null) {
            return Collections.emptyList();
        }
        return standardParameterMapper.findByParameterStandardIdAndActiveTrueOrderByCategoryAscCodeAsc(parameterStandardId);
    }

    public StandardParameter get(Long id) {
        StandardParameter parameter = standardParameterMapper.findById(id);
        if (parameter == null) {
            throw new IllegalArgumentException("标准参数不存在");
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
                throw new IllegalArgumentException("该标准下参数编码已存在");
            }
        } else {
            if (standardParameterMapper.existsByParameterStandardIdAndCodeIgnoreCase(parameterStandardId, code)) {
                throw new IllegalArgumentException("该标准下参数编码已存在");
            }
        }

        StandardParameter parameter = new StandardParameter();
        apply(parameter, request, standardDocumentId, parameterStandardId, code);
        parameter.setCreatedAt(LocalDateTime.now());
        parameter.setUpdatedAt(LocalDateTime.now());
        standardParameterMapper.insert(parameter);
        clearRenderedContent(standardDocumentId, parameterStandardId);
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
                throw new IllegalArgumentException("该标准下参数编码已存在");
            }
        } else {
            StandardParameter existing = standardParameterMapper.findByParameterStandardIdAndCodeIgnoreCase(parameterStandardId, code);
            if (existing != null && !existing.getId().equals(id)) {
                throw new IllegalArgumentException("该标准下参数编码已存在");
            }
        }

        apply(parameter, request, standardDocumentId, parameterStandardId, code);
        parameter.setUpdatedAt(LocalDateTime.now());
        standardParameterMapper.update(parameter);
        clearRenderedContent(standardDocumentId, parameterStandardId);
        return parameter;
    }

    @Transactional
    public void delete(Long id) {
        StandardParameter parameter = get(id);
        Long standardDocumentId = parameter.getStandardDocumentId();
        Long parameterStandardId = parameter.getParameterStandardId();
        standardParameterMapper.deleteById(id);
        clearRenderedContent(standardDocumentId, parameterStandardId);
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
        }
    }

    private void apply(StandardParameter parameter, StandardParameterRequest request,
                       Long standardDocumentId, Long parameterStandardId, String code) {
        parameter.setStandardDocumentId(standardDocumentId);
        parameter.setParameterStandardId(parameterStandardId);
        parameter.setCode(code);
        parameter.setName(requireText(request.getName(), "标准参数名称不能为空"));
        parameter.setValue(requireText(request.getValue(), "标准参数值不能为空"));
        parameter.setCategory(trimToNull(request.getCategory()));
        parameter.setDescription(trimToNull(request.getDescription()));
        parameter.setActive(request.isActive());
        parameter.setDeploymentStandard(request.isDeploymentStandard());
    }

    private void resolveBinding(Long standardDocumentId, Long parameterStandardId) {
        if (standardDocumentId == null && parameterStandardId == null) {
            throw new IllegalArgumentException("参数必须绑定标准");
        }
        if (standardDocumentId != null && parameterStandardId != null) {
            throw new IllegalArgumentException("参数只能绑定一种标准类型");
        }
        if (standardDocumentId != null && standardDocumentMapper.findById(standardDocumentId) == null) {
            throw new IllegalArgumentException("绑定的标准不存在");
        }
        if (parameterStandardId != null && parameterStandardMapper.findById(parameterStandardId) == null) {
            throw new IllegalArgumentException("绑定的参数标准不存在");
        }
    }

    private String normalizeCode(String code) {
        return requireText(code, "标准参数编码不能为空").trim().toUpperCase();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
