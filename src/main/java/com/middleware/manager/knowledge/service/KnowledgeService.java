package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.knowledge.loader.StandardDocumentLoader;
import com.middleware.manager.knowledge.repository.KnowledgeChunkMapper;
import com.middleware.manager.knowledge.splitter.TextSplitter;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final KnowledgeChunkMapper chunkMapper;
    private final StandardDocumentLoader standardDocumentLoader;
    private final List<DocumentLoader> documentLoaders;
    private final StorageService storageService;

    public KnowledgeService(TextSplitter textSplitter,
                            EmbeddingService embeddingService,
                            VectorStore vectorStore,
                            KnowledgeChunkMapper chunkMapper,
                            StandardDocumentLoader standardDocumentLoader,
                            List<DocumentLoader> documentLoaders,
                            StorageService storageService) {
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.chunkMapper = chunkMapper;
        this.standardDocumentLoader = standardDocumentLoader;
        this.documentLoaders = documentLoaders;
        this.storageService = storageService;
    }

    public ImportResult importFile(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        DocumentLoader loader = resolveLoader(fileName);

        byte[] fileBytes = file.getBytes();

        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0) extension = fileName.substring(lastDot);
        String storedFileName = "knowledge/" + UUID.randomUUID() + extension;
        Path dest = storageService.getRootLocation().resolve(storedFileName);
        Files.createDirectories(dest.getParent());
        Files.write(dest, fileBytes);

        String content;
        try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
            content = loader.load(is, fileName);
        }

        String sourceTitle = fileName;
        List<TextSplitter.TextChunk> chunks = textSplitter.split(content, sourceTitle);

        return persistChunks(chunks, null, "UPLOAD", null, null, storedFileName);
    }

    public ImportResult importStandardDocument(Long docId) {
        StandardDocument doc = standardDocumentLoader.getStandardDocument(docId);
        String content = doc.getContent();
        String sourceTitle = doc.getTitle();

        List<TextSplitter.TextChunk> chunks = textSplitter.split(content, sourceTitle);

        return persistChunks(chunks, docId, "STANDARD_DOC", doc.getCategory(), doc.getSoftware(), null);
    }

    private static final float MIN_SCORE_THRESHOLD = 0.5f;

    public List<SearchResult> search(String query, int topK) {
        List<SearchResult> results = new ArrayList<>();

        // 向量检索
        try {
            float[] queryVector = embeddingService.embed(query);
            List<VectorStore.VectorSearchResult> vectorResults = vectorStore.search(queryVector, topK);
            if (!vectorResults.isEmpty() && vectorResults.get(0).getScore() >= MIN_SCORE_THRESHOLD) {
                for (VectorStore.VectorSearchResult vr : vectorResults) {
                    if (vr.getScore() < MIN_SCORE_THRESHOLD) break;
                    Map<String, String> meta = vr.getMetadata();
                    SearchResult sr = new SearchResult();
                    sr.setContent(meta != null ? meta.get("content") : null);
                    sr.setSourceTitle(meta != null ? meta.get("sourceTitle") : null);
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
        List<Integer> weights = buildSearchWeights(terms, query);
        List<KnowledgeChunk> keywordChunks = chunkMapper.findByTermsWithScore(terms, weights, topK);
        for (KnowledgeChunk chunk : keywordChunks) {
            boolean duplicate = false;
            for (SearchResult existing : results) {
                if (chunk.getSourceTitle() != null && chunk.getSourceTitle().equals(existing.getSourceTitle())
                        && chunk.getContent() != null && chunk.getContent().equals(existing.getContent())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                SearchResult sr = new SearchResult();
                sr.setContent(chunk.getContent());
                sr.setSourceTitle(chunk.getSourceTitle());
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
        throw new IllegalArgumentException("No document loader found for file: " + fileName);
    }

    private ImportResult persistChunks(List<TextSplitter.TextChunk> chunks,
                                       Long sourceId,
                                       String sourceType,
                                       String category,
                                       String software,
                                       String storedFileName) {
        if (sourceId != null) {
            deleteBySource(sourceId, sourceType);
        }

        List<String> texts = new ArrayList<>();
        for (TextSplitter.TextChunk chunk : chunks) {
            texts.add(chunk.getContent());
        }

        List<float[]> vectors = embeddingService.embedBatch(texts);

        int count = 0;
        for (int i = 0; i < chunks.size(); i++) {
            TextSplitter.TextChunk chunk = chunks.get(i);
            float[] vector = vectors.get(i);

            String vectorId = UUID.randomUUID().toString();

            Map<String, String> metadata = new HashMap<>();
            metadata.put("content", chunk.getContent());
            metadata.put("sourceTitle", chunk.getSourceTitle());
            metadata.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
            if (sourceType != null) metadata.put("sourceType", sourceType);
            if (sourceId != null) metadata.put("sourceId", String.valueOf(sourceId));
            vectorStore.add(vectorId, vector, metadata);

            KnowledgeChunk entity = new KnowledgeChunk();
            entity.setContent(chunk.getContent());
            entity.setSourceTitle(chunk.getSourceTitle());
            entity.setSourceType(sourceType);
            entity.setSourceId(sourceId);
            entity.setCategory(category);
            entity.setSoftware(software);
            entity.setChunkIndex(chunk.getChunkIndex());
            entity.setVectorId(vectorId);
            entity.setStoredFileName(storedFileName);
            entity.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(entity);

            count++;
        }

        log.info("Imported {} chunks from source: {}", count,
                chunks.isEmpty() ? "unknown" : chunks.get(0).getSourceTitle());

        ImportResult result = new ImportResult();
        result.setChunkCount(count);
        result.setSourceTitle(chunks.isEmpty() ? null : chunks.get(0).getSourceTitle());
        return result;
    }

    public int deleteDocument(String sourceTitle, String sourceType) {
        List<KnowledgeChunk> chunks = chunkMapper.findBySourceTitleContaining(sourceTitle);
        String storedFileToDelete = null;
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getVectorId() != null) {
                try { vectorStore.delete(chunk.getVectorId()); } catch (Exception ignored) {}
            }
            if (chunk.getStoredFileName() != null && storedFileToDelete == null) {
                storedFileToDelete = chunk.getStoredFileName();
            }
        }
        if (storedFileToDelete != null) {
            try { storageService.deleteIfExists(storedFileToDelete); } catch (Exception ignored) {}
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

    private void deleteBySource(Long sourceId, String sourceType) {
        chunkMapper.deleteBySourceIdAndSourceType(sourceId, sourceType);
    }

    public static class ImportResult {
        private int chunkCount;
        private String sourceTitle;
        public int getChunkCount() { return chunkCount; }
        public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
        public String getSourceTitle() { return sourceTitle; }
        public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }
    }

    public static class SearchResult {
        private String content;
        private String sourceTitle;
        private float score;
        private String source;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSourceTitle() { return sourceTitle; }
        public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }
        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
}
