package com.middleware.manager.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForumTagManagementMigrationTest {
    @Test
    @DisplayName("TC-FORUM-TAG-004/005/006 迁移增加所属组并保留基于标签ID的文章关联")
    void migrationAddsCategoryAndMapperDeletesAssociationsByTagId() throws IOException {
        String migration = resource("/db/migration/V20260805__forum_tag_management.sql");
        String mapper = resource("/mapper/ForumTagMapper.xml");

        assertTrue(migration.contains("ADD COLUMN category"));
        assertTrue(migration.contains("UPDATE forum_tags SET category = '未分组'"));
        assertTrue(migration.contains("uk_forum_tag_category_name (category, name)"));
        assertTrue(mapper.contains("WHERE fpt.post_id = #{postId}"));
        assertTrue(mapper.contains("DELETE FROM forum_post_tags WHERE tag_id = #{tagId}"));
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("测试资源不存在: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
