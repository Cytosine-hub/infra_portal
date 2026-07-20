package com.middleware.manager.wiki.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WikiExportService {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;

    @Autowired(required = false)
    private WikiSourceMapper sourceMapper;

    @Value("${app.wiki.export.signature-secret:middleware-resource-manager}")
    private String signatureSecret;

    public WikiExportService(WikiPageMapper pageMapper, WikiLinkMapper linkMapper) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
    }

    public byte[] exportAll() throws IOException {
        List<WikiPage> pages = pageMapper.findAll();
        return exportPages(pages);
    }

    public byte[] exportByCategory(String category) throws IOException {
        List<WikiPage> pages = pageMapper.findByCategory(category);
        return exportPages(pages);
    }

    private byte[] exportPages(List<WikiPage> pages) throws IOException {
        Map<String, String> entries = buildEntries(pages);
        JsonObject manifest = buildManifest(pages, entries);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addZipEntry(zos, "manifest.json", gson.toJson(manifest));
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                addZipEntry(zos, entry.getKey(), entry.getValue());
            }
        }
        log.info("Exported {} pages, ZIP size: {} bytes", pages.size(), baos.size());
        return baos.toByteArray();
    }

    private Map<String, String> buildEntries(List<WikiPage> pages) {
        Map<String, String> entries = new java.util.LinkedHashMap<>();
        for (WikiPage page : pages) {
            String fileName = sanitizeFileName(page.getTitle()) + "_" + page.getPageType() + ".md";
            entries.put("pages/" + fileName, buildPageMarkdown(page));
        }

        Map<Long, String> pageTitleMap = new HashMap<>();
        for (WikiPage page : pages) {
            pageTitleMap.put(page.getId(), page.getTitle());
        }

        JsonArray linksArray = new JsonArray();
        for (WikiPage page : pages) {
            List<WikiLink> links = linkMapper.findByFromPageId(page.getId());
            for (WikiLink link : links) {
                String toTitle = pageTitleMap.get(link.getToPageId());
                if (toTitle != null) {
                    JsonObject linkObj = new JsonObject();
                    linkObj.addProperty("from", page.getTitle());
                    linkObj.addProperty("to", toTitle);
                    linkObj.addProperty("type", link.getLinkType());
                    if (link.getConfidence() != null) linkObj.addProperty("confidence", link.getConfidence());
                    linksArray.add(linkObj);
                }
            }
        }
        entries.put("links.json", gson.toJson(linksArray));
        if (sourceMapper != null) {
            Set<Long> referencedSourceIds = collectReferencedSourceIds(pages);
            List<WikiSource> sources = sourceMapper.findAll().stream()
                    .filter(source -> referencedSourceIds.contains(source.getId()))
                    .collect(Collectors.toList());
            entries.put("sources.json", gson.toJson(sources));
        }
        return entries;
    }

    private Set<Long> collectReferencedSourceIds(List<WikiPage> pages) {
        Set<Long> sourceIds = new HashSet<>();
        for (WikiPage page : pages) {
            String sourceRefs = page.getSourceRefs();
            if (sourceRefs == null || sourceRefs.isBlank()) {
                continue;
            }
            try {
                JsonElement element = gson.fromJson(sourceRefs, JsonElement.class);
                collectReferencedSourceIds(element, sourceIds);
            } catch (Exception e) {
                log.warn("Skip invalid source_refs for page {}: {}", page.getId(), e.getMessage());
            }
        }
        return sourceIds;
    }

    private void collectReferencedSourceIds(JsonElement element, Set<Long> sourceIds) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                collectReferencedSourceIds(item, sourceIds);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement idElement = object.get("id");
        if (idElement != null && !idElement.isJsonNull()) {
            try {
                sourceIds.add(idElement.getAsLong());
            } catch (Exception ignored) {
                // Invalid source id in historical source_refs; ignore it.
            }
        }
    }

    private JsonObject buildManifest(List<WikiPage> pages, Map<String, String> entries) {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("version", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        manifest.addProperty("exported_at", LocalDateTime.now().toString());
        manifest.addProperty("total_pages", pages.size());

        Map<String, Integer> categoryCount = new HashMap<>();
        for (WikiPage page : pages) {
            String cat = page.getCategory() != null ? page.getCategory() : "未分类";
            categoryCount.merge(cat, 1, Integer::sum);
        }
        JsonObject categories = new JsonObject();
        categoryCount.forEach(categories::addProperty);
        manifest.add("categories", categories);

        JsonObject hashes = new JsonObject();
        entries.forEach((name, content) -> hashes.addProperty(name, sha256(content)));
        manifest.add("file_hashes", hashes);
        manifest.addProperty("signature_algorithm", "sha256(secret + canonical_file_hashes_v2)");
        manifest.addProperty("signature", sign(hashes));

        return manifest;
    }

    private String buildPageMarkdown(WikiPage page) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(page.getTitle()).append("\n");
        sb.append("page_type: ").append(page.getPageType()).append("\n");
        if (page.getCategory() != null) sb.append("category: ").append(page.getCategory()).append("\n");
        if (page.getSoftware() != null) sb.append("software: ").append(page.getSoftware()).append("\n");
        if (page.getVersion() != null) sb.append("version: ").append(page.getVersion()).append("\n");
        if (page.getSummary() != null) sb.append("summary: ").append(page.getSummary()).append("\n");
        if (page.getSourceRefs() != null) sb.append("source_refs: ").append(page.getSourceRefs()).append("\n");
        sb.append("status: ").append(page.getStatus()).append("\n");
        if (page.getCompiledBy() != null) sb.append("compiled_by: ").append(page.getCompiledBy()).append("\n");
        sb.append("---\n\n");
        sb.append(page.getContent());
        return sb.toString();
    }

    private void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }

    private String sign(JsonObject hashes) {
        return sha256(signatureSecret + canonicalHashPayload(hashes));
    }

    private String canonicalHashPayload(JsonObject hashes) {
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : hashes.entrySet()) {
            sorted.put(entry.getKey(), entry.getValue().getAsString());
        }
        return new Gson().toJson(sorted);
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
                    com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "导出包签名失败");
        }
    }
}
