package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.knowledge.splitter.TextSplitter;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.knowledge.store.VectorSearchFilter;
import com.middleware.manager.service.StorageService;
import com.middleware.manager.util.TextUtil;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class KnowledgeService implements KnowledgeSearchPort {

    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final List<DocumentLoader> documentLoaders;
    private final StorageService storageService;
    private final WikiSourceMapper wikiSourceMapper;

    public KnowledgeService(TextSplitter textSplitter,
                            EmbeddingService embeddingService,
                            VectorStore vectorStore,
                            List<DocumentLoader> documentLoaders,
                            StorageService storageService,
                            WikiSourceMapper wikiSourceMapper) {
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.documentLoaders = documentLoaders;
        this.storageService = storageService;
        this.wikiSourceMapper = wikiSourceMapper;
    }

    /**
     * 把一篇已发布的参数标准写入索引。由 StandardIndexSyncService 对账时调用，
     * 不再提供人工「导入」入口——人工导入会产生标准的静态副本，标准更新后必然腐烂。
     */
    @Transactional
    public ImportResult indexStandard(ParameterStandard standard) {
        String sourceTitle = standard.getTitle();
        String content = standard.getContent();
        List<TextSplitter.TextChunk> chunks = textSplitter.split(content, sourceTitle);
        if (chunks.isEmpty()) {
            deleteDocument(sourceTitle, "STANDARD_DOC");
            return emptyImportResult(sourceTitle);
        }
        List<float[]> vectors = embeddingService.embedBatch(chunkTexts(chunks));
        SourceUpsertResult upsert = upsertSource(sourceTitle, "STANDARD_DOC", null, content,
                standard.getCategory(), standard.getSoftware(), null);
        return persistVectors(chunks, vectors, upsert.source().getId(), "STANDARD_DOC",
                standard.getCategory(), standard.getSoftware(), null);
    }

    @Transactional
    public boolean removeStandardIfUnindexable(ParameterStandard standard) {
        List<TextSplitter.TextChunk> chunks = textSplitter.split(standard.getContent(), standard.getTitle());
        if (!chunks.isEmpty()) {
            return false;
        }
        deleteDocument(standard.getTitle(), "STANDARD_DOC");
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        DocumentLoader loader = resolveLoader(fileName);

        byte[] fileBytes = file.getBytes();
        StorageService.StoredFile storedFile = storageService.store(file, "knowledge");
        SourceUpsertResult upsert = null;
        try {
            String content;
            try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
                content = loader.load(is, fileName);
            }

            List<TextSplitter.TextChunk> chunks = textSplitter.split(content, fileName);
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_CONTENT_EMPTY,
                        ErrorMessages.KNOWLEDGE_CONTENT_EMPTY);
            }

            // Embedding 成功后再创建来源记录，避免模型失败留下悬空文档。
            List<float[]> vectors = embeddingService.embedBatch(chunkTexts(chunks));
            upsert = upsertSource(fileName, "UPLOAD", storedFile.storedFileName(), content,
                    null, null, null);
            ImportResult result = persistVectors(chunks, vectors, upsert.source().getId(), "UPLOAD",
                    null, null, storedFile.storedFileName());
            deleteReplacedFileAfterCommit(upsert, storedFile.storedFileName());
            return result;
        } catch (Exception e) {
            compensateFailedImport(storedFile, upsert);
            throw e;
        }
    }

    /** 本模块写入向量时打的来源标记，检索时据此与 wiki 的向量隔离。 */
    private static final String SOURCE_KNOWLEDGE = "knowledge";

    @Override
    public List<KnowledgeSearchResult> search(String query, int topK) {
        return search(query, topK, VectorSearchFilter.none());
    }

    @Override
    public List<KnowledgeSearchResult> search(String query, int topK, VectorSearchFilter filter) {
        VectorSearchFilter safeFilter = filter == null ? VectorSearchFilter.none() : filter;
        // 只召回本模块写入的切片。此前不加此过滤，会串到 wiki 的向量（含未发布草稿）
        VectorSearchFilter scoped = safeFilter.isEmpty()
                ? VectorSearchFilter.none().addSource(SOURCE_KNOWLEDGE)
                : safeFilter.addSource(SOURCE_KNOWLEDGE);

        try {
            float[] queryVector = embeddingService.embed(query);
            // 混合检索：稠密语义 + BM25 精确 token，由 Milvus 原生 RRF 融合。
            // 参数名、错误码这类查询稠密向量召不回，必须靠 BM25 那一路。
            List<VectorStore.VectorSearchResult> hits =
                    vectorStore.hybridSearch(query, queryVector, topK, scoped);
            List<KnowledgeSearchResult> results = new ArrayList<>();
            for (VectorStore.VectorSearchResult hit : hits) {
                results.add(toSearchResult(hit));
            }
            return results;
        } catch (Exception e) {
            log.warn("知识库检索失败 query={}: {}", query, e.getMessage());
            return List.of();
        }
    }

    private KnowledgeSearchResult toSearchResult(VectorStore.VectorSearchResult hit) {
        Map<String, String> meta = hit.getMetadata() == null ? Map.of() : hit.getMetadata();
        KnowledgeSearchResult sr = new KnowledgeSearchResult();
        sr.setContent(meta.get("content"));
        sr.setSourceTitle(meta.get("sourceTitle"));
        sr.setSourceType(meta.get("sourceType"));
        sr.setSourceId(parseLong(meta.get("sourceId")));
        sr.setCategory(meta.get("category"));
        sr.setSoftware(meta.get("software"));
        sr.setSectionPath(meta.get("sectionPath"));
        sr.setScore(hit.getScore());
        // RRF 融合后无法区分单路来源，统一标记为 hybrid
        sr.setSource("hybrid");
        return sr;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DocumentLoader resolveLoader(String fileName) {
        for (DocumentLoader loader : documentLoaders) {
            if (loader.supports(fileName)) return loader;
        }
        throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "不支持的文档格式");
    }

    private ImportResult persistVectors(List<TextSplitter.TextChunk> chunks,
                                        List<float[]> vectors,
                                        Long sourceId,
                                        String sourceType,
                                        String category,
                                        String software,
                                        String storedFileName) {
        List<VectorStore.VectorRecord> records = new ArrayList<>(chunks.size());
        Set<String> retainedIds = new LinkedHashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            TextSplitter.TextChunk chunk = chunks.get(i);
            float[] vector = vectors.get(i);

            String vectorId = vectorId(sourceId, i);
            retainedIds.add(vectorId);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("source", SOURCE_KNOWLEDGE);
            metadata.put("content", chunk.getContent());
            metadata.put("sourceTitle", chunk.getSourceTitle());
            metadata.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
            if (chunk.getSectionPath() != null && !chunk.getSectionPath().isEmpty()) {
                metadata.put("sectionPath", chunk.getSectionPath());
            }
            if (sourceType != null) metadata.put("sourceType", sourceType);
            if (sourceId != null) metadata.put("sourceId", String.valueOf(sourceId));
            if (category != null) metadata.put("category", category);
            if (software != null) metadata.put("software", software);
            if (storedFileName != null) metadata.put("filePath", storedFileName);

            records.add(new VectorStore.VectorRecord(vectorId, vector, metadata));
        }

        vectorStore.addAll(records);
        try {
            vectorStore.deleteBySourceExcept(sourceType, sourceId, retainedIds);
        } catch (Exception cleanupError) {
            // 当前版本已完整写入，保留少量旧切片比回滚数据库后留下新旧来源错位更可控。
            log.warn("清理来源过期向量失败 sourceType={} sourceId={}: {}",
                    sourceType, sourceId, cleanupError.getMessage());
        }

        log.info("Imported {} chunks from source: {}", records.size(),
                chunks.isEmpty() ? "unknown" : chunks.get(0).getSourceTitle());

        ImportResult result = new ImportResult();
        result.setChunkCount(records.size());
        result.setSourceTitle(chunks.isEmpty() ? null : chunks.get(0).getSourceTitle());
        result.setSourceId(sourceId);
        return result;
    }

    public int deleteDocument(String sourceTitle, String sourceType) {
        WikiSource source = wikiSourceMapper.findByTitleAndType(sourceTitle, sourceType);
        if (source != null) {
            deleteSourceVectors(source);
            if (source.getFilePath() != null) {
                try { storageService.deleteIfExists(source.getFilePath()); } catch (Exception ignored) {}
            }
            wikiSourceMapper.deleteById(source.getId());
        }
        return 0;
    }

    public List<Map<String, Object>> listDocuments() {
        List<Map<String, Object>> docs = new ArrayList<>();
        for (WikiSource source : wikiSourceMapper.findAll()) {
            if (!"UPLOAD".equals(source.getSourceType()) && !"STANDARD_DOC".equals(source.getSourceType())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source_title", source.getTitle());
            item.put("source_type", source.getSourceType());
            item.put("source_id", source.getId());
            item.put("chunk_count", previewChunks(source).size());
            item.put("stored_file_name", source.getFilePath());
            docs.add(item);
        }
        return docs;
    }

    public PreviewDocument previewDocument(String title, String sourceType) {
        WikiSource source = requireSource(title, sourceType);
        List<TextSplitter.TextChunk> chunks = previewChunks(source);
        PreviewDocument preview = new PreviewDocument();
        preview.setTitle(source.getTitle());
        preview.setSourceType(source.getSourceType());
        preview.setStoredFileName(source.getFilePath());
        preview.setChunks(chunks);
        return preview;
    }

    public String getSourceFilePath(String title, String sourceType) {
        WikiSource source = requireSource(title, sourceType);
        if (source.getFilePath() == null || source.getFilePath().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到原文件");
        }
        return source.getFilePath();
    }

    private SourceUpsertResult upsertSource(String title, String sourceType, String filePath, String content,
                                            String category, String software, Long createdBy) {
        String hash = TextUtil.sha256Hex(content == null ? "" : content);
        WikiSource source = wikiSourceMapper.findByTitleAndType(title, sourceType);
        if (source == null) {
            source = new WikiSource();
            source.setTitle(title);
            source.setSourceType(sourceType);
            source.setFilePath(filePath);
            source.setContentHash(hash);
            source.setContent(content);
            source.setCategory(category);
            source.setSoftware(software);
            source.setCreatedBy(createdBy);
            wikiSourceMapper.insert(source);
            return new SourceUpsertResult(source, true, null);
        }
        String previousFilePath = source.getFilePath();
        source.setTitle(title);
        source.setSourceType(sourceType);
        if (filePath != null) source.setFilePath(filePath);
        source.setContentHash(hash);
        source.setContent(content);
        if (category != null) source.setCategory(category);
        if (software != null) source.setSoftware(software);
        wikiSourceMapper.update(source);
        return new SourceUpsertResult(source, false, previousFilePath);
    }

    private List<String> chunkTexts(List<TextSplitter.TextChunk> chunks) {
        return chunks.stream().map(TextSplitter.TextChunk::getContent).toList();
    }

    private ImportResult emptyImportResult(String sourceTitle) {
        ImportResult result = new ImportResult();
        result.setSourceTitle(sourceTitle);
        result.setChunkCount(0);
        return result;
    }

    private void compensateFailedImport(StorageService.StoredFile storedFile, SourceUpsertResult upsert) {
        if (upsert != null && upsert.created() && upsert.source().getId() != null) {
            try {
                deleteSourceVectors(upsert.source());
            } catch (Exception cleanupError) {
                log.warn("清理失败上传的向量失败 sourceId={}: {}",
                        upsert.source().getId(), cleanupError.getMessage());
            }
            try {
                wikiSourceMapper.deleteById(upsert.source().getId());
            } catch (Exception cleanupError) {
                log.warn("清理失败上传的来源记录失败 sourceId={}: {}",
                        upsert.source().getId(), cleanupError.getMessage());
            }
        }
        try {
            storageService.deleteIfExists(storedFile.storedFileName());
        } catch (Exception cleanupError) {
            log.warn("清理失败上传的文件失败 path={}: {}",
                    storedFile.storedFileName(), cleanupError.getMessage());
        }
    }

    private void deleteReplacedFileAfterCommit(SourceUpsertResult upsert, String currentFilePath) {
        String previousFilePath = upsert.previousFilePath();
        if (previousFilePath == null || previousFilePath.equals(currentFilePath)) {
            return;
        }
        Runnable cleanup = () -> {
            try {
                storageService.deleteIfExists(previousFilePath);
            } catch (Exception cleanupError) {
                log.warn("清理被替换的旧文件失败 path={}: {}",
                        previousFilePath, cleanupError.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
            return;
        }
        cleanup.run();
    }

    private WikiSource requireSource(String title, String sourceType) {
        WikiSource source = wikiSourceMapper.findByTitleAndType(title, sourceType);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, ErrorMessages.NOT_FOUND);
        }
        return source;
    }

    private List<TextSplitter.TextChunk> previewChunks(WikiSource source) {
        String content = source.getContent();
        if ((content == null || content.isBlank()) && source.getFilePath() != null) {
            content = loadContentFromSourceFile(source);
        }
        return textSplitter.split(content == null ? "" : content, source.getTitle());
    }

    private String loadContentFromSourceFile(WikiSource source) {
        DocumentLoader loader = resolveLoader(source.getTitle());
        try (InputStream is = storageService.loadAsResource(source.getFilePath()).getInputStream()) {
            return loader.load(is, source.getTitle());
        } catch (Exception e) {
            log.warn("Failed to load source file content sourceId={}: {}", source.getId(), e.getMessage());
            return "";
        }
    }

    private void deleteSourceVectors(WikiSource source) {
        vectorStore.deleteBySource(source.getSourceType(), source.getId());
    }

    private String vectorId(Long sourceId, int chunkIndex) {
        return "knowledge_source_" + sourceId + "_" + chunkIndex;
    }

    private record SourceUpsertResult(WikiSource source, boolean created, String previousFilePath) {
    }

    public static class ImportResult {
        private int chunkCount;
        private String sourceTitle;
        private Long sourceId;
        public int getChunkCount() { return chunkCount; }
        public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
        public String getSourceTitle() { return sourceTitle; }
        public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }
        public Long getSourceId() { return sourceId; }
        public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    }

    public static class PreviewDocument {
        private String title;
        private String sourceType;
        private String storedFileName;
        private List<TextSplitter.TextChunk> chunks = Collections.emptyList();

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getStoredFileName() { return storedFileName; }
        public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
        public List<TextSplitter.TextChunk> getChunks() { return chunks; }
        public void setChunks(List<TextSplitter.TextChunk> chunks) { this.chunks = chunks; }
    }

}
