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
}
