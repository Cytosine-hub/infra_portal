# Docker Compose 完整部署设计

## 1. 目标

将裸机启动手册中的完整运行拓扑容器化，使开发和部署人员能够通过 Docker Compose 完成镜像构建、依赖组件初始化、业务配置初始化、服务启动和健康验证。

交付范围包括：

- Vue 前端及 Nginx 反向代理；
- API Gateway；
- core-service、community-service、ai-service；
- middleware-service、database-service、host-service、network-service、security-service；
- MySQL、Nacos、Milvus、etcd、MinIO；
- MySQL 首次建库和现有种子数据初始化；
- Nacos namespace 和九个业务服务 Data ID 初始化；
- 本地 OrbStack Fedora Docker 环境中的完整构建与冒烟测试；
- 与现有 GitLab Runner 镜像部署流程兼容。

## 2. 方案选择

采用单一 `deploy/docker-compose.yml` 同时服务本地和 CI：

- 本地使用 `docker compose up --build` 构建并启动；
- CI 继续预先构建带提交标签的镜像，再使用 `docker compose up --no-build --pull never` 部署；
- 每个自研服务同时声明 `build` 和 `image`，避免本地与 CI 维护两套拓扑。

不采用独立的本地/CI Compose 文件，原因是两套服务定义容易在路由、依赖、端口和挂载目录上产生漂移。也不引入额外编排平台或配置生成框架，保持 Docker Compose、Shell 和现有 Spring Cloud Alibaba 能力范围内的最小实现。

## 3. 运行拓扑

外部访问链路为：

```text
浏览器 -> frontend:80 -> api-gateway:8080 -> Nacos 服务发现 -> 各业务服务
```

前端 Nginx 提供静态资源，并将 `/api`、`/files` 原样代理到 `api-gateway:8080`。Gateway 和各业务服务以 `cloud` profile 启动，通过 Nacos 完成配置导入、服务注册和发现。

业务服务的数据链路为：

```text
core/community/ai/job services -> mysql:3306
ai-service -> milvus:19530 -> etcd + MinIO
```

默认只要求前端端口作为门户入口。Gateway 和各后端端口保留可配置的宿主机映射，以兼容现有调试、CI 和裸机迁移验证；MySQL、Nacos、MinIO 控制台及 Milvus 管理端口默认绑定 `127.0.0.1`。

## 4. 镜像设计

### 4.1 后端

继续使用 `deploy/Dockerfile` 的 Maven 多阶段构建，通过 `SERVICE` 参数构建指定 Maven 服务模块。运行镜像使用 Java 17 JRE，并补充健康检查所需的轻量 HTTP 客户端。

本地 Compose 为九个后端服务配置独立的 `build.args.SERVICE`。CI 仍可按服务单独构建同一个 Dockerfile，并使用 `IMAGE_NAMESPACE`、`IMAGE_TAG` 选择已构建镜像。

### 4.2 前端

新增前端多阶段 Dockerfile：

1. Node 20 阶段使用锁文件安装依赖并执行 `npm run build`；
2. Nginx 阶段只复制 `frontend/dist` 和专用代理配置；
3. Nginx 提供 SPA hash 路由兼容、静态缓存、上传大小限制和 `/api`、`/files` 反向代理；
4. 前端健康检查访问根页面。

## 5. 配置分层

配置明确分为三层。

### 5.1 Compose 启动配置

`deploy/compose.env` 由 `deploy/compose.env.example` 创建，负责：

- 镜像 namespace、标签和组件版本；
- 宿主机端口；
- 持久化数据根目录；
- MySQL、Nacos、MinIO 等依赖组件认证；
- Nacos namespace、配置组和服务发现组；
- Compose 项目级启动参数。

该文件通过 `docker compose --env-file deploy/compose.env` 参与变量插值，不作为业务容器的通用 `env_file`。

### 5.2 Nacos 业务配置

`deploy/nacos-config/` 保存可版本化、非敏感的业务配置模板，Data ID 与现有 `application-cloud.yml` 完全一致：

- `api-gateway.properties`；
- `core-service.properties`；
- `ai-service.properties`；
- `community-service.properties`；
- `middleware-service.properties`；
- `database-service.properties`；
- `host-service.properties`；
- `network-service.properties`；
- `security-service.properties`。

模板包含日志级别、限流、模块开关、AI 参数、向量集合、Wiki、岗位服务行为等非敏感业务参数。数据库和依赖组件的 Compose 内部地址由部署层注入，不要求使用者手工填写容器服务名。

### 5.3 业务敏感配置

`deploy/services.env` 由 `deploy/services.env.example` 创建，保存：

- `GATEWAY_SIGNING_SECRET`；
- `ADMIN_DEFAULT_PASSWORD`；
- AI、Embedding 和 Wiki 导出密钥；
- Zabbix 凭据；
- Nacos 模板中引用的其他敏感值。

