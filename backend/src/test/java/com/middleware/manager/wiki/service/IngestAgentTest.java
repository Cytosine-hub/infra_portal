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
import java.util.Objects;

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
                new DocumentTypeClassifier(), new DocumentOutlineExtractor(), new WikiIngestQualityGate(), 50000, 2);
    }

    @Nested
    @DisplayName("planned ingest 规划校验")
    class PlannedIngestValidation {

        @Test
        @DisplayName("page_plan 漏掉 required section 时补充兜底计划继续编译")
        void repairsPagePlanWhenRequiredSectionIsMissing() {
            JsonObject sectionFacts = new JsonObject();
            com.google.gson.JsonArray facts = new com.google.gson.JsonArray();
            JsonObject fact = new JsonObject();
            fact.addProperty("section_id", "sec-001");
            fact.addProperty("section_path", "配置说明");
            facts.add(fact);
            JsonObject fact2 = new JsonObject();
            fact2.addProperty("section_id", "sec-002");
            fact2.addProperty("section_path", "配置说明/最大连接数");
            facts.add(fact2);
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
            ChatResponse pagesResponse = createChatResponse("{}");
            when(chatModel.chat(anyList()))
                    .thenReturn(sectionFactsResponse)
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
                    参数 maxConnections 默认值为 100。
                    """);

            IngestAgent.IngestResult result = ingestAgent.ingestPlanned(source, 1L);

            assertNotEquals("FAILED", result.getStatus());
            ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
            verify(pageMapper, atLeast(2)).insert(pageCaptor.capture());
            String mergedSourceRefs = pageCaptor.getAllValues().stream()
                    .map(WikiPage::getSourceRefs)
                    .filter(Objects::nonNull)
                    .reduce("", String::concat);
            assertTrue(mergedSourceRefs.contains("sec-001"));
            assertTrue(mergedSourceRefs.contains("sec-002"));
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

        @Test
        @DisplayName("标题-only章节使用本地事实，跳过section_facts LLM")
        void skipsSectionFactLlmForTitleOnlySections() {
            JsonObject pagePlan = new JsonObject();
            com.google.gson.JsonArray plans = new com.google.gson.JsonArray();
            JsonObject plan = new JsonObject();
            plan.addProperty("planned_title", "BES 配置目录");
            plan.addProperty("page_type", "CONCEPT");
            plan.addProperty("category", "中间件");
            plan.addProperty("software", "BES");
            plan.addProperty("version", "");
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
            page.addProperty("title", "BES 配置目录");
            page.addProperty("page_type", "CONCEPT");
            page.addProperty("category", "中间件");
            page.addProperty("software", "BES");
            page.addProperty("version", "");
            page.addProperty("summary", "BES 配置目录页面");
            page.addProperty("content", longContent());
            pages.add(page);
            pagesResult.add("pages", pages);

            ChatResponse pagePlanResponse = createChatResponse(gson.toJson(pagePlan));
            ChatResponse pagesResponse = createChatResponse(gson.toJson(pagesResult));
            when(chatModel.chat(anyList()))
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

                    ## JDBC配置
                    """);

            IngestAgent.IngestResult result = ingestAgent.ingestPlanned(source, 1L);

            assertNotEquals("FAILED", result.getStatus());
            verify(chatModel, times(2)).chat(anyList());
            verify(pageMapper).insert(any(WikiPage.class));
        }

        @Test
        @DisplayName("页面生成单批异常时使用兜底页面继续编译")
        void continuesWithFallbackPagesWhenOnePageGenerationBatchFails() {
            JsonObject sectionFacts = new JsonObject();
            com.google.gson.JsonArray facts = new com.google.gson.JsonArray();
            for (int i = 1; i <= 5; i++) {
                JsonObject fact = new JsonObject();
                fact.addProperty("section_id", "sec-00" + i);
                fact.addProperty("section_path", "章节 " + i);
                fact.add("facts", new com.google.gson.JsonArray());
                fact.add("operations", new com.google.gson.JsonArray());
                fact.add("config_items", new com.google.gson.JsonArray());
                fact.add("warnings", new com.google.gson.JsonArray());
                fact.add("entities", new com.google.gson.JsonArray());
                facts.add(fact);
            }
            sectionFacts.add("section_facts", facts);

            JsonObject pagePlan = new JsonObject();
            com.google.gson.JsonArray plans = new com.google.gson.JsonArray();
            for (int i = 1; i <= 5; i++) {
                JsonObject plan = new JsonObject();
                plan.addProperty("planned_title", "BES 页面 " + i);
                plan.addProperty("page_type", "STANDARD");
                plan.addProperty("category", "中间件");
                plan.addProperty("software", "BES");
                com.google.gson.JsonArray covered = new com.google.gson.JsonArray();
                covered.add("sec-00" + i);
                plan.add("covered_section_ids", covered);
                plan.addProperty("required", true);
                plan.addProperty("merge_strategy", "CREATE_OR_PATCH");
                plans.add(plan);
            }
            pagePlan.add("pages", plans);

            JsonObject secondBatchPages = new JsonObject();
            com.google.gson.JsonArray pages = new com.google.gson.JsonArray();
            JsonObject page = new JsonObject();
            page.addProperty("title", "BES 页面 5");
            page.addProperty("page_type", "STANDARD");
            page.addProperty("category", "中间件");
            page.addProperty("software", "BES");
            page.addProperty("summary", "BES 页面 5");
            page.addProperty("content", longContent());
            pages.add(page);
            secondBatchPages.add("pages", pages);

            when(chatModel.chat(anyList())).thenAnswer(invocation -> {
                String prompt = invocation.getArgument(0).toString();
                if (prompt.contains("章节事实抽取器")) {
                    return createChatResponse(gson.toJson(sectionFacts));
                }
                if (prompt.contains("页面规划器")) {
                    return createChatResponse(gson.toJson(pagePlan));
                }
                if (prompt.contains("Wiki 页面生成器") && prompt.contains("BES 页面 5")) {
                    return createChatResponse(gson.toJson(secondBatchPages));
                }
                throw new RuntimeException("mock page batch failure");
            });
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            WikiSource source = new WikiSource();
            source.setId(1L);
            source.setTitle("config.md");
            source.setSourceType("UPLOAD");
            source.setCategory("中间件");
            source.setSoftware("BES");
            source.setContent("""
                    # 章节 1
                    内容 1。
                    # 章节 2
                    内容 2。
                    # 章节 3
                    内容 3。
                    # 章节 4
                    内容 4。
                    # 章节 5
                    内容 5。
                    """);

            IngestAgent.IngestResult result = ingestAgent.ingestPlanned(source, 1L);

            assertNotEquals("FAILED", result.getStatus());
            verify(pageMapper, atLeast(5)).insert(any(WikiPage.class));
        }
    }

    @Nested
    @DisplayName("parseJson 安全性")
    class ParseJson {

        @Test
        @DisplayName("去 markdown 代码块后解析")
        void stripsCodeBlock() {
            // 通过 planned ingest 间接验证 — LLM 返回带代码块的 page_plan JSON 能被正确解析
            JsonObject sectionFacts = new JsonObject();
            com.google.gson.JsonArray facts = new com.google.gson.JsonArray();
            JsonObject fact = new JsonObject();
            fact.addProperty("section_id", "sec-001");
            fact.addProperty("section_path", "配置说明");
            fact.add("facts", new com.google.gson.JsonArray());
            fact.add("operations", new com.google.gson.JsonArray());
            fact.add("config_items", new com.google.gson.JsonArray());
            fact.add("warnings", new com.google.gson.JsonArray());
            fact.add("entities", new com.google.gson.JsonArray());
            facts.add(fact);
            sectionFacts.add("section_facts", facts);

            JsonObject pagePlan = new JsonObject();
            com.google.gson.JsonArray plans = new com.google.gson.JsonArray();
            JsonObject plan = new JsonObject();
            plan.addProperty("planned_title", "BES 配置说明");
            plan.addProperty("page_type", "STANDARD");
            plan.addProperty("category", "中间件");
            plan.addProperty("software", "BES");
            plan.addProperty("version", "");
            com.google.gson.JsonArray covered = new com.google.gson.JsonArray();
            covered.add("sec-001");
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
            page.addProperty("version", "");
            page.addProperty("summary", "BES 配置说明页面");
            page.addProperty("content", longContent());
            pages.add(page);
            pagesResult.add("pages", pages);

            // section_facts 正常返回，page_plan 返回带 markdown 代码块
            ChatResponse factsResponse = createChatResponse(gson.toJson(sectionFacts));
            ChatResponse pagePlanResponse = createChatResponse("```json\n" + gson.toJson(pagePlan) + "\n```");
            ChatResponse pagesResponse = createChatResponse(gson.toJson(pagesResult));
            when(chatModel.chat(anyList()))
                    .thenReturn(factsResponse)
                    .thenReturn(pagePlanResponse)
                    .thenReturn(pagesResponse);
            when(softwareTypeMapper.findAllByOrderByCategoryAscNameAsc()).thenReturn(Collections.emptyList());

            WikiSource source = new WikiSource();
            source.setId(1L);
            source.setTitle("config.md");
            source.setSourceType("UPLOAD");
            source.setCategory("中间件");
            source.setSoftware("BES");
            source.setContent("# 配置说明\n这里描述配置文件。\n");

            IngestAgent.IngestResult result = ingestAgent.ingestPlanned(source, 1L);

            assertNotEquals("FAILED", result.getStatus());
            verify(pageMapper).insert(any(WikiPage.class));
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
