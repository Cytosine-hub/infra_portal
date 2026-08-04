-- wiki_pages 全文索引改用 ngram 解析器（2026-07-30）
--
-- 问题：ft_content 用的是 MySQL 默认全文解析器，按空格和标点切词。中文句子没有
-- 空格，「MySQL主从延迟处理方案」会被当成一个 token，搜「主从延迟」完全匹配不到。
-- 经验页面的全文检索这一路一直是失效的。
--
-- 修法：改用 MySQL 5.7.6+ 内置的 ngram 解析器（默认 ngram_token_size=2，即二元切分）。
-- 二元切分对中文是粗糙但有效的方案：「主从延迟」切成「主从/从延/延迟」，能召回。
--
-- 注意：
--   1. ngram_token_size 是服务端启动参数，默认 2。若要改需在 my.cnf 设置并重启，
--      且改动后所有 ngram 索引都要重建。保持默认 2 即可。
--   2. 重建索引期间该表的全文检索不可用，数据量小时通常几秒完成。
--   3. 检查是否已生效：
--      SELECT * FROM information_schema.INNODB_FT_INDEX_TABLE LIMIT 5;
--
-- 可重复执行。

SET @has_index := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'wiki_pages' AND index_name = 'ft_content'
);
SET @drop_sql := IF(@has_index > 0, 'ALTER TABLE wiki_pages DROP INDEX ft_content', 'SELECT 1');
PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE wiki_pages
    ADD FULLTEXT INDEX ft_content (title, summary, content) WITH PARSER ngram;
