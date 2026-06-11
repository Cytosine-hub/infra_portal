package com.middleware.manager.wiki.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentOutlineExtractor {
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^\\s*((第[一二三四五六七八九十0-9]+章)|([0-9]+(\\.[0-9]+){0,4})|([一二三四五六七八九十]+、)|(\\([一二三四五六七八九十0-9]+\\)))\\s+(.+?)\\s*$");
    public DocumentOutline extract(String title, String content, String category, String software,
                                   DocumentTypeClassifier.Classification classification) {
        String safeContent = content == null ? "" : content;
        DocumentOutline outline = new DocumentOutline();
        outline.setTitle(title);
        outline.setCategory(category);
        outline.setSoftware(software);
        outline.setDocumentType(classification.getDocumentType());
        outline.setFormat(classification.getFormat());
        outline.setStructureQuality(classification.getStructureQuality());

        List<HeadingCandidate> headings = "MARKDOWN".equals(classification.getFormat())
                ? extractMarkdownHeadings(safeContent)
                : extractTextHeadings(safeContent);
        if (headings.isEmpty()) {
            headings = extractTextHeadings(safeContent);
        }

        List<DocumentSection> sections = headings.isEmpty()
                ? List.of(singleSection(safeContent, classification.getDocumentType()))
                : buildSections(safeContent, headings, classification.getDocumentType());
        outline.setSections(sections);
        return outline;
    }

    private List<HeadingCandidate> extractMarkdownHeadings(String content) {
        List<HeadingCandidate> headings = new ArrayList<>();
        int offset = 0;
        for (String line : content.split("\\R", -1)) {
            Matcher matcher = MARKDOWN_HEADING.matcher(line);
            if (matcher.matches() && !isInsideCodeFence(content, offset)) {
                headings.add(new HeadingCandidate(cleanHeading(matcher.group(2)), matcher.group(1).length(), offset));
            }
            offset += line.length() + 1;
        }
        return headings;
    }

    private boolean isInsideCodeFence(String content, int offset) {
        String before = content.substring(0, Math.min(offset, content.length()));
        int fences = 0;
        for (String line : before.split("\\R")) {
            if (line.trim().startsWith("```")) {
                fences++;
            }
        }
        return fences % 2 == 1;
    }

    private List<HeadingCandidate> extractTextHeadings(String content) {
        List<HeadingCandidate> headings = new ArrayList<>();
        int offset = 0;
        for (String line : content.split("\\R", -1)) {
            String trimmed = line.trim();
            Matcher numbered = NUMBERED_HEADING.matcher(trimmed);
            if (numbered.matches()) {
                String marker = numbered.group(1);
                String heading = cleanHeading(trimmed);
                headings.add(new HeadingCandidate(heading, headingLevel(marker), offset));
            } else if (looksLikeShortHeading(trimmed)) {
                headings.add(new HeadingCandidate(cleanHeading(trimmed), 2, offset));
            }
            offset += line.length() + 1;
        }
        return headings;
    }

    private boolean looksLikeShortHeading(String line) {
        if (line.length() < 3 || line.length() > 40) return false;
        if (line.endsWith("。") || line.endsWith("，") || line.endsWith(",")) return false;
        if (line.contains("http://") || line.contains("https://")) return false;
        return line.matches(".*(概述|简介|环境|配置|参数|步骤|启动|停止|验证|监控|指标|故障|问题|日志|标准|规范|要求|说明|注册|授权).*");
    }

    private List<DocumentSection> buildSections(String content, List<HeadingCandidate> headings, String documentType) {
        List<DocumentSection> sections = new ArrayList<>();
        Map<Integer, String> stack = new HashMap<>();
        for (int i = 0; i < headings.size(); i++) {
            HeadingCandidate heading = headings.get(i);
            int end = i + 1 < headings.size() ? headings.get(i + 1).start() : content.length();
            String sectionContent = content.substring(Math.min(heading.start(), content.length()), Math.min(end, content.length())).trim();
            stack.keySet().removeIf(level -> level >= heading.level());
            stack.put(heading.level(), heading.title());
            String path = buildPath(stack);

            DocumentSection section = new DocumentSection();
            section.setId("sec-" + String.format("%03d", i + 1));
            section.setPath(path);
            section.setLevel(heading.level());
            section.setOrder(i + 1);
            section.setCharStart(heading.start());
            section.setCharEnd(end);
            section.setRequired(isRequired(path, sectionContent, documentType, heading.level()));
            section.setSectionType(detectSectionType(path + "\n" + sectionContent, documentType));
            section.setConfidence(0.82);
            section.setExcerpt(excerpt(sectionContent));
            section.setBlocks(detectBlocks(sectionContent));
            sections.add(section);
        }
        return sections;
    }

    private DocumentSection singleSection(String content, String documentType) {
        DocumentSection section = new DocumentSection();
        section.setId("sec-001");
        section.setPath("全文");
        section.setLevel(1);
        section.setOrder(1);
        section.setCharStart(0);
        section.setCharEnd(content == null ? 0 : content.length());
        section.setRequired(true);
        section.setSectionType(detectSectionType(content, documentType));
        section.setConfidence(0.45);
        section.setExcerpt(excerpt(content));
        section.setBlocks(detectBlocks(content));
        return section;
    }

    private String buildPath(Map<Integer, String> stack) {
        List<Integer> levels = new ArrayList<>(stack.keySet());
        levels.sort(Integer::compareTo);
        List<String> path = new ArrayList<>();
        for (Integer level : levels) {
            path.add(stack.get(level));
        }
        return String.join("/", path);
    }

    private String cleanHeading(String heading) {
        if (heading == null) return "";
        return heading.replaceFirst("^\\s*((第[一二三四五六七八九十0-9]+章)|([0-9]+(\\.[0-9]+){0,4})|([一二三四五六七八九十]+、)|(\\([一二三四五六七八九十0-9]+\\)))\\s*", "")
                .replaceAll("\\s+\\.{2,}\\s*\\d+\\s*$", "")
                .trim();
    }

    private int headingLevel(String marker) {
        if (marker == null) return 2;
        if (marker.startsWith("第") || marker.matches("[一二三四五六七八九十]+、")) return 1;
        if (marker.contains(".")) return Math.min(6, marker.split("\\.").length);
        if (marker.startsWith("(") || marker.startsWith("（")) return 3;
        return 1;
    }

    private boolean isRequired(String path, String content, String documentType, int level) {
        if (level <= 2) return true;
        String text = (path + "\n" + content).toLowerCase(Locale.ROOT);
        return text.contains("必须") || text.contains("禁止") || text.contains("默认值")
                || text.contains("参数") || text.contains("步骤") || text.contains("验证")
                || text.contains("故障") || text.contains("指标") || text.contains("命令")
                || DocumentTypeClassifier.CONFIG_GUIDE.equals(documentType) && text.contains("配置")
                || DocumentTypeClassifier.TROUBLESHOOTING.equals(documentType) && text.contains("处理");
    }

    private String detectSectionType(String text, String documentType) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (value.contains("参数") || value.contains("默认值") || value.contains("配置项")) return "CONFIG_ITEM";
        if (value.contains("步骤") || value.contains("启动") || value.contains("停止") || value.contains("执行")) return "PROCEDURE";
        if (value.contains("指标") || value.contains("监控") || value.contains("jmx") || value.contains("actuator")) return "METRIC";
        if (value.contains("故障") || value.contains("异常") || value.contains("根因") || value.contains("处理")) return "TROUBLESHOOTING_STEP";
        if (value.contains("必须") || value.contains("禁止") || value.contains("标准") || value.contains("规范")) return "STANDARD_RULE";
        if (DocumentTypeClassifier.PRODUCT_OVERVIEW.equals(documentType)) return "OVERVIEW";
        return "REFERENCE";
    }

    private List<String> detectBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            blocks.add("paragraph");
            return blocks;
        }
        blocks.add("paragraph");
        if (content.contains("|")) blocks.add("table");
        if (content.contains("```") || content.matches("(?s).*\\n\\s*(sudo |systemctl |kubectl |docker |java |sh |./).*")) blocks.add("code");
        if (content.matches("(?s).*\\n\\s*[-*+]\\s+.+")) blocks.add("list");
        return blocks;
    }

    private String excerpt(String content) {
        if (content == null) return "";
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private record HeadingCandidate(String title, int level, int start) {}

    public static class DocumentOutline {
        private String documentType;
        private String format;
        private String title;
        private String category;
        private String software;
        private String structureQuality;
        private List<DocumentSection> sections = new ArrayList<>();

        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSoftware() { return software; }
        public void setSoftware(String software) { this.software = software; }
        public String getStructureQuality() { return structureQuality; }
        public void setStructureQuality(String structureQuality) { this.structureQuality = structureQuality; }
        public List<DocumentSection> getSections() { return sections; }
        public void setSections(List<DocumentSection> sections) { this.sections = sections; }
    }

    public static class DocumentSection {
        private String id;
        private String path;
        private int level;
        private int order;
        private int charStart;
        private int charEnd;
        private boolean required;
        private String sectionType;
        private double confidence;
        private String excerpt;
        private List<String> blocks = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public int getCharStart() { return charStart; }
        public void setCharStart(int charStart) { this.charStart = charStart; }
        public int getCharEnd() { return charEnd; }
        public void setCharEnd(int charEnd) { this.charEnd = charEnd; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public String getSectionType() { return sectionType; }
        public void setSectionType(String sectionType) { this.sectionType = sectionType; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getExcerpt() { return excerpt; }
        public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
        public List<String> getBlocks() { return blocks; }
        public void setBlocks(List<String> blocks) { this.blocks = blocks; }
    }
}
