package com.middleware.manager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForumTagMigrationTest {

    @Test
    @DisplayName("TC-FORUM-TAG-010（TC-07）标签迁移应先回填空类别再设置非空唯一约束")
    void migrationBackfillsCategoryBeforeAddingNotNullConstraint() throws IOException {
        String migration = resource("db/migration/V20260805__forum_tag_management.sql");

        int backfillIndex = migration.indexOf("UPDATE forum_tags SET category = '未分组'");
        int notNullIndex = migration.indexOf("MODIFY COLUMN category VARCHAR(100) NOT NULL");
        assertThat(backfillIndex).isGreaterThanOrEqualTo(0);
        assertThat(notNullIndex).isGreaterThan(backfillIndex);
        assertThat(migration).contains("UNIQUE KEY uk_forum_tag_category_name (category, name)");
    }

    @Test
    @DisplayName("TC-FORUM-TAG-011 发帖标签查询应始终限定明确类别")
    void mapperDoesNotExposeGlobalSingleTagLookup() throws IOException {
        String mapper = resource("mapper/ForumTagMapper.xml");

        assertThat(mapper).doesNotContain("id=\"findByNameIgnoreCase\"")
                .contains("id=\"findByNameIgnoreCaseAndCategory\"")
                .contains("AND category = #{category}");
    }

    private String resource(String resourcePath) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(input).as("资源应存在: %s", resourcePath).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
