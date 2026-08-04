package com.middleware.manager.knowledge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.middleware.manager.knowledge.service.KnowledgeService;
import com.middleware.manager.knowledge.service.CorpusHealthService;
import com.middleware.manager.knowledge.service.KnowledgeContentImportService;
import com.middleware.manager.knowledge.service.ParameterLookupService;
import com.middleware.manager.knowledge.service.StandardIndexSyncService;
import com.middleware.manager.knowledge.web.dto.KnowledgeContentImportRequest;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.StorageService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

class KnowledgeControllerTest {

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private StorageService storageService;

    @Mock
    private StandardIndexSyncService standardIndexSyncService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private ParameterLookupService parameterLookupService;

    @Mock
    private CorpusHealthService corpusHealthService;

    @Mock
    private KnowledgeContentImportService contentImportService;

    @Mock
    private Authentication authentication;

    private KnowledgeController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new KnowledgeController(knowledgeService, storageService,
                standardIndexSyncService, permissionService, parameterLookupService,
                corpusHealthService, contentImportService);
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-001 中文文件名使用 RFC 5987 Content-Disposition")
    void serveFileUsesRfc5987ContentDispositionForChineseTitle() {
        String title = "中间件手册.pdf";
        when(knowledgeService.getSourceFilePath(title, "UPLOAD")).thenReturn("stored/manual.pdf");
        when(storageService.loadAsResource("stored/manual.pdf"))
                .thenReturn(new ByteArrayResource("pdf".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<?> response = controller.serveFile(title, "UPLOAD");

        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(contentDisposition)
                .startsWith("attachment; filename=\"")
                .contains("; filename*=UTF-8''%E4%B8%AD%E9%97%B4%E4%BB%B6%E6%89%8B%E5%86%8C.pdf")
                .doesNotContain("中间件手册");
        assertThat(contentDisposition.chars()).allMatch(character -> character <= 0x7f);
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-002 导入端点只传递来源类型和业务 ID")
    void delegatesBusinessSourceReference() {
        KnowledgeContentImportRequest request = importRequest(21L, "FORUM_POST");
        KnowledgeService.ImportResult result = new KnowledgeService.ImportResult();
        result.setSourceId(31L);
        when(contentImportService.importContent("FORUM_POST", 21L, authentication))
                .thenReturn(result);

        ResponseEntity<KnowledgeService.ImportResult> response =
                controller.importContent(request, authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(contentImportService).importContent("FORUM_POST", 21L, authentication);
    }

    private KnowledgeContentImportRequest importRequest(Long sourceId, String sourceType) {
        KnowledgeContentImportRequest request = new KnowledgeContentImportRequest();
        request.setSourceId(sourceId);
        request.setSourceType(sourceType);
        return request;
    }
}
