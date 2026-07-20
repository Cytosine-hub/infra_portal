package com.middleware.manager.wiki.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WikiIngestQualityGateTest {

    private final WikiIngestQualityGate qualityGate = new WikiIngestQualityGate();

    @Test
    void passesWhenRequiredSectionsAreCoveredAndSourceRefsExist() {
        DocumentOutlineExtractor.DocumentOutline outline = outline("sec-001", "sec-002");
        JsonArray pages = new JsonArray();
        pages.add(page("BES V9.5.5 配置参数", "sec-001", "sec-002"));

        WikiIngestQualityGate.QualityReport report = qualityGate.evaluate(outline, pages);

        assertThat(report.getStatus()).isEqualTo("SUCCESS");
        assertThat(report.getCoverageRatio()).isEqualTo(1.0);
        assertThat(report.getMissingSections()).isEmpty();
    }

    @Test
    void failsWhenCoverageIsTooLow() {
        DocumentOutlineExtractor.DocumentOutline outline = outline("sec-001", "sec-002");
        JsonArray pages = new JsonArray();
        pages.add(page("BES V9.5.5 配置参数", "sec-001"));

        WikiIngestQualityGate.QualityReport report = qualityGate.evaluate(outline, pages);

        assertThat(report.getStatus()).isEqualTo("FAILED");
        assertThat(report.getMissingSections()).containsExactly("sec-002");
    }

    @Test
    void marksGenericTitlesAsPartial() {
        DocumentOutlineExtractor.DocumentOutline outline = outline("sec-001");
        JsonArray pages = new JsonArray();
        pages.add(page("参数说明", "sec-001"));

        WikiIngestQualityGate.QualityReport report = qualityGate.evaluate(outline, pages);

        assertThat(report.getStatus()).isEqualTo("PARTIAL");
        assertThat(report.getGenericTitles()).containsExactly("参数说明");
        assertThat(report.getIssues()).contains("存在泛化标题");
    }

    @Test
    void marksOverCompressedPagesAsPartial() {
        DocumentOutlineExtractor.DocumentOutline outline = outline("sec-001", "sec-002");
        JsonObject compressed = page("BES V9.5.5 配置参数", "sec-001", "sec-002");
        compressed.addProperty("content", "这是一段覆盖多个章节但明显过短的正文。".repeat(20));
        JsonArray pages = new JsonArray();
        pages.add(compressed);

        WikiIngestQualityGate.QualityReport report = qualityGate.evaluate(outline, pages);

        assertThat(report.getStatus()).isEqualTo("PARTIAL");
        assertThat(report.getOverCompressedPages()).containsExactly("BES V9.5.5 配置参数");
        assertThat(report.getIssues()).contains("存在过度压缩页面");
    }

    private DocumentOutlineExtractor.DocumentOutline outline(String... sectionIds) {
        DocumentOutlineExtractor.DocumentOutline outline = new DocumentOutlineExtractor.DocumentOutline();
        outline.setDocumentType(DocumentTypeClassifier.CONFIG_GUIDE);
        outline.setFormat("MARKDOWN");
        outline.setTitle("config.md");
        outline.setSoftware("BES");
        outline.setSections(List.of(sectionIds).stream().map(id -> {
            DocumentOutlineExtractor.DocumentSection section = new DocumentOutlineExtractor.DocumentSection();
            section.setId(id);
            section.setPath("配置/" + id);
            section.setLevel(2);
            section.setOrder(Integer.parseInt(id.substring(id.length() - 1)));
            section.setRequired(true);
            section.setSectionType("CONFIG_ITEM");
            return section;
        }).toList());
        return outline;
    }

    private JsonObject page(String title, String... sectionIds) {
        JsonObject page = new JsonObject();
        page.addProperty("title", title);
        page.addProperty("page_type", "STANDARD");
        page.addProperty("software", "BES");
        page.addProperty("content", "这是一段超过三百字的正文。".repeat(80));

        JsonArray ids = new JsonArray();
        JsonArray refsSections = new JsonArray();
        for (String sectionId : sectionIds) {
            ids.add(sectionId);
            JsonObject section = new JsonObject();
            section.addProperty("section_id", sectionId);
            section.addProperty("section_path", "配置/" + sectionId);
            refsSections.add(section);
        }

        JsonObject coverage = new JsonObject();
        coverage.add("section_ids", ids);
        page.add("coverage", coverage);

        JsonObject refs = new JsonObject();
        refs.addProperty("source_id", 1);
        refs.addProperty("source_title", "config.md");
        refs.addProperty("source_type", "UPLOAD");
        refs.add("sections", refsSections);
        page.add("source_refs", refs);
        return page;
    }
}
