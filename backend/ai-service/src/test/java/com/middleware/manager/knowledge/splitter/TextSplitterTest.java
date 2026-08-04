package com.middleware.manager.knowledge.splitter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 切片层结构穿透测试。
 * <p>解析层已把 PDF/Word/Excel 统一成结构化 Markdown，切片层必须把标题层级重建成
 * sectionPath 并让每个切片都带上，否则解析层的成果会在这里被丢弃。
 */
class TextSplitterTest {

    @Nested
    @DisplayName("sectionPath 重建")
    class SectionPathBuilding {

        @Test
        @DisplayName("TC-SPLIT-001 应按 Markdown 标题层级构建 sectionPath")
        void buildsSectionPathFromHeadingHierarchy() {
            String text = """
                    # MySQL
                    数据库总览。

                    ## 应急处理
                    应急总述。

                    ### 主从延迟
                    先看 Seconds_Behind_Master。
                    """;

            List<TextSplitter.TextChunk> chunks = new TextSplitter().split(text, "MySQL标准");

            assertThat(chunks).isNotEmpty();
            assertThat(chunks).anySatisfy(c ->
                    assertThat(c.getSectionPath()).isEqualTo("MySQL / 应急处理 / 主从延迟"));
        }

        @Test
        @DisplayName("TC-SPLIT-002 同级标题切换时 sectionPath 应正确回退而非累积")
        void siblingHeadingResetsPath() {
            String text = """
                    # Redis
                    ## 参数标准
                    maxmemory 配置说明。

                    ## 监控标准
                    内存使用率告警阈值。
                    """;

            List<TextSplitter.TextChunk> chunks = new TextSplitter().split(text, "Redis标准");

            assertThat(chunks).anySatisfy(c ->
                    assertThat(c.getSectionPath()).isEqualTo("Redis / 监控标准"));
            assertThat(chunks).noneSatisfy(c ->
                    assertThat(c.getSectionPath()).contains("参数标准 / 监控标准"));
        }

        @Test
        @DisplayName("TC-SPLIT-003 无标题文档应正常切片且 sectionPath 为空")
        void plainTextHasEmptySectionPath() {
            List<TextSplitter.TextChunk> chunks =
                    new TextSplitter().split("一段没有任何标题的纯文本内容。", "纯文本");

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getSectionPath()).isEmpty();
            assertThat(chunks.get(0).getContent()).contains("没有任何标题");
        }

