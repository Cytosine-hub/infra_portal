CREATE TABLE IF NOT EXISTS api_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(60) NOT NULL COMMENT '用户账号',
    method VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
    path VARCHAR(500) NOT NULL COMMENT '请求路径',
    query_string VARCHAR(500) COMMENT '查询参数',
    status_code INT COMMENT '响应状态码',
    ip_address VARCHAR(50) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT '用户代理',
    duration_ms BIGINT COMMENT '请求耗时(毫秒)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_username (username),
    INDEX idx_created_at (created_at),
    INDEX idx_path (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API操作审计日志';
