package com.middleware.manager.wiki.service;

import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.wiki.entity.IngestTask;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.IngestTaskMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

@Service
public class IngestTaskService {

    private static final Logger log = LoggerFactory.getLogger(IngestTaskService.class);

    private final IngestTaskMapper taskMapper;
    private final WikiSourceMapper sourceMapper;
    private final IngestAgent ingestAgent;
    private final List<DocumentLoader> documentLoaders;

    @Value("${app.wiki.ingest.max-content-chars:20000}")
    private int maxContentChars;

    @Value("${app.wiki.ingest.chunk-overlap:500}")
    private int chunkOverlap;

    @Value("${app.wiki.ingest.max-concurrent:2}")
    private int maxConcurrent;

    private Semaphore compileSemaphore;

    public IngestTaskService(IngestTaskMapper taskMapper, WikiSourceMapper sourceMapper,
                             IngestAgent ingestAgent, List<DocumentLoader> documentLoaders) {
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
        this.ingestAgent = ingestAgent;
        this.documentLoaders = documentLoaders;
    }

    @PostConstruct
    void init() {
        this.compileSemaphore = new Semaphore(maxConcurrent);
    }

    /**
     * 创建文件上传任务
     */
    public IngestTask createTask(byte[] fileBytes, String fileName, String category, String software, Long operatorId) {
        try {
            DocumentLoader loader = resolveLoader(fileName);
            String content;
            try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
                content = loader.load(is, fileName);
            }
            return createTaskFromContent(fileName, content, category, software, operatorId, "UPLOAD");
        } catch (Exception e) {
            log.error("Failed to create ingest task: {}", e.getMessage(), e);
            throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "创建任务失败");
        }
    }

    /**
     * 创建文本录入任务
     */
    public IngestTask createTextTask(String title, String content, String category, String software, Long operatorId) {
        return createTaskFromContent(title, content, category, software, operatorId, "MANUAL");
    }

    /**
     * 为已有来源创建编译任务
     */
    public IngestTask createReingestTask(Long sourceId, Long operatorId) {
        WikiSource source = sourceMapper.findById(sourceId);
        if (source == null) throw new com.middleware.manager.exception.NotFoundException(com.middleware.manager.constant.ErrorCode.DOCUMENT_NOT_FOUND, com.middleware.manager.constant.ErrorMessages.DOCUMENT_NOT_FOUND);

        IngestTask task = buildTask(source.getTitle(), sourceId, source.getContent(), operatorId);
        taskMapper.insert(task);
        return task;
    }

    @Transactional
    private IngestTask createTaskFromContent(String title, String content, String category, String software,
                                              Long operatorId, String sourceType) {
        String hash = sha256(content);

        WikiSource source = sourceMapper.findByContentHash(hash);
        if (source == null) {
            source = new WikiSource();
            source.setTitle(title);
            source.setSourceType(sourceType);
            source.setContent(content);
            source.setContentHash(hash);
            source.setCategory(category);
            source.setSoftware(software);
            sourceMapper.insert(source);
        }

        IngestTask task = buildTask(title, source.getId(), content, operatorId);
        taskMapper.insert(task);
        return task;
    }

    private IngestTask buildTask(String title, Long sourceId, String content, Long operatorId) {
        int totalChunks = calcChunks(content.length());

        IngestTask task = new IngestTask();
        task.setSourceId(sourceId);
        task.setFileName(title);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setStep("等待处理");
        task.setTotalChunks(totalChunks);
        task.setCompletedChunks(0);
        task.setPagesCreated(0);
        task.setPagesUpdated(0);
        task.setOperatorId(operatorId);
        return task;
    }

    private int calcChunks(int contentLength) {
        if (contentLength <= maxContentChars) return 1;
        return (int) Math.ceil((double) contentLength / (maxContentChars - chunkOverlap));
    }

    /**
     * 异步执行编译任务
     */
    @Async
    public void executeTask(Long taskId) {
        IngestTask task = taskMapper.findById(taskId);
        if (task == null) return;

        WikiSource source = sourceMapper.findById(task.getSourceId());
        if (source == null) {
            taskMapper.updateStatus(taskId, "FAILED", "源文档不存在");
            return;
        }

        // 保存原始状态，失败时恢复
        Boolean originalIngested = source.getIngested();

        // 等待并发槽位
        try {
            taskMapper.updateProgress(taskId, 3, "等待编译资源...", 0);
            compileSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            taskMapper.updateStatus(taskId, "FAILED", "被中断");
            return;
        }

        taskMapper.updateProgress(taskId, 5, "正在准备...", 0);

        try {
            String content = source.getContent();
            int totalChunks = task.getTotalChunks();

            if (totalChunks <= 1) {
                // 短文档，直接编译
                taskMapper.updateProgress(taskId, 10, "LLM 分析文档结构...", 0);
                IngestAgent.IngestResult result = ingestAgent.ingest(source, task.getOperatorId());
                if ("FAILED".equals(result.getStatus())) {
                    taskMapper.updateStatus(taskId, "FAILED", "LLM 编译失败");
                    return;
                }
                taskMapper.updateResult(taskId, result.getPagesCreated(), result.getPagesUpdated());
            } else {
                // 长文档，串行分段编译（串行保证每段能看到前面段的结果，合并逻辑正确）
                List<String> chunks = splitContent(content);
                int totalCreated = 0, totalUpdated = 0;

                for (int i = 0; i < chunks.size(); i++) {
                    int basePct = 10 + (i * 80 / totalChunks);
                    taskMapper.updateProgress(taskId, basePct,
                            String.format("编译分段 %d/%d...", i + 1, totalChunks), i);

                    IngestAgent.IngestResult chunkResult = ingestAgent.ingestContent(
                            chunks.get(i),
                            source.getTitle(),
                            source.getCategory(),
                            source.getSoftware(),
                            task.getOperatorId()
                    );

                    if ("FAILED".equals(chunkResult.getStatus())) {
                        log.warn("Chunk {} failed, continuing with next chunk", i + 1);
                    } else {
                        totalCreated += chunkResult.getPagesCreated();
                        totalUpdated += chunkResult.getPagesUpdated();
                    }

                    int nextPct = 10 + ((i + 1) * 80 / totalChunks);
                    taskMapper.updateProgress(taskId, nextPct,
                            String.format("分段 %d/%d 完成", i + 1, totalChunks), i + 1);
                }

                taskMapper.updateProgress(taskId, 95, "正在解析交叉引用...", totalChunks);
                taskMapper.updateResult(taskId, totalCreated, totalUpdated);
            }

            // 标记 source 已编译
            source.setIngested(true);
            sourceMapper.update(source);

            log.info("Ingest task {} completed: created={}, updated={}",
                    taskId, task.getPagesCreated(), task.getPagesUpdated());

        } catch (Exception e) {
            log.error("Ingest task {} failed: {}", taskId, e.getMessage(), e);
            taskMapper.updateStatus(taskId, "FAILED", e.getMessage());
            // 恢复原始 ingested 状态
            source.setIngested(originalIngested);
            sourceMapper.update(source);
        } finally {
            compileSemaphore.release();
        }
    }

    /**
     * 拆分内容为多个分段
     */
    private List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        int step = maxContentChars - chunkOverlap;
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxContentChars, content.length());
            chunks.add(content.substring(start, end));
            start += step;
        }
        return chunks;
    }

    public IngestTask getTask(Long id) {
        return taskMapper.findById(id);
    }

    public List<IngestTask> getAllTasks() {
        return taskMapper.findAll();
    }

    public void insertTask(IngestTask task) {
        taskMapper.insert(task);
    }

    private DocumentLoader resolveLoader(String fileName) {
        for (DocumentLoader loader : documentLoaders) {
            if (loader.supports(fileName)) return loader;
        }
        throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "不支持的文件格式");
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, com.middleware.manager.constant.ErrorMessages.SHA256_UNAVAILABLE);
        }
    }
}
