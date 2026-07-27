# Docker Compose Complete Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide a tested, one-command Docker Compose deployment for the frontend, nine Java services, MySQL, Nacos, Milvus, etcd and MinIO, including first-run database and Nacos configuration initialization.

**Architecture:** Keep `deploy/docker-compose.yml` as the single topology for local builds and CI image deployment. Split Compose interpolation settings, runtime secrets and version-controlled Nacos business configuration, then gate all Java services on an idempotent `nacos-init` one-shot container.

**Tech Stack:** Docker Compose v2, Docker BuildKit, Eclipse Temurin 17, Maven 3.9, Node.js 20, Nginx, MySQL 8, Nacos 2.3, Milvus 2.3, POSIX shell, curl, jq.

---

## File Map

- Create `deploy/tests/compose-contract.sh`: static and rendered-Compose acceptance tests with `TC-DOCKER-*` identifiers.
- Create `deploy/tests/nacos-init-test.sh`: black-box tests for missing/existing Data ID behavior using a fake HTTP endpoint.
- Create `deploy/compose.env.example`: image, port, data directory and infrastructure component settings used for Compose interpolation.
- Modify `deploy/services.env.example`: runtime business secrets only.
- Create `deploy/nacos-config/*.properties`: nine non-sensitive Nacos Data ID templates.
- Create `deploy/Dockerfile.nacos-init`: curl/jq runtime for deterministic Nacos API initialization.
- Create `deploy/nacos-init.sh`: authenticated namespace/Data ID initialization with create-if-absent semantics.
- Create `deploy/Dockerfile.frontend`: Node build plus Nginx runtime.
- Create `deploy/nginx.conf`: SPA serving and Gateway proxy configuration.
- Modify `deploy/Dockerfile`: add BuildKit Maven cache and an HTTP health-check client.
- Modify `deploy/docker-compose.yml`: full build/image topology, frontend, Nacos initializer, persistence and health dependencies.
- Create `deploy/smoke-test.sh`: full-stack Nacos, service registration, frontend and API checks.
- Modify `.dockerignore` and `.gitignore`: exclude runtime secrets, data and frontend build output.
- Modify `.gitlab-ci.yml`: build/deploy frontend and nacos-init, and consume split environment files.
- Modify `README.md`, `docs/startup-manual.md`, `docs/microservices-stage6-job-services.md`: user and CI operating instructions.

### Task 1: Add The Failing Deployment Contract

**Files:**
- Create: `deploy/tests/compose-contract.sh`

- [ ] **Step 1: Write the failing contract test**

Create an executable shell test with these assertions:

```bash
#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.yml"

pass() { printf '| Subtask success: %s\n' "$1"; }
fail() { printf '%s\n' "- Subtask failure: $1" >&2; exit 1; }
assert_file() { [ -f "$ROOT_DIR/$1" ] || fail "$2 missing file $1"; pass "$2"; }
assert_text() { grep -Eq "$2" "$ROOT_DIR/$1" || fail "$3 missing pattern $2"; pass "$3"; }

printf '%s\n' '+ Task start: Docker Compose contract'
assert_file deploy/compose.env.example 'TC-DOCKER-001'
assert_file deploy/Dockerfile.frontend 'TC-DOCKER-002'
assert_file deploy/nginx.conf 'TC-DOCKER-003'
assert_file deploy/nacos-init.sh 'TC-DOCKER-004'
assert_text deploy/docker-compose.yml '^  frontend:' 'TC-DOCKER-005'
assert_text deploy/docker-compose.yml '^  nacos-init:' 'TC-DOCKER-006'
assert_text deploy/docker-compose.yml 'condition: service_completed_successfully' 'TC-DOCKER-007'
assert_text deploy/docker-compose.yml '../db/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro' 'TC-DOCKER-008'
assert_text deploy/docker-compose.yml '../db/seed.sql:/docker-entrypoint-initdb.d/02-seed.sql:ro' 'TC-DOCKER-009'

for service in api-gateway core-service ai-service community-service middleware-service database-service host-service network-service security-service; do
  assert_file "deploy/nacos-config/$service.properties" "TC-DOCKER-010 $service"
done

assert_text deploy/nginx.conf 'proxy_pass http://api-gateway:8080' 'TC-DOCKER-011'
assert_text .gitignore '^/deploy/compose.env$' 'TC-DOCKER-012'
printf '%s\n' '* Task complete: Docker Compose contract'
```