Nacos 模板以 Spring 属性占位符引用这些环境变量。真实密钥不进入 Git 中的模板，也不由初始化脚本写入 Nacos。现有 `db/seed.sql` 会创建历史账号，因此 `ADMIN_DEFAULT_PASSWORD` 仅在账号表为空时生效，文档必须明确这一行为。

## 6. 首次初始化

### 6.1 MySQL

首次创建 MySQL 数据目录时，官方 MySQL entrypoint 按顺序执行：

1. `db/init.sql`；
2. `db/seed.sql`。

沿用现有初始化内容，不删除或改写历史账号、Token 和业务种子数据。数据目录已存在时 MySQL 不重放脚本。

### 6.2 Nacos

新增一次性 `nacos-init` 服务。其启动顺序为：

1. 等待 Nacos 健康检查通过；
2. 使用配置的 Nacos 账号登录并取得访问令牌；
3. 当 `NACOS_NAMESPACE` 非空且 namespace 不存在时创建 namespace；
4. 逐个查询九个 Data ID；
5. Data ID 缺失时发布对应 `.properties` 文件；
6. Data ID 已存在时输出 `SKIP` 并保留原内容；
7. 任一步骤失败时非零退出。

九个业务服务均以 `condition: service_completed_successfully` 依赖 `nacos-init`。因此业务服务不会在配置中心初始化完成前启动，也不会在发布失败后静默退回 JAR 默认配置。

初始化脚本必须支持 public 空 namespace 和自定义 namespace ID，所有请求使用配置的 `NACOS_CONFIG_GROUP`。重复执行必须保持已有配置不变。

## 7. 持久化与生命周期

持久化范围包括：

- MySQL 数据；
- Nacos 数据和日志；
- Milvus、etcd、MinIO 数据；
- core-service 上传文件；
- ai-service 的数据、Skill 和需要持久化的导出内容。

数据根目录由 Compose 启动配置控制。`docker compose down` 只停止和删除容器、网络，不删除宿主机数据。文档单独提供明确的测试环境重置命令，并提示该操作会删除数据，正常停止流程不得隐式清理持久化目录。

## 8. 健康检查与失败处理

依赖启动条件如下：

- MySQL、Nacos、etcd、MinIO 和 Milvus 使用组件原生健康接口或命令；
- `nacos-init` 必须成功退出；
- 业务服务使用已有公开接口或 `/health` 进行 HTTP 健康检查；
- Gateway 在核心路由可用后判定健康；
- 前端在 Nginx 根页面和代理链可用后判定健康。

初始化和冒烟脚本采用项目约定的无 emoji 日志格式：

```text
+ Task start
| Task info
| Subtask success
- Subtask failure
* Task complete
```

缺少必填密钥、Nacos 发布失败、容器超时或冒烟接口失败时，脚本必须返回非零状态并指出具体服务或 Data ID。

## 9. 测试策略

实现按测试先行推进。

### 9.1 静态契约测试

先编写失败的 Compose 契约测试，覆盖：

- 完整服务集合；
- 九个业务镜像的 build/image 双模式；
- 前端代理和健康检查；
- MySQL 初始化脚本顺序；
- Nacos 初始化服务及九个 Data ID；
- `service_completed_successfully` 启动依赖；
- Compose 配置和业务配置文件分离；
- 必填敏感变量不提供仓库默认值。

每条用例使用 `TC-DOCKER-xxx` 编号。

### 9.2 项目回归

运行：

- 后端 `mvn test`；
- 前端 `npm test`；
- 前端 `npm run build`；
- `docker compose config`。

### 9.3 本机集成验收

在 OrbStack Fedora VM 中使用 `of docker ...`：

1. 使用独立测试数据目录构建全部自研镜像；
2. 启动完整 Compose；
3. 等待全部长期运行容器健康；
4. 通过 Nacos API 验证 namespace 和九个 Data ID；
5. 再次运行 `nacos-init`，确认已存在配置全部跳过；
6. 访问前端根页面；
7. 通过前端入口访问公开 API 和文件代理链；
8. 检查 Nacos 中九个服务的注册状态；
9. 收集 `docker compose ps` 和失败容器日志；
10. 停止测试栈并清理本次测试专用数据，不影响用户已有数据。

AI、Embedding 和 Zabbix 属于外部业务系统。未提供有效凭据或地址时，容器和基础门户必须能启动；依赖这些外部系统的具体业务调用不纳入本地基础冒烟成功条件。

## 10. CI 与文档兼容

GitLab Runner 继续在宿主机逐服务构建镜像。部署作业改为显式提供 Compose 启动配置和业务敏感配置；旧 `DEPLOY_ENV_FILE` 的迁移方式写入文档，避免无提示破坏现有变量配置。

同步更新：

- `README.md` 的快速启动入口；
- `docs/startup-manual.md` 的 Docker Compose 章节；
- `docs/microservices-stage6-job-services.md` 的完整栈和 CI 说明；
- `deploy/services.env.example`；
- 新增 Compose 启动配置示例、Nacos 配置模板和测试脚本说明。

本任务不修改业务接口、权限模型、数据库结构或现有种子数据。
