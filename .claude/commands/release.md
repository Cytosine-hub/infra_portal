---
allowed-tools: Bash(mvn *), Bash(npm *), Bash(mysqldump *), Bash(tar *), Bash(cp *), Bash(mkdir *), Bash(rm *), Bash(ls *), Bash(du *), Bash(git *), Bash(cat *), Bash(date *), Read, Write, Edit
description: 版本发布 - 构建前后端、导出DB、生成nginx配置、打包tar
---

# 版本发布流程

执行版本发布，构建完整的 release 部署包。

## 当前状态

- 项目根目录: /Users/zhushihao/Projects/middleware_resource_manager
- 当前日期: !`date +%Y-%m-%d`
- 当前分支: !`git branch --show-current`
- 最近 commits: !`git log --oneline -5`
- release.md 是否存在: !`ls release/release.md 2>/dev/null && echo "存在" || echo "不存在"`

## 版本号格式

`v<major>.<minor>.<patch>-YYYYMMDD`（例如 v1.0.0-20260528）

用户可以指定版本号，也可以自动推导（上次 patch+1）。

## 执行步骤

### 第一步：判断首次还是增量发布

检查 `release/release.md` 是否存在：
- **不存在** → 首次发布，执行完整构建
- **存在** → 增量发布，读取上次版本号和文件清单

### 第二步：确定版本号

- 如果用户指定了版本号，直接使用
- 如果未指定，从上次 release.md 读取版本号，patch+1
- 首次未指定则默认 `v1.0.0-YYYYMMDD`

### 第三步：构建后端

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
mvn clean package -DskipTests -q
mkdir -p release/backend
cp target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar release/backend/
cp src/main/resources/application.yml release/backend/application.yml.example
```

### 第四步：构建前端

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager/frontend
npm install --silent
npm run build
rm -rf /Users/zhushihao/Projects/middleware_resource_manager/release/frontend
cp -r dist /Users/zhushihao/Projects/middleware_resource_manager/release/frontend
```

### 第五步：生成 nginx 配置

将以下完整 nginx 配置写入 `release/frontend/nginx.conf`：

```nginx
upstream backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name _;
    client_max_body_size 2048m;

    # gzip 压缩
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml;
    gzip_min_length 1024;

    # 前端静态文件
    location / {
        root /opt/middleware-resource-manager/frontend;
        try_files $uri $uri/ /index.html;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 静态资源缓存（带 hash 的文件长期缓存）
    location /assets/ {
        root /opt/middleware-resource-manager/frontend;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_http_version 1.1;
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
        proxy_connect_timeout 10s;
        proxy_buffering off;
        proxy_request_buffering off;
    }

    # 文件下载代理（支持断点续传）
    location /files/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Range $http_range;
        proxy_set_header If-Range $http_if_range;
        proxy_set_header Connection "";
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_read_timeout 600s;
    }
}
```

### 第六步：导出数据库

```bash
mkdir -p /Users/zhushihao/Projects/middleware_resource_manager/release/db

# 导出完整 DDL（不含数据）
mysqldump -u root --no-data --skip-triggers --routines=false --events=false middleware_resource_manager > /Users/zhushihao/Projects/middleware_resource_manager/release/db/full_schema.sql

# 导出种子数据（排除用户生成数据的表）
mysqldump -u root --no-create-info --skip-triggers --complete-insert \
  --ignore-table=middleware_resource_manager.forum_posts \
  --ignore-table=middleware_resource_manager.forum_comments \
  --ignore-table=middleware_resource_manager.forum_post_likes \
  --ignore-table=middleware_resource_manager.forum_post_tags \
  --ignore-table=middleware_resource_manager.chat_sessions \
  --ignore-table=middleware_resource_manager.chat_messages \
  --ignore-table=middleware_resource_manager.knowledge_chunks \
  middleware_resource_manager > /Users/zhushihao/Projects/middleware_resource_manager/release/db/seed_data.sql
```

### 第七步：复制文档

```bash
mkdir -p /Users/zhushihao/Projects/middleware_resource_manager/release/docs
cp /Users/zhushihao/Projects/middleware_resource_manager/docs/startup-manual.md /Users/zhushihao/Projects/middleware_resource_manager/release/docs/
cp /Users/zhushihao/Projects/middleware_resource_manager/docs/production-deploy.md /Users/zhushihao/Projects/middleware_resource_manager/release/docs/
```

### 第八步：生成/更新 release.md

**首次发布**：创建新的 release.md

**增量发布**：读取上次 release.md，在顶部追加新版本记录

格式：

```markdown
# Release <version>

**日期**: YYYY-MM-DD
**自上次发布以来的变更**:
<git log 自上次发布的 commits>

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | XXX MB |
| frontend/index.html | XX KB |
| frontend/assets/index-XXXXX.js | X.X MB |
| frontend/assets/index-XXXXX.css | XX KB |
| frontend/nginx.conf | X KB |
| db/full_schema.sql | XX KB |
| db/seed_data.sql | XX KB |
| docs/startup-manual.md | X KB |
| docs/production-deploy.md | X KB |

---
```

增量发布时，在已有内容顶部插入新版本记录，用 `---` 分隔各版本。

### 第九步：打包

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
tar -czf release/middleware-resource-manager-<version>.tar.gz \
  -C release \
  backend frontend db docs release.md
```

### 第十步：输出结果

输出发布摘要：
- 版本号
- tar 包路径和大小
- 文件清单概览

## 注意事项

- 每次发布后必须 git commit
- release 目录在 .gitignore 中，不入版本控制
- tar 包也在 .gitignore 中
- 增量发布时只更新有变更的部分，但 DB 文件每次都重新导出
- nginx.conf 前端放在 `release/frontend/` 下，部署时复制到 nginx 配置目录
