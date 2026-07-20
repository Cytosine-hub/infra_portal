-- 智能排查会话权限升级（2026-06-07）
-- 可重复运行；通过 information_schema 判断列和索引是否存在。

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_sessions'
      AND column_name = 'created_by'
);
SET @col_sql := IF(
    @col_exists = 0,
    'ALTER TABLE chat_sessions ADD COLUMN created_by BIGINT COMMENT ''创建人用户ID'' AFTER mode',
    'SELECT 1'
);
PREPARE stmt FROM @col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_sessions'
      AND index_name = 'idx_chat_sessions_created_by'
);
SET @idx_sql := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_chat_sessions_created_by ON chat_sessions (created_by)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
