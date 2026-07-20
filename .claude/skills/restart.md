---
name: restart
description: 重启前后端应用，依赖服务（Docker、Milvus）仅在未运行时启动
---

# 重启应用

智能重启：依赖服务已运行则跳过，只重启前后端。

## 步骤

### 1. 检查并启动 Docker 依赖（按需）

```bash
# 检查 Colima 是否运行，未运行则启动
colima status 2>&1 | grep -q "running" || colima start

# 检查 Milvus 容器是否已运行，未运行则启动
docker ps --format '{{.Names}}' | grep -q "milvus_standalone" || {
  echo "Milvus 未运行，正在启动..."
  docker start milvus_etcd milvus_standalone 2>/dev/null || true
  sleep 5
}
echo "Milvus 状态:"
docker ps --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}"
```

### 2. 杀掉旧的前后端进程

```bash
# 只杀前后端进程，不影响 Docker/Milvus
ps aux | grep "java.*middleware\|mvn.*spring\|vite" | grep -v grep | awk '{print $2}' | xargs kill -9 2>/dev/null
sleep 2
```

### 3. 启动后端

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
> /tmp/backend.log
(cd backend && nohup mvn spring-boot:run -DskipTests >> /tmp/backend.log 2>&1 &)
```

等待启动完成（约 15-20 秒，Milvus 未就绪时可能更久）：

```bash
# 检查启动状态
grep "Started\|ERROR" /tmp/backend.log
```

### 4. 启动前端

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager/frontend
nohup npx vite --host 0.0.0.0 > /tmp/frontend.log 2>&1 &
```

### 5. 验证

```bash
# 后端（401 = 需要认证，正常）
curl -s -o /dev/null -w "Backend: %{http_code}\n" http://localhost:8080/api/wiki/pages

# 前端
curl -s -o /dev/null -w "Frontend: %{http_code}\n" http://localhost:5173

# Milvus
docker ps --filter "name=milvus" --format "{{.Names}}: {{.Status}}"
```

## 日志位置

- 后端：`/tmp/backend.log`（`tail -f /tmp/backend.log`）
- 前端：`/tmp/frontend.log`（`tail -f /tmp/frontend.log`）

## 常见问题

- **后端启动慢**：Milvus 未就绪会重试 75 次（约 4 分钟），等 Milvus 启动后自动继续
- **端口占用**：`lsof -i :8080` 或 `lsof -i :5173` 检查并 kill
- **编译错误**：`(cd backend && mvn compile)` 检查，常见是新增 mapper 方法未同步 XML
