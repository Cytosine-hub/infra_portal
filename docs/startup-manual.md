# 项目启动手册

本文档适用于当前前后端分离版本：

- API Gateway：Spring Cloud Gateway，默认端口 `8080`
- community-service：独立论坛服务，默认端口 `8082`
- ai-service：独立 AI/Agent 集群服务，默认端口 `8083`
- core-service：独立 identity/catalog/standards 平台核心服务，默认端口 `8084`
- middleware-service：中间件岗位服务，默认端口 `8085`
- database-service：数据库岗位薄服务，默认端口 `8086`
- host-service：主机岗位薄服务，默认端口 `8087`
- network-service：网络岗位薄服务，默认端口 `8088`
- security-service：网络安全岗位薄服务，默认端口 `8089`
- 前端：Vue 3 + Vite，默认端口 `5173`
- 数据库：MySQL 8.0，默认端口 `3306`

## 1. 环境要求

- JDK 17（必须，Spring Boot 3.x 不兼容 JDK 8）
- Maven 3.8.x
- Node.js 20.19.x（`package.json` 限定为 `>=20.19 <21`）
- MySQL 8.x

### JAVA_HOME 配置

确保 `JAVA_HOME` 指向 JDK 17：

```bash
# macOS / Linux（添加到 ~/.zshrc 或 ~/.bashrc）
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Windows 下在系统环境变量中设置 `JAVA_HOME` 为 JDK 17 安装路径。

Windows PowerShell 下建议使用 `npm.cmd`，避免 `npm.ps1` 被执行策略拦截。

## 2. Docker Compose 完整栈

Docker Compose 覆盖裸机清单中的前端、Gateway、8 个业务服务，并包含 MySQL、Nacos、Milvus、etcd、MinIO 和一次性 `nacos-init`：

```bash
cp deploy/compose.env.example deploy/compose.env
cp deploy/services.env.example deploy/services.env
```

测试或 CI 静态验证使用模板生成器，不需要手工填写测试密码：

```bash
sh deploy/generate-test-env.sh
```

生成器会创建 `deploy/compose.test.env` 和 `deploy/services.test.env`，随机生成 MySQL、MinIO、Nacos 鉴权及业务服务密钥，并使用独立的业务/依赖 Compose 项目名、共享网络、宿主端口和 `/app/infra-portal-test/data` 数据目录。Nacos 登录密码保留镜像默认值 `nacos`，因为当前镜像不会通过 Compose 环境变量修改默认账号；Nacos 鉴权 Token 和身份键值仍为随机值。生成文件权限为 `0600` 且不会被 Git 跟踪，脚本拒绝覆盖已有文件，避免测试数据卷与新密码不一致。

配置职责必须保持分离：

| 文件 | 职责 | 是否注入业务服务 |
|------|------|------------------|
| `deploy/docker-compose.dependencies.yml` | MySQL、Nacos、Nacos 初始化、etcd、MinIO、Milvus 依赖栈 | 不适用 |
| `deploy/docker-compose.yml` | Gateway、9 个 Java 服务和前端业务栈 | 不适用 |
| `deploy/compose.env` | 两个 Compose 项目名、共享网络、镜像版本、端口、数据目录、基础组件凭据 | 否 |
| `deploy/services.env` | Gateway 签名、管理员初始密码、AI、Wiki、Zabbix 等运行时密钥 | 是 |
| `deploy/nacos-config/*.properties` | 9 个 Java 服务的业务配置模板，构建时复制进 `nacos-init` 镜像 | 由 Nacos Config 加载 |

生产部署必须替换两个环境文件中的开发凭据和空业务密钥。本地在项目根目录按依赖栈、业务栈的顺序启动：

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

不传 `--env-file` 时，两份 Compose 配置仍可解析，缺省使用仅限本地开发的 MySQL、Nacos 和 MinIO 凭据；缺失的 `services.env` 不阻断 Compose 配置解析。业务服务实际启动及生产部署仍必须提供至少 32 字节的 `GATEWAY_SIGNING_SECRET` 等业务密钥。

从旧版配置升级时，必须删除 `compose.env` 中的 Docker Compose 保留变量 `COMPOSE_PROJECT_NAME`，并改用 `COMPOSE_BUSINESS_PROJECT_NAME`。保留旧变量会覆盖两份清单顶层的独立项目名，使业务栈和依赖栈重新归入同一 Compose 项目。

GitLab 流水线中的 `verify:deployment` 会执行 Shell 语法检查、CI/Compose/镜像维护契约测试，并使用临时测试配置解析 Compose；该门禁不安装 `jq`，也不执行依赖该工具的 Nacos 初始化单元测试，不启动或更新数据库、Nacos 和业务容器。生产 `nacos-init` 镜像仍内置 `jq`。后端首次镜像构建执行完整 `mvn clean verify`，同一提交的其他服务复用 BuildKit 构建层；前端镜像构建执行 Vitest 和 Vite 构建。真实部署需要在 GitLab CI/CD Variables 中创建以下变量：

| 变量 | 类型 | 内容 |
|------|------|------|
| `DEPLOY_COMPOSE_ENV_FILE` | File | 基于 `deploy/compose.env.example` 的完整部署配置 |
| `DEPLOY_SERVICES_ENV_FILE` | File | 基于 `deploy/services.env.example` 的完整业务密钥配置 |
| `DEPLOY_COMPOSE_ROOT` | Variable，可选 | Runner 宿主机上的 Compose 总目录，默认 `/app/infra-portal/compose` |
| `DEPLOY_DATA_ROOT` | Variable，可选 | Runner 宿主机上的数据总目录，默认 `/app/infra-portal/data` |
| `DEPLOY_ROLLBACK_STATE_ROOT` | Variable，可选 | 每服务回滚历史目录，默认 `/app/infra-portal/compose/rollback` |
| `AUTO_ROLLBACK_ENABLED` | Variable，必填 | 业务部署失败自动回滚开关；`true` 开启，`false` 关闭 |

`verify:all-backend-services` 只验证并构建 9 个后端镜像，不执行部署。`deploy:dependencies` 只执行依赖镜像缺失检查、MySQL/Nacos/Milvus 依赖栈初始化或更新，以及 Nacos 配置幂等初始化；它不读取 `DEPLOY_SERVICES_ENV_FILE`，也不构建或部署业务服务。手动业务部署分为三个范围：`deploy:all-backend-services` 仅部署全部后端，`deploy:all-services` 部署前端和全部后端，`deploy:full-stack` 用于初始化或全量部署，会先处理依赖栈和 Nacos 初始化，再部署完整业务栈。

所有业务部署入口在启动容器前都会记录受影响服务的实际运行镜像。`AUTO_ROLLBACK_ENABLED` 只从 GitLab 项目 **Settings > CI/CD > Variables** 获取，仓库不定义默认值；设为 `true` 时，Compose 健康等待或前端连通检查失败后按服务恢复部署前快照，即使恢复成功，部署 job 仍返回失败；设为 `false` 时保留失败现场。修改项目变量即可调整开关，无需提交代码。变量缺失或值不是 `true`/`false` 时，业务部署会在更新容器前失败。依赖栈是有状态组件，不参与自动回滚。

手动回滚不读取 `AUTO_ROLLBACK_ENABLED`，该变量缺失时仍可执行，也不需要在创建流水线时选择目标。独立 `rollback` stage 会列出 10 个 `rollback:<service>` 单服务作业，以及 `rollback:all-backend-services`、`rollback:all-services` 两个聚合作业；直接点击所需作业即可回滚其他服务。作业先检查所有目标历史和镜像都存在，再执行回滚；成功后交换当前/上一镜像记录，后续可再次执行切回。首次成功部署前没有上一版本记录，手动回滚会安全失败且不更新容器。

部署 job 读取到的 File 变量值是 GitLab 临时文件路径。业务部署持久化到 `$DEPLOY_COMPOSE_ROOT/business`，依赖部署持久化到 `$DEPLOY_COMPOSE_ROOT/dependencies`，MySQL 初始化脚本放在依赖目录的 `initdb` 中；回滚历史写入 `$DEPLOY_ROLLBACK_STATE_ROOT`。环境文件中的 `IMAGE_TAG` 会同步为实际部署标签，`DEPLOY_DATA_ROOT` 固定为 `/app/infra-portal/data`，业务 `BUSINESS_ENV_FILE` 固定为 `./services.env`，依赖 `MYSQL_INIT_DIR` 固定为 `./initdb`。密钥文件和回滚状态文件权限为 `0600`。

Runner 必须将宿主机 `/app` 挂载到 Job 容器的 `/app`。默认部署完成后，在宿主机执行：

```bash
cd /app/infra-portal/compose
docker compose --env-file business/compose.env --file business/compose.yml ps
docker compose --env-file dependencies/compose.env --file dependencies/compose.yml ps
docker compose --env-file business/compose.env --file business/compose.yml stop
docker compose --env-file dependencies/compose.env --file dependencies/compose.yml stop
docker compose --env-file dependencies/compose.env --file dependencies/compose.yml start
docker compose --env-file business/compose.env --file business/compose.yml start
```

此时 `docker compose ls` 中业务项目和依赖项目的配置路径应分别位于 `/app/infra-portal/compose/business/compose.yml` 和 `/app/infra-portal/compose/dependencies/compose.yml`，不再引用会被 Runner 清理的 `/builds/...`。受保护变量只会注入受保护分支或 Tag；在普通 feature 分支手动部署前必须确认变量保护范围和 Environment scope。

Runner 的 Docker Executor 应保持 `pull_policy = "if-not-present"`，并挂载宿主机 `/var/run/docker.sock`。项目通过宿主机 Docker 守护进程保留 BuildKit 构建层，Maven 与 npm 依赖分别使用 Dockerfile 中的 `/root/.m2` 和 `/root/.npm` cache mount；Runner 的 `/cache` 挂载只服务于 GitLab Job cache，不能替代 BuildKit 缓存。依赖镜像仅在对应 tag 不存在时拉取。不要配置无保留策略的定时 `docker builder prune -a`，否则下一次构建会重新下载基础镜像层和依赖。

内部构建镜像统一使用 `${IMAGE_NAMESPACE}/${service}:${yyyyMMddHHmmss}-${commit:7}`，时间由 `CI_PIPELINE_CREATED_AT` 转换到 `Asia/Shanghai` 后生成。例如 `infra-portal/core-service:20260803153012-0123456`。后端与前端最终镜像均将完整提交 SHA 写入 `org.opencontainers.image.revision`，将 `IMAGE_TAG` 写入 `org.opencontainers.image.version`；版本元数据使每次流水线生成独立的平台镜像配置，实际文件层仍复用 BuildKit 缓存，避免 containerd 将共享同一平台 manifest 的多个历史 image index 同时显示为使用中。标签在同一流水线中保持稳定，并避免同一提交的不同流水线覆盖镜像；增量部署必须等待当前流水线全部验证和构建任务成功，不会在其他服务仍失败时提前发布；部署开始后不可被新流水线取消。开放 MR 的分支只创建 MR 流水线，避免重复占用单并发 Runner。

在 GitLab 项目的 Pipeline Schedules 中为 `master` 创建每日清理计划：Cron 填写 `0 3 * * *`，Cron timezone 选择 `Asia/Shanghai`。定时流水线会跳过构建和部署，仅执行 `cleanup:business-images`。该任务对 9 个后端服务和前端逐仓库按 Docker 镜像创建时间降序排序，保留最近 3 个符合 `yyyyMMddHHmmss-commit7` 格式的标签以及回滚状态引用的当前/上一镜像；依赖镜像、`nacos-init`、旧格式标签和历史完整 SHA 标签不在清理范围内。若过期镜像仍被容器引用，Docker 会拒绝删除，任务会保留该镜像并记录提示，不强制影响运行容器。

测试环境也可直接将生成的两个 `.test.env` 文件内容分别配置为上述 File 变量。部署入口会统一把业务密钥文件路径覆盖为 `./services.env`，不依赖上传前的本地文件名。

依赖栈内部由健康检查和 `depends_on` 控制：MySQL/Nacos/Milvus 先就绪，`nacos-init` 再创建缺失的 namespace 和 9 个 Data ID。依赖命令成功后再启动业务栈；`nacos-init` 遇到已存在的 Data ID 会跳过，人工在 Nacos 中调整的业务配置不会被覆盖。

首次创建 `${DEPLOY_DATA_ROOT}/dependencies/mysql` 时会依次执行 `initdb/init.sql` 和 `initdb/seed.sql`，沿用现有种子账号。已有 MySQL 数据目录不会再次初始化；`ADMIN_DEFAULT_PASSWORD` 仅在账号表为空时生效。

访问与停止命令：

```text
前端：http://localhost:5173
Gateway：http://localhost:8080
Nacos：http://localhost:8848/nacos/
```

```bash
docker compose --env-file deploy/compose.env \
  --file deploy/docker-compose.yml down
docker compose --env-file deploy/compose.env \
  --file deploy/docker-compose.dependencies.yml down
```

`down` 不删除宿主机 `${DEPLOY_DATA_ROOT}` 下的数据。需要重新初始化时应先备份并明确处理对应数据目录，不要直接覆盖已有 Nacos/MySQL 数据。

模拟首次初始化时，只能处理 `/app/infra-portal/data/dependencies` 与 `/app/infra-portal/data/business` 下明确指定的数据目录；必须保留 `/app/infra-portal/compose`。历史源码归档到 `/data/infra-portal`，不参与 Compose 运行和数据卷挂载。

## 3. 裸机启动数据库

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-mysql.ps1
```

停止数据库：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local-mysql.ps1
```

8 个业务服务的默认数据库配置分别在各自 `application.yml`，共同连接一期共享库：

- 数据库：`middleware_resource_manager`
- 地址：`127.0.0.1:3306`
- 用户：`root`
- 密码：读取 `APP_DB_PASSWORD`，仓库不提供密码默认值

## 4. 裸机启动后端与 Gateway

进入 `backend/` 目录执行：

启动 core-service：

```powershell
cd backend
mvn -pl core-service -am spring-boot:run
```

另开终端启动 community-service 与 ai-service：

```powershell
cd backend
mvn -pl community-service -am spring-boot:run
mvn -pl ai-service -am spring-boot:run
```

分别在独立终端启动 5 个岗位服务：

```powershell
cd backend
mvn -pl middleware-service -am spring-boot:run
mvn -pl database-service -am spring-boot:run
mvn -pl host-service -am spring-boot:run
mvn -pl network-service -am spring-boot:run
mvn -pl security-service -am spring-boot:run
```

最后启动 Gateway：

```powershell
cd backend
mvn -pl api-gateway -am spring-boot:run
```

默认 profile 不连接 Nacos。Gateway 将 `/api/forum/**` 静态转发到 community-service `:8082`；将 AI/Agent 路径转发到 ai-service `:8083`；将 identity/catalog/standards 原路径与 `/files/**` 转发到 core-service `:8084`；将 `/api/middleware-commands/**` 转发到 middleware-service `:8085`。其余 4 个岗位服务暂无业务路由。启用 Nacos 的 `cloud` 启动与验证步骤见 `docs/microservices-stage6-job-services.md`。

九个后端进程启动成功后经 Gateway 访问：

```text
http://localhost:8080/api/public/releases
http://localhost:8080/api/forum/posts
http://localhost:8080/api/wiki/pages
```

如果返回 JSON，说明后端接口可用。

## 5. 裸机启动前端

进入前端目录：

```powershell
cd .\frontend
```

首次启动前安装依赖：

```powershell
npm.cmd install
```

启动 Vue 开发服务器：

```powershell
npm.cmd run dev
```

前端访问地址：

```text
http://localhost:5173
```

Vite 已配置代理：

- `/api` 转发到 `http://localhost:8080`
- `/files` 转发到 `http://localhost:8080`

`8080` 是 Gateway；8 个业务服务直连端口为 `8082-8089`（`8081` 已停用）。外部业务流量只应进入 Gateway。

## 6. 登录后台

打开前端：

```text
http://localhost:5173/#/admin
```

使用数据库中的管理员账号登录。

首次初始化空账号表前必须设置 `ADMIN_DEFAULT_PASSWORD`。仓库和配置文件不提供内置密码；如果数据库里已经有管理员账号，实际密码以数据库现有数据为准。

### RBAC 角色体系

系统内置 13 个角色，分为三类：

| 类型 | 角色 | 权限 |
|------|------|------|
| 系统管理员 | 系统管理员 | 全局管理，可操作所有分类和系统设置 |
| 专业管理员 | 中间件/数据库/主机/网络/网络安全管理员 | 管理本分类 + 审核权 |
| 管理岗 | 中间件/数据库/主机/网络/网络安全管理岗 | 管理本分类，无审核权 |
| 只读角色 | 开发经理、运维经理 | 只读访问 |

角色信息存储在数据库 `roles` 表中，系统管理员可在用户管理界面增删改角色。

### 修订历史

参数标准和标准文档审核通过时，系统自动创建修订记录，包含：
- 版本号、修订时间、修订人、提交人
- 审核意见
- 完整内容快照（参数标准含参数列表）

可在参数标准/标准文档列表的「修订历史」按钮查看。

## 7. 常用页面

| 页面 | 地址 | 说明 |
|------|------|------|
| 门户首页 | `http://localhost:5173/#/home` | 公开入口，聚合下载、标准、漏洞、论坛 |
| 下载中心 | `http://localhost:5173/#/downloads` | 中间件资源下载 |
| 标准发布 | `http://localhost:5173/#/standards` | 已发布的参数标准和标准文档 |
| 论坛 | `http://localhost:5173/#/forum` | 技术交流 |
| 知识库管理 | `http://localhost:5173/#/knowledge` | 文档上传、切分、向量化 |
| 智能排查 | `http://localhost:5173/#/diagnostics` | 基于知识库的 AI 排查对话 |
| 管理后台 | `http://localhost:5173/#/admin` | 需登录，含以下子模块 |

管理后台子模块：

| 子模块 | 说明 | 权限 |
|--------|------|------|
| 文件管理 | 中间件资源上传、编辑、发布 | 管理员+管理岗（本岗位分类） |
| 类型管理 | 软件分类和软件类型维护 | 仅系统管理员 |
| 参数标准 | 参数标准的创建、编辑、发布、版本管理、修订历史 | 管理员+管理岗（本岗位分类） |
| 标准文档 | 手册/文章的编写和管理、修订历史 | 管理员+管理岗（本岗位分类） |
| 审核管理 | 参数标准和标准文档的审核流程 | 系统管理员+专业管理员（本岗位分类） |
| 用户管理 | 管理员账号和角色管理 | 仅系统管理员 |
| 系统设置 | 模块开关（知识库、智能排查） | 仅系统管理员 |

后端 API：

- 公开资源列表：`http://localhost:8080/api/public/releases`
- 文件下载接口：`http://localhost:8080/files/{downloadToken}`
- 公开参数标准：`http://localhost:8080/api/public/parameter-standards`
- 论坛帖子：`http://localhost:8080/api/forum/posts`

## 8. 构建前端

进入 `frontend` 目录执行：

```powershell
npm.cmd run build
```

构建产物输出到：

```text
frontend/dist
```

## 9. 后端测试

进入 `backend/` 目录执行：

```powershell
cd backend
mvn test
```

## 10. 端口占用检查

检查后端端口：

```powershell
netstat -ano | Select-String ':8080'
netstat -ano | Select-String ':8082'
netstat -ano | Select-String ':8083'
netstat -ano | Select-String ':8084'
netstat -ano | Select-String ':8085'
netstat -ano | Select-String ':8086'
netstat -ano | Select-String ':8087'
netstat -ano | Select-String ':8088'
netstat -ano | Select-String ':8089'
```

检查前端端口：

```powershell
netstat -ano | Select-String ':5173'
```

## 11. 裸机推荐启动顺序

1. 启动 MySQL
2. 启动 core-service（`:8084`）
3. 启动 community-service（`:8082`）与 ai-service（`:8083`）
4. 启动 5 个岗位服务（`:8085-8089`）
5. 启动 Gateway（`:8080`）
6. 启动 Vue 前端
7. 打开 `http://localhost:5173`

如果前端页面能打开但接口报错，先检查 Gateway `8080`；论坛检查 community-service `8082`；知识库、Wiki 和 Agent 检查 ai-service `8083`；登录、下载、标准和管理后台检查 core-service `8084`；中间件命令检查 middleware-service `8085`；4 个薄服务用各自 `/health` 检查。任一业务服务启动失败时优先检查 MySQL，AI 功能还需检查 Milvus、LLM 和 Zabbix 配置。

## 11. 知识库模块配置

### 1. 执行 DDL

首次运行需手动建表，使用全量脚本：

```bash
mysql -u root -p middleware_resource_manager < release/db/full_schema.sql
mysql -u root -p middleware_resource_manager < release/db/seed_data.sql
```

后续版本升级使用增量脚本（按版本号顺序执行）：

```bash
mysql -u root -p middleware_resource_manager < release/db/upgrade-v1.0.4.sql
```

> 增量 SQL 使用 `CREATE TABLE IF NOT EXISTS` 和 `INSERT IGNORE`，重复执行安全无副作用。

### 2. 配置 AI 模型

设置环境变量或修改 `application.yml`：

```bash
export AI_BASE_URL=https://your-model-api/v1
export AI_API_KEY=your-api-key
export AI_MODEL=your-model-name
```

### 3. 前端页面

- 知识库管理：`http://localhost:5173/#/knowledge`
- 智能排查：`http://localhost:5173/#/diagnostics`
