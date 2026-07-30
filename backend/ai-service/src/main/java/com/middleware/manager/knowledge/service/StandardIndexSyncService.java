package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.repository.ParameterStandardIndexMapper;
import com.middleware.manager.util.TextUtil;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 参数标准 → 知识库索引的对账同步。
 * <p>standards 属于 core-service、知识库属于 ai-service，两个进程但共用同一个库，
 * 因此这里直读标准表做对账，而不是让发布动作跨服务推送——推送一旦失败，索引会静默
 * 陈旧且无人发现；对账每次都比对状态，本身是自愈的。
 * <p>沿用 wiki_sources 的 content_hash 做增量判断：内容没变就跳过，不重复消耗 embedding。
 */
@Service
@Slf4j
public class StandardIndexSyncService {

    private static final String SOURCE_TYPE = "STANDARD_DOC";

    private final ParameterStandardIndexMapper standardMapper;
    private final WikiSourceMapper sourceMapper;
    private final KnowledgeService knowledgeService;

    public StandardIndexSyncService(ParameterStandardIndexMapper standardMapper,
                                    WikiSourceMapper sourceMapper,
                                    KnowledgeService knowledgeService) {
        this.standardMapper = standardMapper;
        this.sourceMapper = sourceMapper;
        this.knowledgeService = knowledgeService;
    }

    public SyncReport sync() {
        SyncReport report = new SyncReport();
        List<ParameterStandard> published = standardMapper.findPublished();

        Map<String, String> indexedHashes = new HashMap<>();
        for (WikiSource source : sourceMapper.findAllByType(SOURCE_TYPE)) {
            indexedHashes.put(source.getTitle(), source.getContentHash());
        }
        Set<String> stillPublished = new HashSet<>();

        for (ParameterStandard standard : published) {
            stillPublished.add(standard.getTitle());
            if (knowledgeService.removeStandardIfUnindexable(standard)) {
                log.debug("标准未生成可索引切片，已清理历史索引: {}", standard.getTitle());
                report.skipped++;
                continue;
            }
            if (standard.getContent() == null || standard.getContent().isBlank()) {
                log.debug("标准正文为空，跳过索引: {}", standard.getTitle());
                report.skipped++;
                continue;
            }
            if (hashOf(standard).equals(indexedHashes.get(standard.getTitle()))) {
                report.skipped++;
                continue;
            }
            try {
                KnowledgeService.ImportResult result = knowledgeService.indexStandard(standard);
                if (result != null && result.getChunkCount() > 0) {
                    report.indexed++;
                } else {
                    log.debug("标准未生成可索引切片，跳过: {}", standard.getTitle());
                    report.skipped++;
                }
            } catch (Exception e) {
                // 单篇失败不中断整批：下次对账会重试，好过一篇挂掉全部标准都进不去
                log.warn("标准索引失败，将在下次对账重试 title={}: {}", standard.getTitle(), e.getMessage());
                report.failed++;
            }
        }

        // 已撤下发布的标准要从索引移除，否则会检索到已经不作数的内容
        for (String indexedTitle : indexedHashes.keySet()) {
            if (stillPublished.contains(indexedTitle)) {
                continue;
            }
            try {
                knowledgeService.deleteDocument(indexedTitle, SOURCE_TYPE);
                report.removed++;
            } catch (Exception e) {
                log.warn("移除已撤下标准的索引失败 title={}: {}", indexedTitle, e.getMessage());
                report.failed++;
            }
        }

        log.info("标准索引对账完成 已索引={} 跳过={} 已移除={} 失败={}",
                report.indexed, report.skipped, report.removed, report.failed);
        return report;
    }

    String hashOf(ParameterStandard standard) {
        return TextUtil.sha256Hex(standard.getContent() == null ? "" : standard.getContent());
    }

    public static class SyncReport {
        private int indexed;
        private int skipped;
        private int removed;
        private int failed;

        public int getIndexed() { return indexed; }
        public int getSkipped() { return skipped; }
        public int getRemoved() { return removed; }
        public int getFailed() { return failed; }
    }
}
