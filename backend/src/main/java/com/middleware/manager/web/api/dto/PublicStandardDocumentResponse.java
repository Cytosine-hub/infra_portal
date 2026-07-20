package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.StandardDocument;

import java.util.ArrayList;
import java.util.List;

public class PublicStandardDocumentResponse extends StandardDocumentResponse {
    private List<StandardDocumentResponse> relatedDocuments = new ArrayList<>();

    public static PublicStandardDocumentResponse from(StandardDocument document,
                                                      String renderedContent,
                                                      List<StandardDocumentResponse> relatedDocuments) {
        StandardDocumentResponse base = StandardDocumentResponse.from(document, renderedContent);
        PublicStandardDocumentResponse response = new PublicStandardDocumentResponse();
        response.setId(base.getId());
        response.setTitle(base.getTitle());
        response.setDocumentType(base.getDocumentType());
        response.setStatus(base.getStatus());
        response.setSummary(base.getSummary());
        response.setRelatedStandardDocumentId(base.getRelatedStandardDocumentId());
        response.setSoftwareTypeId(base.getSoftwareTypeId());
        response.setCategory(base.getCategory());
        response.setSoftware(base.getSoftware());
        response.setSoftwareVersion(base.getSoftwareVersion());
        response.setStandardVersion(base.getStandardVersion());
        response.setContent(base.getContent());
        response.setRenderedContent(base.getRenderedContent());
        response.setPublishedAt(base.getPublishedAt());
        response.setCreatedAt(base.getCreatedAt());
        response.setUpdatedAt(base.getUpdatedAt());
        response.setStoredFileName(base.getStoredFileName());
        response.setOriginalFileName(base.getOriginalFileName());
        response.setRelatedDocuments(relatedDocuments);
        return response;
    }

    public List<StandardDocumentResponse> getRelatedDocuments() {
        return relatedDocuments;
    }

    public void setRelatedDocuments(List<StandardDocumentResponse> relatedDocuments) {
        this.relatedDocuments = relatedDocuments;
    }
}
