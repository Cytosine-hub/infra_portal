package com.middleware.manager.knowledge.web;

import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import com.middleware.manager.knowledge.repository.KnowledgeChunkMapper;
import com.middleware.manager.knowledge.service.KnowledgeService;
import com.middleware.manager.knowledge.service.KnowledgeService.ImportResult;
import com.middleware.manager.knowledge.service.KnowledgeService.SearchResult;
import com.middleware.manager.service.StorageService;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private StorageService storageService;

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
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * POST /api/knowledge/batch-upload
     * Upload multiple files into the knowledge base.
     */
    @PostMapping("/batch-upload")
    public ResponseEntity<?> batchUpload(@RequestParam(value = "files", required = false) MultipartFile[] files) {
        if (files == null || files.length == 0) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "未选择文件");
            return ResponseEntity.badRequest().body(error);
        }
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, Object> item = new HashMap<>();
            item.put("fileName", file.getOriginalFilename());
            try {
                ImportResult result = knowledgeService.importFile(file);
                item.put("status", "success");
                item.put("chunkCount", result.getChunkCount());
                item.put("sourceTitle", result.getSourceTitle());
            } catch (Exception e) {
                item.put("status", "error");
                item.put("error", e.getMessage());
            }
            results.add(item);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("total", files.length);
        response.put("results", results);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/knowledge/import/{docId}
     * Import an existing StandardDocument into the knowledge base.
     */
    @PostMapping("/import/{docId}")
    public ResponseEntity<?> importDoc(@PathVariable Long docId) {
        try {
            ImportResult result = knowledgeService.importStandardDocument(docId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * GET /api/knowledge/search?q=xxx&topK=5
     * Search the knowledge base.
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        try {
            List<SearchResult> results = knowledgeService.search(q, topK);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * GET /api/knowledge/docs
     * List all documents in the knowledge base.
     */
    @GetMapping("/docs")
    public ResponseEntity<List<Map<String, Object>>> listDocs() {
        return ResponseEntity.ok(chunkMapper.findDistinctSources());
    }

    /**
     * GET /api/knowledge/docs/preview?title=xxx&sourceType=xxx
     * Get all chunks of a document for preview.
     */
    @GetMapping("/docs/preview")
    public ResponseEntity<?> previewDoc(@RequestParam String title, @RequestParam String sourceType) {
        List<KnowledgeChunk> chunks = chunkMapper.findBySourceTitleAndSourceType(title, sourceType);
        List<Map<String, Object>> result = chunks.stream().map(c -> {
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
        resp.put("storedFileName", chunks.isEmpty() ? null : chunks.get(0).getStoredFileName());
        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/knowledge/docs/file?title=xxx&sourceType=xxx
     * Serve the original file for preview (PDF, Markdown, etc.)
     */
    @GetMapping("/docs/file")
    public ResponseEntity<?> serveFile(@RequestParam String title, @RequestParam String sourceType) {
        List<KnowledgeChunk> chunks = chunkMapper.findBySourceTitleAndSourceType(title, sourceType);
        if (chunks.isEmpty() || chunks.get(0).getStoredFileName() == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "未找到原文件");
            return ResponseEntity.notFound().build();
        }
        String storedFileName = chunks.get(0).getStoredFileName();
        Resource resource = storageService.loadAsResource(storedFileName);

        String contentType = "application/octet-stream";
        String lower = storedFileName.toLowerCase();
        if (lower.endsWith(".pdf")) contentType = "application/pdf";
        else if (lower.endsWith(".md")) contentType = "text/markdown; charset=utf-8";
        else if (lower.endsWith(".docx")) contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        else if (lower.endsWith(".doc")) contentType = "application/msword";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + title + "\"")
                .body(resource);
    }

    /**
     * GET /api/knowledge/docs/html?title=xxx&sourceType=xxx
     * Convert document to HTML for preview (Word docs via Tika).
     */
    @GetMapping("/docs/html")
    public ResponseEntity<?> serveHtml(@RequestParam String title, @RequestParam String sourceType) {
        List<KnowledgeChunk> chunks = chunkMapper.findBySourceTitleAndSourceType(title, sourceType);
        if (chunks.isEmpty() || chunks.get(0).getStoredFileName() == null) {
            return ResponseEntity.notFound().build();
        }
        String storedFileName = chunks.get(0).getStoredFileName();
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

    /**
     * DELETE /api/knowledge/docs/test
     * Delete all test documents from the knowledge base.
     */
    @DeleteMapping("/docs/test")
    public ResponseEntity<?> deleteTestDocs() {
        try {
            int count = knowledgeService.deleteTestDocuments();
            Map<String, Object> result = new HashMap<>();
            result.put("deleted", count);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
