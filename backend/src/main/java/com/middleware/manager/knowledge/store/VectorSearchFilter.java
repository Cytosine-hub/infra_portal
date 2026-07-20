package com.middleware.manager.knowledge.store;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class VectorSearchFilter {
    private final Set<String> sources = new LinkedHashSet<>();
    private final Set<String> categories = new LinkedHashSet<>();
    private final Set<String> software = new LinkedHashSet<>();
    private final Set<String> sourceTypes = new LinkedHashSet<>();
    private final Set<String> sourceIds = new LinkedHashSet<>();
    private final Set<String> statuses = new LinkedHashSet<>();

    public static VectorSearchFilter none() {
        return new VectorSearchFilter();
    }

    public boolean isEmpty() {
        return sources.isEmpty()
                && categories.isEmpty()
                && software.isEmpty()
                && sourceTypes.isEmpty()
                && sourceIds.isEmpty()
                && statuses.isEmpty();
    }

    public boolean matches(Map<String, String> metadata) {
        if (isEmpty()) {
            return true;
        }
        if (metadata == null) {
            return false;
        }
        return matchesString(sources, metadata.get("source"))
                && matchesString(categories, metadata.get("category"))
                && matchesString(software, metadata.get("software"))
                && matchesString(sourceTypes, metadata.get("sourceType"))
                && matchesString(sourceIds, metadata.get("sourceId"))
                && matchesString(statuses, metadata.get("status"));
    }

    public VectorSearchFilter addSource(String source) {
        addValue(sources, source);
        return this;
    }

    public VectorSearchFilter addCategory(String category) {
        addValue(categories, category);
        return this;
    }

    public VectorSearchFilter addSoftware(String softwareName) {
        addValue(software, softwareName);
        return this;
    }

    public VectorSearchFilter addSourceType(String sourceType) {
        addValue(sourceTypes, sourceType);
        return this;
    }

    public VectorSearchFilter addSourceId(Long sourceId) {
        if (sourceId != null) {
            sourceIds.add(String.valueOf(sourceId));
        }
        return this;
    }

    public VectorSearchFilter addStatus(String status) {
        addValue(statuses, status);
        return this;
    }

    public Set<String> getSources() {
        return Set.copyOf(sources);
    }

    public Set<String> getCategories() {
        return Set.copyOf(categories);
    }

    public Set<String> getSoftware() {
        return Set.copyOf(software);
    }

    public Set<String> getSourceTypes() {
        return Set.copyOf(sourceTypes);
    }

    public Set<String> getSourceIds() {
        return Set.copyOf(sourceIds);
    }

    public Set<String> getStatuses() {
        return Set.copyOf(statuses);
    }

    private boolean matchesString(Collection<String> expected, String actual) {
        return expected.isEmpty() || expected.contains(normalize(actual));
    }

    private void addValue(Set<String> values, String value) {
        String normalized = normalize(value);
        if (!normalized.isEmpty()) {
            values.add(normalized);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
