package com.middleware.manager.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.ReviewRecord;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.repository.ReviewRecordMapper;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.web.api.dto.ReviewResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewService {
    private final ReviewRecordMapper mapper;
    private final StandardDocumentService documentService;
    private final ParameterStandardService parameterStandardService;
    private final PermissionService permissionService;

    public ReviewService(ReviewRecordMapper mapper,
                         StandardDocumentService documentService,
                         ParameterStandardService parameterStandardService,
                         PermissionService permissionService) {
        this.mapper = mapper;
        this.documentService = documentService;
        this.parameterStandardService = parameterStandardService;
        this.permissionService = permissionService;
    }

    public List<ReviewResponse> listReviews(Authentication authentication) {
        boolean isAdmin = permissionService.isAdmin(authentication);
        List<ReviewRecord> records;
        if (isAdmin) {
            records = mapper.findAllByOrderBySubmittedAtDesc();
        } else {
            records = mapper.findBySubmitterUsernameOrderBySubmittedAtDesc(authentication.getName());
        }
        return records.stream().map(ReviewResponse::from).collect(Collectors.toList());
    }

    public ReviewResponse getReviewDetail(Long id) {
        ReviewRecord record = getRecord(id);
        ReviewResponse response = ReviewResponse.from(record);
        response.setDiff(computeDiff(record));
        return response;
    }

    @Transactional
    public ReviewResponse approve(Long id, String reviewer, String comment) {
        ReviewRecord record = getRecord(id);
        if (!"PENDING".equals(record.getStatus())) {
            throw new IllegalStateException("该审核记录不是待审核状态");
        }
        record.setStatus("APPROVED");
        record.setReviewerUsername(reviewer);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewComment(comment);

        if ("PARAMETER_STANDARD".equals(record.getDocumentType())) {
            ParameterStandard ps = parameterStandardService.get(record.getDocumentId());
            ps.setPendingReviewRecordId(null);
            ps.setStatus("PUBLISHED");
            ps.setPublishedAt(LocalDateTime.now());
            if (ps.getPreviousContent() == null) {
                ps.setVersion(VersionManager.firstPublishVersion());
            } else {
                ps.setVersion(VersionManager.toPublishedVersion(ps.getVersion()));
            }
            ps.setPreviousContent(null);
            parameterStandardService.save(ps);
        } else {
            StandardDocument doc = documentService.get(record.getDocumentId());
            doc.setPendingReviewRecordId(null);
            if (doc.getPreviousContent() == null) {
                doc.setVersion(VersionManager.firstPublishVersion());
            } else {
                doc.setVersion(VersionManager.toPublishedVersion(doc.getVersion()));
            }
            doc.setStatus("PUBLISHED");
            doc.setPublishedAt(LocalDateTime.now());
            doc.setPreviousContent(record.getCurrentContent());
            doc.setReviewedAt(LocalDateTime.now());
            doc.setReviewedBy(reviewer);
            doc.setReviewComment(comment);
            documentService.save(doc);
        }

        mapper.update(record);
        return ReviewResponse.from(record);
    }

    @Transactional
    public ReviewResponse reject(Long id, String reviewer, String comment) {
        ReviewRecord record = getRecord(id);
        if (!"PENDING".equals(record.getStatus())) {
            throw new IllegalStateException("该审核记录不是待审核状态");
        }
        record.setStatus("REJECTED");
        record.setReviewerUsername(reviewer);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewComment(comment);

        if ("PARAMETER_STANDARD".equals(record.getDocumentType())) {
            ParameterStandard ps = parameterStandardService.get(record.getDocumentId());
            ps.setPendingReviewRecordId(null);
            ps.setVersion(VersionManager.nextModifyingVersion(ps.getVersion()));
            parameterStandardService.save(ps);
        } else {
            StandardDocument doc = documentService.get(record.getDocumentId());
            doc.setPendingReviewRecordId(null);
            doc.setVersion(VersionManager.nextModifyingVersion(doc.getVersion()));
            doc.setReviewedAt(LocalDateTime.now());
            doc.setReviewedBy(reviewer);
            doc.setReviewComment(comment);
            documentService.save(doc);
        }

        mapper.update(record);
        return ReviewResponse.from(record);
    }

    public String computeDiff(ReviewRecord record) {
        String current = record.getCurrentContent();
        String previous = record.getPreviousContent();
        if (previous == null) {
            return "（首次提交，无历史版本对比）\n\n" + truncate(current, 2000);
        }
        return computeLineDiff(previous, current);
    }

    private ReviewRecord getRecord(Long id) {
        ReviewRecord record = mapper.findById(id);
        if (record == null) {
            throw new IllegalArgumentException("审核记录不存在");
        }
        return record;
    }

    private String computeLineDiff(String oldText, String newText) {
        List<String> oldLines = Arrays.asList(oldText.split("\\n", -1));
        List<String> newLines = Arrays.asList(newText.split("\\n", -1));
        StringBuilder sb = new StringBuilder();
        int maxLen = Math.max(oldLines.size(), newLines.size());
        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < oldLines.size() ? oldLines.get(i) : null;
            String newLine = i < newLines.size() ? newLines.get(i) : null;
            if (oldLine == null) {
                sb.append("+ ").append(newLine).append("\n");
            } else if (newLine == null) {
                sb.append("- ").append(oldLine).append("\n");
            } else if (!oldLine.equals(newLine)) {
                sb.append("- ").append(oldLine).append("\n");
                sb.append("+ ").append(newLine).append("\n");
            } else {
                sb.append("  ").append(oldLine).append("\n");
            }
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n...";
    }
}
