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
- Node.js 18+，当前环境已验证 Node.js 22 可用
- MySQL 8.x

### JAVA_HOME 配置

确保 `JAVA_HOME` 指向 JDK 17：

```bash
# macOS / Linux（添加到 ~/.zshrc 或 ~/.bashrc）
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Windows 下在系统环境变量中设置 `JAVA_HOME` 为 JDK 17 安装路径。

Windows PowerShell 下建议使用 `npm.cmd`，避免 `npm.ps1` 被执行策略拦截。

## 2. 启动数据库

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

## 3. 启动后端与 Gateway

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

## 4. 启动前端

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

## 5. 登录后台

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

## 6. 常用页面

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

## 7. 构建前端

进入 `frontend` 目录执行：

```powershell
npm.cmd run build
```

构建产物输出到：

```text
frontend/dist
```

## 8. 后端测试

进入 `backend/` 目录执行：

```powershell
cd backend
mvn test
```

## 9. 端口占用检查

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

## 10. 推荐启动顺序

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
