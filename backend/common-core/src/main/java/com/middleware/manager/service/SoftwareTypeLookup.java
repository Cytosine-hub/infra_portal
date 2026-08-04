package com.middleware.manager.service;

import com.middleware.manager.domain.SoftwareType;

import java.util.List;

public interface SoftwareTypeLookup {
    List<SoftwareType> findActive();

    SoftwareType get(Long id);

    List<SoftwareType> findByIds(List<Long> ids);

    List<SoftwareType> findByCategory(String category);

    SoftwareType resolveOrCreate(String category, String name);
}
