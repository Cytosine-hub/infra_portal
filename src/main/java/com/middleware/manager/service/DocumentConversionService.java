package com.middleware.manager.service;

import com.middleware.manager.config.StorageProperties;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.web.api.dto.DocumentUploadResponse;
import com.middleware.manager.web.api.dto.StandardDocumentRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档转换服务：支持 Word (.doc/.docx) 和 Markdown (.md) 文件上传
 */
@Service
@Slf4j
public class DocumentConversionService {

    private static final String DOCX_EXTENSION = ".docx";
    private static final String DOC_EXTENSION = ".doc";
    private static final String DEFAULT_IMAGE_EXT = ".png";
    private static final Set<String> WORD_EXTENSIONS = new HashSet<>(Arrays.asList(DOC_EXTENSION, DOCX_EXTENSION));
    private static final Set<String> MARKDOWN_EXTENSIONS = new HashSet<>(Arrays.asList(".md", ".markdown"));
    private static final String IMAGE_URL_PREFIX = "/files/images/";
    private static final String IMAGES_SUBDIR = "images";
    private static final String DOCUMENTS_SUBDIR = "documents";
    private static final String EMPTY_CONTENT = "";
    private static final String BULLET_FORMAT = "bullet";
    private static final String HEADING_STYLE_PREFIX_LOWER = "heading";
    private static final String HEADING_STYLE_PREFIX_UPPER = "Heading";
    private static final String HEADING_1_PREFIX = "# ";
    private static final String HEADING_2_PREFIX = "## ";
    private static final int MAX_FILE_SIZE = 20 * 1024 * 1024;
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_LIST_INDENT = 3;
    private static final String INDENT_UNIT = "  ";
    private static final int BODY_CONTENT_HANDLER_UNLIMITED = -1;

    private final Path imageStoragePath;
    private final Path documentsStoragePath;
    private final AutoDetectParser tikaParser;
    private final StandardDocumentService documentService;

    public DocumentConversionService(StorageProperties storageProperties, StandardDocumentService documentService) {
        this.imageStoragePath = Paths.get(storageProperties.getLocation(), IMAGES_SUBDIR).toAbsolutePath().normalize();
        this.documentsStoragePath = Paths.get(storageProperties.getLocation(), DOCUMENTS_SUBDIR).toAbsolutePath().normalize();
        this.documentService = documentService;
        try {
            Files.createDirectories(this.imageStoragePath);
            Files.createDirectories(this.documentsStoragePath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, ErrorMessages.DOCUMENT_CONVERT_FAILED);
        }
        this.tikaParser = new AutoDetectParser();
    }

    /**
     * 转换文档
     * @param file 上传的文件
     * @param convertToMarkdown 是否转换为 Markdown（仅 .docx 有效；.doc 始终保存原始文件；.md 直接读取）
     * @return 转换结果：content, title, images, storedFileName, originalFileName
     */
    public DocumentUploadResponse convert(MultipartFile file, boolean convertToMarkdown) {
        validateFile(file);
        String fileName = file.getOriginalFilename();
        String ext = getExtension(fileName);

        try {
            if (MARKDOWN_EXTENSIONS.contains(ext)) {
                return convertMarkdown(file);
            } else if (DOCX_EXTENSION.equals(ext)) {
                return convertDocx(file, convertToMarkdown);
            } else if (DOC_EXTENSION.equals(ext)) {
                // .doc 格式始终保存原始文件，不支持 Markdown 转换
                return convertDoc(file);
            } else {
                throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.DOCUMENT_FORMAT_NOT_SUPPORTED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档转换失败 fileName={}", fileName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, ErrorMessages.DOCUMENT_CONVERT_FAILED);
        }
    }



    /** Markdown 文件：直接读取 */
    private DocumentUploadResponse convertMarkdown(MultipartFile file) {
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Markdown 文件读取失败 fileName={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, ErrorMessages.DOCUMENT_PARSE_FAILED);
        }
        String title = extractMarkdownTitle(content);
        log.info("Markdown 文件已读取 fileName={}", file.getOriginalFilename());
        return buildResult(content, title, Collections.emptyList());
    }

