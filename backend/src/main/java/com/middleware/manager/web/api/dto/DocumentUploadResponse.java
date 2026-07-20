package com.middleware.manager.web.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class DocumentUploadResponse {
    private Long documentId;
    private String content;
    private String title;
    private List<String> images;
    private String storedFileName;
    private String originalFileName;

    /** 转换模式：返回 Markdown 内容供编辑器使用 */
    public DocumentUploadResponse(String content, String title, List<String> images) {
        this.content = content;
        this.title = title;
        this.images = images;
    }

    /** 非转换模式：返回文档 ID 供预览使用 */
    public DocumentUploadResponse(Long documentId, String title, String storedFileName, String originalFileName) {
        this.documentId = documentId;
        this.title = title;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
    }

    /** 完整构造：转换结果 + 文件引用 */
    public DocumentUploadResponse(String content, String title, List<String> images,
                                  String storedFileName, String originalFileName) {
        this.content = content;
        this.title = title;
        this.images = images;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
    }
}
