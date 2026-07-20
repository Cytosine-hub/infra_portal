# 后端微服务化阶段 2：community 服务剥离

## 1. 阶段结果与拓扑

本阶段使用 strangler 模式把论坛从 app 剥离为独立 Spring Boot 应用。`community` 保持纯业务库，新增 `community-service` 作为部署单元；Controller、Service、四份 Forum Mapper XML、API 路径和安全规则均未改写。

```text
Vue / Vite (:5173)
        |
        v
api-gateway (:8080)
        |-- /api/forum/** ----------> community-service (:8082)
        |                               |-- forum_*（领域数据）
        |                               |-- user_tokens/admin_accounts/roles（认证读取）
        |                               `-- api_audit_log（既有横切审计）
        `-- /api/**、/files/** ------> app (:8081，不含 community)
                                        `-- 其余既有模块
```

默认 profile 使用静态地址且三个进程均不连接 Nacos。`cloud` profile 下三个进程注册 Nacos，Gateway 使用 `lb://community-service` 和 `lb://middleware-resource-manager-app`。

## 2. 构建与模块边界

可执行产物：

```text
backend/app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
backend/community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar
backend/api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
```

`community-service` 直接继承聚合根父 POM，对 `common-core`、`common-security`、`common-web` 和 `community` 切断阶段 0 重父 POM 带来的传递依赖，再显式加入 Web/MyBatis/Security/MySQL 与 Nacos starter。因此可执行 JAR 不携带 Tika、POI、Milvus 或 LangChain4j。app 已移除 `community` Maven 依赖，运行时类路径不再包含 `ForumController`，直连 `:8081/api/forum/**` 应返回 404。

认证实现仍是原 Java 链路：`TokenAuthenticationFilter` 调用 `TokenService`，读取 `user_tokens` 并滑动续期，再从 `admin_accounts`、`roles` 恢复用户名和权限。Token/Role Mapper 与 XML 原样归入 `common-security` 供 app 和 community-service 共用；网关未增加集中认证。

论坛 Mapper 使用显式白名单加载，只有以下既有 SQL 资源进入 community-service：

```text
ForumPostMapper.xml
ForumCommentMapper.xml
ForumTagMapper.xml
PostLikeMapper.xml
```

## 3. Profile 与路由

| 行为 | 默认 profile | `cloud` profile |
|---|---|---|
| app | `:8081`，Nacos 关闭 | 注册为 `middleware-resource-manager-app` |
| community-service | `:8082`，Nacos 关闭 | 注册为 `community-service` |
| Gateway | `:8080`，Nacos 关闭 | 注册为 `api-gateway` |
| `/api/forum/**` | `${COMMUNITY_SERVICE_URL:http://127.0.0.1:8082}` | `lb://community-service` |
| 其余 `/api/**`、`/files/**` | `${APP_SERVICE_URL:http://127.0.0.1:8081}` | `lb://middleware-resource-manager-app` |

`community-api` 路由声明在 app 泛路由之前，不剥离或重写路径。`cloud` profile 可选导入以下 Nacos DataId：

```text
middleware-resource-manager-app.properties
community-service.properties
api-gateway.properties
```

三个进程必须使用相同的 `NACOS_NAMESPACE` 和 `NACOS_DISCOVERY_GROUP`。

## 4. 数据归属

一期继续共享 `middleware_resource_manager` MySQL 库，不执行任何 schema 或 DML 迁移。

community-service 逻辑拥有 `forum_posts`、`forum_comments`、`forum_tags`、`forum_post_tags`、`forum_post_likes`。其他业务服务不得直接写这些表。认证读取和既有 API 审计属于共享横切能力，不改变论坛领域表归属。

数据库连接继续使用 `APP_DB_HOST`、`APP_DB_PORT`、`APP_DB_NAME`、`APP_DB_USERNAME`、`APP_DB_PASSWORD`，app 与 community-service 必须指向同一共享库。

## 5. 默认 profile 多进程启动

先构建，再分别启动三个 JAR：

```bash
cd backend
mvn -DskipTests clean package

java -jar app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
java -jar community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
```

三条 `java` 命令需要在不同终端运行。Windows 可在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-full.ps1
```

不启动 Gateway 时，app 与 community-service 也可分别通过以下命令独立启动：

```bash
cd backend
mvn -pl app -am spring-boot:run
mvn -pl community-service -am spring-boot:run
```

## 6. 默认 profile 验证清单

```bash
# app 仍提供非论坛 API
curl -i http://127.0.0.1:8081/api/public/config

