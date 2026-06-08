package com.middleware.manager.wiki.web;

import com.middleware.manager.constant.ErrorCode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.repository.AdminAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.wiki.service.WikiSearchService;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.wiki.repository.WikiPagePermissionMapper;
import com.middleware.manager.wiki.entity.IngestTask;
import com.middleware.manager.wiki.entity.LintResult;
import com.middleware.manager.wiki.repository.IngestTaskMapper;
import com.middleware.manager.wiki.repository.WikiIngestLogMapper;
import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.LintResultMapper;
import com.middleware.manager.wiki.repository.WikiAuditLogMapper;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import com.middleware.manager.wiki.service.IngestAgent;
import com.middleware.manager.wiki.service.IngestTaskService;
import com.middleware.manager.wiki.service.LintAgent;
import com.middleware.manager.wiki.entity.WikiPagePermission;
import com.middleware.manager.wiki.service.WikiExportService;
import com.middleware.manager.wiki.service.WikiGraphService;
import com.middleware.manager.wiki.service.WikiImportService;
import com.middleware.manager.wiki.service.WikiPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/wiki")
@Slf4j
public class WikiController {

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;
    private final WikiSourceMapper sourceMapper;
    private final IngestAgent ingestAgent;
    private final WikiExportService exportService;
    private final WikiImportService importService;
    private final WikiGraphService graphService;
    private final List<DocumentLoader> documentLoaders;
    private final AdminAccountMapper adminAccountMapper;
    private final WikiAuditLogMapper auditLogMapper;
    private final LintAgent lintAgent;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    @Autowired
    private WikiSearchService wikiSearchService;
    private final LintResultMapper lintResultMapper;
    private final IngestTaskService taskService;
    private final IngestTaskMapper taskMapper;
    private final WikiIngestLogMapper ingestLogMapper;
    private final WikiPermissionService wikiPermissionService;
    private final WikiPagePermissionMapper pagePermissionMapper;
    private final Gson gson = new Gson();

