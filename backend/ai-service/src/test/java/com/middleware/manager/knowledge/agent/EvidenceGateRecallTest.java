package com.middleware.manager.knowledge.agent;

import com.middleware.manager.knowledge.service.KnowledgeSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证据门禁的「不能误杀」侧。
 * <p>门禁的目的是挡住无证据时的幻觉，但它是词法覆盖判定；而稠密向量检索的全部价值
 * 恰恰在于用户口语表述与文档技术表述**用词不同**时仍能命中。若门禁按字面重合度否决，
 * 等于把语义检索的能力抵消掉——把"幻觉"换成"什么都答不了"，并非改善。
 * <p>下列用例都是「检索召回正确、答案就在上下文里」的场景，必须放行。
 */
class EvidenceGateRecallTest {

    private final RetrievalEvidenceFilter filter = new RetrievalEvidenceFilter();

    private KnowledgeSearchResult result(String title, String content) {
        KnowledgeSearchResult r = new KnowledgeSearchResult();
        r.setSourceTitle(title);
        r.setContent(content);
        return r;
    }

    private boolean passes(String query, KnowledgeSearchResult hit) {
        return filter.select(query, List.of(), List.of(hit)).hasReliableEvidence();
    }

    @Test
    @DisplayName("TC-RAG-101 口语提问命中技术表述的正确切片时应放行")
    void colloquialQueryHittingTechnicalChunk() {
        KnowledgeSearchResult hit =
                result("MySQL参数标准", "innodb_buffer_pool_size 建议设置为物理内存的 70%");

        assertThat(passes("MySQL 缓存应该分配多少物理内存", hit)).isTrue();
    }

    @Test
    @DisplayName("TC-RAG-102 同义表述（主从延迟 / 复制延迟）命中时应放行")
    void synonymPhrasingShouldPass() {
        KnowledgeSearchResult hit = result("MySQL应急处理",
                "主库与备库之间出现复制延迟时，先查看 Seconds_Behind_Master 指标");

        assertThat(passes("备库落后主库很多怎么办", hit)).isTrue();
    }

    @Test
    @DisplayName("TC-RAG-103 跨文档比较类问题不应因只召回其中一方而整体拒答")
    void crossDocumentQueryShouldNotHardFail() {
        KnowledgeSearchResult hit = result("Nginx标准",
                "upstream 健康检查通过 max_fails 与 fail_timeout 配置");

        assertThat(passes("Nginx 和 F5 的健康检查配置差异", hit)).isTrue();
    }
}
