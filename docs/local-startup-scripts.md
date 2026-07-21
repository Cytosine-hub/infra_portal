# 本地前后端启动脚本

## 一键启动

先在 `backend/` 执行 `mvn -DskipTests package` 生成 9 个可执行 JAR，再从项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-full.ps1
```

启动地址：

| 进程 | 地址 |
|------|------|
| api-gateway | http://localhost:8080 |
| community-service | http://localhost:8082 |
| ai-service | http://localhost:8083 |
| core-service | http://localhost:8084 |
| middleware-service | http://localhost:8085 |
| database-service | http://localhost:8086 |
| host-service | http://localhost:8087 |
| network-service | http://localhost:8088 |
| security-service | http://localhost:8089 |
| 前端 | http://localhost:5173 |

`8081` 不再使用；`app` 已退役。

## 分别启动

平台服务沿用各自脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-community-jar.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-ai-jar.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-core-jar.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-gateway-jar.ps1
```

岗位服务使用统一脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-job-service-jar.ps1 -ServiceName middleware-service -Port 8085
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-job-service-jar.ps1 -ServiceName database-service -Port 8086
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-job-service-jar.ps1 -ServiceName host-service -Port 8087
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-job-service-jar.ps1 -ServiceName network-service -Port 8088
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-job-service-jar.ps1 -ServiceName security-service -Port 8089
```

前端：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-frontend-dev.ps1
```

## 健康检查

```text
http://localhost:8086/health
http://localhost:8087/health
http://localhost:8088/health
http://localhost:8089/health
http://localhost:8080/api/middleware-commands/types
```

Gateway 默认 profile 将中间件命令转发到 `middleware-service:8085`。其余 4 个岗位服务暂无业务路径，不配置网关路由。

## 日志

脚本将日志写到项目根目录，岗位服务命名为 `<service>-local.out.log` 和 `<service>-local.err.log`。已有平台服务继续使用 `gateway/community/ai/core-local.*.log`，前端使用 `frontend-local.*.log`。

## 数据库与密钥

所有业务服务连接同一 MySQL。数据库密码由 `APP_DB_PASSWORD` 提供；网关及业务服务必须使用同一个至少 32 UTF-8 字节的 `GATEWAY_SIGNING_SECRET`。仓库不提供真实默认值。

数据库脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-mysql.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local-mysql.ps1
```
