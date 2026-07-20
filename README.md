# Middleware Resource Manager

基于 `Spring Boot 3.5 + Java 17 + MySQL 8.0` 的中间件文件管理平台，集成 Wiki 知识库、AI 智能排查和运维 Agent。

## 功能模块

### 资源管理
- 管理员登录后台维护版本信息
- 上传中间件安装包并记录文件元数据
- 文件按中间件名称自动归档到本地目录
- 维护版本号、平台、发布日期、版本说明、发布状态
- 自动生成公开详情页和下载直链
- 统计下载次数

### 参数标准
- 参数标准管理：创建、编辑、发布、版本管理（草稿→审核→发布→修改）
- 标准文档管理：手册/文章编写，关联参数标准，支持 `{{参数名}}` 占位符自动替换
- 审核流程：提交审核、审批通过/驳回，审核时可对比参数值变更差异
- 标准发布页面：按分类展示已发布的参数标准和关联手册

### Wiki 知识库
- **目录驱动编译**：PDF/Word/Markdown 文档自动编译为结构化 Wiki 页面
- **质量门禁**：章节覆盖率、过度压缩、泛化标题、短页面等多维度质量检测
- **增量重编译**：支持重编译缺失章节和过度压缩页面，复用已有中间产物
- **任务控制**：支持暂停/继续编译任务，批次间检查暂停状态
- **中间产物持久化**：section_facts、page_plan、quality_report 自动保存
- **知识图谱**：5 信号评分 + 软件类型社区聚类，按权重限边展示
- **Lint 检测**：断链、孤立页面、过期内容、重复标题等自动检测

### 智能排查
- 基于知识库的 RAG 对话，AI 辅助故障诊断和排查建议
- 工具调用：Zabbix 监控数据查询、日志检索、命令执行

### 论坛
- 发帖、评论、点赞、标签分类

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.5.3, Spring MVC, Spring Security, MyBatis |
| 前端 | Vue 3 + Vite, force-graph（知识图谱） |
| AI/LLM | LangChain4j, OpenAI 兼容 API |
| 向量库 | Milvus |
| 数据库 | MySQL 8.0 |
| 运行时 | Java 17 |

## 快速启动

### 后端

```bash
cd backend
mvn clean package -DskipTests
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

### 依赖服务

- MySQL 8.0：`127.0.0.1:3306`
- Milvus（向量数据库）：`localhost:19530`

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `APP_DB_HOST` | `127.0.0.1` | 数据库地址 |
| `APP_DB_PORT` | `3306` | 数据库端口 |
| `APP_DB_NAME` | `middleware_resource_manager` | 数据库名 |
| `APP_DB_USERNAME` | `root` | 数据库用户 |
| `APP_DB_PASSWORD` | — | 数据库密码 |
| `AI_BASE_URL` | `https://token-plan-cn.xiaomimimo.com/v1` | LLM API 地址 |
| `AI_API_KEY` | — | LLM API Key |
| `AI_MODEL` | `mimo-v2.5-pro` | 模型名称 |
| `VECTOR_HOST` | `localhost` | Milvus 地址 |
| `VECTOR_PORT` | `19530` | Milvus 端口 |
| `ZABBIX_URL` | `http://localhost:8080/api_jsonrpc.php` | Zabbix API 地址 |

## 访问地址

| 页面 | 地址 |
|------|------|
| 前端（开发） | `http://localhost:5173` |
| 门户首页 | `http://localhost:5173/#/home` |
| 下载中心 | `http://localhost:5173/#/downloads` |
| 标准发布 | `http://localhost:5173/#/standards` |
| Wiki 知识库 | `http://localhost:5173/#/wiki` |
| 论坛 | `http://localhost:5173/#/forum` |
| 知识库 | `http://localhost:5173/#/knowledge` |
| 智能排查 | `http://localhost:5173/#/diagnostics` |
| 管理后台 | `http://localhost:5173/#/admin` |

## 管理员账号

系统预设了以下管理员账号（密码均为 `admin123`）：

| 用户名 | 角色 | 显示名称 |
|--------|------|----------|
| `sysadmin` | 系统管理员 | 系统管理员 |
| `mwadmin` | 中间件管理岗 | 中间件管理员 |
| `dbadmin` | 数据库管理岗 | 数据库管理员 |
| `hostadmin` | 主机管理岗 | 主机管理员 |
| `netadmin` | 网络管理岗 | 网络管理员 |
| `secadmin` | 网络安全岗 | 安全管理员 |
| `devmgr` | 开发经理 | 开发经理 |
| `opsmgr` | 运维经理 | 运维经理 |

## 数据库初始化

```bash
# 创建数据库
CREATE DATABASE IF NOT EXISTS middleware_resource_manager
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 导入表结构
mysql -u root middleware_resource_manager < db/init.sql

# 导入种子数据（可选）
mysql -u root middleware_resource_manager < db/seed_data.sql
```

## 项目结构

```
├── backend/                     # 后端 Spring Boot 工程（Maven，与 frontend/ 平级）
│   ├── src/main/java/com/middleware/manager/
│   │   ├── wiki/                    # Wiki 知识库模块
│   │   │   ├── service/             # IngestAgent, LinkResolver, WikiGraphService
│   │   │   ├── repository/          # MyBatis Mapper
│   │   │   ├── entity/              # WikiPage, WikiSource, WikiLink
│   │   │   └── web/                 # WikiController
│   │   ├── agent/                   # 运维 Agent 模块
│   │   │   ├── service/             # AgentService
│   │   │   ├── tool/                # ZabbixTool, SearchTool
│   │   │   └── zabbix/              # ZabbixClient
│   │   ├── knowledge/               # 知识库 & 智能排查模块
│   │   ├── service/                 # 业务服务层
│   │   ├── repository/              # 数据访问层
│   │   ├── security/                # RBAC 权限
│   │   └── config/                  # 配置类
│   └── src/main/resources/
│   │   ├── mapper/                  # MyBatis XML
│   │   ├── db/                      # 数据库脚本
│   │   └── application.yml          # 应用配置
├── frontend/
│   ├── src/components/          # Vue 组件
│   └── src/composables/         # 组合式函数
├── db/                          # 数据库迁移脚本
├── docs/                        # 设计文档
└── release/                     # 发布包
```

## 文档

- `docs/development-standards.md` — 开发规范
- `docs/wiki-ingest-quality-optimization-plan-v2.md` — Wiki 编译优化方案
- `docs/wiki-ingest-quality-issues.md` — Wiki 编译质量问题清单
- `db/wiki_ingest_quality_optimization_20260612.md` — 数据库变更记录