- [ ] **Step 2: Run the test and verify RED**

Run: `sh deploy/tests/compose-contract.sh`

Expected: exit 1 at `TC-DOCKER-001` because `deploy/compose.env.example` does not exist.

- [ ] **Step 3: Commit the Red test**

```bash
git add deploy/tests/compose-contract.sh
git commit -m "test(部署): 增加 Compose 部署契约用例 (#3)"
```

### Task 2: Split Configuration And Add Nacos Data IDs

**Files:**
- Create: `deploy/compose.env.example`
- Modify: `deploy/services.env.example`
- Create: `deploy/nacos-config/api-gateway.properties`
- Create: `deploy/nacos-config/core-service.properties`
- Create: `deploy/nacos-config/ai-service.properties`
- Create: `deploy/nacos-config/community-service.properties`
- Create: `deploy/nacos-config/middleware-service.properties`
- Create: `deploy/nacos-config/database-service.properties`
- Create: `deploy/nacos-config/host-service.properties`
- Create: `deploy/nacos-config/network-service.properties`
- Create: `deploy/nacos-config/security-service.properties`
- Modify: `.gitignore`

- [ ] **Step 1: Add the Compose interpolation example**

Create `deploy/compose.env.example` with concrete local defaults and empty required credentials:

```dotenv
COMPOSE_PROJECT_NAME=infra-portal
IMAGE_NAMESPACE=infra-portal
IMAGE_TAG=local
BUSINESS_ENV_FILE=./services.env
DEPLOY_DATA_DIR=./data

FRONTEND_PORT=5173
API_GATEWAY_PORT=8080
COMMUNITY_SERVICE_PORT=8082
AI_SERVICE_PORT=8083
CORE_SERVICE_PORT=8084
MIDDLEWARE_SERVICE_PORT=8085
DATABASE_SERVICE_PORT=8086
HOST_SERVICE_PORT=8087
NETWORK_SERVICE_PORT=8088
SECURITY_SERVICE_PORT=8089
MYSQL_PORT=3306
NACOS_PORT=8848
MILVUS_PORT=19530
MILVUS_WEBUI_PORT=9091
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001

MYSQL_VERSION=8.0
NACOS_VERSION=v2.3.2
MILVUS_VERSION=v2.3.4
ETCD_VERSION=v3.5.5
MINIO_VERSION=RELEASE.2023-03-20T20-16-18Z
APP_DB_NAME=middleware_resource_manager
APP_DB_USERNAME=root
APP_DB_PASSWORD=
NACOS_NAMESPACE=
NACOS_CONFIG_GROUP=DEFAULT_GROUP
NACOS_DISCOVERY_GROUP=DEFAULT_GROUP
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos
NACOS_AUTH_ENABLE=true
NACOS_AUTH_TOKEN=
NACOS_AUTH_IDENTITY_KEY=
NACOS_AUTH_IDENTITY_VALUE=
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=
```

- [ ] **Step 2: Restrict the business environment example to secrets**

Replace `deploy/services.env.example` with:

```dotenv
# Copy to deploy/services.env. This file is injected into Java services only.
GATEWAY_SIGNING_SECRET=
ADMIN_DEFAULT_PASSWORD=
AI_API_KEY=
EMBEDDING_API_KEY=ollama
WIKI_EXPORT_SIGNATURE_SECRET=
ZABBIX_PASSWORD=
```

- [ ] **Step 3: Add nine Nacos property templates**

Use the exact imported Data ID names from `application-cloud.yml`. The templates must contain these concrete properties:

```properties
# api-gateway.properties
logging.level.root=INFO
logging.level.com.middleware.gateway=INFO
app.rate-limit.enabled=true
app.rate-limit.window-seconds=60
app.rate-limit.max-client-keys=10000
app.rate-limit.download-per-window=6
app.rate-limit.document-per-window=60
app.rate-limit.document-file-per-window=18
app.rate-limit.forum-post-per-window=120
app.security.gateway-signing-secret=${GATEWAY_SIGNING_SECRET}
app.security.introspection-base-url=http://core-service
app.security.introspection-load-balanced=true
app.security.introspection-cache-ttl=PT15S
app.security.introspection-cache-max-size=10000
```

