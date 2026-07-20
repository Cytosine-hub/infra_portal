package com.middleware.manager.wiki.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IngestPromptTemplatesTest {

    @Test
    @DisplayName("分析提示词包含文档内容和已有页面摘要")
    void analysisPromptContainsInputs() {
        String prompt = IngestPromptTemplates.buildAnalysisPrompt(
                "文档内容", "已有页面", "软件参考");

        assertTrue(prompt.contains("文档内容"));
        assertTrue(prompt.contains("已有页面"));
        assertTrue(prompt.contains("软件参考"));
        assertTrue(prompt.contains("entities"));
        assertTrue(prompt.contains("contradictions"));
    }

    @Test
    @DisplayName("页面生成提示词包含文档内容和分析结果")
    void pageGenerationPromptContainsInputs() {
        String prompt = IngestPromptTemplates.buildPageGenerationPrompt(
                "文档内容", "分析JSON");

        assertTrue(prompt.contains("文档内容"));
        assertTrue(prompt.contains("分析JSON"));
    }

    @Test
    @DisplayName("合并决策提示词指定 'action' 和 'reason' 字段名")
    void mergeDecisionPromptSpecifiesCorrectFieldNames() {
        String prompt = IngestPromptTemplates.buildMergeDecisionPrompt(
                "已有内容", "新内容");

        assertTrue(prompt.contains("已有内容"));
        assertTrue(prompt.contains("新内容"));
        // 关键：prompt 必须指定 "action" 字段名，与 IngestAgent.ingest/ingestContent 读取的字段一致
        assertTrue(prompt.contains("\"action\""), "Prompt 必须指定 action 字段名");
        assertTrue(prompt.contains("\"reason\""), "Prompt 必须指定 reason 字段名");
    }

    @Test
    @DisplayName("合并决策提示词的 action 值包含 OVERWRITE/APPEND/CONTRADICT")
    void mergeDecisionPromptDefinesValidActions() {
        String prompt = IngestPromptTemplates.buildMergeDecisionPrompt("old", "new");

        assertTrue(prompt.contains("OVERWRITE"));
        assertTrue(prompt.contains("APPEND"));
        assertTrue(prompt.contains("CONTRADICT"));
    }

    @Test
    @DisplayName("分析提示词不包含格式化占位符")
    void noFormatSpecifiersInOutput() {
        // 如果输入包含 %s，formatted() 会抛异常或产生错误
        // 验证正常输入不会产生裸露的 %s
        String prompt = IngestPromptTemplates.buildAnalysisPrompt(
                "test content", "summary", "reference");

        // 不应包含未替换的 %s（排除 JSON 示例中的）
        assertFalse(prompt.contains("%s"), "Prompt 不应包含未替换的格式化占位符");
    }
}
