package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.repository.ParameterStandardIndexMapper;
import com.middleware.manager.repository.StandardParameterLookupMapper;
import com.middleware.manager.service.SoftwareTypeLookup;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 语料健康度统计。
 * <p>检索层和生成层的指标只能说明"现有语料检索得好不好"，说明不了"该写的内容写了
 * 没有"。而后者才是真正的瓶颈——首次实测覆盖率只有 2.5%，任何检索优化在这个基数上
 * 都无从体现。
 * <p>本服务算的都是确定性指标，不依赖大模型：
 * <ul>
 *   <li><b>覆盖率矩阵</b>：软件 × 标准类型，哪些格子有已发布标准、哪些空着。
 *       空缺列表可以直接当作内容待办清单</li>
 *   <li><b>参数矛盾</b>：同一参数在不同已发布标准下给出不同取值。这类问题最危险——
 *       两份标准都"生效"，按哪个做都能找到依据</li>
 *   <li><b>未索引来源</b>：文档在库里但检索不到，用户会以为知识库缺内容</li>
 * </ul>
 */
@Service
@Slf4j
public class CorpusHealthService {

    /** 每类软件应当具备的标准类型。空缺即内容待办。 */
    private static final List<String> STANDARD_TYPES = List.of("参数", "部署", "监控", "应急");
    private static final String SOURCE_TYPE_UPLOAD = "UPLOAD";
    private static final String SOURCE_TYPE_STANDARD_DOCUMENT = "STANDARD_DOC";
    private static final String PAGE_TYPE_EXPERIENCE = "EXPERIENCE";
    private static final String PAGE_STATUS_ACTIVE = "ACTIVE";
    private static final String PAGE_STATUS_DRAFT = "DRAFT";

    private final ParameterStandardIndexMapper standardMapper;
    private final StandardParameterLookupMapper parameterMapper;
    private final WikiSourceMapper sourceMapper;
    private final VectorStore vectorStore;
    private final SoftwareTypeLookup softwareTypeLookup;
    private final WikiPageMapper pageMapper;

    public CorpusHealthService(ParameterStandardIndexMapper standardMapper,
                               StandardParameterLookupMapper parameterMapper,
                               WikiSourceMapper sourceMapper,
                               VectorStore vectorStore,
                               SoftwareTypeLookup softwareTypeLookup,
                               WikiPageMapper pageMapper) {
        this.standardMapper = standardMapper;
        this.parameterMapper = parameterMapper;
        this.sourceMapper = sourceMapper;
        this.vectorStore = vectorStore;
        this.softwareTypeLookup = softwareTypeLookup;
        this.pageMapper = pageMapper;
    }

    public CorpusHealthReport report() {
        CorpusHealthReport report = new CorpusHealthReport();
        fillCoverage(report);
        fillParameterConflicts(report);
        fillSources(report);
        return report;
    }

    /** 覆盖率矩阵：已发布标准落在哪些「软件 × 标准类型」格子里。 */
    private void fillCoverage(CorpusHealthReport report) {
        CoverageState state = new CoverageState();
        addCatalogSoftwareTypes(loadCatalogTypes(report), state);
        addPublishedStandards(safeList(standardMapper.findPublished()), state);
        applyCoverage(report, state);
    }

    private List<SoftwareType> loadCatalogTypes(CorpusHealthReport report) {
        try {
            return safeList(softwareTypeLookup.findActive());
        } catch (BusinessException exception) {
            log.warn("查询后台软件分类失败，本次覆盖率仅按已录标准统计 reason={}", exception.getMessage());
            report.catalogStatusReliable = false;
            return List.of();
        }
    }

    private void addCatalogSoftwareTypes(List<SoftwareType> catalogTypes, CoverageState state) {
        for (SoftwareType type : catalogTypes) {
            String software = blankToNull(type.getName());
            if (software == null) {
                continue;
            }
            String category = categoryOrDefault(type.getCategory());
            String scoped = scope(category, software);
            String normalized = normalizeScope(scoped);
            if (state.catalogScopeByNormalized.putIfAbsent(normalized, scoped) == null) {
                state.denominator.add(scoped);
                state.softwareSetsByCategory
                        .computeIfAbsent(category, key -> new LinkedHashSet<>()).add(software);
            }
        }
    }

    private void addPublishedStandards(List<ParameterStandard> published, CoverageState state) {
        for (ParameterStandard s : published) {
            String software = blankToNull(s.getSoftware());
            if (software == null) {
                continue;
            }
            // 格子键带上分类：中间件:Nginx 与 应用:Nginx 是两套标准，不能塌缩成一格
            String category = categoryOrDefault(s.getCategory());
            String rawScope = scope(category, software);
            String scoped = state.catalogScopeByNormalized.getOrDefault(normalizeScope(rawScope), rawScope);
            state.denominator.add(scoped);
            addScopeToCategory(state.softwareSetsByCategory, scoped);
            String type = inferType(s.getTitle());
            if (type != null) {
                state.covered.add(scoped + " / " + type);
            } else {
                // 标题识别不出类型的标准既不计入 covered、其软件又进分母，会系统性
                // 低估覆盖率。单列出来，让人知道是「归类不了」而不是「没写」。
                state.unclassified.add(scoped + " -> " + s.getTitle());
            }
        }
    }

