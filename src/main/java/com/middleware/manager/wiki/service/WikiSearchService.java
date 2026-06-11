package com.middleware.manager.wiki.service;

import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WikiSearchService {
    private static final float VECTOR_SCORE_THRESHOLD = 0.5f;

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired(required = false)
    private EmbeddingService embeddingService;

    @Autowired(required = false)
    private WikiPermissionService wikiPermissionService;

    @Value("${app.wiki.search.max-context-pages:8}")
    private int maxContextPages;

    @Value("${app.wiki.search.graph-hop-limit:1}")
    private int graphHopLimit;

    @Value("${app.wiki.search.vector-top-k:5}")
    private int vectorTopK;

    @Value("${app.wiki.search.fulltext-top-k:5}")
    private int fulltextTopK;

    @Value("${app.wiki.search.timeout-ms:3000}")
    private long searchTimeoutMs;

    private ExecutorService searchExecutor;

    public WikiSearchService(WikiPageMapper pageMapper, WikiLinkMapper linkMapper) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
    }

    @PostConstruct
    void init() {
        this.searchExecutor = Executors.newFixedThreadPool(2);
    }

    public static class WikiSearchResult {
        private WikiPage page;
        private float score;
        private String matchSource;  // "vector", "fulltext", "graph"
        private List<String> relatedPageTitles;

        public WikiPage getPage() { return page; }
        public void setPage(WikiPage page) { this.page = page; }
        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
        public String getMatchSource() { return matchSource; }
        public void setMatchSource(String matchSource) { this.matchSource = matchSource; }
        public List<String> getRelatedPageTitles() { return relatedPageTitles; }
        public void setRelatedPageTitles(List<String> relatedPageTitles) { this.relatedPageTitles = relatedPageTitles; }
    }

    public List<WikiSearchResult> search(String query, int topK) {
        return search(query, topK, null);
    }

    public List<WikiSearchResult> search(String query, int topK, Authentication authentication) {
        int activeCount = pageMapper.countByStatus("ACTIVE");
        if (activeCount == 0) {
            return Collections.emptyList();
        }

        // === 阶段 1: 向量 + FULLTEXT 并行 ===
        List<WikiPage> vectorHits = Collections.emptyList();
        List<WikiPage> fulltextHits = Collections.emptyList();

        boolean hasVector = vectorStore != null && embeddingService != null;

        if (hasVector) {
            // 并行执行，使用共享线程池
            CompletableFuture<List<WikiPage>> vectorFuture = CompletableFuture.supplyAsync(
                    () -> vectorSearch(query, vectorTopK), searchExecutor);
            CompletableFuture<List<WikiPage>> fulltextFuture = CompletableFuture.supplyAsync(
                    () -> pageMapper.fulltextSearch(query, fulltextTopK), searchExecutor);

            try {
                fulltextHits = safeGet(fulltextFuture, "fulltext");
                vectorHits = safeGet(vectorFuture, "vector");
            } catch (Exception e) {
                log.warn("Parallel search failed: {}", e.getMessage());
            }
        } else {
            // 无向量服务，仅 FULLTEXT
            fulltextHits = pageMapper.fulltextSearch(query, fulltextTopK);
        }

        // === 合并：向量结果优先，FULLTEXT 补充 ===
        Map<Long, Float> pageScores = new LinkedHashMap<>();
        Map<Long, String> pageSources = new LinkedHashMap<>();
        Map<Long, WikiPage> pageMap = new LinkedHashMap<>();

        // 向量结果优先（分数高）
        for (WikiPage p : vectorHits) {
            pageScores.put(p.getId(), 1.0f);
            pageSources.put(p.getId(), "vector");
            pageMap.put(p.getId(), p);
        }

        // FULLTEXT 结果补充（去重后追加，分数略低）
        for (WikiPage p : fulltextHits) {
            if (!pageScores.containsKey(p.getId())) {
                pageScores.put(p.getId(), 0.9f);
                pageSources.put(p.getId(), "fulltext");
                pageMap.put(p.getId(), p);
            }
        }

        // === 阶段 2: 图扩展（支持多跳） ===
        Set<Long> directHitIds = new LinkedHashSet<>(pageScores.keySet());
        Set<Long> expandedIds = new LinkedHashSet<>(directHitIds);

        Set<Long> currentHop = new LinkedHashSet<>(directHitIds);
        for (int hop = 0; hop < graphHopLimit && !currentHop.isEmpty(); hop++) {
            Set<Long> nextHop = new LinkedHashSet<>();
            for (Long pageId : currentHop) {
                List<WikiLink> links = linkMapper.findAllByPageId(pageId);
                for (WikiLink link : links) {
                    Long relatedId = link.getFromPageId().equals(pageId)
                            ? link.getToPageId() : link.getFromPageId();
                    if (expandedIds.add(relatedId)) {
                        nextHop.add(relatedId);
                    }
                }
            }
            currentHop = nextHop;
        }

        // 批量获取所有页面
        if (!expandedIds.isEmpty()) {
            List<WikiPage> allPages = pageMapper.findByIds(new ArrayList<>(expandedIds));
            for (WikiPage p : allPages) {
                pageMap.putIfAbsent(p.getId(), p);
            }
        }

        // === 阶段 3: 组装结果 ===
        List<WikiSearchResult> results = new ArrayList<>();
        Set<Long> added = new LinkedHashSet<>();

        // 直接命中（向量 + FULLTEXT）
        for (Map.Entry<Long, Float> entry : pageScores.entrySet()) {
            Long pageId = entry.getKey();
            WikiPage page = pageMap.get(pageId);
            if (isVisibleActive(page, authentication) && !added.contains(pageId)) {
                WikiSearchResult sr = new WikiSearchResult();
                sr.setPage(page);
                sr.setScore(entry.getValue());
                sr.setMatchSource(pageSources.get(pageId));
                results.add(sr);
                added.add(pageId);
            }
        }

        // 图扩展页面
        for (Long expandedId : expandedIds) {
            if (!added.contains(expandedId)) {
                WikiPage page = pageMap.get(expandedId);
                if (isVisibleActive(page, authentication)) {
                    WikiSearchResult sr = new WikiSearchResult();
                    sr.setPage(page);
                    sr.setScore(0.5f);
                    sr.setMatchSource("graph");
                    results.add(sr);
                    added.add(expandedId);
                }
            }
        }

        // 为直接命中的页面填充关联页面标题
        for (WikiSearchResult sr : results) {
            if (!"graph".equals(sr.getMatchSource())) {
                List<WikiLink> links = linkMapper.findAllByPageId(sr.getPage().getId());
                List<String> relatedTitles = new ArrayList<>();
                for (WikiLink link : links) {
                    Long relatedId = link.getFromPageId().equals(sr.getPage().getId())
                            ? link.getToPageId() : link.getFromPageId();
                    WikiPage related = pageMap.get(relatedId);
                    if (isVisibleActive(related, authentication)) {
                        relatedTitles.add(related.getTitle());
                    }
                }
                sr.setRelatedPageTitles(relatedTitles);
            }
        }

        results = preferExplicitProductMatches(query, results);

        // 限制结果数：取调用方请求的 topK 和配置上限的较小值
        int limit = Math.min(topK, maxContextPages);
        if (results.size() > limit) {
            results = results.subList(0, limit);
        }

        return results;
    }

    private List<WikiSearchResult> preferExplicitProductMatches(String query, List<WikiSearchResult> results) {
        if (query == null || results.size() <= 1) {
            return results;
        }
        List<String> tokens = extractProductTokens(query);
        if (tokens.isEmpty()) {
            return results;
        }
        List<WikiSearchResult> matched = results.stream()
                .filter(result -> matchesAnyToken(result.getPage(), tokens))
                .toList();
        if (matched.size() >= 2) {
            return new ArrayList<>(matched);
        }
        return results;
    }

    private List<String> extractProductTokens(String query) {
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,}")
                .matcher(query);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (!tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean matchesAnyToken(WikiPage page, List<String> tokens) {
        if (page == null) {
            return false;
        }
        String haystack = String.join(" ",
                valueOrEmpty(page.getTitle()),
                valueOrEmpty(page.getSoftware()),
                valueOrEmpty(page.getCategory()),
                valueOrEmpty(page.getSummary()),
                valueOrEmpty(page.getContent()))
                .toLowerCase(Locale.ROOT);
        return tokens.stream().anyMatch(haystack::contains);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<WikiPage> safeGet(CompletableFuture<List<WikiPage>> future, String source) {
        try {
            return future.get(searchTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            log.warn("{} search timed out after {} ms", source, searchTimeoutMs);
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("{} search failed: {}", source, e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isVisibleActive(WikiPage page, Authentication authentication) {
        if (page == null || !"ACTIVE".equals(page.getStatus())) return false;
        return authentication == null || wikiPermissionService == null || wikiPermissionService.canView(authentication, page);
    }

    private List<WikiPage> vectorSearch(String query, int topK) {
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                float[] queryVec = embeddingService.embed(query);
                log.debug("Vector search (attempt {}): query='{}', dim={}, topK={}", attempt, query, queryVec.length, topK * 2);
                List<VectorStore.VectorSearchResult> vecResults = vectorStore.search(queryVec, topK * 2);
                log.debug("Vector search returned {} results", vecResults.size());
                List<WikiPage> pages = new ArrayList<>();
                for (VectorStore.VectorSearchResult vr : vecResults) {
                    if (vr.getScore() < VECTOR_SCORE_THRESHOLD) break;
                    Map<String, String> meta = vr.getMetadata();
                    if (meta != null && "wiki".equals(meta.get("source"))) {
                        String pageIdStr = meta.get("pageId");
                        if (pageIdStr != null) {
                            Long pageId = Long.parseLong(pageIdStr);
                            WikiPage page = pageMapper.findById(pageId);
                            if (page != null && "ACTIVE".equals(page.getStatus())) {
                                pages.add(page);
                            }
                        }
                    }
                }
                return pages;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    log.warn("Vector search attempt {} failed for query '{}': {}, retrying...", attempt, query, e.getMessage());
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    log.warn("Vector search failed for query '{}' after {} attempts: {}", query, maxRetries, e.getMessage());
                }
            }
        }
        return Collections.emptyList();
    }
}
