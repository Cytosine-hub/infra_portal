---
name: "source-command-release"
description: "执行本项目版本发布：检查变更记录，按需构建 backend/frontend/db/docs，更新 release.md 并打包 tar；用户提到 release、/release、版本发布、打包发布时使用。"
---

# source-command-release

Use this skill when the user asks for `/release`, release, 版本发布, or 打包发布.

## Release Defaults

- Project root: `/Users/zhushihao/Projects/middleware_resource_manager`
- Version format: `v<major>.<minor>.<patch>-YYYYMMDD`
- Release directory: `release/`
- Release directory and tar packages are ignored by git and are not committed.
- **变更记录文件**: `.claude/changes.md`

## 第一步：读取变更记录

读取 `.claude/changes.md` 文件，提取 `<!-- CHANGES_START -->` 和 `<!-- CHANGES_END -->` 之间的所有记录。

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
sed -n '/<!-- CHANGES_START -->/,/<!-- CHANGES_END -->/p' .claude/changes.md | grep -v "^<!--"
```

## 第二步：分析变更范围

结合以下两个来源判断发布范围：

### 来源 A：变更记录文件
从 `.claude/changes.md` 中提取的记录，格式如：
- [DB] 2026-06-10 db/init.sql — 数据库初始化/种子数据变更
- [CONFIG] 2026-06-10 backend/src/main/resources/application.yml — 应用配置变更

### 来源 B：Git diff
通过 git diff 分析文件变更：

| 变更路径 | 发布模块 |
|----------|----------|
| `backend/src/main/java/**`, `backend/src/main/resources/**`, `backend/pom.xml` | backend |
| `frontend/src/**`, `frontend/package.json`, `frontend/vite.config.*` | frontend |
| `backend/src/main/resources/db/**`, schema/migration changes | db |
| `docs/**`, deployment docs | docs |
| `.claude/**`, `.agents/**`, scripts only | usually no runtime release |

### 合并判断规则
- 变更记录中有 `[DB]` → **必须包含 db 模块**
- 变更记录中有 `[CONFIG]` → **必须包含 backend 模块**（配置打包在 JAR 中）
- Git diff 中有对应路径变更 → 包含对应模块

## 第三步：向用户确认发布范围

根据变更分析，向用户确认本次发布哪些模块：
- **frontend** — 前端静态文件
- **backend** — 后端 JAR
- **db** — 数据库增量 DDL 脚本
- **docs** — 文档（增量部署手册）
- **full** — 全量发布

**重要提示**：如果变更记录中有 DB 或 CONFIG 变更，必须在确认信息中明确提醒用户：

```
⚠️ 检测到以下变更记录：
- [DB] db/init.sql — 数据库初始化/种子数据变更
- [CONFIG] application.yml — 应用配置变更

建议发布范围：frontend + backend + db
```

## 第四步：确定版本号

- 如果用户指定了版本号，直接使用
- 如果未指定，从上次 release.md 读取版本号，patch+1
- 首次未指定则默认 `v1.0.0-YYYYMMDD`

## 第五步：清理旧发布目录

根据确认的发布范围，删除需要重建的目录（保留 release.md 和 tar 包）：

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
# 只删除本次发布范围内需要重建的目录，不要删除或重建未发布模块
# frontend 发布：rm -rf release/frontend
# backend 发布：rm -rf release/backend
# db 发布：rm -rf release/db
# docs 发布：rm -rf release/docs
# full 发布：rm -rf release/backend release/frontend release/db release/docs
```

## 第六步：按需构建

### 后端（仅在需要时执行）

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
(cd backend && mvn clean package -DskipTests -q)
mkdir -p release/backend
cp backend/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar release/backend/
cp backend/src/main/resources/application.yml release/backend/application.yml.example
```

### 前端（仅在需要时执行）

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager/frontend
npm install --silent
npm run build
rm -rf /Users/zhushihao/Projects/middleware_resource_manager/release/frontend
cp -r dist /Users/zhushihao/Projects/middleware_resource_manager/release/frontend
```

首次发布时，将 nginx 配置写入 `release/frontend/nginx.conf`。

### DB（仅在需要时执行）

**增量发布**：生成增量升级 SQL：

```bash
mkdir -p /Users/zhushihao/Projects/middleware_resource_manager/release/db
```

增量 SQL 文件命名规则：`upgrade-v<版本号>.sql`，内容应包含：
- `CREATE TABLE IF NOT EXISTS` 新表
- `INSERT IGNORE` 种子数据
- `ALTER TABLE ... ADD COLUMN` 新列（附注如已存在可忽略报错）

**首次发布或全量重建**：同时生成全量脚本：

```bash
mysqldump -u root --no-data --skip-triggers --routines=false --events=false middleware_resource_manager > release/db/full_schema.sql
mysqldump -u root --no-create-info --skip-triggers --complete-insert \
  --ignore-table=middleware_resource_manager.forum_posts \
  --ignore-table=middleware_resource_manager.forum_comments \
  --ignore-table=middleware_resource_manager.forum_post_likes \
  --ignore-table=middleware_resource_manager.forum_post_tags \
  --ignore-table=middleware_resource_manager.chat_sessions \
  --ignore-table=middleware_resource_manager.chat_messages \
  --ignore-table=middleware_resource_manager.knowledge_chunks \
  middleware_resource_manager > release/db/seed_data.sql
```

**重要**：如果变更记录中有 DB 变更，必须在增量 SQL 中体现，或在 release.md 中说明需要手动执行的 SQL。

### 文档（仅在需要时执行）

```bash
mkdir -p /Users/zhushihao/Projects/middleware_resource_manager/release/docs
cp /Users/zhushihao/Projects/middleware_resource_manager/docs/startup-manual.md release/docs/
cp /Users/zhushihao/Projects/middleware_resource_manager/docs/production-deploy.md release/docs/
```

## 第七步：更新 release.md

**首次发布**：创建新的 release.md

**增量发布**：读取上次 release.md，在顶部追加新版本记录

格式：

```markdown
# Release <version>

**日期**: YYYY-MM-DD
**分支**: <branch>
**发布范围**: frontend / backend / db / docs（列出本次更新的模块）

**自上次发布以来的变更**:
<git log 自上次发布的 commits>

## 变更记录检查

以下变更已包含在本次发布中：
- [x] [DB] db/init.sql — 数据库初始化/种子数据变更
- [x] [CONFIG] application.yml — 应用配置变更

## 文件清单

| 文件 | 大小 |
|------|------|
| （仅列出本次更新的文件） |

---
```

## 第八步：打包

**只打包本次发布范围内的模块**，并始终包含 `release.md`。禁止在 frontend-only、backend-only 等增量发布包中混入未更新的旧目录。

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
# frontend-only 示例
tar -czf release/middleware-resource-manager-<version>.tar.gz -C release frontend release.md

# backend + frontend 示例
tar -czf release/middleware-resource-manager-<version>.tar.gz -C release backend frontend release.md

# full 示例
tar -czf release/middleware-resource-manager-<version>.tar.gz -C release backend frontend db docs release.md
```

打包后必须检查 tar 内容，确认没有包含发布范围外的模块：

```bash
tar -tzf release/middleware-resource-manager-<version>.tar.gz | sed -n '1,80p'
```

如果发布范围是 `frontend`，tar 内容只能包含：
- `frontend/`
- `release.md`
- 可选的变更记录备份文件（仅当明确需要随包交付时）

## 第九步：备份并重置变更记录

**发布完成后必须执行此步骤**：

1. 备份当前变更记录到 release 目录：
```bash
cp .claude/changes.md release/changes-v<版本号>.md.bak
```

2. 重置变更记录文件：
```bash
cat > .claude/changes.md << 'EOF'
# 变更记录

> 此文件由 hook 自动维护，release 时用于检查遗漏。
> 格式：每条记录包含日期、类型、文件、说明。

<!-- CHANGES_START -->

<!-- CHANGES_END -->
EOF
```

## 第十步：输出结果

输出发布摘要：
- 版本号
- 发布范围（哪些模块）
- tar 包路径和大小
- 变更记录检查结果（已包含/遗漏）
- 更新的文件清单

## 注意事项

- 每次发布后必须 git commit
- release 目录在 .gitignore 中，不入版本控制
- tar 包也在 .gitignore 中
- 增量发布时只更新有变更的部分
- nginx.conf 首次发布时生成，后续不变则不更新
- 打包时 tar 包只包含本次发布范围内的目录和 `release.md`；不要包含未更新的旧目录
- DB 增量脚本使用 `CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE` 保证重复执行安全
- **变更记录是发布安全网**：如果变更记录中有遗漏，必须在发布前补充或告知用户
