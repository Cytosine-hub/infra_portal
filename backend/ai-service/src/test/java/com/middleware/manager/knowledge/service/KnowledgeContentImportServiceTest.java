package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.KnowledgeImportSource;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.KnowledgeImportSourceMapper;
import com.middleware.manager.security.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeContentImportServiceTest {

    @Mock private KnowledgeImportSourceMapper sourceLookup;
    @Mock private KnowledgeService knowledgeService;
    @Mock private PermissionService permissionService;
    @Mock private Authentication authentication;

    private KnowledgeContentImportService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new KnowledgeContentImportService(sourceLookup, knowledgeService, permissionService);
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-012 应从数据库读取已发布标准正文后导入")
    void importsAuthoritativeStandardDocument() {
        KnowledgeImportSource source = source(
                11L, "Nginx 部署规范", "STANDARD_DOCUMENT", "权威正文", "中间件", "Nginx");
        when(sourceLookup.findPublishedStandardDocument(11L)).thenReturn(source);
        when(permissionService.canManageCategory(authentication, "中间件")).thenReturn(true);
        KnowledgeService.ImportResult result = new KnowledgeService.ImportResult();
        result.setSourceId(101L);
        when(knowledgeService.importContent(
                "Nginx 部署规范", "STANDARD_DOCUMENT", "权威正文",
                "中间件", "Nginx", "11")).thenReturn(result);

        service.importContent("STANDARD_DOCUMENT", 11L, authentication);

        verify(knowledgeService).importContent(
                "Nginx 部署规范", "STANDARD_DOCUMENT", "权威正文",
                "中间件", "Nginx", "11");
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-013 分类权限必须依据数据库中的真实分类")
    void rejectsCategoryOutsidePermission() {
        KnowledgeImportSource source = source(
                12L, "Oracle 标准", "STANDARD_DOCUMENT", "正文", "数据库", "Oracle");
        when(sourceLookup.findPublishedStandardDocument(12L)).thenReturn(source);
        when(permissionService.isAdmin(authentication)).thenReturn(false);
        when(permissionService.canManageCategory(authentication, "数据库")).thenReturn(false);

        assertThatThrownBy(() -> service.importContent(
                "STANDARD_DOCUMENT", 12L, authentication))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("TC-KNOWLEDGE-IMPORT-014 已删除或未发布的论坛文章不得导入")
    void rejectsUnavailableForumPost() {
        when(sourceLookup.findPublishedForumPost(21L)).thenReturn(null);

        assertThatThrownBy(() -> service.importContent("FORUM_POST", 21L, authentication))
                .isInstanceOf(NotFoundException.class);
    }

    private KnowledgeImportSource source(Long id, String title, String sourceType,
                                         String content, String category, String software) {
        KnowledgeImportSource source = new KnowledgeImportSource();
        source.setSourceId(id);
        source.setTitle(title);
        source.setSourceType(sourceType);
        source.setContent(content);
        source.setCategory(category);
        source.setSoftware(software);
        return source;
    }
}
