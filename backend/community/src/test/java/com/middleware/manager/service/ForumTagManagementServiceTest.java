package com.middleware.manager.service;

import com.middleware.manager.domain.ForumTag;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.repository.ForumTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForumTagManagementServiceTest {

    @Mock
    private ForumTagMapper tagMapper;

    private ForumTagManagementService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ForumTagManagementService(tagMapper);
    }

    @Test
    @DisplayName("TC-01 个人中心仅返回当前用户文章关联的标签")
    void personalTagsAreLimitedToCurrentAuthor() {
        when(tagMapper.findByAuthorUsername("user-a")).thenReturn(List.of(tag(1L, "Java", "中间件"), tag(2L, "Redis", "中间件")));

        List<ForumTag> result = service.listPersonal("user-a");

        assertEquals(List.of("Java", "Redis"), result.stream().map(ForumTag::getName).toList());
        verify(tagMapper).findByAuthorUsername("user-a");
    }

    @Test
    @DisplayName("TC-02 个人修改标签应同步当前用户对应文章且不影响共享标签")
    void personalRenameReassignsOnlyCurrentAuthorsPostsWhenTagIsShared() {
        ForumTag redis = tag(1L, "Redis", "中间件");
        when(tagMapper.findById(1L)).thenReturn(redis);
        when(tagMapper.isUsedByAuthor(1L, "user-a")).thenReturn(true);
        when(tagMapper.hasAssociationsOutsideAuthor(1L, "user-a")).thenReturn(true);
        when(tagMapper.findByNameIgnoreCase("Redis缓存")).thenReturn(null);
        doAnswer(invocation -> {
            ForumTag inserted = invocation.getArgument(0);
            inserted.setId(10L);
            return 1;
        }).when(tagMapper).insert(any(ForumTag.class));

        service.renamePersonal(1L, " Redis缓存 ", "user-a");

        verify(tagMapper).insert(any(ForumTag.class));
        verify(tagMapper).reassignAuthorTag(10L, redis.getId(), "user-a");
        verify(tagMapper, never()).update(redis);
    }

    @Test
    @DisplayName("TC-03 个人中心禁止编辑或删除他人文章标签")
    void personalMutationRejectsAnotherAuthorsTag() {
        when(tagMapper.findById(9L)).thenReturn(tag(9L, "MySQL", "数据库"));
        when(tagMapper.isUsedByAuthor(9L, "user-a")).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.renamePersonal(9L, "MariaDB", "user-a"));
        assertThrows(ForbiddenException.class, () -> service.deletePersonal(9L, "user-a"));
        verify(tagMapper, never()).update(any());
        verify(tagMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("TC-03 个人删除自己的标签时仅解除本人文章关联")
    void personalDeleteRemovesOnlyCurrentAuthorsAssociations() {
        ForumTag tag = tag(3L, "Redis", "中间件");
        when(tagMapper.findById(3L)).thenReturn(tag);
        when(tagMapper.isUsedByAuthor(3L, "user-a")).thenReturn(true);

        service.deletePersonal(3L, "user-a");

        verify(tagMapper).deleteAuthorTagAssociations(3L, "user-a");
        verify(tagMapper).refreshPostCount(3L);
    }

    @Test
    @DisplayName("TC-04 超级管理员可查看并管理所有论坛标签")
    void systemAdminCanManageAllTags() {
        when(tagMapper.findAllByOrderByPostCountDesc()).thenReturn(List.of(tag(1L, "网关", "中间件"), tag(2L, "监控", "主机")));
        when(tagMapper.findByNameIgnoreCase("Java")).thenReturn(null);
        when(tagMapper.findByNameIgnoreCase("可观测")).thenReturn(null);
        when(tagMapper.findById(2L)).thenReturn(tag(2L, "监控", "主机"));

        assertEquals(2, service.listAdmin(null, true).size());
        service.createAdmin("Java", "中间件", "root");
        service.renameAdmin(2L, "可观测", null, true);
        service.deleteAdmin(2L, null, true);

        verify(tagMapper).insert(any(ForumTag.class));
        verify(tagMapper).update(any(ForumTag.class));
        verify(tagMapper).deletePostTagsByTagId(2L);
        verify(tagMapper).deleteById(2L);
    }

    @Test
    @DisplayName("TC-05 组管理员仅可查看并管理所属组标签")
    void categoryAdminIsLimitedToManagedCategory() {
        ForumTag gateway = tag(1L, "网关", "中间件");
        when(tagMapper.findByCategory("中间件")).thenReturn(List.of(gateway));
        when(tagMapper.findById(1L)).thenReturn(gateway);
        when(tagMapper.findById(2L)).thenReturn(tag(2L, "监控", "主机"));
        when(tagMapper.findByNameIgnoreCase("API网关")).thenReturn(null);

        assertEquals(1, service.listAdmin("中间件", false).size());
        service.renameAdmin(1L, "API网关", "中间件", false);
        assertThrows(ForbiddenException.class, () -> service.deleteAdmin(2L, "中间件", false));
        verify(tagMapper).update(gateway);
        verify(tagMapper, never()).deleteById(2L);
    }

    @Test
    @DisplayName("TC-07 新增或编辑标签应拒绝空白、重复和超长名称")
    void invalidTagNamesAreRejected() {
        ForumTag java = tag(1L, "Java", "中间件");
        when(tagMapper.findByNameIgnoreCase("Java")).thenReturn(java);

        assertThrows(BusinessException.class, () -> service.createAdmin("", "中间件", "admin"));
        assertThrows(BusinessException.class, () -> service.createAdmin("   ", "中间件", "admin"));
        assertThrows(BusinessException.class, () -> service.createAdmin("Java", "中间件", "admin"));
        assertThrows(BusinessException.class, () -> service.createAdmin("x".repeat(51), "中间件", "admin"));
        verify(tagMapper, never()).insert(any());
    }

    @Test
    @DisplayName("TC-FORUM-TAG-012（TC-07）跨岗位组新增同名标签应被拒绝")
    void duplicateNameInAnotherCategoryIsRejected() {
        ForumTag java = tag(1L, "Java", "中间件");
        when(tagMapper.findByNameIgnoreCase("Java")).thenReturn(java);

        assertThrows(BusinessException.class, () -> service.createAdmin("Java", "数据库", "root"));

        verify(tagMapper, never()).insert(any());
    }

    @Test
    @DisplayName("TC-FORUM-TAG-009（TC-07）历史未分组标签重命名时应拒绝重复名称")
    void legacyNullCategoryRenameRejectsDuplicateName() {
        ForumTag legacyTag = tag(1L, "旧标签", null);
        ForumTag duplicate = tag(2L, "Java", "未分组");
        when(tagMapper.findById(1L)).thenReturn(legacyTag);
        when(tagMapper.isUsedByAuthor(1L, "user-a")).thenReturn(true);
        when(tagMapper.findByNameIgnoreCase("Java")).thenReturn(duplicate);

        assertThrows(BusinessException.class, () -> service.renamePersonal(1L, "Java", "user-a"));

        verify(tagMapper, never()).update(any());
    }

    private ForumTag tag(Long id, String name, String category) {
        ForumTag tag = new ForumTag();
        tag.setId(id);
        tag.setName(name);
        tag.setCategory(category);
        return tag;
    }
}
