---
allowed-tools: Bash(mvn *), Bash(npm *), Bash(pkill *), Bash(lsof *), Bash(kill *), Bash(sleep *), Bash(curl *), Bash(grep *), Bash(awk *), Bash(cd *), Bash(docker *), Bash(colima *), Bash(ps *), Bash(tail *), Bash(cat *)
description: 重启前后端服务（含 Docker/Milvus）
---

# 重启项目服务

重启前后端开发服务及依赖的 Docker 容器，并验证可用性。

## 当前状态

- 项目根目录: /Users/zhushihao/Projects/middleware_resource_manager
- 当前日期: !`date +%Y-%m-%d`

## 执行步骤

### 第一步：启动 Docker 依赖

检查 Colima 是否运行，未运行则启动：

```bash
colima status 2>&1 | grep -q "running" || colima start
```

启动 Milvus 向量数据库：

```bash
docker start milvus_etcd milvus_standalone 2>/dev/null || true
```

验证 Milvus 运行：

```bash
docker ps | grep milvus
```

### 第二步：停止旧进程

```bash
ps aux | grep "java.*middleware\|mvn.*spring\|vite" | grep -v grep | awk '{print $2}' | xargs kill -9 2>/dev/null
sleep 2
```

### 第三步：编译并启动后端

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
(cd backend && mvn compile -q)
```

如果编译失败，输出错误信息并停止。

启动后端：

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
> /tmp/backend.log
(cd backend && nohup mvn spring-boot:run -DskipTests >> /tmp/backend.log 2>&1 &)
```

等待启动完成（约 15-20 秒，Milvus 未就绪时可能需要 4 分钟）：

```bash
sleep 15
grep "Started\|ERROR" /tmp/backend.log | tail -3
```

如果启动失败，输出错误：

```bash
grep -B5 "APPLICATION FAILED\|BUILD FAILURE" /tmp/backend.log
```

### 第四步：启动前端

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager/frontend
nohup npx vite --host 0.0.0.0 > /tmp/frontend.log 2>&1 &
```

等待启动完成：

```bash
sleep 3
grep "ready\|Local" /tmp/frontend.log
```

### 第五步：验证服务

```bash
curl -s -o /dev/null -w "后端: %{http_code}\n" http://localhost:8080/api/wiki/pages
curl -s -o /dev/null -w "前端: %{http_code}\n" http://localhost:5173/
docker ps | grep milvus | awk '{print "Milvus:", $1, $7}'
```

输出启动结果摘要：各服务状态、端口。

## 日志位置

- 后端: `tail -f /tmp/backend.log`
- 前端: `tail -f /tmp/frontend.log`

## 常见问题

- **后端启动慢**: Milvus 未就绪会重试连接，等 Milvus 启动后自动继续
- **端口占用**: `lsof -i :8080` 或 `lsof -i :5173` 检查
- **编译失败**: 常见是新增 mapper 方法未同步 XML，或 import 缺失
