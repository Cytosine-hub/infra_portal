# 阶段 6：岗位服务拆分与多进程运行清单

## 1. 结果

阶段 6 将原 `app` 中聚合的 5 个 `job-*` 模块拆成 5 个独立 Spring Boot 应用。`app` 的启动类、配置、测试、父 POM 模块声明、网关路由、CI 和部署单元均已删除。

| 服务 | 端口 | 业务模块 | 业务/运维端点 | Nacos 服务名 |
|------|------|----------|---------------|----------------|
| middleware-service | 8085 | job-middleware | `/api/middleware-commands/**` | middleware-service |
| database-service | 8086 | job-database | `/health` | database-service |
| host-service | 8087 | job-host | `/health` | host-service |
| network-service | 8088 | job-network | `/health` | network-service |
| security-service | 8089 | job-security | `/health` | security-service |

每个服务都有自己的 `main`、`application.yml`、`application-cloud.yml`、Boot Maven 插件和 `*-exec.jar`。部署服务只依赖 `common-core`、`common-security`、`common-web` 与自身 `job-*` 模块。

## 2. 行为不变量

- `job-middleware` 的 Java 业务代码、Mapper XML、DDL、DML 和 `/api/middleware-commands/**` 路径未修改。
- 默认 profile 关闭 Nacos discovery/config；`cloud` profile 才启用配置导入与服务注册。
- 下游认证继续复用网关签名身份头。未签名伪造身份访问中间件写端点仍返回 401。
- database/host/network/security-service 当前没有业务端点；`/health` 仅用于直连存活检查并在 `SecurityConfig` 中 `permitAll`。
- 148 个业务端点保持不变。4 个 `/health` 为新增运维端点，不计入业务端点集合。
- 本阶段不修改数据库 schema/DML、前端或 core/ai/community 业务服务。

## 3. 网关路由

默认 profile：

```text
/api/middleware-commands/** -> ${MIDDLEWARE_SERVICE_URL:http://127.0.0.1:8085}
```

`cloud` profile：

```text
/api/middleware-commands/** -> lb://middleware-service
```

原 `/api/** -> app` 泛路由已删除。database/host/network/security-service 目前只注册 Nacos，不增加网关路由。后续新增岗位业务端点时，必须同时增加精确 Path 路由与网关路由测试；禁止重新引入兜底泛路由。

## 4. 构建与测试

```bash
cd backend
mvn -q -DskipTests test-compile
mvn test
mvn -DskipTests clean package
```

单服务构建：

```bash
mvn -pl middleware-service -am clean verify
mvn -pl database-service -am clean verify
mvn -pl host-service -am clean verify
mvn -pl network-service -am clean verify
mvn -pl security-service -am clean verify
```

最终只应存在 9 个可执行 JAR：

```text
api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar
ai-service/target/ai-service-0.0.1-SNAPSHOT-exec.jar
core-service/target/core-service-0.0.1-SNAPSHOT-exec.jar
middleware-service/target/middleware-service-0.0.1-SNAPSHOT-exec.jar
database-service/target/database-service-0.0.1-SNAPSHOT-exec.jar
host-service/target/host-service-0.0.1-SNAPSHOT-exec.jar
network-service/target/network-service-0.0.1-SNAPSHOT-exec.jar
security-service/target/security-service-0.0.1-SNAPSHOT-exec.jar
```

## 5. 多进程运行清单

所有进程共享以下环境变量契约，真实值不得写入仓库：

```bash
export APP_DB_HOST=127.0.0.1
export APP_DB_PORT=3306
export APP_DB_NAME=middleware_resource_manager
export APP_DB_USERNAME=root
export APP_DB_PASSWORD=
export GATEWAY_SIGNING_SECRET=
```

