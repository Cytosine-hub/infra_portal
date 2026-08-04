ALTER TABLE chat_messages
    ADD COLUMN attachments_text TEXT NULL COMMENT '附件元数据JSON，不包含文件正文' AFTER references_text;
