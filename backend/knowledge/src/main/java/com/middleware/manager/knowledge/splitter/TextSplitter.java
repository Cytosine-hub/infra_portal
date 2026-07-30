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

    /**
     * 中文最坏情况约 1 字 1 token；再留 15% 余量给面包屑前缀与分词波动。
     * 按 token 上限反推字符预算，而不是拍一个固定值——固定值要么撑爆模型上下文
     * （KBV-001），要么为了迁就小模型把预算砍得连参数表都装不下。
     */
    private static final double CHARS_PER_TOKEN = 0.85;

    /** 单个切片的字符上限不超过这个值，避免大上下文模型下切片过大稀释语义。 */
    private static final int MAX_PRACTICAL_CHUNK_SIZE = 1600;

    /** bge-large 的 512 token 是当前默认 embedding 模型的上限。 */
    private static final int DEFAULT_TOKEN_LIMIT = 512;
    private static final int DEFAULT_MAX_CHUNK_SIZE = budgetForTokenLimit(DEFAULT_TOKEN_LIMIT);
    private static final int DEFAULT_OVERLAP = DEFAULT_MAX_CHUNK_SIZE / 6;

    /**
     * 由 embedding 模型的 token 上限推导安全的切片字符预算。
     * <p>换模型时只需改配置里的 token 上限：bge-large 512、bge-m3 8192。
     */
    public static int budgetForTokenLimit(int tokenLimit) {
        if (tokenLimit <= 0) {
            throw new IllegalArgumentException("embedding token 上限必须为正数，实际: " + tokenLimit);
        }
        return Math.min(MAX_PRACTICAL_CHUNK_SIZE, (int) Math.floor(tokenLimit * CHARS_PER_TOKEN));
    }
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern FENCE_PATTERN = Pattern.compile("^\\s*(```|~~~)");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|[\\s:|-]*-[\\s:|-]*\\|$");
    private static final String PATH_SEPARATOR = " / ";

    private final int maxChunkSize;
    private final int overlap;

    public TextSplitter() {
        this(DEFAULT_MAX_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * Spring 装配入口：切片预算跟随 embedding 模型的 token 上限。
     * bge-large 填 512，换 bge-m3 改成 8192 即可，无需改代码。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public TextSplitter(@org.springframework.beans.factory.annotation.Value(
            "${app.embedding.max-tokens:512}") Integer embeddingMaxTokens) {
        this(budgetForTokenLimit(embeddingMaxTokens),
                budgetForTokenLimit(embeddingMaxTokens) / 6);
    }

    public TextSplitter(int maxChunkSize) {
        this(maxChunkSize, DEFAULT_OVERLAP);
    }

    public TextSplitter(int maxChunkSize, int overlap) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize 必须为正数，实际: " + maxChunkSize);
        }
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
            // 面包屑前缀占用切片预算，必须先扣除，否则拼接后会超出 maxChunkSize
            String path = section.path();
            int prefixLength = path.isEmpty() ? 0 : path.length() + 2;
            if (prefixLength >= maxChunkSize) {
                path = "";
                prefixLength = 0;
            }
            int budget = maxChunkSize - prefixLength;
            for (String part : splitSectionBody(body, budget)) {
                result.add(chunk(path, part, sourceTitle, index));
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

        boolean inFence = false;
        for (String line : text.split("\n", -1)) {
            // 代码围栏内的 # 是命令注释，不是标题——运维文档里 ```bash 块极常见
            if (FENCE_PATTERN.matcher(line).find()) {
                inFence = !inFence;
                body.append(line).append("\n");
                continue;
            }
            Matcher matcher = inFence ? null : HEADING_PATTERN.matcher(line);
            if (matcher == null || !matcher.matches()) {
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

    private List<String> splitSectionBody(String body, int budget) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (Block block : parseBlocks(body)) {
            if (block.isTable()) {
                flush(parts, current);
                parts.addAll(splitTable(block.text(), budget));
                continue;
            }
            for (String line : block.text().split("\n")) {
                appendLine(parts, current, line, budget);
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
    private List<String> splitTable(String table, int budget) {
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
        if (table.trim().length() <= budget) {
            parts.add(String.join("\n", rows));
            return parts;
        }

        // 分隔行形如 |---|---| 或紧凑的 |:-:|:-:|，两种都要认出来
        int headerRows = rows.size() > 1 && TABLE_SEPARATOR.matcher(rows.get(1).trim()).matches() ? 2 : 1;
        String header = String.join("\n", rows.subList(0, Math.min(headerRows, rows.size())));

        // 表头自身就超预算时无法逐片重复，退化为逐行切分，至少不丢内容
        if (header.length() >= budget) {
            StringBuilder fallback = new StringBuilder();
            for (String row : rows) {
                appendLine(parts, fallback, row, budget);
            }
            flush(parts, fallback);
            return parts;
        }

        StringBuilder current = new StringBuilder(header);
        for (int i = headerRows; i < rows.size(); i++) {
            String row = rows.get(i);
            if (current.length() + row.length() + 1 > budget && current.length() > header.length()) {
                parts.add(current.toString());
                current = new StringBuilder(header);
            }
            if (current.length() + row.length() + 1 > budget) {
                // 单行宽于预算：独立硬切，避免产出超限切片
                parts.add(current.toString());
                current = new StringBuilder(header);
                hardSplit(parts, row, budget);
                continue;
            }
            current.append("\n").append(row);
        }
        if (current.length() > header.length()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private void appendLine(List<String> parts, StringBuilder current, String line, int budget) {
        if (line.length() > budget) {
            flush(parts, current);
            hardSplit(parts, line, budget);
            return;
        }
        if (current.length() + line.length() + 1 > budget && current.length() > 0) {
            String carry = tailOverlap(current.toString(), budget - line.length() - 1);
            parts.add(current.toString());
            current.setLength(0);
            current.append(carry);
        }
        if (current.length() > 0) {
            current.append("\n");
        }
        current.append(line);
    }

    private void hardSplit(List<String> parts, String line, int budget) {
        for (int i = 0; i < line.length(); i += budget) {
            parts.add(line.substring(i, Math.min(i + budget, line.length())));
        }
    }

    /**
     * 取上一子块末尾的若干字符作为下一子块的开头，按行边界对齐，避免切断处语义割裂。
     * carry 长度受 limit 约束，保证后续追加不会突破预算。
     */
    private String tailOverlap(String text, int limit) {
        int window = Math.min(overlap, Math.max(0, limit));
        if (window <= 0 || text.length() <= window) {
            return "";
        }
        String tail = text.substring(text.length() - window);
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
