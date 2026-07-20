package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.ReviewRecord;
import com.middleware.manager.domain.ReviewStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ReviewResponse {
    private Long id;
    private Long documentId;
    private String documentTitle;
    private String documentType;
    private String category;
    private String software;
    private String documentVersion;
    private String submitterUsername;
    private String submitterDisplayName;
    private String status;
    private String statusLabel;
    private LocalDateTime submittedAt;
    private String reviewerUsername;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private String diff;
    private String currentContent;

    /** 元数据信息（JSON）：category, software, softwareVersion, code */
    private String metadata;

    /** 当前参数列表（参数标准审核时填充） */
    private List<Map<String, Object>> currentParameters;

    /** 上一版参数列表（参数标准审核且有 previousContent 时填充） */
    private List<Map<String, Object>> previousParameters;

    /** Word 文档的存储文件名（标准文档审核时，若为 Word 文档则填充） */
    private String storedFileName;

    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }

    public static ReviewResponse from(ReviewRecord record) {
        ReviewResponse r = new ReviewResponse();
        r.setId(record.getId());
        r.setDocumentId(record.getDocumentId());
        r.setDocumentTitle(record.getDocumentTitle());
        r.setDocumentType(record.getDocumentType());
        r.setCategory(record.getCategory());
        r.setSoftware(record.getSoftware());
        r.setDocumentVersion(record.getDocumentVersion());
        r.setSubmitterUsername(record.getSubmitterUsername());
        r.setSubmitterDisplayName(record.getSubmitterDisplayName());
        r.setStatus(record.getStatus());
        r.setStatusLabel(ReviewStatus.labelOf(record.getStatus()));
        r.setSubmittedAt(record.getSubmittedAt());
        r.setReviewerUsername(record.getReviewerUsername());
        r.setReviewedAt(record.getReviewedAt());
        r.setReviewComment(record.getReviewComment());
        r.setCurrentContent(record.getCurrentContent());
        return r;
    }
}