    private void applyCoverage(CorpusHealthReport report, CoverageState state) {
        // 分母 = 后台启用的软件类型 ∪ 已录入标准的软件。
        // 取并集而非只用清单：清单外但确实录了标准的软件也应计入，否则会漏掉真实语料。
        List<String> missing = new ArrayList<>();
        for (String software : state.denominator) {
            for (String type : STANDARD_TYPES) {
                String cell = software + " / " + type;
                if (!state.covered.contains(cell)) {
                    missing.add(cell);
                }
            }
        }

        int totalCells = state.denominator.size() * STANDARD_TYPES.size();
        report.coveredCells = state.covered.size();
        report.totalCells = totalCells;
        report.coverage = totalCells == 0 ? 0.0 : (double) state.covered.size() / totalCells;
        report.missingCells = missing;
        Map<String, List<String>> softwareByCategory = new TreeMap<>();
        state.softwareSetsByCategory.forEach((category, softwares) ->
                softwareByCategory.put(category, new ArrayList<>(softwares)));
        report.softwareByCategory = softwareByCategory;
        report.unclassifiedStandards = state.unclassified;
        report.catalogSoftwareCount = state.catalogScopeByNormalized.size();
        report.targetCatalogConfigured = !state.catalogScopeByNormalized.isEmpty();
        report.coverageHint = !report.catalogStatusReliable
                ? "后台软件分类查询失败，本次分母仅来自已录入标准的软件，覆盖率结论不完整。"
                : state.catalogScopeByNormalized.isEmpty()
                ? "后台管理未配置启用的软件类型，当前分母仅来自已录入标准的软件，无法反映整套语料空白。"
                : "分母来自后台管理中已启用的软件类型与已录入标准的并集。";
    }

    /** 标题里带「参数 / 部署 / 监控 / 应急」即归入对应类型，识别不出的不计入覆盖。 */
    private String inferType(String title) {
        if (title == null) {
            return null;
        }
        for (String type : STANDARD_TYPES) {
            if (title.contains(type)) {
                return type;
            }
        }
        return null;
    }

    /** 同一软件下同名参数在不同已发布标准中取值不一致 —— 两份标准都生效，按哪个做都有依据。 */
    private void fillParameterConflicts(CorpusHealthReport report) {
        List<ParameterAnswerRow> rows = safeList(parameterMapper.search(null, null, Integer.MAX_VALUE));

        Map<String, Map<String, Set<String>>> valuesByParam = new LinkedHashMap<>();
        for (ParameterAnswerRow row : rows) {
            String key = (row.getSoftware() == null ? "未标注" : row.getSoftware()) + " / " + row.getCode();
            valuesByParam
                    .computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getValue() == null ? "" : row.getValue(), k -> new LinkedHashSet<>())
                    .add(row.getStandardTitle() == null ? "未知标准" : row.getStandardTitle());
        }

