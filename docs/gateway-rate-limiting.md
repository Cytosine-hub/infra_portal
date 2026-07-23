# Gateway 下载、文档与论坛详情限流

## 目标与边界

`api-gateway` 使用进程内固定窗口计数器，降低高频下载、标准文档详情/文件访问和论坛文章详情访问对 Gateway 与下游服务的资源占用。

- 计数维度：接口组 + 客户端 IP。
- 默认窗口：60 秒固定窗口。
- 超限响应：`429 Too Many Requests`，并返回剩余窗口秒数 `Retry-After`。
- 部署边界：仅适用于单 Gateway 实例，不提供多实例统一额度。
- 能力边界：不限制下载带宽、传输速率或长连接并发数。

## 接口分组

仅对以下 `GET` 请求计数：

| 接口组 | 路径 | 默认额度 |
|---|---|---:|
| 下载 | `/files/{token}`、`/files/{middlewareName}/{fileName}` | 6 次/窗口/IP |
| 文档详情 | `/api/public/standards/{id}`、`/api/admin/standard-documents/{id}` | 60 次/窗口/IP |
| 文档文件 | `/api/public/standards/preview`、`/api/public/standards/raw`、`/api/admin/standard-documents/{id}/preview`、`/api/admin/standard-documents/{id}/raw` | 18 次/窗口/IP |
| 论坛文章详情 | `/api/forum/posts/{id}` | 120 次/窗口/IP |

以下请求不计数：下载目录中的 `/files/images/**` 页面图片、文章和文档列表、上传、编辑、评论、点赞及其他非 `GET` 请求。

## 配置

默认配置位于 `backend/api-gateway/src/main/resources/application.yml`：

```yaml
app:
  rate-limit:
    enabled: true
    window-seconds: 60
    max-client-keys: 10000
    download-per-window: 6
    document-per-window: 60
    document-file-per-window: 18
    forum-post-per-window: 120
```

对应环境变量：

| 配置项 | 环境变量 |
|---|---|
| `enabled` | `GATEWAY_RATE_LIMIT_ENABLED` |
| `window-seconds` | `GATEWAY_RATE_LIMIT_WINDOW_SECONDS` |
| `max-client-keys` | `GATEWAY_RATE_LIMIT_MAX_CLIENT_KEYS` |
| `download-per-window` | `GATEWAY_RATE_LIMIT_DOWNLOAD_PER_WINDOW` |
| `document-per-window` | `GATEWAY_RATE_LIMIT_DOCUMENT_PER_WINDOW` |
| `document-file-per-window` | `GATEWAY_RATE_LIMIT_DOCUMENT_FILE_PER_WINDOW` |
| `forum-post-per-window` | `GATEWAY_RATE_LIMIT_FORUM_POST_PER_WINDOW` |

Cloud profile 可由 Nacos 在应用启动时提供同名 `app.rate-limit.*` 配置。

`max-client-keys` 限制“接口组 + IP”计数键总量。达到容量后淘汰最久未访问的键，避免异常客户端数量导致进程内存无限增长。

## 过滤器顺序与认证

认证过滤器顺序为 `Ordered.HIGHEST_PRECEDENCE`，限流过滤器为其后一位。管理端等受保护请求若未认证，会先返回原有 `401`，不会进入限流过滤器或消耗计数。

当前 `/files/**` 下载、公开文档和论坛只读接口沿用既有公开访问行为；限流不改变这些接口的认证策略。下载页使用普通链接访问 `/files/{token}`，浏览器无需携带 Bearer Token，认证过滤器会直接转发给后置限流过滤器；达到下载额度后仍返回 `429`。客户端即使携带 Bearer Token，计数维度也仅使用接口组和 IP。

后台标准文档详情、预览和原文件接口需要有效登录态。未认证请求会先由认证过滤器返回 `401`，不会进入限流过滤器或消耗对应文档接口组额度。

## 客户端 IP

Gateway 先检查 TCP 对端地址。仅当对端是本机回环地址（当前 Nginx 与 Gateway 同机部署）时，才按以下顺序读取代理传递的客户端 IP：

1. `X-Real-IP`；
2. `X-Forwarded-For` 的第一个地址；
非本机直连请求忽略上述转发头，直接使用 TCP 远端地址；无远端地址时使用 `unknown`。

生产环境必须只允许受信任的本机反向代理访问 Gateway，并由代理覆盖 `X-Real-IP` 和 `X-Forwarded-For`。现有 Nginx 部署示例已设置这两个请求头。若未来将反向代理与 Gateway 分机部署，应增加明确的受信代理网段配置后再信任远端代理头，不能直接放宽为信任所有来源。

## 多实例与大文件

多 Gateway 实例会分别计数，无法形成统一全局额度。需要多实例统一额度、按用户/IP/资源多维计数或更平滑的突发控制时，应迁移到 Redis 分布式令牌桶。

大文件传输造成资源挤占时，应在 Nginx 等反向代理层增加单 IP 并发下载数和传输速率限制；本过滤器只限制请求次数。
