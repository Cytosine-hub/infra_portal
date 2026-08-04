package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.SoftwareCategory;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.ReleaseAssetMapper;
import com.middleware.manager.repository.SoftwareCategoryMapper;
import com.middleware.manager.repository.SoftwareTypeMapper;
import com.middleware.manager.web.api.dto.SoftwareTypeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SoftwareTypeService implements SoftwareTypeLookup {
    private final SoftwareTypeMapper softwareTypeMapper;
    private final SoftwareCategoryMapper softwareCategoryMapper;
    private final ReleaseAssetMapper releaseAssetMapper;

    public SoftwareTypeService(SoftwareTypeMapper softwareTypeMapper,
                               SoftwareCategoryMapper softwareCategoryMapper,
                               ReleaseAssetMapper releaseAssetMapper) {
        this.softwareTypeMapper = softwareTypeMapper;
        this.softwareCategoryMapper = softwareCategoryMapper;
        this.releaseAssetMapper = releaseAssetMapper;
    }

    public List<SoftwareType> list(boolean activeOnly) {
        return activeOnly
                ? softwareTypeMapper.findByActiveTrueOrderByCategoryAscNameAsc()
                : softwareTypeMapper.findAllByOrderByCategoryAscNameAsc();
    }

    @Override
    public List<SoftwareType> findActive() {
        return softwareTypeMapper.findByActiveTrueOrderByCategoryAscNameAsc();
    }

    public List<String> listCategories() {
        Set<String> names = new LinkedHashSet<>();
        softwareCategoryMapper.findAllByOrderByNameAsc().forEach(category -> names.add(category.getName()));
        names.addAll(softwareTypeMapper.findDistinctCategories().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toList()));
        return new ArrayList<>(names);
    }

    @Transactional
    public void createCategory(String category) {
        ensureCategory(normalizeCategory(category));
    }

    @Override
    public SoftwareType get(Long id) {
        SoftwareType type = softwareTypeMapper.findById(id);
        if (type == null) {
            throw new NotFoundException(ErrorCode.SOFTWARE_TYPE_NOT_FOUND, ErrorMessages.SOFTWARE_TYPE_NOT_FOUND);
        }
        return type;
    }

    @Override
    public List<SoftwareType> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        return normalizedIds.isEmpty() ? List.of() : softwareTypeMapper.findByIds(normalizedIds);
    }

    @Override
    public List<SoftwareType> findByCategory(String category) {
        return softwareTypeMapper.findByCategoryIgnoreCaseOrderByNameAsc(normalizeCategory(category));
    }

    @Override
    @Transactional
    public SoftwareType resolveOrCreate(String category, String name) {
        String normalizedCategory = normalizeCategory(category);
        String normalizedName = normalizeName(name);
        SoftwareType existing = softwareTypeMapper.findByCategoryIgnoreCaseAndNameIgnoreCase(
                normalizedCategory, normalizedName);
        if (existing != null) {
            return existing;
        }

        ensureCategory(normalizedCategory);
        SoftwareType type = new SoftwareType();
        type.setCategory(normalizedCategory);
        type.setName(normalizedName);
        type.setActive(true);
        type.setCreatedAt(LocalDateTime.now());
        type.setUpdatedAt(LocalDateTime.now());
        try {
            softwareTypeMapper.insert(type);
        } catch (DuplicateKeyException exception) {
            SoftwareType concurrent = softwareTypeMapper.findByCategoryIgnoreCaseAndNameIgnoreCase(
                    normalizedCategory, normalizedName);
            if (concurrent != null) {
                return concurrent;
            }
            throw exception;
        }
        log.info("软件类型已按名落地 id={} category={} name={}",
                type.getId(), normalizedCategory, normalizedName);
        return type;
    }

    @Transactional
    public SoftwareType create(SoftwareTypeRequest request) {
        String category = normalizeCategory(request.getCategory());
        String name = normalizeName(request.getName());
        if (softwareTypeMapper.existsByCategoryIgnoreCaseAndNameIgnoreCase(category, name)) {
            throw new BusinessException(ErrorCode.SOFTWARE_TYPE_DUPLICATE, ErrorMessages.SOFTWARE_TYPE_DUPLICATE);
        }

        SoftwareType type = new SoftwareType();
        ensureCategory(category);
        type.setCategory(category);
        type.setName(name);
        type.setDescription(trimToNull(request.getDescription()));
        type.setActive(request.isActive());
        type.setCreatedAt(LocalDateTime.now());
        type.setUpdatedAt(LocalDateTime.now());
        softwareTypeMapper.insert(type);
        log.info("软件类型已创建 id={}", type.getId());
        return type;
    }

    @Transactional
    public SoftwareType update(Long id, SoftwareTypeRequest request) {
        SoftwareType type = get(id);
        String category = normalizeCategory(request.getCategory());
        String name = normalizeName(request.getName());
        SoftwareType existing = softwareTypeMapper.findByCategoryIgnoreCaseAndNameIgnoreCase(category, name);
        if (existing != null && !existing.getId().equals(id)) {
            throw new BusinessException(ErrorCode.SOFTWARE_TYPE_DUPLICATE, ErrorMessages.SOFTWARE_TYPE_DUPLICATE);
        }

        type.setCategory(category);
        ensureCategory(category);
        type.setName(name);
        type.setDescription(trimToNull(request.getDescription()));
        type.setActive(request.isActive());
        type.setUpdatedAt(LocalDateTime.now());
        softwareTypeMapper.update(type);
        log.info("软件类型已更新 id={}", id);
        return type;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        if (releaseAssetMapper.existsBySoftwareTypeId(id)) {
            throw new BusinessException(ErrorCode.SOFTWARE_TYPE_IN_USE, ErrorMessages.SOFTWARE_TYPE_IN_USE);
        }
        softwareTypeMapper.deleteById(id);
        log.info("软件类型已删除 id={}", id);
    }

    private void ensureCategory(String category) {
        if (softwareCategoryMapper.existsByNameIgnoreCase(category)) {
            return;
        }
        insertCategory(category);
    }

    private void insertCategory(String category) {
        SoftwareCategory softwareCategory = new SoftwareCategory();
        softwareCategory.setName(category);
        softwareCategory.setCreatedAt(LocalDateTime.now());
        softwareCategory.setUpdatedAt(LocalDateTime.now());
        try {
            softwareCategoryMapper.insert(softwareCategory);
        } catch (DuplicateKeyException exception) {
            log.debug("软件分类已被并发创建 category={}", category);
        }
    }

    private String normalizeCategory(String category) {
        String value = StringUtils.hasText(category) ? category.trim() : "";
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.PARAM_INVALID);
        }
        return value;
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.PARAM_INVALID);
        }
        return name.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