        List<String> conflicts = new ArrayList<>();
        valuesByParam.forEach((param, byValue) -> {
            if (byValue.size() <= 1) {
                return;
            }
            List<String> parts = new ArrayList<>();
            byValue.forEach((value, standards) -> parts.add(value + "（" + String.join("、", standards) + "）"));
            conflicts.add(param + " 取值不一致：" + String.join(" vs ", parts));
        });
        report.parameterConflicts = conflicts;
        report.totalParameters = rows.size();
    }

    /**
     * 已入库但检索不到的文档。
     * <p>判定以**向量是否存在**为准，不能用 wiki_sources.ingested——那个字段表示
     * Wiki 编译状态，上传类文档从不参与编译、该字段恒为 false，用它判断会把已经
     * 可检索的文档全部误报为未索引。
     * <p>向量库不可用时降级为「不下结论」：宁可不报，也不能把全部文档误报成未索引。
     */
    private void fillSources(CorpusHealthReport report) {
        List<WikiSource> sources = safeList(sourceMapper.findAll());
        fillDocumentComposition(report, sources);
        fillExperienceComposition(report);
        fillIndexStatus(report, sources);
        report.totalKnowledgeItems = report.totalSources + report.experiencePages;
    }

    private void fillDocumentComposition(CorpusHealthReport report, List<WikiSource> sources) {
        int uploadedDocuments = 0;
        int standardDocuments = 0;
        int otherDocuments = 0;

        for (WikiSource source : sources) {
            if (SOURCE_TYPE_UPLOAD.equalsIgnoreCase(source.getSourceType())) {
                uploadedDocuments++;
            } else if (SOURCE_TYPE_STANDARD_DOCUMENT.equalsIgnoreCase(source.getSourceType())) {
                standardDocuments++;
            } else {
                otherDocuments++;
            }
        }
        report.totalSources = sources.size();
        report.uploadedDocuments = uploadedDocuments;
        report.standardDocuments = standardDocuments;
        report.otherDocuments = otherDocuments;
    }

    private void fillExperienceComposition(CorpusHealthReport report) {
        report.experiencePages = pageMapper.countByPageType(PAGE_TYPE_EXPERIENCE);
        report.activeExperiencePages =
                pageMapper.countByPageTypeAndStatus(PAGE_TYPE_EXPERIENCE, PAGE_STATUS_ACTIVE);
        report.draftExperiencePages =
                pageMapper.countByPageTypeAndStatus(PAGE_TYPE_EXPERIENCE, PAGE_STATUS_DRAFT);
    }

    private void fillIndexStatus(CorpusHealthReport report, List<WikiSource> sources) {
        List<String> unindexed = new ArrayList<>();
        boolean reliable = true;
        for (WikiSource s : sources) {
            try {
                if (!vectorStore.existsBySource(s.getSourceType(), s.getId())) {
                    unindexed.add(s.getTitle());
                }
            } catch (Exception e) {
                log.warn("查询来源 {} 的索引状态失败，本次不下结论: {}", s.getTitle(), e.getMessage());
                reliable = false;
            }
        }
        long indexedChunks = 0L;
        try {
            // 切片总量取集合总数：逐源累加既受条数上限截断，也没有额外信息量
            indexedChunks = vectorStore.count();
        } catch (Exception e) {
            log.warn("查询索引切片总数失败: {}", e.getMessage());
            reliable = false;
        }

        // 判定不可信时连切片总数也不输出，避免局部数字被当成完整结论
        report.indexedChunks = reliable ? indexedChunks : 0L;
        report.indexStatusReliable = reliable;
        // 判定不可信时不输出清单，避免把「查不到」误读成「没索引」
        report.unindexedSources = reliable ? unindexed : List.of();
    }

    private void addScopeToCategory(Map<String, LinkedHashSet<String>> byCategory, String scoped) {
        int separator = scoped.indexOf(':');
        String category = scoped.substring(0, separator);
        String software = scoped.substring(separator + 1);
        byCategory.computeIfAbsent(category, key -> new LinkedHashSet<>()).add(software);
    }

    private String scope(String category, String software) {
        return category + ":" + software.trim();
    }

    private String normalizeScope(String scoped) {
        return scoped.toLowerCase(Locale.ROOT);
    }

    private String categoryOrDefault(String category) {
        String value = blankToNull(category);
        return value == null ? "未分类" : value.trim();
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static class CoverageState {
        private final Set<String> covered = new LinkedHashSet<>();
        private final Map<String, LinkedHashSet<String>> softwareSetsByCategory = new TreeMap<>();
        private final Map<String, String> catalogScopeByNormalized = new LinkedHashMap<>();
        private final Set<String> denominator = new LinkedHashSet<>();
        private final List<String> unclassified = new ArrayList<>();
    }

    public static class CorpusHealthReport {
        private int coveredCells;
        private int totalCells;
        private double coverage;
        private List<String> missingCells = List.of();
        private Map<String, List<String>> softwareByCategory = Map.of();
        private List<String> parameterConflicts = List.of();
        private int totalParameters;
        private int totalSources;
        private int totalKnowledgeItems;
        private int catalogSoftwareCount;
        private int uploadedDocuments;
        private int standardDocuments;
        private int otherDocuments;
        private int experiencePages;
        private int activeExperiencePages;
        private int draftExperiencePages;
        private List<String> unindexedSources = List.of();
        private long indexedChunks;
        private boolean indexStatusReliable = true;
        private boolean catalogStatusReliable = true;
        private boolean targetCatalogConfigured;
        private String coverageHint = "";
        private List<String> unclassifiedStandards = List.of();

        public int getCoveredCells() { return coveredCells; }
        public int getTotalCells() { return totalCells; }
        public double getCoverage() { return coverage; }
        public List<String> getMissingCells() { return missingCells; }
        public Map<String, List<String>> getSoftwareByCategory() { return softwareByCategory; }
        public List<String> getParameterConflicts() { return parameterConflicts; }
        public int getTotalParameters() { return totalParameters; }
        public int getTotalSources() { return totalSources; }
        public int getTotalKnowledgeItems() { return totalKnowledgeItems; }
        public int getCatalogSoftwareCount() { return catalogSoftwareCount; }
        public int getUploadedDocuments() { return uploadedDocuments; }
        public int getStandardDocuments() { return standardDocuments; }
        public int getOtherDocuments() { return otherDocuments; }
        public int getExperiencePages() { return experiencePages; }
        public int getActiveExperiencePages() { return activeExperiencePages; }
        public int getDraftExperiencePages() { return draftExperiencePages; }
        public List<String> getUnindexedSources() { return unindexedSources; }
        public long getIndexedChunks() { return indexedChunks; }
        public boolean isIndexStatusReliable() { return indexStatusReliable; }
        public boolean isCatalogStatusReliable() { return catalogStatusReliable; }
        public boolean isTargetCatalogConfigured() { return targetCatalogConfigured; }
        public String getCoverageHint() { return coverageHint; }
        public List<String> getUnclassifiedStandards() { return unclassifiedStandards; }
    }
}
