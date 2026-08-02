package com.middleware.manager.knowledge.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.KnowledgeImportSource;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.KnowledgeImportSourceMapper;
import com.middleware.manager.security.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** 从业务表读取已发布正文并导入知识库，客户端只提交不可篡改的来源标识。 */
@Service
@Slf4j
public class KnowledgeContentImportService {

    private static final String STANDARD_DOCUMENT = "STANDARD_DOCUMENT";
    private static final String FORUM_POST = "FORUM_POST";

    private final KnowledgeImportSourceMapper sourceLookup;
    private final KnowledgeService knowledgeService;
    private final PermissionService permissionService;

    public KnowledgeContentImportService(KnowledgeImportSourceMapper sourceLookup,
                                         KnowledgeService knowledgeService,
                                         PermissionService permissionService) {
        this.sourceLookup = sourceLookup;
        this.knowledgeService = knowledgeService;
        this.permissionService = permissionService;
    }

    public KnowledgeService.ImportResult importContent(String sourceType, Long sourceId,
                                                        Authentication authentication) {
        String normalizedType = normalizeType(sourceType);
        KnowledgeImportSource source = loadSource(normalizedType, sourceId);
        requirePermission(authentication, source);
        try {
            KnowledgeService.ImportResult result = knowledgeService.importContent(
                    source.getTitle(), normalizedType, source.getContent(),
                    source.getCategory(), source.getSoftware(), String.valueOf(sourceId));
            log.info("业务内容导入知识库成功 sourceType={} sourceId={} knowledgeSourceId={}",
                    normalizedType, sourceId, result.getSourceId());
            return result;
        } catch (RuntimeException exception) {
            log.warn("业务内容导入知识库失败 sourceType={} sourceId={} reason={}",
                    normalizedType, sourceId, exception.getMessage());
            throw exception;
        }
    }

    private KnowledgeImportSource loadSource(String sourceType, Long sourceId) {
        KnowledgeImportSource source = switch (sourceType) {
            case STANDARD_DOCUMENT -> sourceLookup.findPublishedStandardDocument(sourceId);
            case FORUM_POST -> sourceLookup.findPublishedForumPost(sourceId);
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.PARAM_INVALID);
        };
        if (source == null) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, ErrorMessages.NOT_FOUND);
        }
        return source;
    }

    private void requirePermission(Authentication authentication, KnowledgeImportSource source) {
        boolean allowed = permissionService.isAdmin(authentication)
                || permissionService.canManageCategory(authentication, source.getCategory());
        if (!allowed) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
    }

    private String normalizeType(String sourceType) {
        return sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
    }
}
