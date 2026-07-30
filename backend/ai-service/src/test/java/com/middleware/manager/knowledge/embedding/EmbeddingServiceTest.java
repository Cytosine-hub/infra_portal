package com.middleware.manager.knowledge.embedding;

import com.middleware.manager.knowledge.splitter.TextSplitter;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingServiceTest {

    @Test
    @DisplayName("TC-EMBED-001 超长文本进入模型前必须按 token 上限推导的预算截断")
    void truncatesTextBeforeCallingModel() {
        // 截断长度不再是写死的字符数，而是由 embedding 模型 token 上限推导，
        // 与切片预算同源。换 bge-m3 时只改配置，这条断言自动跟随。
        int tokenLimit = 512;
        int expected = TextSplitter.budgetForTokenLimit(tokenLimit);

        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(argThat((String text) -> text != null && text.length() == expected)))
                .thenThrow(new IllegalStateException("stop after argument capture"));
        EmbeddingService service = new EmbeddingService(model, tokenLimit);

        assertThatThrownBy(() -> service.embed("中".repeat(expected * 3)))
                .isInstanceOf(IllegalStateException.class);

        verify(model).embed(argThat((String text) -> text.length() == expected));
    }
}
