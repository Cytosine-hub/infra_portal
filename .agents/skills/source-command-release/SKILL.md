---
name: "source-command-release"
description: "执行本项目版本发布：判断发布范围，按需构建 backend/frontend/db/docs，更新 release.md 并打包 tar；用户提到 release、/release、版本发布、打包发布时使用。"
---

# source-command-release

Use this skill when the user asks for `/release`, release, 版本发布, or 打包发布.

## Release Defaults

- Project root: `/Users/zhushihao/Projects/middleware_resource_manager`
- Version format: `v<major>.<minor>.<patch>-YYYYMMDD`
- Release directory: `release/`
- Release directory and tar packages are ignored by git and are not committed.

## Decide Release Scope

Inspect changed files since the last release entry or recent commits:

| Changed path | Module |
|---|---|
| `src/main/java/**`, `src/main/resources/**`, `pom.xml` | backend |
| `frontend/src/**`, `frontend/package.json`, `frontend/vite.config.*` | frontend |
| `src/main/resources/db/**`, schema/migration changes | db |
| `docs/**`, deployment docs | docs |
| `.claude/**`, `.agents/**`, scripts only | usually no runtime release |

If the user did not specify scope, propose the inferred module list and proceed with the safest obvious scope.

## Build Steps

Backend:

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
mvn clean package -DskipTests -q
mkdir -p release/backend
cp target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar release/backend/
cp src/main/resources/application.yml release/backend/application.yml.example
```

Frontend:

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager/frontend
npm install --silent
npm run build
rm -rf /Users/zhushihao/Projects/middleware_resource_manager/release/frontend
cp -r dist /Users/zhushihao/Projects/middleware_resource_manager/release/frontend
```

DB:

- Create `release/db`.
- For incremental releases, include idempotent upgrade scripts such as `CREATE TABLE IF NOT EXISTS`, `INSERT IGNORE`, and documented `ALTER TABLE ... ADD COLUMN` statements.
- Include new scripts from `src/main/resources/db/` when they are part of the release.

Docs:

- Create `release/docs`.
- Include deployment or startup docs that changed.
- Ensure update/deployment instructions mention any DB upgrade script.

## release.md

Create or prepend a release entry:

```markdown
# Release <version>

**日期**: YYYY-MM-DD
**分支**: <branch>
**发布范围**: backend / frontend / db / docs

**自上次发布以来的变更**:
<commit summary>

## 文件清单

| 文件 | 大小 |
|---|---:|
```

## Package

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
tar -czf release/middleware-resource-manager-<version>.tar.gz \
  -C release \
  backend frontend db docs release.md
```

## Safety

- Do not delete `release/release.md` or old tarballs unless the user explicitly asks.
- Destructive cleanup commands like `rm -rf release/backend ...` require normal Codex escalation policy.
- Every release should end with a summary: version, scope, tar path, tar size, and files included.
