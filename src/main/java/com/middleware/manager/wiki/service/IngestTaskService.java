package com.middleware.manager.wiki.service;

import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.service.StorageService;
import com.middleware.manager.wiki.entity.IngestTask;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.IngestTaskMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class IngestTaskService {

    private final IngestTaskMapper taskMapper;
    private final WikiSourceMapper sourceMapper;
    private final IngestAgent ingestAgent;
    private final List<DocumentLoader> documentLoaders;
    private final StorageService storageService;

    @Value("${app.wiki.ingest.max-content-chars:20000}")
    private int maxContentChars;

    @Value("${app.wiki.ingest.max-concurrent:2}")
    private int maxConcurrent;

    private Semaphore compileSemaphore;

    public IngestTaskService(IngestTaskMapper taskMapper, WikiSourceMapper sourceMapper,
                             IngestAgent ingestAgent, List<DocumentLoader> documentLoaders,
                             StorageService storageService) {
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
        this.ingestAgent = ingestAgent;
        this.documentLoaders = documentLoaders;
        this.storageService = storageService;
    }

    @PostConstruct
    void init() {
        this.compileSemaphore = new Semaphore(maxConcurrent);
    }

    /**
     * 创建文件上传任务
     */
    @Transactional
    public IngestTask createTask(MultipartFile file, String category, String software, Long operatorId) {
        try {
            String fileName = file.getOriginalFilename();
            byte[] fileBytes = file.getBytes();
            DocumentLoader loader = resolveLoader(fileName);
            String content;
            try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
                content = loader.load(is, fileName);
            }
            WikiSource existing = sourceMapper.findByContentHash(sha256(content));
            String filePath = existing != null ? existing.getFilePath() : null;
            if (filePath == null || filePath.isBlank()) {
                StorageService.StoredFile storedFile = storageService.store(file, "wiki");
                filePath = storedFile.storedFileName();
            }
            return createTaskFromContent(fileName, content, category, software, operatorId,
                    "UPLOAD", filePath);
        } catch (Exception e) {
            log.error("Failed to create ingest task: {}", e.getMessage(), e);
            throw new com.middleware.manager.exception.BusinessException(
                    com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "创建任务失败");
        }
    }

    @Transactional
    public IngestTask createTask(byte[] fileBytes, String fileName, String category, String software, Long operatorId) {
        return createTask(fileBytes, fileName, category, software, operatorId, null);
    }

    private IngestTask createTask(byte[] fileBytes, String fileName, String category, String software,
                                  Long operatorId, String filePath) {
        try {
            DocumentLoader loader = resolveLoader(fileName);
            String content;
            try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
                content = loader.load(is, fileName);
            }
            return createTaskFromContent(fileName, content, category, software, operatorId, "UPLOAD", filePath);
        } catch (Exception e) {
            log.error("Failed to create ingest task: {}", e.getMessage(), e);
            throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "创建任务失败");
        }
    }

    /**
     * 创建文本录入任务
     */
    @Transactional
    public IngestTask createTextTask(String title, String content, String category, String software, Long operatorId) {
        return createTaskFromContent(title, content, category, software, operatorId, "MANUAL", null);
    }

    /**
     * 为已有来源创建编译任务
     */
    @Transactional
    public IngestTask createReingestTask(Long sourceId, Long operatorId) {
        WikiSource source = sourceMapper.findById(sourceId);
        if (source == null) throw new com.middleware.manager.exception.NotFoundException(com.middleware.manager.constant.ErrorCode.DOCUMENT_NOT_FOUND, com.middleware.manager.constant.ErrorMessages.DOCUMENT_NOT_FOUND);

        IngestTask task = buildTask(source.getTitle(), sourceId, source.getContent(), operatorId);
        taskMapper.insert(task);
        return task;
    }

    private IngestTask createTaskFromContent(String title, String content, String category, String software,
                                              Long operatorId, String sourceType, String filePath) {
        String hash = sha256(content);

        WikiSource source = sourceMapper.findByContentHash(hash);
        if (source == null) {
            source = new WikiSource();
            source.setTitle(title);
            source.setSourceType(sourceType);
            source.setContent(content);
            source.setContentHash(hash);
            source.setFilePath(filePath);
            source.setCategory(category);
            source.setSoftware(software);
            source.setCreatedBy(operatorId);
            sourceMapper.insert(source);
        } else if ((source.getCategory() == null && category != null)
                || (source.getSoftware() == null && software != null)
                || (source.getFilePath() == null && filePath != null)) {
            if (source.getCategory() == null && category != null) source.setCategory(category);
            if (source.getSoftware() == null && software != null) source.setSoftware(software);
            if (source.getFilePath() == null && filePath != null) source.setFilePath(filePath);
            sourceMapper.update(source);
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
        return (int) Math.ceil((double) contentLength / maxContentChars);
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
        LocalDateTime originalIngestedAt = source.getIngestedAt();

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
            executePlannedTask(taskId, task, source);

        } catch (Exception e) {
            log.error("Ingest task {} failed: {}", taskId, e.getMessage(), e);
            taskMapper.updateStatus(taskId, "FAILED", ErrorMessages.WIKI_INGEST_TASK_FAILED);
            // 恢复原始 ingested 状态
            source.setIngested(originalIngested);
            source.setIngestedAt(originalIngestedAt);
            sourceMapper.update(source);
        } finally {
            compileSemaphore.release();
        }
    }

    private void executePlannedTask(Long taskId, IngestTask task, WikiSource source) {
        int totalChunks = task.getTotalChunks();

        taskMapper.updateProgress(taskId, 10, "正在抽取文档类型和目录结构...", 0);
        taskMapper.updateProgress(taskId, 25, "正在生成章节事实和页面计划...", 0);
        IngestAgent.IngestResult result = ingestAgent.ingestPlanned(source, task.getOperatorId());
        if ("FAILED".equals(result.getStatus())) {
            markSourceNotIngested(source);
            taskMapper.updateStatus(taskId, "FAILED", failureMessage(result, ErrorMessages.WIKI_INGEST_FAILED));
            return;
        }
        if (result.getPagesCreated() + result.getPagesUpdated() <= 0 && !"SKIPPED".equals(result.getStatus())) {
            markSourceNotIngested(source);
            taskMapper.updateStatus(taskId, "FAILED", ErrorMessages.WIKI_INGEST_EMPTY_RESULT);
            return;
        }

        taskMapper.updateProgress(taskId, 90, "正在解析交叉引用并执行质量门禁...", totalChunks);
        taskMapper.updateResult(taskId, result.getPagesCreated(), result.getPagesUpdated());
        if ("PARTIAL".equals(result.getStatus())) {
            taskMapper.updateStatus(taskId, "PARTIAL",
                    failureMessage(result, ErrorMessages.WIKI_QUALITY_GATE_PARTIAL));
        }
        markSourceCompiled(source, result);
        log.info("Ingest task {} completed: created={}, updated={}",
                taskId, result.getPagesCreated(), result.getPagesUpdated());
    }

    private void markSourceNotIngested(WikiSource source) {
        source.setIngested(false);
        source.setIngestedAt(null);
        sourceMapper.update(source);
    }

    private void markSourceCompiled(WikiSource source, IngestAgent.IngestResult result) {
        source.setIngested("SUCCESS".equals(result.getStatus()) || "SKIPPED".equals(result.getStatus()));
        source.setIngestedAt(Boolean.TRUE.equals(source.getIngested()) ? LocalDateTime.now() : null);
        sourceMapper.update(source);
    }

    private String failureMessage(IngestAgent.IngestResult result, String fallback) {
        if (result == null || result.getErrorMessage() == null || result.getErrorMessage().isBlank()) {
            return fallback;
        }
        return result.getErrorMessage();
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
