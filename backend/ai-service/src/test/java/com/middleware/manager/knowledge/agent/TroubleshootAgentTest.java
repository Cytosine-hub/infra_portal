package com.middleware.manager.knowledge.agent;

import com.middleware.manager.knowledge.service.KnowledgeSearchPort;
import com.middleware.manager.knowledge.service.KnowledgeSearchResult;
import com.middleware.manager.wiki.service.WikiSearchPort;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TroubleshootAgentTest {

    @Test
    @DisplayName("TC-RAG-006 无可靠证据时不得调用大模型")
    void skipsModelWhenRetrievedEvidenceIsNotReliable() {
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

        TroubleshootAgent agent = new TroubleshootAgent(
                chatModel, streamClient, knowledgeService, wikiProvider,
                sessionMapper, messageMapper, new RetrievalEvidenceFilter());

        TroubleshootAgent.AgentResponse response = agent.chat(1L, "ORA-01555 怎么处理");

        assertThat(response.getAnswer()).isEqualTo(
                "知识库中未找到足够相关的内容，无法给出基于内部知识库的结论。");
        assertThat(response.getReferences()).isEmpty();
        verifyNoInteractions(chatModel, streamClient);
    }

    private KnowledgeSearchResult result(String title, String content) {
        KnowledgeSearchResult result = new KnowledgeSearchResult();
        result.setSourceTitle(title);
        result.setContent(content);
        return result;
    }
}