    /** .docx 转换：使用 POI XWPFDocument 提取格式和图片；convertToMarkdown=false 时保存原始文件 */
    private DocumentUploadResponse convertDocx(MultipartFile file, boolean convertToMarkdown) {
        List<String> images = new ArrayList<>();
        String originalName = file.getOriginalFilename();

        try (InputStream is = file.getInputStream(); XWPFDocument doc = new XWPFDocument(is)) {
            Map<String, String> imageMapping = extractAndSaveImages(doc, images);

            String content;
            String title;
            String storedFileName = null;

            if (convertToMarkdown) {
                StringBuilder sb = new StringBuilder();
                for (IBodyElement element : doc.getBodyElements()) {
                    if (element instanceof XWPFParagraph) {
                        processParagraph((XWPFParagraph) element, sb, imageMapping);
                    } else if (element instanceof XWPFTable) {
                        processTable((XWPFTable) element, sb, imageMapping);
                    }
                }
                content = sb.toString().trim();
                title = extractFirstHeading(content);
            } else {
                // 不转换：保存原始文件，提取纯文本用于参数替换
                storedFileName = saveOriginalFile(file);
                BodyContentHandler handler = new BodyContentHandler(BODY_CONTENT_HANDLER_UNLIMITED);
                Metadata metadata = new Metadata();
                tikaParser.parse(file.getInputStream(), handler, metadata);
                content = handler.toString();
                title = content.length() > MAX_TITLE_LENGTH ? content.substring(0, MAX_TITLE_LENGTH).trim() : content;
            }

            log.info("DOCX 文件已处理 fileName={}, convertToMarkdown={}, images={}", originalName, convertToMarkdown, images.size());
            return new DocumentUploadResponse(content, title, images, storedFileName, originalName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DOCX 文件处理失败 fileName={}", originalName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, ErrorMessages.DOCUMENT_PARSE_FAILED);
        }
    }

    /** .doc 处理：保存原始文件，提取纯文本用于参数替换 */
    private DocumentUploadResponse convertDoc(MultipartFile file) {
        List<String> images = new ArrayList<>();
        String originalName = file.getOriginalFilename();

        // 始终保存原始文件（.doc 格式无法在前端编辑）
        String storedFileName = saveOriginalFile(file);

        BodyContentHandler handler = new BodyContentHandler(BODY_CONTENT_HANDLER_UNLIMITED);
        Metadata metadata = new Metadata();
        try (InputStream is = file.getInputStream()) {
            tikaParser.parse(is, handler, metadata);
        } catch (Exception e) {
            log.error("DOC 文件解析失败 fileName={}", originalName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, ErrorMessages.DOCUMENT_PARSE_FAILED);
        }
        String content = handler.toString();
        String title = content.length() > MAX_TITLE_LENGTH ? content.substring(0, MAX_TITLE_LENGTH).trim() : content;

        log.info("DOC 文件已处理 fileName={}, length={}", originalName, content.length());
        return new DocumentUploadResponse(content, title, images, storedFileName, originalName);
    }

    /** 从 DOCX 提取并保存图片，返回 oldRId → newUrl 映射 */
    private Map<String, String> extractAndSaveImages(XWPFDocument doc, List<String> images) throws IOException {
        Map<String, String> mapping = new HashMap<>();
        for (XWPFPictureData picture : doc.getAllPictures()) {
            String fileName = picture.getFileName();
            String ext = getExtension(fileName);
            if (ext.isEmpty()) ext = DEFAULT_IMAGE_EXT;

            String storedName = UUID.randomUUID() + ext;
            Path target = imageStoragePath.resolve(storedName);
            Files.copy(picture.getPackagePart().getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String url = IMAGE_URL_PREFIX + storedName;
            images.add(url);

            // 建立原始文件名到新 URL 的映射
            mapping.put(fileName, url);
            mapping.put(picture.getPackagePart().getPartName().getName(), url);
        }
        return mapping;
    }

    /** 处理段落：识别标题、粗体、斜体、列表等 */
    private void processParagraph(XWPFParagraph para, StringBuilder sb, Map<String, String> imageMapping) {
        String style = para.getStyle();
        int headingLevel = getHeadingLevel(style);

        // 检查是否为列表项
        String numFmt = para.getNumFmt();
        boolean isBullet = BULLET_FORMAT.equals(numFmt);
        boolean isNumber = numFmt != null && !numFmt.equals(BULLET_FORMAT) && !numFmt.isEmpty();

        // 处理列表缩进
        String indent = "";
        if (isBullet || isNumber) {
            int ilvl = 0;
            if (para.getCTP() != null && para.getCTP().getPPr() != null
                    && para.getCTP().getPPr().getNumPr() != null
                    && para.getCTP().getPPr().getNumPr().getIlvl() != null) {
                ilvl = (int) Math.min(para.getCTP().getPPr().getNumPr().getIlvl().getVal().longValue(), MAX_LIST_INDENT);
            }
            indent = INDENT_UNIT.repeat(ilvl);
        }

        // 处理段落内容
        StringBuilder line = new StringBuilder();
        for (XWPFRun run : para.getRuns()) {
            // 处理图片
            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                String picName = pic.getPictureData().getFileName();
                String url = imageMapping.get(picName);
                if (url != null) {
                    line.append("![](").append(url).append(")");
                }
            }

            String text = run.text();
            if (text == null || text.isEmpty()) continue;

            boolean bold = run.isBold();
            boolean italic = run.isItalic();
            boolean strike = run.isStrikeThrough();

            if (bold && italic) {
                text = "***" + text + "***";
            } else if (bold) {
                text = "**" + text + "**";
            } else if (italic) {
                text = "*" + text + "*";
            }
            if (strike) {
                text = "~~" + text + "~~";
            }
            line.append(text);
        }

        String text = line.toString().trim();
        if (text.isEmpty()) {
            sb.append("\n");
            return;
        }

        // 输出 Markdown
        if (headingLevel > 0 && headingLevel <= 3) {
            sb.append("#".repeat(headingLevel)).append(" ").append(text).append("\n\n");
        } else if (isBullet) {
            sb.append(indent).append("- ").append(text).append("\n");
        } else if (isNumber) {
            sb.append(indent).append("1. ").append(text).append("\n");
        } else {
            sb.append(text).append("\n\n");
        }
    }

    /** 处理表格 */
    private void processTable(XWPFTable table, StringBuilder sb, Map<String, String> imageMapping) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return;

        sb.append("\n");
        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            sb.append("|");
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText().trim().replace("|", "\\|").replace("\n", " ");
                sb.append(" ").append(cellText).append(" |");
            }
            sb.append("\n");

