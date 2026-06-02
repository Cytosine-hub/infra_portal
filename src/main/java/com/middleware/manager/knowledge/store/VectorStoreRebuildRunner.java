package com.middleware.manager.knowledge.store;

import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import com.middleware.manager.knowledge.repository.KnowledgeChunkMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时自动重建向量库。
 * 从 MySQL knowledge_chunks 表读取已有切片，重新生成 embedding 写入向量库。
 * 切换向量存储类型（memory/milvus）后，重启即可自动迁移向量数据。
 */
@Component
@Order(10)
public class VectorStoreRebuildRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreRebuildRunner.class);

    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public VectorStoreRebuildRunner(KnowledgeChunkMapper chunkMapper,
                                     EmbeddingService embeddingService,
                                     VectorStore vectorStore) {
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        new Thread(() -> rebuild(), "vector-rebuild").start();
    }

    private void rebuild() {
        try {
            long existingCount = vectorStore.count();
            if (existingCount > 0) {
                log.info("[VectorRebuild] Vector store already has {} entries, skip rebuild.", existingCount);
                return;
            }

            List<KnowledgeChunk> chunks = chunkMapper.findAll();
            if (chunks.isEmpty()) {
                log.info("[VectorRebuild] No chunks in database, skip.");
                return;
            }

            log.info("[VectorRebuild] Found {} chunks, rebuilding vectors...", chunks.size());
            int success = 0;
            int fail = 0;

            for (KnowledgeChunk chunk : chunks) {
                try {
                    float[] vector = embeddingService.embed(chunk.getContent());
                    String vectorId = chunk.getVectorId();
                    if (vectorId == null) {
                        vectorId = java.util.UUID.randomUUID().toString();
                    }

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("content", chunk.getContent());
                    metadata.put("sourceTitle", chunk.getSourceTitle());
                    metadata.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
                    if (chunk.getSourceType() != null) {
                        metadata.put("sourceType", chunk.getSourceType());
                    }
                    if (chunk.getSourceId() != null) {
                        metadata.put("sourceId", String.valueOf(chunk.getSourceId()));
                    }

                    vectorStore.add(vectorId, vector, metadata);
                    success++;
                } catch (Exception e) {
                    fail++;
                    log.warn("[VectorRebuild] Failed to embed chunk {}: {}", chunk.getId(), e.getMessage());
                }
            }

            log.info("[VectorRebuild] Done. success={}, fail={}, total={}", success, fail, chunks.size());
        } catch (Exception e) {
            log.error("[VectorRebuild] Rebuild failed: {}", e.getMessage(), e);
        }
    }
}
