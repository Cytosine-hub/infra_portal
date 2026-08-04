package com.middleware.manager.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class CatalogSoftwareTypeProtocol {
    public static final String BASE_PATH = "/api/internal/catalog/software-types";
    public static final String BY_IDS_PATH = "/by-ids";
    public static final String BY_CATEGORY_PATH = "/by-category";
    public static final String ACTIVE_PATH = "/active";
    public static final String RESOLVE_PATH = "/resolve";
    public static final String BY_IDS_OPERATION = "catalog.software-types.by-ids";
    public static final String BY_CATEGORY_OPERATION = "catalog.software-types.by-category";
    public static final String ACTIVE_OPERATION = "catalog.software-types.active";
    public static final String RESOLVE_OPERATION = "catalog.software-types.resolve";

    private CatalogSoftwareTypeProtocol() {
    }

    public static String idsPayload(List<Long> ids) {
        if (ids == null) {
            return "";
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public static String categoryPayload(String category) {
        return encoded(category);
    }

    public static String resolvePayload(String category, String name) {
        return encoded(category) + encoded(name);
    }

    private static String encoded(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() + ":" + normalized;
    }
}
