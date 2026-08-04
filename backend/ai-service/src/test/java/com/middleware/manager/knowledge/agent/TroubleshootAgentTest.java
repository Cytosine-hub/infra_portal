package com.middleware.manager.knowledge.agent;

import com.middleware.manager.knowledge.service.KnowledgeSearchPort;
import com.middleware.manager.knowledge.service.KnowledgeSearchResult;
import com.middleware.manager.wiki.service.WikiSearchPort;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TroubleshootAgentTest {

    @Test
    @DisplayName("TC-DIAG-ATT-108 RAG 应把附件正文和图片传入模型并保存元数据")
    void includesAttachmentsInStreamingChat() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        OpenAiStreamClient streamClient = mock(OpenAiStreamClient.class);
        KnowledgeSearchPort knowledgeService = mock(KnowledgeSearchPort.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WikiSearchPort> wikiProvider = mock(ObjectProvider.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        DiagnosticAttachmentService attachmentService = new DiagnosticAttachmentService(List.of());

        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setTitle("已有会话");
        when(sessionMapper.findById(1L)).thenReturn(session);
        when(messageMapper.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(knowledgeService.search(eq("分析故障附件"), eq(5))).thenReturn(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<dev.langchain4j.data.message.ChatMessage> messages = invocation.getArgument(0);
            UserMessage user = (UserMessage) messages.get(messages.size() - 1);
            assertThat(user.contents()).anyMatch(ImageContent.class::isInstance);
            assertThat(user.contents().toString()).contains("connection refused");
            return "截图和日志显示连接被拒绝。";
        }).when(streamClient).stream(any(List.class), any());

        List<DiagnosticAttachment> attachments = List.of(
                new DiagnosticAttachment("error.log", "text/plain", 18,
                        DiagnosticAttachment.Kind.DOCUMENT, "connection refused", null),
                new DiagnosticAttachment("screen.png", "image/png", 4,
                        DiagnosticAttachment.Kind.IMAGE, null, "aGVsbG8="));
        TroubleshootAgent agent = new TroubleshootAgent(
                chatModel, streamClient, knowledgeService, wikiProvider,
                sessionMapper, messageMapper, new RetrievalEvidenceFilter(),
                new AnswerGroundingVerifier(), attachmentService);

        agent.chatStream(1L, "分析故障附件", attachments, null, ignored -> {}, null);

        verify(messageMapper).insert(argThat(message -> "user".equals(message.getRole())
                && message.getAttachmentsText().contains("screen.png")
                && message.getAttachmentsText().contains("error.log")));
    }

    @Test
    @DisplayName("TC-RAG-006 无可靠证据时仍应调用大模型并且不返回知识库引用")
    void generatesGeneralAnswerWhenRetrievedEvidenceIsNotReliable() {
        ChatModel chatModel = mock(ChatModel.class);
        OpenAiStreamClient streamClient = mock(OpenAiStreamClient.class);
        KnowledgeSearchPort knowledgeService = mock(KnowledgeSearchPort.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WikiSearchPort> wikiProvider = mock(ObjectProvider.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);

        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setTitle("已有会话");
        when(sessionMapper.findById(1L)).thenReturn(session);
        when(messageMapper.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(knowledgeService.search(eq("ORA-01555 怎么处理"), eq(5)))
                .thenReturn(List.of(result("MySQL 参数台账", "innodb_buffer_pool_size 建议 70%")));
        when(chatModel.chat(any(List.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("可以先检查长事务、UNDO 空间和 SQL 执行时间，再结合现场日志确认根因。"))
                .build());

        TroubleshootAgent agent = new TroubleshootAgent(
                chatModel, streamClient, knowledgeService, wikiProvider,
                sessionMapper, messageMapper, new RetrievalEvidenceFilter(), new AnswerGroundingVerifier(),
                new DiagnosticAttachmentService(List.of()));

        TroubleshootAgent.AgentResponse response = agent.chat(1L, "ORA-01555 怎么处理");

        assertThat(response.getAnswer()).contains("检查长事务");
        assertThat(response.getReferences()).isEmpty();
        verify(chatModel).chat(any(List.class));
        verifyNoInteractions(streamClient);
    }

    @Test
    @DisplayName("TC-RAG-007 流式回答无可靠证据时仍应调用大模型并且不返回知识库引用")
    void streamsGeneralAnswerWhenRetrievedEvidenceIsNotReliable() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        OpenAiStreamClient streamClient = mock(OpenAiStreamClient.class);
        KnowledgeSearchPort knowledgeService = mock(KnowledgeSearchPort.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WikiSearchPort> wikiProvider = mock(ObjectProvider.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);

        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setTitle("已有会话");
        when(sessionMapper.findById(1L)).thenReturn(session);
        when(messageMapper.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(knowledgeService.search(eq("未知组件启动失败"), eq(5))).thenReturn(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> onDelta = invocation.getArgument(1);
            onDelta.accept("请先收集错误日志和运行环境信息。");
            return "请先收集错误日志和运行环境信息。";
        }).when(streamClient).stream(any(List.class), any());

        TroubleshootAgent agent = new TroubleshootAgent(
                chatModel, streamClient, knowledgeService, wikiProvider,
                sessionMapper, messageMapper, new RetrievalEvidenceFilter(), new AnswerGroundingVerifier(),
                new DiagnosticAttachmentService(List.of()));

        TroubleshootAgent.AgentResponse response =
                agent.chatStream(1L, "未知组件启动失败", null, ignored -> {}, null);

        assertThat(response.getAnswer()).contains("收集错误日志");
        assertThat(response.getReferences()).isEmpty();
        verify(streamClient).stream(any(List.class), any());
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("TC-RAG-008 使用可靠知识库证据时应返回来源")
    void returnsReferencesWhenReliableEvidenceIsUsed() {
        ChatModel chatModel = mock(ChatModel.class);
        OpenAiStreamClient streamClient = mock(OpenAiStreamClient.class);
        KnowledgeSearchPort knowledgeService = mock(KnowledgeSearchPort.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WikiSearchPort> wikiProvider = mock(ObjectProvider.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);

        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setTitle("已有会话");
        when(sessionMapper.findById(1L)).thenReturn(session);
        when(messageMapper.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        KnowledgeSearchResult evidence = result(
                "MySQL 参数标准", "innodb_buffer_pool_size 建议设置为物理内存的 70%");
        evidence.setSource("standard");
        when(knowledgeService.search(eq("innodb_buffer_pool_size 如何设置"), eq(5)))
                .thenReturn(List.of(evidence));
        when(chatModel.chat(any(List.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(
                        "建议设置为物理内存的 70%。【知识库：MySQL 参数标准】"))
                .build());

        TroubleshootAgent agent = new TroubleshootAgent(
                chatModel, streamClient, knowledgeService, wikiProvider,
                sessionMapper, messageMapper, new RetrievalEvidenceFilter(), new AnswerGroundingVerifier(),
                new DiagnosticAttachmentService(List.of()));

        TroubleshootAgent.AgentResponse response =
                agent.chat(1L, "innodb_buffer_pool_size 如何设置");

        assertThat(response.getReferences()).singleElement().satisfies(reference -> {
            assertThat(reference.get("title")).isEqualTo("MySQL 参数标准");
            assertThat(reference.get("source")).isEqualTo("standard");
        });
    }

    private KnowledgeSearchResult result(String title, String content) {
        KnowledgeSearchResult result = new KnowledgeSearchResult();
        result.setSourceTitle(title);
        result.setContent(content);
        return result;
    }
}
