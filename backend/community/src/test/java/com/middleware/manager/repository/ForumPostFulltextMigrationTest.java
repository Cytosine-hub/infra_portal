package com.middleware.manager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForumPostFulltextMigrationTest {

    private String resource(String resourcePath) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(input).as("资源应存在: %s", resourcePath).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("TC-COMMUNITY-008 论坛迁移为标题和正文创建 ngram FULLTEXT 索引")
    void migrationCreatesNgramFulltextIndex() throws IOException {
        String migration = resource("db/migration/V20260721__add_forum_post_fulltext_index.sql");

        assertThat(migration).containsIgnoringCase("FULLTEXT")
                .contains("forum_posts")
                .contains("title, content")
                .containsIgnoringCase("WITH PARSER ngram")
                .contains("ngram_token_size")
                .contains("默认值为 2");
    }

    @Test
    @DisplayName("TC-COMMUNITY-009 岗位筛选不应生成依赖全文索引的 MATCH 条件")
    void mapperOnlyUsesFulltextForKeywordSearch() throws IOException {
        String mapper = resource("mapper/ForumPostMapper.xml");

        assertThat(mapper).contains("<if test=\"keyword != null\">")
                .contains("MATCH(p.title, p.content)");
    }

    @Test
    @DisplayName("TC-COMMUNITY-010 全文索引迁移应可重复执行")
    void migrationChecksExistingIndexBeforeAlter() throws IOException {
        String migration = resource("db/migration/V20260721__add_forum_post_fulltext_index.sql");

        assertThat(migration).containsIgnoringCase("information_schema.statistics")
                .contains("ft_forum_posts_title_content")
                .containsIgnoringCase("PREPARE")
                .containsIgnoringCase("DEALLOCATE PREPARE");
    }
}
