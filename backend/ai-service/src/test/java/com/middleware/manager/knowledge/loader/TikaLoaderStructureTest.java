package com.middleware.manager.knowledge.loader;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 解析层结构保真测试：验证 PDF / Word / Excel 三类格式都能产出结构化 Markdown，
 * 使下游 TextSplitter 的标题切分策略可以生效。
 */
class TikaLoaderStructureTest {

    private final TikaLoader loader = new TikaLoader();

    @Nested
    @DisplayName("Excel 结构化解析")
    class ExcelStructure {

        @Test
        @DisplayName("TC-LOADER-001 xlsx 参数表应转成带表头分隔行的 Markdown 表格")
        void xlsxBecomesMarkdownTable() throws Exception {
            byte[] bytes = buildWorkbook(new XSSFWorkbook(), "MySQL参数",
                    new String[][]{
                            {"参数名", "默认值", "建议值", "说明"},
                            {"innodb_buffer_pool_size", "128M", "物理内存70%", "缓冲池大小"}
                    });

            String content = loader.load(new ByteArrayInputStream(bytes), "params.xlsx");

            assertThat(content).contains("| 参数名 | 默认值 | 建议值 | 说明 |");
            assertThat(content).contains("| --- | --- | --- | --- |");
            assertThat(content).contains("| innodb_buffer_pool_size | 128M | 物理内存70% | 缓冲池大小 |");
        }

        @Test
        @DisplayName("TC-LOADER-002 xlsx 每个 sheet 应产出独立的 Markdown 标题")
        void xlsxSheetsBecomeHeadings() throws Exception {
            Workbook workbook = new XSSFWorkbook();
            appendSheet(workbook, "参数标准", new String[][]{{"参数名", "值"}, {"max_connections", "1000"}});
            appendSheet(workbook, "监控标准", new String[][]{{"指标", "阈值"}, {"CPU使用率", "80%"}});
            byte[] bytes = toBytes(workbook);

            String content = loader.load(new ByteArrayInputStream(bytes), "standards.xlsx");

            assertThat(content).contains("# 参数标准");
            assertThat(content).contains("# 监控标准");
            assertThat(content).contains("max_connections");
            assertThat(content).contains("CPU使用率");
        }

        @Test
        @DisplayName("TC-LOADER-003 老版 xls 也应走结构化解析而非扁平文本")
        void legacyXlsIsAlsoStructured() throws Exception {
            byte[] bytes = buildWorkbook(new HSSFWorkbook(), "Redis参数",
                    new String[][]{{"参数名", "建议值"}, {"maxmemory-policy", "allkeys-lru"}});

            String content = loader.load(new ByteArrayInputStream(bytes), "redis.xls");

            assertThat(content).contains("# Redis参数");
            assertThat(content).contains("| maxmemory-policy | allkeys-lru |");
        }

        @Test
        @DisplayName("TC-LOADER-004 xlsx 全空行应被跳过，不产生空的表格行")
        void xlsxSkipsBlankRows() throws Exception {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("参数");
            writeRow(sheet.createRow(0), new String[]{"参数名", "值"});
            sheet.createRow(1);
            writeRow(sheet.createRow(2), new String[]{"timeout", "30"});
            byte[] bytes = toBytes(workbook);

            String content = loader.load(new ByteArrayInputStream(bytes), "sparse.xlsx");

            assertThat(content).contains("| timeout | 30 |");
            assertThat(content).doesNotContain("|  |  |");
        }
    }

    @Nested
    @DisplayName("Word 结构化解析")
    class WordStructure {

        @Test
        @DisplayName("TC-LOADER-005 docx 表格应转成 Markdown 表格并保留表头分隔行")
        void docxTableBecomesMarkdownTable() throws Exception {
            byte[] bytes;
            try (XWPFDocument document = new XWPFDocument();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                XWPFParagraph heading = document.createParagraph();
                heading.setStyle("Heading1");
                heading.createRun().setText("参数配置");

                XWPFTable table = document.createTable(2, 2);
                XWPFTableRow header = table.getRow(0);
                header.getCell(0).setText("参数名");
                header.getCell(1).setText("建议值");
                XWPFTableRow body = table.getRow(1);
                body.getCell(0).setText("wal_keep_segments");
                body.getCell(1).setText("64");

                document.write(output);
                bytes = output.toByteArray();
            }

            String content = loader.load(new ByteArrayInputStream(bytes), "pg.docx");

            assertThat(content).contains("# 参数配置");
            assertThat(content).contains("| 参数名 | 建议值 |");
            assertThat(content).contains("| --- | --- |");
            assertThat(content).contains("| wal_keep_segments | 64 |");
        }

        @Test
        @DisplayName("TC-LOADER-006 无法结构化解析的 doc 应回退到 Tika 而不是抛异常")
        void malformedDocFallsBackGracefully() throws Exception {
            byte[] bytes = "not a real word document".getBytes();

            String content = loader.load(new ByteArrayInputStream(bytes), "legacy.doc");

            assertThat(content).isNotNull();
        }
    }

