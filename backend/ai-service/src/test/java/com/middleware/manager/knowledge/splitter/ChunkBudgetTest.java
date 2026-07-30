package com.middleware.manager.knowledge.splitter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 切片预算应由 embedding 模型的上下文上限推导，而不是拍一个字符数。
 * <p>KBV-001 的临时处置是把上限从 900 直接砍到 300，代价是「参数表格整表不切」
 * 这条优化被废掉——300 字符装不下表头加几行数据，而参数表恰恰是本项目最核心的语料。
 * <p>正确做法：按模型 token 上限反推安全字符预算。中文最坏情况约 1 字 1 token，
 * 预留出面包屑前缀与安全余量即可。换 bge-m3（8192 token）后表格可整块进。
 */
class ChunkBudgetTest {

    private String paramTable(int rows) {
        StringBuilder sb = new StringBuilder("| 参数名 | 默认值 | 建议值 | 说明 |\n| --- | --- | --- | --- |\n");
        for (int i = 0; i < rows; i++) {
            sb.append("| param_").append(i).append(" | 0 | 100 | 第").append(i).append("个参数的说明文字 |\n");
        }
        return sb.toString();
    }

    @Test
    @DisplayName("TC-BUDGET-001 512 token 模型下的预算应容纳典型参数表整表")
    void smallModelBudgetHoldsTypicalTable() {
        // bge-large：512 token，安全字符预算约 400
        int budget = TextSplitter.budgetForTokenLimit(512);
        String text = "# MySQL\n## 参数标准\n" + paramTable(6);

        List<TextSplitter.TextChunk> chunks = new TextSplitter(budget).split(text, "MySQL标准");

        assertThat(budget).isBetween(300, 460);
        assertThat(chunks).anySatisfy(c -> {
            assertThat(c.getContent()).contains("| 参数名 | 默认值 | 建议值 | 说明 |");
            assertThat(c.getContent()).contains("param_0");
            assertThat(c.getContent()).contains("param_5");
        });
    }

    @Test
    @DisplayName("TC-BUDGET-002 8192 token 模型下应有足够余量容纳大表")
    void largeModelBudgetHoldsBigTable() {
        // bge-m3：8192 token，但不必跑满，取上限的安全折算值
        int budget = TextSplitter.budgetForTokenLimit(8192);
        String text = "# MySQL\n## 参数标准\n" + paramTable(40);

        List<TextSplitter.TextChunk> chunks = new TextSplitter(budget).split(text, "MySQL标准");

        assertThat(budget).isGreaterThan(1000);
        assertThat(chunks).anySatisfy(c -> {
            assertThat(c.getContent()).contains("param_0");
            assertThat(c.getContent()).contains("param_39");
        });
    }

    @Test
    @DisplayName("TC-BUDGET-003 任何模型下切片都不得超出该模型的字符预算")
    void chunksNeverExceedBudget() {
        for (int tokenLimit : new int[]{512, 1024, 8192}) {
            int budget = TextSplitter.budgetForTokenLimit(tokenLimit);
            StringBuilder sb = new StringBuilder("# 标准\n## 章节\n");
            for (int i = 0; i < 400; i++) {
                sb.append("这是第").append(i).append("行正文，用来把本节撑得远超单个切片上限。\n");
            }

            List<TextSplitter.TextChunk> chunks = new TextSplitter(budget).split(sb.toString(), "标准");

            assertThat(chunks).allSatisfy(c ->
                    assertThat(c.getContent().length())
                            .as("tokenLimit=%d 时切片超出预算 %d", tokenLimit, budget)
                            .isLessThanOrEqualTo(budget));
        }
    }

    @Test
    @DisplayName("TC-BUDGET-004 非法的 token 上限应被拒绝")
    void rejectsInvalidTokenLimit() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> TextSplitter.budgetForTokenLimit(0));
    }
}
