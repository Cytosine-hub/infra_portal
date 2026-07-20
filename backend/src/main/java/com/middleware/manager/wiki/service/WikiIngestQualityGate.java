package com.middleware.manager.wiki.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WikiIngestQualityGate {

    public QualityReport evaluate(DocumentOutlineExtractor.DocumentOutline outline, JsonArray pages) {
        QualityReport report = new QualityReport();
        if (outline == null || outline.getSections() == null || outline.getSections().isEmpty()) {
            report.setStatus("FAILED");
            report.getIssues().add("未抽取到文档章节");
            return report;
        }

        Set<String> requiredSectionIds = new HashSet<>();
        for (DocumentOutlineExtractor.DocumentSection section : outline.getSections()) {
            if (section.isRequired()) {
                requiredSectionIds.add(section.getId());
            }
        }
        report.setRequiredSectionsTotal(requiredSectionIds.size());

        Set<String> covered = new HashSet<>();
        Set<String> titles = new HashSet<>();
        if (pages != null) {
            for (JsonElement element : pages) {
                if (!element.isJsonObject()) continue;
                JsonObject page = element.getAsJsonObject();
                String title = getAsString(page, "title");
                String pageType = getAsString(page, "page_type");
                if (title != null && !titles.add(title + "/" + pageType)) {
                    report.getDuplicateTitles().add(title);
                }
                String content = getAsString(page, "content");
                if (!"OVERVIEW".equals(pageType) && (content == null || content.length() < 300)) {
                    report.getShortPages().add(title == null ? "未命名页面" : title);
                }
                if (isGenericTitle(title, page, outline)) {
                    report.getGenericTitles().add(title == null ? "未命名页面" : title);
                }
                if (!"OVERVIEW".equals(pageType) && !hasSourceRefsSections(page)) {
                    report.getPagesWithoutSourceRefs().add(title == null ? "未命名页面" : title);
                }
                JsonObject coverage = page.has("coverage") && page.get("coverage").isJsonObject()
                        ? page.getAsJsonObject("coverage") : null;
                int requiredSectionsInPage = 0;
                if (coverage != null && coverage.has("section_ids") && coverage.get("section_ids").isJsonArray()) {
                    for (JsonElement id : coverage.getAsJsonArray("section_ids")) {
                        if (!id.isJsonNull()) {
                            String sectionId = id.getAsString();
                            covered.add(sectionId);
                            if (requiredSectionIds.contains(sectionId)) {
                                requiredSectionsInPage++;
                            }
                        }
                    }
                }
                if (!"OVERVIEW".equals(pageType) && requiredSectionsInPage >= 2
                        && (content == null || content.length() < requiredSectionsInPage * 300)) {
                    report.getOverCompressedPages().add(title == null ? "未命名页面" : title);
                }
            }
        }

        int coveredRequired = 0;
        for (String sectionId : requiredSectionIds) {
            if (covered.contains(sectionId)) {
                coveredRequired++;
            } else {
                report.getMissingSections().add(sectionId);
            }
        }
        report.setRequiredSectionsCovered(coveredRequired);
        report.setCoverageRatio(requiredSectionIds.isEmpty() ? 1.0 : (double) coveredRequired / requiredSectionIds.size());

        if (report.getCoverageRatio() < 0.7) {
            report.setStatus("FAILED");
            report.getIssues().add("章节覆盖率低于 70%");
        } else if (report.getCoverageRatio() < 0.9 || !report.getMissingSections().isEmpty()
                || !report.getPagesWithoutSourceRefs().isEmpty()
                || !report.getGenericTitles().isEmpty()
                || !report.getOverCompressedPages().isEmpty()
                || !report.getShortPages().isEmpty()
                || !report.getDuplicateTitles().isEmpty()) {
            report.setStatus("PARTIAL");
        } else {
            report.setStatus("SUCCESS");
        }
        appendQualityIssues(report);
        return report;
    }

    private boolean hasSourceRefsSections(JsonObject page) {
        if (!page.has("source_refs") || !page.get("source_refs").isJsonObject()) {
            return false;
        }
        JsonObject refs = page.getAsJsonObject("source_refs");
        return refs.has("sections") && refs.get("sections").isJsonArray()
                && !refs.getAsJsonArray("sections").isEmpty();
    }

    private String getAsString(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return elem != null && !elem.isJsonNull() ? elem.getAsString() : null;
    }

    private boolean isGenericTitle(String title, JsonObject page, DocumentOutlineExtractor.DocumentOutline outline) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String normalized = title.replaceAll("\\s+", "");
        String lower = normalized.toLowerCase(Locale.ROOT);
        String software = getAsString(page, "software");
        if ((software == null || software.isBlank()) && outline != null) {
            software = outline.getSoftware();
        }
        boolean lacksSoftware = software != null && !software.isBlank()
                && !lower.contains(software.replaceAll("\\s+", "").toLowerCase(Locale.ROOT));
        boolean genericKeyword = lower.matches(".*(安装方式|安装步骤|环境要求|产品配置|配置说明|参数说明|问题处理|故障处理|监控指标|常见问题|操作步骤|使用说明).*");
        boolean veryShortGeneric = normalized.length() <= 8
                && lower.matches(".*(安装|配置|参数|监控|故障|问题|步骤|说明|概述).*");
        return lacksSoftware && (genericKeyword || veryShortGeneric);
    }

    private void appendQualityIssues(QualityReport report) {
        if (!report.getGenericTitles().isEmpty()) {
            report.getIssues().add("存在泛化标题");
        }
        if (!report.getOverCompressedPages().isEmpty()) {
            report.getIssues().add("存在过度压缩页面");
        }
        if (!report.getPagesWithoutSourceRefs().isEmpty()) {
            report.getIssues().add("存在缺少来源章节的页面");
        }
        if (!report.getShortPages().isEmpty()) {
            report.getIssues().add("存在正文过短页面");
        }
    }

    public static class QualityReport {
        private double coverageRatio;
        private int requiredSectionsTotal;
        private int requiredSectionsCovered;
        private List<String> missingSections = new ArrayList<>();
        private List<String> shortPages = new ArrayList<>();
        private List<String> genericTitles = new ArrayList<>();
        private List<String> overCompressedPages = new ArrayList<>();
        private List<String> duplicateTitles = new ArrayList<>();
        private List<String> pagesWithoutSourceRefs = new ArrayList<>();
        private List<String> issues = new ArrayList<>();
        private String status;

        // Timing metrics (milliseconds)
        private long totalDurationMs;
        private long outlineDurationMs;
        private long sectionFactsDurationMs;
        private long pagePlanDurationMs;
        private long pageGenerationDurationMs;
        private long qualityGateDurationMs;

        // LLM call metrics
        private int llmCallCount;
        private int llmRetryCount;
        private int llmInputTokens;
        private int llmOutputTokens;

        public double getCoverageRatio() { return coverageRatio; }
        public void setCoverageRatio(double coverageRatio) { this.coverageRatio = coverageRatio; }
        public int getRequiredSectionsTotal() { return requiredSectionsTotal; }
        public void setRequiredSectionsTotal(int requiredSectionsTotal) { this.requiredSectionsTotal = requiredSectionsTotal; }
        public int getRequiredSectionsCovered() { return requiredSectionsCovered; }
        public void setRequiredSectionsCovered(int requiredSectionsCovered) { this.requiredSectionsCovered = requiredSectionsCovered; }
        public List<String> getMissingSections() { return missingSections; }
        public void setMissingSections(List<String> missingSections) { this.missingSections = missingSections; }
        public List<String> getShortPages() { return shortPages; }
        public void setShortPages(List<String> shortPages) { this.shortPages = shortPages; }
        public List<String> getGenericTitles() { return genericTitles; }
        public void setGenericTitles(List<String> genericTitles) { this.genericTitles = genericTitles; }
        public List<String> getOverCompressedPages() { return overCompressedPages; }
        public void setOverCompressedPages(List<String> overCompressedPages) { this.overCompressedPages = overCompressedPages; }
        public List<String> getDuplicateTitles() { return duplicateTitles; }
        public void setDuplicateTitles(List<String> duplicateTitles) { this.duplicateTitles = duplicateTitles; }
        public List<String> getPagesWithoutSourceRefs() { return pagesWithoutSourceRefs; }
        public void setPagesWithoutSourceRefs(List<String> pagesWithoutSourceRefs) { this.pagesWithoutSourceRefs = pagesWithoutSourceRefs; }
        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public long getTotalDurationMs() { return totalDurationMs; }
        public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
        public long getOutlineDurationMs() { return outlineDurationMs; }
        public void setOutlineDurationMs(long outlineDurationMs) { this.outlineDurationMs = outlineDurationMs; }
        public long getSectionFactsDurationMs() { return sectionFactsDurationMs; }
        public void setSectionFactsDurationMs(long sectionFactsDurationMs) { this.sectionFactsDurationMs = sectionFactsDurationMs; }
        public long getPagePlanDurationMs() { return pagePlanDurationMs; }
        public void setPagePlanDurationMs(long pagePlanDurationMs) { this.pagePlanDurationMs = pagePlanDurationMs; }
        public long getPageGenerationDurationMs() { return pageGenerationDurationMs; }
        public void setPageGenerationDurationMs(long pageGenerationDurationMs) { this.pageGenerationDurationMs = pageGenerationDurationMs; }
        public long getQualityGateDurationMs() { return qualityGateDurationMs; }
        public void setQualityGateDurationMs(long qualityGateDurationMs) { this.qualityGateDurationMs = qualityGateDurationMs; }
        public int getLlmCallCount() { return llmCallCount; }
        public void setLlmCallCount(int llmCallCount) { this.llmCallCount = llmCallCount; }
        public int getLlmRetryCount() { return llmRetryCount; }
        public void setLlmRetryCount(int llmRetryCount) { this.llmRetryCount = llmRetryCount; }
        public int getLlmInputTokens() { return llmInputTokens; }
        public void setLlmInputTokens(int llmInputTokens) { this.llmInputTokens = llmInputTokens; }
        public int getLlmOutputTokens() { return llmOutputTokens; }
        public void setLlmOutputTokens(int llmOutputTokens) { this.llmOutputTokens = llmOutputTokens; }
    }
}
