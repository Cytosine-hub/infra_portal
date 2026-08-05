package com.middleware.manager.web.api;

import com.middleware.manager.domain.ForumPost;
import com.middleware.manager.domain.ForumTag;
import com.middleware.manager.repository.ForumTagMapper;
import com.middleware.manager.service.ForumService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ForumControllerTest {

    @Mock
    private ForumService forumService;

    @Mock
    private ForumTagMapper tagMapper;

    private ForumController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ForumController(forumService, tagMapper);
    }

    @Test
    @DisplayName("TC-FORUM-001 再次编辑已发布文章时应展示已有标签")
    void detailContainsExistingTags() {
        ForumPost post = post(1L);
        when(forumService.getPost(1L)).thenReturn(post);
        when(forumService.getComments(1L)).thenReturn(List.of());
        when(forumService.getTagsByPostId(1L)).thenReturn(List.of(tag(10L, "中间件"), tag(11L, "Kafka")));

        Map<String, Object> result = controller.detail(1L, null);

        assertEquals(List.of("中间件", "Kafka"), result.get("tags"));
    }

    private ForumPost post(Long id) {
        ForumPost post = new ForumPost();
        post.setId(id);
        post.setTitle("测试文章");
        post.setContent("正文");
        post.setAuthorUsername("tester");
        return post;
    }

    private ForumTag tag(Long id, String name) {
        ForumTag tag = new ForumTag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }
}
