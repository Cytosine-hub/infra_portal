package com.middleware.manager.knowledge.loader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.model.StyleSheet;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class TikaLoader implements DocumentLoader {

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(
            Arrays.asList(".pdf", ".doc", ".docx", ".xls", ".xlsx")
    );
    private static final Pattern HEADING_STYLE_LEVEL = Pattern.compile("(?i).*(?:heading|标题)\\s*([1-6]).*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String PDF_TOC_HEADER = "目录";
    private static final int MAX_HEADING_LEVEL = 6;

    private final AutoDetectParser parser;

    public TikaLoader() {
        this.parser = new AutoDetectParser();
    }

    public TikaLoader(AutoDetectParser parser) {
        this.parser = parser;
    }

    @Override
    public String load(InputStream inputStream, String fileName) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".docx")) {
            String structured = loadDocxWithStructure(bytes);
            if (!structured.isBlank()) {
                return structured;
            }
        }
        if (lower.endsWith(".doc")) {
            String structured = loadDocWithStructure(bytes);
            if (!structured.isBlank()) {
                return structured;
            }
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            String structured = loadWorkbookWithStructure(bytes);
            if (!structured.isBlank()) {
                return structured;
            }
        }
        if (lower.endsWith(".pdf")) {
            String structured = loadPdfWithStructure(bytes);
            if (!structured.isBlank()) {
                return structured;
            }
        }
        return loadWithTika(bytes);
    }

    private String loadWithTika(byte[] bytes) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        parser.parse(new ByteArrayInputStream(bytes), handler, metadata);
        return handler.toString();
    }

    private String loadDocxWithStructure(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder content = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(content, paragraph, document);
                } else if (element instanceof XWPFTable table) {
                    appendTable(content, table);
                }
            }
            return content.toString().trim();
        } catch (Exception e) {
            log.warn("DOCX 结构化解析失败，降级为 Tika 扁平文本，检索质量会下降: {}", e.getMessage());
            return "";
        }
    }

    private void appendParagraph(StringBuilder content, XWPFParagraph paragraph, XWPFDocument document) {
        String text = paragraph.getText();
        if (text == null || text.trim().isBlank()) {
            content.append("\n");
            return;
        }
        int headingLevel = headingLevel(paragraph, document);
        if (headingLevel > 0) {
            content.append("\n").append(markdownHeading(headingLevel, text.trim())).append("\n\n");
        } else {
            content.append(text.trim()).append("\n\n");
        }
    }

    private String markdownHeading(int level, String text) {
        return "#".repeat(Math.min(MAX_HEADING_LEVEL, Math.max(1, level))) + " " + text;
    }

    /** 渲染一行 Markdown 表格；不足 columns 的位置补空，保证各行列数一致。 */
    private void appendMarkdownRow(StringBuilder content, List<String> cells, int columns) {
        content.append("|");
        for (int c = 0; c < columns; c++) {
            content.append(" ").append(c < cells.size() ? cells.get(c) : "").append(" |");
        }
        content.append("\n");
    }

    private void appendMarkdownSeparator(StringBuilder content, int columns) {
        content.append("|").append(" --- |".repeat(columns)).append("\n");
    }

    private String normalizeCell(String text) {
        return text == null ? "" : WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    private int headingLevel(XWPFParagraph paragraph, XWPFDocument document) {
        int level = parseHeadingLevel(paragraph.getStyle());
        if (level > 0) {
            return level;
        }
        XWPFStyles styles = document.getStyles();
        if (styles == null || paragraph.getStyle() == null) {
            return 0;
        }
        XWPFStyle style = styles.getStyle(paragraph.getStyle());
        return style == null ? 0 : parseHeadingLevel(style.getName());
    }

    private int parseHeadingLevel(String styleName) {
        if (styleName == null || styleName.isBlank()) {
            return 0;
        }
        Matcher matcher = HEADING_STYLE_LEVEL.matcher(styleName);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private void appendTable(StringBuilder content, XWPFTable table) {
        boolean header = true;
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(normalizeCell(cell.getText()));
            }
            int columns = Math.max(1, cells.size());
            appendMarkdownRow(content, cells, columns);
            if (header) {
                appendMarkdownSeparator(content, columns);
                header = false;
            }
        }
        content.append("\n");
    }

    /**
     * 老版 Word（.doc）结构化解析：把 Heading/标题 样式的段落转写成 Markdown 标题。
     * Tika 对 .doc 只产出扁平文本，标题层级丢失后下游只能按字符数兜底切片。
     */
    private String loadDocWithStructure(byte[] bytes) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            StyleSheet styleSheet = document.getStyleSheet();
            Range range = document.getRange();
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < range.numParagraphs(); i++) {
                Paragraph paragraph = range.getParagraph(i);
                String text = paragraph.text().replace('\r', '\n').trim();
                if (text.isEmpty()) {
                    continue;
                }
                int headingLevel = docHeadingLevel(styleSheet, paragraph.getStyleIndex());
                if (headingLevel > 0) {
                    content.append("\n").append(markdownHeading(headingLevel, text)).append("\n\n");
                } else {
                    content.append(text).append("\n\n");
                }
            }
            return content.toString().trim();
        } catch (Exception e) {
            log.warn("DOC 结构化解析失败，降级为 Tika 扁平文本，检索质量会下降: {}", e.getMessage());
            return "";
        }
    }

    private int docHeadingLevel(StyleSheet styleSheet, int styleIndex) {
        if (styleSheet == null || styleIndex < 0 || styleIndex >= styleSheet.numStyles()) {
            return 0;
        }
        StyleDescription description = styleSheet.getStyleDescription(styleIndex);
        return description == null ? 0 : parseHeadingLevel(description.getName());
    }

    /**
     * Excel 结构化解析：每个 sheet 产出一个 Markdown 标题 + 一张 Markdown 表格。
     * 参数标准多以 Excel 台账形式维护，扁平文本化会丢失行列关系，导致参数名与其值被切到不同切片。
     */
    private String loadWorkbookWithStructure(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                appendSheet(content, workbook.getSheetAt(i), formatter);
            }
            return content.toString().trim();
        } catch (Exception e) {
            log.warn("Excel 结构化解析失败，降级为 Tika 扁平文本，行列关系会丢失: {}", e.getMessage());
            return "";
        }
    }

    private void appendSheet(StringBuilder content, Sheet sheet, DataFormatter formatter) {
        List<List<String>> rows = new ArrayList<>();
        int columns = 0;
        for (Row row : sheet) {
            List<String> values = readRow(row, formatter);
            if (values.isEmpty()) {
                continue;
            }
            columns = Math.max(columns, values.size());
            rows.add(values);
        }
        if (rows.isEmpty()) {
            return;
        }

        String sheetName = sheet.getSheetName();
        if (sheetName != null && !sheetName.isBlank()) {
            content.append(markdownHeading(1, sheetName.trim())).append("\n\n");
        }
        for (int i = 0; i < rows.size(); i++) {
            appendMarkdownRow(content, rows.get(i), columns);
            if (i == 0) {
                appendMarkdownSeparator(content, columns);
            }
        }
        content.append("\n");
    }

    /** 返回该行的单元格文本；整行为空时返回空列表，以便跳过空行。 */
    private List<String> readRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        boolean hasContent = false;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            String text = cell == null ? "" : normalizeCell(formatter.formatCellValue(cell));
            if (!text.isEmpty()) {
                hasContent = true;
            }
            values.add(text);
        }
        return hasContent ? values : List.of();
    }

    /**
     * PDF 结构化解析：单次打开文档，遍历一次书签树，同时产出目录块与回填了标题的正文。
     * <p>目录块保留「标题 .... 页码」格式，wiki 的 DocumentOutlineExtractor 依赖它做章节页码归属；
     * 正文侧把书签标题就地改写成 Markdown 标题，使下游 TextSplitter 的标题切分策略生效
     * （此前 Tika 输出的是无标题标记的扁平文本流，只能走字符数兜底）。
     */
    private String loadPdfWithStructure(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline == null || outline.getFirstChild() == null) {
                return "";
            }
            List<PdfBookmark> bookmarks = new ArrayList<>();
            collectBookmarks(outline.getFirstChild(), document, 1, bookmarks);
            if (bookmarks.isEmpty()) {
                return "";
            }
            String toc = renderToc(bookmarks);
            String body = renderBodyWithHeadings(document, bookmarks);
            return toc.isBlank() ? body : toc + "\n\n" + body;
        } catch (Exception e) {
            log.warn("PDF 结构化解析失败，降级为 Tika 扁平文本，检索质量会下降: {}", e.getMessage());
            return "";
        }
    }

    private String renderToc(List<PdfBookmark> bookmarks) {
        StringBuilder content = new StringBuilder(PDF_TOC_HEADER).append("\n");
        for (PdfBookmark bookmark : bookmarks) {
            content.append("  ".repeat(Math.max(0, bookmark.level() - 1))).append(bookmark.title());
            if (bookmark.page() > 0) {
                content.append(" .... ").append(bookmark.page());
            }
            content.append("\n");
        }
        return content.toString().trim();
    }

    private String renderBodyWithHeadings(PDDocument document, List<PdfBookmark> bookmarks) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        StringBuilder content = new StringBuilder();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            content.append(injectHeadings(stripper.getText(document), bookmarks, page)).append("\n");
        }
        return content.toString().trim();
    }

    /** 把落在本页的书签标题就地改写成 Markdown 标题；正文中定位不到时，退化为在页首插入。 */
    private String injectHeadings(String pageText, List<PdfBookmark> bookmarks, int page) {
        String result = pageText == null ? "" : pageText;
        StringBuilder prefix = new StringBuilder();
        int searchFrom = 0;
        for (PdfBookmark bookmark : bookmarks) {
            if (bookmark.page() != page) {
                continue;
            }
            String heading = markdownHeading(bookmark.level(), bookmark.title());
            int[] span = locateTitle(result, bookmark.title(), searchFrom);
            if (span == null) {
                prefix.append(heading).append("\n\n");
                continue;
            }
            String replacement = "\n\n" + heading + "\n\n";
            result = result.substring(0, span[0]) + replacement + result.substring(span[1]);
            searchFrom = span[0] + replacement.length();
        }
        return prefix + result;
    }

    /**
     * 在正文中定位标题，返回 [起始, 结束) 下标；找不到返回 null。
     * <p>PDF 文本抽取常在字符间插入空白（中文文档尤其明显，「集群管理」可能抽成「集 群 管 理」），
     * 精确匹配失败后改用「去掉全部空白再匹配」并把下标映射回原文。
     */
    private int[] locateTitle(String text, String title, int fromIndex) {
        if (text == null || title == null || fromIndex >= text.length()) {
            return null;
        }
        int exact = text.indexOf(title, fromIndex);
        if (exact >= 0) {
            return new int[]{exact, exact + title.length()};
        }

        String compactTitle = WHITESPACE.matcher(title).replaceAll("");
        if (compactTitle.isEmpty()) {
            return null;
        }
        StringBuilder compactText = new StringBuilder();
        int[] originalIndex = new int[text.length() - fromIndex + 1];
        for (int i = fromIndex; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                originalIndex[compactText.length()] = i;
                compactText.append(text.charAt(i));
            }
        }
        int hit = compactText.indexOf(compactTitle);
        if (hit < 0) {
            return null;
        }
        return new int[]{originalIndex[hit], originalIndex[hit + compactTitle.length() - 1] + 1};
    }

    private void collectBookmarks(PDOutlineItem item, PDDocument document, int level, List<PdfBookmark> sink)
            throws Exception {
        PDOutlineItem current = item;
        while (current != null) {
            String title = current.getTitle();
            if (title != null && !title.isBlank()) {
                sink.add(new PdfBookmark(title.trim(), resolvePdfPageNumber(current, document), level));
            }
            if (current.getFirstChild() != null) {
                collectBookmarks(current.getFirstChild(), document, level + 1, sink);
            }
            current = current.getNextSibling();
        }
    }

    private record PdfBookmark(String title, int page, int level) {
    }

    private int resolvePdfPageNumber(PDOutlineItem item, PDDocument document) throws Exception {
        PDDestination destination = item.getDestination();
        if (destination == null) {
            PDAction action = item.getAction();
            if (action instanceof PDActionGoTo goTo) {
                destination = goTo.getDestination();
            }
        }
        if (destination instanceof PDPageDestination pageDestination) {
            int pageNumber = pageDestination.retrievePageNumber();
            if (pageNumber >= 0) {
                return pageNumber + 1;
            }
        }
        return -1;
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
