package com.middleware.manager.knowledge.embedding;

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
    @DisplayName("TC-EMBED-001 超长文本进入模型前必须按配置上限截断")
    void truncatesTextBeforeCallingModel() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(argThat((String text) -> text != null && text.length() == 300)))
                .thenThrow(new IllegalStateException("stop after argument capture"));
        EmbeddingService service = new EmbeddingService(model, 300);

        assertThatThrownBy(() -> service.embed("中".repeat(900)))
                .isInstanceOf(IllegalStateException.class);

        verify(model).embed(argThat((String text) -> text.length() == 300));
    }
}
