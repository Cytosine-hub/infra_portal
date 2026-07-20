-- 标准参数表：将 category 改名为 param_type，新增 value_range 字段
-- 需求：参数类型（文本值/布尔值/数值）+ 取值范围

-- 1. 重命名 category 列为 param_type
ALTER TABLE standard_parameters
    CHANGE COLUMN category param_type VARCHAR(100) DEFAULT NULL COMMENT '参数类型（文本值/布尔值/数值）';

-- 2. 新增 value_range 列
ALTER TABLE standard_parameters
    ADD COLUMN value_range VARCHAR(200) DEFAULT NULL COMMENT '取值范围' AFTER param_type;

-- 3. 回填已有数据：将 NULL 的 param_type 默认设为「文本值」，value_range 设为「-」
UPDATE standard_parameters SET param_type = '文本值' WHERE param_type IS NULL;
UPDATE standard_parameters SET value_range = '-' WHERE value_range IS NULL;
