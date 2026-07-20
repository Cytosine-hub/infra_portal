package com.middleware.manager.knowledge.loader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
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
import java.util.Arrays;
import java.util.HashSet;
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
        String text = loadWithTika(bytes);
        if (lower.endsWith(".pdf")) {
            String outline = extractPdfOutline(bytes);
            if (!outline.isBlank()) {
                return outline + "\n\n" + text;
            }
        }
        return text;
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
            log.debug("DOCX structured parse failed, falling back to Tika: {}", e.getMessage());
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
            content.append("\n")
                    .append("#".repeat(headingLevel))
                    .append(" ")
                    .append(text.trim())
                    .append("\n\n");
        } else {
            content.append(text.trim()).append("\n\n");
        }
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
            content.append("|");
            int columns = Math.max(1, row.getTableCells().size());
            for (XWPFTableCell cell : row.getTableCells()) {
                content.append(" ").append(cell.getText().replaceAll("\\s+", " ").trim()).append(" |");
            }
            content.append("\n");
            if (header) {
                content.append("|");
                for (int i = 0; i < columns; i++) {
                    content.append(" --- |");
                }
                content.append("\n");
                header = false;
            }
        }
        content.append("\n");
    }

    private String extractPdfOutline(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline == null || outline.getFirstChild() == null) {
                return "";
            }
            StringBuilder content = new StringBuilder("目录\n");
            appendPdfOutlineItems(content, outline.getFirstChild(), document, 1);
            return content.toString().trim();
        } catch (Exception e) {
            log.debug("PDF outline parse failed, using Tika text only: {}", e.getMessage());
            return "";
        }
    }

    private void appendPdfOutlineItems(StringBuilder content, PDOutlineItem item, PDDocument document, int level) throws Exception {
        PDOutlineItem current = item;
        while (current != null) {
            String title = current.getTitle();
            int pageNumber = resolvePdfPageNumber(current, document);
            if (title != null && !title.isBlank()) {
                content.append("  ".repeat(Math.max(0, level - 1))).append(title.trim());
                if (pageNumber > 0) {
                    content.append(" .... ").append(pageNumber);
                }
                content.append("\n");
            }
            if (current.getFirstChild() != null) {
                appendPdfOutlineItems(content, current.getFirstChild(), document, level + 1);
            }
            current = current.getNextSibling();
        }
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
