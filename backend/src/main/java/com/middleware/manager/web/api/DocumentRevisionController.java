package com.middleware.manager.web.api;

import com.middleware.manager.domain.DocumentRevision;
import com.middleware.manager.repository.DocumentRevisionMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/revisions")
public class DocumentRevisionController {
    private final DocumentRevisionMapper revisionMapper;

    public DocumentRevisionController(DocumentRevisionMapper revisionMapper) {
        this.revisionMapper = revisionMapper;
    }

    @GetMapping
    public List<DocumentRevision> list(@RequestParam Long documentId,
                                       @RequestParam String documentType) {
        return revisionMapper.findByDocumentIdAndType(documentId, documentType);
    }
}
