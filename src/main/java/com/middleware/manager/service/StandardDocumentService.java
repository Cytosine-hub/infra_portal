package com.middleware.manager.service;

import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.repository.StandardDocumentRepository;
import com.middleware.manager.web.api.dto.StandardDocumentRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StandardDocumentService {
    private final StandardDocumentRepository repository;
    private final StandardParameterService parameterService;
    private final SoftwareTypeService softwareTypeService;

    public StandardDocumentService(StandardDocumentRepository repository,
                                   StandardParameterService parameterService,
                                   SoftwareTypeService softwareTypeService) {
        this.repository = repository;
        this.parameterService = parameterService;
        this.softwareTypeService = softwareTypeService;
    }

    public List<StandardDocument> list(String keyword, String documentType, String status, String category) {
        return repository.findAll(specification(keyword, documentType, status, category));
    }

    public List<StandardDocument> listPublishedStandards() {
        return repository.findByDocumentTypeAndStatusOrderByPublishedAtDescUpdatedAtDesc("STANDARD", "PUBLISHED");
    }

    public List<StandardDocument> listAllPublished() {
        return repository.findByStatusOrderByCategoryAscPublishedAtDesc("PUBLISHED");
    }

    public List<StandardDocument> listPublishedRelatedDocuments(Long standardDocumentId) {
        return repository.findByRelatedStandardDocumentIdAndStatusOrderByPublishedAtDescUpdatedAtDesc(standardDocumentId, "PUBLISHED");
    }

    public StandardDocument get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Standard document does not exist"));
    }

    public StandardDocument getPublished(Long id) {
        StandardDocument document = get(id);
        if (!"PUBLISHED".equals(document.getStatus())) {
            throw new IllegalArgumentException("Standard document does not exist");
        }
        return document;
    }

    @Transactional
    public StandardDocument create(StandardDocumentRequest request) {
        StandardDocument document = new StandardDocument();
        apply(document, request);
        document.setStatus("DRAFT");
        StandardDocument saved = repository.save(document);
        refreshRenderedContent(saved);
        return saved;
    }

    @Transactional
    public StandardDocument update(Long id, StandardDocumentRequest request) {
        StandardDocument document = get(id);
        apply(document, request);
        StandardDocument saved = repository.save(document);
        refreshRenderedContent(saved);
        return saved;
    }

    @Transactional
    public StandardDocument publish(Long id) {
        StandardDocument document = get(id);
        if (!"STANDARD".equals(document.getDocumentType()) && document.getRelatedStandardDocumentId() != null) {
            StandardDocument parent = get(document.getRelatedStandardDocumentId());
            if (!"PUBLISHED".equals(parent.getStatus())) {
                throw new IllegalStateException("关联的标准尚未发布，请先发布标准后再发布本文档");
            }
        }
        document.setStatus("PUBLISHED");
        document.setPublishedAt(LocalDateTime.now());
        return repository.save(document);
    }

    @Transactional
    public StandardDocument unpublish(Long id) {
        StandardDocument document = get(id);
        document.setStatus("DRAFT");
        return repository.save(document);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(get(id));
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
        repository.save(document);
    }

    @Transactional
    public void refreshRenderedContentForStandard(Long standardDocumentId) {
        StandardDocument standard = get(standardDocumentId);
        refreshRenderedContent(standard);
    }

    private void apply(StandardDocument document, StandardDocumentRequest request) {
        document.setTitle(requireText(request.getTitle(), "Document title cannot be blank"));
        String documentType = normalizeDocumentType(request.getDocumentType());
        document.setDocumentType(documentType);
        document.setSummary(trimToNull(request.getSummary()));

        if ("STANDARD".equals(documentType)) {
            SoftwareType softwareType = resolveSoftwareType(request.getSoftwareTypeId());
            document.setRelatedStandardDocumentId(null);
            document.setSoftwareTypeId(softwareType.getId());
            document.setCategory(softwareType.getCategory());
            document.setSoftware(softwareType.getName());
            document.setSoftwareVersion(trimToNull(request.getSoftwareVersion()));
            document.setStandardVersion(trimToNull(request.getStandardVersion()));
        } else {
            StandardDocument relatedStandard = resolveRelatedStandard(request.getRelatedStandardDocumentId());
            document.setRelatedStandardDocumentId(relatedStandard.getId());
            document.setSoftwareTypeId(relatedStandard.getSoftwareTypeId());
            document.setCategory(relatedStandard.getCategory());
            document.setSoftware(relatedStandard.getSoftware());
            document.setSoftwareVersion(relatedStandard.getSoftwareVersion());
            document.setStandardVersion(relatedStandard.getStandardVersion());
        }

        document.setContent(requireText(request.getContent(), "Document content cannot be blank"));
        document.setCode(trimToNull(request.getCode()));
    }

    private Specification<StandardDocument> specification(String keyword, String documentType, String status, String category) {
        Specification<StandardDocument> specification = Specification.where(null);
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("category"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("software"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("softwareVersion"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("standardVersion"), "")), pattern),
                    cb.like(cb.lower(root.get("content")), pattern)
            ));
        }
        if (StringUtils.hasText(documentType)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("documentType"), documentType.trim()));
        }
        if (StringUtils.hasText(status)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status.trim()));
        }
        if (StringUtils.hasText(category)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("category"), category.trim()));
        }
        return specification;
    }

    private String normalizeDocumentType(String documentType) {
        String value = StringUtils.hasText(documentType) ? documentType.trim().toUpperCase() : "MANUAL";
        if (!"MANUAL".equals(value) && !"ARTICLE".equals(value) && !"STANDARD".equals(value)) {
            throw new IllegalArgumentException("Document type must be MANUAL, ARTICLE, or STANDARD");
        }
        return value;
    }

    private SoftwareType resolveSoftwareType(Long softwareTypeId) {
        if (softwareTypeId == null) {
            throw new IllegalArgumentException("Standard must select a software type");
        }
        return softwareTypeService.get(softwareTypeId);
    }

    private Long resolveParameterStandardId(StandardDocument document) {
        if ("STANDARD".equals(document.getDocumentType())) {
            return document.getId();
        }
        return document.getRelatedStandardDocumentId();
    }

    private StandardDocument resolveRelatedStandard(Long standardDocumentId) {
        if (standardDocumentId == null) {
            throw new IllegalArgumentException("Document must be linked to a standard");
        }
        StandardDocument standard = get(standardDocumentId);
        if (!"STANDARD".equals(standard.getDocumentType())) {
            throw new IllegalArgumentException("Linked document must be a standard");
        }
        return standard;
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
