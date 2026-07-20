---
name: "source-command-restart"
description: "按需重启本项目后端、前端和必要依赖服务；用户提到 restart、/restart、重启服务、重启前后端、看不到改动需要重启时使用。"
---

# source-command-restart

Use this skill when the user asks for `/restart`, restart, 重启服务, or repository instructions require service restart after code changes.

Default behavior is change-aware:

- If no application code/config/assets changed since the last successful verification, do not restart backend or frontend. Only check that existing services are healthy and report their status.
- Restart only the service affected by the change when the scope is clear.
- Restart both backend and frontend only when the change crosses the API/UI boundary, changes shared configuration, or when the affected scope is ambiguous.

## Project Paths

- Root: `/Users/zhushihao/Projects/middleware_resource_manager`
- Backend port: `8080`
- Frontend port: `5173`
- Backend log: `/tmp/backend.log`
- Frontend log: `/tmp/frontend.log`

## Workflow

1. Determine whether a restart is needed.
   - Inspect changed files with `git status --short` and, when needed, the latest relevant commit with `git diff --name-only HEAD~1 HEAD`.
   - Ignore unrelated local noise such as `.DS_Store`, screenshots, release notes, or documentation-only changes unless they directly affect runtime behavior.
   - Backend restart is needed for changes under `backend/src/main/`, `backend/pom.xml`, backend config, database migrations used at runtime, or backend resources.
   - Frontend restart is needed for changes under `frontend/src/`, `frontend/public/`, `frontend/package*.json`, `frontend/vite.config.*`, or frontend env/config files.
   - If neither backend nor frontend restart is needed, skip stop/start steps and perform only the verification checks in step 6.
2. Check dependency services.
   - If Colima is not running, start it.
   - If Milvus containers are not running, start `milvus_etcd` and `milvus_standalone`.
3. Stop only affected project dev processes.
   - Prefer port/process-targeted commands for backend Spring Boot and Vite.
   - Do not stop unrelated Docker containers.
4. Compile backend before starting when backend restart is needed.
   - Run `mvn compile -q` from the `backend/` directory.
   - If compile fails, report the compiler errors and stop.
5. Start affected services.
   - Start backend only when backend restart is needed.
   - Truncate `/tmp/backend.log`.
   - Run `(cd backend && nohup mvn spring-boot:run -DskipTests >> /tmp/backend.log 2>&1 &)`.
   - Wait and check `Started`, `APPLICATION FAILED`, `BUILD FAILURE`, and `ERROR`.
   - Start frontend only when frontend restart is needed.
   - Run from `frontend`: `nohup npx vite --host 0.0.0.0 > /tmp/frontend.log 2>&1 &`.
   - Wait and check for `ready` or `Local`.
6. Verify.
   - Backend: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/wiki/pages`
   - Frontend: `curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/`
   - Milvus: `docker ps --filter "name=milvus" --format "{{.Names}}: {{.Status}}"`

## Notes

- Backend may take longer when Milvus is cold.
- In this Codex environment, GUI/browser opening is not part of restart; use the Browser skill separately for UI verification.
- Commands that start Docker, Colima, kill processes, or run long-lived servers may require escalation.
