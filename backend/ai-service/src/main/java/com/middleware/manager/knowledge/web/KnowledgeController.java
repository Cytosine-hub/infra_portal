package com.middleware.manager.knowledge.web;

import com.middleware.manager.knowledge.service.KnowledgeService;
import com.middleware.manager.knowledge.service.KnowledgeService.ImportResult;
import com.middleware.manager.knowledge.service.KnowledgeService.PreviewDocument;
import com.middleware.manager.knowledge.service.KnowledgeSearchResult;
import com.middleware.manager.knowledge.store.VectorSearchFilter;
import com.middleware.manager.service.StorageService;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.middleware.manager.knowledge.service.CorpusHealthService;
import com.middleware.manager.knowledge.service.ParameterLookupService;
import com.middleware.manager.knowledge.service.StandardIndexSyncService;
import com.middleware.manager.security.PermissionService;
import org.springframework.security.core.Authentication;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/knowledge")
@Slf4j
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final StorageService storageService;
    private final StandardIndexSyncService standardIndexSyncService;
    private final PermissionService permissionService;
    private final ParameterLookupService parameterLookupService;
    private final CorpusHealthService corpusHealthService;

    public KnowledgeController(KnowledgeService knowledgeService,
                               StorageService storageService,
                               StandardIndexSyncService standardIndexSyncService,
                               PermissionService permissionService,
                               ParameterLookupService parameterLookupService,
                               CorpusHealthService corpusHealthService) {
        this.knowledgeService = knowledgeService;
        this.storageService = storageService;
        this.standardIndexSyncService = standardIndexSyncService;
        this.permissionService = permissionService;
        this.parameterLookupService = parameterLookupService;
        this.corpusHealthService = corpusHealthService;
    }

    /**
     * POST /api/knowledge/upload
     * Upload a file into the knowledge base.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            ImportResult result = knowledgeService.importFile(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Knowledge upload failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "知识库文档上传失败，请查看后台日志");
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * GET /api/knowledge/search?q=xxx&topK=5
     * Search the knowledge base.
     */
    /**
     * 触发参数标准 → 知识库索引的对账。标准发布后由启动对账或此端点同步，
     * 不提供人工「导入」入口（导入会产生会腐烂的静态副本）。
     */
    @PostMapping("/sync-standards")
    public ResponseEntity<StandardIndexSyncService.SyncReport> syncStandards(Authentication authentication) {
        if (!permissionService.isAdmin(authentication)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(standardIndexSyncService.sync());
    }

    /**
     * 参数标准精确查询。参数值必须 100% 准确且可追责，RAG 只能给语义相近的片段，
     * 因此这类问题直接查表并返回带标准版本号的确定答案，不走检索。
     */
    @GetMapping("/parameters")
    public ResponseEntity<List<ParameterLookupService.ParameterAnswer>> lookupParameters(
            @RequestParam(required = false) String software,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(parameterLookupService.lookup(software, name, limit));
    }

    /**
     * 语料健康度。检索层与生成层的分数只说明「现有语料检索得好不好」，
     * 说明不了「该写的内容写了没有」，而后者才是当前真正的瓶颈（首次实测覆盖率 2.5%）。
     * 其中空缺格子列表可直接当作内容待办清单。
     */
    @GetMapping("/corpus-health")
    public ResponseEntity<CorpusHealthService.CorpusHealthReport> corpusHealth() {
        return ResponseEntity.ok(corpusHealthService.report());
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String software,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long sourceId) {
        try {
            VectorSearchFilter filter = VectorSearchFilter.none()
                    .addCategory(category)
                    .addSoftware(software)
                    .addSourceType(sourceType)
                    .addSourceId(sourceId);
            List<KnowledgeSearchResult> results = knowledgeService.search(q, topK, filter);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.warn("Knowledge search failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "知识库搜索失败，请查看后台日志");
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * GET /api/knowledge/docs
     * List all documents in the knowledge base.
     */
    @GetMapping("/docs")
    public ResponseEntity<List<Map<String, Object>>> listDocs() {
        return ResponseEntity.ok(knowledgeService.listDocuments());
    }

    /**
     * GET /api/knowledge/docs/preview?title=xxx&sourceType=xxx
     * Get all chunks of a document for preview.
     */
    @GetMapping("/docs/preview")
    public ResponseEntity<?> previewDoc(@RequestParam String title, @RequestParam String sourceType) {
        PreviewDocument preview = knowledgeService.previewDocument(title, sourceType);
        List<Map<String, Object>> result = preview.getChunks().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("chunkIndex", c.getChunkIndex());
            m.put("content", c.getContent());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> resp = new HashMap<>();
        resp.put("title", title);
        resp.put("sourceType", sourceType);
        resp.put("chunks", result);
        resp.put("totalChunks", result.size());
        resp.put("storedFileName", preview.getStoredFileName());
        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/knowledge/docs/file?title=xxx&sourceType=xxx
     * Serve the original file for preview (PDF, Markdown, etc.)
     */
    @GetMapping("/docs/file")
    public ResponseEntity<?> serveFile(@RequestParam String title, @RequestParam String sourceType) {
        String storedFileName = knowledgeService.getSourceFilePath(title, sourceType);
        Resource resource = storageService.loadAsResource(storedFileName);

        String contentType = "application/octet-stream";
        String lower = storedFileName.toLowerCase();
        if (lower.endsWith(".pdf")) contentType = "application/pdf";
        else if (lower.endsWith(".md")) contentType = "text/markdown; charset=utf-8";
        else if (lower.endsWith(".docx")) contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        else if (lower.endsWith(".doc")) contentType = "application/msword";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(title))
                .body(resource);
    }

    private String buildContentDisposition(String fileName) {
        String asciiFallback = fileName
                .replaceAll("[^\\x20-\\x7E]", "_")
                .replace('"', '_')
                .replace('\\', '_');
        if (asciiFallback.isBlank()) {
            asciiFallback = "download";
        }
        String encodedFileName = UriUtils.encode(fileName, StandardCharsets.UTF_8);
        return "attachment; filename=\"" + asciiFallback
                + "\"; filename*=UTF-8''" + encodedFileName;
    }

    /**
     * GET /api/knowledge/docs/html?title=xxx&sourceType=xxx
     * Convert document to HTML for preview (Word docs via Tika).
     */
    @GetMapping("/docs/html")
    public ResponseEntity<?> serveHtml(@RequestParam String title, @RequestParam String sourceType) {
        String storedFileName = knowledgeService.getSourceFilePath(title, sourceType);
        String lower = storedFileName.toLowerCase();

        // 只处理 Word 文档，PDF 和 Markdown 前端直接渲染
        if (!lower.endsWith(".doc") && !lower.endsWith(".docx")) {
            return ResponseEntity.badRequest().body("{\"error\":\"此文件类型不需要HTML转换\"}");
        }

        try {
            Resource resource = storageService.loadAsResource(storedFileName);
            ToXMLContentHandler handler = new ToXMLContentHandler();
            AutoDetectParser parser = new AutoDetectParser();
            try (InputStream is = resource.getInputStream()) {
                parser.parse(is, handler, new Metadata());
            }
            String html = handler.toString();
            // 提取 body 内容
            int bodyStart = html.indexOf("<body>");
            int bodyEnd = html.indexOf("</body>");
            String body = (bodyStart >= 0 && bodyEnd > bodyStart)
                    ? html.substring(bodyStart + 6, bodyEnd)
                    : html;

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(body);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "文档转换失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * DELETE /api/knowledge/docs?title=xxx&sourceType=xxx
     * Delete a document from the knowledge base by title and source type.
     */
    @DeleteMapping("/docs")
    public ResponseEntity<?> deleteDoc(@RequestParam String title, @RequestParam String sourceType) {
        try {
            int count = knowledgeService.deleteDocument(title, sourceType);
            Map<String, Object> result = new HashMap<>();
            result.put("deleted", count);
            result.put("title", title);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

}
