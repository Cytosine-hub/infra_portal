package com.middleware.manager.web.api;

import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.service.DocumentConversionService;
import com.middleware.manager.service.StandardDocumentService;
import com.middleware.manager.web.api.dto.DocumentPreviewResponse;
import com.middleware.manager.web.api.dto.PublicStandardDocumentResponse;
import com.middleware.manager.web.api.dto.StandardDocumentResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/public/standards")
public class PublicStandardDocumentApiController {
    private static final String DOCX_EXT = "docx";
    private static final MediaType DOCX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final StandardDocumentService service;
    private final DocumentConversionService conversionService;

    public PublicStandardDocumentApiController(StandardDocumentService service,
                                               DocumentConversionService conversionService) {
        this.service = service;
        this.conversionService = conversionService;
    }

    @GetMapping("/raw")
    public ResponseEntity<Resource> rawFile(
            @RequestParam("storedFileName") @NotBlank @Size(max = 255) String storedFileName) {
        log.info("公开获取原始文档文件 storedFileName={}", storedFileName);
        Path filePath = conversionService.getDocumentPath(storedFileName);
        Resource resource = new PathResource(filePath);
        int dot = storedFileName.lastIndexOf('.');
        String ext = dot >= 0 ? storedFileName.substring(dot + 1).toLowerCase() : "";
        MediaType mediaType = DOCX_EXT.equals(ext) ? DOCX_MEDIA_TYPE : MediaType.APPLICATION_OCTET_STREAM;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.inline().filename(storedFileName).build());
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    @GetMapping("/preview")
    public DocumentPreviewResponse previewFile(
            @RequestParam("storedFileName") @NotBlank @Size(max = 255) String storedFileName) {
        String html = conversionService.renderAsHtml(storedFileName);
        return new DocumentPreviewResponse(html);
    }

    @GetMapping
    public List<PublicStandardDocumentResponse> list() {
        return service.listPublishedStandards().stream()
                .map(standard -> PublicStandardDocumentResponse.from(
                        standard,
                        null,
                        service.listPublishedRelatedDocuments(standard.getId()).stream()
                                .map(document -> StandardDocumentResponse.from(document, null))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    @GetMapping("/all")
    public List<PublicStandardDocumentResponse> listAllPublished() {
        return service.listAllPublic().stream()
                .map(doc -> PublicStandardDocumentResponse.from(doc, null, java.util.Collections.emptyList()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public PublicStandardDocumentResponse detail(@PathVariable Long id) {
        try {
            StandardDocument document = service.get(id);
            if (!"PUBLISHED".equals(document.getStatus()) && !"MODIFYING".equals(document.getStatus())) {
                throw new NotFoundException(ErrorCode.DOCUMENT_NOT_FOUND, ErrorMessages.DOCUMENT_NOT_FOUND);
            }
            if ("MODIFYING".equals(document.getStatus()) && document.getPreviousContent() != null) {
                document.setContent(document.getPreviousContent());
                document.setRenderedContent(null);
            }
            Long standardId = "STANDARD".equals(document.getDocumentType())
                    ? document.getId()
                    : document.getRelatedStandardDocumentId();
            List<StandardDocumentResponse> relatedDocuments = standardId == null
                    ? java.util.Collections.emptyList()
                    : service.listPublishedRelatedDocuments(standardId).stream()
                            .map(related -> StandardDocumentResponse.from(related, null))
                            .collect(Collectors.toList());
            return PublicStandardDocumentResponse.from(document, service.render(document), relatedDocuments);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}
