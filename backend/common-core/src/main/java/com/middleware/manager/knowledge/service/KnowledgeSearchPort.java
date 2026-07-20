package com.middleware.manager.knowledge.service;

import com.middleware.manager.knowledge.store.VectorSearchFilter;

import java.util.List;

public interface KnowledgeSearchPort {
    List<KnowledgeSearchResult> search(String query, int topK);

    List<KnowledgeSearchResult> search(String query, int topK, VectorSearchFilter filter);
}
