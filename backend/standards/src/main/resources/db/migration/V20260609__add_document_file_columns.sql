-- 标准文档表：新增原始文件存储字段
ALTER TABLE standard_documents
    ADD COLUMN stored_file_name VARCHAR(255) DEFAULT NULL COMMENT '存储的文件名' AFTER previous_content,
    ADD COLUMN original_file_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名' AFTER stored_file_name;

-- 创建文档存储目录（应用启动时自动创建，此处仅为说明）
-- 物理路径：{app.storage.location}/documents/
