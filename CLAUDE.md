# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Backend (Spring Boot 3.5.3, Java 17)
mvn clean package -DskipTests        # compile
mvn spring-boot:run                  # run (starts on :8080)

# Frontend (Vue 3 + Vite)
cd frontend && npm install && npm run dev   # starts on :5173, proxies /api and /files to :8080
```

Database: MySQL 8.0 at `127.0.0.1:3306/middleware_resource_manager`, user `root`. Credentials in `~/.my.cnf`.

**Knowledge module** (`knowledge/`) — 独立的知识库和 AI 排查模块，使用 JdbcTemplate（非 JPA）：
- `config/` — AiConfig（AI 模型和向量数据库配置）
- `client/` — LlmClient（已被 LangChain4j 的 ChatLanguageModel 替代，调用 OpenAI 兼容格式的大模型 API）
- `loader/` — 文档加载器（Markdown、Tika PDF/Word、StandardDocument）
- `splitter/` — TextSplitter（按 Markdown 标题和段落切分文档）
- `embedding/` — EmbeddingService（调用模型 embedding 接口）
- `store/` — VectorStore 接口 + InMemoryVectorStore（开发用）
- `retriever/` — HybridRetriever（向量 + 关键词混合检索）
- `entity/` — KnowledgeChunk POJO
- `agent/` — ChatSession/ChatMessage POJO、TroubleshootAgent（RAG 排查）
- `repository/` — JdbcTemplate 实现的数据访问层
- `service/` — KnowledgeService（知识库核心服务）
- `web/` — KnowledgeController、AgentController

DDL 脚本：`src/main/resources/db/knowledge_ddl.sql`
前端组件：`frontend/src/components/KnowledgePanel.vue`、`DiagnosticsPanel.vue`

## Architecture

**Backend** — standard Spring Boot layered architecture:
- `domain/` — Lombok POJOs mapped to MySQL tables
- `repository/` — MyBatis Mapper interfaces + XML mapping files (`resources/mapper/`)
- `service/` — business logic
- `web/api/` — REST controllers (`/api/admin/**`, `/api/public/**`, `/api/auth/**`, `/api/forum/**`)
- `web/controller/` — Spring MVC view controllers (Thymeleaf SSR pages like `/login`, `/admin/releases`)
- `config/` — SecurityConfig, StorageProperties, WarmupRunner, AccessLogFilter
- `security/` — Role enum + PermissionService
- `web/api/dto/` — request/response DTOs, `web/form/` — form backing objects
- LangChain4j 集成 — 通过 `langchain4j-spring-boot-starter` 和 `langchain4j-open-ai-spring-boot-starter` 提供 LLM 和 Embedding 能力，配置在 `application.yml` 的 `langchain4j` 节点

**Frontend** — single-file Vue 3 SPA (no router library):
- `App.vue` — the entire application: hash-based routing, all admin CRUD, public download/standards pages, forum
- `components/` — DocumentEditor, ForumPostList/Detail/Editor, Pagination, MarkdownHelp
- `api.js` — thin fetch wrapper with HTTP Basic Auth (SHA-256 hashed password stored in sessionStorage)

## Auth & RBAC

HTTP Basic Auth. Password stored as `{bcrypt}...` in `admin_accounts` table. Frontend SHA-256 hashes the password before base64-encoding for the Authorization header.

**8 roles** (from `security/Role.java`):

| Role | Authority | Managed Category | Capabilities |
|------|-----------|-----------------|--------------|
| 系统管理员 | `ROLE_SYS_ADMIN` | * (all) | Full access, user management |
| 中间件管理岗 | `ROLE_MIDDLEWARE_MGR` | 中间件 | Admin CRUD for middleware category |
| 数据库管理岗 | `ROLE_DATABASE_MGR` | 数据库 | Admin CRUD for database category |
| 主机管理岗 | `ROLE_HOST_MGR` | 主机 | Admin CRUD for host category |
| 网络管理岗 | `ROLE_NETWORK_MGR` | 网络 | Admin CRUD for network category |
| 网络安全岗 | `ROLE_SECURITY_MGR` | 安全 | Admin CRUD for security category |
| 开发经理 | `ROLE_DEV_MGR` | — | Read-only, redirected to home |
| 运维经理 | `ROLE_OPS_MGR` | — | Read-only, redirected to home |

Managers are scoped to their category — they can only manage releases/types/standards within their category. `PermissionService.getManagedCategory()` enforces this.

## Key domain model relationships

- `SoftwareCategory` — simple name list (中间件, 数据库, 主机, 安全, 网络...)
- `SoftwareType` — belongs to a category, has name; unique on (category, name)
- `ReleaseAsset` — a published download file; links to SoftwareType; stored in `./storage/<middlewareName>/`
- `ParameterStandard` — 独立的参数标准实体，含版本管理（草稿→审核→发布→修改），表 `parameter_standards`
- `StandardParameter` — key-value params attached to a ParameterStandard (e.g., `{{JDK_VERSION}}`)，表 `standard_parameters`
- `StandardDocument` — MANUAL or ARTICLE; links to SoftwareType and ParameterStandard; 表 `standard_documents`
- `ReviewRecord` — 审核记录，关联 ParameterStandard 或 StandardDocument; 表 `review_records`
- `ForumPost` / `ForumComment` / `ForumTag` / `PostLike` — simple forum
- `AdminAccount` — users with role, password hash, display name

## API patterns

- Admin APIs (`/api/admin/**`) require role-based access; see SecurityConfig for exact role mappings
- Public APIs (`/api/public/**`) are unauthenticated
- File downloads require login (`/files/**` → authenticated)
- Frontend fetches through Vite proxy, so API paths are relative (`/api/...`)

## Configuration

`src/main/resources/application.yml` — single config file. DB connection defaults can be overridden via env vars: `APP_DB_HOST`, `APP_DB_PORT`, `APP_DB_NAME`, `APP_DB_USERNAME`, `APP_DB_PASSWORD`.

**External Service Configuration:**

All external service addresses must be configurable via environment variables:

| Service | Env Var | Default | Description |
|---------|---------|---------|-------------|
| Zabbix | `ZABBIX_URL` | `http://localhost:8080/api_jsonrpc.php` | Zabbix API URL |
| Zabbix | `ZABBIX_USERNAME` | `Admin` | Zabbix username |
| Zabbix | `ZABBIX_PASSWORD` | `zabbix` | Zabbix password |
| Zabbix | `ZABBIX_TIMEOUT` | `30` | Connection timeout (seconds) |
| AI Model | `AI_BASE_URL` | `https://token-plan-cn.xiaomimimo.com/v1` | LLM API URL |
| AI Model | `AI_API_KEY` | (configured) | LLM API key |
| Vector DB | `VECTOR_HOST` | `localhost` | Milvus host |
| Vector DB | `VECTOR_PORT` | `19530` | Milvus port |

## Zabbix Integration

**Module**: `agent/zabbix/` — Zabbix monitoring data integration

- `ZabbixConfig` — Configuration properties for Zabbix connection
- `ZabbixClient` — Zabbix API client (JSON-RPC 2.0)
- `ZabbixTool` — Agent tool for querying Zabbix data
- `ZabbixExportTool` — Agent tool for exporting data to Excel
- `ExcelExportService` — Excel file generation service

**API Endpoints:**

- `POST /api/ops-agent/chat` — Agent conversation (includes Zabbix queries)
- `GET /api/ops-agent/export/zabbix` — Export Zabbix data to Excel
- `POST /api/ops-agent/export/zabbix/batch` — Batch export multiple hosts

**Skill**: `zabbix-monitor.yaml` — Auto-triggered by keywords: zabbix, 监控, 监控数据, 主机监控, 性能监控, 导出监控

**Documentation:**
- `docs/zabbix-integration-guide.md` — Complete integration guide
- `docs/zabbix-implementation-summary.md` — Implementation summary
