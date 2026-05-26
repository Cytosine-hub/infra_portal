package com.middleware.manager.agent.tool;

import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.store.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KnowledgeSearchTool implements Tool {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public KnowledgeSearchTool(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Override
    public String name() { return "knowledge_search"; }

    @Override
    public String description() {
        return "从内部知识库检索相关文档。用于查找排查手册、配置指南、历史案例。参数：query(查询内容), top_k(返回数量，默认5)";
    }

    @Override
    public String call(Map<String, Object> params) {
        String query = String.valueOf(params.get("query"));
        int topK = 5;
        if (params.containsKey("top_k")) {
            Object v = params.get("top_k");
            topK = v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(String.valueOf(v));
        }

        float[] vector = embeddingService.embed(query);
        List<VectorStore.VectorSearchResult> results = vectorStore.search(vector, topK);

        if (results.isEmpty()) {
            return "知识库中未找到相关内容";
        }

        return results.stream()
                .map(r -> "【" + r.getMetadata().getOrDefault("sourceTitle", "未知来源") + "】\n"
                        + r.getMetadata().getOrDefault("content", "")
                        + "\n相关度: " + String.format("%.2f", r.getScore()))
                .collect(Collectors.joining("\n---\n"));
    }
}
