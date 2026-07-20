package com.middleware.manager.wiki.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.middleware.manager.knowledge.embedding.EmbeddingProvider;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.SoftwareTypeMapper;
import com.middleware.manager.wiki.entity.WikiIngestLog;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiIngestLogMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class IngestAgent {
    private static final int MAX_RETRIES = 3;
    private static final int SECTION_FACTS_BATCH_SIZE = 12;
    private static final int SECTION_FACTS_BATCH_CHAR_LIMIT = 12000;
    private static final int TITLE_ONLY_EXCERPT_MAX_LENGTH = 120;
    private static final int LOW_INFORMATION_EXCERPT_MAX_LENGTH = 50;
    private static final int PAGE_GENERATION_BATCH_SIZE = 4;
    private static final int SOURCE_VECTOR_CHUNK_CHARS = 1200;
    private static final int SOURCE_VECTOR_CHUNK_OVERLAP = 120;
    private static final int SOURCE_VECTOR_DELETE_LIMIT = 2048;
    private static final List<String> SECTION_FACT_OPERATION_SIGNALS = List.of(
            "配置", "安装", "启动", "停止", "部署", "验证", "创建", "删除", "更新", "修改",
            "命令", "参数", "端口", "路径", "文件", "脚本", "报错", "错误", "故障", "异常",
            "监控", "阈值", "日志", "license", "http", "https", "jdbc", "jndi", "session",
            "worker", "apache", "tongweb", "ths", "tdg");
    private static final Set<String> VALID_PAGE_TYPES = Set.of(
            "ENTITY", "CONCEPT", "RUNBOOK", "EXPERIENCE", "STANDARD", "SYNTHESIS", "OVERVIEW");

    private final ChatModel chatModel;
    private final WikiPageMapper pageMapper;
    private final WikiSourceMapper sourceMapper;
    private final WikiIngestLogMapper logMapper;
    private final LinkResolver linkResolver;
    private final EmbeddingProvider embeddingService;
    private final VectorStore vectorStore;
    private final SoftwareTypeMapper softwareTypeMapper;
    private final DocumentTypeClassifier documentTypeClassifier;
    private final DocumentOutlineExtractor documentOutlineExtractor;
    private final WikiIngestQualityGate qualityGate;
    private final Gson gson = new Gson();
    private final int maxContentChars;
    private final int llmBatchConcurrency;
    private final ExecutorService llmBatchExecutor;

    @Value("${app.vector.type:milvus}")
    private String vectorType;

    public IngestAgent(ChatModel chatModel,
                       WikiPageMapper pageMapper,
                       WikiSourceMapper sourceMapper,
                       WikiIngestLogMapper logMapper,
                       LinkResolver linkResolver,
                       EmbeddingProvider embeddingService,
                       VectorStore vectorStore,
                       SoftwareTypeMapper softwareTypeMapper,
                       DocumentTypeClassifier documentTypeClassifier,
                       DocumentOutlineExtractor documentOutlineExtractor,
                       WikiIngestQualityGate qualityGate,
                       @Value("${app.wiki.ingest.max-content-chars:50000}") int maxContentChars,
                       @Value("${app.llm.max-concurrent:5}") int llmBatchConcurrency) {
        this.chatModel = chatModel;
        this.pageMapper = pageMapper;
        this.sourceMapper = sourceMapper;
        this.logMapper = logMapper;
        this.linkResolver = linkResolver;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.softwareTypeMapper = softwareTypeMapper;
        this.documentTypeClassifier = documentTypeClassifier;
        this.documentOutlineExtractor = documentOutlineExtractor;
        this.qualityGate = qualityGate;
        this.maxContentChars = maxContentChars;
        this.llmBatchConcurrency = Math.max(1, llmBatchConcurrency);
        this.llmBatchExecutor = Executors.newFixedThreadPool(this.llmBatchConcurrency);
    }

    @PreDestroy
    public void shutdownLlmBatchExecutor() {
        llmBatchExecutor.shutdownNow();
    }

    public static class IngestResult {
        private int pagesCreated;
        private int pagesUpdated;
        private int linksCreated;
        private int contradictionsFound;
        private String status;
        private String errorMessage;
        private String qualityReport;

        public int getPagesCreated() { return pagesCreated; }
        public int getPagesUpdated() { return pagesUpdated; }
        public int getLinksCreated() { return linksCreated; }
        public int getContradictionsFound() { return contradictionsFound; }
        public String getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }
        public String getQualityReport() { return qualityReport; }
        public void setPagesCreated(int v) { this.pagesCreated = v; }
        public void setPagesUpdated(int v) { this.pagesUpdated = v; }
        public void setLinksCreated(int v) { this.linksCreated = v; }
        public void setContradictionsFound(int v) { this.contradictionsFound = v; }
        public void setStatus(String v) { this.status = v; }
        public void setErrorMessage(String v) { this.errorMessage = v; }
        public void setQualityReport(String v) { this.qualityReport = v; }
    }

    @FunctionalInterface
    public interface ProgressReporter {
        void report(int progress, String step, int completedUnits, int totalUnits);
    }

    private static final ProgressReporter NOOP_PROGRESS = (progress, step, completedUnits, totalUnits) -> {
    };

    /**
     * 暂停检查器：返回 true 表示任务已暂停，应停止执行。
     */
    @FunctionalInterface
    public interface PauseChecker {
        boolean isPaused();
    }

    private static final PauseChecker NO_PAUSE = () -> false;

    /**
     * LLM 调用指标收集器，线程安全。
     */
    static class LlmMetrics {
        private int callCount;
        private int retryCount;
        private int inputTokens;
        private int outputTokens;

        synchronized void recordCall() { callCount++; }
        synchronized void recordRetry() { retryCount++; }
        synchronized void addTokens(int input, int output) { inputTokens += input; outputTokens += output; }
        synchronized int getCallCount() { return callCount; }
        synchronized int getRetryCount() { return retryCount; }
        synchronized int getInputTokens() { return inputTokens; }
        synchronized int getOutputTokens() { return outputTokens; }
    }

    @Transactional
    public IngestResult ingestPlanned(WikiSource source, Long operatorId) {
        return ingestPlanned(source, operatorId, NOOP_PROGRESS, null);
    }

    @Transactional
    public IngestResult ingestPlanned(WikiSource source, Long operatorId, ProgressReporter progressReporter) {
        return ingestPlanned(source, operatorId, progressReporter, null);
    }

    @Transactional
    public IngestResult ingestPlanned(WikiSource source, Long operatorId,
                                       ProgressReporter progressReporter,
                                       java.util.function.BiConsumer<String, String> artifactSink) {
        return ingestPlanned(source, operatorId, progressReporter, artifactSink, NO_PAUSE);
    }

    @Transactional
    public IngestResult ingestPlanned(WikiSource source, Long operatorId,
                                       ProgressReporter progressReporter,
                                       java.util.function.BiConsumer<String, String> artifactSink,
                                       PauseChecker pauseChecker) {
        long startTime = System.currentTimeMillis();
        IngestResult result = new IngestResult();
        WikiIngestLog ingestLog = new WikiIngestLog();
        ingestLog.setSourceId(source.getId());
        ingestLog.setOperatorId(operatorId);
        ProgressReporter progress = progressReporter == null ? NOOP_PROGRESS : progressReporter;
        PauseChecker checker = pauseChecker == null ? NO_PAUSE : pauseChecker;

        try {
            String content = source.getContent();
            String hash = sha256(content);
            if (hash.equals(source.getContentHash()) && Boolean.TRUE.equals(source.getIngested())) {
                log.info("Source '{}' content unchanged, skipping planned ingest", source.getTitle());
                result.setStatus("SKIPPED");
                return result;
            }

            PlannedPages plannedPages = generatePlannedPages(source, content, progress, artifactSink, checker);
            if ("PAUSED".equals(plannedPages.report().getStatus())) {
                result.setStatus("PAUSED");
                result.setErrorMessage(ErrorMessages.WIKI_TASK_PAUSED);
                return result;
            }
            result.setQualityReport(gson.toJson(plannedPages.report()));
            if ("FAILED".equals(plannedPages.report().getStatus())) {
                return failPlanned(result, ingestLog, startTime,
                        ErrorMessages.WIKI_QUALITY_GATE_FAILED + "：" + summarizeQuality(plannedPages.report()));
            }

            SaveStats stats = savePages(plannedPages.pages(), source);
            int linksCreated = linkResolver.resolveLinks(stats.savedPages());
            completePlannedIngest(source, hash, plannedPages.report(), stats, linksCreated, result);
            vectorizeSource(source);
            writePlannedLog(ingestLog, plannedPages.report(), stats, linksCreated, result, startTime);

            log.info("Planned ingest completed for '{}': status={}, created={}, updated={}, links={}",
                    source.getTitle(), result.getStatus(), stats.created(), stats.updated(), linksCreated);
        } catch (PlannedIngestException e) {
            if (ErrorMessages.WIKI_TASK_PAUSED.equals(e.getMessage())) {
                result.setStatus("PAUSED");
                result.setErrorMessage(ErrorMessages.WIKI_TASK_PAUSED);
                return result;
            }
            return failPlanned(result, ingestLog, startTime, e.getMessage());
        } catch (Exception e) {
            log.error("Planned ingest failed for source '{}': {}", source.getTitle(), e.getMessage(), e);
            result.setStatus("FAILED");
            result.setErrorMessage(ErrorMessages.WIKI_INGEST_TASK_FAILED);
            ingestLog.setStatus("FAILED");
            ingestLog.setErrorDetail(ErrorMessages.WIKI_INGEST_TASK_FAILED);
            ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
            logMapper.insert(ingestLog);
        }
        return result;
    }

    private PlannedPages generatePlannedPages(WikiSource source, String content, ProgressReporter progress,
                                               java.util.function.BiConsumer<String, String> artifactSink,
                                               PauseChecker pauseChecker) {
        long totalStart = System.currentTimeMillis();
        LlmMetrics llmMetrics = new LlmMetrics();

        // 暂停检查
        if (pauseChecker.isPaused()) {
            WikiIngestQualityGate.QualityReport pausedReport = new WikiIngestQualityGate.QualityReport();
            pausedReport.setStatus("PAUSED");
            return new PlannedPages(new JsonArray(), pausedReport);
        }

        long stepStart = System.currentTimeMillis();
        progress.report(10, "正在抽取文档类型和目录结构...", 0, 0);
        log.info("Planned ingest Step 0: Extracting outline for source '{}'", source.getTitle());
        DocumentTypeClassifier.Classification classification =
                documentTypeClassifier.classify(source.getTitle(), content);
        DocumentOutlineExtractor.DocumentOutline outline = documentOutlineExtractor.extract(
                source.getTitle(), content, source.getCategory(), source.getSoftware(), classification);
        String outlineJson = toCompactOutlineJson(outline, outline.getSections(), 260);
        long outlineMs = System.currentTimeMillis() - stepStart;
        log.info("Planned ingest Step 0 completed for source '{}': sections={}, durationMs={}",
                source.getTitle(), outline.getSections().size(), outlineMs);

        // 暂停检查
        if (pauseChecker.isPaused()) {
            WikiIngestQualityGate.QualityReport pausedReport = new WikiIngestQualityGate.QualityReport();
            pausedReport.setStatus("PAUSED");
            return new PlannedPages(new JsonArray(), pausedReport);
        }

        log.info("Planned ingest Step 1: Extracting section facts for source '{}'", source.getTitle());
        stepStart = System.currentTimeMillis();
        JsonObject sectionFacts = generateSectionFacts(outline, progress, llmMetrics, pauseChecker);
        if (pauseChecker.isPaused()) {
            WikiIngestQualityGate.QualityReport pausedReport = new WikiIngestQualityGate.QualityReport();
            pausedReport.setStatus("PAUSED");
            return new PlannedPages(new JsonArray(), pausedReport);
        }
        String sectionFactsJson = gson.toJson(sectionFacts);
        long sectionFactsMs = System.currentTimeMillis() - stepStart;
        if (artifactSink != null) {
            artifactSink.accept("sectionFacts", sectionFactsJson);
        }

        // 暂停检查
        if (pauseChecker.isPaused()) {
            WikiIngestQualityGate.QualityReport pausedReport = new WikiIngestQualityGate.QualityReport();
            pausedReport.setStatus("PAUSED");
            return new PlannedPages(new JsonArray(), pausedReport);
        }

        stepStart = System.currentTimeMillis();
        JsonObject pagePlan = generatePagePlan(source, outlineJson, sectionFactsJson, outline, progress, llmMetrics);
        long pagePlanMs = System.currentTimeMillis() - stepStart;
        if (artifactSink != null) {
            artifactSink.accept("pagePlan", gson.toJson(pagePlan));
        }
        validatePagePlanCoverage(pagePlan, outline);

        // 暂停检查
        if (pauseChecker.isPaused()) {
            WikiIngestQualityGate.QualityReport pausedReport = new WikiIngestQualityGate.QualityReport();
            pausedReport.setStatus("PAUSED");
            return new PlannedPages(new JsonArray(), pausedReport);
        }

        stepStart = System.currentTimeMillis();
        JsonArray pages = generatePagesFromPlan(source, outline, sectionFacts, pagePlan, progress, llmMetrics, pauseChecker);
        long pageGenerationMs = System.currentTimeMillis() - stepStart;

        // 暂停检查
        if (pauseChecker.isPaused()) {
            WikiIngestQualityGate.QualityReport pausedReport = new WikiIngestQualityGate.QualityReport();
            pausedReport.setStatus("PAUSED");
            return new PlannedPages(new JsonArray(), pausedReport);
        }

        progress.report(82, "正在补充来源引用并执行页面校验...", 0, 0);
        enrichPagesWithPlan(pages, pagePlan.getAsJsonArray("pages"), outline, source);
        validateGeneratedPages(pages);

        progress.report(86, "正在执行质量门禁...", 0, 0);
        stepStart = System.currentTimeMillis();
        WikiIngestQualityGate.QualityReport report = qualityGate.evaluate(outline, pages);
        long qualityGateMs = System.currentTimeMillis() - stepStart;

        report.setTotalDurationMs(System.currentTimeMillis() - totalStart);
        report.setOutlineDurationMs(outlineMs);
        report.setSectionFactsDurationMs(sectionFactsMs);
        report.setPagePlanDurationMs(pagePlanMs);
        report.setPageGenerationDurationMs(pageGenerationMs);
        report.setQualityGateDurationMs(qualityGateMs);
        report.setLlmCallCount(llmMetrics.getCallCount());
        report.setLlmRetryCount(llmMetrics.getRetryCount());
        report.setLlmInputTokens(llmMetrics.getInputTokens());
        report.setLlmOutputTokens(llmMetrics.getOutputTokens());

        log.info("Planned ingest timing sourceId={} outlineMs={} sectionFactsMs={} pagePlanMs={} pageGenerationMs={} qualityGateMs={} totalMs={} llmCalls={} llmRetries={}",
                source.getId(), outlineMs, sectionFactsMs, pagePlanMs, pageGenerationMs, qualityGateMs,
                report.getTotalDurationMs(), llmMetrics.getCallCount(), llmMetrics.getRetryCount());
        return new PlannedPages(pages, report);
    }

    private JsonObject generatePagePlan(WikiSource source, String outlineJson, String sectionFactsJson,
                                        DocumentOutlineExtractor.DocumentOutline outline, ProgressReporter progress,
                                        LlmMetrics llmMetrics) {
        long start = System.currentTimeMillis();
        progress.report(55, "正在生成页面计划...", 0, 0);
        log.info("Planned ingest Step 2: Planning pages for source '{}'", source.getTitle());
        String existingSummary = buildExistingPagesSummary(source.getCategory(), source.getSoftware());
        String softwareRef = buildSoftwareReference();
        String pagePlanJson = callLlm(IngestPromptTemplates.buildPagePlanPrompt(
                outlineJson, sectionFactsJson, existingSummary, softwareRef), llmMetrics);
        JsonObject pagePlan = parseJson(pagePlanJson);
        if (pagePlan == null || !pagePlan.has("pages") || !pagePlan.get("pages").isJsonArray()
                || pagePlan.getAsJsonArray("pages").isEmpty()) {
            log.warn("Page plan JSON invalid for source '{}', using deterministic fallback", source.getTitle());
            return fallbackPagePlan(source, outline);
        }
        repairPagePlanCoverage(source, pagePlan, outline);
        log.info("Planned ingest Step 2 completed for source '{}': plannedPages={}, durationMs={}",
                source.getTitle(), pagePlan.getAsJsonArray("pages").size(), System.currentTimeMillis() - start);
        return pagePlan;
    }

    private JsonArray generatePagesFromPlan(WikiSource source, DocumentOutlineExtractor.DocumentOutline outline,
                                            JsonObject sectionFacts, JsonObject pagePlan, ProgressReporter progress,
                                            LlmMetrics llmMetrics, PauseChecker pauseChecker) {
        log.info("Planned ingest Step 3: Generating planned pages for source '{}'", source.getTitle());
        JsonArray plans = pagePlan.getAsJsonArray("pages");
        if (plans == null || plans.isEmpty()) {
            throw new PlannedIngestException(ErrorMessages.WIKI_PAGE_GENERATION_FAILED);
        }
        List<JsonArray> planBatches = partitionPlanBatch(plans);
        progress.report(65, "正在按页面计划生成页面 0/" + planBatches.size(), 0, planBatches.size());

        Map<Integer, JsonArray> resultsByIndex = new LinkedHashMap<>();
        CompletionService<BatchPagesResult> completionService = new ExecutorCompletionService<>(llmBatchExecutor);
        List<Future<BatchPagesResult>> futures = new ArrayList<>();
        for (int i = 0; i < planBatches.size(); i++) {
            if (pauseChecker != null && pauseChecker.isPaused()) {
                throw new PlannedIngestException(ErrorMessages.WIKI_TASK_PAUSED);
            }
            final int batchIndex = i;
            final JsonArray planBatch = planBatches.get(batchIndex);
            futures.add(completionService.submit(() -> generatePageBatch(source, outline, sectionFacts, planBatch,
                    batchIndex, planBatches.size(), llmMetrics)));
        }

        int completed = 0;
        try {
            for (int i = 0; i < planBatches.size(); i++) {
                if (pauseChecker != null && pauseChecker.isPaused()) {
                    cancelFutures(futures);
                    throw new PlannedIngestException(ErrorMessages.WIKI_TASK_PAUSED);
                }
                Future<BatchPagesResult> future = completionService.take();
                BatchPagesResult result = future.get();
                resultsByIndex.put(result.batchIndex(), result.pages());
                completed++;
                progress.report(progressBetween(65, 80, completed, planBatches.size()),
                        "正在按页面计划生成页面 " + completed + "/" + planBatches.size(),
                        completed, planBatches.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PlannedIngestException(ErrorMessages.WIKI_PAGE_GENERATION_FAILED);
        } catch (ExecutionException e) {
            log.warn("Page generation batch failed unexpectedly: {}", e.getMessage());
            throw new PlannedIngestException(ErrorMessages.WIKI_PAGE_GENERATION_FAILED);
        }

        JsonArray generatedPages = new JsonArray();
        for (int i = 0; i < planBatches.size(); i++) {
            JsonArray batchResult = resultsByIndex.get(i);
            if (batchResult != null) {
                for (JsonElement page : batchResult) {
                    generatedPages.add(page);
                }
            }
        }

        if (generatedPages.isEmpty()) {
            throw new PlannedIngestException(ErrorMessages.WIKI_PAGE_GENERATION_FAILED);
        }
        progress.report(80, "页面生成完成，正在校验质量...", planBatches.size(), planBatches.size());
        return generatedPages;
    }

    private void cancelFutures(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private BatchPagesResult generatePageBatch(WikiSource source,
                                               DocumentOutlineExtractor.DocumentOutline outline,
                                               JsonObject sectionFacts,
                                               JsonArray planBatch,
                                               int batchIndex,
                                               int totalBatches,
                                               LlmMetrics llmMetrics) {
        long batchStart = System.currentTimeMillis();
        try {
            Set<String> sectionIds = coveredSectionIds(planBatch);
            String outlineJson = toCompactOutlineJson(outline, sectionsByIds(outline, sectionIds), 500);
            String sectionFactsJson = gson.toJson(filterSectionFacts(sectionFacts, sectionIds));
            JsonObject batchPlan = new JsonObject();
            batchPlan.add("pages", planBatch.deepCopy());
            String pagesJson = callLlm(IngestPromptTemplates.buildPlannedPageGenerationPrompt(
                    outlineJson, sectionFactsJson, gson.toJson(batchPlan), buildSourceMetaJson(source)), llmMetrics);
            JsonObject pagesResult = parseJson(pagesJson);
            JsonArray pages = pagesResult != null && pagesResult.has("pages") && pagesResult.get("pages").isJsonArray()
                    ? pagesResult.getAsJsonArray("pages")
                    : null;
            if (pages == null || pages.isEmpty()) {
                log.warn("Planned page JSON invalid for source '{}', using deterministic fallback for {} plans",
                        source.getTitle(), planBatch.size());
                pages = fallbackPagesFromPlan(source, planBatch, outline, sectionFacts);
            }
            log.info("Planned ingest Step 3 batch completed for source '{}': batch={}/{}, plans={}, pages={}, durationMs={}",
                    source.getTitle(), batchIndex + 1, totalBatches, planBatch.size(), pages.size(),
                    System.currentTimeMillis() - batchStart);
            return new BatchPagesResult(batchIndex, pages);
        } catch (Exception e) {
            JsonArray fallback = fallbackPagesFromPlan(source, planBatch, outline, sectionFacts);
            log.warn("Planned page batch failed for source '{}', using deterministic fallback: batch={}/{}, plans={}, pages={}, error={}",
                    source.getTitle(), batchIndex + 1, totalBatches, planBatch.size(), fallback.size(), e.getMessage());
            return new BatchPagesResult(batchIndex, fallback);
        }
    }

    private JsonObject generateSectionFacts(DocumentOutlineExtractor.DocumentOutline outline, ProgressReporter progress,
                                            LlmMetrics llmMetrics, PauseChecker pauseChecker) {
        long start = System.currentTimeMillis();
        List<List<DocumentOutlineExtractor.DocumentSection>> batches = partitionSectionFacts(outline.getSections());
        progress.report(25, "正在生成章节事实 0/" + batches.size(), 0, batches.size());

        // 按 batch 顺序存放结果
        Map<Integer, JsonArray> resultsByIndex = new LinkedHashMap<>();
        List<Integer> llmBatchIndices = new ArrayList<>();
        int localOnlySections = 0;

        // 先处理本地 batch（无需 LLM）
        for (int i = 0; i < batches.size(); i++) {
            // 暂停检查
            if (pauseChecker.isPaused()) {
                throw new PlannedIngestException(ErrorMessages.WIKI_TASK_PAUSED);
            }
            List<DocumentOutlineExtractor.DocumentSection> batch = batches.get(i);
            if (batch.stream().allMatch(this::canUseLocalSectionFact)) {
                JsonArray facts = new JsonArray();
                for (DocumentOutlineExtractor.DocumentSection section : batch) {
                    facts.add(fallbackSectionFact(section));
                }
                resultsByIndex.put(i, facts);
                localOnlySections += batch.size();
                log.info("Section facts batch skipped LLM: batch={}/{}, sections={}", i + 1, batches.size(), batch.size());
            } else {
                llmBatchIndices.add(i);
            }
        }

        // LLM batch 并发执行
        if (!llmBatchIndices.isEmpty()) {
            CompletionService<BatchSectionFactsResult> completionService = new ExecutorCompletionService<>(llmBatchExecutor);
            for (int batchIndex : llmBatchIndices) {
                // 暂停检查
                if (pauseChecker.isPaused()) {
                    throw new PlannedIngestException(ErrorMessages.WIKI_TASK_PAUSED);
                }
                final List<DocumentOutlineExtractor.DocumentSection> batch = batches.get(batchIndex);
                completionService.submit(() -> generateSectionFactsBatch(outline, batch, batchIndex, batches.size(), llmMetrics));
            }

            try {
                for (int i = 0; i < llmBatchIndices.size(); i++) {
                    Future<BatchSectionFactsResult> future = completionService.take();
                    BatchSectionFactsResult result = future.get();
                    resultsByIndex.put(result.batchIndex(), result.facts());
                    progress.report(progressBetween(25, 52, resultsByIndex.size(), batches.size()),
                            "正在生成章节事实 " + resultsByIndex.size() + "/" + batches.size(),
                            resultsByIndex.size(), batches.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PlannedIngestException(ErrorMessages.WIKI_INGEST_FAILED);
            } catch (ExecutionException e) {
                log.warn("Section facts batch failed unexpectedly: {}", e.getMessage());
                throw new PlannedIngestException(ErrorMessages.WIKI_INGEST_FAILED);
            }
        }

        JsonArray mergedFacts = new JsonArray();
        for (int i = 0; i < batches.size(); i++) {
            JsonArray batchResult = resultsByIndex.get(i);
            if (batchResult != null) {
                for (JsonElement fact : batchResult) {
                    mergedFacts.add(fact);
                }
            }
        }

        JsonObject merged = new JsonObject();
        merged.add("section_facts", mergedFacts);
        progress.report(52, "章节事实生成完成，正在生成页面计划...", batches.size(), batches.size());
        log.info("Planned ingest Step 1 completed: sections={}, batches={}, localOnlySections={}, durationMs={}",
                outline.getSections().size(), batches.size(), localOnlySections, System.currentTimeMillis() - start);
        return merged;
    }

    private BatchSectionFactsResult generateSectionFactsBatch(DocumentOutlineExtractor.DocumentOutline outline,
                                                             List<DocumentOutlineExtractor.DocumentSection> batch,
                                                             int batchIndex,
                                                             int totalBatches,
                                                             LlmMetrics llmMetrics) {
        long batchStart = System.currentTimeMillis();
        try {
            String batchOutlineJson = toFactsOnlyOutlineJson(outline, batch);
            String response = callLlm(IngestPromptTemplates.buildSectionFactsPrompt(batchOutlineJson), llmMetrics);
            JsonObject parsed = parseJson(response);
            JsonObject normalized = normalizeSectionFacts(parsed, batch);
            if (parsed == null || !parsed.has("section_facts")) {
                log.warn("Section facts JSON invalid, using fallback for {} sections", batch.size());
            }
            JsonArray facts = normalized.getAsJsonArray("section_facts");
            log.info("Section facts batch completed: batch={}/{}, sections={}, durationMs={}",
                    batchIndex + 1, totalBatches, batch.size(), System.currentTimeMillis() - batchStart);
            return new BatchSectionFactsResult(batchIndex, facts);
        } catch (Exception e) {
            JsonArray facts = new JsonArray();
            for (DocumentOutlineExtractor.DocumentSection section : batch) {
                facts.add(fallbackSectionFact(section));
            }
            log.warn("Section facts batch failed, using fallback: batch={}/{}, sections={}, error={}",
                    batchIndex + 1, totalBatches, batch.size(), e.getMessage());
            return new BatchSectionFactsResult(batchIndex, facts);
        }
    }

    private boolean canUseLocalSectionFact(DocumentOutlineExtractor.DocumentSection section) {
        if (section == null || hasRichBlocks(section)) {
            return false;
        }
        String excerpt = trimToNull(section.getExcerpt());
        String title = trimToNull(lastPathSegment(section.getPath()));
        if (excerpt == null || title == null || excerpt.length() > TITLE_ONLY_EXCERPT_MAX_LENGTH) {
            return false;
        }
        String normalizedExcerpt = canonicalTitle(excerpt);
        String normalizedTitle = canonicalTitle(title);
        if (!normalizedExcerpt.isBlank()
                && !normalizedTitle.isBlank()
                && (normalizedExcerpt.equals(normalizedTitle) || normalizedExcerpt.endsWith(normalizedTitle))) {
            return true;
        }
        return isLowInformationSection(excerpt);
    }

    private boolean isLowInformationSection(String excerpt) {
        String value = trimToNull(excerpt);
        if (value == null || value.length() > LOW_INFORMATION_EXCERPT_MAX_LENGTH) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String signal : SECTION_FACT_OPERATION_SIGNALS) {
            if (normalized.contains(signal.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return normalized.matches("[\\p{Punct}\\s\\d０-９一二三四五六七八九十百千万第章节、（）()\\.．-]+")
                || normalized.length() < 24
                || normalized.startsWith("本章")
                || normalized.startsWith("本节")
                || normalized.startsWith("本文");
    }

    private boolean hasRichBlocks(DocumentOutlineExtractor.DocumentSection section) {
        List<String> blocks = section.getBlocks();
        return blocks != null && (blocks.contains("table") || blocks.contains("code") || blocks.contains("list"));
    }

    private JsonObject normalizeSectionFacts(JsonObject parsed,
                                             List<DocumentOutlineExtractor.DocumentSection> batch) {
        JsonObject normalized = new JsonObject();
        JsonArray facts = new JsonArray();
        Set<String> expectedIds = new LinkedHashSet<>();
        for (DocumentOutlineExtractor.DocumentSection section : batch) {
            expectedIds.add(section.getId());
        }

        Set<String> presentIds = new HashSet<>();
        if (parsed != null && parsed.has("section_facts") && parsed.get("section_facts").isJsonArray()) {
            for (JsonElement element : parsed.getAsJsonArray("section_facts")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject fact = element.getAsJsonObject();
                String sectionId = getAsString(fact, "section_id");
                if (sectionId != null && expectedIds.contains(sectionId) && presentIds.add(sectionId)) {
                    facts.add(ensureFactShape(fact));
                }
            }
        }

        for (DocumentOutlineExtractor.DocumentSection section : batch) {
            if (!presentIds.contains(section.getId())) {
                facts.add(fallbackSectionFact(section));
            }
        }
        normalized.add("section_facts", facts);
        return normalized;
    }

    private JsonObject ensureFactShape(JsonObject fact) {
        String[] arrayFields = {"facts", "operations", "config_items", "warnings", "entities"};
        for (String field : arrayFields) {
            if (!fact.has(field) || !fact.get(field).isJsonArray()) {
                fact.add(field, new JsonArray());
            }
        }
        return fact;
    }

    private JsonObject fallbackSectionFact(DocumentOutlineExtractor.DocumentSection section) {
        JsonObject fact = new JsonObject();
        fact.addProperty("section_id", section.getId());
        fact.addProperty("section_path", section.getPath());
        JsonArray facts = new JsonArray();
        String excerpt = trimToNull(section.getExcerpt());
        facts.add(excerpt == null
                ? "章节涉及：" + section.getPath()
                : "章节摘录：" + excerpt);
        fact.add("facts", facts);
        fact.add("operations", new JsonArray());
        fact.add("config_items", new JsonArray());
        fact.add("warnings", new JsonArray());
        JsonArray entities = new JsonArray();
        String entity = lastPathSegment(section.getPath());
        if (entity != null && entity.length() <= 60) {
            entities.add(entity);
        }
        fact.add("entities", entities);
        return fact;
    }

    private List<List<DocumentOutlineExtractor.DocumentSection>> partitionSectionFacts(
            List<DocumentOutlineExtractor.DocumentSection> sections) {
        List<List<DocumentOutlineExtractor.DocumentSection>> batches = new ArrayList<>();
        List<DocumentOutlineExtractor.DocumentSection> current = new ArrayList<>();
        int currentChars = 0;
        for (DocumentOutlineExtractor.DocumentSection section : sections) {
            int sectionChars = safeLength(section.getPath()) + safeLength(section.getExcerpt()) + 160;
            if (!current.isEmpty()
                    && (current.size() >= SECTION_FACTS_BATCH_SIZE
                    || currentChars + sectionChars > SECTION_FACTS_BATCH_CHAR_LIMIT)) {
                batches.add(current);
                current = new ArrayList<>();
                currentChars = 0;
            }
            current.add(section);
            currentChars += sectionChars;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    private JsonObject fallbackPagePlan(WikiSource source, DocumentOutlineExtractor.DocumentOutline outline) {
        JsonObject pagePlan = new JsonObject();
        JsonArray plans = new JsonArray();
        List<DocumentOutlineExtractor.DocumentSection> candidates = outline.getSections().stream()
                .filter(DocumentOutlineExtractor.DocumentSection::isRequired)
                .toList();
        if (candidates.isEmpty()) {
            candidates = outline.getSections();
        }

        List<DocumentOutlineExtractor.DocumentSection> current = new ArrayList<>();
        String currentGroup = null;
        for (DocumentOutlineExtractor.DocumentSection section : candidates) {
            String group = firstPathSegment(section.getPath());
            if (!current.isEmpty() && (!Objects.equals(currentGroup, group) || current.size() >= 8)) {
                plans.add(fallbackPlan(source, currentGroup, current));
                current = new ArrayList<>();
            }
            currentGroup = group;
            current.add(section);
        }
        if (!current.isEmpty()) {
            plans.add(fallbackPlan(source, currentGroup, current));
        }
        pagePlan.add("pages", plans);
        return pagePlan;
    }

    private void repairPagePlanCoverage(WikiSource source, JsonObject pagePlan,
                                        DocumentOutlineExtractor.DocumentOutline outline) {
        if (pagePlan == null || !pagePlan.has("pages") || !pagePlan.get("pages").isJsonArray()) {
            return;
        }
        Set<String> covered = coveredSectionIds(pagePlan.getAsJsonArray("pages"));
        List<DocumentOutlineExtractor.DocumentSection> missing = outline.getSections().stream()
                .filter(DocumentOutlineExtractor.DocumentSection::isRequired)
                .filter(section -> !covered.contains(section.getId()))
                .toList();
        if (missing.isEmpty()) {
            return;
        }

        JsonArray plans = pagePlan.getAsJsonArray("pages");
        List<DocumentOutlineExtractor.DocumentSection> current = new ArrayList<>();
        String currentGroup = null;
        for (DocumentOutlineExtractor.DocumentSection section : missing) {
            String group = firstPathSegment(section.getPath());
            if (!current.isEmpty() && (!Objects.equals(currentGroup, group) || current.size() >= 8)) {
                plans.add(fallbackPlan(source, currentGroup, current));
                current = new ArrayList<>();
            }
            currentGroup = group;
            current.add(section);
        }
        if (!current.isEmpty()) {
            plans.add(fallbackPlan(source, currentGroup, current));
        }
        log.warn("Page plan missed required sections for source '{}', added fallback plans: missingSections={}, totalPlans={}",
                source.getTitle(), missing.size(), plans.size());
    }

    private JsonObject fallbackPlan(WikiSource source, String group,
                                    List<DocumentOutlineExtractor.DocumentSection> sections) {
        JsonObject plan = new JsonObject();
        String software = trimToNull(source.getSoftware());
        String titleSubject = trimToNull(group) == null ? "文档知识" : group;
        plan.addProperty("planned_title", (software == null ? "" : software + " ") + titleSubject);
        plan.addProperty("page_type", fallbackPageType(sections));
        plan.addProperty("category", source.getCategory());
        plan.addProperty("software", source.getSoftware());
        plan.addProperty("version", "");
        JsonArray covered = new JsonArray();
        JsonArray expectedOutline = new JsonArray();
        for (DocumentOutlineExtractor.DocumentSection section : sections) {
            covered.add(section.getId());
            expectedOutline.add(section.getPath());
        }
        plan.add("covered_section_ids", covered);
        plan.addProperty("required", sections.stream().anyMatch(DocumentOutlineExtractor.DocumentSection::isRequired));
        plan.addProperty("merge_strategy", "CREATE_OR_PATCH");
        plan.add("expected_outline", expectedOutline);
        return plan;
    }

    private String fallbackPageType(List<DocumentOutlineExtractor.DocumentSection> sections) {
        String joined = sections.stream()
                .map(DocumentOutlineExtractor.DocumentSection::getSectionType)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" "));
        if (joined.contains("TROUBLESHOOTING_STEP")) return "EXPERIENCE";
        if (joined.contains("PROCEDURE")) return "RUNBOOK";
        if (joined.contains("CONFIG_ITEM") || joined.contains("STANDARD_RULE")) return "STANDARD";
        if (joined.contains("OVERVIEW")) return "OVERVIEW";
        return "CONCEPT";
    }

    private List<JsonArray> partitionPlanBatch(JsonArray plans) {
        List<JsonArray> batches = new ArrayList<>();
        JsonArray current = new JsonArray();
        for (JsonElement plan : plans) {
            current.add(plan.deepCopy());
            if (current.size() >= PAGE_GENERATION_BATCH_SIZE) {
                batches.add(current);
                current = new JsonArray();
            }
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    private Set<String> coveredSectionIds(JsonArray plans) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : plans) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject plan = element.getAsJsonObject();
            if (!plan.has("covered_section_ids") || !plan.get("covered_section_ids").isJsonArray()) {
                continue;
            }
            for (JsonElement id : plan.getAsJsonArray("covered_section_ids")) {
                if (!id.isJsonNull()) {
                    ids.add(id.getAsString());
                }
            }
        }
        return ids;
    }

    private List<DocumentOutlineExtractor.DocumentSection> sectionsByIds(
            DocumentOutlineExtractor.DocumentOutline outline, Set<String> ids) {
        if (ids.isEmpty()) {
            return outline.getSections();
        }
        return outline.getSections().stream()
                .filter(section -> ids.contains(section.getId()))
                .toList();
    }

    private JsonObject filterSectionFacts(JsonObject sectionFacts, Set<String> ids) {
        JsonObject filtered = new JsonObject();
        JsonArray facts = new JsonArray();
        if (sectionFacts != null && sectionFacts.has("section_facts")
                && sectionFacts.get("section_facts").isJsonArray()) {
            for (JsonElement element : sectionFacts.getAsJsonArray("section_facts")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject fact = element.getAsJsonObject();
                String sectionId = getAsString(fact, "section_id");
                if (ids.isEmpty() || ids.contains(sectionId)) {
                    facts.add(fact.deepCopy());
                }
            }
        }
        filtered.add("section_facts", facts);
        return filtered;
    }

    private JsonArray fallbackPagesFromPlan(WikiSource source, JsonArray plans,
                                            DocumentOutlineExtractor.DocumentOutline outline,
                                            JsonObject sectionFacts) {
        JsonArray pages = new JsonArray();
        Map<String, DocumentOutlineExtractor.DocumentSection> sectionById = sectionById(outline);
        Map<String, JsonObject> factBySectionId = factBySectionId(sectionFacts);
        for (JsonElement element : plans) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject plan = element.getAsJsonObject();
            JsonObject page = new JsonObject();
            String title = trimToNull(getAsString(plan, "planned_title"));
            page.addProperty("title", title == null ? source.getSoftware() + " 文档知识" : title);
            page.addProperty("page_type", trimToNull(getAsString(plan, "page_type")) == null
                    ? "CONCEPT" : getAsString(plan, "page_type"));
            page.addProperty("category", getAsString(plan, "category"));
            page.addProperty("software", getAsString(plan, "software"));
            page.addProperty("version", getAsString(plan, "version"));
            page.add("alias_titles", new JsonArray());
            page.addProperty("summary", "根据来源章节事实生成的基础页面，覆盖 "
                    + coveredSectionIds(singlePlanArray(plan)).size() + " 个章节。");
            page.addProperty("content", fallbackPageContent(page, plan, sectionById, factBySectionId));
            pages.add(page);
        }
        return pages;
    }

    private String fallbackPageContent(JsonObject page, JsonObject plan,
                                       Map<String, DocumentOutlineExtractor.DocumentSection> sectionById,
                                       Map<String, JsonObject> factBySectionId) {
        String title = getAsString(page, "title");
        StringBuilder content = new StringBuilder("# ").append(title).append("\n\n");
        content.append("## 覆盖范围\n");
        JsonArray sectionIds = plan.has("covered_section_ids") && plan.get("covered_section_ids").isJsonArray()
                ? plan.getAsJsonArray("covered_section_ids") : new JsonArray();
        for (JsonElement idElement : sectionIds) {
            if (idElement.isJsonNull()) {
                continue;
            }
            String sectionId = idElement.getAsString();
            DocumentOutlineExtractor.DocumentSection section = sectionById.get(sectionId);
            JsonObject fact = factBySectionId.get(sectionId);
            content.append("\n### ")
                    .append(section == null ? sectionId : section.getPath())
                    .append("\n");
            appendFactArray(content, "事实", fact, "facts");
            appendFactArray(content, "操作", fact, "operations");
            appendFactArray(content, "配置项", fact, "config_items");
            appendFactArray(content, "注意事项", fact, "warnings");
            if (fact == null && section != null && trimToNull(section.getExcerpt()) != null) {
                content.append("- 章节摘录：").append(section.getExcerpt()).append("\n");
            }
        }
        content.append("\n## 来源说明\n");
        content.append("本页面按文档目录章节聚合，保留章节事实、操作、配置项和注意事项。");
        while (content.length() < 360) {
            content.append(" 后续人工审核时可根据原始文档补充截图、命令输出和环境差异。");
        }
        return content.toString();
    }

    private void appendFactArray(StringBuilder content, String label, JsonObject fact, String field) {
        if (fact == null || !fact.has(field) || !fact.get(field).isJsonArray()) {
            return;
        }
        JsonArray values = fact.getAsJsonArray(field);
        if (values.isEmpty()) {
            return;
        }
        content.append("- ").append(label).append("：");
        List<String> rendered = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonNull()) {
                rendered.add(renderFactValue(value));
            }
        }
        content.append(String.join("；", rendered)).append("\n");
    }

    private String renderFactValue(JsonElement value) {
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String action = getAsString(object, "action");
            String name = getAsString(object, "name");
            String description = getAsString(object, "description");
            String evidence = getAsString(object, "evidence");
            return java.util.stream.Stream.of(action, name, description, evidence)
                    .filter(Objects::nonNull)
                    .filter(text -> !text.isBlank())
                    .collect(java.util.stream.Collectors.joining(" "));
        }
        return value.toString();
    }

    private JsonArray singlePlanArray(JsonObject plan) {
        JsonArray array = new JsonArray();
        array.add(plan);
        return array;
    }

    private Map<String, DocumentOutlineExtractor.DocumentSection> sectionById(
            DocumentOutlineExtractor.DocumentOutline outline) {
        Map<String, DocumentOutlineExtractor.DocumentSection> sections = new HashMap<>();
        for (DocumentOutlineExtractor.DocumentSection section : outline.getSections()) {
            sections.put(section.getId(), section);
        }
        return sections;
    }

    private Map<String, JsonObject> factBySectionId(JsonObject sectionFacts) {
        Map<String, JsonObject> facts = new HashMap<>();
        if (sectionFacts == null || !sectionFacts.has("section_facts")
                || !sectionFacts.get("section_facts").isJsonArray()) {
            return facts;
        }
        for (JsonElement element : sectionFacts.getAsJsonArray("section_facts")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject fact = element.getAsJsonObject();
            String sectionId = getAsString(fact, "section_id");
            if (sectionId != null) {
                facts.put(sectionId, fact);
            }
        }
        return facts;
    }

    private String toCompactOutlineJson(DocumentOutlineExtractor.DocumentOutline outline,
                                        List<DocumentOutlineExtractor.DocumentSection> sections,
                                        int excerptLimit) {
        JsonObject compact = new JsonObject();
        compact.addProperty("documentType", outline.getDocumentType());
        compact.addProperty("format", outline.getFormat());
        compact.addProperty("title", outline.getTitle());
        compact.addProperty("category", outline.getCategory());
        compact.addProperty("software", outline.getSoftware());
        compact.addProperty("structureQuality", outline.getStructureQuality());
        JsonArray compactSections = new JsonArray();
        for (DocumentOutlineExtractor.DocumentSection section : sections) {
            JsonObject item = new JsonObject();
            item.addProperty("id", section.getId());
            item.addProperty("path", section.getPath());
            item.addProperty("level", section.getLevel());
            item.addProperty("order", section.getOrder());
            if (section.getPageRange() != null) item.addProperty("pageRange", section.getPageRange());
            item.addProperty("sourceSignal", section.getSourceSignal());
            item.addProperty("required", section.isRequired());
            item.addProperty("sectionType", section.getSectionType());
            item.addProperty("confidence", section.getConfidence());
            item.addProperty("excerpt", limit(section.getExcerpt(), excerptLimit));
            JsonArray blocks = new JsonArray();
            if (section.getBlocks() != null) {
                for (String block : section.getBlocks()) {
                    blocks.add(block);
                }
            }
            item.add("blocks", blocks);
            compactSections.add(item);
        }
        compact.add("sections", compactSections);
        return gson.toJson(compact);
    }

    private String toFactsOnlyOutlineJson(DocumentOutlineExtractor.DocumentOutline outline,
                                          List<DocumentOutlineExtractor.DocumentSection> sections) {
        JsonObject compact = new JsonObject();
        compact.addProperty("documentType", outline.getDocumentType());
        compact.addProperty("title", outline.getTitle());
        compact.addProperty("category", outline.getCategory());
        compact.addProperty("software", outline.getSoftware());
        JsonArray compactSections = new JsonArray();
        for (DocumentOutlineExtractor.DocumentSection section : sections) {
            JsonObject item = new JsonObject();
            item.addProperty("id", section.getId());
            item.addProperty("path", section.getPath());
            item.addProperty("required", section.isRequired());
            item.addProperty("sectionType", section.getSectionType());
            item.addProperty("excerpt", limit(section.getExcerpt(), 360));
            JsonArray blocks = new JsonArray();
            if (section.getBlocks() != null) {
                for (String block : section.getBlocks()) {
                    blocks.add(block);
                }
            }
            item.add("blocks", blocks);
            compactSections.add(item);
        }
        compact.add("sections", compactSections);
        return gson.toJson(compact);
    }

    private String firstPathSegment(String path) {
        String value = trimToNull(path);
        if (value == null) {
            return "文档知识";
        }
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    private String lastPathSegment(String path) {
        String value = trimToNull(path);
        if (value == null) {
            return null;
        }
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private int progressBetween(int start, int end, int completed, int total) {
        if (total <= 0) {
            return start;
        }
        int boundedCompleted = Math.max(0, Math.min(completed, total));
        return start + (int) Math.floor((end - start) * (boundedCompleted / (double) total));
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void validatePagePlanCoverage(JsonObject pagePlan, DocumentOutlineExtractor.DocumentOutline outline) {
        if (pagePlan == null || !pagePlan.has("pages") || !pagePlan.get("pages").isJsonArray()) {
            throw new PlannedIngestException(ErrorMessages.WIKI_PAGE_PLAN_FAILED);
        }
        Set<String> required = new LinkedHashSet<>();
        for (DocumentOutlineExtractor.DocumentSection section : outline.getSections()) {
            if (section.isRequired()) {
                required.add(section.getId());
            }
        }
        if (required.isEmpty()) {
            return;
        }
        Set<String> covered = new HashSet<>();
        for (JsonElement element : pagePlan.getAsJsonArray("pages")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject plan = element.getAsJsonObject();
            if (!plan.has("covered_section_ids") || !plan.get("covered_section_ids").isJsonArray()) {
                continue;
            }
            for (JsonElement sectionId : plan.getAsJsonArray("covered_section_ids")) {
                if (!sectionId.isJsonNull()) {
                    covered.add(sectionId.getAsString());
                }
            }
        }
        List<String> missing = required.stream()
                .filter(sectionId -> !covered.contains(sectionId))
                .toList();
        if (!missing.isEmpty()) {
            throw new PlannedIngestException(ErrorMessages.WIKI_PAGE_PLAN_FAILED + "：缺少必需章节 " + missing);
        }
    }

    private void completePlannedIngest(WikiSource source, String hash, WikiIngestQualityGate.QualityReport report,
                                       SaveStats stats, int linksCreated, IngestResult result) {
        source.setContentHash(hash);
        source.setIngested("SUCCESS".equals(report.getStatus()));
        source.setIngestedAt("SUCCESS".equals(report.getStatus()) ? LocalDateTime.now() : null);
        sourceMapper.update(source);

        result.setPagesCreated(stats.created());
        result.setPagesUpdated(stats.updated());
        result.setContradictionsFound(stats.contradictions());
        result.setLinksCreated(linksCreated);
        result.setStatus(report.getStatus());
        if ("PARTIAL".equals(report.getStatus())) {
            result.setErrorMessage(ErrorMessages.WIKI_QUALITY_GATE_PARTIAL + "：" + summarizeQuality(report));
        }
    }

    private void writePlannedLog(WikiIngestLog ingestLog, WikiIngestQualityGate.QualityReport report,
                                 SaveStats stats, int linksCreated, IngestResult result, long startTime) {
        ingestLog.setPagesCreated(stats.created());
        ingestLog.setPagesUpdated(stats.updated());
        ingestLog.setLinksCreated(linksCreated);
        ingestLog.setContradictionsFound(stats.contradictions());
        ingestLog.setStatus(report.getStatus());
        ingestLog.setErrorDetail(result.getErrorMessage());
        ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
        logMapper.insert(ingestLog);
    }

    private IngestResult failPlanned(IngestResult result, WikiIngestLog ingestLog, long startTime, String message) {
        result.setStatus("FAILED");
        result.setErrorMessage(message);
        ingestLog.setStatus("FAILED");
        ingestLog.setErrorDetail(message);
        ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
        logMapper.insert(ingestLog);
        return result;
    }

    private record PlannedPages(JsonArray pages, WikiIngestQualityGate.QualityReport report) {}

    private record BatchPagesResult(int batchIndex, JsonArray pages) {}

    private record BatchSectionFactsResult(int batchIndex, JsonArray facts) {}

    private static class PlannedIngestException extends RuntimeException {
        PlannedIngestException(String message) {
            super(message);
        }
    }

    private String callLlm(String prompt) {
        return callLlm(prompt, null);
    }

    private String callLlm(String prompt, LlmMetrics metrics) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("你是一个严格的知识编译器。只输出要求的格式，不要包含任何其他文字。"));
        messages.add(new UserMessage(prompt));

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ChatResponse response = chatModel.chat(messages);
                if (metrics != null) {
                    metrics.recordCall();
                    if (response.tokenUsage() != null) {
                        metrics.addTokens(
                                response.tokenUsage().inputTokenCount() != null ? response.tokenUsage().inputTokenCount() : 0,
                                response.tokenUsage().outputTokenCount() != null ? response.tokenUsage().outputTokenCount() : 0);
                    }
                }
                AiMessage aiMessage = response.aiMessage();
                return aiMessage != null ? aiMessage.text() : "";
            } catch (Exception e) {
                log.warn("LLM call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (metrics != null) {
                    metrics.recordRetry();
                }
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(attempt * 2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw new com.middleware.manager.exception.BusinessException(
                com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR,
                ErrorMessages.LLM_SERVICE_BUSY);
    }

    private JsonObject parseJson(String text) {
        if (text == null) return null;
        text = text.trim();

        // 去掉 markdown 代码块
        if (text.startsWith("```json")) text = text.substring(7);
        if (text.startsWith("```")) text = text.substring(3);
        if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        text = text.trim();

        try {
            return gson.fromJson(text, JsonObject.class);
        } catch (Exception ignored) {}

        String balancedJson = extractBalancedJsonObject(text);
        if (balancedJson != null) {
            try {
                return gson.fromJson(balancedJson, JsonObject.class);
            } catch (Exception ignored) {}
        }

        String head = text.substring(0, Math.min(200, text.length()));
        String tail = text.substring(Math.max(0, text.length() - Math.min(200, text.length())));
        log.warn("Failed to parse JSON from LLM response ({} chars): head={}... tail={}...",
                text.length(), head, tail);
        return null;
    }

    private String extractBalancedJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String getAsString(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return elem != null && !elem.isJsonNull() ? elem.getAsString() : null;
    }

    private String buildSourceMetaJson(WikiSource source) {
        JsonObject meta = new JsonObject();
        meta.addProperty("source_id", source.getId());
        meta.addProperty("source_title", source.getTitle());
        meta.addProperty("source_type", source.getSourceType());
        meta.addProperty("category", source.getCategory());
        meta.addProperty("software", source.getSoftware());
        return gson.toJson(meta);
    }

    private void enrichPagesWithPlan(JsonArray pages, JsonArray plans,
                                     DocumentOutlineExtractor.DocumentOutline outline, WikiSource source) {
        if (pages == null || plans == null) return;
        Map<String, JsonObject> planByTitle = new HashMap<>();
        for (JsonElement planElement : plans) {
            if (!planElement.isJsonObject()) continue;
            JsonObject plan = planElement.getAsJsonObject();
            String plannedTitle = getAsString(plan, "planned_title");
            if (plannedTitle != null) {
                planByTitle.put(plannedTitle, plan);
            }
        }
        Map<String, DocumentOutlineExtractor.DocumentSection> sections = new HashMap<>();
        for (DocumentOutlineExtractor.DocumentSection section : outline.getSections()) {
            sections.put(section.getId(), section);
        }

        for (int i = 0; i < pages.size(); i++) {
            if (!pages.get(i).isJsonObject()) continue;
            JsonObject page = pages.get(i).getAsJsonObject();
            JsonObject plan = planByTitle.get(getAsString(page, "title"));
            if (plan == null && i < plans.size() && plans.get(i).isJsonObject()) {
                plan = plans.get(i).getAsJsonObject();
            }
            if (plan == null) continue;

            JsonArray sectionIds = plan.has("covered_section_ids") && plan.get("covered_section_ids").isJsonArray()
                    ? plan.getAsJsonArray("covered_section_ids") : new JsonArray();
            JsonObject coverage = page.has("coverage") && page.get("coverage").isJsonObject()
                    ? page.getAsJsonObject("coverage") : new JsonObject();
            coverage.add("section_ids", sectionIds.deepCopy());
            if (!coverage.has("evidence_quotes") || !coverage.get("evidence_quotes").isJsonArray()) {
                coverage.add("evidence_quotes", new JsonArray());
            }
            page.add("coverage", coverage);
            page.add("source_refs", buildSourceRefs(source, sectionIds, sections));
            if (!page.has("category") || page.get("category").isJsonNull()) {
                page.addProperty("category", getAsString(plan, "category"));
            }
            if (!page.has("software") || page.get("software").isJsonNull()) {
                page.addProperty("software", getAsString(plan, "software"));
            }
            if (!page.has("version") || page.get("version").isJsonNull()) {
                page.addProperty("version", getAsString(plan, "version"));
            }
        }
    }

    private JsonObject buildSourceRefs(WikiSource source, JsonArray sectionIds,
                                       Map<String, DocumentOutlineExtractor.DocumentSection> sections) {
        JsonObject refs = new JsonObject();
        refs.addProperty("source_id", source.getId());
        refs.addProperty("source_title", source.getTitle());
        refs.addProperty("source_type", source.getSourceType());
        JsonArray refsSections = new JsonArray();
        for (JsonElement idElement : sectionIds) {
            if (idElement.isJsonNull()) continue;
            String sectionId = idElement.getAsString();
            DocumentOutlineExtractor.DocumentSection section = sections.get(sectionId);
            JsonObject sectionRef = new JsonObject();
            sectionRef.addProperty("section_id", sectionId);
            if (section != null) {
                sectionRef.addProperty("section_path", section.getPath());
                sectionRef.addProperty("char_range", section.getCharStart() + "-" + section.getCharEnd());
                if (section.getPageRange() != null) {
                    sectionRef.addProperty("page_range", section.getPageRange());
                }
                sectionRef.addProperty("paragraph_range", section.getParagraphStart() + "-" + section.getParagraphEnd());
                if (section.getSourceSignal() != null) {
                    sectionRef.addProperty("source_signal", section.getSourceSignal());
                }
            }
            refsSections.add(sectionRef);
        }
        refs.add("sections", refsSections);
        return refs;
    }

    /**
     * 重编译过度压缩页面：只重新生成 overCompressedPages 列表中的页面，复用已持久化的 section_facts。
     */
    @Transactional
    public IngestResult recompileCompressedPages(WikiSource source, Long operatorId,
                                                  String qualityReportJson, String sectionFactsJson,
                                                  String pagePlanJson, ProgressReporter progressReporter) {
        long startTime = System.currentTimeMillis();
        IngestResult result = new IngestResult();
        ProgressReporter progress = progressReporter == null ? NOOP_PROGRESS : progressReporter;

        try {
            // 1. 解析质量报告，获取过度压缩页面标题
            JsonObject qualityReport = parseJson(qualityReportJson);
            if (qualityReport == null || !qualityReport.has("overCompressedPages")) {
                result.setStatus("SKIPPED");
                result.setErrorMessage(ErrorMessages.WIKI_NO_COMPRESSED_PAGES);
                return result;
            }
            JsonArray overCompressedTitles = qualityReport.getAsJsonArray("overCompressedPages");
            if (overCompressedTitles.isEmpty()) {
                result.setStatus("SKIPPED");
                result.setErrorMessage(ErrorMessages.WIKI_NO_COMPRESSED_PAGES);
                return result;
            }
            Set<String> compressedTitles = new HashSet<>();
            for (JsonElement elem : overCompressedTitles) {
                compressedTitles.add(elem.getAsString());
            }
            log.info("Recompile compressed: {} pages to regenerate for source '{}'",
                    compressedTitles.size(), source.getTitle());

            // 2. 解析 section_facts 和 page_plan
            JsonObject sectionFacts = parseJson(sectionFactsJson);
            JsonObject pagePlan = parseJson(pagePlanJson);
            if (sectionFacts == null || pagePlan == null) {
                result.setStatus("FAILED");
                result.setErrorMessage(ErrorMessages.WIKI_MISSING_ARTIFACTS);
                return result;
            }

            // 3. 从 page_plan 中筛选过度压缩页面的计划
            JsonArray allPlans = pagePlan.getAsJsonArray("pages");
            JsonArray compressedPlans = new JsonArray();
            for (JsonElement elem : allPlans) {
                JsonObject plan = elem.getAsJsonObject();
                String title = getAsString(plan, "planned_title");
                if (title != null && compressedTitles.contains(title)) {
                    compressedPlans.add(plan);
                }
            }
            if (compressedPlans.isEmpty()) {
                result.setStatus("SKIPPED");
                result.setErrorMessage(ErrorMessages.WIKI_NO_MATCHING_COMPRESSED_PLANS);
                return result;
            }
            log.info("Recompile compressed: found {} matching plans", compressedPlans.size());

            // 4. 重新生成过度压缩页面
            progress.report(10, "正在重新生成过度压缩页面...", 0, compressedPlans.size());
            DocumentTypeClassifier.Classification classification =
                    documentTypeClassifier.classify(source.getTitle(), source.getContent());
            DocumentOutlineExtractor.DocumentOutline outline = documentOutlineExtractor.extract(
                    source.getTitle(), source.getContent(), source.getCategory(), source.getSoftware(), classification);

            JsonArray recompiledPages = new JsonArray();
            LlmMetrics llmMetrics = new LlmMetrics();
            int completed = 0;
            for (JsonElement planElem : compressedPlans) {
                JsonObject plan = planElem.getAsJsonObject();
                JsonArray singlePlan = new JsonArray();
                singlePlan.add(plan);
                try {
                    Set<String> sectionIds = coveredSectionIds(singlePlan);
                    String outlineJson = toCompactOutlineJson(outline, sectionsByIds(outline, sectionIds), 500);
                    String sectionFactsJsonFiltered = gson.toJson(filterSectionFacts(sectionFacts, sectionIds));
                    JsonObject batchPlan = new JsonObject();
                    batchPlan.add("pages", singlePlan.deepCopy());
                    String pagesJson = callLlm(IngestPromptTemplates.buildPlannedPageGenerationPrompt(
                            outlineJson, sectionFactsJsonFiltered, gson.toJson(batchPlan),
                            buildSourceMetaJson(source)), llmMetrics);
                    JsonObject pagesResult = parseJson(pagesJson);
                    if (pagesResult != null && pagesResult.has("pages") && pagesResult.get("pages").isJsonArray()) {
                        for (JsonElement p : pagesResult.getAsJsonArray("pages")) {
                            recompiledPages.add(p);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Recompile compressed page failed: {}", e.getMessage());
                }
                completed++;
                progress.report(progressBetween(10, 80, completed, compressedPlans.size()),
                        "正在重新生成过度压缩页面 " + completed + "/" + compressedPlans.size(),
                        completed, compressedPlans.size());
            }

            if (recompiledPages.isEmpty()) {
                result.setStatus("FAILED");
                result.setErrorMessage(ErrorMessages.WIKI_RECOMPILE_FAILED);
                return result;
            }

            // 5. 补充来源引用
            progress.report(80, "正在补充来源引用...", 0, 0);
            enrichPagesWithPlan(recompiledPages, compressedPlans, outline, source);

            // 6. 保存页面
            progress.report(85, "正在保存页面...", 0, 0);
            SaveStats stats = savePages(recompiledPages, source);
            int linksCreated = linkResolver.resolveLinks(stats.savedPages());

            // 7. 重新执行质量门禁（拉取 source 关联的所有页面）
            progress.report(90, "正在重新执行质量门禁...", 0, 0);
            List<WikiPage> allPages = pageMapper.findByCategoryOrSoftware(
                    source.getCategory(), source.getSoftware(), 500);
            JsonArray allPagesJson = pagesToJsonArray(allPages, source.getId());
            WikiIngestQualityGate.QualityReport report = qualityGate.evaluate(outline, allPagesJson);
            result.setQualityReport(gson.toJson(report));
            result.setPagesCreated(stats.created());
            result.setPagesUpdated(stats.updated());
            result.setLinksCreated(linksCreated);
            result.setStatus(report.getStatus());

            log.info("Recompile compressed completed for '{}': status={}, created={}, updated={}, links={}, durationMs={}",
                    source.getTitle(), report.getStatus(), stats.created(), stats.updated(), linksCreated,
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Recompile compressed failed for source '{}': {}", source.getTitle(), e.getMessage(), e);
            result.setStatus("FAILED");
            result.setErrorMessage(ErrorMessages.WIKI_RECOMPILE_ERROR + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * 重编译缺失章节：只重新生成 missingSections 对应的页面，复用已持久化的 section_facts。
     */
    @Transactional
    public IngestResult recompileMissingSections(WikiSource source, Long operatorId,
                                                  String qualityReportJson, String sectionFactsJson,
                                                  String pagePlanJson, ProgressReporter progressReporter) {
        long startTime = System.currentTimeMillis();
        IngestResult result = new IngestResult();
        ProgressReporter progress = progressReporter == null ? NOOP_PROGRESS : progressReporter;

        try {
            // 1. 解析质量报告，获取缺失章节 ID
            JsonObject qualityReport = parseJson(qualityReportJson);
            if (qualityReport == null || !qualityReport.has("missingSections")) {
                result.setStatus("SKIPPED");
                result.setErrorMessage(ErrorMessages.WIKI_NO_MISSING_SECTIONS);
                return result;
            }
            JsonArray missingSectionIds = qualityReport.getAsJsonArray("missingSections");
            if (missingSectionIds.isEmpty()) {
                result.setStatus("SKIPPED");
                result.setErrorMessage(ErrorMessages.WIKI_NO_MISSING_SECTIONS);
                return result;
            }
            Set<String> missingIds = new HashSet<>();
            for (JsonElement elem : missingSectionIds) {
                missingIds.add(elem.getAsString());
            }
            log.info("Recompile missing: {} missing sections for source '{}'",
                    missingIds.size(), source.getTitle());

            // 2. 解析 section_facts 和 page_plan
            JsonObject sectionFacts = parseJson(sectionFactsJson);
            JsonObject pagePlan = parseJson(pagePlanJson);
            if (sectionFacts == null || pagePlan == null) {
                result.setStatus("FAILED");
                result.setErrorMessage(ErrorMessages.WIKI_MISSING_ARTIFACTS);
                return result;
            }

            // 3. 从 page_plan 中筛选覆盖缺失章节的页面计划
            JsonArray allPlans = pagePlan.getAsJsonArray("pages");
            JsonArray missingPlans = new JsonArray();
            for (JsonElement elem : allPlans) {
                JsonObject plan = elem.getAsJsonObject();
                if (plan.has("covered_section_ids") && plan.get("covered_section_ids").isJsonArray()) {
                    for (JsonElement sectionId : plan.getAsJsonArray("covered_section_ids")) {
                        if (missingIds.contains(sectionId.getAsString())) {
                            missingPlans.add(plan);
                            break;
                        }
                    }
                }
            }
            if (missingPlans.isEmpty()) {
                result.setStatus("SKIPPED");
                result.setErrorMessage(ErrorMessages.WIKI_NO_MATCHING_MISSING_PLANS);
                return result;
            }
            log.info("Recompile missing: found {} plans covering missing sections", missingPlans.size());

            // 4. 重新生成缺失章节对应的页面
            progress.report(10, "正在重新生成缺失章节页面...", 0, missingPlans.size());
            DocumentTypeClassifier.Classification classification =
                    documentTypeClassifier.classify(source.getTitle(), source.getContent());
            DocumentOutlineExtractor.DocumentOutline outline = documentOutlineExtractor.extract(
                    source.getTitle(), source.getContent(), source.getCategory(), source.getSoftware(), classification);

            JsonArray recompiledPages = new JsonArray();
            LlmMetrics llmMetrics = new LlmMetrics();
            int completed = 0;
            for (JsonElement planElem : missingPlans) {
                JsonObject plan = planElem.getAsJsonObject();
                JsonArray singlePlan = new JsonArray();
                singlePlan.add(plan);
                try {
                    Set<String> sectionIds = coveredSectionIds(singlePlan);
                    String outlineJson = toCompactOutlineJson(outline, sectionsByIds(outline, sectionIds), 500);
                    String sectionFactsJsonFiltered = gson.toJson(filterSectionFacts(sectionFacts, sectionIds));
                    JsonObject batchPlan = new JsonObject();
                    batchPlan.add("pages", singlePlan.deepCopy());
                    String pagesJson = callLlm(IngestPromptTemplates.buildPlannedPageGenerationPrompt(
                            outlineJson, sectionFactsJsonFiltered, gson.toJson(batchPlan),
                            buildSourceMetaJson(source)), llmMetrics);
                    JsonObject pagesResult = parseJson(pagesJson);
                    if (pagesResult != null && pagesResult.has("pages") && pagesResult.get("pages").isJsonArray()) {
                        for (JsonElement p : pagesResult.getAsJsonArray("pages")) {
                            recompiledPages.add(p);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Recompile missing page failed: {}", e.getMessage());
                }
                completed++;
                progress.report(progressBetween(10, 80, completed, missingPlans.size()),
                        "正在重新生成缺失章节页面 " + completed + "/" + missingPlans.size(),
                        completed, missingPlans.size());
            }

            if (recompiledPages.isEmpty()) {
                result.setStatus("FAILED");
                result.setErrorMessage(ErrorMessages.WIKI_RECOMPILE_FAILED);
                return result;
            }

            // 5. 补充来源引用
            progress.report(80, "正在补充来源引用...", 0, 0);
            enrichPagesWithPlan(recompiledPages, missingPlans, outline, source);

            // 6. 保存页面
            progress.report(85, "正在保存页面...", 0, 0);
            SaveStats stats = savePages(recompiledPages, source);
            int linksCreated = linkResolver.resolveLinks(stats.savedPages());

            // 7. 重新执行质量门禁（拉取 source 关联的所有页面）
            progress.report(90, "正在重新执行质量门禁...", 0, 0);
            List<WikiPage> allPages = pageMapper.findByCategoryOrSoftware(
                    source.getCategory(), source.getSoftware(), 500);
            JsonArray allPagesJson = pagesToJsonArray(allPages, source.getId());
            WikiIngestQualityGate.QualityReport report = qualityGate.evaluate(outline, allPagesJson);
            result.setQualityReport(gson.toJson(report));
            result.setPagesCreated(stats.created());
            result.setPagesUpdated(stats.updated());
            result.setLinksCreated(linksCreated);
            result.setStatus(report.getStatus());

            log.info("Recompile missing completed for '{}': status={}, created={}, updated={}, links={}, durationMs={}",
                    source.getTitle(), report.getStatus(), stats.created(), stats.updated(), linksCreated,
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Recompile missing failed for source '{}': {}", source.getTitle(), e.getMessage(), e);
            result.setStatus("FAILED");
            result.setErrorMessage(ErrorMessages.WIKI_RECOMPILE_ERROR + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * 将 WikiPage 列表转为 JsonArray（供质量门禁使用）。
     */
    private JsonArray pagesToJsonArray(List<WikiPage> pages, Long sourceId) {
        JsonArray arr = new JsonArray();
        for (WikiPage p : pages) {
            JsonObject sourceRefs = parseJson(p.getSourceRefs());
            if (sourceId != null && !sourceRefsMatch(sourceRefs, sourceId)) {
                continue;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("title", p.getTitle());
            obj.addProperty("page_type", p.getPageType());
            obj.addProperty("content", p.getContent() != null ? p.getContent() : "");
            obj.addProperty("summary", p.getSummary());
            if (sourceRefs != null) {
                obj.add("source_refs", sourceRefs);
                obj.add("coverage", coverageFromSourceRefs(sourceRefs));
            }
            arr.add(obj);
        }
        return arr;
    }

    private boolean sourceRefsMatch(JsonObject sourceRefs, Long sourceId) {
        if (sourceRefs == null || sourceId == null || !sourceRefs.has("source_id")) {
            return false;
        }
        try {
            return sourceId.equals(sourceRefs.get("source_id").getAsLong());
        } catch (Exception e) {
            return false;
        }
    }

    private JsonObject coverageFromSourceRefs(JsonObject sourceRefs) {
        JsonObject coverage = new JsonObject();
        JsonArray sectionIds = new JsonArray();
        if (sourceRefs != null && sourceRefs.has("sections") && sourceRefs.get("sections").isJsonArray()) {
            for (JsonElement sectionElement : sourceRefs.getAsJsonArray("sections")) {
                if (!sectionElement.isJsonObject()) {
                    continue;
                }
                JsonObject section = sectionElement.getAsJsonObject();
                if (section.has("section_id") && !section.get("section_id").isJsonNull()) {
                    sectionIds.add(section.get("section_id").getAsString());
                }
            }
        }
        coverage.add("section_ids", sectionIds);
        coverage.add("evidence_quotes", new JsonArray());
        return coverage;
    }

    private SaveStats savePages(JsonArray pages, WikiSource source) {
        int created = 0, updated = 0, contradictions = 0;
        List<WikiPage> savedPages = new ArrayList<>();
        for (JsonElement elem : pages) {
            JsonObject pageObj = elem.getAsJsonObject();
            String title = getAsString(pageObj, "title");
            String pageType = getAsString(pageObj, "page_type");

            if (title == null || pageType == null) continue;
            WikiPage existing = findExistingPage(pageObj, title, pageType, source.getCategory(), source.getSoftware());
            if (existing != null) {
                String outcome = mergeExistingPage(existing, pageObj, source);
                if ("CONTRADICT".equals(outcome)) {
                    contradictions++;
                } else if ("UPDATED".equals(outcome)) {
                    savedPages.add(existing);
                    updated++;
                }
            } else {
                WikiPage newPage = new WikiPage();
                updatePageFromJson(newPage, pageObj, source);
                pageMapper.insert(newPage);
                created++;
                savedPages.add(newPage);
            }
        }
        return new SaveStats(created, updated, contradictions, savedPages);
    }

    private WikiPage findExistingPage(JsonObject pageObj, String title, String pageType,
                                      String category, String software) {
        if (title == null || pageType == null) {
            return null;
        }
        for (String candidateTitle : candidateTitles(pageObj, title)) {
            WikiPage exact = pageMapper.findByTitleAndType(candidateTitle, pageType);
            if (exact != null) {
                return exact;
            }
        }

        String targetCanonical = canonicalTitle(title);
        WikiPage canonicalMatch = pageMapper.findByCanonicalTitleAndType(targetCanonical, pageType, category, software);
        if (canonicalMatch != null) {
            return canonicalMatch;
        }

        List<WikiPage> candidates = (category != null || software != null)
                ? pageMapper.findByCategoryOrSoftwareExcludingContent(category, software, 100)
                : pageMapper.findAllExcludingContent();
        for (WikiPage candidate : candidates) {
            if (!pageType.equals(candidate.getPageType())) {
                continue;
            }
            if (targetCanonical.equals(pageCanonicalTitle(candidate))) {
                return candidate;
            }
            for (String aliasTitle : candidateTitles(pageObj, title)) {
                String aliasCanonical = canonicalTitle(aliasTitle);
                if (aliasCanonical.equals(pageCanonicalTitle(candidate))) {
                    return candidate;
                }
                for (String persistedAlias : persistedAliasTitles(candidate)) {
                    if (aliasCanonical.equals(canonicalTitle(persistedAlias))) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private Set<String> candidateTitles(JsonObject pageObj, String title) {
        Set<String> titles = new LinkedHashSet<>();
        if (title != null && !title.isBlank()) {
            titles.add(title.trim());
        }
        if (pageObj != null && pageObj.has("alias_titles") && pageObj.get("alias_titles").isJsonArray()) {
            for (JsonElement aliasElement : pageObj.getAsJsonArray("alias_titles")) {
                if (!aliasElement.isJsonNull()) {
                    String alias = aliasElement.getAsString();
                    if (alias != null && !alias.isBlank()) {
                        titles.add(alias.trim());
                    }
                }
            }
        }
        return titles;
    }

    private String canonicalTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、（）【】《》“”‘’]+", "");
    }

    private String pageCanonicalTitle(WikiPage page) {
        if (page == null) {
            return "";
        }
        if (page.getCanonicalTitle() != null && !page.getCanonicalTitle().isBlank()) {
            return page.getCanonicalTitle();
        }
        return canonicalTitle(page.getTitle());
    }

    private List<String> persistedAliasTitles(WikiPage page) {
        if (page == null || page.getAliasTitles() == null || page.getAliasTitles().isBlank()) {
            return Collections.emptyList();
        }
        return parseAliasTitles(page.getAliasTitles(), page.getId());
    }

    private List<String> parseAliasTitles(String aliasTitles, Long pageId) {
        if (aliasTitles == null || aliasTitles.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonArray aliases = gson.fromJson(aliasTitles, JsonArray.class);
            List<String> result = new ArrayList<>();
            if (aliases != null) {
                for (JsonElement alias : aliases) {
                    if (!alias.isJsonNull() && !alias.getAsString().isBlank()) {
                        result.add(alias.getAsString().trim());
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("Ignore invalid alias_titles for pageId={}: {}", pageId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String mergeExistingPage(WikiPage existing, JsonObject pageObj, WikiSource source) {
        String mergeDecision = callLlm(IngestPromptTemplates.buildMergeDecisionPrompt(
                existing.getContent(), getAsString(pageObj, "content")));
        JsonObject decision = parseJson(mergeDecision);
        String action = decision != null ? getAsString(decision, "action") : "APPEND";

        if ("CONTRADICT".equals(action)) {
            existing.setStatus("CONTRADICTED");
            existing.setContradictionNote(decision != null ? getAsString(decision, "reason") : "与新文档内容矛盾");
            pageMapper.update(existing);
            return "CONTRADICT";
        }
        if ("OVERWRITE".equals(action)) {
            updatePageFromJson(existing, pageObj, source);
        } else {
            appendPageContent(existing, pageObj, source);
        }
        pageMapper.update(existing);
        return "UPDATED";
    }

    private void appendPageContent(WikiPage existing, JsonObject pageObj, WikiSource source) {
        String newContent = getAsString(pageObj, "content");
        if (newContent != null) {
            existing.setContent(mergeMarkdownByHeading(existing.getContent(), newContent));
        }
        String newSummary = getAsString(pageObj, "summary");
        if (newSummary != null) existing.setSummary(newSummary);
        existing.setSourceRefs(sourceRefsFromPage(pageObj, source));
        existing.setCanonicalTitle(canonicalTitle(existing.getTitle()));
        existing.setAliasTitles(mergeAliasTitles(existing.getAliasTitles(), pageObj));
        existing.setCompiledBy("ingest-agent");
        existing.setCompiledAt(LocalDateTime.now());
    }

    private String mergeMarkdownByHeading(String existingContent, String newContent) {
        if (newContent == null || newContent.isBlank()) {
            return existingContent;
        }
        if (existingContent == null || existingContent.isBlank()) {
            return newContent;
        }

        List<MarkdownBlock> existingBlocks = markdownBlocks(existingContent);
        List<MarkdownBlock> newBlocks = markdownBlocks(newContent);
        if (existingBlocks.isEmpty() || newBlocks.isEmpty()) {
            return appendWithSeparator(existingContent, newContent);
        }

        Map<String, MarkdownBlock> newByHeading = new LinkedHashMap<>();
        for (MarkdownBlock block : newBlocks) {
            newByHeading.putIfAbsent(canonicalTitle(block.heading()), block);
        }

        Set<String> consumedHeadings = new HashSet<>();
        StringBuilder merged = new StringBuilder();
        int cursor = 0;
        for (MarkdownBlock existingBlock : existingBlocks) {
            merged.append(existingContent, cursor, existingBlock.end());
            String key = canonicalTitle(existingBlock.heading());
            MarkdownBlock patch = newByHeading.get(key);
            if (patch != null) {
                String patchBody = stripHeading(patch.text()).trim();
                if (!patchBody.isBlank() && !existingBlock.text().contains(patchBody)) {
                    merged.append("\n\n").append(patchBody);
                }
                consumedHeadings.add(key);
            }
            cursor = existingBlock.end();
        }
        if (cursor < existingContent.length()) {
            merged.append(existingContent.substring(cursor));
        }
        for (MarkdownBlock newBlock : newBlocks) {
            if (!consumedHeadings.contains(canonicalTitle(newBlock.heading()))) {
                merged.append("\n\n").append(newBlock.text().trim());
            }
        }
        return merged.toString().trim();
    }

    private String appendWithSeparator(String existingContent, String newContent) {
        return existingContent + "\n\n---\n\n" + newContent;
    }

    private List<MarkdownBlock> markdownBlocks(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^#{1,6}\\s+(.+)$")
                .matcher(content);
        List<Integer> starts = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            headings.add(matcher.group(1).trim());
        }
        if (starts.isEmpty()) {
            return Collections.emptyList();
        }
        List<MarkdownBlock> blocks = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : content.length();
            blocks.add(new MarkdownBlock(start, end, headings.get(i), content.substring(start, end)));
        }
        return blocks;
    }

    private String stripHeading(String blockText) {
        if (blockText == null) {
            return "";
        }
        return blockText.replaceFirst("(?s)^#{1,6}\\s+.*?(\\R|$)", "");
    }

    private record MarkdownBlock(int start, int end, String heading, String text) {}

    private String summarizeQuality(WikiIngestQualityGate.QualityReport report) {
        return String.format(Locale.ROOT, "coverage=%.2f, required=%d/%d, missing=%s",
                report.getCoverageRatio(),
                report.getRequiredSectionsCovered(),
                report.getRequiredSectionsTotal(),
                report.getMissingSections());
    }

    private record SaveStats(int created, int updated, int contradictions, List<WikiPage> savedPages) {}

    /**
     * Wiki 页面是结构化编译结果，向量主索引使用源文档片段，避免用 LLM 摘要丢失原文细节。
     */
    public void vectorizePages(List<WikiPage> pages) {
        if (pages == null) {
            return;
        }
        for (WikiPage page : pages) {
            if (page == null || page.getId() == null) {
                continue;
            }
            try {
                if ("milvus".equals(vectorType)) {
                    String vectorId = "wiki_" + page.getId();
                    vectorStore.delete(vectorId);
                }
            } catch (Exception e) {
                log.debug("Legacy wiki page vector delete ignored pageId={}: {}", page.getId(), e.getMessage());
            }
        }
    }

    public void vectorizeSource(WikiSource source) {
        if (!"milvus".equals(vectorType) || source == null || source.getId() == null
                || source.getContent() == null || source.getContent().isBlank()) {
            return;
        }
        try {
            DocumentTypeClassifier.Classification classification =
                    documentTypeClassifier.classify(source.getTitle(), source.getContent());
            DocumentOutlineExtractor.DocumentOutline outline = documentOutlineExtractor.extract(
                    source.getTitle(), source.getContent(), source.getCategory(), source.getSoftware(), classification);
            List<SourceVectorChunk> chunks = sourceVectorChunks(source, outline);
            deleteExistingSourceVectors(source.getId());
            int indexed = 0;
            for (SourceVectorChunk chunk : chunks) {
                try {
                    float[] vector = embeddingService.embed(chunk.content());
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("source", "wiki_source");
                    metadata.put("sourceType", source.getSourceType() != null ? source.getSourceType() : "wiki");
                    metadata.put("sourceId", String.valueOf(source.getId()));
                    metadata.put("sourceTitle", source.getTitle());
                    metadata.put("chunkIndex", String.valueOf(chunk.index()));
                    metadata.put("content", chunk.content());
                    metadata.put("sectionId", chunk.sectionId());
                    metadata.put("sectionPath", chunk.sectionPath());
                    if (source.getCategory() != null) metadata.put("category", source.getCategory());
                    if (source.getSoftware() != null) metadata.put("software", source.getSoftware());
                    metadata.put("status", Boolean.TRUE.equals(source.getIngested()) ? "ACTIVE" : "DRAFT");
                    vectorStore.add(sourceVectorId(source.getId(), chunk.index()), vector, metadata);
                    indexed++;
                } catch (Exception e) {
                    log.warn("Source vector chunk failed sourceId={}, chunkIndex={}: {}",
                            source.getId(), chunk.index(), e.getMessage());
                }
            }
            log.info("Source vectorization completed sourceId={}, chunks={}", source.getId(), indexed);
        } catch (Exception e) {
            log.warn("Source vectorization failed sourceId={}: {}", source.getId(), e.getMessage());
        }
    }

    private List<SourceVectorChunk> sourceVectorChunks(WikiSource source, DocumentOutlineExtractor.DocumentOutline outline) {
        List<SourceVectorChunk> chunks = new ArrayList<>();
        String content = source.getContent();
        int index = 0;
        if (outline != null && outline.getSections() != null && !outline.getSections().isEmpty()) {
            for (DocumentOutlineExtractor.DocumentSection section : outline.getSections()) {
                String text = sectionText(content, section);
                if (text.isBlank()) {
                    continue;
                }
                for (String part : splitVectorText(text)) {
                    chunks.add(new SourceVectorChunk(index++, section.getId(), section.getPath(), part));
                }
            }
        }
        if (chunks.isEmpty()) {
            for (String part : splitVectorText(content)) {
                chunks.add(new SourceVectorChunk(index++, "source", source.getTitle(), part));
            }
        }
        return chunks;
    }

    private String sectionText(String content, DocumentOutlineExtractor.DocumentSection section) {
        if (content == null || section == null) {
            return "";
        }
        int start = Math.max(0, Math.min(section.getCharStart(), content.length()));
        int end = Math.max(start, Math.min(section.getCharEnd(), content.length()));
        String text = end > start ? content.substring(start, end) : section.getExcerpt();
        return text == null ? "" : text.trim();
    }

    private List<String> splitVectorText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null) {
            return chunks;
        }
        String normalized = text.replaceAll("\\n{3,}", "\n\n").trim();
        int pos = 0;
        while (pos < normalized.length()) {
            int end = Math.min(normalized.length(), pos + SOURCE_VECTOR_CHUNK_CHARS);
            String chunk = normalized.substring(pos, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == normalized.length()) {
                break;
            }
            pos = Math.max(end - SOURCE_VECTOR_CHUNK_OVERLAP, pos + 1);
        }
        return chunks;
    }

    private void deleteExistingSourceVectors(Long sourceId) {
        for (int i = 0; i < SOURCE_VECTOR_DELETE_LIMIT; i++) {
            try {
                vectorStore.delete(sourceVectorId(sourceId, i));
            } catch (Exception e) {
                log.debug("Source vector delete ignored sourceId={}, chunkIndex={}: {}", sourceId, i, e.getMessage());
            }
        }
    }

    private String sourceVectorId(Long sourceId, int chunkIndex) {
        return "wiki_source_" + sourceId + "_" + chunkIndex;
    }

    private record SourceVectorChunk(int index, String sectionId, String sectionPath, String content) {}

    private void validateGeneratedPages(JsonArray pages) {
        if (pages == null || pages.isEmpty()) {
            throw new com.middleware.manager.exception.BusinessException(
                    com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 未生成有效 Wiki 页面");
        }
        for (JsonElement elem : pages) {
            if (!elem.isJsonObject()) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面格式非法");
            }
            JsonObject page = elem.getAsJsonObject();
            String title = trimToNull(getAsString(page, "title"));
            String pageType = trimToNull(getAsString(page, "page_type"));
            String content = trimToNull(getAsString(page, "content"));
            String summary = getAsString(page, "summary");

            if (title == null || content == null) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面缺少标题或正文");
            }
            if (title.length() > 200) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面标题超过 200 字符");
            }
            if (pageType == null || !VALID_PAGE_TYPES.contains(pageType)) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面类型非法: " + pageType);
            }
            if (summary != null && summary.length() > 500) {
                throw new com.middleware.manager.exception.BusinessException(
                        com.middleware.manager.constant.ErrorCode.PARAM_INVALID, "LLM 页面摘要超过 500 字符");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void updatePageFromJson(WikiPage page, JsonObject json, WikiSource source) {
        page.setTitle(getAsString(json, "title"));
        page.setPageType(getAsString(json, "page_type"));
        page.setCategory(getAsString(json, "category"));
        if (page.getCategory() == null && source != null) page.setCategory(source.getCategory());
        page.setSoftware(getAsString(json, "software"));
        if (page.getSoftware() == null && source != null) page.setSoftware(source.getSoftware());
        page.setVersion(getAsString(json, "version"));
        page.setCanonicalTitle(canonicalTitle(page.getTitle()));
        page.setAliasTitles(aliasTitlesFromPage(json));
        page.setContent(getAsString(json, "content"));
        page.setSummary(getAsString(json, "summary"));
        page.setStatus("DRAFT");
        page.setCompiledBy("ingest-agent");
        page.setCompiledAt(LocalDateTime.now());
        page.setSourceRefs(sourceRefsFromPage(json, source));
    }

    private String aliasTitlesFromPage(JsonObject json) {
        JsonArray aliases = new JsonArray();
        if (json != null && json.has("alias_titles") && json.get("alias_titles").isJsonArray()) {
            for (JsonElement alias : json.getAsJsonArray("alias_titles")) {
                if (!alias.isJsonNull()) {
                    String value = alias.getAsString();
                    if (value != null && !value.isBlank()) {
                        aliases.add(value.trim());
                    }
                }
            }
        }
        return aliases.isEmpty() ? null : gson.toJson(aliases);
    }

    private String mergeAliasTitles(String existingAliasTitles, JsonObject pageObj) {
        Set<String> aliases = new LinkedHashSet<>(parseAliasTitles(existingAliasTitles, null));
        String newAliases = aliasTitlesFromPage(pageObj);
        if (newAliases != null) {
            try {
                JsonArray newArray = gson.fromJson(newAliases, JsonArray.class);
                for (JsonElement alias : newArray) {
                    if (!alias.isJsonNull() && !alias.getAsString().isBlank()) {
                        aliases.add(alias.getAsString().trim());
                    }
                }
            } catch (Exception e) {
                log.debug("Ignore invalid generated alias_titles: {}", e.getMessage());
            }
        }
        if (aliases.isEmpty()) {
            return existingAliasTitles;
        }
        JsonArray merged = new JsonArray();
        for (String alias : aliases) {
            merged.add(alias);
        }
        return gson.toJson(merged);
    }

    private String sourceRefsFromPage(JsonObject json, WikiSource source) {
        if (json != null && json.has("source_refs") && json.get("source_refs").isJsonObject()) {
            JsonObject refs = json.getAsJsonObject("source_refs");
            if (!refs.has("source_id") && source != null) refs.addProperty("source_id", source.getId());
            if (!refs.has("source_title") && source != null) refs.addProperty("source_title", source.getTitle());
            if (!refs.has("source_type") && source != null) refs.addProperty("source_type", source.getSourceType());
            return gson.toJson(refs);
        }
        if (source == null) {
            return null;
        }
        JsonObject ref = new JsonObject();
        ref.addProperty("source_title", source.getTitle());
        ref.addProperty("source_type", source.getSourceType());
        ref.addProperty("source_id", source.getId());
        ref.add("sections", new JsonArray());
        return gson.toJson(ref);
    }

    private String buildExistingPagesSummary(String category, String software) {
        List<WikiPage> pages = (category != null || software != null)
                ? pageMapper.findByCategoryOrSoftwareExcludingContent(category, software, 20)
                : pageMapper.findAllExcludingContent();
        StringBuilder sb = new StringBuilder();
        for (WikiPage p : pages) {
            sb.append("- ").append(p.getTitle());
            if (p.getSummary() != null) sb.append("：").append(p.getSummary());
            sb.append("\n");
        }
        return sb.length() > 0 ? sb.toString() : "（暂无相关页面）";
    }

    private String buildSoftwareReference() {
        List<SoftwareType> types = softwareTypeMapper.findAllByOrderByCategoryAscNameAsc();
        StringBuilder sb = new StringBuilder();
        String currentCategory = null;
        for (SoftwareType st : types) {
            if (!st.getCategory().equals(currentCategory)) {
                if (currentCategory != null) sb.append("\n");
                sb.append("【").append(st.getCategory()).append("】");
                currentCategory = st.getCategory();
            }
            sb.append(st.getName()).append(" ");
        }
        return sb.length() > 0 ? sb.toString() : "（暂无软件分类数据）";
    }

    public static String sha256(String input) {
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