生产环境必须为 `GATEWAY_SIGNING_SECRET` 提供至少 32 UTF-8 字节的同一值。`cloud` profile 还需要按环境提供 `NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`NACOS_USERNAME`、`NACOS_PASSWORD`、`NACOS_CONFIG_GROUP` 和 `NACOS_DISCOVERY_GROUP`。

systemd 的 9 个 unit 统一读取 `/app/infra_portal/backend/services.env`。部署包提供 `services.env.example`，所有敏感值保持为空；首次运行部署脚本会创建 `0600` 的 `services.env` 并在停止现有服务前退出。填写至少 32 字节的真实 `GATEWAY_SIGNING_SECRET` 后重新部署，否则网关签名密钥校验会使服务启动失败。

默认 profile 推荐启动顺序：

1. MySQL。
2. core-service `:8084`。
3. community-service `:8082`、ai-service `:8083`。
4. middleware/database/host/network/security-service `:8085-8089`。
5. api-gateway `:8080`。
6. 前端 `:5173`。

Maven 启动命令：

```bash
cd backend
mvn -pl core-service -am spring-boot:run
mvn -pl community-service -am spring-boot:run
mvn -pl ai-service -am spring-boot:run
mvn -pl middleware-service -am spring-boot:run
mvn -pl database-service -am spring-boot:run
mvn -pl host-service -am spring-boot:run
mvn -pl network-service -am spring-boot:run
mvn -pl security-service -am spring-boot:run
mvn -pl api-gateway -am spring-boot:run
```

每条命令需要独立终端。Windows 可在构建 JAR 后执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-full.ps1
```

## 6. 运行检查

```bash
curl -i http://127.0.0.1:8086/health
curl -i http://127.0.0.1:8087/health
curl -i http://127.0.0.1:8088/health
curl -i http://127.0.0.1:8089/health
curl -i http://127.0.0.1:8080/api/middleware-commands/types
```

前 4 条应返回 200。中间件命令请求经 Gateway 到 `middleware-service`，响应内容取决于共享 MySQL 数据。

`cloud` profile 还需确认 Nacos 中出现 9 个服务名，并确认 Gateway 只对 middleware-service 建立岗位业务路由。4 个薄服务的 `/health` 不经 Gateway 暴露。

## 7. CI/CD

`.gitlab-ci.yml` 中每个岗位服务都有独立 verify/deploy 作业。verify 的路径触发由服务目录、自身 `job-*` 目录和扇出段组成；扇出段为 `common-*`、父 POM 与 `.gitlab-ci.yml`。构建命令统一为：

```text
mvn -pl <service> -am clean verify
```

在 GitLab 的 **Run pipeline** 页面创建的 `web` 流水线中，可以手动运行以下作业：

- `verify:<service>`：构建一个业务服务的可执行镜像；随后可运行对应的 `deploy:<service>`；
- `verify:all-services`：按固定顺序构建 9 个业务服务镜像；
- `prepare:dependencies`：预拉取 MySQL、Nacos、etcd、MinIO 和 Milvus 镜像；
- `deploy:<service>`：部署一个业务服务，其 Compose 启动依赖会自动启动；
- `deploy:dependencies`：单独部署 MySQL、Nacos 和 Milvus 依赖组；
- `deploy:full-stack`：在一个 job 中构建全部业务镜像、预拉取依赖组件并部署完整运行栈；不需要先运行其他可选手动作业。

`deploy/docker-compose.yml` 是完整运行栈定义。它使用同一个 Compose 网络连接 9 个业务服务、MySQL、Nacos 以及 Milvus/etcd/MinIO；所有有状态数据均通过宿主机目录挂载，根目录由 `DEPLOY_DATA_DIR` 控制，默认 `/data/infra-portal`。首次创建 `${DEPLOY_DATA_DIR}/mysql` 时，MySQL 会执行 `db/init.sql` 和 `db/seed.sql`；已有目录不会重复初始化。部署前应基于 `deploy/services.env.example` 配置 GitLab 受保护的文件变量 `DEPLOY_ENV_FILE`，其中必须提供数据库密码、网关签名密钥及 Nacos 服务端认证参数。

## 8. 沙箱依赖说明

实现期间尝试采用 Spring Boot Actuator，但当前沙箱无法访问或写入宿主 Maven 缓存中的 `spring-boot-starter-actuator`。未修改依赖版本，也未引入替代仓库；按阶段规格允许的方案，4 个薄服务改用简单 `/health`。该端点没有外部依赖，且由上下文启动测试覆盖。后续若统一引入 Actuator，应在依赖可用的构建环境中完成，并同步替换健康检查脚本和测试。

## 9. 回滚边界

阶段 6 没有数据迁移。代码回滚必须成组恢复 app 模块、原启动配置、原网关路由、CI 和部署脚本；仅停掉 5 个岗位服务会导致岗位能力不可用，不是完整回滚。
