package com.middleware.manager.web.api;

import com.middleware.manager.domain.ForumComment;
import com.middleware.manager.domain.ForumPost;
import com.middleware.manager.domain.ForumTag;
import com.middleware.manager.repository.ForumTagMapper;
import com.middleware.manager.service.ForumService;
import com.middleware.manager.web.api.dto.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/forum")
public class ForumController {
    private final ForumService forumService;
    private final ForumTagMapper tagMapper;

    public ForumController(ForumService forumService, ForumTagMapper tagMapper) {
        this.forumService = forumService;
        this.tagMapper = tagMapper;
    }

    @GetMapping("/posts")
    public PageResult<Map<String, Object>> list(@RequestParam(defaultValue = "") String keyword,
                                     @RequestParam(defaultValue = "") String tag,
                                     @RequestParam(defaultValue = "") String job,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "12") int size) {
        var p = forumService.listPosts(keyword, tag, job, page, size);
        Map<Long, List<String>> tagsByPostId = batchLoadTags(p.getList());
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setContent(p.getList().stream().map(post -> toSummary(post, tagsByPostId)).collect(Collectors.toList()));
        result.setPage(p.getPageNum() - 1);
        result.setSize(p.getPageSize());
        result.setTotalElements(p.getTotal());
        result.setTotalPages(p.getPages());
        result.setFirst(p.isIsFirstPage());
        result.setLast(p.isIsLastPage());
        return result;
    }

    @GetMapping("/posts/{id}")
    public Map<String, Object> detail(@PathVariable Long id, Authentication auth) {
        ForumPost post = forumService.getPost(id);
        forumService.incrementViewCount(id);
        Map<String, Object> result = toDetail(post);
        result.put("liked", auth != null && forumService.hasUserLiked(id, auth.getName()));
        result.put("comments", forumService.getComments(id).stream().map(this::toCommentMap).collect(Collectors.toList()));
        return result;
    }

    @PostMapping("/posts")
    public Map<String, Object> create(@RequestBody CreatePostRequest req, Authentication auth) {
        if (req.title == null || req.title.trim().length() < 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        if (req.content == null || req.content.trim().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容不能为空");
        ForumPost post = forumService.createPost(req.title, req.content, req.tags,
                auth.getName(), getDisplayName(auth));
        return toDetail(post);
    }

    @PutMapping("/posts/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody CreatePostRequest req, Authentication auth) {
        if (req.title == null || req.title.trim().length() < 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        if (req.content == null || req.content.trim().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容不能为空");
        ForumPost post = forumService.updatePost(id, req.title, req.content, req.tags, auth.getName());
        return toDetail(post);
    }

    @DeleteMapping("/posts/{id}")
    public void delete(@PathVariable Long id, Authentication auth) {
        forumService.deletePost(id, auth.getName());
    }

    @PostMapping("/posts/{id}/like")
    public Map<String, Object> like(@PathVariable Long id, Authentication auth) {
        return forumService.toggleLike(id, auth.getName());
    }

    @PostMapping("/posts/{id}/comments")
    public Map<String, Object> addComment(@PathVariable Long id, @RequestBody CommentRequest req, Authentication auth) {
        if (req.content == null || req.content.trim().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论内容不能为空");
        ForumComment c;
        if (req.parentId != null) {
            c = forumService.addReply(id, req.parentId, req.content, auth.getName(), getDisplayName(auth));
        } else {
            c = forumService.addComment(id, req.content, auth.getName(), getDisplayName(auth));
        }
        return toCommentMap(c);
    }

    @PostMapping("/comments/{id}/like")
    public Map<String, Object> likeComment(@PathVariable Long id, Authentication auth) {
        return forumService.toggleCommentLike(id);
    }

    @GetMapping("/tags")
    public List<Map<String, Object>> tags() {
        return forumService.getAllTags().stream().map(this::toTagMap).collect(Collectors.toList());
    }

    @GetMapping("/my-posts")
    public PageResult<Map<String, Object>> myPosts(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "12") int size,
                                                   Authentication auth) {
        var p = forumService.listPostsByAuthor(auth.getName(), page, size);
        Map<Long, List<String>> tagsByPostId = batchLoadTags(p.getList());
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setContent(p.getList().stream().map(post -> toSummary(post, tagsByPostId)).collect(Collectors.toList()));
        result.setPage(p.getPageNum() - 1);
        result.setSize(p.getPageSize());
        result.setTotalElements(p.getTotal());
        result.setTotalPages(p.getPages());
        result.setFirst(p.isIsFirstPage());
        result.setLast(p.isIsLastPage());
        return result;
    }

    private Map<Long, List<String>> batchLoadTags(List<ForumPost> posts) {
        if (posts == null || posts.isEmpty()) return Collections.emptyMap();
        List<Long> postIds = posts.stream().map(ForumPost::getId).collect(Collectors.toList());
        List<ForumTag> tags = tagMapper.findByPostIds(postIds);
        return tags.stream().collect(Collectors.groupingBy(
                ForumTag::getPostId,
                Collectors.mapping(ForumTag::getName, Collectors.toList())
        ));
    }

    private Map<String, Object> toSummary(ForumPost p, Map<Long, List<String>> tagsByPostId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("title", p.getTitle());
        m.put("authorDisplayName", p.getAuthorDisplayName());
        m.put("viewCount", p.getViewCount()); m.put("likeCount", p.getLikeCount());
        m.put("commentCount", p.getCommentCount());
        m.put("tags", tagsByPostId.getOrDefault(p.getId(), Collections.emptyList()));
        String content = p.getContent();
        m.put("summary", content != null && content.length() > 200 ? content.substring(0, 200) + "..." : content);
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    private Map<String, Object> toDetail(ForumPost p) {
        Map<String, Object> m = toSummary(p, Collections.emptyMap());
        m.remove("summary"); m.put("content", p.getContent());
        m.put("authorUsername", p.getAuthorUsername());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }

    private Map<String, Object> toCommentMap(ForumComment c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId()); m.put("content", c.getContent());
        m.put("authorDisplayName", c.getAuthorDisplayName());
        m.put("authorUsername", c.getAuthorUsername());
        m.put("parentId", c.getParentId());
        m.put("likeCount", c.getLikeCount());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    private Map<String, Object> toTagMap(ForumTag t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId()); m.put("name", t.getName()); m.put("postCount", t.getPostCount());
        return m;
    }

    private String getDisplayName(Authentication auth) {
        return auth.getName();
    }

    static class CreatePostRequest {
        public String title;
        public String content;
        public List<String> tags;
    }

    static class CommentRequest {
        public String content;
        public Long parentId;
    }
}
