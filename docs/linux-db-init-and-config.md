# Linux 服务器部署手册：数据库初始化与参数配置

本文档基于当前项目实际代码整理，适用于将 `middleware-resource-manager` 部署到 Linux 服务器。

## 1. 适用版本

- JDK：17
- 数据库：MySQL 8.x
- 应用包：`backend/app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar`

## 2. 当前项目涉及的数据库与配置项

### 2.1 数据库连接参数

项目当前从以下环境变量读取数据库配置：

- `APP_DB_HOST`，默认 `127.0.0.1`
- `APP_DB_PORT`，默认 `3306`
- `APP_DB_NAME`，默认 `middleware_resource_manager`
- `APP_DB_USERNAME`，默认 `root`
- `APP_DB_PASSWORD`，默认 `OlgDqdJfehRwBUITqFpi`

对应应用配置见 `backend/app/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${APP_DB_HOST:127.0.0.1}:${APP_DB_PORT:3306}/${APP_DB_NAME:middleware_resource_manager}?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: ${APP_DB_USERNAME:root}
    password: ${APP_DB_PASSWORD:OlgDqdJfehRwBUITqFpi}
```

### 2.2 当前数据表

业务表由 JPA 自动维护（`ddl-auto: update`），首次启动自动创建：

| 表名 | 用途 |
|------|------|
| `release_assets` | 中间件资源文件 |
| `software_categories` | 软件分类 |
| `software_types` | 软件类型（属于某个分类） |
| `parameter_standards` | 参数标准（独立实体，含版本管理） |
| `standard_parameters` | 标准参数键值对（关联参数标准） |
| `standard_documents` | 标准文档（手册/文章） |
| `review_records` | 审核记录 |
| `admin_accounts` | 管理员账号 |
| `forum_posts` | 论坛帖子 |
| `forum_comments` | 论坛评论 |
| `forum_tags` | 论坛标签 |
| `post_likes` | 帖子点赞 |

知识库相关表需手动执行 DDL（见第 4.3 节）。

JPA 配置：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

含义：

- 第一次启动时，如果库里没有对应表，应用会自动创建表
- 后续字段变更时，Hibernate 会尝试自动更新表结构

如果你希望生产环境完全手工控表，需要把 `ddl-auto` 改为 `validate` 或 `none`，并自行维护 SQL。当前项目默认不是这种模式。

## 3. Linux 服务器目录规划建议

建议目录：

```text
/opt/middleware-resource-manager/
├─ app/
│  └─ middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
├─ storage/
├─ logs/
└─ conf/
```

建议说明：

- `app/`：放应用 JAR
- `storage/`：放上传文件
- `logs/`：放应用日志
- `conf/`：如后续需要放 systemd 环境文件，可放这里

## 4. MySQL 初始化步骤

### 4.1 创建数据库

建议显式创建数据库，而不是完全依赖 `createDatabaseIfNotExist=true`。

登录 MySQL：

```bash
mysql -uroot -p
```

执行：

```sql
CREATE DATABASE IF NOT EXISTS middleware_resource_manager
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

### 4.2 创建业务账号

不建议生产环境继续用 `root` 直连应用。

示例：

```sql
CREATE USER IF NOT EXISTS 'middleware_mgr'@'%' IDENTIFIED BY 'ReplaceWithStrongPassword';
GRANT ALL PRIVILEGES ON middleware_resource_manager.* TO 'middleware_mgr'@'%';
FLUSH PRIVILEGES;
```

如果应用和数据库部署在同一台机器上，也可以限制来源：

```sql
CREATE USER IF NOT EXISTS 'middleware_mgr'@'localhost' IDENTIFIED BY 'ReplaceWithStrongPassword';
GRANT ALL PRIVILEGES ON middleware_resource_manager.* TO 'middleware_mgr'@'localhost';
FLUSH PRIVILEGES;
```

### 4.3 初始化表结构

业务表由 Hibernate 自动创建，无需手动执行 SQL。

知识库相关表需手动执行 DDL：

```bash
mysql -u root -p middleware_resource_manager < backend/knowledge/src/main/resources/db/knowledge_ddl.sql
```

启动完成后，可以执行检查：

```sql
USE middleware_resource_manager;
SHOW TABLES;
```

应至少看到：`release_assets`、`software_categories`、`software_types`、`parameter_standards`、`standard_parameters`、`standard_documents`、`review_records`、`admin_accounts`、`forum_posts`、`forum_comments`、`forum_tags`、`post_likes`、`knowledge_chunks`、`chat_sessions`、`chat_messages`。

## 5. 应用参数配置手册

## 5.1 必配参数

### 数据库参数

```bash
export APP_DB_HOST=127.0.0.1
export APP_DB_PORT=3306
export APP_DB_NAME=middleware_resource_manager
export APP_DB_USERNAME=middleware_mgr
export APP_DB_PASSWORD='ReplaceWithStrongPassword'
```

### 管理员账号参数

管理员账号存储在 `admin_accounts` 表中，密码使用 bcrypt 加密。

系统首次启动时，如果 `admin_accounts` 表为空，会自动初始化以下默认账号（密码均为 `admin123`）：

| 用户名 | 角色 |
|--------|------|
| `sysadmin` | 系统管理员 |
| `mwadmin` | 中间件管理岗 |
| `dbadmin` | 数据库管理岗 |
| `hostadmin` | 主机管理岗 |
| `netadmin` | 网络管理岗 |
| `secadmin` | 网络安全岗 |
| `devmgr` | 开发经理 |
| `opsmgr` | 运维经理 |

**上线后必须立即修改所有默认密码。**

### 存储目录参数

当前默认上传目录是：

```yaml
app:
  storage:
    location: ./storage
