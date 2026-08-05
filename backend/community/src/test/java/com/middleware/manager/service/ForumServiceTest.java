package com.middleware.manager.service;

import com.middleware.manager.domain.ForumPost;
import com.middleware.manager.domain.ForumTag;
import com.middleware.manager.repository.ForumCommentMapper;
import com.middleware.manager.repository.ForumPostMapper;
import com.middleware.manager.repository.ForumTagMapper;
import com.middleware.manager.repository.PostLikeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForumServiceTest {

    @Mock
    private ForumPostMapper postMapper;
    @Mock
    private ForumTagMapper tagMapper;
    @Mock
    private ForumCommentMapper commentMapper;
    @Mock
    private PostLikeMapper postLikeMapper;

    private ForumService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ForumService(postMapper, tagMapper, commentMapper, postLikeMapper);
        when(postMapper.findById(1L)).thenReturn(post());
    }

    @Test
    @DisplayName("TC-FORUM-001 查询文章标签时应返回全部已有标签")
    void getTagsByPostIdReturnsExistingTags() {
        List<ForumTag> tags = List.of(tag(10L, "中间件", 1), tag(11L, "Kafka", 1));
        when(tagMapper.findByPostId(1L)).thenReturn(tags);

        List<ForumTag> result = service.getTagsByPostId(1L);

        assertEquals(tags, result);
        verify(tagMapper).findByPostId(1L);
    }

    @Test
    @DisplayName("TC-FORUM-002 再次编辑时新增标签不应覆盖旧标签")
    void updateAddsTagWithoutRemovingExistingTags() {
        ForumTag middleware = tag(10L, "中间件", 1);
        ForumTag kafka = tag(11L, "Kafka", 1);
        ForumTag rocketMq = tag(12L, "RocketMQ", 0);
        when(tagMapper.findByPostId(1L)).thenReturn(List.of(middleware, kafka));
        when(tagMapper.findByNameIgnoreCase("RocketMQ")).thenReturn(rocketMq);

        service.updatePost(1L, "新标题", "新正文", List.of("中间件", "Kafka", "RocketMQ"), "tester");

        verify(tagMapper).insertPostTag(1L, 12L);
        verify(tagMapper, never()).deletePostTag(any(), any());
    }

    @Test
    @DisplayName("TC-FORUM-003 再次编辑时应删除指定已有标签")
    void updateRemovesSelectedTag() {
        ForumTag middleware = tag(10L, "中间件", 1);
        ForumTag kafka = tag(11L, "Kafka", 1);
        ForumTag rocketMq = tag(12L, "RocketMQ", 1);
        when(tagMapper.findByPostId(1L)).thenReturn(List.of(middleware, kafka, rocketMq));

        service.updatePost(1L, "新标题", "新正文", List.of("中间件", "RocketMQ"), "tester");

        verify(tagMapper).deletePostTag(1L, 11L);
        verify(tagMapper).decrementPostCount(11L);
        verify(tagMapper, never()).insertPostTag(any(), any());
    }

    @Test
    @DisplayName("TC-FORUM-004 再次编辑时应同时保存新增和删除标签")
    void updateAddsAndRemovesTagsTogether() {
        ForumTag middleware = tag(10L, "中间件", 1);
        ForumTag kafka = tag(11L, "Kafka", 1);
        ForumTag redis = tag(12L, "Redis", 0);
        ForumTag mq = tag(13L, "MQ", 0);
        when(tagMapper.findByPostId(1L)).thenReturn(List.of(middleware, kafka));
        when(tagMapper.findByNameIgnoreCase("Redis")).thenReturn(redis);
        when(tagMapper.findByNameIgnoreCase("MQ")).thenReturn(mq);

        service.updatePost(1L, "新标题", "新正文", List.of("中间件", "Redis", "MQ"), "tester");

        verify(tagMapper).deletePostTag(1L, 11L);
        verify(tagMapper).insertPostTag(1L, 12L);
        verify(tagMapper).insertPostTag(1L, 13L);
    }

    @Test
    @DisplayName("TC-FORUM-005 编辑其他内容但不修改标签时应保持原标签")
    void updateOtherContentKeepsTagsUnchanged() {
        when(tagMapper.findByPostId(1L)).thenReturn(List.of(
                tag(10L, "中间件", 1), tag(11L, "Kafka", 1)));

        service.updatePost(1L, "新标题", "新正文", List.of("中间件", "Kafka"), "tester");

        verify(tagMapper, never()).deletePostTag(any(), any());
        verify(tagMapper, never()).insertPostTag(any(), any());
        verify(tagMapper, never()).decrementPostCount(any());
    }

    @Test
    @DisplayName("TC-FORUM-006 原文章无标签时再次编辑应可新增标签")
    void updateUntaggedPostAddsTag() {
        ForumTag middleware = tag(10L, "中间件", 0);
        when(tagMapper.findByPostId(1L)).thenReturn(List.of());
        when(tagMapper.findByNameIgnoreCase("中间件")).thenReturn(middleware);

        service.updatePost(1L, "新标题", "新正文", List.of("中间件"), "tester");

        verify(tagMapper).insertPostTag(1L, 10L);
    }

    @Test
    @DisplayName("TC-FORUM-007 新增重复标签时应忽略大小写自动去重")
    void updateDeduplicatesTagNamesIgnoringCase() {
        when(tagMapper.findByPostId(1L)).thenReturn(List.of(
                tag(10L, "中间件", 1), tag(11L, "Kafka", 1)));

        service.updatePost(1L, "新标题", "新正文", List.of("中间件", "Kafka", "kafka"), "tester");

        verify(tagMapper, never()).deletePostTag(any(), any());
        verify(tagMapper, never()).insertPostTag(any(), any());
        verify(tagMapper, never()).findByNameIgnoreCase(any());
    }

    private ForumPost post() {
        ForumPost post = new ForumPost();
        post.setId(1L);
        post.setAuthorUsername("tester");
        post.setTitle("原标题");
        post.setContent("原正文");
        return post;
    }

    private ForumTag tag(Long id, String name, int postCount) {
        ForumTag tag = new ForumTag();
        tag.setId(id);
        tag.setName(name);
        tag.setPostCount(postCount);
        return tag;
    }
}
