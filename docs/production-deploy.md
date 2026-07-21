# 生产环境部署手册

> 本文主体保留早期单体直连部署说明，仅供历史环境参考。当前阶段 6 为 9 进程拓扑，`app` 与 `:8081` 已退役；新部署以 `docs/microservices-stage6-job-services.md` 和 `scripts/package-for-deploy.sh` 为准。

## 1. 架构概览

```
                    ┌─────────────┐
  用户 ────────────►│   Nginx:80  │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │ 静态文件    │ /api/      │ /files/
              ▼            ▼            ▼
         frontend/dist  Spring Boot   Spring Boot
                        :8080         :8080
                           │
                    ┌──────┴──────┐
                    │  MySQL:3306 │
                    └─────────────┘
```

- 前端：Vue 3 构建产物，Nginx 托管静态文件
- 后端：Spring Boot JAR，Nginx 反向代理 `/api/` 和 `/files/`
- 数据库：MySQL 8.0

## 2. 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17 | Spring Boot 3.x 必须 |
| MySQL | 8.0+ | |
| Nginx | 1.18+ | |
| Maven | 3.8+ | 构建后端用 |
| Node.js | 18+ | 构建前端用 |

## 3. 构建

### 3.1 构建后端

```bash
cd /path/to/middleware_resource_manager/backend
mvn clean package -DskipTests
# 产物：9 个 `backend/*-service/target/*-exec.jar`（含 api-gateway，无 app）
```

### 3.2 构建前端

```bash
cd frontend
npm install
npm run build
# 产物：frontend/dist/
```

## 4. 服务器部署

### 4.1 目录结构

```
/opt/middleware-resource-manager/
├── backend/
│   ├── middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
│   ├── data/skills/          # 经验 Skill YAML（运行时生成）
│   └── storage/              # 上传文件存储
├── frontend/
│   └── dist/                 # Vue 构建产物
└── logs/                     # 应用日志（可选）
```

### 4.2 数据库初始化

首次部署使用全量脚本：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS middleware_resource_manager"
mysql -u root middleware_resource_manager < release/db/full_schema.sql
mysql -u root middleware_resource_manager < release/db/seed_data.sql
```

后续升级使用增量脚本（按版本顺序执行）：

```bash
mysql -u root middleware_resource_manager < release/db/upgrade-v1.0.4.sql
```

> 应用启动时 MyBatis 不会自动建表，必须手动执行 DDL 脚本。

### 4.3 启动后端

```bash
cd /opt/middleware-resource-manager/backend

# 生产环境推荐配置
java -jar middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar \
  --server.port=8080 \
  --spring.jpa.open-in-view=false \
  --app.modules.knowledge-enabled=false \
  --app.modules.diagnostics-enabled=false
```

**隐藏知识库和智能排查菜单**：设置 `app.modules.knowledge-enabled=false` 和 `app.modules.diagnostics-enabled=false`。

也可以通过环境变量：

```bash
export APP_MODULES_KNOWLEDGE_ENABLED=false
export APP_MODULES_DIAGNOSTICS_ENABLED=false
java -jar middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
```

### 4.4 systemd 服务（推荐）

```bash
sudo vi /etc/systemd/system/middleware-resource-manager.service
```

```ini
[Unit]
Description=Middleware Resource Manager
After=network.target mysql.service

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/middleware-resource-manager/backend
ExecStart=/usr/bin/java -jar middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar \
  --server.port=8080 \
  --app.modules.knowledge-enabled=false \
  --app.modules.diagnostics-enabled=false
Environment=APP_DB_HOST=127.0.0.1
Environment=APP_DB_PASSWORD=your_db_password
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable middleware-resource-manager
sudo systemctl start middleware-resource-manager
```

## 5. Nginx 配置

```bash
sudo vi /etc/nginx/conf.d/middleware-resource-manager.conf
```

```nginx
# 后端 upstream（可配置多个实现负载均衡）
upstream backend {
    server 127.0.0.1:8080;
    # 多实例时添加更多 server：
    # server 127.0.0.1:8081;
    # server 127.0.0.1:8082;
    keepalive 32;
}

server {
    listen 80;
    server_name your-domain.com;

    root /opt/middleware-resource-manager/frontend/dist;
    index index.html;

    # 文件上传大小限制
    client_max_body_size 2048m;

    # ===== 静态资源（前端） =====
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|svg|ico|woff2?)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # ===== API 代理 =====
    location /api/ {
        proxy_pass http://backend/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";

        # 超时设置（Agent 排查可能耗时较长）
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;

        # 连接复用
        proxy_buffering on;
        proxy_buffer_size 8k;
        proxy_buffers 8 8k;
    }

    # ===== 文件下载代理 =====
    location /files/ {
        proxy_pass http://backend/files/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";

        # 大文件下载：关闭缓冲，支持断点续传
        proxy_buffering off;
        proxy_request_buffering off;

        # 下载超时
        proxy_read_timeout 600s;

        # 支持 Range 请求（断点续传）
        proxy_set_header Range $http_range;
        proxy_set_header If-Range $http_if_range;
    }
}
```

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## 6. 并发下载性能优化

### 6.1 Nginx 层优化

- **`proxy_buffering off`**（文件下载）：Nginx 不缓存响应体，直接流式转发，减少内存占用
- **`keepalive 32`**：复用后端连接，减少 TCP 握手开销
- **静态资源缓存**：`expires 30d` + `Cache-Control: immutable`，减少重复请求
- **多 upstream**：高并发时可启动多个后端实例，Nginx 轮询分发

### 6.2 Spring Boot 层优化

在 `application.yml` 中调整 Tomcat 参数：

```yaml
server:
  tomcat:
    max-threads: 200          # 最大工作线程数（默认 200）
    min-spare-threads: 20     # 最小空闲线程
    max-connections: 8192     # 最大连接数
    accept-count: 100         # 等待队列长度
```

### 6.3 MySQL 连接池

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### 6.4 文件下载优化（已实现）

项目已支持：
- 断点续传（Range 请求）
- 流式下载（不加载整个文件到内存）
- 下载进度显示

## 7. 环境变量清单

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `APP_DB_HOST` | MySQL 地址 | 127.0.0.1 |
| `APP_DB_PORT` | MySQL 端口 | 3306 |
| `APP_DB_NAME` | 数据库名 | middleware_resource_manager |
| `APP_DB_USERNAME` | 数据库用户 | root |
| `APP_DB_PASSWORD` | 数据库密码 | （配置文件默认值） |
| `APP_MODULES_KNOWLEDGE_ENABLED` | 知识库模块开关 | true |
| `APP_MODULES_DIAGNOSTICS_ENABLED` | 智能排查模块开关 | true |
| `AI_BASE_URL` | AI 模型地址 | xiaomimimo |
| `AI_API_KEY` | AI 模型密钥 | （配置文件默认值） |
| `VECTOR_HOST` | Milvus 地址 | localhost |
| `VECTOR_PORT` | Milvus 端口 | 19530 |
| `ZABBIX_URL` | Zabbix 地址 | localhost:8080 |
| `ZABBIX_USERNAME` | Zabbix 用户 | Admin |
| `ZABBIX_PASSWORD` | Zabbix 密码 | zabbix |

## 8. 验证

```bash
# 后端健康检查
curl http://127.0.0.1:8080/api/public/releases

# 前端页面
curl -I http://your-domain.com/

# 模块开关验证（应返回 knowledgeEnabled: false）
curl http://your-domain.com/api/public/config

# 文件下载
curl -I http://your-domain.com/files/{token}
```

## 9. 日志

```bash
# systemd 日志
sudo journalctl -u middleware-resource-manager -f

# 应用日志（如果配置了文件输出）
tail -f /opt/middleware-resource-manager/backend/logs/middleware-resource-manager.log
```

## 10. 更新部署

### 10.1 增量升级（推荐）

每次发布包含增量升级脚本 `release/db/upgrade-vX.X.X.sql`，只需执行自上次版本以来的脚本。

```bash
# 1. 执行增量数据库升级（如有）
mysql -u root middleware_resource_manager < db/upgrade-v1.0.4.sql

# 2. 替换后端 JAR
sudo systemctl stop middleware-resource-manager
cp backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar /opt/middleware-resource-manager/backend/
sudo systemctl start middleware-resource-manager

# 3. 替换前端
rm -rf /opt/middleware-resource-manager/frontend/dist
cp -r frontend/dist /opt/middleware-resource-manager/frontend/
sudo systemctl reload nginx
```

> 增量 SQL 使用 `CREATE TABLE IF NOT EXISTS` 和 `INSERT IGNORE`，重复执行不会产生副作用。部分 `ALTER TABLE` 语句如已执行会报 Duplicate 错误，可安全忽略。

### 10.2 全量部署（全新安装）

首次部署或需要重建数据库时，使用 `release/db/full_schema.sql` + `release/db/seed_data.sql`：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS middleware_resource_manager"
mysql -u root middleware_resource_manager < db/full_schema.sql
mysql -u root middleware_resource_manager < db/seed_data.sql
```

### 10.3 仅构建替换

```bash
# 构建新版本
(cd backend && mvn clean package -DskipTests)
cd frontend && npm run build && cd ..

# 替换后端 JAR
sudo systemctl stop middleware-resource-manager
cp backend/app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar /opt/middleware-resource-manager/backend/
sudo systemctl start middleware-resource-manager

# 替换前端
rm -rf /opt/middleware-resource-manager/frontend/dist
cp -r frontend/dist /opt/middleware-resource-manager/frontend/
sudo systemctl reload nginx
```
