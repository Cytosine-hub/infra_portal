package com.middleware.manager.knowledge.splitter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构感知切片器。
 * <p>解析层（DocumentLoader）已把 PDF / Word / Excel / Markdown 统一成结构化 Markdown，
 * 本类负责把标题层级重建成 sectionPath 并让**每个**切片都带上，否则解析层的结构化成果
 * 会在切片环节被丢弃——这正是改造前的问题：大段落拆分后只有首个子块保留标题。
 * <p>三条切分规则，优先级从高到低：
 * <ol>
 *   <li>按 Markdown 标题分节，节内正文剥离标题行（标题信息已由 sectionPath 承载）</li>
 *   <li>Markdown 表格视为原子块；整表放不下时按行拆分，<b>每片重复表头</b>，
 *       避免参数名与其取值落到不同切片</li>
 *   <li>其余正文按行贪心合并到上限，相邻子块间保留 overlap</li>
 * </ol>
 */
@Component
public class TextSplitter {

    private static final int DEFAULT_MAX_CHUNK_SIZE = 900;
    private static final int DEFAULT_OVERLAP = 150;
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final String PATH_SEPARATOR = " / ";

    private final int maxChunkSize;
    private final int overlap;

    public TextSplitter() {
        this(DEFAULT_MAX_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public TextSplitter(int maxChunkSize) {
        this(maxChunkSize, DEFAULT_OVERLAP);
    }

    public TextSplitter(int maxChunkSize, int overlap) {
        this.maxChunkSize = maxChunkSize;
        this.overlap = Math.max(0, Math.min(overlap, Math.max(0, maxChunkSize - 1)));
    }

    public List<TextChunk> split(String text, String sourceTitle) {
        List<TextChunk> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        int[] index = {0};
        for (Section section : parseSections(text)) {
            String body = section.body().trim();
            if (body.isEmpty()) {
                continue;
            }
            for (String part : splitSectionBody(body)) {
                result.add(chunk(section.path(), part, sourceTitle, index));
            }
        }
        return result;
    }

    // ---------- 分节 ----------

    /** 按 Markdown 标题切成若干节，并沿标题栈维护每节的 sectionPath。 */
    private List<Section> parseSections(String text) {
        List<Section> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        String currentPath = "";

        for (String line : text.split("\n", -1)) {
            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (!matcher.matches()) {
                body.append(line).append("\n");
                continue;
            }
            if (body.length() > 0) {
                sections.add(new Section(currentPath, body.toString()));
                body.setLength(0);
            }
            int level = matcher.group(1).length();
            String title = matcher.group(2).trim();
            // 同级或更高级标题出现时回退标题栈，避免路径无限累积
            while (headingStack.size() >= level) {
                headingStack.remove(headingStack.size() - 1);
            }
            while (headingStack.size() < level - 1) {
                headingStack.add("");
            }
            headingStack.add(title);
            currentPath = String.join(PATH_SEPARATOR,
                    headingStack.stream().filter(s -> !s.isEmpty()).toList());
        }
        if (body.length() > 0) {
            sections.add(new Section(currentPath, body.toString()));
        }
        return sections;
    }

    // ---------- 节内切分 ----------

    private List<String> splitSectionBody(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (Block block : parseBlocks(body)) {
            if (block.isTable()) {
                flush(parts, current);
                parts.addAll(splitTable(block.text()));
                continue;
            }
            for (String line : block.text().split("\n")) {
                appendLine(parts, current, line);
            }
        }
        flush(parts, current);
        return parts;
    }

    /** 把节内正文切成「表格块」与「普通文本块」，表格作为原子单元处理。 */
    private List<Block> parseBlocks(String body) {
        List<Block> blocks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean inTable = false;

        for (String line : body.split("\n")) {
            boolean tableLine = line.trim().startsWith("|");
            if (tableLine != inTable && buffer.length() > 0) {
                blocks.add(new Block(buffer.toString(), inTable));
                buffer.setLength(0);
            }
            inTable = tableLine;
            buffer.append(line).append("\n");
        }
        if (buffer.length() > 0) {
            blocks.add(new Block(buffer.toString(), inTable));
        }
        return blocks;
    }

    /** 表格按行拆分，每片重复表头与分隔行，保证参数名与取值不会失去列含义。 */
    private List<String> splitTable(String table) {
        List<String> rows = new ArrayList<>();
        for (String line : table.split("\n")) {
            if (!line.trim().isEmpty()) {
                rows.add(line);
            }
        }
        List<String> parts = new ArrayList<>();
        if (rows.isEmpty()) {
            return parts;
        }
        if (table.length() <= maxChunkSize) {
            parts.add(String.join("\n", rows));
            return parts;
        }

        int headerRows = rows.size() > 1 && rows.get(1).contains("---") ? 2 : 1;
        String header = String.join("\n", rows.subList(0, Math.min(headerRows, rows.size())));
        StringBuilder current = new StringBuilder(header);
        for (int i = headerRows; i < rows.size(); i++) {
            String row = rows.get(i);
            if (current.length() + row.length() + 1 > maxChunkSize && current.length() > header.length()) {
                parts.add(current.toString());
                current = new StringBuilder(header);
            }
            current.append("\n").append(row);
        }
        if (current.length() > header.length()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private void appendLine(List<String> parts, StringBuilder current, String line) {
        if (line.length() > maxChunkSize) {
            flush(parts, current);
            for (int i = 0; i < line.length(); i += maxChunkSize) {
                parts.add(line.substring(i, Math.min(i + maxChunkSize, line.length())));
            }
            return;
        }
        if (current.length() + line.length() + 1 > maxChunkSize && current.length() > 0) {
            String carry = tailOverlap(current.toString());
            parts.add(current.toString());
            current.setLength(0);
            current.append(carry);
        }
        if (current.length() > 0) {
            current.append("\n");
        }
        current.append(line);
    }

    /** 取上一子块末尾的若干字符作为下一子块的开头，按行边界对齐，避免切断处语义割裂。 */
    private String tailOverlap(String text) {
        if (overlap <= 0 || text.length() <= overlap) {
            return "";
        }
        String tail = text.substring(text.length() - overlap);
        int newline = tail.indexOf('\n');
        return newline >= 0 && newline + 1 < tail.length() ? tail.substring(newline + 1) : tail;
    }

    private void flush(List<String> parts, StringBuilder current) {
        if (current.length() > 0) {
            parts.add(current.toString());
            current.setLength(0);
        }
    }

    // ---------- 组装 ----------

    private TextChunk chunk(String sectionPath, String body, String sourceTitle, int[] index) {
        TextChunk chunk = new TextChunk();
        // 面包屑前缀进正文：让「MySQL 主从延迟」这类查询能同时命中标题路径与正文
        chunk.setContent(sectionPath.isEmpty() ? body.trim() : sectionPath + "\n\n" + body.trim());
        chunk.setSectionPath(sectionPath);
        chunk.setSourceTitle(sourceTitle);
        chunk.setChunkIndex(index[0]++);
        return chunk;
    }

    private record Section(String path, String body) {
    }

    private record Block(String text, boolean isTable) {
    }

    public static class TextChunk {
        private String content;
        private String sourceTitle;
        private String sectionPath = "";
        private int chunkIndex;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSourceTitle() {
            return sourceTitle;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }

        public String getSectionPath() {
            return sectionPath;
        }

        public void setSectionPath(String sectionPath) {
            this.sectionPath = sectionPath == null ? "" : sectionPath;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
    }
}
