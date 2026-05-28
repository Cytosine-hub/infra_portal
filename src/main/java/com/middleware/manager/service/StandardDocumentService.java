package com.middleware.manager.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.ReviewRecord;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.repository.ParameterStandardMapper;
import com.middleware.manager.repository.ReviewRecordMapper;
import com.middleware.manager.repository.StandardDocumentMapper;
import com.middleware.manager.web.api.dto.StandardDocumentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class StandardDocumentService {
    private final StandardDocumentMapper standardDocumentMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final StandardParameterService parameterService;
    private final SoftwareTypeService softwareTypeService;
    private final ParameterStandardMapper parameterStandardMapper;

    public StandardDocumentService(StandardDocumentMapper standardDocumentMapper,
                                   ReviewRecordMapper reviewRecordMapper,
                                   StandardParameterService parameterService,
                                   SoftwareTypeService softwareTypeService,
                                   ParameterStandardMapper parameterStandardMapper) {
        this.standardDocumentMapper = standardDocumentMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.parameterService = parameterService;
        this.softwareTypeService = softwareTypeService;
        this.parameterStandardMapper = parameterStandardMapper;
    }

    public List<StandardDocument> list(String keyword, String documentType, String status, String category) {
        return standardDocumentMapper.findWithFilter(keyword, documentType, status, category);
    }

    public List<StandardDocument> listPublishedStandards() {
        return standardDocumentMapper.findByDocumentTypeAndStatusOrderByPublishedAtDescUpdatedAtDesc("STANDARD", "PUBLISHED");
    }

    public List<StandardDocument> listAllPublished() {
        return standardDocumentMapper.findByStatusOrderByCategoryAscPublishedAtDesc("PUBLISHED");
    }

    public List<StandardDocument> listAllPublic() {
        List<StandardDocument> docs = standardDocumentMapper.findByStatusInOrderByUpdatedAtDesc(
                java.util.Arrays.asList("PUBLISHED", "MODIFYING"));
        for (StandardDocument doc : docs) {
            if ("MODIFYING".equals(doc.getStatus()) && doc.getPreviousContent() != null) {
                doc.setContent(doc.getPreviousContent());
                doc.setRenderedContent(null);
            }
        }
        return docs;
    }

    public List<StandardDocument> listPublishedRelatedDocuments(Long standardDocumentId) {
        return standardDocumentMapper.findByRelatedStandardDocumentIdAndStatusOrderByPublishedAtDescUpdatedAtDesc(standardDocumentId, "PUBLISHED");
    }

    public StandardDocument get(Long id) {
        StandardDocument document = standardDocumentMapper.findById(id);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        return document;
    }

    public StandardDocument getPublished(Long id) {
        StandardDocument document = get(id);
        if (!"PUBLISHED".equals(document.getStatus())) {
            throw new IllegalArgumentException("文档不存在或未发布");
        }
        return document;
    }

    @Transactional
    public StandardDocument save(StandardDocument document) {
        document.setUpdatedAt(LocalDateTime.now());
        if (document.getId() == null) {
            document.setCreatedAt(LocalDateTime.now());
            standardDocumentMapper.insert(document);
        } else {
            standardDocumentMapper.update(document);
        }
        return document;
    }

    @Transactional
    public StandardDocument create(StandardDocumentRequest request) {
        StandardDocument document = new StandardDocument();
        apply(document, request);
        document.setStatus("DRAFT");
        document.setVersion(VersionManager.firstDraftVersion());
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        standardDocumentMapper.insert(document);
        refreshRenderedContent(document);
        return document;
    }

    @Transactional
    public StandardDocument update(Long id, StandardDocumentRequest request) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档正在审核中，不可编辑");
        }
        String status = document.getStatus();
        if (!"DRAFT".equals(status) && !"MODIFYING".equals(status)) {
            throw new IllegalStateException("当前状态不可编辑");
        }
        apply(document, request);
        if ("DRAFT".equals(status)) {
            document.setVersion(VersionManager.nextDraftVersion(document.getVersion()));
        } else {
            document.setVersion(VersionManager.nextModifyingVersion(document.getVersion()));
        }
        document.setUpdatedAt(LocalDateTime.now());
        standardDocumentMapper.update(document);
        refreshRenderedContent(document);
        return document;
    }

    @Transactional
    public ReviewRecord submitForReview(Long id, String submitter, String submitterDisplayName) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档已有待审核记录");
        }
        String status = document.getStatus();
        if (!"DRAFT".equals(status) && !"MODIFYING".equals(status)) {
            throw new IllegalStateException("只有草稿或修改中的文档可以提交审核");
        }

        // 创建审核记录
        ReviewRecord record = new ReviewRecord();
        record.setDocumentId(document.getId());
        record.setDocumentTitle(document.getTitle());
        record.setDocumentType(document.getDocumentType());
        record.setCategory(document.getCategory());
        record.setSoftware(document.getSoftware());
        record.setDocumentVersion(document.getVersion());
        record.setSubmitterUsername(submitter);
        record.setSubmitterDisplayName(submitterDisplayName);
        record.setStatus("PENDING");
        record.setSubmittedAt(LocalDateTime.now());
        record.setPreviousContent(document.getPreviousContent());
        record.setCurrentContent(document.getContent());
        reviewRecordMapper.insert(record);

        // 文档关联审核记录
        document.setPendingReviewRecordId(record.getId());
        document.setSubmittedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        standardDocumentMapper.update(document);

        return record;
    }

    @Transactional
    public StandardDocument startModify(Long id) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档正在审核中，不可操作");
        }
        if (!"PUBLISHED".equals(document.getStatus())) {
            throw new IllegalStateException("只有已发布的文档可以开始修改");
        }
        document.setPreviousContent(document.getContent());
        document.setStatus("MODIFYING");
        document.setVersion(VersionManager.toModifyingVersion(document.getVersion()));
        document.setSubmittedAt(null);
        document.setReviewedAt(null);
        document.setReviewedBy(null);
        document.setReviewComment(null);
        document.setUpdatedAt(LocalDateTime.now());
        standardDocumentMapper.update(document);
        return document;
    }

    @Transactional
    public StandardDocument cancelModify(Long id) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档正在审核中，不可操作");
        }
        if (!"MODIFYING".equals(document.getStatus())) {
            throw new IllegalStateException("只有修改中的文档可以取消修改");
        }
        document.setStatus("PUBLISHED");
        document.setVersion(restorePublishedVersion(document.getVersion()));
        if (document.getPreviousContent() != null) {
            document.setContent(document.getPreviousContent());
            document.setRenderedContent(null);
        }
        document.setSubmittedAt(null);
        document.setReviewedAt(null);
        document.setReviewedBy(null);
        document.setReviewComment(null);
        document.setUpdatedAt(LocalDateTime.now());
        standardDocumentMapper.update(document);
        refreshRenderedContent(document);
        return document;
    }

    @Transactional
    public void delete(Long id) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档正在审核中，不可删除");
        }
        if ("PUBLISHED".equals(document.getStatus())) {
            throw new IllegalStateException("已发布的文档不能删除，请先开始修改后再删除");
        }
        standardDocumentMapper.deleteById(id);
    }

    public String render(StandardDocument document) {
        if (document.getRenderedContent() != null) {
            return document.getRenderedContent();
        }
        String rendered = document.getContent();
        Long parameterStandardId = resolveParameterStandardId(document);
        for (StandardParameter parameter : parameterService.listActiveByStandardDocumentId(parameterStandardId)) {
            rendered = rendered.replace("{{" + parameter.getCode() + "}}", parameter.getValue());
        }
        document.setRenderedContent(rendered);
        return rendered;
    }

    @Transactional
    public void refreshRenderedContent(StandardDocument document) {
        String rendered = document.getContent();
        Long parameterStandardId = resolveParameterStandardId(document);
        for (StandardParameter parameter : parameterService.listActiveByStandardDocumentId(parameterStandardId)) {
            rendered = rendered.replace("{{" + parameter.getCode() + "}}", parameter.getValue());
        }
        document.setRenderedContent(rendered);
        document.setUpdatedAt(LocalDateTime.now());
        standardDocumentMapper.update(document);
    }

    @Transactional
    public void refreshRenderedContentForStandard(Long standardDocumentId) {
        StandardDocument standard = get(standardDocumentId);
        refreshRenderedContent(standard);
    }

    private void apply(StandardDocument document, StandardDocumentRequest request) {
        document.setTitle(requireText(request.getTitle(), "文档标题不能为空"));
        String documentType = normalizeDocumentType(request.getDocumentType());
        document.setDocumentType(documentType);
        document.setSummary(trimToNull(request.getSummary()));

        if ("STANDARD".equals(documentType)) {
            com.middleware.manager.domain.SoftwareType softwareType = resolveSoftwareType(request.getSoftwareTypeId());
            document.setRelatedStandardDocumentId(null);
            document.setSoftwareTypeId(softwareType.getId());
            document.setCategory(softwareType.getCategory());
            document.setSoftware(softwareType.getName());
            document.setSoftwareVersion(trimToNull(request.getSoftwareVersion()));
            document.setStandardVersion(trimToNull(request.getStandardVersion()));
        } else {
            Long relatedId = request.getRelatedStandardDocumentId();
            if (relatedId == null) {
                throw new IllegalArgumentException("文档必须关联标准");
            }
            ParameterStandard ps = parameterStandardMapper.findById(relatedId);
            if (ps == null) {
                throw new IllegalArgumentException("关联的参数标准不存在");
            }
            document.setRelatedStandardDocumentId(relatedId);
            document.setSoftwareTypeId(ps.getSoftwareTypeId());
            document.setCategory(ps.getCategory());
            document.setSoftware(ps.getSoftware());
            document.setSoftwareVersion(ps.getSoftwareVersion());
        }

        document.setContent(requireText(request.getContent(), "文档内容不能为空"));
        document.setCode(trimToNull(request.getCode()));
    }

    private String normalizeDocumentType(String documentType) {
        String value = StringUtils.hasText(documentType) ? documentType.trim().toUpperCase() : "MANUAL";
        if (!"MANUAL".equals(value) && !"ARTICLE".equals(value)) {
            throw new IllegalArgumentException("文档类型必须是 MANUAL 或 ARTICLE");
        }
        return value;
    }

    private com.middleware.manager.domain.SoftwareType resolveSoftwareType(Long softwareTypeId) {
        if (softwareTypeId == null) {
            throw new IllegalArgumentException("标准必须选择软件类型");
        }
        return softwareTypeService.get(softwareTypeId);
    }

    private Long resolveParameterStandardId(StandardDocument document) {
        if ("STANDARD".equals(document.getDocumentType())) {
            return document.getId();
        }
        return document.getRelatedStandardDocumentId();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /** 从修改态版本号还原为已发布版本号：X.YZ → X.Y（去掉末尾的 Z） */
    private String restorePublishedVersion(String modifyingVersion) {
        if (modifyingVersion == null) return VersionManager.firstPublishVersion();
        int dotIdx = modifyingVersion.indexOf('.');
        if (dotIdx < 0) return VersionManager.firstPublishVersion();
        String yzPart = modifyingVersion.substring(dotIdx + 1);
        if (yzPart.length() <= 1) return modifyingVersion; // 已经是 X.Y 格式
        return modifyingVersion.substring(0, dotIdx + 1) + yzPart.substring(0, yzPart.length() - 1);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
