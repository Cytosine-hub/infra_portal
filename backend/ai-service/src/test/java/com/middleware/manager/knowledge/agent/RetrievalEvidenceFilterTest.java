package com.middleware.manager.knowledge.agent;

import com.middleware.manager.knowledge.service.KnowledgeSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalEvidenceFilterTest {

    private final RetrievalEvidenceFilter filter = new RetrievalEvidenceFilter();

    @Test
    @DisplayName("TC-RAG-001 技术标识未出现在上下文时必须拒绝作为可靠证据")
    void rejectsMissingTechnicalIdentifier() {
        RetrievalEvidenceFilter.EvidenceSelection selection = filter.select(
                "ORA-01555 怎么处理", List.of(), List.of(result("MySQL 参数台账", "innodb_buffer_pool_size 建议 70%")));

        assertThat(selection.hasReliableEvidence()).isFalse();
        assertThat(selection.knowledgeResults()).isEmpty();
    }

    @Test
    @DisplayName("TC-RAG-002 技术标识命中时应保留相关上下文")
    void keepsMatchingTechnicalIdentifier() {
        RetrievalEvidenceFilter.EvidenceSelection selection = filter.select(
                "innodb_buffer_pool_size 设置多大", List.of(),
                List.of(result("MySQL 参数台账", "innodb_buffer_pool_size 建议物理内存 70%")));

        assertThat(selection.hasReliableEvidence()).isTrue();
        assertThat(selection.knowledgeResults()).hasSize(1);
    }

    @Test
    @DisplayName("TC-RAG-003 中文关键短语覆盖不足时必须拒绝无关上下文")
    void rejectsWeakChineseOverlap() {
        RetrievalEvidenceFilter.EvidenceSelection selection = filter.select(
                "数据库连接池满了怎么办", List.of(),
                List.of(result("数据库变更", "登录数据库执行备份 SQL")));

        assertThat(selection.hasReliableEvidence()).isFalse();
    }

    @Test
    @DisplayName("TC-RAG-004 中英文关键证据同时命中时应允许语义回答")
    void acceptsRelevantSemanticEvidence() {
        RetrievalEvidenceFilter.EvidenceSelection selection = filter.select(
                "MySQL 主从延迟怎么排查", List.of(),
                List.of(result("MySQL 参数台账", "主从延迟应急处理，执行 SHOW REPLICA STATUS")));

        assertThat(selection.hasReliableEvidence()).isTrue();
    }

    @Test
    @DisplayName("TC-RAG-005 跨文档查询的技术标识可由多个上下文共同覆盖")
    void acceptsTechnicalIdentifiersAcrossDocuments() {
        RetrievalEvidenceFilter.EvidenceSelection selection = filter.select(
                "Nginx 和 F5 的健康检查配置差异", List.of(), List.of(
                        result("Nginx 标准", "Nginx health check 配置"),
                        result("F5 标准", "F5 健康检查配置")));

        assertThat(selection.hasReliableEvidence()).isTrue();
        assertThat(selection.knowledgeResults()).hasSize(2);
    }

    private KnowledgeSearchResult result(String title, String content) {
        KnowledgeSearchResult result = new KnowledgeSearchResult();
        result.setSourceTitle(title);
        result.setSectionPath(title);
        result.setContent(content);
        return result;
    }
}
