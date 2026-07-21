# 中间件资源管理平台 — 后端按岗位拆分微服务方案

> 目标：把当前单体后端按「岗位」组织成模块，并演进为微服务架构，与前端已有的岗位化模块对齐。
> 本文档既是给人审阅的设计方案，也是交给实现 Agent（codex gpt-5.6-sol / xhigh）的规格书。
> 进度：阶段 0-5 已完成；阶段 5 已将 Token 校验集中到 Gateway + identity，并由 Gateway 向下游下发签名身份头。落地细节见 `docs/microservices-stage5-gateway-authentication.md`。

---

## 1. 现状盘点（已核实）

- **规模**：`backend/` 单体 Spring Boot 3.5（Java 17），194 个 Java 文件 ~22k 行，16 个测试类；单进程 `:8080`，单 MySQL。
- **技术栈**：Spring Web + Spring Security + MyBatis(+PageHelper) + MySQL；AI 侧 LangChain4j + Milvus + OpenAI 兼容大模型；运维侧 Zabbix JSON-RPC。
- **认证**：Bearer Token（`user_tokens` 表）只由 identity 的 introspect 校验和滑动续期；Gateway 集中调用并注入 HMAC 签名的用户/角色/岗位头；下游 `SecurityConfig` 按原路径授权，`PermissionService` 按签名的「分类(category)」做管理/审批授权。
- **关键发现 — 「分类(category)」即「岗位」**：`roles` 种子里 `managed_category` 正是 5 个岗位：**中间件 / 数据库 / 主机 / 网络 / 安全**，每岗位有「管理员(可审)」+「管理岗(只改)」两级角色；外加系统管理员 / 开发经理 / 运维经理三个横切角色。内容表（`parameter_standards`/`standard_documents`/`knowledge_*`/`wiki_*`）均带 `category` 字段。
- **前端已岗位化**：`frontend/src/modules/{middleware,database,host,network,network-security}`，每岗位模块自带独立 API 基址 `VITE_<岗位>_API_BASE_URL`（默认 `/api`）——**天然为微服务预留了按岗位路由的入口**。
- **岗位内容现状**：中间件有「运维命令」(`/api/middleware-commands`)；数据库有「数据迁移」(前端页面)；主机/网络/安全岗位后端暂无专属端点，内容走通用的 category 打标机制。

### 1.1 现有功能 → 边界上下文归类

