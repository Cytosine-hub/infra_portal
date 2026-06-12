package com.middleware.manager.wiki.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.repository.SoftwareTypeMapper;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiIngestLogMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IngestAgentTest {

    @Mock
    private ChatModel chatModel;
    @Mock
    private WikiPageMapper pageMapper;
    @Mock
    private WikiSourceMapper sourceMapper;
    @Mock
    private WikiIngestLogMapper logMapper;
    @Mock
    private LinkResolver linkResolver;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private SoftwareTypeMapper softwareTypeMapper;

    private IngestAgent ingestAgent;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ingestAgent = new IngestAgent(chatModel, pageMapper, sourceMapper, logMapper,
                linkResolver, embeddingService, vectorStore, softwareTypeMapper,
                new DocumentTypeClassifier(), new DocumentOutlineExtractor(), new WikiIngestQualityGate(), 50000);
    }

    @Nested
    @DisplayName("ingestContent 字段名一致性")
    class IngestContentFieldNames {

        @Test
        @DisplayName("合并决策使用 'action' 字段（与 prompt 一致）")
        void mergeDecisionUsesActionField() {
            // Setup: 模拟 LLM 返回分析 JSON
            JsonObject analysis = new JsonObject();
            analysis.addProperty("dummy", "value");
            String analysisJson = gson.toJson(analysis);

            // 模拟 LLM 返回页面生成 JSON
            JsonObject pagesResult = new JsonObject();
            com.google.gson.JsonArray pages = new com.google.gson.JsonArray();
            JsonObject pageObj = new JsonObject();
            pageObj.addProperty("title", "Test Page");
            pageObj.addProperty("page_type", "ENTITY");
            pageObj.addProperty("content", "new content");
            pageObj.addProperty("summary", "summary");
            pages.add(pageObj);
            pagesResult.add("pages", pages);

            // 模拟合并决策 JSON — 使用 "action" 字段（正确的字段名）
            JsonObject mergeDecision = new JsonObject();
            mergeDecision.addProperty("action", "APPEND");
            mergeDecision.addProperty("reason", "补充信息");

            // Mock LLM 调用：第一次返回分析，第二次返回页面，第三次返回合并决策
            ChatResponse resp1 = createChatResponse(analysisJson);
            ChatResponse resp2 = createChatResponse(gson.toJson(pagesResult));
            ChatResponse resp3 = createChatResponse(gson.toJson(mergeDecision));
            when(chatModel.chat(anyList()))
                    .thenReturn(resp1)
                    .thenReturn(resp2)
                    .thenReturn(resp3);

            // Mock: 已存在同名页面
            WikiPage existing = new WikiPage();
            existing.setId(1L);
            existing.setTitle("Test Page");
            existing.setPageType("CONFIG");
            existing.setContent("old content");
            when(pageMapper.findByTitleAndType("Test Page", "ENTITY")).thenReturn(existing);
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            IngestAgent.IngestResult result = ingestAgent.ingestContent(
                    "test content", "test title", "中间件", "Redis", 1L);

            assertEquals("SUCCESS", result.getStatus());
            // 验证 APPEND 分支被执行（内容被追加而非覆盖）
            ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
            verify(pageMapper).update(pageCaptor.capture());
            WikiPage updatedPage = pageCaptor.getValue();
            assertTrue(updatedPage.getContent().contains("old content"));
            assertTrue(updatedPage.getContent().contains("new content"));
        }

        @Test
        @DisplayName("CONTRADICT 决策使用 'reason' 字段（与 prompt 一致）")
        void contradictDecisionUsesReasonField() {
            JsonObject analysis = new JsonObject();
            analysis.addProperty("dummy", "value");

            JsonObject pagesResult = new JsonObject();
            com.google.gson.JsonArray pages = new com.google.gson.JsonArray();
            JsonObject pageObj = new JsonObject();
            pageObj.addProperty("title", "Test Page");
            pageObj.addProperty("page_type", "ENTITY");
            pageObj.addProperty("content", "contradicting content");
            pages.add(pageObj);
            pagesResult.add("pages", pages);

            JsonObject mergeDecision = new JsonObject();
            mergeDecision.addProperty("action", "CONTRADICT");
            mergeDecision.addProperty("reason", "版本冲突");

            ChatResponse resp1 = createChatResponse(gson.toJson(analysis));
            ChatResponse resp2 = createChatResponse(gson.toJson(pagesResult));
            ChatResponse resp3 = createChatResponse(gson.toJson(mergeDecision));
            when(chatModel.chat(anyList()))
                    .thenReturn(resp1)
                    .thenReturn(resp2)
                    .thenReturn(resp3);

            WikiPage existing = new WikiPage();
            existing.setId(1L);
            existing.setTitle("Test Page");
            existing.setPageType("CONFIG");
            existing.setContent("old content");
            when(pageMapper.findByTitleAndType("Test Page", "ENTITY")).thenReturn(existing);
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            IngestAgent.IngestResult result = ingestAgent.ingestContent(
                    "test content", "test title", "中间件", "Redis", 1L);

            assertEquals("SUCCESS", result.getStatus());
            // 验证状态被设为 CONTRADICTED，且 contradictionNote 使用 "reason" 字段值
            ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
            verify(pageMapper).update(pageCaptor.capture());
            WikiPage updatedPage = pageCaptor.getValue();
            assertEquals("CONTRADICTED", updatedPage.getStatus());
            assertEquals("版本冲突", updatedPage.getContradictionNote());
        }

        @Test
        @DisplayName("等价标题按 canonical title 匹配已有页面")
        void canonicalTitleMatchesExistingPage() {
            JsonObject analysis = new JsonObject();
            analysis.addProperty("dummy", "value");

            JsonObject pagesResult = new JsonObject();
            com.google.gson.JsonArray pages = new com.google.gson.JsonArray();
            JsonObject pageObj = new JsonObject();
            pageObj.addProperty("title", "BES安装指南");
            pageObj.addProperty("page_type", "RUNBOOK");
            pageObj.addProperty("content", "new install content");
            pageObj.addProperty("summary", "summary");
            pages.add(pageObj);
            pagesResult.add("pages", pages);

            JsonObject mergeDecision = new JsonObject();
            mergeDecision.addProperty("action", "APPEND");

            ChatResponse resp1 = createChatResponse(gson.toJson(analysis));
            ChatResponse resp2 = createChatResponse(gson.toJson(pagesResult));
            ChatResponse resp3 = createChatResponse(gson.toJson(mergeDecision));
            when(chatModel.chat(anyList()))
                    .thenReturn(resp1)
                    .thenReturn(resp2)
                    .thenReturn(resp3);

            WikiPage existing = new WikiPage();
            existing.setId(1L);
            existing.setTitle("BES 安装指南");
            existing.setPageType("RUNBOOK");
            existing.setCategory("中间件");
            existing.setSoftware("BES");
            existing.setContent("old install content");
            when(pageMapper.findByCategoryOrSoftware("中间件", "BES", 100))
                    .thenReturn(java.util.List.of(existing));
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            IngestAgent.IngestResult result = ingestAgent.ingestContent(
                    "test content", "test title", "中间件", "BES", 1L);

            assertEquals("SUCCESS", result.getStatus());
            verify(pageMapper, never()).insert(any(WikiPage.class));
            ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
            verify(pageMapper).update(pageCaptor.capture());
            assertEquals("BES 安装指南", pageCaptor.getValue().getTitle());
            assertTrue(pageCaptor.getValue().getContent().contains("new install content"));
        }

        @Test
        @DisplayName("APPEND 合并按 Markdown 标题块补丁更新")
        void appendMergesMarkdownHeadingBlocks() {
            JsonObject analysis = new JsonObject();
            analysis.addProperty("dummy", "value");

            JsonObject pagesResult = new JsonObject();
            com.google.gson.JsonArray pages = new com.google.gson.JsonArray();
            JsonObject pageObj = new JsonObject();
            pageObj.addProperty("title", "BES 安装指南");
            pageObj.addProperty("page_type", "RUNBOOK");
            pageObj.addProperty("content", "# 安装步骤\n新增安装步骤\n# 启动验证\n新增验证方法");
            pageObj.addProperty("summary", "summary");
            pages.add(pageObj);
            pagesResult.add("pages", pages);

            JsonObject mergeDecision = new JsonObject();
            mergeDecision.addProperty("action", "APPEND");

            ChatResponse resp1 = createChatResponse(gson.toJson(analysis));
            ChatResponse resp2 = createChatResponse(gson.toJson(pagesResult));
            ChatResponse resp3 = createChatResponse(gson.toJson(mergeDecision));
            when(chatModel.chat(anyList()))
                    .thenReturn(resp1)
                    .thenReturn(resp2)
                    .thenReturn(resp3);

            WikiPage existing = new WikiPage();
            existing.setId(1L);
            existing.setTitle("BES 安装指南");
            existing.setPageType("RUNBOOK");
            existing.setContent("# 安装步骤\n已有安装步骤");
            when(pageMapper.findByTitleAndType("BES 安装指南", "RUNBOOK")).thenReturn(existing);
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            IngestAgent.IngestResult result = ingestAgent.ingestContent(
                    "test content", "test title", "中间件", "BES", 1L);

            assertEquals("SUCCESS", result.getStatus());
            ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
            verify(pageMapper).update(pageCaptor.capture());
            String content = pageCaptor.getValue().getContent();
            assertEquals(1, countOccurrences(content, "# 安装步骤"));
            assertTrue(content.contains("已有安装步骤"));
            assertTrue(content.contains("新增安装步骤"));
            assertTrue(content.contains("# 启动验证"));
        }

        @Test
        @DisplayName("新建页面持久化 canonical title 和 alias titles")
        void persistsCanonicalAndAliasTitlesOnCreate() {
            JsonObject analysis = new JsonObject();
            analysis.addProperty("dummy", "value");

            JsonObject pagesResult = new JsonObject();
            com.google.gson.JsonArray pages = new com.google.gson.JsonArray();
            JsonObject pageObj = new JsonObject();
            pageObj.addProperty("title", "BES 安装指南");
            pageObj.addProperty("page_type", "RUNBOOK");
            pageObj.addProperty("content", "new install content");
            pageObj.addProperty("summary", "summary");
            com.google.gson.JsonArray aliases = new com.google.gson.JsonArray();
            aliases.add("BES安装指南");
            aliases.add("安装部署");
            pageObj.add("alias_titles", aliases);
            pages.add(pageObj);
            pagesResult.add("pages", pages);

            ChatResponse resp1 = createChatResponse(gson.toJson(analysis));
            ChatResponse resp2 = createChatResponse(gson.toJson(pagesResult));
            when(chatModel.chat(anyList()))
                    .thenReturn(resp1)
                    .thenReturn(resp2);
            when(pageMapper.findByCategoryOrSoftware("中间件", "BES", 100))
                    .thenReturn(Collections.emptyList());
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            IngestAgent.IngestResult result = ingestAgent.ingestContent(
                    "test content", "test title", "中间件", "BES", 1L);

            assertEquals("SUCCESS", result.getStatus());
            ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
            verify(pageMapper).insert(pageCaptor.capture());
            WikiPage inserted = pageCaptor.getValue();
            assertEquals("bes安装指南", inserted.getCanonicalTitle());
            assertTrue(inserted.getAliasTitles().contains("BES安装指南"));
            assertTrue(inserted.getAliasTitles().contains("安装部署"));
        }
    }

    @Nested
    @DisplayName("planned ingest 规划校验")
    class PlannedIngestValidation {

        @Test
        @DisplayName("page_plan 漏掉 required section 时直接失败")
        void failsWhenPagePlanMissesRequiredSection() {
            JsonObject sectionFacts = new JsonObject();
            com.google.gson.JsonArray facts = new com.google.gson.JsonArray();
            JsonObject fact = new JsonObject();
            fact.addProperty("section_id", "sec-001");
            fact.addProperty("section_path", "配置说明");
            facts.add(fact);
            sectionFacts.add("section_facts", facts);

            JsonObject pagePlan = new JsonObject();
            com.google.gson.JsonArray plans = new com.google.gson.JsonArray();
            JsonObject plan = new JsonObject();
            plan.addProperty("planned_title", "BES V9.5.5 配置说明");
            plan.addProperty("page_type", "STANDARD");
            com.google.gson.JsonArray covered = new com.google.gson.JsonArray();
            covered.add("sec-001");
            plan.add("covered_section_ids", covered);
            plans.add(plan);
            pagePlan.add("pages", plans);

            ChatResponse sectionFactsResponse = createChatResponse(gson.toJson(sectionFacts));
            ChatResponse pagePlanResponse = createChatResponse(gson.toJson(pagePlan));
            when(chatModel.chat(anyList()))
                    .thenReturn(sectionFactsResponse)
                    .thenReturn(pagePlanResponse);
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            WikiSource source = new WikiSource();
            source.setId(1L);
            source.setTitle("config.md");
            source.setSourceType("UPLOAD");
            source.setCategory("中间件");
            source.setSoftware("BES");
            source.setContent("""
                    # 配置说明
                    这里描述配置文件。

                    ## 最大连接数
                    参数 maxConnections 默认值为 100。
                    """);

            IngestAgent.IngestResult result = ingestAgent.ingestPlanned(source, 1L);

            assertEquals("FAILED", result.getStatus());
            assertTrue(result.getErrorMessage().contains("缺少必需章节"));
            verify(pageMapper, never()).insert(any(WikiPage.class));
            verify(logMapper).insert(any());
        }

        @Test
        @DisplayName("section_facts JSON 截断时使用章节兜底事实继续编译")
        void continuesWithFallbackFactsWhenSectionFactsJsonIsTruncated() {
            JsonObject pagePlan = new JsonObject();
            com.google.gson.JsonArray plans = new com.google.gson.JsonArray();
            JsonObject plan = new JsonObject();
            plan.addProperty("planned_title", "BES 配置说明");
            plan.addProperty("page_type", "STANDARD");
            plan.addProperty("category", "中间件");
            plan.addProperty("software", "BES");
            plan.addProperty("version", "V9");
            com.google.gson.JsonArray covered = new com.google.gson.JsonArray();
            covered.add("sec-001");
            covered.add("sec-002");
            plan.add("covered_section_ids", covered);
            plan.addProperty("required", true);
            plan.addProperty("merge_strategy", "CREATE_OR_PATCH");
            plans.add(plan);
            pagePlan.add("pages", plans);

            JsonObject pagesResult = new JsonObject();
            com.google.gson.JsonArray pages = new com.google.gson.JsonArray();
            JsonObject page = new JsonObject();
            page.addProperty("title", "BES 配置说明");
            page.addProperty("page_type", "STANDARD");
            page.addProperty("category", "中间件");
            page.addProperty("software", "BES");
            page.addProperty("version", "V9");
            page.addProperty("summary", "BES 配置说明页面");
            page.addProperty("content", longContent());
            pages.add(page);
            pagesResult.add("pages", pages);

            ChatResponse truncatedFactsResponse = createChatResponse("{\"section_facts\":[{\"section_id\":\"sec-001\"");
            ChatResponse pagePlanResponse = createChatResponse(gson.toJson(pagePlan));
            ChatResponse pagesResponse = createChatResponse(gson.toJson(pagesResult));
            when(chatModel.chat(anyList()))
                    .thenReturn(truncatedFactsResponse)
                    .thenReturn(pagePlanResponse)
                    .thenReturn(pagesResponse);
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            WikiSource source = new WikiSource();
            source.setId(1L);
            source.setTitle("config.md");
            source.setSourceType("UPLOAD");
            source.setCategory("中间件");
            source.setSoftware("BES");
            source.setContent("""
                    # 配置说明
                    这里描述配置文件。

                    ## 最大连接数
                    参数 maxConnections 默认值为 100，修改后重启服务生效。
                    """);

            IngestAgent.IngestResult result = ingestAgent.ingestPlanned(source, 1L);

            assertNotEquals("FAILED", result.getStatus());
            ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
            verify(pageMapper).insert(pageCaptor.capture());
            WikiPage inserted = pageCaptor.getValue();
            assertEquals("BES 配置说明", inserted.getTitle());
            assertNotNull(inserted.getSourceRefs());
            assertTrue(inserted.getSourceRefs().contains("sec-001"));
            assertTrue(inserted.getSourceRefs().contains("sec-002"));
        }
    }

    @Nested
    @DisplayName("parseJson 安全性")
    class ParseJson {

        @Test
        @DisplayName("去 markdown 代码块后解析")
        void stripsCodeBlock() {
            // 通过 ingest 测试间接验证 — 验证 LLM 返回带代码块的 JSON 能被正确解析
            JsonObject analysis = new JsonObject();
            analysis.addProperty("status", "ok");

            JsonObject pagesResult = new JsonObject();
            com.google.gson.JsonArray pages = new com.google.gson.JsonArray();
            JsonObject page = new JsonObject();
            page.addProperty("title", "Valid Page");
            page.addProperty("page_type", "ENTITY");
            page.addProperty("content", "valid content");
            pages.add(page);
            pagesResult.add("pages", pages);

            ChatResponse resp1 = createChatResponse("```json\n" + gson.toJson(analysis) + "\n```");
            ChatResponse resp2 = createChatResponse(gson.toJson(pagesResult));
            when(chatModel.chat(anyList()))
                    .thenReturn(resp1)
                    .thenReturn(resp2);
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            WikiSource source = new WikiSource();
            source.setId(1L);
            source.setTitle("test");
            source.setContent("content");
            source.setSourceType("UPLOAD");
            source.setCategory("中间件");

            IngestAgent.IngestResult result = ingestAgent.ingest(source, 1L);

            // 解析成功，返回 SUCCESS（非 FAILED）
            assertEquals("SUCCESS", result.getStatus());
        }
    }

    @Nested
    @DisplayName("sha256")
    class Sha256 {

        @Test
        @DisplayName("相同内容产生相同哈希")
        void sameContentSameHash() {
            String hash1 = IngestAgent.sha256("hello");
            String hash2 = IngestAgent.sha256("hello");
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("不同内容产生不同哈希")
        void differentContentDifferentHash() {
            String hash1 = IngestAgent.sha256("hello");
            String hash2 = IngestAgent.sha256("world");
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("哈希长度为 64 字符（SHA-256 十六进制）")
        void hashLength64() {
            String hash = IngestAgent.sha256("test");
            assertEquals(64, hash.length());
        }
    }

    private ChatResponse createChatResponse(String text) {
        AiMessage aiMessage = new AiMessage(text);
        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        return response;
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private String longContent() {
        return """
                # BES 配置说明

                ## 配置文件
                本章节说明 BES 配置文件中的关键参数，包含参数名称、默认值、修改时机和生效方式。
                变更配置前需要确认当前运行环境、备份原始配置，并在变更后记录版本、操作人和验证结果。

                ## 最大连接数
                maxConnections 默认值为 100，用于限制连接池可同时处理的最大连接数量。
                调整该参数后需要重启服务，并观察连接数、响应时间和错误日志，确认配置已经按预期生效。

                ## 验证
                验证时需要检查服务启动状态、业务访问结果和日志中是否存在配置解析异常。
                如果验证失败，应回退到备份配置并重新执行启动检查。

                ## 审核记录
                配置变更完成后，需要把变更前后的参数值、服务重启时间、验证访问地址、日志检查结果写入审核记录。
                对生产环境还需要补充影响范围、回退窗口、值班联系人和监控观察周期，保证后续排查能够追溯到本次变更。

                ## 回退
                回退时先停止相关实例，再恢复备份配置文件，随后重新启动服务并检查端口监听、管理控制台访问和应用健康状态。
                如果回退后仍存在异常，应保留现场日志，停止继续变更，并由维护人员根据错误信息进行进一步分析。
                """;
    }
}
