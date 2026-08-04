package com.middleware.manager.knowledge.service;

import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 面向整个知识库的确定性健康检查。
 * <p>检查对象由知识库中实际存在的来源和页面决定，不依赖软件目录、固定标准类型或
 * 其他业务场景。新增来源类型后会自动进入统计与检查范围。</p>
 */
@Service
@Slf4j
public class CorpusHealthService {

    private static final String PAGE_STATUS_ACTIVE = "ACTIVE";
    private static final String PAGE_STATUS_DRAFT = "DRAFT";

    private final WikiSourceMapper sourceMapper;
    private final VectorStore vectorStore;
    private final WikiPageMapper pageMapper;

    public CorpusHealthService(WikiSourceMapper sourceMapper,
                               VectorStore vectorStore,
                               WikiPageMapper pageMapper) {
        this.sourceMapper = sourceMapper;
        this.vectorStore = vectorStore;
        this.pageMapper = pageMapper;
    }

    public CorpusHealthReport report() {
        CorpusHealthReport report = new CorpusHealthReport();
        List<WikiSource> sources = safeList(sourceMapper.findAllForHealth());
        fillSourceHealth(report, sources);
        fillPageHealth(report);
        fillIndexHealth(report, sources);
        report.totalKnowledgeItems = report.totalSources + report.totalPages;
        return report;
    }

    private void fillSourceHealth(CorpusHealthReport report, List<WikiSource> sources) {
        Map<String, Integer> typeCounts = new TreeMap<>();
        Map<String, List<String>> titlesByHash = new LinkedHashMap<>();
        List<String> emptySources = new ArrayList<>();

        for (WikiSource source : sources) {
            typeCounts.merge(normalizeSourceType(source.getSourceType()), 1, Integer::sum);
            String label = sourceLabel(source);
            if (isBlank(source.getContent()) && isBlank(source.getFilePath())) {
                emptySources.add(label);
            }
            if (!isBlank(source.getContentHash())) {
                titlesByHash.computeIfAbsent(source.getContentHash(), ignored -> new ArrayList<>())
                        .add(label);
            }
        }

        List<String> duplicates = titlesByHash.values().stream()
                .filter(titles -> titles.size() > 1)
                .map(titles -> String.join("、", titles))
                .toList();
        report.totalSources = sources.size();
        report.sourceTypeCounts = new LinkedHashMap<>(typeCounts);
        report.emptySources = emptySources;
        report.duplicateContentGroups = duplicates;
    }

    private void fillPageHealth(CorpusHealthReport report) {
        report.totalPages = pageMapper.countAll();
        report.activePages = pageMapper.countByStatus(PAGE_STATUS_ACTIVE);
        report.draftPages = pageMapper.countByStatus(PAGE_STATUS_DRAFT);
    }

    /**
     * 向量库不可用时不输出局部清单，避免把“暂时查不到”误判成“未索引”。
     */
    private void fillIndexHealth(CorpusHealthReport report, List<WikiSource> sources) {
        List<String> unindexed = new ArrayList<>();
        boolean reliable = true;
        for (WikiSource source : sources) {
            try {
                if (source.getId() == null
                        || !vectorStore.existsBySource(source.getSourceType(), source.getId())) {
                    unindexed.add(sourceLabel(source));
                }
            } catch (Exception exception) {
                reliable = false;
                log.warn("查询知识来源索引状态失败 sourceId={} title={}: {}",
                        source.getId(), source.getTitle(), exception.getMessage());
            }
        }

        long indexedChunks = 0L;
        try {
            indexedChunks = vectorStore.count();
        } catch (Exception exception) {
            reliable = false;
            log.warn("查询知识库索引切片总数失败: {}", exception.getMessage());
        }

        report.indexStatusReliable = reliable;
        report.indexedChunks = reliable ? indexedChunks : 0L;
        report.unindexedSources = reliable ? unindexed : List.of();
    }

    private String normalizeSourceType(String sourceType) {
        return isBlank(sourceType) ? "UNKNOWN" : sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private String sourceLabel(WikiSource source) {
        if (!isBlank(source.getTitle())) {
            return source.getTitle();
        }
        return source.getId() == null ? "未命名来源" : "未命名来源 #" + source.getId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public static class CorpusHealthReport {
        private int totalSources;
        private int totalPages;
        private int totalKnowledgeItems;
        private int activePages;
        private int draftPages;
        private Map<String, Integer> sourceTypeCounts = Map.of();
        private List<String> emptySources = List.of();
        private List<String> duplicateContentGroups = List.of();
        private List<String> unindexedSources = List.of();
        private long indexedChunks;
        private boolean indexStatusReliable = true;

        public int getTotalSources() { return totalSources; }
        public int getTotalPages() { return totalPages; }
        public int getTotalKnowledgeItems() { return totalKnowledgeItems; }
        public int getActivePages() { return activePages; }
        public int getDraftPages() { return draftPages; }
        public Map<String, Integer> getSourceTypeCounts() { return sourceTypeCounts; }
        public List<String> getEmptySources() { return emptySources; }
        public List<String> getDuplicateContentGroups() { return duplicateContentGroups; }
        public List<String> getUnindexedSources() { return unindexedSources; }
        public long getIndexedChunks() { return indexedChunks; }
        public boolean isIndexStatusReliable() { return indexStatusReliable; }
    }
}
