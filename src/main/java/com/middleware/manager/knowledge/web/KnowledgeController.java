package com.middleware.manager.knowledge.web;

import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import com.middleware.manager.knowledge.repository.KnowledgeChunkRepository;
import com.middleware.manager.knowledge.service.KnowledgeService;
import com.middleware.manager.knowledge.service.KnowledgeService.ImportResult;
import com.middleware.manager.knowledge.service.KnowledgeService.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private KnowledgeChunkRepository chunkRepository;

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
        return ResponseEntity.ok(chunkRepository.findDistinctSources());
    }

    /**
     * GET /api/knowledge/docs/preview?title=xxx&sourceType=xxx
     * Get all chunks of a document for preview.
     */
    @GetMapping("/docs/preview")
    public ResponseEntity<?> previewDoc(@RequestParam String title, @RequestParam String sourceType) {
        List<KnowledgeChunk> chunks = chunkRepository.findBySourceTitleAndSourceType(title, sourceType);
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
        return ResponseEntity.ok(resp);
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