    public WikiController(WikiPageMapper pageMapper,
                          WikiLinkMapper linkMapper,
                          WikiSourceMapper sourceMapper,
                          IngestAgent ingestAgent,
                          WikiExportService exportService,
                          WikiImportService importService,
                          WikiGraphService graphService,
                          List<DocumentLoader> documentLoaders,
                          AdminAccountMapper adminAccountMapper,
                          WikiAuditLogMapper auditLogMapper,
                          LintAgent lintAgent,
                          LintResultMapper lintResultMapper,
                          WikiPermissionService wikiPermissionService,
                          WikiPagePermissionMapper pagePermissionMapper,
                          IngestTaskService taskService,
                          IngestTaskMapper taskMapper,
                          WikiIngestLogMapper ingestLogMapper,
                          EmbeddingService embeddingService,
                          VectorStore vectorStore) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
        this.sourceMapper = sourceMapper;
        this.ingestAgent = ingestAgent;
        this.exportService = exportService;
        this.importService = importService;
        this.graphService = graphService;
        this.documentLoaders = documentLoaders;
        this.adminAccountMapper = adminAccountMapper;
        this.auditLogMapper = auditLogMapper;
        this.lintAgent = lintAgent;
        this.lintResultMapper = lintResultMapper;
        this.wikiPermissionService = wikiPermissionService;
        this.pagePermissionMapper = pagePermissionMapper;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.ingestLogMapper = ingestLogMapper;
    }

    @GetMapping("/pages")
    public List<WikiPage> listPages(@RequestParam(required = false) String category,
                                    @RequestParam(required = false) String software,
                                    @RequestParam(required = false) String status,
                                    Authentication authentication) {
        List<WikiPage> pages;
        if (category != null) pages = pageMapper.findByCategory(category);
        else if (software != null) pages = pageMapper.findBySoftware(software);
        else if (status != null) pages = pageMapper.findByStatus(status);
        else pages = pageMapper.findAll();
        return filterVisible(authentication, pages);
    }

    public List<WikiPage> listPages(String category, String software, String status) {
        return listPages(category, software, status, null);
    }

    @GetMapping("/pages/{id}")
    public ResponseEntity<WikiPage> getPage(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        WikiPage page = pageMapper.findById(id);
        if (page == null) return ResponseEntity.notFound().build();
        if (!canView(authentication, page)) {
            recordAudit("ACCESS_DENIED", "PAGE", id, authentication, request,
                    "{\"reason\":\"wiki_page_view_denied\"}");
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(page);
    }

    public ResponseEntity<WikiPage> getPage(Long id) {
        return getPage(id, null, null);
    }

    @GetMapping("/pages/search")
    public List<WikiPage> searchPages(@RequestParam String q, @RequestParam(defaultValue = "10") int limit,
                                      Authentication authentication) {
        // 使用 WikiSearchService 的混合检索（向量 + FULLTEXT 并行）
        List<WikiSearchService.WikiSearchResult> searchResults = wikiSearchService.search(q, limit, authentication);
        List<WikiPage> results = new ArrayList<>();
        for (WikiSearchService.WikiSearchResult sr : searchResults) {
            results.add(sr.getPage());
        }
        if (results.isEmpty()) {
            results = filterVisible(authentication, pageMapper.findByTitleContaining(q, limit));
        }
        return results;
    }

    @PostMapping("/pages/reindex")
    public ResponseEntity<Map<String, Object>> reindexPages() {
        List<WikiPage> pages = pageMapper.findAll();
        int success = 0, failed = 0;
        for (WikiPage page : pages) {
            try {
                String vectorId = "wiki_" + page.getId();
                // 先删除旧向量
                try {
                    vectorStore.delete(vectorId);
                } catch (Exception ignored) {}
                // 插入新向量
                String text = page.getTitle() + "\n" + (page.getSummary() != null ? page.getSummary() : "");
                float[] vector = embeddingService.embed(text);
                Map<String, String> metadata = new HashMap<>();
                metadata.put("source", "wiki");
                metadata.put("pageId", String.valueOf(page.getId()));
                metadata.put("title", page.getTitle());
                metadata.put("pageType", page.getPageType());
                metadata.put("content", page.getSummary() != null ? page.getSummary() : page.getTitle());
                metadata.put("sourceTitle", page.getTitle());
                vectorStore.add(vectorId, vector, metadata);
                success++;
            } catch (Exception e) {
                log.warn("Failed to vectorize page {}: {}", page.getId(), e.getMessage());
                failed++;
            }
        }
        return ResponseEntity.ok(Map.of("total", pages.size(), "success", success, "failed", failed));
    }

    @PutMapping("/pages-batch-category")
    public ResponseEntity<Void> batchUpdateCategory(@RequestBody Map<String, Object> body) {
        List<?> ids = (List<?>) body.get("ids");
        String category = (String) body.get("category");
        String software = (String) body.get("software");
        if (ids == null || ids.isEmpty()) return ResponseEntity.badRequest().build();
        List<Long> idList = ids.stream().map(idObj -> Long.valueOf(idObj.toString())).toList();
        List<WikiPage> pages = pageMapper.findByIds(idList);
        for (WikiPage page : pages) {
            if (category != null && !category.isBlank()) page.setCategory(category);
            if (software != null && !software.isBlank()) page.setSoftware(software);
            pageMapper.update(page);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/pages/{id}")
    public ResponseEntity<WikiPage> updatePage(@PathVariable Long id, @RequestBody WikiPage page,
                                                Authentication authentication, HttpServletRequest request) {
        WikiPage existing = pageMapper.findById(id);
        if (existing == null) return ResponseEntity.notFound().build();

        String oldStatus = existing.getStatus();
        String newStatus = page.getStatus();

        if (page.getTitle() != null) existing.setTitle(page.getTitle());
        if (page.getContent() != null) existing.setContent(page.getContent());
        if (page.getSummary() != null) existing.setSummary(page.getSummary());
        if (page.getCategory() != null) existing.setCategory(page.getCategory());
        if (page.getSoftware() != null) existing.setSoftware(page.getSoftware());
        if (page.getVersion() != null) existing.setVersion(page.getVersion());
        if (page.getContradictionNote() != null) existing.setContradictionNote(page.getContradictionNote());
        if (newStatus != null) existing.setStatus(newStatus);

        // Set reviewer info when approving (status -> ACTIVE)
        if (newStatus != null && "ACTIVE".equals(newStatus) && authentication != null) {
            try {
                AdminAccount reviewer = adminAccountMapper.findByUsername(authentication.getName());
                if (reviewer != null) {
                    existing.setReviewedBy(reviewer.getId());
                    existing.setReviewedAt(LocalDateTime.now());
                }
            } catch (Exception e) {
                log.warn("Failed to resolve reviewer: {}", e.getMessage());
            }
        }

        existing.setUpdatedAt(LocalDateTime.now());
        pageMapper.update(existing);

        // Audit log for status changes
        if (newStatus != null && !newStatus.equals(oldStatus)) {
            String auditAction = resolveAuditAction(newStatus);
            Long actorId = resolveActorId(authentication);
            String actorIp = request.getRemoteAddr();
            JsonObject detailObj = new JsonObject();
            detailObj.addProperty("oldStatus", oldStatus);
            detailObj.addProperty("newStatus", newStatus);
            detailObj.addProperty("title", existing.getTitle());
            String detail = gson.toJson(detailObj);
            try {
                auditLogMapper.insert(auditAction, "PAGE", id, actorId,
                        authentication != null ? authentication.getName() : "system", actorIp, detail);
            } catch (Exception e) {
                log.warn("Audit log write failed: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(existing);
    }

    @DeleteMapping("/pages/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable Long id) {
        // 级联删除：链接、权限、向量
        linkMapper.deleteByPageId(id);
        pagePermissionMapper.deleteByPageId(id);
        try {
            vectorStore.delete("wiki_" + id);
        } catch (Exception e) {
            log.debug("Vector delete failed for page {}: {}", id, e.getMessage());
        }
        pageMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ingest/upload")
    public ResponseEntity<IngestTask> ingestUpload(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(required = false) String category,
                                                    @RequestParam(required = false) String software,
                                                    Authentication authentication) {
        try {
            Long operatorId = resolveActorId(authentication);
            IngestTask task = taskService.createTask(file.getBytes(), file.getOriginalFilename(), category, software, operatorId);
            taskService.executeTask(task.getId());
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            log.error("Ingest upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/ingest/text")
    public ResponseEntity<IngestTask> ingestText(@RequestBody Map<String, String> body,
                                                  Authentication authentication) {
        try {
            String title = body.getOrDefault("title", "未命名文档");
            String content = body.get("content");
            String category = body.get("category");
            String software = body.get("software");

            if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();

            Long operatorId = resolveActorId(authentication);
            IngestTask task = taskService.createTextTask(title, content, category, software, operatorId);
            taskService.executeTask(task.getId());
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            log.error("Ingest text failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/ingest/tasks")
    public List<IngestTask> listTasks() {
        return taskService.getAllTasks();
    }

    @GetMapping("/ingest/tasks/{id}")
    public ResponseEntity<IngestTask> getTask(@PathVariable Long id) {
        IngestTask task = taskService.getTask(id);
        return task != null ? ResponseEntity.ok(task) : ResponseEntity.notFound().build();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWiki(@RequestParam(required = false) String category,
                                              Authentication authentication, HttpServletRequest request) {
        try {
            if (!canAdministerWiki(authentication, category)) {
                recordAudit("ACCESS_DENIED", "SYSTEM", null, authentication, request,
                        "{\"reason\":\"wiki_export_denied\"}");
                return ResponseEntity.status(403).build();
            }
            byte[] zipBytes = (category != null) ? exportService.exportByCategory(category) : exportService.exportAll();
            Long actorId = resolveActorId(authentication);
            try {
                auditLogMapper.insert("INGEST_EXPORT", "SYSTEM", null, actorId,
                        authentication != null ? authentication.getName() : "system",
                        request.getRemoteAddr(), "{\"type\":\"export\"}");
            } catch (Exception e) { log.warn("Audit log failed: {}", e.getMessage()); }
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=wiki-export.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
        } catch (Exception e) {
            log.error("Export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/import")
    public ResponseEntity<WikiImportService.ImportResult> importWiki(@RequestParam("file") MultipartFile file,
                                                                      @RequestParam(defaultValue = "false") boolean dryRun,
                                                                      Authentication authentication, HttpServletRequest request) {
        try {
            if (!wikiPermissionService.isAdmin(authentication)) {
                recordAudit("ACCESS_DENIED", "SYSTEM", null, authentication, request,
                        "{\"reason\":\"wiki_import_denied\"}");
                return ResponseEntity.status(403).build();
            }
            WikiImportService.ImportResult result = importService.importFromZip(file.getBytes(), dryRun);
            Long actorId = resolveActorId(authentication);
            try {
                String detail = String.format("{\"dryRun\":%s,\"pagesCreated\":%d,\"pagesUpdated\":%d,\"conflicts\":%d,\"linksCreated\":%d}",
                        dryRun, result.getPagesCreated(), result.getPagesUpdated(), result.getConflicts(), result.getLinksCreated());
                auditLogMapper.insert("INGEST_IMPORT", "SYSTEM", null, actorId,
                        authentication != null ? authentication.getName() : "system",
                        request.getRemoteAddr(), detail);
            } catch (Exception e) { log.warn("Audit log failed: {}", e.getMessage()); }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/graph")
    public Map<String, Object> getWikiGraph(Authentication authentication) {
        return graphService.buildGraph(authentication);
    }

    @GetMapping("/pages/{id}/links")
    public List<Map<String, Object>> getPageLinks(@PathVariable Long id, Authentication authentication,
                                                  HttpServletRequest request) {
        WikiPage current = pageMapper.findById(id);
        if (current == null) return Collections.emptyList();
        if (!canView(authentication, current)) {
            recordAudit("ACCESS_DENIED", "PAGE", id, authentication, request,
                    "{\"reason\":\"wiki_page_links_denied\"}");
            return Collections.emptyList();
        }
        List<WikiLink> links = linkMapper.findAllByPageId(id);
        if (links.isEmpty()) return Collections.emptyList();
        // 批量获取关联页面，避免 N+1 查询
        Set<Long> relatedIds = new HashSet<>();
        for (WikiLink link : links) {
            relatedIds.add(link.getFromPageId().equals(id) ? link.getToPageId() : link.getFromPageId());
        }
        Map<Long, WikiPage> pageMap = new HashMap<>();
        for (WikiPage p : pageMapper.findByIds(new ArrayList<>(relatedIds))) {
            pageMap.put(p.getId(), p);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (WikiLink link : links) {
            Long relatedId = link.getFromPageId().equals(id) ? link.getToPageId() : link.getFromPageId();
            WikiPage related = pageMap.get(relatedId);
            if (canView(authentication, related)) {
                Map<String, Object> item = new HashMap<>();
                item.put("linkId", link.getId());
                item.put("linkType", link.getLinkType());
                item.put("direction", link.getFromPageId().equals(id) ? "outgoing" : "incoming");
                item.put("relatedPageId", relatedId);
                item.put("relatedTitle", related.getTitle());
                item.put("relatedPageType", related.getPageType());
                item.put("relatedStatus", related.getStatus());
                result.add(item);
            }
        }
        return result;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_pages", pageMapper.countAll());
        stats.put("active_pages", pageMapper.countByStatus("ACTIVE"));
        stats.put("draft_pages", pageMapper.countByStatus("DRAFT"));
        stats.put("contradicted_pages", pageMapper.countByStatus("CONTRADICTED"));
        stats.put("stale_pages", pageMapper.countByStatus("STALE"));
        stats.put("total_sources", sourceMapper.countAll());
        stats.put("uningested_sources", sourceMapper.countByIngested(false));
        return stats;
    }

    @GetMapping("/sources")
    public List<WikiSource> listSources(Authentication authentication) {
        List<WikiSource> sources = sourceMapper.findAll();
        if (authentication == null || wikiPermissionService.isAdmin(authentication)) return sources;
        String category = wikiPermissionService.getManagedCategory(authentication);
        if (category == null) return Collections.emptyList();
        return sources.stream()
                .filter(source -> source.getCategory() == null || category.equals(source.getCategory()))
                .toList();
    }

    @GetMapping("/sources/{id}")
    public ResponseEntity<WikiSource> getSource(@PathVariable Long id, Authentication authentication,
                                                HttpServletRequest request) {
        WikiSource source = sourceMapper.findById(id);
        if (source == null) return ResponseEntity.notFound().build();
        if (!canViewSource(authentication, source)) {
            recordAudit("ACCESS_DENIED", "SOURCE", id, authentication, request,
                    "{\"reason\":\"wiki_source_view_denied\"}");
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(source);
    }

    @DeleteMapping("/sources/{id}")
    public ResponseEntity<Void> deleteSource(@PathVariable Long id) {
        sourceMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sources/{id}/ingest")
    public ResponseEntity<IngestTask> reingestSource(@PathVariable Long id, Authentication authentication) {
        WikiSource source = sourceMapper.findById(id);
        if (source == null) return ResponseEntity.notFound().build();
        try {
            Long operatorId = resolveActorId(authentication);
            IngestTask task = taskService.createReingestTask(source.getId(), operatorId);
            taskService.executeTask(task.getId());
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            log.error("Re-ingest failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // --- Lint endpoints ---

    @GetMapping("/lint/results")
    public List<LintResult> listLintResults(@RequestParam(required = false) String severity) {
        if (severity != null) return lintResultMapper.findBySeverity(severity);
        return lintResultMapper.findUnresolved();
    }

    @PostMapping("/lint/run")
    public List<LintResult> runLint(Authentication authentication, HttpServletRequest request) {
        List<LintResult> results = lintAgent.runLint();
        recordAudit("LINT_RUN", "SYSTEM", null, authentication, request,
                "{\"total\":" + results.size() + "}");
        return results;
    }

    @PutMapping("/lint/results/{id}/resolve")
    public ResponseEntity<Void> resolveLintResult(@PathVariable Long id, Authentication authentication) {
        Long actorId = resolveActorId(authentication);
        int updated = lintResultMapper.resolve(id, actorId);
        if (updated > 0) {
            recordAudit("LINT_RESOLVE", "SYSTEM", id, authentication, null,
                    "{\"lintResultId\":" + id + "}");
        }
        return updated > 0 ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // --- Permission endpoints ---

    @GetMapping("/pages/{id}/permission")
    public ResponseEntity<WikiPagePermission> getPagePermission(@PathVariable Long id) {
        WikiPagePermission perm = wikiPermissionService.getPagePermission(id);
        return ResponseEntity.ok(perm); // may be null if no override exists
    }

    @PutMapping("/pages/{id}/permission")
    public ResponseEntity<WikiPagePermission> setPagePermission(@PathVariable Long id,
                                                                 @RequestBody Map<String, String> body,
                                                                 Authentication authentication) {
        // Only SYS_ADMIN and category admins can set permissions
        WikiPage page = pageMapper.findById(id);
        if (page == null) return ResponseEntity.notFound().build();

        if (!wikiPermissionService.isAdmin(authentication) &&
            !wikiPermissionService.isCategoryAdminForPage(authentication, page)) {
            return ResponseEntity.status(403).build();
        }

        String permissionType = body.get("permissionType");
        String targetRoles = body.get("targetRoles");

        if (permissionType == null || permissionType.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Long userId = resolveActorId(authentication);
        WikiPagePermission perm = wikiPermissionService.setPagePermission(id, permissionType, targetRoles, userId);
        return ResponseEntity.ok(perm);
    }

    private DocumentLoader resolveLoader(String fileName) {
        for (DocumentLoader loader : documentLoaders) {
            if (loader.supports(fileName)) return loader;
        }
        throw new com.middleware.manager.exception.BusinessException(ErrorCode.PARAM_INVALID, "不支持的文档格式");
    }

    private String resolveAuditAction(String status) {
        return switch (status) {
            case "PENDING_REVIEW" -> "PAGE_SUBMIT";
            case "ACTIVE" -> "PAGE_APPROVE";
            case "REJECTED" -> "PAGE_REJECT";
            case "ARCHIVED" -> "PAGE_DELETE";
            default -> "PAGE_EDIT";
        };
    }

    private Long resolveActorId(Authentication authentication) {
        if (authentication == null) return 0L;
        try {
            AdminAccount account = adminAccountMapper.findByUsername(authentication.getName());
            return account != null ? account.getId() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<WikiPage> filterVisible(Authentication authentication, List<WikiPage> pages) {
        if (authentication == null) return pages;
        return wikiPermissionService.filterVisiblePages(authentication, pages);
    }

    private boolean canView(Authentication authentication, WikiPage page) {
        return authentication == null || wikiPermissionService.canView(authentication, page);
    }

    private boolean canViewSource(Authentication authentication, WikiSource source) {
        if (authentication == null || wikiPermissionService.isAdmin(authentication)) return true;
        String category = wikiPermissionService.getManagedCategory(authentication);
        return category != null && (source.getCategory() == null || category.equals(source.getCategory()));
    }

    private boolean canAdministerWiki(Authentication authentication, String category) {
        if (authentication == null || wikiPermissionService.isAdmin(authentication)) return true;
        if (category == null || category.isBlank()) return false;
        String managedCategory = wikiPermissionService.getManagedCategory(authentication);
        return category.equals(managedCategory);
    }

    private void recordAudit(String action, String targetType, Long targetId,
                             Authentication authentication, HttpServletRequest request, String detail) {
        try {
            auditLogMapper.insert(action, targetType, targetId, resolveActorId(authentication),
                    authentication != null ? authentication.getName() : "system",
                    request != null ? request.getRemoteAddr() : null,
                    detail);
        } catch (Exception e) {
            log.warn("Audit log write failed: {}", e.getMessage());
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, com.middleware.manager.constant.ErrorMessages.SHA256_UNAVAILABLE);
        }
    }
}
