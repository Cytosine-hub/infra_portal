package com.middleware.manager.agent.tool;

import com.middleware.manager.knowledge.service.KnowledgeService;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.service.WikiSearchService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class KnowledgeSearchTool implements Tool {

    private final KnowledgeService knowledgeService;
    private final WikiSearchService wikiSearchService;

    public KnowledgeSearchTool(KnowledgeService knowledgeService,
                               ObjectProvider<WikiSearchService> wikiSearchServiceProvider) {
        this.knowledgeService = knowledgeService;
        this.wikiSearchService = wikiSearchServiceProvider.getIfAvailable();
    }

    @Override
    public String name() { return "knowledge_search"; }

    @Override
    public String description() {
        return "从 Wiki 和向量知识库混合检索相关内容。用于查找产品介绍、排查手册、配置指南、历史案例。参数：query(查询内容), top_k(返回数量，默认5)";
    }

    @Override
    public String call(Map<String, Object> params) {
        String query = String.valueOf(params.get("query"));
        int topK = 5;
        if (params.containsKey("top_k")) {
            Object v = params.get("top_k");
            topK = v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(String.valueOf(v));
        }

        List<String> blocks = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();

        if (wikiSearchService != null) {
            List<WikiSearchService.WikiSearchResult> wikiResults = wikiSearchService.search(
                    query, topK, ToolContextHolder.getAuthentication());
            for (WikiSearchService.WikiSearchResult result : wikiResults) {
                WikiPage page = result.getPage();
                if (page == null || !dedupe.add("wiki:" + page.getId())) {
                    continue;
                }
                String content = trim(page.getContent(), 1200);
                blocks.add("【Wiki：" + page.getTitle() + "】\n"
                        + "类型: " + valueOrDefault(page.getPageType(), "未知")
                        + "；分类: " + valueOrDefault(page.getCategory(), "未分类")
                        + "；软件: " + valueOrDefault(page.getSoftware(), "未指定")
                        + "\n" + content
                        + "\n相关度: " + String.format("%.2f", result.getScore()));
            }
        }

        List<KnowledgeService.SearchResult> knowledgeResults = knowledgeService.search(query, topK);
        for (KnowledgeService.SearchResult result : knowledgeResults) {
            String key = "doc:" + result.getSourceTitle() + ":" + result.getContent();
            if (!dedupe.add(key)) {
                continue;
            }
            blocks.add("【知识库：" + valueOrDefault(result.getSourceTitle(), "未知来源") + "】\n"
                    + trim(result.getContent(), 1200)
                    + "\n相关度: " + String.format("%.2f", result.getScore()));
        }

        if (blocks.isEmpty()) {
            return "知识库中未找到相关内容";
        }

        return blocks.stream()
                .limit(topK)
                .collect(Collectors.joining("\n---\n"));
    }

    private String trim(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