```properties
# core-service.properties
logging.level.root=INFO
logging.level.com.middleware.manager=INFO
app.storage.location=/app/storage
app.security.gateway-signing-secret=${GATEWAY_SIGNING_SECRET}
app.security.admin.default-password=${ADMIN_DEFAULT_PASSWORD:}
```

```properties
# community-service.properties
logging.level.root=INFO
logging.level.com.middleware=INFO
app.storage.location=/app/storage
app.security.gateway-signing-secret=${GATEWAY_SIGNING_SECRET}
```

```properties
# ai-service.properties
logging.level.root=INFO
logging.level.com.middleware.manager=INFO
app.storage.location=/app/storage
app.security.gateway-signing-secret=${GATEWAY_SIGNING_SECRET}
app.modules.knowledge-enabled=true
app.modules.diagnostics-enabled=true
app.llm.max-concurrent=5
app.llm.timeout-seconds=600
app.ai.base-url=https://token-plan-cn.xiaomimimo.com/v1
app.ai.api-key=${AI_API_KEY:}
app.ai.model=mimo-v2.5-pro
app.ai.max-tokens=8192
app.ai.temperature=0.1
app.vector.type=milvus
app.vector.host=milvus
app.vector.port=19530
app.vector.collection=knowledge_chunks
app.wiki.search.max-context-pages=8
app.wiki.search.max-content-chars=2000
app.wiki.search.graph-hop-limit=1
app.wiki.search.fulltext-min-results=3
app.wiki.search.min-results-for-wiki=2
app.wiki.ingest.max-content-chars=20000
app.wiki.ingest.chunk-overlap=500
app.wiki.ingest.parallel-chunks=3
app.wiki.export.signature-secret=${WIKI_EXPORT_SIGNATURE_SECRET:}
app.zabbix.url=http://host.containers.internal:8080/api_jsonrpc.php
app.zabbix.username=Admin
app.zabbix.password=${ZABBIX_PASSWORD:}
app.zabbix.timeout=30
langchain4j.open-ai.chat-model.base-url=https://token-plan-cn.xiaomimimo.com/v1
langchain4j.open-ai.chat-model.api-key=${AI_API_KEY:}
langchain4j.open-ai.chat-model.model-name=mimo-v2.5-pro
langchain4j.open-ai.chat-model.max-tokens=8192
langchain4j.open-ai.chat-model.temperature=0.1
langchain4j.open-ai.chat-model.timeout=PT600S
langchain4j.open-ai.chat-model.log-requests=true
langchain4j.open-ai.chat-model.log-responses=true
langchain4j.open-ai.embedding-model.base-url=http://host.containers.internal:11434/v1
langchain4j.open-ai.embedding-model.api-key=${EMBEDDING_API_KEY}
langchain4j.open-ai.embedding-model.model-name=bge-large
skills.external-dir=/app/data/skills
```

`middleware-service.properties` contains the common logging/storage/security keys plus `app.catalog.base-url=http://core-service` and `app.catalog.load-balanced=true`. Each of `database-service.properties`, `host-service.properties`, `network-service.properties`, and `security-service.properties` contains its service logging namespace, `app.storage.location=/app/storage`, and `app.security.gateway-signing-secret=${GATEWAY_SIGNING_SECRET}`.

- [ ] **Step 4: Ignore runtime configuration and data**

Append these exact patterns to `.gitignore`:

```gitignore
/deploy/compose.env
/deploy/data/
```

- [ ] **Step 5: Run the contract and observe the next expected failure**

Run: `sh deploy/tests/compose-contract.sh`

Expected: configuration and Data ID assertions pass; test stops at missing `deploy/Dockerfile.frontend`.

- [ ] **Step 6: Commit the configuration layer**

```bash
git add .gitignore deploy/compose.env.example deploy/services.env.example deploy/nacos-config
git commit -m "feat(部署): 拆分 Compose 与 Nacos 业务配置 (#3)"
```

### Task 3: Implement Idempotent Nacos Initialization

**Files:**
- Create: `deploy/tests/nacos-init-test.sh`
- Create: `deploy/Dockerfile.nacos-init`
- Create: `deploy/nacos-init.sh`

