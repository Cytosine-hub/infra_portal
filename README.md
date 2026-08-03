# Infra Portal

面向基础设施/运维团队的集成中心门户，服务中间件、数据库、主机、网络和安全等多个运维岗位。项目基于 `Spring Boot 3.5 + Java 17 + MySQL 8.0`，集成 Wiki 知识库、AI 智能排查和运维 Agent。

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

### Docker Compose（完整运行栈）

Compose 会启动前端、Gateway、8 个业务服务、MySQL、Nacos、Milvus、etcd 和 MinIO。首次启动先创建两类环境文件：

```bash
cp deploy/compose.env.example deploy/compose.env
cp deploy/services.env.example deploy/services.env
```

测试环境可从同一组模板生成隔离配置。脚本会随机生成数据库密码、基础组件鉴权值和业务密钥，并使用独立的业务/依赖项目名、共享网络、端口和 `/app/infra-portal-test` 数据目录：

```bash
sh deploy/generate-test-env.sh
```

生成的 `deploy/compose.test.env` 和 `deploy/services.test.env` 权限为 `0600`，已被 Git 忽略。为避免意外轮换已有测试环境密码，脚本不会覆盖现有文件。

- `deploy/docker-compose.dependencies.yml`：MySQL、Nacos、Nacos 配置初始化、etcd、MinIO 和 Milvus 依赖栈。
- `deploy/docker-compose.yml`：Gateway、9 个 Java 业务服务和前端业务栈。
- `deploy/compose.env`：两个 Compose 项目名、共享网络、镜像版本、端口、数据目录和基础组件凭据。
- `deploy/services.env`：只注入 Java 业务容器的运行时密钥。
- `deploy/nacos-config/*.properties`：业务服务配置模板，构建时复制进 `nacos-init` 镜像并发布到 Nacos；模板中的密钥通过业务容器环境变量解析。

本地启动时先启动依赖栈，待其健康并完成 Nacos 初始化后再启动业务栈：

```bash
docker compose --env-file deploy/compose.env \
  --file deploy/docker-compose.dependencies.yml \
  up --detach --wait mysql nacos etcd minio milvus
docker compose --env-file deploy/compose.env \
  --file deploy/docker-compose.dependencies.yml \
  run --rm --build --no-deps nacos-init
docker compose --env-file deploy/compose.env \
  --file deploy/docker-compose.yml up --detach --build
sh deploy/smoke-test.sh
```

不指定 `--env-file` 时，两份 Compose 仍可正常解析，并使用仅限本地开发的基础组件默认值；业务密钥文件也按可选文件处理。生产部署必须提供并替换 `compose.env` 和 `services.env` 中的全部密钥，禁止使用仓库内的开发默认凭据。

旧版 `compose.env` 中若存在 Docker Compose 保留变量 `COMPOSE_PROJECT_NAME`，升级时必须删除并改用 `COMPOSE_BUSINESS_PROJECT_NAME`；否则它会覆盖两份清单各自的项目名，破坏独立启停。

`nacos-init` 只创建缺失的 namespace 和 9 个 Data ID，不覆盖 Nacos 中已有配置。MySQL 只在全新数据目录首次执行 `db/init.sql` 和 `db/seed.sql`，已有数据目录不会重放初始化脚本。前端入口为 `http://localhost:5173`。

GitLab 的 `verify:deployment` 会执行部署脚本语法、CI/Compose/镜像维护契约测试，并自动生成临时测试配置完成 Compose 解析，不启动运行栈。Nacos 初始化单元测试依赖 `jq`，不在 `docker:27-cli` 门禁中执行；生产 `nacos-init` 镜像仍内置 `jq`。后端镜像首次构建运行完整 Maven 验证并通过 BuildKit 复用结果，前端镜像构建会执行 Vitest。实际部署使用 File 类型 CI/CD Variable：依赖部署只要求 `DEPLOY_COMPOSE_ENV_FILE`，业务部署还要求 `DEPLOY_SERVICES_ENV_FILE`。

CI 构建镜像统一命名为 `${IMAGE_NAMESPACE}/${service}:${yyyyMMddHHmmss}-${commit:7}`，例如 `infra-portal/core-service:20260803153012-0123456`；时间取流水线创建时间并转换为 `Asia/Shanghai`，避免同一提交的不同流水线覆盖镜像。在 GitLab 为 `master` 创建时区为 `Asia/Shanghai`、Cron 为 `0 3 * * *` 的 Pipeline Schedule 后，`cleanup:business-images` 每日只运行镜像清理：9 个后端服务和前端各自保留按创建时间排序的最近 3 个新格式镜像，不清理 MySQL、Nacos、Milvus 等依赖镜像、`nacos-init` 和历史完整 SHA 标签。

CI 中 `verify:all-services` 只验证并构建 9 个后端镜像，不执行部署。手动部署入口按范围分为：`deploy:all-services` 部署全部后端服务，`deploy:business-stack` 部署前端和全部后端服务，`deploy:full-stack` 先初始化或更新 MySQL、Nacos、Milvus 等依赖，再部署完整业务栈。`deploy:dependencies` 仍可单独初始化或更新依赖栈，且仅要求 `DEPLOY_COMPOSE_ENV_FILE`。

部署 job 会将两份运行清单持久化到 Runner 宿主机 `/app/infra-portal/deploy`。业务环境保存为 `compose.env` 和 `services.env`，依赖环境独立保存为 `dependencies.env`；MySQL 初始化脚本保存到 `/app/infra-portal/db`。Job 结束后可在宿主机分别管理：

```bash
cd /app/infra-portal/deploy
docker compose --env-file compose.env --file docker-compose.yml ps
docker compose --env-file dependencies.env --file docker-compose.dependencies.yml ps
docker compose --env-file compose.env --file docker-compose.yml stop
docker compose --env-file dependencies.env --file docker-compose.dependencies.yml stop
docker compose --env-file dependencies.env --file docker-compose.dependencies.yml start
docker compose --env-file compose.env --file docker-compose.yml start
```

`compose.env` 和 `services.env` 包含部署密钥，权限固定为 `0600`。模拟首次初始化时只清理 `/app/infra-portal/mysql`、`nacos`、`milvus`、`storage` 和 `ai` 等数据目录，保留 `deploy` 与 `db` 目录。

停止服务但保留数据：

```bash
docker compose --env-file deploy/compose.env \
  --file deploy/docker-compose.yml down
docker compose --env-file deploy/compose.env \
  --file deploy/docker-compose.dependencies.yml down
```

### 裸机后端

```bash
cd backend
mvn clean package -DskipTests
mvn spring-boot:run
```

### 裸机前端

```bash
cd frontend
npm install
npm run dev
```

### 裸机依赖服务

- MySQL 8.0：`127.0.0.1:3306`
- Milvus（向量数据库）：`localhost:19530`

## 裸机业务环境变量

裸机默认 profile 继续直接读取以下环境变量；Compose 的 `cloud` profile 则优先从 Nacos 的 9 个 Data ID 加载非敏感业务配置，敏感值仍由 `deploy/services.env` 注入。

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
mysql -u root middleware_resource_manager < db/seed.sql
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
