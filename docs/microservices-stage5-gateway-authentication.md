# 后端微服务化阶段 5：网关集中认证说明与安全验证清单

## 1. 范围与结论

阶段 5 将认证权威收敛到 `api-gateway` 和 core-service 内的 identity：客户端仍使用原 Bearer Token，Gateway 负责集中校验，identity 独占 token 表、角色表和岗位权限事实，下游服务只接受 Gateway 签名的身份头。数据库 schema/DML、前端、业务 Controller 语义和 `SecurityConfig` 的路径授权集合均未改变。

安全结论：

1. 直连 app、community-service、ai-service 或 core-service 的受保护业务端点，仅发送 `X-Roles: ROLE_SYS_ADMIN` 而没有正确 `X-Gateway-Sign`，不会建立 `Authentication`，最终返回 401。
2. 客户端经 Gateway 发送伪造的 `X-User`、`X-Roles`、`X-Category`、`X-Category-Admin`、`X-Display-Name` 或 `X-Gateway-Sign` 时，GlobalFilter 先删除全部这些头，再按 Bearer Token 的 introspect 结果重新注入，客户端值不会进入下游。
3. `POST /api/auth/introspect` 缺少或携带错误 `X-Gateway-Sign` 时，identity 在读取 token 表之前返回 403；通过 Gateway 外部访问该路径则由 Gateway 直接返回 404，不转发。

服务端口必须只暴露在受控内网，公网只暴露 Gateway。HMAC 防止伪造，但不能替代网络 ACL、TLS；高安全环境建议服务间使用 mTLS。

## 2. 请求链路

```text
client --Bearer token--> api-gateway
                         | 1. 删除所有客户端身份头
                         | 2. 短 TTL 缓存未命中
                         v
                       core-service /api/auth/introspect
                         | X-Gateway-Sign = HMAC(token)
                         | TokenService 校验并滑动续期
                         | RoleService/PermissionService 形成身份
                         v
api-gateway <-- {valid, username, displayName, roles, category, categoryAdmin}
     | 3. 签名并注入身份头
     v
downstream GatewayHeaderAuthenticationFilter --验签--> Spring Security Authentication
```

默认 profile 的 introspect 基址为 `${CORE_SERVICE_URL:http://127.0.0.1:8084}`。`cloud` profile 使用带 `@LoadBalanced` 的 WebClient 访问 `http://core-service/api/auth/introspect`，由 Nacos 发现实例；业务路由仍为 `lb://core-service`。

## 3. identity 内部接口

请求：

```http
POST /api/auth/introspect
Content-Type: application/json
X-Gateway-Sign: <token 专用 HMAC>

{"token":"<bearer-token>"}
```

有效响应包含：

```json
{
  "valid": true,
  "username": "alice",
  "displayName": "Alice",
  "roles": ["ROLE_MIDDLEWARE_ADMIN"],
  "category": "中间件",
  "categoryAdmin": true
}
```

`categoryAdmin` 是为完整保持原有「岗位管理员可审核、管理岗只可修改」语义增加的字段。无效或过期 token 返回 `valid=false`；有效校验继续复用 `TokenService.validateToken`，因此原两小时滑动续期语义不变。

## 4. HMAC 协议

- 算法：HMAC-SHA256。
- 编码：密钥和载荷均为 UTF-8；结果为小写十六进制。
- 密钥：所有五个进程读取同一个 `GATEWAY_SIGNING_SECRET`，至少 32 UTF-8 字节；生产配置没有真实默认值，缺失或过短时应用启动失败。
- 比较：使用常量时间字节比较。

introspect 的签名载荷为：

```text
introspect
<token>
```

下游身份签名覆盖完整头集合，字段标签、换行和顺序均固定；空值按空字符串计算：

```text
identity
user
<X-User>
displayName
<X-Display-Name>
roles
<X-Roles>
category
<X-Category>
categoryAdmin
<X-Category-Admin>
```

Gateway 下发：`X-User`、`X-Display-Name`、`X-Roles`（逗号分隔）、`X-Category`、`X-Category-Admin`（`true`/`false`）和 `X-Gateway-Sign`。非 Java 服务只需按上述规范验签并解析这些头，不需要访问 token/角色数据库。

## 5. 公开路径与缓存

Gateway 对以下请求放行且不 introspect、不注入身份：所有 `OPTIONS`、`/api/public/**`、`/files/**`、除 `/api/forum/my-posts` 外的 `GET /api/forum/**`、`GET /api/middleware-commands/**`、`POST /api/auth/login`。即使是公开请求，客户端伪造的身份头也会先被删除。

其他路径必须有 Bearer Token。introspect 结果按 token 缓存，默认 TTL 15 秒、最大 10000 项，可用 `GATEWAY_AUTH_CACHE_TTL` 和 `GATEWAY_AUTH_CACHE_MAX_SIZE` 调整。角色或岗位变更最多在 TTL 后生效；`POST /api/auth/logout` 转发完成后立即淘汰对应 token。TTL 应保持短小，不能作为长期会话缓存。

