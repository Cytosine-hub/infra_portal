package com.middleware.manager.knowledge.service;

import com.middleware.manager.repository.StandardParameterLookupMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 参数标准精确查询。
 * <p>「innodb_buffer_pool_size 标准值是多少」这类问题必须 100% 准确且可追责，
 * RAG 只能给出语义相近的片段，做不到。这里直接查 standard_parameters，返回带
 * 标准版本号与发布时间的确定答案，完全绕开向量检索。
 * <p>这也是「12 种中间件 + 10 种数据库参数标准」这个场景收益最高的一条路径：
 * 参数类问题大概率占日常查询的一半以上。
 */
@Service
@Slf4j
public class ParameterLookupService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;

    private final StandardParameterLookupMapper mapper;

    public ParameterLookupService(StandardParameterLookupMapper mapper) {
        this.mapper = mapper;
    }

    public List<ParameterAnswer> lookup(String software, String name, int limit) {
        String safeSoftware = trimToNull(software);
        String safeName = trimToNull(name);
        // 两个条件都空会退化成全表扫描，直接拒绝
        if (safeSoftware == null && safeName == null) {
            return List.of();
        }
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return mapper.search(safeSoftware, safeName, safeLimit).stream()
                .map(ParameterAnswer::from)
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 对外返回结构，与行映射解耦，便于后续调整字段而不动 Mapper。 */
    public static class ParameterAnswer {
        private String code;
        private String name;
        private String value;
        private String paramType;
        private String valueRange;
        private String description;
        private boolean deploymentStandard;
        private String software;
        private String standardTitle;
        private String standardVersion;
        private String publishedAt;

        static ParameterAnswer from(ParameterAnswerRow row) {
            ParameterAnswer a = new ParameterAnswer();
            a.code = row.getCode();
            a.name = row.getName();
            a.value = row.getValue();
            a.paramType = row.getParamType();
            a.valueRange = row.getValueRange();
            a.description = row.getDescription();
            a.deploymentStandard = row.isDeploymentStandard();
            a.software = row.getSoftware();
            a.standardTitle = row.getStandardTitle();
            a.standardVersion = row.getStandardVersion();
            a.publishedAt = row.getPublishedAt();
            return a;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getParamType() { return paramType; }
        public void setParamType(String paramType) { this.paramType = paramType; }
        public String getValueRange() { return valueRange; }
        public void setValueRange(String valueRange) { this.valueRange = valueRange; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isDeploymentStandard() { return deploymentStandard; }
        public void setDeploymentStandard(boolean v) { this.deploymentStandard = v; }
        public String getSoftware() { return software; }
        public void setSoftware(String software) { this.software = software; }
        public String getStandardTitle() { return standardTitle; }
        public void setStandardTitle(String standardTitle) { this.standardTitle = standardTitle; }
        public String getStandardVersion() { return standardVersion; }
        public void setStandardVersion(String v) { this.standardVersion = v; }
        public String getPublishedAt() { return publishedAt; }
        public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    }
}