        @Test
        @DisplayName("TC-SPLIT-004 sectionPath 应作为面包屑前缀写入切片正文，供检索命中")
        void sectionPathIsPrefixedIntoContent() {
            String text = """
                    # PostgreSQL
                    ## 应急处理
                    ### 连接数耗尽
                    调整 max_connections。
                    """;

            List<TextSplitter.TextChunk> chunks = new TextSplitter().split(text, "PG标准");

            assertThat(chunks).anySatisfy(c -> {
                assertThat(c.getContent()).startsWith("PostgreSQL / 应急处理 / 连接数耗尽");
                assertThat(c.getContent()).contains("max_connections");
            });
        }
    }

    @Nested
    @DisplayName("大段落拆分")
    class LargeSectionSplitting {

        private String longSection(String heading, int lines) {
            StringBuilder sb = new StringBuilder(heading).append("\n");
            for (int i = 0; i < lines; i++) {
                sb.append("这是第").append(i).append("行内容，用于把本节撑到超过单个切片上限。\n");
            }
            return sb.toString();
        }

        @Test
        @DisplayName("TC-SPLIT-005 大段落拆分后每个子块都应带 sectionPath（此前只有首块带）")
        void everySubChunkKeepsSectionPath() {
            String text = "# Oracle\n## 参数标准\n" + longSection("### 内存参数", 60);

            List<TextSplitter.TextChunk> chunks = new TextSplitter(400, 60).split(text, "Oracle标准");

            List<TextSplitter.TextChunk> memoryChunks = chunks.stream()
                    .filter(c -> "Oracle / 参数标准 / 内存参数".equals(c.getSectionPath()))
                    .toList();

            assertThat(memoryChunks).hasSizeGreaterThan(1);
            assertThat(memoryChunks).allSatisfy(c ->
                    assertThat(c.getContent()).startsWith("Oracle / 参数标准 / 内存参数"));
        }

        @Test
        @DisplayName("TC-SPLIT-006 相邻子块之间应有 overlap，避免切断处语义割裂")
        void adjacentSubChunksOverlap() {
            String text = "# Kafka\n## 参数标准\n" + longSection("### 副本参数", 60);

            List<TextSplitter.TextChunk> chunks = new TextSplitter(400, 80).split(text, "Kafka标准");
            List<TextSplitter.TextChunk> sub = chunks.stream()
                    .filter(c -> "Kafka / 参数标准 / 副本参数".equals(c.getSectionPath()))
                    .toList();

            assertThat(sub).hasSizeGreaterThan(1);
            String first = sub.get(0).getContent();
            String second = sub.get(1).getContent();
            String tail = first.substring(Math.max(0, first.length() - 40));
            assertThat(second).contains(tail.strip().split("\n")[0].strip());
        }
    }

    @Nested
    @DisplayName("边界与健壮性")
    class Robustness {

        @Test
        @DisplayName("TC-SPLIT-009 代码围栏内的 # 注释不得被当作标题")
        void fencedCodeCommentsAreNotHeadings() {
            String text = """
                    # 主机标准
                    ## 内核参数
                    调整步骤如下：

                    ```bash
                    # 关闭 swap
                    swapoff -a
                    # 调整脏页比例
                    sysctl -w vm.dirty_ratio=10
                    ```

                    调整完成后需重启验证。
                    """;

            List<TextSplitter.TextChunk> chunks = new TextSplitter().split(text, "主机标准");

            String all = chunks.stream().map(TextSplitter.TextChunk::getContent)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(all).contains("# 关闭 swap");
            assertThat(all).contains("swapoff -a");
            assertThat(chunks).allSatisfy(c ->
                    assertThat(c.getSectionPath()).doesNotContain("关闭 swap"));
        }

        @Test
        @DisplayName("TC-SPLIT-010 切片正文长度不得超过上限（面包屑前缀必须计入预算）")
        void chunkNeverExceedsMaxSize() {
            StringBuilder sb = new StringBuilder("# 数据库标准\n## 参数标准\n### 一个相当长的章节标题用来占用面包屑预算\n");
            for (int i = 0; i < 200; i++) {
                sb.append("这是第").append(i).append("行正文，用来把本节撑得远超单个切片上限。\n");
            }

            int max = 400;
            List<TextSplitter.TextChunk> chunks = new TextSplitter(max, 80).split(sb.toString(), "DB标准");

            assertThat(chunks).isNotEmpty();
            assertThat(chunks).allSatisfy(c ->
                    assertThat(c.getContent().length())
                            .as("切片 %d 超长: %d > %d", c.getChunkIndex(), c.getContent().length(), max)
                            .isLessThanOrEqualTo(max));
        }

        @Test
        @DisplayName("TC-SPLIT-011 非法的 maxChunkSize 应被拒绝而非导致死循环")
        void rejectsNonPositiveChunkSize() {
            assertThatThrownBy(() -> new TextSplitter(0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new TextSplitter(-1, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TC-SPLIT-012 跳级标题（# 直接到 ###）不应产生空层级")
        void skippedHeadingLevels() {
            String text = "# Nginx\n### 超时参数\nproxy_read_timeout 说明。\n";

            List<TextSplitter.TextChunk> chunks = new TextSplitter().split(text, "Nginx标准");

            assertThat(chunks).anySatisfy(c -> {
                assertThat(c.getSectionPath()).isEqualTo("Nginx / 超时参数");
                assertThat(c.getSectionPath()).doesNotContain(" /  / ");
            });
        }

        @Test
        @DisplayName("TC-SPLIT-015 默认切片长度不得超过 embedding 模型的安全预算")
        void defaultChunksFitEmbeddingLimit() {
            // 预算由默认 embedding 模型（bge-large，512 token）推导，不写死字符数
            int budget = TextSplitter.budgetForTokenLimit(512);
            String text = "# 长文档\n" + "中文运维说明".repeat(300);

            List<TextSplitter.TextChunk> chunks = new TextSplitter().split(text, "长文档");

            assertThat(chunks).isNotEmpty();
            assertThat(chunks).allSatisfy(chunk ->
                    assertThat(chunk.getContent().length()).isLessThanOrEqualTo(budget));
        }
    }

    @Nested
    @DisplayName("Markdown 表格保护")
    class TableProtection {

        private String paramTable(int rows) {
            StringBuilder sb = new StringBuilder("| 参数名 | 默认值 | 建议值 | 说明 |\n| --- | --- | --- | --- |\n");
            for (int i = 0; i < rows; i++) {
                sb.append("| param_").append(i).append(" | 0 | 100 | 第").append(i).append("个参数的说明文字 |\n");
            }
            return sb.toString();
        }

        @Test
        @DisplayName("TC-SPLIT-007 小表应整表保留在同一切片内，不被拦腰切断")
        void smallTableStaysWhole() {
            String text = "# MySQL\n## 参数标准\n" + paramTable(3);

            List<TextSplitter.TextChunk> chunks = new TextSplitter(900, 100).split(text, "MySQL标准");

            assertThat(chunks).anySatisfy(c -> {
                assertThat(c.getContent()).contains("| 参数名 | 默认值 | 建议值 | 说明 |");
                assertThat(c.getContent()).contains("param_0");
                assertThat(c.getContent()).contains("param_2");
            });
        }

        @Test
        @DisplayName("TC-SPLIT-013 紧凑分隔行 |:-:| 应被识别为表头分隔而非数据行")
        void compactSeparatorRecognised() {
            StringBuilder sb = new StringBuilder("# MySQL\n## 参数\n|参数名|建议值|\n|:-:|:-:|\n");
            for (int i = 0; i < 60; i++) {
                sb.append("|param_").append(i).append("|value_").append(i).append("|\n");
            }

            List<TextSplitter.TextChunk> chunks = new TextSplitter(300, 0).split(sb.toString(), "MySQL标准");
            List<TextSplitter.TextChunk> tableChunks = chunks.stream()
                    .filter(c -> c.getContent().contains("param_")).toList();

            assertThat(tableChunks).hasSizeGreaterThan(1);
            assertThat(tableChunks).allSatisfy(c -> {
                assertThat(c.getContent()).contains("|参数名|建议值|");
                assertThat(c.getContent()).contains("|:-:|:-:|");
            });
        }

        @Test
        @DisplayName("TC-SPLIT-014 仅有表头的超限表格不应被整体丢弃")
        void headerOnlyOversizedTableIsKept() {
            String header = "| " + "很长的列名".repeat(80) + " |";
            String text = "# 标准\n## 表\n" + header + "\n| --- |\n";

            List<TextSplitter.TextChunk> chunks = new TextSplitter(200, 0).split(text, "标准");

            assertThat(chunks).isNotEmpty();
            assertThat(chunks.stream().anyMatch(c -> c.getContent().contains("很长的列名"))).isTrue();
        }

        @Test
        @DisplayName("TC-SPLIT-008 超大表格按行拆分时，每个切片都应重复表头")
        void oversizedTableRepeatsHeaderInEveryChunk() {
            String text = "# MySQL\n## 参数标准\n" + paramTable(60);

            List<TextSplitter.TextChunk> chunks = new TextSplitter(400, 0).split(text, "MySQL标准");
            List<TextSplitter.TextChunk> tableChunks = chunks.stream()
                    .filter(c -> c.getContent().contains("param_"))
                    .toList();

            assertThat(tableChunks).hasSizeGreaterThan(1);
            assertThat(tableChunks).allSatisfy(c -> {
                assertThat(c.getContent()).contains("| 参数名 | 默认值 | 建议值 | 说明 |");
                assertThat(c.getContent()).contains("| --- |");
            });
        }
    }
}
