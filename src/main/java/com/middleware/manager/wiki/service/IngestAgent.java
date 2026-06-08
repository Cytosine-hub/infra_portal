package com.middleware.manager.wiki.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.SoftwareTypeMapper;
import com.middleware.manager.wiki.entity.WikiIngestLog;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiIngestLogMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class IngestAgent {
    private static final int MAX_RETRIES = 3;
    private static final Set<String> VALID_PAGE_TYPES = Set.of(
            "ENTITY", "CONCEPT", "RUNBOOK", "EXPERIENCE", "STANDARD", "SYNTHESIS", "OVERVIEW");

    private final ChatModel chatModel;
    private final WikiPageMapper pageMapper;
    private final WikiSourceMapper sourceMapper;
    private final WikiIngestLogMapper logMapper;
    private final LinkResolver linkResolver;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final SoftwareTypeMapper softwareTypeMapper;
    private final Gson gson = new Gson();
    private final int maxContentChars;

    @Value("${app.vector.type:milvus}")
    private String vectorType;

    public IngestAgent(ChatModel chatModel,
                       WikiPageMapper pageMapper,
                       WikiSourceMapper sourceMapper,
                       WikiIngestLogMapper logMapper,
                       LinkResolver linkResolver,
                       EmbeddingService embeddingService,
                       VectorStore vectorStore,
                       SoftwareTypeMapper softwareTypeMapper,
                       @Value("${app.wiki.ingest.max-content-chars:50000}") int maxContentChars) {
        this.chatModel = chatModel;
        this.pageMapper = pageMapper;
        this.sourceMapper = sourceMapper;
        this.logMapper = logMapper;
        this.linkResolver = linkResolver;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.softwareTypeMapper = softwareTypeMapper;
        this.maxContentChars = maxContentChars;
    }

    public static class IngestResult {
        private int pagesCreated;
        private int pagesUpdated;
        private int linksCreated;
        private int contradictionsFound;
        private String status;
        private String errorMessage;

        public int getPagesCreated() { return pagesCreated; }
        public int getPagesUpdated() { return pagesUpdated; }
        public int getLinksCreated() { return linksCreated; }
        public int getContradictionsFound() { return contradictionsFound; }
        public String getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }
        public void setPagesCreated(int v) { this.pagesCreated = v; }
        public void setPagesUpdated(int v) { this.pagesUpdated = v; }
        public void setLinksCreated(int v) { this.linksCreated = v; }
        public void setContradictionsFound(int v) { this.contradictionsFound = v; }
        public void setStatus(String v) { this.status = v; }
        public void setErrorMessage(String v) { this.errorMessage = v; }
    }

    @Transactional
    public IngestResult ingest(WikiSource source, Long operatorId) {
        long startTime = System.currentTimeMillis();
        IngestResult result = new IngestResult();
        WikiIngestLog ingestLog = new WikiIngestLog();
        ingestLog.setSourceId(source.getId());
        ingestLog.setOperatorId(operatorId);

        try {
            String hash = sha256(source.getContent());
            if (hash.equals(source.getContentHash()) && Boolean.TRUE.equals(source.getIngested())) {
                log.info("Source '{}' content unchanged, skipping ingest", source.getTitle());
                result.setStatus("SKIPPED");
                return result;
            }

            String existingSummary = buildExistingPagesSummary(source.getCategory(), source.getSoftware());

            // 截断过长内容，避免超出 LLM 上下文窗口
            String content = source.getContent();
            if (content.length() > maxContentChars) {
                log.warn("Source '{}' content too long ({} chars), truncating to {} chars",
                        source.getTitle(), content.length(), maxContentChars);
                content = content.substring(0, maxContentChars) + "\n\n[... 文档内容过长，已截断 ...]";
            }

            log.info("Ingest Step 1: Analyzing source '{}' ({} chars)", source.getTitle(), content.length());
            String softwareRef = buildSoftwareReference();
            String analysisPrompt = IngestPromptTemplates.buildAnalysisPrompt(content, existingSummary, softwareRef);
            String analysisJson = callLlm(analysisPrompt);
            ingestLog.setLlmModel("configured");

            JsonObject analysis = parseJson(analysisJson);
            if (analysis == null) {
                log.error("Failed to parse analysis JSON for source '{}'", source.getTitle());
                result.setStatus("FAILED");
                result.setErrorMessage("Failed to parse analysis JSON");
                ingestLog.setStatus("FAILED");
                ingestLog.setErrorDetail("Failed to parse analysis JSON");
                ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
                logMapper.insert(ingestLog);
                return result;
            }

            log.info("Ingest Step 2: Generating Wiki pages for source '{}'", source.getTitle());
            String generationPrompt = IngestPromptTemplates.buildPageGenerationPrompt(content, analysisJson);
            String pagesJson = callLlm(generationPrompt);

            JsonObject pagesResult = parseJson(pagesJson);
            if (pagesResult == null || !pagesResult.has("pages")) {
                log.error("Failed to parse pages JSON for source '{}'", source.getTitle());
                result.setStatus("FAILED");
                result.setErrorMessage("Failed to parse pages JSON");
                ingestLog.setStatus("FAILED");
                ingestLog.setErrorDetail("Failed to parse pages JSON");
                ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
                logMapper.insert(ingestLog);
                return result;
            }

            int created = 0, updated = 0, contradictions = 0;
            List<WikiPage> savedPages = new ArrayList<>();
            JsonArray pages = pagesResult.getAsJsonArray("pages");
            validateGeneratedPages(pages);

            for (JsonElement elem : pages) {
                JsonObject pageObj = elem.getAsJsonObject();
                String title = getAsString(pageObj, "title");
                String pageType = getAsString(pageObj, "page_type");

                if (title == null || pageType == null) continue;

                WikiPage existing = pageMapper.findByTitleAndType(title, pageType);
                if (existing != null) {
                    String mergeDecision = callLlm(IngestPromptTemplates.buildMergeDecisionPrompt(
                        existing.getContent(), getAsString(pageObj, "content")));
                    JsonObject decision = parseJson(mergeDecision);
                    String action = decision != null ? getAsString(decision, "action") : "APPEND";

                    if ("CONTRADICT".equals(action)) {
                        existing.setStatus("CONTRADICTED");
                        existing.setContradictionNote(decision != null ? getAsString(decision, "reason") : "与新文档内容矛盾");
                        pageMapper.update(existing);
                        contradictions++;
                    } else if ("OVERWRITE".equals(action)) {
                        updatePageFromJson(existing, pageObj, source);
                        pageMapper.update(existing);
                        updated++;
                        savedPages.add(existing);
                    } else {
                        String newContent = getAsString(pageObj, "content");
                        if (newContent != null) {
                            existing.setContent(existing.getContent() + "\n\n---\n\n" + newContent);
                        }
                        String newSummary = getAsString(pageObj, "summary");
                        if (newSummary != null) existing.setSummary(newSummary);
                        existing.setCompiledBy("ingest-agent");
                        existing.setCompiledAt(LocalDateTime.now());
                        pageMapper.update(existing);
                        updated++;
                        savedPages.add(existing);
                    }
                } else {
                    WikiPage newPage = new WikiPage();
                    updatePageFromJson(newPage, pageObj, source);
                    pageMapper.insert(newPage);
                    created++;
                    savedPages.add(newPage);
                }
            }

            int linksCreated = linkResolver.resolveLinks(savedPages);

            if ("milvus".equals(vectorType)) {
                try {
                    for (WikiPage page : savedPages) {
                        String text = page.getTitle() + "\n" + (page.getSummary() != null ? page.getSummary() : "");
                        float[] vector = embeddingService.embed(text);
                        String vectorId = "wiki_" + page.getId();
                        Map<String, String> metadata = new HashMap<>();
                        metadata.put("source", "wiki");
                        metadata.put("pageId", String.valueOf(page.getId()));
                        metadata.put("title", page.getTitle());
                        metadata.put("pageType", page.getPageType());
                        vectorStore.add(vectorId, vector, metadata);
                    }
                } catch (Exception e) {
                    log.warn("Vectorization failed: {}", e.getMessage());
                }
            }

            source.setContentHash(hash);
            source.setIngested(true);
            source.setIngestedAt(LocalDateTime.now());
            sourceMapper.update(source);

            result.setPagesCreated(created);
            result.setPagesUpdated(updated);
            result.setLinksCreated(linksCreated);
            result.setContradictionsFound(contradictions);
            result.setStatus("SUCCESS");

            ingestLog.setPagesCreated(created);
            ingestLog.setPagesUpdated(updated);
            ingestLog.setLinksCreated(linksCreated);
            ingestLog.setContradictionsFound(contradictions);
            ingestLog.setStatus("SUCCESS");
            ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
            logMapper.insert(ingestLog);

            log.info("Ingest completed for '{}': created={}, updated={}, links={}, contradictions={}",
                source.getTitle(), created, updated, linksCreated, contradictions);

        } catch (Exception e) {
            log.error("Ingest failed for source '{}': {}", source.getTitle(), e.getMessage(), e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            ingestLog.setStatus("FAILED");
            ingestLog.setErrorDetail(e.getMessage());
            ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
            logMapper.insert(ingestLog);
        }

        return result;
    }

    /**
     * 直接编译内容，不创建/更新 WikiSource（用于分段编译）
     */
    @Transactional
    public IngestResult ingestContent(String content, String title, String category, String software, Long operatorId) {
        IngestResult result = new IngestResult();

        try {
            String existingSummary = buildExistingPagesSummary(category, software);

            // 截断
            if (content.length() > maxContentChars) {
                content = content.substring(0, maxContentChars) + "\n\n[... 已截断 ...]";
            }

            log.info("IngestContent Step 1: Analyzing '{}' ({} chars)", title, content.length());
            String softwareRef = buildSoftwareReference();
            String analysisPrompt = IngestPromptTemplates.buildAnalysisPrompt(content, existingSummary, softwareRef);
            String analysisJson = callLlm(analysisPrompt);

            JsonObject analysis = parseJson(analysisJson);
            if (analysis == null) {
                log.error("Failed to parse analysis JSON for '{}'", title);
                result.setStatus("FAILED");
                result.setErrorMessage("Failed to parse analysis JSON");
                return result;
            }

            log.info("IngestContent Step 2: Generating pages for '{}'", title);
            String generationPrompt = IngestPromptTemplates.buildPageGenerationPrompt(content, analysisJson);
            String pagesJson = callLlm(generationPrompt);

            JsonObject pagesResult = parseJson(pagesJson);
            if (pagesResult == null || !pagesResult.has("pages")) {
                log.error("Failed to parse pages JSON for '{}'", title);
                result.setStatus("FAILED");
                result.setErrorMessage("Failed to parse pages JSON");
                return result;
            }

            // 保存页面
            int created = 0, updated = 0;
            JsonArray pages = pagesResult.getAsJsonArray("pages");
            validateGeneratedPages(pages);
            for (JsonElement pageElem : pages) {
                JsonObject pageObj = pageElem.getAsJsonObject();
                String pageTitle = getAsString(pageObj, "title");
                String pageType = getAsString(pageObj, "page_type");
                if (pageTitle == null || pageType == null) continue;

                // 去掉分段后缀，用原始标题
                String cleanTitle = pageTitle.replaceAll("\\s*\\[\\d+\\]$", "");

                WikiPage existing = pageMapper.findByTitleAndType(cleanTitle, pageType);
                if (existing != null) {
                    String mergeDecision = callLlm(IngestPromptTemplates.buildMergeDecisionPrompt(
                            existing.getContent(), getAsString(pageObj, "content")));
                    JsonObject decision = parseJson(mergeDecision);
                    String action = decision != null ? getAsString(decision, "action") : "OVERWRITE";

                    if ("CONTRADICT".equals(action)) {
                        existing.setStatus("CONTRADICTED");
                        existing.setContradictionNote(decision != null ? getAsString(decision, "reason") : "与新文档内容矛盾");
                        pageMapper.update(existing);
                    } else if ("APPEND".equals(action)) {
                        existing.setContent(existing.getContent() + "\n\n" + getAsString(pageObj, "content"));
                        existing.setUpdatedAt(LocalDateTime.now());
                        pageMapper.update(existing);
                    } else {
                        existing.setContent(getAsString(pageObj, "content"));
                        existing.setSummary(getAsString(pageObj, "summary"));
                        existing.setUpdatedAt(LocalDateTime.now());
                        pageMapper.update(existing);
                    }
                    updated++;
                } else {
                    WikiPage newPage = new WikiPage();
                    newPage.setTitle(cleanTitle);
                    newPage.setPageType(pageType);
                    newPage.setCategory(category);
                    newPage.setSoftware(software);
                    newPage.setVersion(getAsString(pageObj, "version"));
                    newPage.setContent(getAsString(pageObj, "content"));
                    newPage.setSummary(getAsString(pageObj, "summary"));
                    newPage.setStatus("DRAFT");
                    newPage.setCompiledBy("configured");
                    pageMapper.insert(newPage);
                    created++;
                }
            }

            // 解析链接
            List<WikiPage> savedPages = new ArrayList<>();
            for (JsonElement pageElem : pages) {
                JsonObject pageObj = pageElem.getAsJsonObject();
                String pageTitle = getAsString(pageObj, "title");
                if (pageTitle == null) continue;
                String cleanTitle = pageTitle.replaceAll("\\s*\\[\\d+\\]$", "");
                WikiPage savedPage = pageMapper.findByTitleAndType(cleanTitle, getAsString(pageObj, "page_type"));
                if (savedPage != null) {
                    savedPages.add(savedPage);
                }
            }
            if (!savedPages.isEmpty()) {
                linkResolver.resolveLinks(savedPages);
            }

            result.setPagesCreated(created);
            result.setPagesUpdated(updated);
            result.setStatus("SUCCESS");

        } catch (Exception e) {
            log.error("IngestContent failed for '{}': {}", title, e.getMessage(), e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    private String callLlm(String prompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("你是一个严格的知识编译器。只输出要求的格式，不要包含任何其他文字。"));
        messages.add(new UserMessage(prompt));

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ChatResponse response = chatModel.chat(messages);
                AiMessage aiMessage = response.aiMessage();
                return aiMessage != null ? aiMessage.text() : "";
            } catch (Exception e) {
                log.warn("LLM call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(attempt * 2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "LLM 调用失败，请稍后再试");
    }

    private JsonObject parseJson(String text) {
        if (text == null) return null;
        text = text.trim();

        // 去掉 markdown 代码块
        if (text.startsWith("```json")) text = text.substring(7);
        if (text.startsWith("```")) text = text.substring(3);
        if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        text = text.trim();

        // 直接尝试解析
        try {
            return gson.fromJson(text, JsonObject.class);
        } catch (Exception ignored) {}

        // 尝试提取第一个 { ... } 块
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return gson.fromJson(text.substring(start, end + 1), JsonObject.class);
            } catch (Exception ignored) {}
        }

        log.warn("Failed to parse JSON from LLM response ({} chars): {}...",
                text.length(), text.substring(0, Math.min(200, text.length())));
        return null;
    }

    private String getAsString(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return elem != null && !elem.isJsonNull() ? elem.getAsString() : null;
    }

    private void validateGeneratedPages(JsonArray pages) {
        if (pages == null || pages.isEmpty()) {
            throw new com.middleware.manager.exception.BusinessException(
                    com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 未生成有效 Wiki 页面");
        }
        for (JsonElement elem : pages) {
            if (!elem.isJsonObject()) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面格式非法");
            }
            JsonObject page = elem.getAsJsonObject();
            String title = trimToNull(getAsString(page, "title"));
            String pageType = trimToNull(getAsString(page, "page_type"));
            String content = trimToNull(getAsString(page, "content"));
            String summary = getAsString(page, "summary");

            if (title == null || content == null) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面缺少标题或正文");
            }
            if (title.length() > 200) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面标题超过 200 字符");
            }
            if (pageType == null || !VALID_PAGE_TYPES.contains(pageType)) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面类型非法: " + pageType);
            }
            if (summary != null && summary.length() > 500) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面摘要超过 500 字符");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void updatePageFromJson(WikiPage page, JsonObject json, WikiSource source) {
        page.setTitle(getAsString(json, "title"));
        page.setPageType(getAsString(json, "page_type"));
        page.setCategory(getAsString(json, "category"));
        if (page.getCategory() == null) page.setCategory(source.getCategory());
        page.setSoftware(getAsString(json, "software"));
        if (page.getSoftware() == null) page.setSoftware(source.getSoftware());
        page.setVersion(getAsString(json, "version"));
        page.setContent(getAsString(json, "content"));
        page.setSummary(getAsString(json, "summary"));
        page.setStatus("DRAFT");
        page.setCompiledBy("ingest-agent");
        page.setCompiledAt(LocalDateTime.now());
        JsonObject ref = new JsonObject();
        ref.addProperty("title", source.getTitle());
        ref.addProperty("type", source.getSourceType());
        ref.addProperty("id", source.getId());
        page.setSourceRefs(gson.toJson(ref));
    }

    private String buildExistingPagesSummary(String category, String software) {
        List<WikiPage> pages = (category != null || software != null)
                ? pageMapper.findByCategoryOrSoftware(category, software, 20)
                : pageMapper.findAll();
        StringBuilder sb = new StringBuilder();
        for (WikiPage p : pages) {
            sb.append("- ").append(p.getTitle());
            if (p.getSummary() != null) sb.append("：").append(p.getSummary());
            sb.append("\n");
        }
        return sb.length() > 0 ? sb.toString() : "（暂无相关页面）";
    }

    private String buildSoftwareReference() {
        List<SoftwareType> types = softwareTypeMapper.findAllByOrderByCategoryAscNameAsc();
        StringBuilder sb = new StringBuilder();
        String currentCategory = null;
        for (SoftwareType st : types) {
            if (!st.getCategory().equals(currentCategory)) {
                if (currentCategory != null) sb.append("\n");
                sb.append("【").append(st.getCategory()).append("】");
                currentCategory = st.getCategory();
            }
            sb.append(st.getName()).append(" ");
        }
        return sb.length() > 0 ? sb.toString() : "（暂无软件分类数据）";
    }

    public static String sha256(String input) {
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
