package com.middleware.manager.web.api;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.service.ParameterStandardService;
import com.middleware.manager.service.StandardDocumentService;
import com.middleware.manager.web.api.dto.ParameterStandardResponse;
import com.middleware.manager.web.api.dto.StandardDocumentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/parameter-standards")
public class PublicParameterStandardController {
    private final ParameterStandardService service;
    private final StandardDocumentService documentService;

    public PublicParameterStandardController(ParameterStandardService service, StandardDocumentService documentService) {
        this.service = service;
        this.documentService = documentService;
    }

    @GetMapping
    public List<ParameterStandardResponse> list() {
        return service.listPublicStandards().stream().map(doc -> {
            ParameterStandardResponse resp = ParameterStandardResponse.from(doc, service.render(doc));
            List<StandardDocumentResponse> relatedDocs = documentService.listPublishedRelatedDocuments(doc.getId())
                    .stream()
                    .map(rd -> StandardDocumentResponse.from(rd, null))
                    .collect(Collectors.toList());
            resp.setRelatedDocuments(relatedDocs);
            return resp;
        }).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ParameterStandardResponse detail(@PathVariable Long id) {
        ParameterStandard standard = service.get(id);
        if (!"PUBLISHED".equals(standard.getStatus()) && !"MODIFYING".equals(standard.getStatus())) {
            throw new IllegalArgumentException("参数标准不存在或未发布");
        }
        if ("MODIFYING".equals(standard.getStatus()) && standard.getPreviousContent() != null) {
            standard.setContent(standard.getPreviousContent());
            standard.setRenderedContent(null);
        }
        ParameterStandardResponse resp = ParameterStandardResponse.from(standard, service.render(standard));
        List<StandardDocumentResponse> relatedDocs = documentService
                .listPublishedRelatedDocuments(standard.getId()).stream()
                .map(rd -> StandardDocumentResponse.from(rd, null))
                .collect(Collectors.toList());
        resp.setRelatedDocuments(relatedDocs);
        return resp;
    }
}
