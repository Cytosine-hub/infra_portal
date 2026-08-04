-- 论坛标题与正文全文索引。脚本可重复执行。
-- 中文分词依赖 MySQL 全局参数 ngram_token_size，默认值为 2。
SET @forum_fulltext_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'forum_posts'
      AND index_name = 'ft_forum_posts_title_content'
);

SET @forum_fulltext_ddl = IF(
    @forum_fulltext_index_exists = 0,
    'ALTER TABLE forum_posts ADD FULLTEXT INDEX ft_forum_posts_title_content (title, content) WITH PARSER ngram',
    'SELECT 1'
);

PREPARE forum_fulltext_statement FROM @forum_fulltext_ddl;
EXECUTE forum_fulltext_statement;
DEALLOCATE PREPARE forum_fulltext_statement;