- [ ] **Step 1: Write black-box Nacos initializer tests**

The test must place fake `curl` and `jq` executables at the front of `PATH`, run `nacos-init.sh`, and assert:

```text
TC-DOCKER-013 missing namespace is created
TC-DOCKER-014 missing Data ID is published
TC-DOCKER-015 existing Data ID logs SKIP and is not published
TC-DOCKER-016 login or publish failure returns non-zero
TC-DOCKER-017 all nine property files are processed
```

The fake curl records method, URL and form fields in a temporary call log. Use `mktemp -d`, clean it with `trap`, and never touch a real Nacos instance.

- [ ] **Step 2: Run the initializer tests and verify RED**

Run: `sh deploy/tests/nacos-init-test.sh`

Expected: exit 1 because `deploy/nacos-init.sh` does not exist.

- [ ] **Step 3: Add the initializer image**

Create `deploy/Dockerfile.nacos-init`:

```dockerfile
FROM alpine:3.20
RUN apk add --no-cache curl jq
COPY deploy/nacos-init.sh /usr/local/bin/nacos-init
RUN chmod 0555 /usr/local/bin/nacos-init
ENTRYPOINT ["/usr/local/bin/nacos-init"]
```

- [ ] **Step 4: Implement `nacos-init.sh`**

The script must use `set -eu`, validate `NACOS_URL`, `NACOS_USERNAME`, `NACOS_PASSWORD`, `NACOS_CONFIG_GROUP` and `NACOS_CONFIG_DIR`, authenticate through `/nacos/v1/auth/users/login`, and parse `accessToken` with jq. For a non-empty namespace it queries `/nacos/v1/console/namespaces` and creates the namespace only when absent. For each sorted `*.properties` file it GETs `/nacos/v1/cs/configs`; HTTP 200 logs `SKIP`, HTTP 404 POSTs the file with `type=properties`, and every other status exits non-zero.

The script must emit only the agreed log grammar and never print passwords, tokens, signing secrets or config file contents.

- [ ] **Step 5: Run initializer tests and verify GREEN**

Run: `sh deploy/tests/nacos-init-test.sh`

Expected: all five `TC-DOCKER-013` through `TC-DOCKER-017` pass.

- [ ] **Step 6: Commit Nacos initialization**

```bash
git add deploy/tests/nacos-init-test.sh deploy/Dockerfile.nacos-init deploy/nacos-init.sh
git commit -m "feat(部署): 增加 Nacos 配置幂等初始化 (#3)"
```

### Task 4: Add Frontend Image And Complete Compose Topology

**Files:**
- Create: `deploy/Dockerfile.frontend`
- Create: `deploy/nginx.conf`
- Modify: `deploy/Dockerfile`
- Modify: `deploy/docker-compose.yml`
- Modify: `.dockerignore`

- [ ] **Step 1: Add the frontend multi-stage image**

Create `deploy/Dockerfile.frontend`:

```dockerfile
# syntax=docker/dockerfile:1
FROM node:20.20-alpine AS build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html
EXPOSE 80
```

- [ ] **Step 2: Add the Nginx configuration**

Create a server listening on port 80 with `client_max_body_size 2048m`, `root /usr/share/nginx/html`, `location / { try_files $uri $uri/ /index.html; }`, and separate `/api/` and `/files/` locations using `proxy_pass http://api-gateway:8080` without a trailing URI. Forward host, client IP and protocol headers; set proxy read/send timeouts to 600 seconds.

- [ ] **Step 3: Harden and cache the backend image**

Modify `deploy/Dockerfile` so Maven uses `--mount=type=cache,target=/root/.m2`, the JRE layer installs curl, creates an `app` system user, owns `/app`, and runs as that non-root user.

- [ ] **Step 4: Expand the Compose topology**

For all nine Java services add `build.context: ..`, `build.dockerfile: deploy/Dockerfile`, and the exact module name as `build.args.SERVICE`. Keep the existing `image` expression for CI.

Add `nacos-init` with its own build/image, read-only `./nacos-config:/config`, Nacos bootstrap environment, and `depends_on.nacos.condition: service_healthy`. Add `frontend` with its build/image, `${FRONTEND_PORT:-5173}:80`, Nginx health check and Gateway dependency.

