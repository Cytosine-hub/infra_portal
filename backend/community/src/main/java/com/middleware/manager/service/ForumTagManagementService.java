package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.ForumTag;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.ForumTagMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ForumTagManagementService {
    public static final int MAX_TAG_NAME_LENGTH = 50;

    private final ForumTagMapper tagMapper;

    public ForumTagManagementService(ForumTagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    public List<ForumTag> listPersonal(String username) {
        return tagMapper.findByAuthorUsername(username);
    }

    public List<ForumTag> listAdmin(String managedCategory, boolean systemAdmin) {
        return systemAdmin ? tagMapper.findAllByOrderByPostCountDesc() : tagMapper.findByCategory(managedCategory);
    }

    @Transactional
    public ForumTag createAdmin(String rawName, String category, String username) {
        String name = validateName(rawName);
        if (!StringUtils.hasText(category)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.PARAM_INVALID);
        }
        ensureUnique(name, category, null);
        ForumTag tag = new ForumTag();
        tag.setName(name);
        tag.setCategory(category.trim());
        tag.setCreatedBy(username);
        tag.setCreatedAt(LocalDateTime.now());
        tag.setUpdatedAt(LocalDateTime.now());
        tagMapper.insert(tag);
        log.info("论坛标签已创建 id={} category={}", tag.getId(), tag.getCategory());
        return tag;
    }

    @Transactional
    public ForumTag renamePersonal(Long id, String rawName, String username) {
        ForumTag tag = requirePersonalTag(id, username);
        String name = validateName(rawName);
        ensureUnique(name, tag.getCategory(), id);
        if (tagMapper.hasAssociationsOutsideAuthor(id, username)) {
            ForumTag replacement = new ForumTag();
            replacement.setName(name);
            replacement.setCategory(tag.getCategory());
            replacement.setCreatedBy(username);
            replacement.setCreatedAt(LocalDateTime.now());
            replacement.setUpdatedAt(LocalDateTime.now());
            tagMapper.insert(replacement);
            tagMapper.reassignAuthorTag(replacement.getId(), id, username);
            tagMapper.refreshPostCount(replacement.getId());
            tagMapper.refreshPostCount(id);
            return replacement;
        }
        tag.setName(name);
        tag.setUpdatedAt(LocalDateTime.now());
        tagMapper.update(tag);
        return tag;
    }

    @Transactional
    public void deletePersonal(Long id, String username) {
        requirePersonalTag(id, username);
        tagMapper.deleteAuthorTagAssociations(id, username);
        tagMapper.refreshPostCount(id);
        ForumTag remaining = tagMapper.findById(id);
        if (remaining != null && remaining.getPostCount() == 0) {
            tagMapper.deleteById(id);
        }
        log.info("用户文章标签关联已删除 tagId={}", id);
    }

    @Transactional
    public ForumTag renameAdmin(Long id, String rawName, String managedCategory, boolean systemAdmin) {
        ForumTag tag = requireAdminTag(id, managedCategory, systemAdmin);
        String name = validateName(rawName);
        ensureUnique(name, tag.getCategory(), id);
        tag.setName(name);
        tag.setUpdatedAt(LocalDateTime.now());
        tagMapper.update(tag);
        return tag;
    }

    @Transactional
    public void deleteAdmin(Long id, String managedCategory, boolean systemAdmin) {
        requireAdminTag(id, managedCategory, systemAdmin);
        tagMapper.deletePostTagsByTagId(id);
        tagMapper.deleteById(id);
        log.info("管理员已删除论坛标签 id={}", id);
    }

    private ForumTag requirePersonalTag(Long id, String username) {
        ForumTag tag = get(id);
        if (!tagMapper.isUsedByAuthor(id, username)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
        return tag;
    }

    private ForumTag requireAdminTag(Long id, String managedCategory, boolean systemAdmin) {
        ForumTag tag = get(id);
        if (!systemAdmin && (tag.getCategory() == null || !tag.getCategory().equals(managedCategory))) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
        return tag;
    }

    private ForumTag get(Long id) {
        ForumTag tag = tagMapper.findById(id);
        if (tag == null) {
            throw new NotFoundException(ErrorCode.FORUM_TAG_NOT_FOUND, ErrorMessages.FORUM_TAG_NOT_FOUND);
        }
        return tag;
    }

    private String validateName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new BusinessException(ErrorCode.FORUM_TAG_NAME_INVALID, ErrorMessages.FORUM_TAG_NAME_INVALID);
        }
        String name = rawName.trim();
        if (name.length() > MAX_TAG_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.FORUM_TAG_NAME_INVALID, ErrorMessages.FORUM_TAG_NAME_INVALID);
        }
        return name;
    }

    private void ensureUnique(String name, String category, Long currentId) {
        ForumTag duplicate = tagMapper.findByNameIgnoreCaseAndCategory(name, category);
        if (duplicate != null && !duplicate.getId().equals(currentId)) {
            throw new BusinessException(ErrorCode.FORUM_TAG_DUPLICATE, ErrorMessages.FORUM_TAG_DUPLICATE);
        }
    }
}
