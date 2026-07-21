# 后端微服务化 · 全景与备忘

> 中间件资源管理平台后端按平台能力与岗位边界拆分后的总览。各阶段详细说明见 `docs/microservices-split-plan.md` 与 `docs/microservices-stage*.md`。

## 概览

| 指标 | 值 |
|------|----|
| 可部署单元 | 9（网关 + 3 个平台能力服务 + 5 个岗位服务） |
| Maven Reactor 项目 | 26 |
| 测试 | 134 · 全绿 |
| 业务端点 | 148 · 路径、鉴权、SQL 不变 |
| DB / 前端改动 | 0 |

运行时为 9 个 Java 进程 + 单 MySQL（共享库 + 逻辑归属，二期再物理拆库）。`app` 已在阶段 6 退役。

## 服务拓扑

```mermaid
graph TD
  FE["前端 5 岗位模块<br/>VITE_*_API_BASE_URL"] --> GW
  GW["api-gateway :8080<br/>统一入口 · 集中认证 · Nacos 路由"]
  GW --> CORE["core-service :8084<br/>identity + catalog + standards"]
  GW --> AI["ai-service :8083<br/>knowledge + wiki + ops-agent"]
  GW --> COMM["community-service :8082<br/>forum"]
  GW --> MW["middleware-service :8085<br/>middleware-commands"]
  DBJOB["database-service :8086<br/>岗位骨架 · /health"]
  HOST["host-service :8087<br/>岗位骨架 · /health"]
  NET["network-service :8088<br/>岗位骨架 · /health"]
  SEC["security-service :8089<br/>岗位骨架 · /health"]
  GW -. introspect 校验 .-> CORE
  NACOS[("Nacos<br/>注册 + 配置")] -.- GW
  NACOS -.- CORE
  NACOS -.- AI
  NACOS -.- COMM
  NACOS -.- MW
  NACOS -.- DBJOB
  NACOS -.- HOST
  NACOS -.- NET
  NACOS -.- SEC
  DB[("MySQL 单库<br/>逻辑归属")] -.- CORE
  DB -.- MW
```

database/host/network/security-service 当前没有业务路径，只在 `cloud` profile 注册 Nacos，不配置网关路由。新增业务端点时必须使用精确路径增加路由，禁止恢复 `/api/**` 兜底路由。

## 服务职责 / 端点 / 数据归属

| 服务 | 端口 | 职责 | 主要对外端点 | 数据表 |
|------|------|------|------|------|
| **api-gateway** | 8080 | 统一入口、集中认证、路由 | `/api/**`（按精确规则转发） | - |
| **community-service** | 8082 | 论坛社区 | `/api/forum/**` | forum_* |
| **ai-service** | 8083 | 知识库 RAG、Wiki、Zabbix Agent | `/api/knowledge/**` `/api/agent/**` `/api/wiki/**` `/api/ops-agent/**` | knowledge_*、wiki_*、agent_tool_invocations |
| **core-service** | 8084 | 身份、资源目录、标准 | `/api/auth/**` `/api/admin/**` `/api/public/**` `/files/**` | admin_accounts、roles、user_tokens、catalog/standards 相关表 |
| **middleware-service** | 8085 | 中间件岗位专属命令 | `/api/middleware-commands/**` | middleware_commands、middleware_types；只读 software_types |
| **database-service** | 8086 | 数据库岗位骨架 | 直连 `/health`，暂无网关业务路由 | 暂无专属表 |
| **host-service** | 8087 | 主机岗位骨架 | 直连 `/health`，暂无网关业务路由 | 暂无专属表 |
| **network-service** | 8088 | 网络岗位骨架 | 直连 `/health`，暂无网关业务路由 | 暂无专属表 |
| **security-service** | 8089 | 网络安全岗位骨架 | 直连 `/health`，暂无网关业务路由 | 暂无专属表 |

## 集中认证流

```mermaid
sequenceDiagram
  participant C as 客户端
  participant G as api-gateway
  participant I as core-service · identity
  participant S as 下游服务
  C->>G: Authorization: Bearer token
  G->>G: 删除客户端身份头
  G->>I: POST /api/auth/introspect + 网关签名
  I-->>G: user / roles / category
  G->>S: 注入身份头 + HMAC 签名
  S->>S: 验签后构建 SecurityContext
```

所有服务复用 `common-security` 与 `common-web`。`GATEWAY_SIGNING_SECRET` 必须由环境变量提供且至少 32 UTF-8 字节，配置文件不提供真实默认值。

## 共享基础库

| 库 | 内容 |
|----|------|
| `common-core` | DTO、错误码、共享模型、跨模块端口契约 |
| `common-security` | 网关身份头验签、HMAC、岗位(category)权限模型 |
| `common-web` | SecurityConfig、访问日志、审计、全局异常处理；公开 `/health` |

## 拆分历程

| 阶段 | 内容 | 状态 |
|------|------|------|
| 0 | 模块化单体 | 已完成 |
| 1 | api-gateway + Nacos 地基 | 已完成 |
| 2 | 剥离 community-service | 已完成 |
| 3 | 剥离 ai-service | 已完成 |
| 4 | 剥离 core-service | 已完成 |
| 5 | 网关集中认证 | 已完成 |
| 6 | 5 个岗位服务独立部署，app 退役 | 已完成 |
| 7 | 物理拆库、可观测性、完整预发集成 | 待实施 |

## 关键不变量

- 9 个可执行 JAR：api-gateway、core-service、ai-service、community-service 与 5 个岗位服务，无 app。
- `/api/middleware-commands/**` 的 Controller、Service、Mapper XML、DDL/DML 均未修改，只变更承载进程和网关目标。
- 148 个业务端点保持不变；四个 `/health` 是运维端点，不计入业务端点集合。
- 默认 profile 所有服务关闭 Nacos；只有 `cloud` profile 注册和读取 Nacos 配置。
- 服务间零编译依赖；部署服务只依赖 `common-*` 与自身能力模块。
- 仓库不存真实密钥。DB、Nacos、AI、Zabbix 与网关签名凭据均通过环境变量注入。

## 后续待办

- 注册 GitLab Runner，并补全 `integration:e2e` 的真实 Nacos/MySQL 多进程验证。
- 按服务物理拆库，建立跨服务事件与对账机制。
- 引入 OpenTelemetry 指标、日志与链路追踪。
- database/host/network/security 岗位新增业务端点时补精确网关路由、鉴权和契约测试。
