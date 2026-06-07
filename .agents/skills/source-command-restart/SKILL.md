---
name: "source-command-restart"
description: "重启本项目后端、前端和必要依赖服务；用户提到 restart、/restart、重启服务、重启前后端、看不到改动需要重启时使用。"
---

# source-command-restart

Use this skill when the user asks for `/restart`, restart, 重启服务, or repository instructions require service restart after code changes.

## Project Paths

- Root: `/Users/zhushihao/Projects/middleware_resource_manager`
- Backend port: `8080`
- Frontend port: `5173`
- Backend log: `/tmp/backend.log`
- Frontend log: `/tmp/frontend.log`

## Workflow

1. Check dependency services.
   - If Colima is not running, start it.
   - If Milvus containers are not running, start `milvus_etcd` and `milvus_standalone`.
2. Stop only project dev processes.
   - Prefer port/process-targeted commands for backend Spring Boot and Vite.
   - Do not stop unrelated Docker containers.
3. Compile backend before starting.
   - Run `mvn compile -q` from the project root.
   - If compile fails, report the compiler errors and stop.
4. Start backend.
   - Truncate `/tmp/backend.log`.
   - Run `nohup mvn spring-boot:run -DskipTests >> /tmp/backend.log 2>&1 &`.
   - Wait and check `Started`, `APPLICATION FAILED`, `BUILD FAILURE`, and `ERROR`.
5. Start frontend.
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
