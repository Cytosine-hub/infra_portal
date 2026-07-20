package com.middleware.manager.knowledge.loader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TikaLoaderTest {

    private final TikaLoader loader = new TikaLoader();

    @Test
    void convertsDocxHeadingStylesToMarkdownHeadings() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("配置说明");

            XWPFParagraph body = document.createParagraph();
            body.createRun().setText("这里描述配置内容。");

            document.write(output);
            bytes = output.toByteArray();
        }

        String content = loader.load(new ByteArrayInputStream(bytes), "manual.docx");

        assertThat(content).contains("# 配置说明");
        assertThat(content).contains("这里描述配置内容。");
    }

    @Test
    void prependsPdfBookmarksAsTocSignals() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);

            PDOutlineItem item = new PDOutlineItem();
            item.setTitle("配置说明");
            PDPageXYZDestination destination = new PDPageXYZDestination();
            destination.setPage(page);
            item.setDestination(destination);
            outline.addLast(item);
            outline.openNode();
            item.openNode();

            document.save(output);
            bytes = output.toByteArray();
        }

        String content = loader.load(new ByteArrayInputStream(bytes), "manual.pdf");

        assertThat(content).startsWith("目录");
        assertThat(content).contains("配置说明 .... 1");
    }
}
