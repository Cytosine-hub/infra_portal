package com.middleware.manager.knowledge.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milvus metadata 字段是 VarChar(4096)（字节），而正文也塞在里面。
 * 切片上限提到 900 后，中文按 3 字节/字符计算已逼近上限；超限时 Milvus insert 会失败，
 * 而失败路径只 log.error 且会把 scalarInsertSupported 永久置 false（整进程检索降级）。
 * 因此写入前必须先把 metadata 压进限制内。
 */
class MetadataFittingTest {

    private Map<String, String> metadata(String content) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("source", "knowledge");
        meta.put("content", content);
        meta.put("sourceTitle", "MySQL 8.0 参数标准 V2.3");
        meta.put("sectionPath", "MySQL / 参数标准 / InnoDB 存储引擎 / 缓冲池相关参数");
        meta.put("category", "数据库");
        meta.put("software", "MySQL");
        return meta;
    }

    @Test
    @DisplayName("TC-META-001 未超限的 metadata 应原样保留")
    void keepsMetadataUnderLimitIntact() {
        Map<String, String> meta = metadata("短正文内容");

        Map<String, String> fitted = MilvusVectorStore.fitMetadata(meta, 4096);

        assertThat(fitted.get("content")).isEqualTo("短正文内容");
        assertThat(fitted).containsAllEntriesOf(meta);
    }

    @Test
    @DisplayName("TC-META-002 超限时应截断正文而非丢弃整条切片")
    void truncatesContentInsteadOfLosingChunk() {
        Map<String, String> meta = metadata("中文内容".repeat(500));

        Map<String, String> fitted = MilvusVectorStore.fitMetadata(meta, 4096);

        assertThat(fitted.get("content")).isNotEmpty();
        assertThat(fitted.get("content").length()).isLessThan(2000);
        // 过滤用的标量字段必须完好，否则检索过滤会失效
        assertThat(fitted.get("category")).isEqualTo("数据库");
        assertThat(fitted.get("software")).isEqualTo("MySQL");
        assertThat(fitted.get("sectionPath")).isEqualTo(meta.get("sectionPath"));
    }

    @Test
    @DisplayName("TC-META-003 截断后序列化字节数必须落在限制内")
    void fittedMetadataFitsByteLimit() {
        int limit = 4096;
        Map<String, String> meta = metadata("参数说明".repeat(800));

        Map<String, String> fitted = MilvusVectorStore.fitMetadata(meta, limit);

        int bytes = MilvusVectorStore.serializeMetadata(fitted).getBytes(StandardCharsets.UTF_8).length;
        assertThat(bytes).isLessThanOrEqualTo(limit);
    }

    @Test
    @DisplayName("TC-META-004 正文之外的字段已超限时应保底截断，不得返回超限结果")
    void degradesGracefullyWhenNonContentFieldsAlreadyOverflow() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("content", "正文");
        meta.put("sourceTitle", "超长标题".repeat(500));

        Map<String, String> fitted = MilvusVectorStore.fitMetadata(meta, 4096);

        int bytes = MilvusVectorStore.serializeMetadata(fitted).getBytes(StandardCharsets.UTF_8).length;
        assertThat(bytes).isLessThanOrEqualTo(4096);
    }
}