```

Linux 部署建议改为绝对路径：

```bash
export APP_STORAGE_LOCATION=/opt/middleware-resource-manager/storage
```

说明：

- 如果不改，默认相对路径 `./storage` 将相对于启动目录创建
- 生产环境建议固定为绝对路径，避免 systemd 启动目录变化导致文件落错位置

## 5.2 可选参数

### 端口

默认：

```yaml
server:
  port: 8080
```

如需修改：

```bash
export SERVER_PORT=8080
```

### 上传文件大小限制

当前配置：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2048MB
      max-request-size: 2048MB
```

如果 Linux 服务器前面还有 Nginx，请同步设置：

```nginx
client_max_body_size 2048m;
```

否则应用虽然允许上传 2GB，代理层也会提前拒绝。

### Thymeleaf 缓存

当前配置：

```yaml
spring:
  thymeleaf:
    cache: false
```

这是开发友好配置。生产环境如果页面改动不频繁，建议改为：

```yaml
spring:
  thymeleaf:
    cache: true
```

如果不改，也能运行，只是模板每次请求都会重新解析。

## 6. Linux 启动前准备

### 6.1 创建目录

```bash
mkdir -p /opt/middleware-resource-manager/app
mkdir -p /opt/middleware-resource-manager/storage
mkdir -p /opt/middleware-resource-manager/logs
```

### 6.2 上传 JAR

把打包产物上传到：

```text
/opt/middleware-resource-manager/app/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
```

### 6.3 设置目录权限

假设应用运行用户为 `appuser`：

```bash
chown -R appuser:appuser /opt/middleware-resource-manager
chmod -R 755 /opt/middleware-resource-manager
```

上传目录必须确保应用用户可写：

```bash
chmod -R 775 /opt/middleware-resource-manager/storage
```

## 7. Linux 启动命令示例

## 7.1 直接前台启动

```bash
export APP_DB_HOST=127.0.0.1
export APP_DB_PORT=3306
export APP_DB_NAME=middleware_resource_manager
export APP_DB_USERNAME=middleware_mgr
export APP_DB_PASSWORD='ReplaceWithStrongPassword'
export APP_STORAGE_LOCATION=/opt/middleware-resource-manager/storage
export SERVER_PORT=8080

java -jar /opt/middleware-resource-manager/app/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
```

## 7.2 后台启动示例

```bash
nohup java -jar /opt/middleware-resource-manager/app/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar \
  > /opt/middleware-resource-manager/logs/app.out.log \
  2> /opt/middleware-resource-manager/logs/app.err.log &
```

## 7.3 推荐的 systemd 方式

建议创建环境文件：

```bash
cat >/opt/middleware-resource-manager/conf/app.env <<'EOF'
APP_DB_HOST=127.0.0.1
APP_DB_PORT=3306
APP_DB_NAME=middleware_resource_manager
APP_DB_USERNAME=middleware_mgr
APP_DB_PASSWORD=ReplaceWithStrongPassword
APP_STORAGE_LOCATION=/opt/middleware-resource-manager/storage
SERVER_PORT=8080
EOF
```

创建 systemd 服务：

```ini
[Unit]
Description=Middleware Resource Manager
After=network.target mysqld.service

[Service]
User=appuser
WorkingDirectory=/opt/middleware-resource-manager/app
EnvironmentFile=/opt/middleware-resource-manager/conf/app.env
ExecStart=/usr/bin/java -jar /opt/middleware-resource-manager/app/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

保存到：

```text
/etc/systemd/system/middleware-resource-manager.service
```

执行：

```bash
systemctl daemon-reload
systemctl enable middleware-resource-manager
systemctl start middleware-resource-manager
systemctl status middleware-resource-manager
```

## 8. 部署后验证

### 8.1 检查端口

```bash
ss -lntp | grep 8080
```

### 8.2 检查数据库表

```sql
USE middleware_resource_manager;
SHOW TABLES;
```

应看到所有业务表（见 2.2 节）。

### 8.3 检查前端页面

浏览器访问：

- `http://<server-ip>/#/home` — 门户首页
- `http://<server-ip>/#/admin` — 登录后进入管理后台

### 8.4 检查上传目录

首次上传文件后，检查：

```bash
ls -R /opt/middleware-resource-manager/storage
```

## 9. 生产环境建议

- 不要使用 `root` 作为应用数据库账号
- 必须修改默认管理员账号密码
- 上传目录使用绝对路径
- 如果有 Nginx，记得同步 `client_max_body_size`
- 若生产环境强调稳定性，建议把 `spring.thymeleaf.cache` 改为 `true`
- 若生产环境强调可控变更，建议把 `spring.jpa.hibernate.ddl-auto` 从 `update` 改成 `validate`，并改为手工维护 SQL

## 10. 当前项目的最小上线参数清单

最少需要确认这些参数：

```text
APP_DB_HOST
APP_DB_PORT
APP_DB_NAME
APP_DB_USERNAME
APP_DB_PASSWORD
APP_STORAGE_LOCATION
SERVER_PORT
```
