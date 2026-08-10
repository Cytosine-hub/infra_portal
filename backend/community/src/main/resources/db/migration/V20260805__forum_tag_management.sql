ALTER TABLE forum_tags
    DROP INDEX name,
    ADD COLUMN category VARCHAR(100) NULL AFTER post_count,
    ADD COLUMN created_by VARCHAR(100) NULL AFTER category,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

UPDATE forum_tags SET category = '未分组' WHERE category IS NULL OR TRIM(category) = '';

ALTER TABLE forum_tags
    MODIFY COLUMN category VARCHAR(100) NOT NULL,
    ADD UNIQUE KEY uk_forum_tag_category_name (category, name),
    ADD KEY idx_forum_tag_category (category);
