package com.middleware.manager.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.domain.ReviewRecord;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.ParameterStandardMapper;
import com.middleware.manager.repository.ReleaseAssetMapper;
import com.middleware.manager.repository.ReviewRecordMapper;
import com.middleware.manager.repository.StandardDocumentMapper;
import com.middleware.manager.repository.StandardParameterMapper;
import com.middleware.manager.web.api.dto.ParameterStandardRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class ParameterStandardService {
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_MODIFYING = "MODIFYING";
    private static final String STATUS_PENDING = "PENDING";
    private static final String DOC_TYPE_PARAMETER_STANDARD = "PARAMETER_STANDARD";
    private static final String TITLE_SEPARATOR = " / ";

    private final ParameterStandardMapper parameterStandardMapper;
    private final StandardParameterMapper standardParameterMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final StandardDocumentMapper standardDocumentMapper;
    private final ReleaseAssetMapper releaseAssetMapper;
    private final SoftwareTypeLookup softwareTypeService;

    public ParameterStandardService(ParameterStandardMapper parameterStandardMapper,
                                    StandardParameterMapper standardParameterMapper,
                                    ReviewRecordMapper reviewRecordMapper,
                                    StandardDocumentMapper standardDocumentMapper,
                                    ReleaseAssetMapper releaseAssetMapper,
                                    SoftwareTypeLookup softwareTypeService) {
        this.parameterStandardMapper = parameterStandardMapper;
        this.standardParameterMapper = standardParameterMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.standardDocumentMapper = standardDocumentMapper;
        this.releaseAssetMapper = releaseAssetMapper;
        this.softwareTypeService = softwareTypeService;
    }

    public PageInfo<ParameterStandard> list(String keyword, String status, String category, int page, int size) {
        PageHelper.startPage(page + 1, size);
        List<ParameterStandard> list = parameterStandardMapper.findWithFilter(keyword, status, category);
        return new PageInfo<>(list);
    }

    public PageInfo<ParameterStandard> listPublished(int page, int size) {
        PageHelper.startPage(page + 1, size);
        List<ParameterStandard> list = parameterStandardMapper.findByStatusOrderByCreatedAtDesc(STATUS_PUBLISHED);
        return new PageInfo<>(list);
    }

    public List<ParameterStandard> listPublicStandards() {
        List<ParameterStandard> standards = parameterStandardMapper.findByStatusInOrderByPublishedAtDesc(
                Arrays.asList(STATUS_PUBLISHED, STATUS_MODIFYING));
        for (ParameterStandard ps : standards) {
            if (STATUS_MODIFYING.equals(ps.getStatus()) && ps.getPreviousContent() != null) {
                ps.setContent(ps.getPreviousContent());
                ps.setRenderedContent(null);
            }
        }
        return standards;
    }

    public ParameterStandard get(Long id) {
        ParameterStandard standard = parameterStandardMapper.findById(id);
        if (standard == null) {
            throw new NotFoundException(ErrorCode.PARAMETER_STANDARD_NOT_FOUND, ErrorMessages.PARAMETER_STANDARD_NOT_FOUND);
        }
        return standard;
    }

    @Transactional
    public ParameterStandard create(ParameterStandardRequest request) {
        ParameterStandard standard = new ParameterStandard();
        apply(standard, request);
        standard.setStatus(STATUS_DRAFT);
        standard.setVersion(VersionManager.firstDraftVersion());
        standard.setCreatedAt(LocalDateTime.now());
        standard.setUpdatedAt(LocalDateTime.now());
        parameterStandardMapper.insert(standard);
        refreshRenderedContent(standard);
        log.info("参数标准已创建 id={}", standard.getId());
        return standard;
    }

    @Transactional
    public ParameterStandard update(Long id, ParameterStandardRequest request) {
        ParameterStandard standard = get(id);
        if (!STATUS_DRAFT.equals(standard.getStatus()) && !STATUS_MODIFYING.equals(standard.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMETER_STANDARD_STATUS_CONFLICT, ErrorMessages.PARAMETER_STANDARD_STATUS_CONFLICT);
        }
        apply(standard, request);
        if (STATUS_DRAFT.equals(standard.getStatus())) {
            standard.setVersion(VersionManager.nextDraftVersion(standard.getVersion()));
        } else {
            standard.setVersion(VersionManager.nextModifyingVersion(standard.getVersion()));
        }
        standard.setUpdatedAt(LocalDateTime.now());
        parameterStandardMapper.update(standard);
        refreshRenderedContent(standard);
        log.info("参数标准已更新 id={}", id);
        return standard;
    }

    @Transactional
    public void delete(Long id) {
        ParameterStandard standard = get(id);
        if (STATUS_PUBLISHED.equals(standard.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMETER_STANDARD_PUBLISHED, ErrorMessages.PARAMETER_STANDARD_PUBLISHED);
        }
        // 检查是否有标准文档引用此参数标准
        List<StandardDocument> relatedDocs = standardDocumentMapper.findByRelatedStandardDocumentId(id);
        if (!relatedDocs.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_STANDARD_HAS_REFERENCES, ErrorMessages.PARAMETER_STANDARD_HAS_REFERENCES);
        }
        // 检查是否有发布资源引用此参数标准
        List<ReleaseAsset> relatedAssets = releaseAssetMapper.findByParameterStandardId(id);
        if (!relatedAssets.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_STANDARD_HAS_REFERENCES, ErrorMessages.PARAMETER_STANDARD_HAS_REFERENCES);
        }
        parameterStandardMapper.deleteById(id);
        log.info("参数标准已删除 id={}", id);
    }

    @Transactional
    public ParameterStandard startModify(Long id) {
        ParameterStandard standard = get(id);
        if (!STATUS_PUBLISHED.equals(standard.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMETER_STANDARD_STATUS_CONFLICT, "只有已发布的参数标准才能开始修改");
        }
        standard.setPreviousContent(standard.getContent());
        standard.setPreviousRenderedContent(buildReviewContent(standard));
        standard.setStatus(STATUS_MODIFYING);
        standard.setVersion(VersionManager.toModifyingVersion(standard.getVersion()));
        standard.setUpdatedAt(LocalDateTime.now());
        parameterStandardMapper.update(standard);
        log.info("参数标准开始修改 id={}", id);
        return standard;
    }

    @Transactional
    public ParameterStandard cancelModify(Long id) {
        ParameterStandard standard = get(id);
        if (!STATUS_MODIFYING.equals(standard.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMETER_STANDARD_STATUS_CONFLICT, "当前状态不可取消修改");
        }
        standard.setStatus(STATUS_PUBLISHED);
        standard.setVersion(restorePublishedVersion(standard.getVersion()));
        if (standard.getPreviousContent() != null) {
            standard.setContent(standard.getPreviousContent());
            standard.setRenderedContent(null);
        }
        standard.setPreviousRenderedContent(null);
        standard.setUpdatedAt(LocalDateTime.now());
        parameterStandardMapper.update(standard);
        refreshRenderedContent(standard);
        log.info("参数标准已取消修改 id={}", id);
        return standard;
    }

    @Transactional
    public ParameterStandard publish(Long id) {
        ParameterStandard standard = get(id);
        if (!STATUS_DRAFT.equals(standard.getStatus()) && !STATUS_MODIFYING.equals(standard.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMETER_STANDARD_STATUS_CONFLICT, "当前状态不可发布");
        }
        if (STATUS_DRAFT.equals(standard.getStatus())) {
            standard.setVersion(VersionManager.firstPublishVersion());
        } else {
            standard.setVersion(restorePublishedVersion(standard.getVersion()));
        }
        standard.setStatus(STATUS_PUBLISHED);
        standard.setPublishedAt(LocalDateTime.now());
        standard.setPreviousContent(null);
        standard.setPreviousRenderedContent(null);
        standard.setUpdatedAt(LocalDateTime.now());
        parameterStandardMapper.update(standard);
        log.info("参数标准已发布 id={}, version={}", id, standard.getVersion());
        return standard;
    }

    @Transactional
    public ParameterStandard submitForReview(Long id, String submitterUsername, String submitterDisplayName) {
        ParameterStandard standard = get(id);
        if (!STATUS_DRAFT.equals(standard.getStatus()) && !STATUS_MODIFYING.equals(standard.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMETER_STANDARD_STATUS_CONFLICT, "当前状态不可提交审核");
        }
        if (standard.getPendingReviewRecordId() != null) {
            throw new BusinessException(ErrorCode.PARAMETER_STANDARD_UNDER_REVIEW, ErrorMessages.PARAMETER_STANDARD_UNDER_REVIEW);
        }

        ReviewRecord record = new ReviewRecord();
        record.setDocumentId(standard.getId());
        record.setDocumentTitle(standard.getTitle());
        record.setDocumentType(DOC_TYPE_PARAMETER_STANDARD);
        record.setCategory(standard.getCategory());
        record.setSoftware(standard.getSoftware());
        record.setDocumentVersion(standard.getVersion());
        record.setSubmitterUsername(submitterUsername);
        record.setSubmitterDisplayName(submitterDisplayName);
        record.setStatus(STATUS_PENDING);
        record.setCurrentContent(buildReviewContent(standard));
        if (STATUS_MODIFYING.equals(standard.getStatus()) && standard.getPreviousRenderedContent() != null) {
            record.setPreviousContent(standard.getPreviousRenderedContent());
        }
        record.setSubmittedAt(LocalDateTime.now());
        reviewRecordMapper.insert(record);

        standard.setPendingReviewRecordId(record.getId());
        standard.setUpdatedAt(LocalDateTime.now());
        parameterStandardMapper.update(standard);
        log.info("参数标准已提交审核 id={}, reviewId={}", id, record.getId());
        return standard;
    }

    public ParameterStandard save(ParameterStandard standard) {
        standard.setUpdatedAt(LocalDateTime.now());
        if (standard.getId() == null) {
            standard.setCreatedAt(LocalDateTime.now());
            parameterStandardMapper.insert(standard);
        } else {
            parameterStandardMapper.update(standard);
        }
        return standard;
    }

    public String render(ParameterStandard standard) {
        if (standard.getRenderedContent() != null) {
            return standard.getRenderedContent();
        }
        String rendered = standard.getContent();
        for (StandardParameter parameter : standardParameterMapper
                .findByParameterStandardIdAndActiveTrueOrderByParamTypeAscCodeAsc(standard.getId())) {
            rendered = rendered.replace("{{" + parameter.getCode() + "}}", parameter.getValue());
        }
        standard.setRenderedContent(rendered);
        return rendered;
    }

    private String buildReviewContent(ParameterStandard standard) {
        StringBuilder sb = new StringBuilder();
        sb.append(render(standard));
        List<StandardParameter> params = standardParameterMapper
                .findByParameterStandardIdAndActiveTrueOrderByParamTypeAscCodeAsc(standard.getId());
        if (!params.isEmpty()) {
            sb.append("\n\n---\n## 标准参数\n\n");
            sb.append("| 参数编码 | 参数值 | 参数类型 | 取值范围 |\n|---|---|---|---|\n");
            for (StandardParameter p : params) {
                sb.append("| ").append(p.getCode())
                  .append(" | ").append(p.getValue())
                  .append(" | ").append(p.getParamType() != null ? p.getParamType() : "-")
                  .append(" | ").append(p.getValueRange() != null ? p.getValueRange() : "-")
                  .append(" |\n");
            }
        }
        return sb.toString();
    }

    @Transactional
    public void refreshRenderedContent(ParameterStandard standard) {
        String rendered = standard.getContent();
        for (StandardParameter parameter : standardParameterMapper
                .findByParameterStandardIdAndActiveTrueOrderByParamTypeAscCodeAsc(standard.getId())) {
            rendered = rendered.replace("{{" + parameter.getCode() + "}}", parameter.getValue());
        }
        standard.setRenderedContent(rendered);
        standard.setUpdatedAt(LocalDateTime.now());
        parameterStandardMapper.update(standard);
    }

    private void apply(ParameterStandard standard, ParameterStandardRequest request) {
        if (request.getSoftwareTypeId() != null) {
            com.middleware.manager.domain.SoftwareType softwareType = softwareTypeService.get(request.getSoftwareTypeId());
            standard.setSoftwareTypeId(softwareType.getId());
            standard.setCategory(softwareType.getCategory());
            standard.setSoftware(softwareType.getName());
        }
        standard.setSoftwareVersion(trimToNull(request.getSoftwareVersion()));
        standard.setTitle(java.util.stream.Stream.of(
                standard.getCategory(), standard.getSoftware(), standard.getSoftwareVersion())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(TITLE_SEPARATOR)));
        standard.setCode(trimToNull(request.getCode()));
        standard.setContent(requireText(request.getContent(), "标准内容不能为空"));
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return value.trim();
    }

    /** 从修改态版本号还原为已发布版本号：X.YZ → X.Y（去掉末尾的 Z） */
    private String restorePublishedVersion(String modifyingVersion) {
        if (modifyingVersion == null) return VersionManager.firstPublishVersion();
        int dotIdx = modifyingVersion.indexOf('.');
        if (dotIdx < 0) return VersionManager.firstPublishVersion();
        String yzPart = modifyingVersion.substring(dotIdx + 1);
        if (yzPart.length() <= 1) return modifyingVersion;
        return modifyingVersion.substring(0, dotIdx + 1) + yzPart.substring(0, yzPart.length() - 1);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
