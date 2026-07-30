package com.middleware.manager.knowledge.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.knowledge.splitter.TextSplitter;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.service.StorageService;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {

    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStore vectorStore;
    @Mock private DocumentLoader documentLoader;
    @Mock private StorageService storageService;
    @Mock private WikiSourceMapper sourceMapper;

    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(documentLoader.supports(anyString())).thenReturn(true);
        service = new KnowledgeService(
                new TextSplitter(300, 50), embeddingService, vectorStore,
                List.of(documentLoader), storageService, sourceMapper);
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-001 embedding 失败时应删除已存文件且不创建来源记录")
    void cleansStoredFileWhenEmbeddingFails() throws Exception {
        MockMultipartFile file = file("manual.pdf");
        when(storageService.store(file, "knowledge")).thenReturn(stored("knowledge/manual.pdf"));
        when(documentLoader.load(any(), anyString())).thenReturn("# 参数\ninnodb_buffer_pool_size 建议 70%");
        when(embeddingService.embedBatch(any())).thenThrow(new IllegalStateException("embedding failed"));

        assertThatThrownBy(() -> service.importFile(file))
                .isInstanceOf(IllegalStateException.class);

        verify(storageService).deleteIfExists("knowledge/manual.pdf");
        verify(sourceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-002 向量写入失败时应补偿删除新来源、向量和文件")
    void compensatesNewSourceWhenVectorWriteFails() throws Exception {
        MockMultipartFile file = file("manual.pdf");
        when(storageService.store(file, "knowledge")).thenReturn(stored("knowledge/manual.pdf"));
        when(documentLoader.load(any(), anyString())).thenReturn("# 参数\ninnodb_buffer_pool_size 建议 70%");
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{1F}));
        doAnswer(invocation -> {
            WikiSource source = invocation.getArgument(0);
            source.setId(91L);
            return 1;
        }).when(sourceMapper).insert(any(WikiSource.class));
        doThrow(new IllegalStateException("milvus failed"))
                .when(vectorStore).addAll(any());

        assertThatThrownBy(() -> service.importFile(file))
                .isInstanceOf(IllegalStateException.class);

        verify(vectorStore).deleteBySource("UPLOAD", 91L);
        verify(sourceMapper).deleteById(91L);
        verify(storageService).deleteIfExists("knowledge/manual.pdf");
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-003 相同正文的不同标准必须创建独立来源")
    void keepsDifferentStandardsSeparateWhenContentMatches() {
        AtomicLong ids = new AtomicLong(100);
        AtomicReference<WikiSource> firstInserted = new AtomicReference<>();
        when(sourceMapper.findByTitleAndType(anyString(), anyString())).thenReturn(null);
        when(sourceMapper.findByContentHash(anyString())).thenAnswer(invocation -> firstInserted.get());
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{1F}));
        doAnswer(invocation -> {
            WikiSource source = invocation.getArgument(0);
            source.setId(ids.incrementAndGet());
            firstInserted.compareAndSet(null, source);
            return 1;
        }).when(sourceMapper).insert(any(WikiSource.class));

        service.indexStandard(standard(1L, "MySQL 参数标准"));
        service.indexStandard(standard(2L, "Nginx 参数标准"));

        verify(sourceMapper, times(2)).insert(any(WikiSource.class));
        verify(sourceMapper, never()).findByContentHash(anyString());
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-004 只有标题而没有正文的上传应失败并清理文件")
    void rejectsUploadThatProducesNoChunks() throws Exception {
        MockMultipartFile file = file("empty.md");
        when(storageService.store(file, "knowledge")).thenReturn(stored("knowledge/empty.md"));
        when(documentLoader.load(any(), anyString())).thenReturn("# 只有标题\n");

        assertThatThrownBy(() -> service.importFile(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo(ErrorCode.KNOWLEDGE_CONTENT_EMPTY));

        verify(storageService).deleteIfExists("knowledge/empty.md");
        verify(embeddingService, never()).embedBatch(any());
        verify(sourceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-005 零切片标准应清理历史来源和向量")
    void removesStaleSourceWhenStandardProducesNoChunks() {
        ParameterStandard standard = standard(3L, "空标准");
        standard.setContent("# 参数标准\n");
        WikiSource existing = new WikiSource();
        existing.setId(77L);
        existing.setTitle(standard.getTitle());
        existing.setSourceType("STANDARD_DOC");
        when(sourceMapper.findByTitleAndType("空标准", "STANDARD_DOC")).thenReturn(existing);

        KnowledgeService.ImportResult result = service.indexStandard(standard);

        assertThat(result.getChunkCount()).isZero();
        verify(vectorStore).deleteBySource("STANDARD_DOC", 77L);
        verify(sourceMapper).deleteById(77L);
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-006 同名文档更新成功后应删除被替换的旧文件")
    void deletesReplacedFileAfterSuccessfulUpdate() throws Exception {
        MockMultipartFile file = file("manual.pdf");
        when(storageService.store(file, "knowledge")).thenReturn(stored("knowledge/new.pdf"));
        when(documentLoader.load(any(), anyString())).thenReturn("# 参数\ninnodb_buffer_pool_size 建议 70%");
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{1F}));
        WikiSource existing = new WikiSource();
        existing.setId(31L);
        existing.setTitle("manual.pdf");
        existing.setSourceType("UPLOAD");
        existing.setFilePath("knowledge/old.pdf");
        when(sourceMapper.findByTitleAndType("manual.pdf", "UPLOAD")).thenReturn(existing);

        service.importFile(file);

        verify(storageService).deleteIfExists("knowledge/old.pdf");
        verify(storageService, never()).deleteIfExists("knowledge/new.pdf");
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-007 同名文档更新失败时必须保留旧向量和旧文件")
    void keepsExistingDocumentWhenReplacementVectorWriteFails() throws Exception {
        MockMultipartFile file = file("manual.pdf");
        when(storageService.store(file, "knowledge")).thenReturn(stored("knowledge/new.pdf"));
        when(documentLoader.load(any(), anyString())).thenReturn("# 参数\ninnodb_buffer_pool_size 建议 75%");
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{1F}));
        WikiSource existing = new WikiSource();
        existing.setId(31L);
        existing.setTitle("manual.pdf");
        existing.setSourceType("UPLOAD");
        existing.setFilePath("knowledge/old.pdf");
        when(sourceMapper.findByTitleAndType("manual.pdf", "UPLOAD")).thenReturn(existing);
        doThrow(new IllegalStateException("milvus failed"))
                .when(vectorStore).addAll(any());

        assertThatThrownBy(() -> service.importFile(file))
                .isInstanceOf(IllegalStateException.class);

        verify(vectorStore, never()).deleteBySource("UPLOAD", 31L);
        verify(storageService).deleteIfExists("knowledge/new.pdf");
        verify(storageService, never()).deleteIfExists("knowledge/old.pdf");
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream", "content".getBytes());
    }

    private StorageService.StoredFile stored(String path) {
        return new StorageService.StoredFile(path, path, "application/octet-stream", 7);
    }

    private ParameterStandard standard(Long id, String title) {
        ParameterStandard standard = new ParameterStandard();
        standard.setId(id);
        standard.setTitle(title);
        standard.setCategory("数据库");
        standard.setSoftware("MySQL");
        standard.setContent("# 参数标准\n共享正文内容");
        return standard;
    }
}
