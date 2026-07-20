package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.knowledge.loader.StandardDocumentLoader;
import com.middleware.manager.knowledge.repository.KnowledgeChunkMapper;
import com.middleware.manager.knowledge.splitter.TextSplitter;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.knowledge.store.VectorSearchFilter;
import com.middleware.manager.service.StorageService;
import com.middleware.manager.util.TextUtil;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.springframework.stereotype.Service;
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
    private final KnowledgeChunkMapper chunkMapper;
    private final StandardDocumentLoader standardDocumentLoader;
    private final List<DocumentLoader> documentLoaders;
    private final StorageService storageService;
    private final WikiSourceMapper wikiSourceMapper;

    public KnowledgeService(TextSplitter textSplitter,
                            EmbeddingService embeddingService,
                            VectorStore vectorStore,
                            KnowledgeChunkMapper chunkMapper,
                            StandardDocumentLoader standardDocumentLoader,
                            List<DocumentLoader> documentLoaders,
                            StorageService storageService,
                            WikiSourceMapper wikiSourceMapper) {
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.chunkMapper = chunkMapper;
        this.standardDocumentLoader = standardDocumentLoader;
        this.documentLoaders = documentLoaders;
        this.storageService = storageService;
        this.wikiSourceMapper = wikiSourceMapper;
    }

    public ImportResult importFile(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        DocumentLoader loader = resolveLoader(fileName);

        byte[] fileBytes = file.getBytes();
        StorageService.StoredFile storedFile = storageService.store(file, "knowledge");

        String content;
        try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
            content = loader.load(is, fileName);
        }

        String sourceTitle = fileName;
        List<TextSplitter.TextChunk> chunks = textSplitter.split(content, sourceTitle);
        WikiSource source = upsertSource(sourceTitle, "UPLOAD", storedFile.storedFileName(), content, null, null, null);

        return persistVectors(chunks, source.getId(), "UPLOAD", null, null, storedFile.storedFileName());
    }

    public ImportResult importStandardDocument(Long docId) {
        StandardDocument doc = standardDocumentLoader.getStandardDocument(docId);
        String content = doc.getContent();
        String sourceTitle = doc.getTitle();

        List<TextSplitter.TextChunk> chunks = textSplitter.split(content, sourceTitle);
        WikiSource source = upsertSource(sourceTitle, "STANDARD_DOC", null, content, doc.getCategory(), doc.getSoftware(), docId);

        return persistVectors(chunks, source.getId(), "STANDARD_DOC", doc.getCategory(), doc.getSoftware(), null);
    }

    private static final float MIN_SCORE_THRESHOLD = 0.5f;

    @Override
    public List<KnowledgeSearchResult> search(String query, int topK) {
        return search(query, topK, VectorSearchFilter.none());
    }

    @Override
    public List<KnowledgeSearchResult> search(String query, int topK, VectorSearchFilter filter) {
        List<KnowledgeSearchResult> results = new ArrayList<>();
        VectorSearchFilter safeFilter = filter == null ? VectorSearchFilter.none() : filter;

        // 向量检索
        try {
            float[] queryVector = embeddingService.embed(query);
            List<VectorStore.VectorSearchResult> vectorResults = vectorStore.search(queryVector, topK, safeFilter);
            if (!vectorResults.isEmpty() && vectorResults.get(0).getScore() >= MIN_SCORE_THRESHOLD) {
                for (VectorStore.VectorSearchResult vr : vectorResults) {
                    if (vr.getScore() < MIN_SCORE_THRESHOLD) break;
                    Map<String, String> meta = vr.getMetadata();
                    KnowledgeSearchResult sr = new KnowledgeSearchResult();
                    sr.setContent(meta != null ? meta.get("content") : null);
                    sr.setSourceTitle(meta != null ? meta.get("sourceTitle") : null);
                    sr.setSourceType(meta != null ? meta.get("sourceType") : null);
                    sr.setSourceId(parseLong(meta != null ? meta.get("sourceId") : null));
                    sr.setCategory(meta != null ? meta.get("category") : null);
                    sr.setSoftware(meta != null ? meta.get("software") : null);
                    sr.setScore(vr.getScore());
                    sr.setSource("vector");
                    results.add(sr);
                }
            }
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
        }

        // 关键词搜索（补充或降级）
        List<String> terms = buildSearchTerms(query);
        if (terms.isEmpty()) {
            return results;
        }
        List<Integer> weights = buildSearchWeights(terms, query);
        List<KnowledgeChunk> keywordChunks = safeFilter.isEmpty()
                ? chunkMapper.findByTermsWithScore(terms, weights, topK)
                : chunkMapper.findByTermsWithScoreFiltered(terms, weights, topK, safeFilter);
        for (KnowledgeChunk chunk : keywordChunks) {
            boolean duplicate = false;
            for (KnowledgeSearchResult existing : results) {
                if (chunk.getSourceTitle() != null && chunk.getSourceTitle().equals(existing.getSourceTitle())
                        && chunk.getContent() != null && chunk.getContent().equals(existing.getContent())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                KnowledgeSearchResult sr = new KnowledgeSearchResult();
                sr.setContent(chunk.getContent());
                sr.setSourceTitle(chunk.getSourceTitle());
                sr.setSourceType(chunk.getSourceType());
                sr.setSourceId(chunk.getSourceId());
                sr.setCategory(chunk.getCategory());
                sr.setSoftware(chunk.getSoftware());
                sr.setScore(0.8f);
                sr.setSource("keyword");
                results.add(sr);
            }
        }

        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }
        return results;
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

    private List<String> buildSearchTerms(String keyword) {
        List<String> terms = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) return terms;
        keyword = keyword.trim();
        terms.add(keyword);
        char[] chars = keyword.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            String bigram = new String(new char[]{chars[i], chars[i + 1]});
            if (!terms.contains(bigram)) terms.add(bigram);
        }
        for (char c : chars) {
            String s = String.valueOf(c);
            if (!terms.contains(s)) terms.add(s);
        }
        return terms;
    }

    private List<Integer> buildSearchWeights(List<String> terms, String keyword) {
        List<Integer> weights = new ArrayList<>();
        if (terms.isEmpty()) return weights;
        int bigramCount = keyword.trim().length() - 1;
        for (int i = 0; i < terms.size(); i++) {
            if (i == 0) weights.add(100);
            else if (i < 1 + bigramCount) weights.add(10);
            else weights.add(1);
        }
        return weights;
    }

    private DocumentLoader resolveLoader(String fileName) {
        for (DocumentLoader loader : documentLoaders) {
            if (loader.supports(fileName)) return loader;
        }
        throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "不支持的文档格式");
    }

    private ImportResult persistVectors(List<TextSplitter.TextChunk> chunks,
                                        Long sourceId,
                                        String sourceType,
                                        String category,
                                        String software,
                                        String storedFileName) {
        List<String> texts = new ArrayList<>();
        for (TextSplitter.TextChunk chunk : chunks) {
            texts.add(chunk.getContent());
        }

        List<float[]> vectors = embeddingService.embedBatch(texts);

        int count = 0;
        for (int i = 0; i < chunks.size(); i++) {
            TextSplitter.TextChunk chunk = chunks.get(i);
            float[] vector = vectors.get(i);

            String vectorId = vectorId(sourceId, i);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("source", "knowledge");
            metadata.put("content", chunk.getContent());
            metadata.put("sourceTitle", chunk.getSourceTitle());
            metadata.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
            if (sourceType != null) metadata.put("sourceType", sourceType);
            if (sourceId != null) metadata.put("sourceId", String.valueOf(sourceId));
            if (category != null) metadata.put("category", category);
            if (software != null) metadata.put("software", software);
            if (storedFileName != null) metadata.put("filePath", storedFileName);

            try {
                vectorStore.delete(vectorId);
            } catch (Exception e) {
                log.debug("Vector delete before upsert ignored vectorId={}", vectorId);
            }
            vectorStore.add(vectorId, vector, metadata);
            count++;
        }

        log.info("Imported {} chunks from source: {}", count,
                chunks.isEmpty() ? "unknown" : chunks.get(0).getSourceTitle());

        ImportResult result = new ImportResult();
        result.setChunkCount(count);
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
        return chunkMapper.deleteBySourceTitleAndSourceType(sourceTitle, sourceType);
    }

    public int deleteTestDocuments() {
        List<KnowledgeChunk> all = chunkMapper.findAll();
        for (KnowledgeChunk chunk : all) {
            if (chunk.getSourceTitle() != null &&
                (chunk.getSourceTitle().startsWith("test") || chunk.getSourceTitle().startsWith("proxy-test"))) {
                if (chunk.getVectorId() != null) {
                    try { vectorStore.delete(chunk.getVectorId()); } catch (Exception ignored) {}
                }
            }
        }
        int count = chunkMapper.deleteBySourceTitleLike("test%");
        count += chunkMapper.deleteBySourceTitleLike("proxy-test%");
        return count;
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

    private WikiSource upsertSource(String title, String sourceType, String filePath, String content,
                                    String category, String software, Long createdBy) {
        String hash = TextUtil.sha256Hex(content == null ? "" : content);
        WikiSource source = wikiSourceMapper.findByTitleAndType(title, sourceType);
        if (source == null) {
            WikiSource sameContent = wikiSourceMapper.findByContentHash(hash);
            if (sameContent != null && sourceType.equals(sameContent.getSourceType())) {
                source = sameContent;
            }
        }
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
            return source;
        }
        deleteSourceVectors(source);
        source.setTitle(title);
        source.setSourceType(sourceType);
        if (filePath != null) source.setFilePath(filePath);
        source.setContentHash(hash);
        source.setContent(content);
        if (category != null) source.setCategory(category);
        if (software != null) source.setSoftware(software);
        wikiSourceMapper.update(source);
        return source;
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
        int chunkCount = previewChunks(source).size();
        for (int i = 0; i < chunkCount; i++) {
            try { vectorStore.delete(vectorId(source.getId(), i)); } catch (Exception ignored) {}
        }
    }

    private String vectorId(Long sourceId, int chunkIndex) {
        return "knowledge_source_" + sourceId + "_" + chunkIndex;
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
