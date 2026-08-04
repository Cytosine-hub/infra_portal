package com.middleware.manager.knowledge.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milvus 2.5 schema 的边界保障。
 * <p>升级前正文塞在 metadata JSON 里受 VarChar(4096) 限制，切片一大就 insert 失败、
 * 静默丢失，还会永久关闭标量过滤。升级后正文移到独立的 text 列（65535，同时是 BM25
 * 的输入），metadata 只放过滤用的轻量字段——这里守住这个结构性前提不被改回去。
 */
class MetadataFittingTest {

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    @Test
    @DisplayName("TC-META-001 轻量 metadata 应原样序列化")
    void keepsLightMetadataIntact() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("sourceTitle", "MySQL 8.0 参数标准");
        meta.put("sectionPath", "MySQL / 参数标准 / InnoDB");
        meta.put("category", "数据库");

        String json = MilvusVectorStore.fitMetadata(meta);

        assertThat(json).contains("MySQL 8.0 参数标准");
        assertThat(json).contains("MySQL / 参数标准 / InnoDB");
        assertThat(bytes(json)).isLessThanOrEqualTo(4096);
    }

    @Test
    @DisplayName("TC-META-002 metadata 极端超限时应裁剪而非产出超限字符串")
    void trimsOversizedMetadata() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("sourceTitle", "超长标题".repeat(400));
        meta.put("sectionPath", "很长的章节路径".repeat(400));

        String json = MilvusVectorStore.fitMetadata(meta);

        assertThat(bytes(json)).isLessThanOrEqualTo(4096);
    }

    @Test
    @DisplayName("TC-META-003 截断应按 UTF-8 字节计算，中文一字三字节不得撑爆列")
    void truncatesByUtf8Bytes() {
        String chinese = "参数".repeat(500);

        String truncated = MilvusVectorStore.truncate(chinese, 200);

        assertThat(bytes(truncated)).isLessThanOrEqualTo(200);
        assertThat(truncated).startsWith("参数");
    }

    @Test
    @DisplayName("TC-META-004 未超限的内容不应被截断")
    void keepsShortValueIntact() {
        assertThat(MilvusVectorStore.truncate("短内容", 200)).isEqualTo("短内容");
        assertThat(MilvusVectorStore.truncate(null, 200)).isEmpty();
    }

    @Test
    @DisplayName("TC-META-005 过滤条件应下推为 Milvus 标量表达式，而非召回后内存过滤")
    void buildsScalarFilterExpression() {
        VectorSearchFilter filter = VectorSearchFilter.none()
                .addSource("knowledge")
                .addCategory("数据库");

        String expr = MilvusVectorStore.buildExpr(filter);

        assertThat(expr).contains("source in [\"knowledge\"]");
        assertThat(expr).contains("category in [\"数据库\"]");
        assertThat(expr).contains(" and ");
    }

    @Test
    @DisplayName("TC-META-006 空过滤器应产出空表达式，不得拼出恒真条件")
    void emptyFilterProducesNoExpression() {
        assertThat(MilvusVectorStore.buildExpr(VectorSearchFilter.none())).isEmpty();
        assertThat(MilvusVectorStore.buildExpr(null)).isEmpty();
    }

    @Test
    @DisplayName("TC-META-007 过滤值中的引号应被转义，避免表达式注入")
    void escapesQuotesInFilterValues() {
        VectorSearchFilter filter = VectorSearchFilter.none().addSoftware("My\"SQL");

        String expr = MilvusVectorStore.buildExpr(filter);

        assertThat(expr).contains("\\\"");
    }
}
