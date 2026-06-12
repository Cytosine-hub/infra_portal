package com.middleware.manager.wiki.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentOutlineExtractorTest {

    private final DocumentTypeClassifier classifier = new DocumentTypeClassifier();
    private final DocumentOutlineExtractor extractor = new DocumentOutlineExtractor();

    @Test
    void extractsMarkdownSectionsByHeadingStack() {
        String content = """
                # 产品配置

                总体说明。

                ## 连接池参数

                | 参数 | 默认值 |
                | --- | --- |
                | max | 100 |

                ## 生效方式

                重启服务后生效。
                """;

        DocumentTypeClassifier.Classification classification = classifier.classify("config.md", content);
        DocumentOutlineExtractor.DocumentOutline outline =
                extractor.extract("config.md", content, "中间件", "BES", classification);

        assertThat(outline.getFormat()).isEqualTo("MARKDOWN");
        assertThat(outline.getSections()).hasSize(3);
        assertThat(outline.getSections().get(1).getPath()).isEqualTo("产品配置/连接池参数");
        assertThat(outline.getSections().get(1).getBlocks()).contains("table");
        assertThat(outline.getSections().get(1).isRequired()).isTrue();
    }

    @Test
    void extractsNumberedSectionsFromTextLikePdfOrWord() {
        String content = """
                1 配置说明
                这里描述配置文件。

                1.1 最大连接数
                参数 maxConnections 默认值为 100。

                2 故障处理
                如果连接失败，先检查日志。
                """;

        DocumentTypeClassifier.Classification classification = classifier.classify("manual.pdf", content);
        DocumentOutlineExtractor.DocumentOutline outline =
                extractor.extract("manual.pdf", content, "中间件", "BES", classification);

        assertThat(outline.getFormat()).isEqualTo("PDF");
        assertThat(outline.getSections()).hasSize(3);
        assertThat(outline.getSections().get(1).getPath()).isEqualTo("配置说明/最大连接数");
        assertThat(outline.getSections().get(1).getSectionType()).isEqualTo("CONFIG_ITEM");
    }

    @Test
    void extractsMarkdownSetextHeadings() {
        String content = """
                配置说明
                ========

                总体配置说明。

                连接池参数
                --------

                参数 maxConnections 默认值为 100。
                """;

        DocumentTypeClassifier.Classification classification = classifier.classify("config.md", content);
        DocumentOutlineExtractor.DocumentOutline outline =
                extractor.extract("config.md", content, "中间件", "BES", classification);

        assertThat(outline.getSections()).hasSize(2);
        assertThat(outline.getSections().get(0).getPath()).isEqualTo("配置说明");
        assertThat(outline.getSections().get(0).getSourceSignal()).isEqualTo("markdown-setext");
        assertThat(outline.getSections().get(1).getPath()).isEqualTo("配置说明/连接池参数");
    }

    @Test
    void mapsTextHeadingsToTocPageRanges() {
        String content = """
                目录
                1 配置说明 .... 3
                1.1 最大连接数 .... 4
                \f
                第 3 页
                1 配置说明
                这里描述配置文件。
                \f
                第 4 页
                1.1 最大连接数
                参数 maxConnections 默认值为 100。
                """;

        DocumentTypeClassifier.Classification classification = classifier.classify("manual.pdf", content);
        DocumentOutlineExtractor.DocumentOutline outline =
                extractor.extract("manual.pdf", content, "中间件", "BES", classification);

        assertThat(outline.getSections()).hasSize(2);
        assertThat(outline.getSections().get(0).getPageRange()).isEqualTo("3");
        assertThat(outline.getSections().get(1).getPageRange()).isEqualTo("4");
        assertThat(outline.getSections().get(1).getSourceSignal()).isEqualTo("numbered-heading");
        assertThat(outline.getSections().get(1).getParagraphStart()).isGreaterThan(0);
    }

    @Test
    void handlesPdfTocLinesWithFullWidthSpaces() {
        String content = """
                目录
                8.4.1　 配置httpd.conf文件 .... 140
                8.4.2　 配置mod_jk.conf文件 .... 141
                \f
                8.4.1　 配置httpd.conf文件
                Listen 80
                ServerName localhost
                \f
                8.4.2　 配置mod_jk.conf文件
                LoadModule jk_module modules/mod_jk.so
                """;

        DocumentTypeClassifier.Classification classification = classifier.classify("manual.pdf", content);
        DocumentOutlineExtractor.DocumentOutline outline =
                extractor.extract("manual.pdf", content, "中间件", "TongWeb", classification);

        assertThat(outline.getSections()).hasSize(2);
        assertThat(outline.getSections().get(0).getPath()).isEqualTo("配置httpd.conf文件");
        assertThat(outline.getSections().get(0).getPageRange()).isEqualTo("140");
        assertThat(outline.getSections().get(0).getSourceSignal()).isEqualTo("numbered-heading");
        assertThat(outline.getSections().get(1).getPath()).isEqualTo("配置mod_jk.conf文件");
        assertThat(outline.getSections().get(1).getPageRange()).isEqualTo("141");
    }
}