    @Nested
    @DisplayName("PDF 结构化解析")
    class PdfStructure {

        @Test
        @DisplayName("TC-LOADER-007 PDF 书签标题应回填进正文成为 Markdown 标题")
        void pdfBookmarksAreBackfilledIntoBody() throws Exception {
            byte[] bytes = buildPdfWithBookmark("Cluster Management",
                    "This chapter describes cluster configuration.");

            String content = loader.load(new ByteArrayInputStream(bytes), "cluster.pdf");

            assertThat(content).contains("# Cluster Management");
            assertThat(content).contains("This chapter describes cluster configuration.");
            assertThat(content.indexOf("# Cluster Management"))
                    .isLessThan(content.indexOf("This chapter describes cluster configuration."));
        }

        @Test
        @DisplayName("TC-LOADER-012 同页多个书签应分别回填到各自位置，而非堆叠在页首")
        void multipleBookmarksOnSamePageAreBackfilledInPlace() throws Exception {
            byte[] bytes = buildPdfWithBookmarks(
                    "Installation intro text. Configuration detail text.",
                    new String[]{"Installation", "Configuration"});

            String content = loader.load(new ByteArrayInputStream(bytes), "guide.pdf");

            int installHeading = content.indexOf("# Installation");
            int introText = content.indexOf("intro text");
            int configHeading = content.indexOf("# Configuration");

            assertThat(installHeading).isGreaterThanOrEqualTo(0);
            assertThat(configHeading).isGreaterThanOrEqualTo(0);
            // 第二个标题必须落在第一段正文之后，说明是就地回填而不是全部堆到页首
            assertThat(configHeading).isGreaterThan(introText);
        }

        @Test
        @DisplayName("TC-LOADER-013 正文标题被逐字拆开时仍应匹配并回填（PDF 抽取常见形态）")
        void backfillsWhenBodyTitleHasScatteredWhitespace() throws Exception {
            byte[] bytes = buildPdfWithBookmarks(
                    "C l u s t e r  M a n a g e m e n t  followed by body.",
                    new String[]{"Cluster Management"});

            String content = loader.load(new ByteArrayInputStream(bytes), "spaced.pdf");

            assertThat(content).contains("# Cluster Management");
            assertThat(content).contains("followed by body.");
            // 归一化匹配成功时原始的散字形态应被改写掉，而不是与标题重复共存
            assertThat(content).doesNotContain("C l u s t e r");
        }

        @Test
        @DisplayName("TC-LOADER-008 无书签的 PDF 应正常返回正文而不报错")
        void pdfWithoutBookmarksStillReturnsBody() throws Exception {
            byte[] bytes;
            try (PDDocument document = new PDDocument();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                PDPage page = new PDPage();
                document.addPage(page);
                writeText(document, page, "Body without bookmarks");
                document.save(output);
                bytes = output.toByteArray();
            }

            String content = loader.load(new ByteArrayInputStream(bytes), "plain.pdf");

            assertThat(content).contains("Body without bookmarks");
        }
    }

    // ---------- helpers ----------

    private static byte[] buildWorkbook(Workbook workbook, String sheetName, String[][] rows) throws Exception {
        appendSheet(workbook, sheetName, rows);
        return toBytes(workbook);
    }

    private static void appendSheet(Workbook workbook, String sheetName, String[][] rows) {
        Sheet sheet = workbook.createSheet(sheetName);
        for (int r = 0; r < rows.length; r++) {
            writeRow(sheet.createRow(r), rows[r]);
        }
    }

    private static void writeRow(Row row, String[] values) {
        for (int c = 0; c < values.length; c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(values[c]);
        }
    }

    private static byte[] toBytes(Workbook workbook) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            workbook.close();
            return output.toByteArray();
        }
    }

    private static byte[] buildPdfWithBookmark(String bookmarkTitle, String bodyText) throws Exception {
        return buildPdfWithBookmarks(bodyText, new String[]{bookmarkTitle});
    }

    private static byte[] buildPdfWithBookmarks(String bodyText, String[] bookmarkTitles) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            writeText(document, page, bodyText);

            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            for (String title : bookmarkTitles) {
                PDOutlineItem item = new PDOutlineItem();
                item.setTitle(title);
                PDPageXYZDestination destination = new PDPageXYZDestination();
                destination.setPage(page);
                item.setDestination(destination);
                outline.addLast(item);
            }
            outline.openNode();

            document.save(output);
            return output.toByteArray();
        }
    }

    /** PDFBox 2.x 的 Standard14 字体无法编码中文，PDF 正文测试统一用 ASCII 文本。 */
    private static void writeText(PDDocument document, PDPage page, String text) throws Exception {
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 12);
            stream.newLineAtOffset(50, 700);
            stream.showText(text);
            stream.endText();
        }
    }
}