| 能力 | 控制器 / 路径 | 主要数据表 | 性质 |
|------|--------------|-----------|------|
| 身份与访问 | AuthApi、AdminUser、AdminAccount、AdminSetting | admin_accounts, roles, user_tokens, system_settings, api_audit_log | **平台横切** |
| 资源目录（软件下载） | Admin/Public Release、SoftwareType、SoftwareCategory | software_categories, software_types, release_assets | 平台横切（可按岗位过滤） |
| 标准与文档 | Admin/Public ParameterStandard、StandardParameter、StandardDocument、DocumentRevision | parameter_standards, standard_parameters, standard_documents, document_revisions | 平台横切（**按 category=岗位 分区**） |
| 评审流 | ReviewApi | review_records | 平台横切 |
| 社区论坛 | ForumController | forum_posts/comments/tags/likes | 平台横切 |
| 知识库 / RAG 排查 | knowledge/* (Knowledge、Agent、KnowledgeGraph) | knowledge_chunks, chat_sessions/messages + Milvus | 平台横切（按 category 分区，AI 基础设施） |
| Wiki 知识图谱 | wiki/* | wiki_pages/sources/links/... | 平台横切（按 category 分区，AI） |
| 运维 Agent(Zabbix) | agent/* (OpsAgent、Export) | agent_tool_invocations | 平台横切 / 运维 |
| **中间件命令** | MiddlewareCommandApi | middleware_commands, middleware_types | **岗位专属：中间件** |
| **数据迁移** | （前端页面，后端待建） | — | **岗位专属：数据库** |

**结论**：现有绝大多数功能是**横切平台能力**，只有极少数是岗位专属。因此正确的拆分是**双轴**：横向的「平台能力服务」+ 纵向的「岗位服务」，而不是简单地把单体劈成 5 个岗位服务。

---

## 2. 目标架构

### 2.1 服务拓扑（推荐）

```
                    ┌─────────────────────────────────────────────┐
   前端岗位模块 ──▶  │            api-gateway (Spring Cloud Gateway) │
  VITE_*_API_BASE   └───────┬──────────────────────────┬──────────┘
                            │                          │
        ┌───────────────────┴──────┐        ┌──────────┴─────────────────────┐
        │   平台能力服务（横切）      │        │   岗位服务（纵向，薄 BFF+专属）   │
        │  core-service             │        │  middleware-service (命令…)     │
        │   (identity+catalog+      │        │  database-service   (迁移…)     │
        │    standards)             │        │  host-service                   │
        │  ai-service               │        │  network-service                │
        │   (knowledge+wiki+agent)  │        │  security-service               │
        │  community-service        │        └─────────────────────────────────┘
        └───────────────────────────┘
   基础设施：注册中心+配置中心(建议 Nacos) · 共享库 common-{core,security,web}
```

- **api-gateway**：统一 `/api/**` 入口，做集中认证、身份头清洗与签名注入、路由、限流。前端岗位模块的 `VITE_*_API_BASE_URL` 指向网关（或经网关按岗位前缀路由到岗位服务）。
- **平台能力服务**：把当前横切能力按边界上下文抽出，服务全岗位；内容按 `category` 分区。
- **岗位服务**：薄服务，承载岗位专属端点（如中间件命令、数据库迁移），并作为该岗位的聚合层(BFF)按需编排平台能力。主机/网络/安全先建空骨架，随业务填充。
- **共享库**：`common-core`(DTO/ApiError/错误码/常量、网关签名协议)、`common-security`(身份头验签、PermissionService、category 上下文)、`common-web`(SecurityConfig/全局异常)。

### 2.2 数据策略

- **一期**：**共享 MySQL + 逻辑归属**——每张表归属唯一服务，服务间**禁止跨库 join**，只能走 API/事件。`category` 作为内容分区维度随数据携带。
- **二期**：按服务物理拆库（`db-per-service`），跨服务一致性用最终一致（事件/对账）。
- 向量库(Milvus)、LLM 和 Zabbix Agent 归 ai-service 专属。

### 2.3 认证与鉴权

- Token 校验集中在 Gateway + identity：Gateway 调用 core-service 内部 introspect，identity 独占 token 表、角色表和滑动续期逻辑。
- Gateway 先删除客户端提交的全部身份头，再注入并 HMAC 签名 `X-User`、`X-Display-Name`、`X-Roles`、`X-Category`、`X-Category-Admin`；下游验签后构建认证上下文，非 Java 服务可实现同一协议。
- `PermissionService` 的 category(岗位) 授权模型在 `common-security` 复用，只读签名上下文，保持「岗位管理员/管理岗」两级语义不变。

---

## 3. 迁移路线（Strangler-Fig，分阶段）

> 原则：**先模块化、后服务化**；每步可构建、可回滚、行为不变；叶子服务先剥离。

- **阶段 0 — 模块化单体（Maven 多模块）** ← 交给 codex 的第一步
  把当前单一 Maven 工程重构为多模块（仍打包成**一个**可启动应用）：
  - `common-core` / `common-security` / `common-web`
  - 平台模块：`identity` `catalog` `standards` `knowledge` `wiki` `community` `ops-agent`
  - **岗位模块**：`job-middleware` `job-database` `job-host` `job-network` `job-security`（镜像前端 `modules/`）
  - 用模块依赖强制边界（岗位模块可依赖平台模块，反之禁止；平台模块之间零依赖或仅依赖 common）。
  - **行为零变化**：端点、路径、鉴权、DB 全不动，`mvn test` 全绿；本地 `mvn spring-boot:run` 正常。
- **阶段 1 — 引入网关 + Nacos（已落地）**：加 `api-gateway`；默认 profile 静态路由到 `app:8081` 以兼容无 Nacos 环境，`cloud` profile 下 app/Gateway 注册到 Nacos、Gateway 通过 `lb://middleware-resource-manager-app` 动态路由；前端 `VITE_*_API_BASE_URL` 指向网关。
- **阶段 2 — community-service 剥离（已落地）**：论坛成为首个独立业务服务，Gateway 按 `/api/forum/**` 路由。
- **阶段 3 — ai-service 剥离（已落地）**：knowledge、wiki、ops-agent 作为紧耦合 AI/Agent 边界上下文整体剥离，Gateway 按原路径路由，避免集群内部远程调用。
- **阶段 4 — core-service 剥离（已落地）**：identity、catalog、standards 因三个内部端口闭环整体剥离，Gateway 按原路径和 `/files/**` 路由，app 仅保留岗位模块。
- **阶段 5 — 网关集中认证（已落地）**：identity 提供 HMAC 保护的内部 introspect；Gateway 集中校验并短 TTL 缓存，清洗和签名注入身份头；下游停止查询 token/角色表，只按签名身份上下文授权。
- **阶段 6 — 数据与运维**：物理拆库、分布式配置、可观测性(链路/日志/指标)、每服务独立 CI/CD。

---

## 4. 交给 codex 的第一个任务（阶段 0，精确范围）

**任务**：将 `backend/` 从单一 Maven 工程重构为**多模块单体**，引入 `common-*` + 平台模块 + 5 个岗位模块，**行为零变化**。

**硬约束（验收）**：
1. `cd backend && mvn -DskipTests clean package` 成功；`mvn test` 全部通过（现有 16 个测试类不改语义、全绿）。
2. `mvn spring-boot:run` 后所有现有 `/api/**` 端点行为、鉴权规则、DB 访问完全不变。
3. 岗位模块目录与前端 `frontend/src/modules/` 一一对应（middleware/database/host/network/security）。
4. 模块边界由 Maven 依赖强制：平台模块不得依赖岗位模块；平台模块间不互相依赖（仅依赖 `common-*`）。
5. 现有 `agent.md` / 代码地图更新为多模块结构；`package-for-deploy.sh` 等脚本适配（仍产出单一可执行 jar）。
6. 提交遵循仓库 `agent.md` 规范；产出一个 PR，PR 描述含模块拆分对照表与验收结果。

**不做**（本阶段边界）：不拆分独立部署单元、不引网关/Nacos、不改数据库、不改 API 契约。

---

## 5. 已确认的关键决策

1. **第一步范围** = **阶段 0：模块化单体**（Maven 多模块，行为零变化，可回滚）。
2. **注册/配置/网关技术栈** = **Nacos**（注册 + 配置中心）——用于阶段 1+，阶段 0 暂不引入。
3. **数据库策略** = **共享库 + 逻辑归属**（一期）——每表归属唯一服务、禁跨服务 join，后续再物理拆库。

> 本次交给 codex 的仅为**阶段 0**，不涉及 Nacos 与数据库改动。Nacos / 拆库在后续阶段落地。
