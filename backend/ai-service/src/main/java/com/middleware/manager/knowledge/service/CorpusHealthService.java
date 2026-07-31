package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.repository.ParameterStandardIndexMapper;
import com.middleware.manager.repository.StandardParameterLookupMapper;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    private final ParameterStandardIndexMapper standardMapper;
    private final StandardParameterLookupMapper parameterMapper;
    private final WikiSourceMapper sourceMapper;

    public CorpusHealthService(ParameterStandardIndexMapper standardMapper,
                               StandardParameterLookupMapper parameterMapper,
                               WikiSourceMapper sourceMapper) {
        this.standardMapper = standardMapper;
        this.parameterMapper = parameterMapper;
        this.sourceMapper = sourceMapper;
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
        List<ParameterStandard> published = safeList(standardMapper.findPublished());

        Set<String> softwares = new LinkedHashSet<>();
        Set<String> covered = new LinkedHashSet<>();
        Map<String, List<String>> byCategory = new TreeMap<>();

        for (ParameterStandard s : published) {
            String software = blankToNull(s.getSoftware());
            if (software == null) {
                continue;
            }
            softwares.add(software);
            String type = inferType(s.getTitle());
            if (type != null) {
                covered.add(software + " / " + type);
            }
            byCategory.computeIfAbsent(
                    s.getCategory() == null ? "未分类" : s.getCategory(), k -> new ArrayList<>()).add(software);
        }

        List<String> missing = new ArrayList<>();
        for (String software : softwares) {
            for (String type : STANDARD_TYPES) {
                String cell = software + " / " + type;
                if (!covered.contains(cell)) {
                    missing.add(cell);
                }
            }
        }

        int totalCells = softwares.size() * STANDARD_TYPES.size();
        report.coveredCells = covered.size();
        report.totalCells = totalCells;
        report.coverage = totalCells == 0 ? 0.0 : (double) covered.size() / totalCells;
        report.missingCells = missing;
        report.softwareByCategory = byCategory;
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

    /** 已入库但未索引的文档：用户在文档列表看得到，检索却命中不了。 */
    private void fillSources(CorpusHealthReport report) {
        List<WikiSource> sources = safeList(sourceMapper.findAll());
        List<String> unindexed = new ArrayList<>();
        for (WikiSource s : sources) {
            if (!Boolean.TRUE.equals(s.getIngested())) {
                unindexed.add(s.getTitle());
            }
        }
        report.totalSources = sources.size();
        report.unindexedSources = unindexed;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
        private List<String> unindexedSources = List.of();

        public int getCoveredCells() { return coveredCells; }
        public int getTotalCells() { return totalCells; }
        public double getCoverage() { return coverage; }
        public List<String> getMissingCells() { return missingCells; }
        public Map<String, List<String>> getSoftwareByCategory() { return softwareByCategory; }
        public List<String> getParameterConflicts() { return parameterConflicts; }
        public int getTotalParameters() { return totalParameters; }
        public int getTotalSources() { return totalSources; }
        public List<String> getUnindexedSources() { return unindexedSources; }
    }
}
