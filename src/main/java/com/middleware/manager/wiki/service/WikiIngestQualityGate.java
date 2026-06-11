package com.middleware.manager.wiki.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
                if (!hasSourceRefsSections(page)) {
                    report.getPagesWithoutSourceRefs().add(title == null ? "未命名页面" : title);
                }
                JsonObject coverage = page.has("coverage") && page.get("coverage").isJsonObject()
                        ? page.getAsJsonObject("coverage") : null;
                if (coverage != null && coverage.has("section_ids") && coverage.get("section_ids").isJsonArray()) {
                    for (JsonElement id : coverage.getAsJsonArray("section_ids")) {
                        if (!id.isJsonNull()) {
                            covered.add(id.getAsString());
                        }
                    }
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
                || !report.getPagesWithoutSourceRefs().isEmpty()) {
            report.setStatus("PARTIAL");
        } else {
            report.setStatus("SUCCESS");
        }
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

    public static class QualityReport {
        private double coverageRatio;
        private int requiredSectionsTotal;
        private int requiredSectionsCovered;
        private List<String> missingSections = new ArrayList<>();
        private List<String> shortPages = new ArrayList<>();
        private List<String> duplicateTitles = new ArrayList<>();
        private List<String> pagesWithoutSourceRefs = new ArrayList<>();
        private List<String> issues = new ArrayList<>();
        private String status;

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
        public List<String> getDuplicateTitles() { return duplicateTitles; }
        public void setDuplicateTitles(List<String> duplicateTitles) { this.duplicateTitles = duplicateTitles; }
        public List<String> getPagesWithoutSourceRefs() { return pagesWithoutSourceRefs; }
        public void setPagesWithoutSourceRefs(List<String> pagesWithoutSourceRefs) { this.pagesWithoutSourceRefs = pagesWithoutSourceRefs; }
        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
