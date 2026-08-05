package com.middleware.manager.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.domain.*;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ForumService {
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String DEFAULT_TAG_CATEGORY = "未分组";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int INITIAL_TAG_COUNT = 0;

    private final ForumPostMapper postMapper;
    private final ForumTagMapper tagMapper;
    private final ForumCommentMapper commentMapper;
    private final PostLikeMapper postLikeMapper;

    public ForumService(ForumPostMapper postMapper, ForumTagMapper tagMapper,
                        ForumCommentMapper commentMapper, PostLikeMapper postLikeMapper) {
        this.postMapper = postMapper;
        this.tagMapper = tagMapper;
        this.commentMapper = commentMapper;
        this.postLikeMapper = postLikeMapper;
    }

    public PageInfo<ForumPost> listPosts(String keyword, String tag, String job, int page, int size) {
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageHelper.startPage(page + 1, s);
        List<ForumPost> posts;
        if (StringUtils.hasText(keyword) || StringUtils.hasText(tag) || StringUtils.hasText(job)) {
            String kw = StringUtils.hasText(keyword) ? sanitizeFulltext(keyword.trim()) : null;
            String tg = StringUtils.hasText(tag) ? tag.trim() : null;
            String jobTag = StringUtils.hasText(job) ? job.trim() : null;
            posts = postMapper.search(kw, tg, jobTag);
        } else {
            posts = postMapper.findByStatusOrderByCreatedAtDesc(STATUS_PUBLISHED);
        }
        return new PageInfo<>(posts);
    }

    public PageInfo<ForumPost> listPostsByAuthor(String authorUsername, int page, int size) {
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageHelper.startPage(page + 1, s);
        List<ForumPost> posts = postMapper.findByAuthorUsernameOrderByCreatedAtDesc(authorUsername);
        return new PageInfo<>(posts);
    }

    public ForumPost getPost(Long id) {
        ForumPost post = postMapper.findById(id);
        if (post == null) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return post;
    }

    @Transactional
    public ForumPost createPost(String title, String content, List<String> tagNames,
                                 String authorUsername, String authorDisplayName) {
        return createPost(title, content, tagNames, authorUsername, authorDisplayName, null);
    }

    @Transactional
    public ForumPost createPost(String title, String content, List<String> tagNames,
                                String authorUsername, String authorDisplayName, String category) {
        ForumPost post = new ForumPost();
        post.setTitle(title);
        post.setContent(content);
        post.setAuthorUsername(authorUsername);
        post.setAuthorDisplayName(authorDisplayName);
        post.setStatus(STATUS_PUBLISHED);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.insert(post);
        if (tagNames != null && !tagNames.isEmpty()) {
            Set<ForumTag> tags = resolveTags(tagNames, category, authorUsername);
            for (ForumTag tag : tags) {
                insertPostTag(post.getId(), tag.getId());
            }
        }
        log.info("文章已创建 id={}", post.getId());
        return post;
    }

    @Transactional
    public ForumPost updatePost(Long id, String title, String content, List<String> tagNames, String username) {
        return updatePost(id, title, content, tagNames, username, null);
    }

    @Transactional
    public ForumPost updatePost(Long id, String title, String content, List<String> tagNames,
                                String username, String category) {
        ForumPost post = getPost(id);
        if (!post.getAuthorUsername().equals(username)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "只能编辑自己的文章");
        }
        post.setTitle(title);
        post.setContent(content);
        post.setUpdatedAt(LocalDateTime.now());
        updateTags(post.getId(), tagNames, category, username);
        postMapper.update(post);
        log.info("文章已更新 id={}", id);
        return post;
    }

    @Transactional
    public void deletePost(Long id, String username) {
        ForumPost post = getPost(id);
        if (!post.getAuthorUsername().equals(username)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "只能删除自己的文章");
        }
        List<ForumTag> postTags = findTagsByPostId(id);
        for (ForumTag tag : postTags) {
            tagMapper.decrementPostCount(tag.getId());
        }
        deletePostTagsByPostId(id);
        commentMapper.deleteByPostId(id);
        postMapper.deleteById(id);
        log.info("文章已删除 id={}", id);
    }

    @Transactional
    public void incrementViewCount(Long id) {
        postMapper.incrementViewCount(id);
    }

    @Transactional
    public Map<String, Object> toggleLike(Long postId, String username) {
        ForumPost post = getPost(postId);
        boolean alreadyLiked = postLikeMapper.existsByPostIdAndUsername(postId, username);
        Map<String, Object> result = new LinkedHashMap<>();
        if (alreadyLiked) {
            postLikeMapper.deleteByPostIdAndUsername(postId, username);
            postMapper.decrementLikeCount(postId);
            result.put("liked", false);
        } else {
            PostLike pl = new PostLike();
            pl.setPostId(postId);
            pl.setUsername(username);
            postLikeMapper.insert(pl);
            postMapper.incrementLikeCount(postId);
            result.put("liked", true);
        }
        ForumPost updated = postMapper.findById(postId);
        result.put("likeCount", updated.getLikeCount());
        return result;
    }

    public boolean hasUserLiked(Long postId, String username) {
        return postLikeMapper.existsByPostIdAndUsername(postId, username);
    }

    public List<ForumComment> getComments(Long postId) {
        return commentMapper.findByPostIdOrderByCreatedAtAsc(postId);
    }

    @Transactional
    public ForumComment addComment(Long postId, String content, String authorUsername, String authorDisplayName) {
        getPost(postId);
        ForumComment comment = new ForumComment();
        comment.setPostId(postId);
        comment.setContent(content);
        comment.setAuthorUsername(authorUsername);
        comment.setAuthorDisplayName(authorDisplayName);
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        postMapper.updateCommentCount(postId);
        log.info("评论已添加 postId={}", postId);
        return comment;
    }

    @Transactional
    public ForumComment addReply(Long postId, Long parentId, String content, String authorUsername, String authorDisplayName) {
        getPost(postId);
        ForumComment parent = commentMapper.findById(parentId);
        if (parent == null) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "父评论不存在");
        }
        ForumComment reply = new ForumComment();
        reply.setPostId(postId);
        reply.setParentId(parentId);
        reply.setContent(content);
        reply.setAuthorUsername(authorUsername);
        reply.setAuthorDisplayName(authorDisplayName);
        reply.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(reply);
        log.info("回复已添加 postId={}, parentId={}", postId, parentId);
        return reply;
    }

    @Transactional
    public Map<String, Object> toggleCommentLike(Long commentId) {
        ForumComment comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        commentMapper.incrementLikeCount(commentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("likeCount", comment.getLikeCount() + 1);
        return result;
    }

    public List<ForumTag> getAllTags() {
        return tagMapper.findAllByOrderByPostCountDesc();
    }

    public List<ForumTag> getTagsByPostId(Long postId) {
        return tagMapper.findByPostId(postId);
    }

    private Set<ForumTag> resolveTags(List<String> names, String category, String username) {
        if (names == null || names.isEmpty()) return new HashSet<>();
        Set<ForumTag> tags = new HashSet<>();
        for (String name : names) {
            tags.add(getOrCreateTag(name, category, username));
        }
        return tags;
    }

    private void updateTags(Long postId, List<String> newNames, String category, String username) {
        Map<String, String> newTagsByNormalizedName = new LinkedHashMap<>();
        if (newNames != null) {
            for (String name : newNames) {
                if (name == null || name.trim().isEmpty()) continue;
                String trimmed = name.trim();
                newTagsByNormalizedName.putIfAbsent(normalizeTagName(trimmed), trimmed);
            }
        }
        List<ForumTag> currentTags = findTagsByPostId(postId);
        Set<String> oldNames = currentTags.stream()
                .map(ForumTag::getName)
                .map(this::normalizeTagName)
                .collect(Collectors.toSet());

        for (ForumTag tag : currentTags) {
            if (!newTagsByNormalizedName.containsKey(normalizeTagName(tag.getName()))) {
                deletePostTag(postId, tag.getId());
                tagMapper.decrementPostCount(tag.getId());
            }
        }
        for (Map.Entry<String, String> entry : newTagsByNormalizedName.entrySet()) {
            if (!oldNames.contains(entry.getKey())) {
                String name = entry.getValue();
                ForumTag tag = getOrCreateTag(name, category, username);
                insertPostTag(postId, tag.getId());
            }
        }
    }

    private String normalizeTagName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private ForumTag getOrCreateTag(String name, String category, String username) {
        String trimmed = name.trim();
        String normalizedCategory = normalizeTagCategory(category);
        ForumTag tag = tagMapper.findByNameIgnoreCaseAndCategory(trimmed, normalizedCategory);
        if (tag == null) {
            tag = new ForumTag();
            tag.setName(trimmed);
            tag.setPostCount(INITIAL_TAG_COUNT);
            tag.setCategory(normalizedCategory);
            tag.setCreatedBy(username);
            tag.setCreatedAt(LocalDateTime.now());
            tag.setUpdatedAt(LocalDateTime.now());
            tagMapper.insert(tag);
        }
        tag.setPostCount(tag.getPostCount() + 1);
        tag.setUpdatedAt(LocalDateTime.now());
        tagMapper.update(tag);
        return tag;
    }

    private String normalizeTagCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : DEFAULT_TAG_CATEGORY;
    }

    private String sanitizeFulltext(String keyword) {
        return keyword.replaceAll("[+\\-<>()~*\"@%_]", " ").trim().replaceAll("\\s+", " ");
    }

    private List<ForumTag> findTagsByPostId(Long postId) {
        return tagMapper.findByPostId(postId);
    }

    private void insertPostTag(Long postId, Long tagId) {
        tagMapper.insertPostTag(postId, tagId);
    }

    private void deletePostTag(Long postId, Long tagId) {
        tagMapper.deletePostTag(postId, tagId);
    }

    private void deletePostTagsByPostId(Long postId) {
        tagMapper.deletePostTagsByPostId(postId);
    }
}