Change every Java service to:

```yaml
env_file:
  - ${BUSINESS_ENV_FILE:-./services.env}
environment:
  SPRING_PROFILES_ACTIVE: cloud
  APP_DB_HOST: mysql
  APP_DB_PORT: 3306
  APP_DB_NAME: ${APP_DB_NAME:-middleware_resource_manager}
  APP_DB_USERNAME: ${APP_DB_USERNAME:-root}
  APP_DB_PASSWORD: ${APP_DB_PASSWORD:?APP_DB_PASSWORD is required}
  NACOS_SERVER_ADDR: nacos:8848
  NACOS_NAMESPACE: ${NACOS_NAMESPACE:-}
  NACOS_USERNAME: ${NACOS_USERNAME:-nacos}
  NACOS_PASSWORD: ${NACOS_PASSWORD:?NACOS_PASSWORD is required}
  NACOS_CONFIG_GROUP: ${NACOS_CONFIG_GROUP:-DEFAULT_GROUP}
  NACOS_DISCOVERY_GROUP: ${NACOS_DISCOVERY_GROUP:-DEFAULT_GROUP}
depends_on:
  mysql: { condition: service_healthy }
  nacos-init: { condition: service_completed_successfully }
```

Gateway omits database settings. AI additionally uses `VECTOR_HOST=milvus`, `VECTOR_PORT=19530`; core mounts `${DEPLOY_DATA_DIR:-./data}/storage:/app/storage`; AI mounts its storage and skill directories. Preserve the existing MySQL `01-init.sql` and `02-seed.sql` order.

Add `extra_hosts: ["host.containers.internal:host-gateway"]` to Java services so Nacos-managed integrations can reach host-side Ollama or Zabbix consistently on Linux Docker and OrbStack.

- [ ] **Step 5: Render and validate Compose**

Prepare ignored runtime files from the examples, fill test-only values with at least 32-byte secrets, then run:

```bash
docker compose --env-file deploy/compose.env -f deploy/docker-compose.yml config --quiet
sh deploy/tests/compose-contract.sh
```

Expected: Compose exits 0 and `TC-DOCKER-001` through `TC-DOCKER-012` pass.

- [ ] **Step 6: Commit the complete topology**

```bash
git add .dockerignore deploy/Dockerfile deploy/Dockerfile.frontend deploy/nginx.conf deploy/docker-compose.yml
git commit -m "feat(部署): 补齐前端与完整 Compose 拓扑 (#3)"
```

### Task 5: Add Full-Stack Smoke Testing

**Files:**
- Create: `deploy/smoke-test.sh`

- [ ] **Step 1: Write smoke checks before starting the stack**

The executable script must accept `COMPOSE_ENV_FILE` and `COMPOSE_FILE`, run `docker compose ps --format json`, and enforce:

```text
TC-DOCKER-018 all long-running containers are running and healthy
TC-DOCKER-019 nacos-init exited successfully
TC-DOCKER-020 all nine Data IDs are readable through Nacos API
TC-DOCKER-021 all nine Java service names are registered in Nacos
TC-DOCKER-022 frontend root returns HTTP 200
TC-DOCKER-023 frontend /api/public/releases proxy returns HTTP 200
TC-DOCKER-024 repeated nacos-init logs SKIP for all nine Data IDs
```

Use bounded polling with `SMOKE_TIMEOUT_SECONDS` defaulting to 600. On failure print `docker compose ps` and the last 100 log lines for the failing service, then exit non-zero.

- [ ] **Step 2: Run the smoke script and verify RED**

Run: `sh deploy/smoke-test.sh`

Expected: non-zero because the full stack is not running.

- [ ] **Step 3: Start the complete stack in OrbStack Fedora**

Run:

```bash
zsh -ic 'of docker compose --env-file deploy/compose.env --file deploy/docker-compose.yml up --detach --build --wait'
```

Expected: all long-running containers reach healthy state and `nacos-init` exits 0.

- [ ] **Step 4: Run smoke tests and verify GREEN**

Run:

```bash
zsh -ic 'cd /Users/sklun/Work/Code/TL/Git/infra_portal && sh deploy/smoke-test.sh'
```

Expected: `TC-DOCKER-018` through `TC-DOCKER-024` pass.

- [ ] **Step 5: Commit smoke testing**

