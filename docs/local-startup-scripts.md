# 本地前后端启动脚本

本文档记录本项目的本地启动方式。下次需要启动时，直接查看本文档即可，不需要重新扫描代码库。

## 一键启动

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-full.ps1
```

启动后访问：

```text
前端：http://localhost:5173
网关：http://localhost:8080
后端 app：http://localhost:8081
论坛服务：http://localhost:8082
```

常用接口检查：

```text
http://localhost:8080/api/public/releases
http://localhost:8080/api/forum/posts
```

## 分别启动

后端 app 使用 `target` 目录下已经打好的可执行 jar：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-backend-jar.ps1
```

Gateway 使用自己的可执行 jar；默认 profile 将论坛转发到 community-service、其余请求转发到 app，不要求 Nacos：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-gateway-jar.ps1
```

community-service 使用独立可执行 jar：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-community-jar.ps1
```

前端项目位于 `frontend` 目录，使用 Vite 开发服务：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-frontend-dev.ps1
```

## 日志文件

脚本会把服务输出写入项目根目录：

```text
backend-local.out.log
backend-local.err.log
gateway-local.out.log
gateway-local.err.log
community-local.out.log
community-local.err.log
frontend-local.out.log
frontend-local.err.log
```

如果页面打不开，先看这几个日志文件。

## 端口

默认端口：

```text
Gateway：8080
后端 app：8081
community-service：8082
前端：5173
```

脚本启动前会检查默认端口是否已经被监听。如果端口已经在使用，脚本会提示已有 PID 并跳过重复启动。

## 数据库

后端连接本地 MySQL。如果数据库未启动，可先执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-mysql.ps1
```

停止本地 MySQL：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local-mysql.ps1
```
