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
    private final IngestProgressHelper progressHelper;

    @Value("${app.wiki.ingest.max-content-chars:20000}")
    private int maxContentChars;

    @Value("${app.wiki.ingest.max-concurrent:2}")
    private int maxConcurrent;

    private Semaphore compileSemaphore;

    public IngestTaskService(IngestTaskMapper taskMapper, WikiSourceMapper sourceMapper,
                             IngestAgent ingestAgent, List<DocumentLoader> documentLoaders,
                             StorageService storageService, IngestProgressHelper progressHelper) {
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
        this.ingestAgent = ingestAgent;
        this.documentLoaders = documentLoaders;
        this.storageService = storageService;
        this.progressHelper = progressHelper;
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

        IngestAgent.IngestResult result = ingestAgent.ingestPlanned(source, task.getOperatorId(),
                // 使用 REQUIRES_NEW 事务传播，进度更新独立提交，前端可实时看到
                (progress, step, completedUnits, totalUnits) -> {
                    if (totalUnits > 0) {
                        progressHelper.updateProgressWithTotal(taskId, progress, step, completedUnits, totalUnits);
                    } else {
                        progressHelper.updateProgress(taskId, progress, step, completedUnits);
                    }
                },
                (type, json) -> {
                    if ("sectionFacts".equals(type)) {
                        progressHelper.updateSectionFacts(taskId, json);
                    } else if ("pagePlan".equals(type)) {
                        progressHelper.updatePagePlan(taskId, json);
                    }
                },
                () -> isTaskPaused(taskId));

        // PAUSED 状态：保留已有进度，不标记完成
        if ("PAUSED".equals(result.getStatus())) {
            log.info("Ingest task {} paused", taskId);
            return;
        }

        persistQualityReport(taskId, result);
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

        int latestTotalChunks = latestTotalChunks(taskId, totalChunks);
        taskMapper.updateProgress(taskId, 90, "正在解析交叉引用并执行质量门禁...", latestTotalChunks);
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

    private int latestTotalChunks(Long taskId, int fallback) {
        IngestTask latest = taskMapper.findById(taskId);
        if (latest == null || latest.getTotalChunks() == null || latest.getTotalChunks() <= 0) {
            return fallback;
        }
        return latest.getTotalChunks();
    }

    private void markSourceCompiled(WikiSource source, IngestAgent.IngestResult result) {
        source.setIngested("SUCCESS".equals(result.getStatus()) || "SKIPPED".equals(result.getStatus()));
        source.setIngestedAt(Boolean.TRUE.equals(source.getIngested()) ? LocalDateTime.now() : null);
        sourceMapper.update(source);
    }

    private void persistQualityReport(Long taskId, IngestAgent.IngestResult result) {
        if (result != null && result.getQualityReport() != null && !result.getQualityReport().isBlank()) {
            taskMapper.updateQualityReport(taskId, result.getQualityReport());
        }
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

    /**
     * 重编译过度压缩页面：只重新生成 quality_report.overCompressedPages 中的页面。
     * 复用已持久化的 section_facts 和 page_plan，跳过最耗时的步骤。
     */
    @Async
    public void recompileCompressed(Long taskId) {
        IngestTask task = taskMapper.findById(taskId);
        if (task == null) return;

        WikiSource source = sourceMapper.findById(task.getSourceId());
        if (source == null) {
            taskMapper.updateStatus(taskId, "FAILED", "源文档不存在");
            return;
        }

        compileSemaphore.acquireUninterruptibly();
        try {
            progressHelper.updateProgress(taskId, 5, "正在准备重编译过度压缩页面...", 0);

            IngestAgent.IngestResult result = ingestAgent.recompileCompressedPages(
                    source, task.getOperatorId(),
                    task.getQualityReport(), task.getSectionFacts(), task.getPagePlan(),
                    (progress, step, completedUnits, totalUnits) -> {
                        if (totalUnits > 0) {
                            progressHelper.updateProgressWithTotal(taskId, progress, step, completedUnits, totalUnits);
                        } else {
                            progressHelper.updateProgress(taskId, progress, step, completedUnits);
                        }
                    });

            if ("FAILED".equals(result.getStatus())) {
                taskMapper.updateStatus(taskId, "FAILED", result.getErrorMessage());
                return;
            }
            if ("SKIPPED".equals(result.getStatus())) {
                progressHelper.updateProgress(taskId, safeProgress(task), result.getErrorMessage(), safeCompletedChunks(task));
                taskMapper.updateStatus(taskId, restoreStatus(task), result.getErrorMessage());
                return;
            }

            progressHelper.updateProgress(taskId, 95, "正在更新质量报告...", 0);
            persistQualityReport(taskId, result);
            taskMapper.updateResult(taskId, result.getPagesCreated(), result.getPagesUpdated());
            if ("PARTIAL".equals(result.getStatus())) {
                taskMapper.updateStatus(taskId, "PARTIAL", failureMessage(result, ErrorMessages.WIKI_QUALITY_GATE_PARTIAL));
            }
            markSourceCompiled(source, result);
            log.info("Recompile compressed task {} completed: status={}, created={}, updated={}",
                    taskId, result.getStatus(), result.getPagesCreated(), result.getPagesUpdated());
        } catch (Exception e) {
            log.error("Recompile compressed task {} failed: {}", taskId, e.getMessage(), e);
            taskMapper.updateStatus(taskId, "FAILED", ErrorMessages.WIKI_RECOMPILE_ERROR);
        } finally {
            compileSemaphore.release();
        }
    }

    /**
     * 重编译缺失章节：只重新生成 quality_report.missingSections 对应的页面。
     * 复用已持久化的 section_facts 和 page_plan，跳过最耗时的步骤。
     */
    @Async
    public void recompileMissing(Long taskId) {
        IngestTask task = taskMapper.findById(taskId);
        if (task == null) return;

        WikiSource source = sourceMapper.findById(task.getSourceId());
        if (source == null) {
            taskMapper.updateStatus(taskId, "FAILED", "源文档不存在");
            return;
        }

        compileSemaphore.acquireUninterruptibly();
        try {
            progressHelper.updateProgress(taskId, 5, "正在准备重编译缺失章节...", 0);

            IngestAgent.IngestResult result = ingestAgent.recompileMissingSections(
                    source, task.getOperatorId(),
                    task.getQualityReport(), task.getSectionFacts(), task.getPagePlan(),
                    (progress, step, completedUnits, totalUnits) -> {
                        if (totalUnits > 0) {
                            progressHelper.updateProgressWithTotal(taskId, progress, step, completedUnits, totalUnits);
                        } else {
                            progressHelper.updateProgress(taskId, progress, step, completedUnits);
                        }
                    });

            if ("FAILED".equals(result.getStatus())) {
                taskMapper.updateStatus(taskId, "FAILED", result.getErrorMessage());
                return;
            }
            if ("SKIPPED".equals(result.getStatus())) {
                progressHelper.updateProgress(taskId, safeProgress(task), result.getErrorMessage(), safeCompletedChunks(task));
                taskMapper.updateStatus(taskId, restoreStatus(task), result.getErrorMessage());
                return;
            }

            progressHelper.updateProgress(taskId, 95, "正在更新质量报告...", 0);
            persistQualityReport(taskId, result);
            taskMapper.updateResult(taskId, result.getPagesCreated(), result.getPagesUpdated());
            if ("PARTIAL".equals(result.getStatus())) {
                taskMapper.updateStatus(taskId, "PARTIAL", failureMessage(result, ErrorMessages.WIKI_QUALITY_GATE_PARTIAL));
            }
            markSourceCompiled(source, result);
            log.info("Recompile missing task {} completed: status={}, created={}, updated={}",
                    taskId, result.getStatus(), result.getPagesCreated(), result.getPagesUpdated());
        } catch (Exception e) {
            log.error("Recompile missing task {} failed: {}", taskId, e.getMessage(), e);
            taskMapper.updateStatus(taskId, "FAILED", ErrorMessages.WIKI_RECOMPILE_ERROR);
        } finally {
            compileSemaphore.release();
        }
    }

    /**
     * 暂停任务：更新状态为 PAUSED，IngestAgent 在批次间检查此状态后自动暂停。
     */
    public void pauseTask(Long taskId) {
        IngestTask task = taskMapper.findById(taskId);
        if (task == null || !"PROCESSING".equals(task.getStatus())) {
            throw new com.middleware.manager.exception.BusinessException(
                    com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "只能暂停正在执行的任务");
        }
        taskMapper.updateStatus(taskId, "PAUSED", null);
        log.info("Task {} paused", taskId);
    }

    /**
     * 继续任务：从暂停点继续执行，复用已有的 section_facts 和 page_plan。
     */
    @Async
    public void resumeTask(Long taskId) {
        IngestTask task = taskMapper.findById(taskId);
        if (task == null || !"PAUSED".equals(task.getStatus())) {
            return;
        }

        WikiSource source = sourceMapper.findById(task.getSourceId());
        if (source == null) {
            taskMapper.updateStatus(taskId, "FAILED", "源文档不存在");
            return;
        }

        compileSemaphore.acquireUninterruptibly();
        try {
            // 恢复为 PROCESSING 状态
            taskMapper.updateStatus(taskId, "PROCESSING", null);
            progressHelper.updateProgress(taskId, task.getProgress(), "正在继续执行...", task.getCompletedChunks());

            // 重新触发编译（会从头开始，但复用已有的 section_facts）
            executePlannedTask(taskId, task, source);
        } catch (Exception e) {
            log.error("Resume task {} failed: {}", taskId, e.getMessage(), e);
            taskMapper.updateStatus(taskId, "FAILED", "继续执行失败");
        } finally {
            compileSemaphore.release();
        }
    }

    /**
     * 检查任务是否已暂停（供 IngestAgent 在批次间调用）。
     */
    public boolean isTaskPaused(Long taskId) {
        IngestTask task = taskMapper.findById(taskId);
        return task != null && "PAUSED".equals(task.getStatus());
    }

    private int safeProgress(IngestTask task) {
        return task != null && task.getProgress() != null ? task.getProgress() : 0;
    }

    private int safeCompletedChunks(IngestTask task) {
        return task != null && task.getCompletedChunks() != null ? task.getCompletedChunks() : 0;
    }

    private String restoreStatus(IngestTask task) {
        if (task == null || task.getStatus() == null || "PROCESSING".equals(task.getStatus())) {
            return "PARTIAL";
        }
        return task.getStatus();
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
