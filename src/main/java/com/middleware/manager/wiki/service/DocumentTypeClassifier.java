package com.middleware.manager.wiki.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentTypeClassifier {

    public static final String PRODUCT_OVERVIEW = "PRODUCT_OVERVIEW";
    public static final String INSTALL_GUIDE = "INSTALL_GUIDE";
    public static final String CONFIG_GUIDE = "CONFIG_GUIDE";
    public static final String MONITORING_GUIDE = "MONITORING_GUIDE";
    public static final String TROUBLESHOOTING = "TROUBLESHOOTING";
    public static final String STANDARD_SPEC = "STANDARD_SPEC";
    public static final String RELEASE_NOTE = "RELEASE_NOTE";
    public static final String GENERAL_GUIDE = "GENERAL_GUIDE";

    public Classification classify(String title, String content) {
        String safeTitle = title == null ? "" : title;
        String safeContent = content == null ? "" : content;
        String text = (safeTitle + "\n" + safeContent.substring(0, Math.min(safeContent.length(), 12000)))
                .toLowerCase(Locale.ROOT);

        List<Score> scores = new ArrayList<>();
        scores.add(score(INSTALL_GUIDE, text, "安装", "部署", "启动", "停止", "卸载", "license", "授权", "环境准备"));
        scores.add(score(CONFIG_GUIDE, text, "配置", "参数", "默认值", "配置文件", "生效", "示例", "端口"));
        scores.add(score(MONITORING_GUIDE, text, "监控", "指标", "actuator", "jmx", "告警", "采集", "metric"));
        scores.add(score(TROUBLESHOOTING, text, "故障", "排障", "问题", "异常", "日志", "根因", "处理", "验证"));
        scores.add(score(STANDARD_SPEC, text, "标准", "规范", "基线", "要求", "必须", "禁止", "检查项"));
        scores.add(score(RELEASE_NOTE, text, "release", "版本说明", "变更", "修复", "兼容", "升级", "缺陷"));
        scores.add(score(PRODUCT_OVERVIEW, text, "产品", "概述", "简介", "架构", "特性", "模块", "功能"));

        Score best = scores.stream()
                .max((a, b) -> Integer.compare(a.value(), b.value()))
                .orElse(new Score(GENERAL_GUIDE, 0));

        String documentType = best.value() <= 0 ? GENERAL_GUIDE : best.type();
        double confidence = Math.min(0.95, 0.45 + best.value() * 0.08);

        Classification classification = new Classification();
        classification.setDocumentType(documentType);
        classification.setConfidence(confidence);
        classification.setFormat(detectFormat(safeTitle));
        classification.setStructureQuality(detectStructureQuality(safeContent));
        classification.setSignals(buildSignals(documentType, safeContent));
        return classification;
    }

    private Score score(String type, String text, String... keywords) {
        int value = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                value++;
            }
        }
        return new Score(type, value);
    }

    private String detectFormat(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "MARKDOWN";
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "WORD";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "SPREADSHEET";
        return "TEXT";
    }

    private String detectStructureQuality(String content) {
        if (content == null || content.isBlank()) return "LOW";
        if (content.matches("(?s).*\\n#{1,6}\\s+.+")) return "HIGH";
        if (content.matches("(?s).*\\n\\s*((第[一二三四五六七八九十0-9]+章)|([0-9]+(\\.[0-9]+){0,4})|([一二三四五六七八九十]+、)).+")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> buildSignals(String documentType, String content) {
        List<String> signals = new ArrayList<>();
        signals.add("document_type=" + documentType);
        if (content != null && content.matches("(?s).*\\n#{1,6}\\s+.+")) {
            signals.add("包含 Markdown 标题");
        }
        if (content != null && content.matches("(?s).*\\n\\s*[0-9]+(\\.[0-9]+){0,4}\\s+.+")) {
            signals.add("包含数字编号标题");
        }
        if (content != null && content.contains("....")) {
            signals.add("疑似目录页");
        }
        return signals;
    }

    private record Score(String type, int value) {}

    public static class Classification {
        private String documentType;
        private double confidence;
        private String format;
        private String structureQuality;
        private List<String> signals = new ArrayList<>();

        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getStructureQuality() { return structureQuality; }
        public void setStructureQuality(String structureQuality) { this.structureQuality = structureQuality; }
        public List<String> getSignals() { return signals; }
        public void setSignals(List<String> signals) { this.signals = signals; }
    }
}
