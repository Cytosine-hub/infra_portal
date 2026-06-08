package com.middleware.manager.wiki.service;

import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.wiki.entity.IngestTask;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.IngestTaskMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestTaskServiceTest {

    private IngestTaskMapper taskMapper;
    private WikiSourceMapper sourceMapper;
    private IngestAgent ingestAgent;
    private IngestTaskService service;

    @BeforeEach
    void setUp() throws Exception {
        taskMapper = mock(IngestTaskMapper.class);
        sourceMapper = mock(WikiSourceMapper.class);
        ingestAgent = mock(IngestAgent.class);
        List<DocumentLoader> loaders = Collections.emptyList();
        service = new IngestTaskService(taskMapper, sourceMapper, ingestAgent, loaders);
        setField(service, "maxContentChars", 20);
        setField(service, "chunkOverlap", 5);
        setField(service, "maxConcurrent", 1);
        service.init();
    }

    @Test
    void allFailedChunksDoNotMarkSourceAsIngested() {
        IngestTask task = new IngestTask();
        task.setId(9L);
        task.setSourceId(3L);
        task.setTotalChunks(2);
        task.setOperatorId(1L);

        WikiSource source = new WikiSource();
        source.setId(3L);
        source.setTitle("100_TongWeb_V7.0集群管理指南_7049_M10A01.pdf");
        source.setContent("第一段内容\n\n第二段内容\n\n第三段内容\n\n第四段内容\n\n第五段内容\n\n");
        source.setIngested(false);

        IngestAgent.IngestResult failed = new IngestAgent.IngestResult();
        failed.setStatus("FAILED");
        failed.setErrorMessage("Failed to parse pages JSON");

        when(taskMapper.findById(9L)).thenReturn(task);
        when(sourceMapper.findById(3L)).thenReturn(source);
        when(ingestAgent.ingestContent(anyString(), eq(source.getTitle()), isNull(), isNull(), eq(1L)))
                .thenReturn(failed);

        service.executeTask(9L);

        verify(taskMapper, never()).updateResult(eq(9L), anyInt(), anyInt());
        verify(taskMapper).updateStatus(eq(9L), eq("FAILED"), contains("未生成 Wiki 页面"));
        ArgumentCaptor<WikiSource> sourceCaptor = ArgumentCaptor.forClass(WikiSource.class);
        verify(sourceMapper).update(sourceCaptor.capture());
        assertFalse(Boolean.TRUE.equals(sourceCaptor.getValue().getIngested()));
        assertNull(sourceCaptor.getValue().getIngestedAt());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