## 6. 权威归属与模块边界

- identity 保留 `TokenService`、`UserTokenMapper`、`RoleService`、`RoleMapper` 及 XML；登录、退出和 introspect 继续使用它们。
- `common-security` 不再包含 token/角色 Mapper 或各服务的 UserDetailsService 适配器，只提供 `GatewayHeaderAuthenticationFilter`、`GatewayAuthenticationToken` 和基于签名上下文的 `PermissionService`。
- app、community-service、ai-service 和 core-service 的业务端点都使用同一过滤器。除 identity 自身外，下游类路径不包含 `TokenService`。
- `ROLE_SYS_ADMIN`、岗位 category 和 `categoryAdmin` 均由 identity 生成；下游不得从本地数据库重算或接受未签名值。

## 7. 自动化安全用例

| 用例 | 覆盖内容 |
|------|----------|
| `TC-IDENTITY-001..004` | 有效/过期 token、缺签名、错签名 |
| `TC-GATEWAY-003..010` | 伪造头清洗、无 token 401、正确注入与签名、公开路径、无效 token、缓存、内部路径阻断 |
| `TC-SECURITY-001..005` | 无签名/错签名拒绝、正确签名认证、角色篡改、角色与岗位权限 |
| `TC-CORE-006..008` | core 签名身份访问、introspect 无签名 403、角色路径授权不变 |
| `TC-COMMUNITY-005..007`、`TC-APP-007`、`TC-AI-003` | 下游签名身份、直连伪造拒绝、旧 TokenService 移除 |
| `TC-OPS-SEC-001..004`、`TC-AGENT-SEC-001..003` | 原控制器角色/岗位与用户隔离语义保持 |

最终验收命令：

```bash
cd backend
mvn -q -DskipTests test-compile
mvn test
mvn -DskipTests clean package
```

沙箱不能启动 Nacos 或五个监听进程，因此真实联通和端口隔离必须在外部环境按下一节复验。

## 8. 外部真实环境验证

先生成并只通过密钥管理系统注入同一个高熵密钥，禁止写入仓库或命令历史：

```bash
export GATEWAY_SIGNING_SECRET='<至少 32 UTF-8 字节的随机密钥>'
```

默认 profile 启动 Gateway `:8080`、app `:8081`、community `:8082`、ai `:8083`、core `:8084`。cloud profile 还需配置相同的 Nacos 地址、namespace/group，确认五个服务健康注册，并确认 Gateway 日志没有 introspect 解析或负载均衡错误。

登录和正常鉴权：

```bash
curl -i -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Authorization: Basic $(printf '%s' "$TEST_USERNAME:$TEST_PASSWORD" | base64)"

export TOKEN='<从登录响应取得的 token>'
curl -i http://127.0.0.1:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

安全验证 1，直连下游伪造系统管理员，预期 `401`：

```bash
curl -i http://127.0.0.1:8084/api/admin/users \
  -H 'X-User: mallory' \
  -H 'X-Roles: ROLE_SYS_ADMIN' \
  -H 'X-Category: 安全'
```

对 `:8081`、`:8082`、`:8083` 的任一受保护路径重复该尝试，也必须是 `401`。部署层同时应限制这些端口不能从外网访问。

安全验证 2，经 Gateway 伪造身份头，预期响应身份仍属于 `$TOKEN` 的真实用户，不能变成 `mallory/ROLE_SYS_ADMIN`：

```bash
curl -i http://127.0.0.1:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-User: mallory' \
  -H 'X-Roles: ROLE_SYS_ADMIN' \
  -H 'X-Category: 安全' \
  -H 'X-Gateway-Sign: forged'
```

安全验证 3，直连 introspect 但不带签名，预期 `403`；经 Gateway 访问则预期 `404`：

```bash
curl -i -X POST http://127.0.0.1:8084/api/auth/introspect \
  -H 'Content-Type: application/json' \
  --data "{\"token\":\"$TOKEN\"}"

curl -i -X POST http://127.0.0.1:8080/api/auth/introspect \
  -H 'Content-Type: application/json' \
  --data "{\"token\":\"$TOKEN\"}"
```

最后验证过期 token 经 Gateway 访问受保护端点返回 401；修改用户角色后，在缓存 TTL 到期后权限按 identity 新角色生效；退出后同一 token 再访问立即返回 401。

## 9. 运维注意事项

- 密钥轮换需要 Gateway 和四个下游进程协调滚动；当前协议只接受单密钥，轮换窗口应先阻断外部流量或一次性重启。若需无损双密钥轮换，应另行扩展 key id 和双验签窗口。
- 不记录 token、身份签名或密钥。认证失败日志只记录必要的请求定位信息。
- HMAC 签名不是授权数据的持久凭证；不要把身份头写入消息队列或跨请求复用。
- 监控 introspect 延迟、错误率、401 比例和缓存命中率；identity 不可用时 Gateway 对受保护路径按未认证失败关闭。
