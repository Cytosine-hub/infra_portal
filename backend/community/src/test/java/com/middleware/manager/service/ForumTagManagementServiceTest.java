package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.domain.ForumTag;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.repository.ForumTagMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumTagManagementServiceTest {
    @Mock
    private ForumTagMapper tagMapper;

    private ForumTagManagementService service;

    @BeforeEach
    void setUp() {
        service = new ForumTagManagementService(tagMapper);
    }

    @Test
    @DisplayName("TC-FORUM-TAG-001 (TC-01) 系统管理员查询返回所有小组标签及文章数")
    void listAllReturnsEveryCategory() {
        when(tagMapper.findAllByOrderByPostCountDesc()).thenReturn(List.of(
                tag(1L, "性能优化", "中间件", 3),
                tag(2L, "索引设计", "数据库", 2)));

        List<ForumTag> result = service.listAll();

        assertEquals(2, result.size());
        assertEquals("数据库", result.get(1).getCategory());
        assertEquals(2, result.get(1).getPostCount());
    }

    @Test
    @DisplayName("TC-FORUM-TAG-002 (TC-02) 组管理员查询仅返回所属组标签")
    void listByCategoryReturnsManagedCategoryOnly() {
        when(tagMapper.findByCategory("中间件"))
                .thenReturn(List.of(tag(1L, "性能优化", "中间件", 3)));

        List<ForumTag> result = service.listByCategory("中间件");

        assertEquals(1, result.size());
        assertEquals("中间件", result.get(0).getCategory());
    }

    @Test
    @DisplayName("TC-FORUM-TAG-004 (TC-04) 新增标签持久化名称和所属组")
    void createPersistsNameAndCategory() {
        when(tagMapper.findByNameIgnoreCaseAndCategory("性能优化", "中间件")).thenReturn(null);

        ForumTag result = service.create("  性能优化  ", "中间件", "admin");

        ArgumentCaptor<ForumTag> captor = ArgumentCaptor.forClass(ForumTag.class);
        verify(tagMapper).insert(captor.capture());
        assertEquals("性能优化", captor.getValue().getName());
        assertEquals("中间件", captor.getValue().getCategory());
        assertEquals("admin", captor.getValue().getCreatedBy());
        assertEquals("性能优化", result.getName());
    }

    @Test
    @DisplayName("TC-FORUM-TAG-005 (TC-05) 编辑标签保留ID使关联文章同步展示新名称")
    void renameUpdatesExistingTagIdentity() {
        ForumTag existing = tag(7L, "旧名称", "中间件", 4);
        when(tagMapper.findById(7L)).thenReturn(existing);
        when(tagMapper.findByNameIgnoreCaseAndCategory("新名称", "中间件")).thenReturn(null);

        ForumTag result = service.rename(7L, "新名称");

        assertEquals(7L, result.getId());
        assertEquals("新名称", result.getName());
        verify(tagMapper).update(existing);
    }

    @Test
    @DisplayName("TC-FORUM-TAG-006 (TC-06) 删除标签先清理文章关联再删除标签")
    void deleteRemovesAssociationsThenTag() {
        when(tagMapper.findById(7L)).thenReturn(tag(7L, "待删除", "中间件", 2));

        service.delete(7L);

        var ordered = inOrder(tagMapper);
        ordered.verify(tagMapper).deletePostTagsByTagId(7L);
        ordered.verify(tagMapper).deleteById(7L);
    }

    @Test
    @DisplayName("TC-FORUM-TAG-007 (TC-07) 空值空格超长和同组重名均被拒绝")
    void invalidNamesAreRejected() {
        BusinessException nullName = assertThrows(
                BusinessException.class, () -> service.create(null, "中间件", "admin"));
        BusinessException blankName = assertThrows(
                BusinessException.class, () -> service.create("   ", "中间件", "admin"));
        BusinessException longName = assertThrows(
                BusinessException.class,
                () -> service.create("超".repeat(ForumTagManagementService.MAX_TAG_NAME_LENGTH + 1),
                        "中间件", "admin"));
        when(tagMapper.findByNameIgnoreCaseAndCategory("性能优化", "中间件"))
                .thenReturn(tag(1L, "性能优化", "中间件", 1));
        BusinessException duplicate = assertThrows(
                BusinessException.class, () -> service.create("性能优化", "中间件", "admin"));

        assertEquals(ErrorCode.PARAM_INVALID, nullName.getCode());
        assertEquals(ErrorCode.PARAM_INVALID, blankName.getCode());
        assertEquals(ErrorCode.PARAM_INVALID, longName.getCode());
        assertEquals(ErrorCode.FORUM_TAG_DUPLICATE, duplicate.getCode());
    }

    private ForumTag tag(Long id, String name, String category, int postCount) {
        ForumTag tag = new ForumTag();
        tag.setId(id);
        tag.setName(name);
        tag.setCategory(category);
        tag.setPostCount(postCount);
        return tag;
    }
}
