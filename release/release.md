# Release v1.1.0-20260608

**日期**: 2026-06-08
**分支**: feature/ops-agent
**发布范围**: full（前端 + 后端 + 数据库 + 文档）
**类型**: 全量发布（基线版本）

**主要变更**:
- feat: Agent 工具列表增加 description 字段
- feat: Wiki 搜索工具集成到 Agent
- feat: Wiki 知识图谱导出
- feat: 常用命令模块（Kafka/RabbitMQ/Zookeeper/RocketMQ/Java容器）
- fix: 智能排查 SSE 错误处理和加载状态
- fix: Agent 会话 created_by 硬化
- fix: Wiki 导出源引用修复
- refactor: Wiki Ingest/Lint Agent 生产化加固
- refactor: 数据库表结构完善（32 张表）

## 部署说明

### 新环境部署

1. 创建数据库：
```sql
CREATE DATABASE middleware_resource_manager DEFAULT CHARACTER SET utf8mb4;
```

2. 导入表结构：
```sql
mysql -u root middleware_resource_manager < db/full_schema.sql
```

3. 导入种子数据：
```sql
mysql -u root middleware_resource_manager < db/seed_data.sql
```

4. 部署后端和前端（参见 docs/production-deploy.md）

### 存量环境升级

1. 备份数据库：
```sql
mysqldump -u root middleware_resource_manager > backup_20260608.sql
```

2. 对比表结构差异，手动添加缺失列

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 107M |
| backend/application.yml.example | 2.9 KB |
| frontend/index.html | 4.0K |
| frontend/assets/ | 756K |
| db/full_schema.sql |  24K |
| db/seed_data.sql |  32K |
| docs/startup-manual.md | 8.0K |
| docs/production-deploy.md |  12K |

## 数据库表清单（32 张）

| 类别 | 表名 | 说明 |
|------|------|------|
| 核心 | admin_accounts | 管理员账号 |
| 核心 | software_categories | 软件分类 |
| 核心 | software_types | 软件类型 |
| 核心 | release_assets | 发布资源 |
| 核心 | standard_documents | 标准文档 |
| 核心 | standard_parameters | 标准参数 |
| 核心 | parameter_standards | 参数标准 |
| 核心 | review_records | 审核记录 |
| 核心 | roles | 角色 |
| 核心 | system_settings | 系统设置 |
| 核心 | user_tokens | 用户令牌 |
| 核心 | document_revisions | 文档修订历史 |
| 命令 | middleware_commands | 常用命令 |
| 论坛 | forum_posts | 论坛帖子 |
| 论坛 | forum_comments | 论坛评论 |
| 论坛 | forum_tags | 论坛标签 |
| 论坛 | forum_post_tags | 帖子标签关联 |
| 论坛 | forum_post_likes | 帖子点赞 |
| AI | chat_sessions | AI 对话会话 |
| AI | chat_messages | AI 对话消息 |
| AI | agent_tool_invocations | Agent 工具调用审计 |
| 知识库 | knowledge_chunks | 知识库切片 |
| Wiki | wiki_pages | Wiki 页面 |
| Wiki | wiki_links | Wiki 页面关系 |
| Wiki | wiki_sources | Wiki 原始文档 |
| Wiki | wiki_ingest_tasks | Wiki 编译任务 |
| Wiki | wiki_ingest_log | Wiki 编译日志 |
| Wiki | wiki_lint_results | Wiki 质量检查 |
| Wiki | wiki_audit_log | Wiki 操作审计 |
| Wiki | wiki_page_permissions | Wiki 页面权限 |
| Wiki | wiki_access_requests | Wiki 访问申请 |
