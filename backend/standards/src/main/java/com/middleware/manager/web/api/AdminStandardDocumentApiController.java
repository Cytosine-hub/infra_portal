package com.middleware.manager.web.api;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.AccountDirectory;
import com.middleware.manager.service.DocumentConversionService;
import com.middleware.manager.service.StandardDocumentService;
import com.middleware.manager.web.api.dto.StandardDocumentRequest;
import com.middleware.manager.web.api.dto.DocumentPreviewResponse;
import com.middleware.manager.web.api.dto.DocumentUploadResponse;
import com.middleware.manager.web.api.dto.StandardDocumentResponse;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/standard-documents")
public class AdminStandardDocumentApiController {

    private static final String DOCX_EXT = "docx";
    private static final String PDF_EXT = "pdf";
    private static final MediaType DOCX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final MediaType PDF_MEDIA_TYPE = MediaType.APPLICATION_PDF;
    private final StandardDocumentService service;
    private final DocumentConversionService conversionService;
    private final PermissionService permissionService;
    private final AccountDirectory adminAccountService;

    public AdminStandardDocumentApiController(StandardDocumentService service,
                                              DocumentConversionService conversionService,
                                              PermissionService permissionService,
                                              AccountDirectory adminAccountService) {
        this.service = service;
        this.conversionService = conversionService;
        this.permissionService = permissionService;
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    public List<StandardDocumentResponse> list(@RequestParam(defaultValue = "") String keyword,
                                               @RequestParam(defaultValue = "") String documentType,
                                               @RequestParam(defaultValue = "") String status,
                                               Authentication authentication) {
        String category = permissionService.getManagedCategory(authentication);
        return service.list(keyword, documentType, status, category).stream()
                .map(document -> StandardDocumentResponse.from(document, service.render(document)))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public StandardDocumentResponse detail(@PathVariable Long id, Authentication authentication) {
        return StandardDocumentResponse.from(checkDocAccess(id, authentication), service.render(service.get(id)));
    }

    @PostMapping
    public StandardDocumentResponse create(@Valid @RequestBody StandardDocumentRequest request) {
        StandardDocument document = service.create(request);
        return StandardDocumentResponse.from(document, service.render(document));
    }

    @PostMapping("/upload")
    public DocumentUploadResponse upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "convertToMarkdown", defaultValue = "true") boolean convertToMarkdown) {
        return conversionService.convert(file, convertToMarkdown);
    }

    @GetMapping("/preview")
    public DocumentPreviewResponse preview(@RequestParam("storedFileName") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 255) String storedFileName) {
        String html = conversionService.renderAsHtml(storedFileName);
        return new DocumentPreviewResponse(html);
    }

    @GetMapping("/raw")
    public ResponseEntity<Resource> raw(@RequestParam("storedFileName") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 255) String storedFileName) {
        log.info("获取原始文档文件 storedFileName={}", storedFileName);
        Path filePath = conversionService.getDocumentPath(storedFileName);
        Resource resource = new PathResource(filePath);
        int dot = storedFileName.lastIndexOf('.');
        String ext = dot >= 0 ? storedFileName.substring(dot + 1).toLowerCase() : "";
        MediaType mediaType = DOCX_EXT.equals(ext) ? DOCX_MEDIA_TYPE
                : PDF_EXT.equals(ext) ? PDF_MEDIA_TYPE
                : MediaType.APPLICATION_OCTET_STREAM;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.inline().filename(storedFileName).build());
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    @PutMapping("/{id}")
    public StandardDocumentResponse update(@PathVariable Long id,
                                           @Valid @RequestBody StandardDocumentRequest request,
                                           Authentication authentication) {
        checkDocAccess(id, authentication);
        StandardDocument document = service.update(id, request);
        return StandardDocumentResponse.from(document, service.render(document));
    }

    @PostMapping("/{id}/submit-review")
    public StandardDocumentResponse submitForReview(@PathVariable Long id, Authentication authentication) {
        checkDocAccess(id, authentication);
        service.submitForReview(id, authentication.getName(), getDisplayName(authentication));
        StandardDocument document = service.get(id);
        return StandardDocumentResponse.from(document, service.render(document));
    }

    @PostMapping("/{id}/start-modify")
    public StandardDocumentResponse startModify(@PathVariable Long id, Authentication authentication) {
        checkDocAccess(id, authentication);
        StandardDocument document = service.startModify(id);
        return StandardDocumentResponse.from(document, service.render(document));
    }

    @PostMapping("/{id}/cancel-modify")
    public StandardDocumentResponse cancelModify(@PathVariable Long id, Authentication authentication) {
        checkDocAccess(id, authentication);
        StandardDocument document = service.cancelModify(id);
        return StandardDocumentResponse.from(document, service.render(document));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        checkDocAccess(id, authentication);
        service.delete(id);
    }

    private StandardDocument checkDocAccess(Long id, Authentication authentication) {
        StandardDocument doc = service.get(id);
        if (!permissionService.canManageCategory(authentication, doc.getCategory())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
        return doc;
    }

    private String getDisplayName(Authentication authentication) {
        return adminAccountService.getDisplayNameByUsername(authentication.getName());
    }
}
