package com.middleware.manager.knowledge.loader;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.constant.ErrorMessages;

import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.repository.StandardDocumentMapper;
import org.springframework.stereotype.Component;

@Component
public class StandardDocumentLoader {

    private final StandardDocumentMapper standardDocumentMapper;

    public StandardDocumentLoader(StandardDocumentMapper standardDocumentMapper) {
        this.standardDocumentMapper = standardDocumentMapper;
    }

    public String loadFromStandardDocument(Long docId) {
        StandardDocument doc = standardDocumentMapper.findById(docId);
        if (doc == null) {
            throw new com.middleware.manager.exception.NotFoundException(ErrorCode.DOCUMENT_NOT_FOUND, ErrorMessages.DOCUMENT_NOT_FOUND);
        }
        return doc.getContent();
    }

    public StandardDocument getStandardDocument(Long docId) {
        StandardDocument doc = standardDocumentMapper.findById(docId);
        if (doc == null) {
            throw new com.middleware.manager.exception.NotFoundException(ErrorCode.DOCUMENT_NOT_FOUND, ErrorMessages.DOCUMENT_NOT_FOUND);
        }
        return doc;
    }
}
