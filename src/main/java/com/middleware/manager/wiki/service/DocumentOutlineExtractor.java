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
    private static final Pattern SETEXT_UNDERLINE = Pattern.compile("^\\s*(=+|-{2,})\\s*$");
    private static final String HORIZONTAL_SPACE = "[\\s\\u00A0\\u3000]";
    private static final String HEADING_MARKER =
            "(?:第[一二三四五六七八九十0-9]+章)|(?:[0-9]+(?:\\.[0-9]+){0,4})|(?:[一二三四五六七八九十]+、)|(?:\\([一二三四五六七八九十0-9]+\\))";
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
            "^" + HORIZONTAL_SPACE + "*((" + HEADING_MARKER + "))"
                    + HORIZONTAL_SPACE + "+(.+?)" + HORIZONTAL_SPACE + "*$");
    private static final Pattern WORD_STYLE_HEADING = Pattern.compile("^\\s*(?:Heading|标题)\\s*([1-6])\\s*[:：\\t ]+(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOC_ENTRY = Pattern.compile(
            "^" + HORIZONTAL_SPACE + "*((?:" + HEADING_MARKER + ")?"
                    + HORIZONTAL_SPACE + "*[^\\n]{2,100}?)"
                    + HORIZONTAL_SPACE + "*(?:\\.{2,}|．{2,}|…{1,}|"
                    + HORIZONTAL_SPACE + "{2,})"
                    + HORIZONTAL_SPACE + "*(\\d{1,4})" + HORIZONTAL_SPACE + "*$");

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
        List<LineInfo> lines = splitLines(content);
        boolean inFence = false;
        for (int i = 0; i < lines.size(); i++) {
            LineInfo lineInfo = lines.get(i);
            String line = lineInfo.text();
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }

            Matcher matcher = MARKDOWN_HEADING.matcher(line);
            if (matcher.matches()) {
                headings.add(new HeadingCandidate(cleanHeading(matcher.group(2)), matcher.group(1).length(),
                        lineInfo.start(), "markdown-heading", null, 0.96));
                continue;
            }

            Matcher setext = SETEXT_UNDERLINE.matcher(trimmed);
            if (setext.matches() && i > 0) {
                LineInfo previous = previousNonBlankLine(lines, i - 1);
                if (previous != null && looksLikeSetextHeading(previous.text())) {
                    int level = trimmed.startsWith("=") ? 1 : 2;
                    headings.add(new HeadingCandidate(cleanHeading(previous.text()), level,
                            previous.start(), "markdown-setext", null, 0.9));
                }
            }
        }
        return headings;
    }

    private boolean looksLikeSetextHeading(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        return !trimmed.isBlank() && trimmed.length() <= 80
                && !trimmed.startsWith("|")
                && !trimmed.matches(".*[。；;]$");
    }

    private List<HeadingCandidate> extractTextHeadings(String content) {
        List<HeadingCandidate> bodyHeadings = new ArrayList<>();
        List<HeadingCandidate> tocHeadings = new ArrayList<>();
        Map<String, Integer> tocPages = new HashMap<>();
        List<LineInfo> lines = splitLines(content);
        for (LineInfo lineInfo : lines) {
            String trimmed = lineInfo.text().trim();
            if (trimmed.isBlank()) {
                continue;
            }

            Matcher toc = TOC_ENTRY.matcher(trimmed);
            if (toc.matches()) {
                String tocTitle = cleanHeading(toc.group(1));
                Integer pageNumber = parseInt(toc.group(2));
                if (!tocTitle.isBlank() && pageNumber != null) {
                    tocPages.put(normalizeHeadingKey(tocTitle), pageNumber);
                    tocHeadings.add(new HeadingCandidate(tocTitle, headingLevelFromTitle(toc.group(1)),
                            lineInfo.start(), "toc-entry", pageNumber, 0.58));
                }
                continue;
            }

            Matcher styled = WORD_STYLE_HEADING.matcher(trimmed);
            if (styled.matches()) {
                String heading = cleanHeading(styled.group(2));
                bodyHeadings.add(new HeadingCandidate(heading, parseInt(styled.group(1), 2),
                        lineInfo.start(), "word-style", pageForHeading(content, lineInfo.start(), heading, tocPages), 0.92));
                continue;
            }

            Matcher numbered = NUMBERED_HEADING.matcher(trimmed);
            if (numbered.matches()) {
                String marker = numbered.group(1);
                String heading = cleanHeading(trimmed);
                bodyHeadings.add(new HeadingCandidate(heading, headingLevel(marker), lineInfo.start(),
                        "numbered-heading", pageForHeading(content, lineInfo.start(), heading, tocPages), 0.84));
            } else if (looksLikeShortHeading(trimmed)) {
                String heading = cleanHeading(trimmed);
                bodyHeadings.add(new HeadingCandidate(heading, 2, lineInfo.start(),
                        "short-line", pageForHeading(content, lineInfo.start(), heading, tocPages), 0.62));
            }
        }
        return bodyHeadings.isEmpty() ? tocHeadings : bodyHeadings;
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
            section.setParagraphStart(paragraphIndexAtOffset(content, heading.start()));
            section.setParagraphEnd(paragraphIndexAtOffset(content, end));
            section.setPageRange(pageRange(content, heading, i + 1 < headings.size() ? headings.get(i + 1) : null, end));
            section.setSourceSignal(heading.sourceSignal());
            section.setRequired(isRequired(path, sectionContent, documentType, heading.level()));
            section.setSectionType(detectSectionType(path + "\n" + sectionContent, documentType));
            section.setConfidence(heading.confidence());
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
        section.setParagraphStart(1);
        section.setParagraphEnd(Math.max(1, paragraphIndexAtOffset(content, content == null ? 0 : content.length())));
        section.setPageRange(content != null && content.indexOf('\f') >= 0 ? "1-" + pageAtOffset(content, content.length()) : null);
        section.setSourceSignal("full-text");
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
        return heading.replaceFirst("^" + HORIZONTAL_SPACE + "*((" + HEADING_MARKER + "))" + HORIZONTAL_SPACE + "*", "")
                .replaceAll(HORIZONTAL_SPACE + "+(?:\\.{2,}|．{2,}|…{1,})" + HORIZONTAL_SPACE + "*\\d+" + HORIZONTAL_SPACE + "*$", "")
                .trim();
    }

    private int headingLevel(String marker) {
        if (marker == null) return 2;
        if (marker.startsWith("第") || marker.matches("[一二三四五六七八九十]+、")) return 1;
        if (marker.contains(".")) return Math.min(6, marker.split("\\.").length);
        if (marker.startsWith("(") || marker.startsWith("（")) return 3;
        return 1;
    }

    private int headingLevelFromTitle(String title) {
        Matcher matcher = NUMBERED_HEADING.matcher(title == null ? "" : title.trim());
        if (matcher.matches()) {
            return headingLevel(matcher.group(1));
        }
        return 1;
    }

    private boolean isRequired(String path, String content, String documentType, int level) {
        if (level <= 2) return true;
        String text = (path + "\n" + content).toLowerCase(Locale.ROOT);
        boolean generalRequired = text.contains("必须") || text.contains("禁止") || text.contains("默认值")
                || text.contains("参数") || text.contains("步骤") || text.contains("验证")
                || text.contains("故障") || text.contains("指标") || text.contains("命令");
        boolean typeRequired = switch (documentType) {
            case DocumentTypeClassifier.INSTALL_GUIDE -> containsAny(text, "环境", "前置", "安装", "部署", "启动", "验证", "回滚", "授权", "卸载");
            case DocumentTypeClassifier.CONFIG_GUIDE -> containsAny(text, "配置", "参数", "默认值", "示例", "生效", "端口", "路径");
            case DocumentTypeClassifier.MONITORING_GUIDE -> containsAny(text, "监控", "指标", "告警", "采集", "阈值", "端点");
            case DocumentTypeClassifier.TROUBLESHOOTING -> containsAny(text, "故障", "异常", "日志", "根因", "处理", "修复", "验证");
            case DocumentTypeClassifier.STANDARD_SPEC -> containsAny(text, "标准", "规范", "基线", "必须", "禁止", "不得", "检查项");
            case DocumentTypeClassifier.RELEASE_NOTE -> containsAny(text, "版本", "变更", "修复", "兼容", "升级", "影响");
            default -> false;
        };
        return generalRequired || typeRequired;
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
        if (content.matches("(?s).*(图\\s*\\d+|图片|截图|image|figure).*")) blocks.add("image");
        return blocks;
    }

    private String excerpt(String content) {
        if (content == null) return "";
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<LineInfo> splitLines(String content) {
        List<LineInfo> lines = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        Matcher matcher = Pattern.compile(".*?(?:\\R|$)").matcher(content);
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) {
                break;
            }
            String raw = matcher.group();
            String text = raw.replaceFirst("\\R$", "");
            lines.add(new LineInfo(text, matcher.start(), matcher.end()));
        }
        return lines;
    }

    private LineInfo previousNonBlankLine(List<LineInfo> lines, int index) {
        for (int i = index; i >= 0; i--) {
            if (!lines.get(i).text().trim().isBlank()) {
                return lines.get(i);
            }
        }
        return null;
    }

    private String pageRange(String content, HeadingCandidate current, HeadingCandidate next, int sectionEnd) {
        Integer startPage = current.pageNumber() != null ? current.pageNumber() : pageAtOffsetIfPaged(content, current.start());
        Integer endPage = null;
        if (next != null && next.pageNumber() != null) {
            endPage = Math.max(startPage == null ? next.pageNumber() : startPage, next.pageNumber() - 1);
        }
        if (endPage == null) {
            endPage = pageAtOffsetIfPaged(content, sectionEnd);
        }
        if (startPage == null && endPage == null) {
            return null;
        }
        if (startPage == null) {
            startPage = endPage;
        }
        if (endPage == null || endPage < startPage) {
            endPage = startPage;
        }
        return startPage.equals(endPage) ? String.valueOf(startPage) : startPage + "-" + endPage;
    }

    private Integer pageAtOffsetIfPaged(String content, int offset) {
        return content != null && content.indexOf('\f') >= 0 ? pageAtOffset(content, offset) : null;
    }

    private int pageAtOffset(String content, int offset) {
        int page = 1;
        int end = Math.min(offset, content == null ? 0 : content.length());
        for (int i = 0; i < end; i++) {
            if (content.charAt(i) == '\f') {
                page++;
            }
        }
        return page;
    }

    private Integer pageForHeading(String content, int offset, String heading, Map<String, Integer> tocPages) {
        Integer tocPage = tocPages.get(normalizeHeadingKey(heading));
        return tocPage != null ? tocPage : pageAtOffsetIfPaged(content, offset);
    }

    private int paragraphIndexAtOffset(String content, int offset) {
        if (content == null || content.isBlank()) {
            return 1;
        }
        int paragraph = 0;
        boolean inParagraph = false;
        int end = Math.min(offset, content.length());
        for (String line : content.substring(0, end).split("\\R", -1)) {
            if (line.trim().isBlank()) {
                inParagraph = false;
            } else if (!inParagraph) {
                paragraph++;
                inParagraph = true;
            }
        }
        return Math.max(1, paragraph);
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseInt(String value, int fallback) {
        Integer parsed = parseInt(value);
        return parsed == null ? fallback : parsed;
    }

    private String normalizeHeadingKey(String title) {
        return cleanHeading(title).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、（）【】《》“”‘’]+", "");
    }

    private record HeadingCandidate(String title, int level, int start, String sourceSignal,
                                    Integer pageNumber, double confidence) {}

    private record LineInfo(String text, int start, int end) {}

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
        private int paragraphStart;
        private int paragraphEnd;
        private String pageRange;
        private String sourceSignal;
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
        public int getParagraphStart() { return paragraphStart; }
        public void setParagraphStart(int paragraphStart) { this.paragraphStart = paragraphStart; }
        public int getParagraphEnd() { return paragraphEnd; }
        public void setParagraphEnd(int paragraphEnd) { this.paragraphEnd = paragraphEnd; }
        public String getPageRange() { return pageRange; }
        public void setPageRange(String pageRange) { this.pageRange = pageRange; }
        public String getSourceSignal() { return sourceSignal; }
        public void setSourceSignal(String sourceSignal) { this.sourceSignal = sourceSignal; }
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
