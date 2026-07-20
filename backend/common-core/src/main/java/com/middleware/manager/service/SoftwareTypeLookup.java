package com.middleware.manager.service;

import com.middleware.manager.domain.SoftwareType;

public interface SoftwareTypeLookup {
    SoftwareType get(Long id);
}
