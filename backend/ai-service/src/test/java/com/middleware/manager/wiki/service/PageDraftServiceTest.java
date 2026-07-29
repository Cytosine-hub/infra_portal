package com.middleware.manager.wiki.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PageDraftServiceTest {

    @Mock private WikiSourceMapper sourceMapper;
    @Mock private ChatModel chatModel;

    private PageDraftService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PageDraftService(sourceMapper, chatModel, 4000);
    }

    private WikiSource source(String content) {
        WikiSource s = new WikiSource();
        s.setId(1L);
        s.setTitle("MySQL 运维手册");
        s.setCategory("数据库");
        s.setSoftware("MySQL");
        s.setContent(content);
        return s;
    }

    private void stubReply(String text) {
        when(chatModel.chat(anyList()))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(text)).build());
    }

    @Test
    @DisplayName("TC-DRAFT-001 应基于源文档生成 Markdown 草稿")
    void generatesDraftFromSource() {
        when(sourceMapper.findById(1L)).thenReturn(source("主从延迟的排查步骤……"));
        stubReply("## 现象\n主从延迟升高。\n\n## 排查步骤\n1. 看 Seconds_Behind_Master");

        String draft = service.draft(1L, "主从延迟处理");

        assertThat(draft).contains("## 排查步骤");
        assertThat(draft).contains("Seconds_Behind_Master");
    }

    @Test
    @DisplayName("TC-DRAFT-002 源文档不存在应抛 NotFoundException")
    void missingSourceThrows() {
        when(sourceMapper.findById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.draft(9L, "任意主题"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("TC-DRAFT-003 源文档无正文应抛业务异常而非发起 LLM 调用")
    void emptySourceContentThrows() {
        when(sourceMapper.findById(1L)).thenReturn(source("   "));

        assertThatThrownBy(() -> service.draft(1L, "主题"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("TC-DRAFT-004 超长源文档应截断到预算内再送 LLM，避免超出上下文")
    void truncatesOversizedSource() {
        when(sourceMapper.findById(1L)).thenReturn(source("内容".repeat(10000)));
        stubReply("## 草稿");

        service.draft(1L, "主题");

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(captor.capture());
        String prompt = captor.getValue().toString();
        assertThat(prompt.length()).isLessThan(6000);
    }

    @Test
    @DisplayName("TC-DRAFT-005 LLM 返回空内容应抛业务异常，不返回空草稿")
    void blankReplyThrows() {
        when(sourceMapper.findById(1L)).thenReturn(source("正文"));
        stubReply("   ");

        assertThatThrownBy(() -> service.draft(1L, "主题"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("TC-DRAFT-006 应剥离 LLM 可能附带的 markdown 代码围栏")
    void stripsCodeFence() {
        when(sourceMapper.findById(1L)).thenReturn(source("正文"));
        stubReply("```markdown\n## 现象\n描述\n```");

        String draft = service.draft(1L, "主题");

        assertThat(draft).startsWith("## 现象");
        assertThat(draft).doesNotContain("```");
    }
}
