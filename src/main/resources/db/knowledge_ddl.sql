CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL COMMENT '切片文本内容',
    source_title VARCHAR(500) COMMENT '来源文档标题',
    source_type VARCHAR(50) COMMENT '来源类型：STANDARD_DOC / UPLOAD',
    source_id BIGINT COMMENT '来源文档ID',
    category VARCHAR(80) COMMENT '分类',
    software VARCHAR(120) COMMENT '软件名称',
    chunk_index INT DEFAULT 0 COMMENT '切片在文档中的序号',
    vector_id VARCHAR(100) COMMENT '向量存储ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_source (source_type, source_id),
    INDEX idx_category (category),
    INDEX idx_software (software)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文本切片';

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) COMMENT '会话标题',
    mode VARCHAR(10) DEFAULT 'rag' COMMENT '会话模式：rag / ops',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话';

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user / assistant / system',
    content TEXT NOT NULL COMMENT '消息内容',
    references_text TEXT COMMENT '引用的知识来源JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息';

-- 参数标准表
CREATE TABLE IF NOT EXISTS parameter_standards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(160) NOT NULL COMMENT '标准标题',
    category VARCHAR(60) COMMENT '分类',
    software VARCHAR(120) COMMENT '软件名称',
    software_type_id BIGINT COMMENT '软件类型ID',
    software_version VARCHAR(80) COMMENT '软件版本',
    standard_version VARCHAR(80) COMMENT '标准版本',
    code VARCHAR(20) COMMENT '编码',
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    content TEXT NOT NULL COMMENT '内容（Markdown）',
    rendered_content TEXT COMMENT '渲染后内容',
    published_at DATETIME COMMENT '发布时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参数标准';

-- 数据迁移：将 STANDARD 类型文档迁移到新表
INSERT IGNORE INTO parameter_standards (id, title, category, software, software_type_id, software_version, standard_version, code, status, content, rendered_content, published_at, created_at, updated_at)
SELECT id, title, category, software, software_type_id, software_version, standard_version, code, status, content, rendered_content, published_at, created_at, updated_at
FROM standard_documents WHERE document_type = 'STANDARD';

-- 更新 StandardParameter 关联
ALTER TABLE standard_parameters ADD COLUMN IF NOT EXISTS parameter_standard_id BIGINT COMMENT '关联的参数标准ID';
UPDATE standard_parameters SET parameter_standard_id = standard_document_id WHERE parameter_standard_id IS NULL AND standard_document_id IN (SELECT id FROM parameter_standards);

-- 删除旧的 STANDARD 类型文档
DELETE FROM standard_documents WHERE document_type = 'STANDARD';

-- 参数标准添加审核支持（列不存在时执行）
-- ALTER TABLE parameter_standards ADD COLUMN pending_review_record_id BIGINT COMMENT '待审核记录ID';
-- ALTER TABLE parameter_standards ADD COLUMN previous_content TEXT COMMENT '修改前内容备份';
-- ALTER TABLE parameter_standards ADD COLUMN version VARCHAR(20) COMMENT '版本号';
