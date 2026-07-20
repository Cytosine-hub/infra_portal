package com.middleware.manager.wiki.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WikiImportService {
    private final Gson gson = new Gson();

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;
    private final IngestAgent ingestAgent;

    @Value("${app.wiki.export.signature-secret:middleware-resource-manager}")
    private String signatureSecret;

    public WikiImportService(WikiPageMapper pageMapper, WikiLinkMapper linkMapper, IngestAgent ingestAgent) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
        this.ingestAgent = ingestAgent;
    }

    public static class ImportResult {
        private int pagesCreated;
        private int pagesUpdated;
        private int linksCreated;
        private int conflicts;
        private String status;
        private List<ConflictDetail> conflictDetails = new ArrayList<>();

        public int getPagesCreated() { return pagesCreated; }
        public int getPagesUpdated() { return pagesUpdated; }
        public int getLinksCreated() { return linksCreated; }
        public int getConflicts() { return conflicts; }
        public String getStatus() { return status; }
        public List<ConflictDetail> getConflictDetails() { return conflictDetails; }
        public void setPagesCreated(int v) { this.pagesCreated = v; }
        public void setPagesUpdated(int v) { this.pagesUpdated = v; }
        public void setLinksCreated(int v) { this.linksCreated = v; }
        public void setConflicts(int v) { this.conflicts = v; }
        public void setStatus(String v) { this.status = v; }
    }

    public static class ConflictDetail {
        private String title;
        private String pageType;
        private Long existingPageId;
        private Long importedPageId;

        public ConflictDetail(String title, String pageType, Long existingPageId, Long importedPageId) {
            this.title = title;
            this.pageType = pageType;
            this.existingPageId = existingPageId;
            this.importedPageId = importedPageId;
        }

        public String getTitle() { return title; }
        public String getPageType() { return pageType; }
        public Long getExistingPageId() { return existingPageId; }
        public Long getImportedPageId() { return importedPageId; }
    }

    @Transactional
    public ImportResult importFromZip(byte[] zipBytes) throws IOException {
        return importFromZip(zipBytes, false);
    }

    @Transactional
    public ImportResult importFromZip(byte[] zipBytes, boolean dryRun) throws IOException {
        ImportResult result = new ImportResult();
        Map<String, WikiPage> importedPages = new HashMap<>();
        Map<String, String> entries = readEntries(zipBytes);
        verifyManifest(entries);

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String name = entry.getKey();
            String content = entry.getValue();
            if (name.equals("manifest.json")) {
                log.info("Import manifest: {}", content.substring(0, Math.min(200, content.length())));
            } else if (name.startsWith("pages/") && name.endsWith(".md")) {
                WikiPage page = parsePageMarkdown(content);
                if (page != null) {
                    importedPages.put(page.getTitle(), page);
                }
            }
        }

        int created = 0, conflicts = 0;
        List<WikiPage> savedPages = new ArrayList<>();
        for (WikiPage page : importedPages.values()) {
            WikiPage existing = pageMapper.findByTitleAndType(page.getTitle(), page.getPageType());
            if (existing != null) {
                conflicts++;
                if (dryRun) {
                    result.getConflictDetails().add(new ConflictDetail(
                            existing.getTitle(), existing.getPageType(), existing.getId(), null));
                    continue;
                }
                existing.setStatus("CONTRADICTED");
                String note = buildConflictNote(existing, page);
                existing.setContradictionNote(note);
                pageMapper.update(existing);
                // 保存导入版本为新的 DRAFT 页面，加后缀区分
                page.setTitle(page.getTitle() + " [导入版本]");
                page.setStatus("ACTIVE");
                page.setContradictionNote("这是导入包中的版本，与「" + existing.getTitle().replace(" [导入版本]", "") + "」冲突。审核后可合并或丢弃。");
                pageMapper.insert(page);
                savedPages.add(page);
                result.getConflictDetails().add(new ConflictDetail(
                        existing.getTitle().replace(" [导入版本]", ""),
                        existing.getPageType(),
                        existing.getId(),
                        page.getId()
                ));
            } else {
                created++;
                if (dryRun) {
                    continue;
                }
                page.setStatus("ACTIVE");
                pageMapper.insert(page);
                savedPages.add(page);
            }
        }

        int linksCreated = 0;
        if (!dryRun && entries.containsKey("links.json")) {
            linksCreated = importLinks(entries.get("links.json"), importedPages);
        }
        if (!dryRun) {
            ingestAgent.vectorizePages(savedPages);
        }

        result.setPagesCreated(created);
        result.setLinksCreated(linksCreated);
        result.setConflicts(conflicts);
        result.setStatus(dryRun ? "DRY_RUN" : "SUCCESS");

        log.info("Import completed: dryRun={}, created={}, links={}, conflicts={}", dryRun, created, linksCreated, conflicts);
        return result;
    }

    private Map<String, String> readEntries(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), readEntry(zis));
                zis.closeEntry();
            }
        }
        return entries;
    }

    private void verifyManifest(Map<String, String> entries) {
        String manifestJson = entries.get("manifest.json");
        if (manifestJson == null) {
            throw new com.middleware.manager.exception.BusinessException(
                    com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "导入包缺少 manifest.json");
        }
        JsonObject manifest = gson.fromJson(manifestJson, JsonObject.class);
        if (manifest == null || !manifest.has("file_hashes") || !manifest.has("signature")) {
            throw new com.middleware.manager.exception.BusinessException(
                    com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "导入包缺少签名或文件哈希清单");
        }
        JsonObject hashes = manifest.getAsJsonObject("file_hashes");
        for (Map.Entry<String, JsonElement> hashEntry : hashes.entrySet()) {
            String name = hashEntry.getKey();
            String content = entries.get(name);
            if (content == null) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "导入包缺少文件: " + name);
            }
            String actual = sha256(content);
            String expected = hashEntry.getValue().getAsString();
            if (!actual.equals(expected)) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "导入包文件校验失败: " + name);
            }
        }
        String expectedSignature = sign(hashes);
        String legacyPrettySignature = legacyPrettySign(hashes);
        String actualSignature = manifest.get("signature").getAsString();
        if (!expectedSignature.equals(actualSignature) && !legacyPrettySignature.equals(actualSignature)) {
            throw new com.middleware.manager.exception.BusinessException(
                    com.middleware.manager.constant.ErrorCode.PARAM_INVALID,
                    ErrorMessages.WIKI_IMPORT_SIGNATURE_INVALID);
        }
    }

    private String sign(JsonObject hashes) {
        return sha256(signatureSecret + canonicalHashPayload(hashes));
    }

    private String legacyPrettySign(JsonObject hashes) {
        return sha256(signatureSecret + new GsonBuilder().setPrettyPrinting().create().toJson(hashes));
    }

    private String canonicalHashPayload(JsonObject hashes) {
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : hashes.entrySet()) {
            sorted.put(entry.getKey(), entry.getValue().getAsString());
        }
        return new Gson().toJson(sorted);
    }

    private String buildConflictNote(WikiPage existing, WikiPage imported) {
        StringBuilder sb = new StringBuilder();
        sb.append("【冲突来源】导入包\n");
        sb.append("【检测时间】").append(java.time.LocalDateTime.now().toString().replace("T", " ")).append("\n\n");

        // 差异对比
        sb.append("=== 现有版本 ===\n");
        sb.append("标题: ").append(existing.getTitle()).append("\n");
        sb.append("类型: ").append(existing.getPageType()).append("\n");
        sb.append("版本: ").append(existing.getVersion() != null ? existing.getVersion() : "无").append("\n");
        sb.append("摘要: ").append(existing.getSummary() != null ? existing.getSummary() : "无").append("\n");
        sb.append("内容长度: ").append(existing.getContent() != null ? existing.getContent().length() : 0).append(" 字符\n\n");

        sb.append("=== 导入版本 ===\n");
        sb.append("标题: ").append(imported.getTitle()).append("\n");
        sb.append("类型: ").append(imported.getPageType()).append("\n");
        sb.append("版本: ").append(imported.getVersion() != null ? imported.getVersion() : "无").append("\n");
        sb.append("摘要: ").append(imported.getSummary() != null ? imported.getSummary() : "无").append("\n");
        sb.append("内容长度: ").append(imported.getContent() != null ? imported.getContent().length() : 0).append(" 字符\n\n");

        // 内容差异摘要
        if (existing.getContent() != null && imported.getContent() != null) {
            String existSum = existing.getSummary() != null ? existing.getSummary() : "";
            String importSum = imported.getSummary() != null ? imported.getSummary() : "";
            if (!existSum.equals(importSum)) {
                sb.append("【摘要差异】\n");
                sb.append("  现有: ").append(existSum.isEmpty() ? "(空)" : existSum).append("\n");
                sb.append("  导入: ").append(importSum.isEmpty() ? "(空)" : importSum).append("\n");
            }
            if (!existing.getContent().equals(imported.getContent())) {
                sb.append("【内容差异】内容不同，");
                int diff = imported.getContent().length() - existing.getContent().length();
                sb.append("导入版本").append(diff > 0 ? "多" : "少").append(Math.abs(diff)).append("个字符\n");
            }
        }

        sb.append("\n导入版本已保存为「").append(imported.getTitle()).append("」，可查看对比后裁决。");
        return sb.toString();
    }

    private WikiPage parsePageMarkdown(String markdown) {
        WikiPage page = new WikiPage();
        if (markdown.startsWith("---")) {
            int endIdx = markdown.indexOf("---", 3);
            if (endIdx > 0) {
                String frontmatter = markdown.substring(3, endIdx).trim();
                String body = markdown.substring(endIdx + 3).trim();

                for (String line : frontmatter.split("\n")) {
                    int colonIdx = line.indexOf(':');
                    if (colonIdx > 0) {
                        String key = line.substring(0, colonIdx).trim();
                        String value = line.substring(colonIdx + 1).trim();
                        switch (key) {
                            case "title" -> page.setTitle(value);
                            case "page_type" -> page.setPageType(value);
                            case "category" -> page.setCategory(value);
                            case "software" -> page.setSoftware(value);
                            case "version" -> page.setVersion(value);
                            case "summary" -> page.setSummary(value);
                            case "source_refs" -> page.setSourceRefs(value);
                            case "status" -> page.setStatus(value);
                            case "compiled_by" -> page.setCompiledBy(value);
                        }
                    }
                }
                page.setContent(body);
            }
        } else {
            page.setContent(markdown);
        }

        if (page.getTitle() == null || page.getPageType() == null) {
            log.warn("Skipping page with missing title or page_type");
            return null;
        }
        return page;
    }

    private int importLinks(String linksJson, Map<String, WikiPage> importedPages) {
        int created = 0;
        try {
            JsonArray links = gson.fromJson(linksJson, JsonArray.class);
            for (JsonElement elem : links) {
                JsonObject linkObj = elem.getAsJsonObject();
                String fromTitle = linkObj.get("from").getAsString();
                String toTitle = linkObj.get("to").getAsString();

                WikiPage fromPage = importedPages.get(fromTitle);
                WikiPage toPage = importedPages.get(toTitle);

                if (fromPage != null && toPage != null && fromPage.getId() != null && toPage.getId() != null) {
                    WikiLink link = new WikiLink();
                    link.setFromPageId(fromPage.getId());
                    link.setToPageId(toPage.getId());
                    link.setLinkType(linkObj.has("type") ? linkObj.get("type").getAsString() : "REFERENCES");
                    if (linkObj.has("confidence")) {
                        link.setConfidence(new BigDecimal(linkObj.get("confidence").getAsString()));
                    }
                    linkMapper.insertIgnore(link);
                    created++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to import some links: {}", e.getMessage());
        }
        return created;
    }

    private String readEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new com.middleware.manager.exception.BusinessException(
                    com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "导入包校验失败");
        }
    }
}
