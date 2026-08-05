package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.ForumTag;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.ForumTagMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ForumTagManagementService {
    public static final int MAX_TAG_NAME_LENGTH = 50;

    private final ForumTagMapper tagMapper;

    public ForumTagManagementService(ForumTagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    public List<ForumTag> listAll() {
        return tagMapper.findAllByOrderByPostCountDesc();
    }

    public List<ForumTag> listByCategory(String category) {
        return tagMapper.findByCategory(category);
    }

    public ForumTag get(Long id) {
        ForumTag tag = tagMapper.findById(id);
        if (tag == null) {
            throw new NotFoundException(ErrorCode.FORUM_TAG_NOT_FOUND, ErrorMessages.FORUM_TAG_NOT_FOUND);
        }
        return tag;
    }

    @Transactional
    public ForumTag create(String name, String category, String createdBy) {
        String normalizedName = validateName(name);
        String normalizedCategory = validateCategory(category);
        ensureUnique(normalizedName, normalizedCategory, null);

        LocalDateTime now = LocalDateTime.now();
        ForumTag tag = new ForumTag();
        tag.setName(normalizedName);
        tag.setCategory(normalizedCategory);
        tag.setCreatedBy(createdBy);
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        tagMapper.insert(tag);
        log.info("论坛标签已创建 id={} category={}", tag.getId(), tag.getCategory());
        return tag;
    }

    @Transactional
    public ForumTag rename(Long id, String name) {
        ForumTag tag = get(id);
        String normalizedName = validateName(name);
        ensureUnique(normalizedName, tag.getCategory(), id);
        tag.setName(normalizedName);
        tag.setUpdatedAt(LocalDateTime.now());
        tagMapper.update(tag);
        log.info("论坛标签已更新 id={} category={}", tag.getId(), tag.getCategory());
        return tag;
    }

    @Transactional
    public void delete(Long id) {
        ForumTag tag = get(id);
        tagMapper.deletePostTagsByTagId(id);
        tagMapper.deleteById(id);
        log.info("论坛标签已删除 id={} category={}", id, tag.getCategory());
    }

    private String validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.FORUM_TAG_NAME_REQUIRED);
        }
        String normalized = name.trim();
        if (normalized.length() > MAX_TAG_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.FORUM_TAG_NAME_TOO_LONG);
        }
        return normalized;
    }

    private String validateCategory(String category) {
        if (!StringUtils.hasText(category)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.FORUM_TAG_CATEGORY_REQUIRED);
        }
        return category.trim();
    }

    private void ensureUnique(String name, String category, Long currentId) {
        ForumTag duplicate = tagMapper.findByNameIgnoreCaseAndCategory(name, category);
        if (duplicate != null && !Objects.equals(duplicate.getId(), currentId)) {
            throw new BusinessException(ErrorCode.FORUM_TAG_DUPLICATE, ErrorMessages.FORUM_TAG_DUPLICATE);
        }
    }
}
