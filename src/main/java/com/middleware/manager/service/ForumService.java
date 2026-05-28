package com.middleware.manager.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.middleware.manager.domain.*;
import com.middleware.manager.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ForumService {
    private static final Logger log = LoggerFactory.getLogger(ForumService.class);
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

    public PageInfo<ForumPost> listPosts(String keyword, String tag, int page, int size) {
        int s = Math.min(Math.max(size, 1), 50);
        PageHelper.startPage(page + 1, s);
        List<ForumPost> posts;
        if (StringUtils.hasText(keyword) || StringUtils.hasText(tag)) {
            String kw = StringUtils.hasText(keyword) ? sanitizeFulltext(keyword.trim()) : null;
            String tg = StringUtils.hasText(tag) ? tag.trim() : null;
            posts = postMapper.search(kw, tg);
        } else {
            posts = postMapper.findByStatusOrderByCreatedAtDesc("PUBLISHED");
        }
        return new PageInfo<>(posts);
    }

    public ForumPost getPost(Long id) {
        ForumPost post = postMapper.findById(id);
        if (post == null) {
            throw new IllegalArgumentException("文章不存在");
        }
        return post;
    }

    @Transactional
    public ForumPost createPost(String title, String content, List<String> tagNames,
                                 String authorUsername, String authorDisplayName) {
        ForumPost post = new ForumPost();
        post.setTitle(title);
        post.setContent(content);
        post.setAuthorUsername(authorUsername);
        post.setAuthorDisplayName(authorDisplayName);
        post.setStatus("PUBLISHED");
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.insert(post);
        // Handle tags after insert so we have the post id
        if (tagNames != null && !tagNames.isEmpty()) {
            Set<ForumTag> tags = resolveTags(tagNames);
            for (ForumTag tag : tags) {
                insertPostTag(post.getId(), tag.getId());
            }
        }
        return post;
    }

    @Transactional
    public ForumPost updatePost(Long id, String title, String content, List<String> tagNames, String username) {
        ForumPost post = getPost(id);
        if (!post.getAuthorUsername().equals(username))
            throw new IllegalArgumentException("只能编辑自己的文章");
        post.setTitle(title);
        post.setContent(content);
        post.setUpdatedAt(LocalDateTime.now());
        updateTags(post.getId(), tagNames);
        postMapper.update(post);
        return post;
    }

    @Transactional
    public void deletePost(Long id, String username) {
        ForumPost post = getPost(id);
        if (!post.getAuthorUsername().equals(username))
            throw new IllegalArgumentException("只能删除自己的文章");
        // Decrement tag post counts
        List<ForumTag> postTags = findTagsByPostId(id);
        for (ForumTag tag : postTags) {
            tagMapper.decrementPostCount(tag.getId());
        }
        // Delete post-tag associations
        deletePostTagsByPostId(id);
        // Delete comments
        commentMapper.deleteByPostId(id);
        // Delete post
        postMapper.deleteById(id);
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
        // Re-fetch to get accurate count
        ForumPost updated = postMapper.findById(postId);
        result.put("likeCount", updated.getLikeCount());
        return result;
    }

    public boolean hasUserLiked(Long postId, String username) {
        return postLikeMapper.existsByPostIdAndUsername(postId, username);
    }

    // Comments
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
        return comment;
    }

    @Transactional
    public ForumComment addReply(Long postId, Long parentId, String content, String authorUsername, String authorDisplayName) {
        getPost(postId);
        ForumComment parent = commentMapper.findById(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("父评论不存在");
        }
        ForumComment reply = new ForumComment();
        reply.setPostId(postId);
        reply.setParentId(parentId);
        reply.setContent(content);
        reply.setAuthorUsername(authorUsername);
        reply.setAuthorDisplayName(authorDisplayName);
        reply.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(reply);
        return reply;
    }

    @Transactional
    public Map<String, Object> toggleCommentLike(Long commentId) {
        ForumComment comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        commentMapper.incrementLikeCount(commentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("likeCount", comment.getLikeCount() + 1);
        return result;
    }

    // Tags
    public List<ForumTag> getAllTags() {
        return tagMapper.findAllByOrderByPostCountDesc();
    }

    private Set<ForumTag> resolveTags(List<String> names) {
        if (names == null || names.isEmpty()) return new HashSet<>();
        Set<ForumTag> tags = new HashSet<>();
        for (String name : names) {
            tags.add(getOrCreateTag(name));
        }
        return tags;
    }

    private void updateTags(Long postId, List<String> newNames) {
        Set<String> newSet = newNames != null ?
                newNames.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet()) :
                new HashSet<>();
        List<ForumTag> currentTags = findTagsByPostId(postId);
        Set<String> oldSet = currentTags.stream().map(ForumTag::getName).collect(java.util.stream.Collectors.toSet());

        // Remove tags no longer in the list
        for (ForumTag tag : currentTags) {
            if (!newSet.contains(tag.getName())) {
                deletePostTag(postId, tag.getId());
                tagMapper.decrementPostCount(tag.getId());
            }
        }
        // Add new tags
        for (String name : newSet) {
            if (!oldSet.contains(name)) {
                ForumTag tag = getOrCreateTag(name);
                insertPostTag(postId, tag.getId());
            }
        }
    }

    private ForumTag getOrCreateTag(String name) {
        String trimmed = name.trim();
        ForumTag tag = tagMapper.findByNameIgnoreCase(trimmed);
        if (tag == null) {
            tag = new ForumTag();
            tag.setName(trimmed);
            tag.setPostCount(0);
            tagMapper.insert(tag);
        }
        tag.setPostCount(tag.getPostCount() + 1);
        tagMapper.update(tag);
        return tag;
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