```bash
git add deploy/smoke-test.sh
git commit -m "test(部署): 增加 Compose 全栈冒烟验证 (#3)"
```

### Task 6: Update CI And Operating Documentation

**Files:**
- Modify: `.gitlab-ci.yml`
- Modify: `README.md`
- Modify: `docs/startup-manual.md`
- Modify: `docs/microservices-stage6-job-services.md`

- [ ] **Step 1: Extend CI to the complete stack**

Add `frontend` and `nacos-init` to full-stack image builds. Deployment jobs must materialize two protected file variables:

```bash
cp "$DEPLOY_COMPOSE_ENV_FILE" deploy/compose.env
cp "$DEPLOY_SERVICES_ENV_FILE" deploy/services.env
trap 'rm -f deploy/compose.env deploy/services.env' EXIT
docker compose --env-file deploy/compose.env --file deploy/docker-compose.yml up --detach --no-build --pull never
```

Document the migration from the legacy `DEPLOY_ENV_FILE`; do not silently treat the mixed legacy file as both new files because their variable ownership differs.

- [ ] **Step 2: Add the user-facing Compose procedure**

Document these exact first-run commands in `README.md` and `docs/startup-manual.md`:

```bash
cp deploy/compose.env.example deploy/compose.env
cp deploy/services.env.example deploy/services.env
# Fill every empty required credential before continuing.
docker compose --env-file deploy/compose.env --file deploy/docker-compose.yml config --quiet
docker compose --env-file deploy/compose.env --file deploy/docker-compose.yml up --detach --build --wait
sh deploy/smoke-test.sh
```

Document access at `http://localhost:5173`, status/log/down commands, MySQL's one-time init behavior, Nacos create-if-absent behavior, persistent directories, and the destructive nature of deleting the configured data directory.

- [ ] **Step 3: Update stage 6 CI and topology documentation**

Describe the frontend and `nacos-init` containers, the nine Data IDs, the split protected variables, and the fact that Java services wait for Nacos initialization.

- [ ] **Step 4: Run documentation and contract checks**

Run:

```bash
git diff --check
sh deploy/tests/compose-contract.sh
sh deploy/tests/nacos-init-test.sh
```

Expected: exit 0 with no whitespace errors and all `TC-DOCKER-001` through `TC-DOCKER-017` passing.

- [ ] **Step 5: Commit CI and documentation**

```bash
git add .gitlab-ci.yml README.md docs/startup-manual.md docs/microservices-stage6-job-services.md
git commit -m "docs(部署): 完善 Compose 启动与 CI 说明 (#3)"
```

### Task 7: Run Regression, Review And Final Verification

**Files:**
- Modify only files required by findings from the prescribed project code-review skill.

- [ ] **Step 1: Run project regression tests**

Run:

```bash
cd backend && mvn test
cd ../frontend && npm test
npm run build
```

Expected: Maven reactor succeeds, Vitest reports zero failures, and Vite build exits 0.

- [ ] **Step 2: Rebuild and smoke-test from a clean test data directory**

Set `DEPLOY_DATA_DIR` in the ignored test `compose.env` to a dedicated task directory, then run the full Compose build, `--wait`, `deploy/smoke-test.sh`, and `docker compose ps`. Do not reuse or delete user data.

- [ ] **Step 3: Verify persistence and idempotence**

Restart the stack without deleting the test data directory. Confirm MySQL retains its row counts and every existing Nacos Data ID is logged as `SKIP` by a repeated initializer run.

- [ ] **Step 4: Invoke the project code-review skill**

Use `source-command-code-review` against all task changes. Fix every P0/P1 finding and any P2 finding that violates `agent.md`; rerun the focused tests after each correction.

- [ ] **Step 5: Run fresh final verification**

Run `git diff --check`, both deployment test scripts, full backend tests, frontend tests/build, Compose config, full-stack smoke test and `git status --short`. Record exact pass counts and any external AI/Zabbix checks excluded by the design.

- [ ] **Step 6: Commit review fixes**

```bash
git add .dockerignore .gitignore .gitlab-ci.yml README.md \
  deploy docs/startup-manual.md docs/microservices-stage6-job-services.md
git commit -m "fix(部署): 修正 Compose 验收问题 (#3)"
```