            // 表头分隔行
            if (i == 0) {
                sb.append("|");
                for (int j = 0; j < row.getTableCells().size(); j++) {
                    sb.append("-----|");
                }
                sb.append("\n");
            }
        }
        sb.append("\n");
    }

    /** 从样式名提取标题级别 */
    private int getHeadingLevel(String style) {
        if (style == null) return 0;
        if (style.startsWith(HEADING_STYLE_PREFIX_UPPER) || style.startsWith(HEADING_STYLE_PREFIX_LOWER)) {
            try {
                return Integer.parseInt(style.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** 从 Markdown 内容提取第一个标题 */
    private String extractFirstHeading(String content) {
        for (String line : content.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(HEADING_1_PREFIX)) return trimmed.substring(HEADING_1_PREFIX.length()).trim();
            if (trimmed.startsWith(HEADING_2_PREFIX)) return trimmed.substring(HEADING_2_PREFIX.length()).trim();
        }
        return content.length() > MAX_TITLE_LENGTH ? content.substring(0, MAX_TITLE_LENGTH).trim() : content;
    }

    /** 从 Markdown 内容提取首个 # 标题 */
    private String extractMarkdownTitle(String content) {
        return extractFirstHeading(content);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.DOCUMENT_FILE_REQUIRED);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, ErrorMessages.DOCUMENT_FILE_TOO_LARGE);
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!WORD_EXTENSIONS.contains(ext) && !MARKDOWN_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.DOCUMENT_FORMAT_NOT_SUPPORTED);
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase() : "";
    }

    /** 保存原始文件到 documents 目录，返回存储文件名 */
    private String saveOriginalFile(MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + ext;
        Path target = documentsStoragePath.resolve(storedName);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("保存原始文件失败 fileName={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, ErrorMessages.FILE_UPLOAD_FAILED);
        }
        return storedName;
    }

    private DocumentUploadResponse buildResult(String content, String title, List<String> images) {
        return new DocumentUploadResponse(content, title, images);
    }

    /**
     * 获取存储的原始文档路径，用于直接下载/预览
     * @param storedFileName 存储文件名（UUID.ext 格式）
     * @return 文件路径
     */
    public Path getDocumentPath(String storedFileName) {
        if (!storedFileName.matches("[a-f0-9\\-]+\\.[a-zA-Z0-9]+")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.DOCUMENT_FORMAT_NOT_SUPPORTED);
        }
        Path filePath = documentsStoragePath.resolve(storedFileName).normalize();
        if (!filePath.startsWith(documentsStoragePath)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.DOCUMENT_FORMAT_NOT_SUPPORTED);
        }
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, ErrorMessages.DOCUMENT_NOT_FOUND);
        }
        log.debug("获取文档路径 storedFileName={}", storedFileName);
        return filePath;
    }

    /**
     * 将存储的 Word 文档渲染为 HTML 用于预览
     * @param storedFileName 存储的文件名（UUID.ext 格式）
     * @return HTML 内容
     */
    public String renderAsHtml(String storedFileName) {
        // 路径遍历防护：仅允许 UUID 格式的文件名
        if (!storedFileName.matches("[a-f0-9\\-]+\\.[a-zA-Z0-9]+")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.DOCUMENT_FORMAT_NOT_SUPPORTED);
        }
        Path filePath = documentsStoragePath.resolve(storedFileName).normalize();
        if (!filePath.startsWith(documentsStoragePath)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.DOCUMENT_FORMAT_NOT_SUPPORTED);
        }
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, ErrorMessages.DOCUMENT_NOT_FOUND);
        }

        ToXMLContentHandler handler = new ToXMLContentHandler();
        Metadata metadata = new Metadata();
        try (InputStream is = Files.newInputStream(filePath)) {
            tikaParser.parse(is, handler, metadata);
            String html = handler.toString();
            // 提取 body 内容，避免嵌套完整 HTML 文档
            String bodyOpenTag = "<body>";
            String bodyCloseTag = "</body>";
            int bodyStart = html.indexOf(bodyOpenTag);
            int bodyEnd = html.indexOf(bodyCloseTag);
            if (bodyStart >= 0 && bodyEnd > bodyStart) {
                return html.substring(bodyStart + bodyOpenTag.length(), bodyEnd);
            }
            return html;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档预览渲染失败 storedFileName={}", storedFileName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, ErrorMessages.DOCUMENT_PARSE_FAILED);
        }
    }
}