# app 已不再提供论坛，应为 404
curl -i http://127.0.0.1:8081/api/forum/posts

# community-service 直连论坛
curl -i http://127.0.0.1:8082/api/forum/posts

# 经 Gateway 访问两条路由
curl -i http://127.0.0.1:8080/api/public/config
curl -i http://127.0.0.1:8080/api/forum/posts
```

认证验证使用 app 既有登录接口取得 Token，再经 Gateway 调论坛写接口；请求与阶段 1 相同，Token 不做格式转换：

```bash
TOKEN='<登录接口返回的 token>'
curl -i -X POST http://127.0.0.1:8080/api/forum/posts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"stage2 verify","content":"community-service"}'
```

验收标准：无 Token 的写请求和 `/api/forum/my-posts` 返回 401；公开 GET 保持 200；有效 Token 可被 community-service 从共享 `user_tokens` 校验。

## 7. `cloud` profile 验证清单

准备 Nacos 3.x，并为三个终端设置相同环境变量：

```bash
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_USERNAME=nacos
export NACOS_PASSWORD='<实际密码>'
export NACOS_NAMESPACE=''
export NACOS_DISCOVERY_GROUP=DEFAULT_GROUP
```

依次启动 app、community-service、Gateway：

```bash
java -Dspring.profiles.active=cloud -jar app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
java -Dspring.profiles.active=cloud -jar community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar
java -Dspring.profiles.active=cloud -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
```

检查 Nacos 中三个服务各有健康实例，端口分别为 8081、8082、8080。随后执行默认 profile 的 Gateway curl 清单，并补充故障验证：停止 community-service 后 `/api/forum/**` 应不可用但 `/api/public/config` 仍正常；恢复 community-service 后论坛路由应随 Nacos 健康实例自动恢复。

## 8. 自动化验收

```bash
cd backend
mvn -q -DskipTests test-compile
mvn test
mvn -DskipTests clean package
```

阶段 2 新增/强化的契约用例：

| TC | 验证点 |
|---|---|
| `TC-APP-002` | app 类路径不含 ForumController |
| `TC-COMMUNITY-001` | 默认 profile 独立上下文、8082、Nacos 关闭 |
| `TC-COMMUNITY-002` | forum GET 公开 |
| `TC-COMMUNITY-003` | forum POST 未认证 401 |
| `TC-COMMUNITY-004` | my-posts 未认证 401 |
| `TC-COMMUNITY-005` | 共享库有效 Bearer Token 可访问写接口 |
| `TC-GATEWAY-001` | 默认静态论坛路由指向 8082 |
| `TC-GATEWAY-002` | cloud 论坛与 app 分别使用 lb 路由 |

当前执行沙箱可能禁止监听端口；若无法完成 curl/Nacos 联通，只执行构建和上下文测试，并在外部可联网、可监听环境按本清单复验。

### 8.1 本地执行结果（2026-07-20）

- `mvn -q -DskipTests test-compile`：通过。
- `mvn test`：通过，全部 20 个 Reactor 模块 `BUILD SUCCESS`；Surefire XML 合计 91 个测试，0 失败、0 错误、0 跳过。
- community-service 依赖树检查：未引入 Tika、POI、Milvus 或 LangChain4j。
- `mvn -DskipTests clean package`：Java 编译与前置模块通过，在 api-gateway 的 `spring-boot-maven-plugin:3.5.3:repackage` 阶段因本机 Maven 缓存缺少 `org.springframework.boot:spring-boot-buildpack-platform:3.5.3` 而停止；沙箱 Maven 仓库只读且不可联网补齐。未改用其他 Spring Boot 版本规避该环境问题，需在可联网或已缓存完整 3.5.3 插件依赖的环境重新执行此命令，确认三个 `*-exec.jar`。
- 沙箱禁止监听端口，因此未执行真实进程、Nacos 注册和 curl 联通；按第 6、7 节在外部环境复验。

## 9. 回滚

代码回滚时需要同时恢复 app 对 `community` 的依赖并删除 Gateway 的 `community-api` 路由；仅停止 community-service 会让论坛不可用，不能作为完整回滚。阶段 2 没有数据库变更，因此回滚不涉及数据迁移。
