package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.DocumentRevision;
import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.ReviewRecord;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.DocumentRevisionMapper;
import com.middleware.manager.repository.ReviewRecordMapper;
import com.middleware.manager.repository.StandardParameterMapper;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.web.api.dto.ReviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReviewService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String DOC_TYPE_PARAMETER_STANDARD = "PARAMETER_STANDARD";
    private static final int MAX_DIFF_PREVIEW_LENGTH = 2000;

    private final ReviewRecordMapper mapper;
    private final StandardDocumentService documentService;
    private final ParameterStandardService parameterStandardService;
    private final PermissionService permissionService;
    private final DocumentRevisionMapper revisionMapper;
    private final StandardParameterMapper parameterMapper;
    private final StandardPackageOperations standardPackageService;

    public ReviewService(ReviewRecordMapper mapper,
                         StandardDocumentService documentService,
                         ParameterStandardService parameterStandardService,
                         PermissionService permissionService,
                         DocumentRevisionMapper revisionMapper,
                         StandardParameterMapper parameterMapper,
                         StandardPackageOperations standardPackageService) {
        this.mapper = mapper;
        this.documentService = documentService;
        this.parameterStandardService = parameterStandardService;
        this.permissionService = permissionService;
        this.revisionMapper = revisionMapper;
        this.parameterMapper = parameterMapper;
        this.standardPackageService = standardPackageService;
    }

    public List<ReviewResponse> listReviews(Authentication authentication) {
        List<ReviewRecord> records;
        if (permissionService.isAdmin(authentication)) {
            records = mapper.findAllByOrderBySubmittedAtDesc();
        } else if (permissionService.isCategoryAdmin(authentication)) {
            String category = permissionService.getManagedCategory(authentication);
            records = mapper.findByCategoryOrderBySubmittedAtDesc(category);
        } else {
            records = mapper.findBySubmitterUsernameOrderBySubmittedAtDesc(authentication.getName());
        }
        return records.stream().map(ReviewResponse::from).collect(Collectors.toList());
    }

    public ReviewResponse getReviewDetail(Long id) {
        ReviewRecord record = getRecord(id);
        ReviewResponse response = ReviewResponse.from(record);

        if (DOC_TYPE_PARAMETER_STANDARD.equals(record.getDocumentType())) {
            // 参数标准审核：填充结构化元数据和参数列表
            ParameterStandard ps = parameterStandardService.get(record.getDocumentId());
            response.setMetadata(buildMetadataJson(ps));

            List<StandardParameter> currentParams = parameterMapper
                    .findByParameterStandardIdAndActiveTrueOrderByParamTypeAscCodeAsc(ps.getId());
            response.setCurrentParameters(serializeParameterList(currentParams));
            response.setPreviousParameters(parseParametersFromContent(record.getPreviousContent()));
        } else {
            // 文档审核：保持现有 diff 逻辑
            response.setDiff(computeDiff(record));
            // 标准文档审核：填充 storedFileName（供前端判断是否为 Word 文档）
            try {
                StandardDocument doc = documentService.get(record.getDocumentId());
                response.setStoredFileName(doc.getStoredFileName());
            } catch (NotFoundException e) {
                log.debug("获取文档 storedFileName 失败 documentId={}", record.getDocumentId(), e);
            }
        }
        return response;
    }

    @Transactional
    public ReviewResponse approve(Long id, String reviewer, String comment) {
        ReviewRecord record = getRecord(id);
        if (!STATUS_PENDING.equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.REVIEW_STATUS_CONFLICT, ErrorMessages.REVIEW_STATUS_CONFLICT);
        }
        record.setStatus(STATUS_APPROVED);
        record.setReviewerUsername(reviewer);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewComment(comment);

        String publishedVersion;
        String content;
        String renderedContent;
        if (DOC_TYPE_PARAMETER_STANDARD.equals(record.getDocumentType())) {
            ParameterStandard ps = parameterStandardService.get(record.getDocumentId());
            ps.setPendingReviewRecordId(null);
            ps.setStatus(STATUS_PUBLISHED);
            ps.setPublishedAt(LocalDateTime.now());
            if (ps.getPreviousContent() == null) {
                ps.setVersion(VersionManager.firstPublishVersion());
            } else {
                ps.setVersion(VersionManager.toPublishedVersion(ps.getVersion()));
            }
            publishedVersion = ps.getVersion();
            // 序列化参数列表
            List<StandardParameter> params = parameterMapper.findByParameterStandardIdAndActiveTrueOrderByParamTypeAscCodeAsc(ps.getId());
            content = serializeParameters(ps, params);
            renderedContent = ps.getRenderedContent();
            ps.setPreviousContent(null);
            parameterStandardService.save(ps);
            // 重新生成关联的标准包
            standardPackageService.regenerateByParameterStandard(ps.getId());
        } else {
            StandardDocument doc = documentService.get(record.getDocumentId());
            doc.setPendingReviewRecordId(null);
            if (doc.getPreviousContent() == null) {
                doc.setVersion(VersionManager.firstPublishVersion());
            } else {
                doc.setVersion(VersionManager.toPublishedVersion(doc.getVersion()));
            }
            publishedVersion = doc.getVersion();
            content = doc.getContent();
            renderedContent = doc.getRenderedContent();
            doc.setStatus(STATUS_PUBLISHED);
            doc.setPublishedAt(LocalDateTime.now());
            doc.setPreviousContent(record.getCurrentContent());
            doc.setReviewedAt(LocalDateTime.now());
            doc.setReviewedBy(reviewer);
            doc.setReviewComment(comment);
            documentService.save(doc);
        }

        // 创建修订记录
        DocumentRevision revision = new DocumentRevision();
        revision.setDocumentId(record.getDocumentId());
        revision.setDocumentType(record.getDocumentType());
        revision.setVersion(publishedVersion);
        revision.setContent(content);
        revision.setRenderedContent(renderedContent);
        revision.setRevisionComment(comment);
        revision.setRevisedBy(reviewer);
        revision.setSubmittedBy(record.getSubmitterUsername());
        revision.setRevisedAt(LocalDateTime.now());
        revision.setCategory(record.getCategory());
        revision.setSoftware(record.getSoftware());
        revisionMapper.insert(revision);

        mapper.update(record);
        log.info("审核通过 reviewId={}, reviewer={}", id, reviewer);
        return ReviewResponse.from(record);
    }

    @Transactional
    public ReviewResponse reject(Long id, String reviewer, String comment) {
        ReviewRecord record = getRecord(id);
        if (!STATUS_PENDING.equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.REVIEW_STATUS_CONFLICT, ErrorMessages.REVIEW_STATUS_CONFLICT);
        }
        record.setStatus(STATUS_REJECTED);
        record.setReviewerUsername(reviewer);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewComment(comment);

        if (DOC_TYPE_PARAMETER_STANDARD.equals(record.getDocumentType())) {
            ParameterStandard ps = parameterStandardService.get(record.getDocumentId());
            ps.setPendingReviewRecordId(null);
            ps.setVersion(VersionManager.nextModifyingVersion(ps.getVersion()));
            parameterStandardService.save(ps);
        } else {
            StandardDocument doc = documentService.get(record.getDocumentId());
            doc.setPendingReviewRecordId(null);
            doc.setVersion(VersionManager.nextModifyingVersion(doc.getVersion()));
            doc.setReviewedAt(LocalDateTime.now());
            doc.setReviewedBy(reviewer);
            doc.setReviewComment(comment);
            documentService.save(doc);
        }

        mapper.update(record);
        log.info("审核驳回 reviewId={}, reviewer={}", id, reviewer);
        return ReviewResponse.from(record);
    }

    public String computeDiff(ReviewRecord record) {
        String current = record.getCurrentContent();
        String previous = record.getPreviousContent();
        if (previous == null) {
            return "（首次提交，无历史版本对比）\n\n" + truncate(current, MAX_DIFF_PREVIEW_LENGTH);
        }
        return computeLineDiff(previous, current);
    }

    private ReviewRecord getRecord(Long id) {
        ReviewRecord record = mapper.findById(id);
        if (record == null) {
            throw new NotFoundException(ErrorCode.REVIEW_NOT_FOUND, ErrorMessages.REVIEW_NOT_FOUND);
        }
        return record;
    }

    private String computeLineDiff(String oldText, String newText) {
        List<String> oldLines = Arrays.asList(oldText.split("\\n", -1));
        List<String> newLines = Arrays.asList(newText.split("\\n", -1));
        StringBuilder sb = new StringBuilder();
        int maxLen = Math.max(oldLines.size(), newLines.size());
        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < oldLines.size() ? oldLines.get(i) : null;
            String newLine = i < newLines.size() ? newLines.get(i) : null;
            if (oldLine == null) {
                sb.append("+ ").append(newLine).append("\n");
            } else if (newLine == null) {
                sb.append("- ").append(oldLine).append("\n");
            } else if (!oldLine.equals(newLine)) {
                sb.append("- ").append(oldLine).append("\n");
                sb.append("+ ").append(newLine).append("\n");
            } else {
                sb.append("  ").append(oldLine).append("\n");
            }
        }
        return sb.toString();
    }

    private String serializeParameters(ParameterStandard ps, List<StandardParameter> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(ps.getTitle()).append("\n");
        sb.append("分类：").append(ps.getCategory() != null ? ps.getCategory() : "-").append("\n");
        sb.append("软件：").append(ps.getSoftware() != null ? ps.getSoftware() : "-").append("\n");
        sb.append("软件版本：").append(ps.getSoftwareVersion() != null ? ps.getSoftwareVersion() : "-").append("\n");
        sb.append("\n## 参数列表\n\n");
        if (params.isEmpty()) {
            sb.append("（暂无参数）\n");
        } else {
            for (StandardParameter p : params) {
                sb.append("- **").append(p.getCode()).append("**");
                sb.append(" = ").append(p.getValue());
                if (p.getName() != null && !p.getName().equals(p.getCode())) {
                    sb.append(" （").append(p.getName()).append("）");
                }
                if (p.getParamType() != null) {
                    sb.append(" [").append(p.getParamType()).append("]");
                }
                if (p.getValueRange() != null) {
                    sb.append(" {").append(p.getValueRange()).append("}");
                }
                if (p.isDeploymentStandard()) {
                    sb.append(" *部署标准*");
                }
                sb.append("\n");
                if (p.getDescription() != null && !p.getDescription().isEmpty()) {
                    sb.append("  说明：").append(p.getDescription()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String buildMetadataJson(ParameterStandard ps) {
        return String.format("{\"category\":\"%s\",\"software\":\"%s\",\"softwareVersion\":\"%s\",\"code\":\"%s\"}",
                escapeJson(ps.getCategory()),
                escapeJson(ps.getSoftware()),
                escapeJson(ps.getSoftwareVersion()),
                escapeJson(ps.getCode()));
    }

    private List<Map<String, Object>> serializeParameterList(List<StandardParameter> params) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (StandardParameter p : params) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("code", p.getCode());
            map.put("name", p.getName());
            map.put("value", p.getValue());
            map.put("paramType", p.getParamType());
            map.put("valueRange", p.getValueRange());
            map.put("description", p.getDescription());
            map.put("deploymentStandard", p.isDeploymentStandard());
            map.put("active", p.isActive());
            list.add(map);
        }
        return list;
    }

    /** 从 previousContent 的 Markdown 表格中解析参数列表 */
    private List<Map<String, Object>> parseParametersFromContent(String content) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (content == null) return list;
        String[] lines = content.split("\\n");
        boolean inTable = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|")) {
                if (inTable) break;
                continue;
            }
            // 跳过表头行和分隔行
            if (trimmed.contains("---")) continue;
            if (trimmed.contains("参数编码")) { inTable = true; continue; }
            if (!inTable) continue;

            String[] cells = trimmed.split("\\|");
            // 格式: | code | value | paramType | valueRange |
            if (cells.length >= 5) {
                Map<String, Object> map = new HashMap<>();
                map.put("code", cells[1].trim());
                map.put("value", cells[2].trim());
                map.put("paramType", cells[3].trim());
                map.put("valueRange", cells[4].trim());
                list.add(map);
            }
        }
        return list;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n...";
    }
}
