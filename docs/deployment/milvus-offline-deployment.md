# Milvus 离线生产部署手册

## 1. 适用场景

本文档用于在无法访问外部网络的内网环境中部署 Milvus Standalone。目标环境已安装 Docker 和 docker-compose。

本项目当前使用 `milvus-sdk-java 2.3.4`，因此部署包固定使用 Milvus `v2.3.4`，降低客户端兼容性风险。

## 2. 结论

生产初版建议使用：

- Milvus Standalone
- etcd
- MinIO
- Docker Compose
- 独立数据盘挂载 `/data/milvus`

不要使用 `latest` 镜像。不要把 `19530` 暴露到公网。

## 3. 为什么需要 MinIO

Milvus 2.x 需要元数据存储和对象存储：

- etcd：保存元数据。
- MinIO：保存 segment、index 等对象数据。
- Milvus Standalone：提供向量写入和检索服务。

你本地看起来“没有用 MinIO”，通常有两种情况：

1. 本地脚本把 MinIO 隐藏在启动脚本或容器组里。
2. 本地使用的是开发模式或旧脚本，不适合作为生产离线部署标准。

官方 Docker Compose Standalone 运行结果中包含 `milvus-etcd`、`milvus-minio`、`milvus-standalone` 三个服务，并且 Milvus 默认暴露 `19530` 和 `9091`。参考官方文档：[Run Milvus with Docker Compose](https://milvus.io/docs/install_standalone-docker-compose.md)。

## 4. 目录说明

项目内已准备离线部署材料：

```text
deploy/milvus-offline/
  .env.example
  docker-compose.yml
  images.txt
  scripts/
    prepare-offline-package.sh
    load-images.sh
    start.sh
    stop.sh
    healthcheck.sh
    backup.sh
```

## 5. 外网机器制作离线包

在能访问 Docker 镜像仓库的机器执行：

```bash
cd deploy/milvus-offline
chmod +x scripts/*.sh
./scripts/prepare-offline-package.sh
```

如果 Docker Hub 拉取 `milvusdb/milvus` 时出现 `EOF`、`connection reset`、`timeout`，先编辑 `.env`：

```bash
cp .env.example .env
vi .env
```

配置公司内可用的 Docker Hub 镜像代理：

```bash
DOCKER_HUB_MIRROR_PREFIX=registry.example.com/dockerhub
DOCKER_PULL_RETRIES=5
DOCKER_PLATFORM=linux/amd64
```

说明：

- `DOCKER_HUB_MIRROR_PREFIX` 只用于 Docker Hub 镜像，例如 `milvusdb/milvus`、`minio/minio`。
- `quay.io/coreos/etcd` 不走 Docker Hub 镜像代理。如果内网外网机器无法访问 quay.io，需要让网络侧放通 quay.io，或先在其他机器拉取后手工 `docker save`。
- 如果生产服务器是 x86_64 Linux，Apple Silicon Mac 制包时建议保留 `DOCKER_PLATFORM=linux/amd64`。

生成文件：

```text
deploy/milvus-offline/dist/milvus-offline-package.tar.gz
```

离线包包含：

- `milvusdb/milvus:v2.3.4`
- `quay.io/coreos/etcd:v3.5.5`
- `minio/minio:RELEASE.2023-03-20T20-16-18Z`
- `docker-compose.yml`
- `.env`
- 镜像 SHA-256 校验文件
- 启停和备份脚本

## 6. 内网服务器安装

上传离线包到内网服务器，例如 `/opt/packages`：

```bash
cd /opt/packages
tar -xzf milvus-offline-package.tar.gz
cd milvus-offline-package
chmod +x scripts/*.sh
```

导入镜像：

```bash
./scripts/load-images.sh
```

编辑 `.env`：

```bash
vi .env
```

重点修改：

```bash
MILVUS_DATA_DIR=/data/milvus
MILVUS_PORT=19530
MILVUS_WEBUI_PORT=9091
```

默认保留：

```bash
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
```

原因：Milvus `v2.3.x` Standalone 默认会按 `minioadmin/minioadmin` 访问 MinIO；如果只改 MinIO 账号而 Milvus 配置没有同步生效，会出现 `Access Key Id you provided does not exist`，随后 datacoord/querycoord 无法注册，`/healthz` 返回 500。

安全边界：本套 `docker-compose.yml` 默认将 MinIO `9000/9001` 绑定到 `127.0.0.1`，不要改成 `0.0.0.0` 对外暴露。应用只需要访问 Milvus `19530`。

如果必须改 MinIO 账号密码：

1. 在首次启动前修改 `.env`。
2. 确认 `docker compose config | grep -A8 -E 'MINIO|standalone'` 中 MinIO 和 standalone 的账号一致。
3. 如果已经启动过，必须先停止并清理旧的 MinIO 数据目录，否则 MinIO 会继续使用首次初始化的账号。

创建数据目录并启动：

```bash
./scripts/start.sh
```

健康检查：

```bash
./scripts/healthcheck.sh
```

预期结果：

- `milvus-etcd` 为 healthy 或 running。
- `milvus-minio` 为 healthy 或 running。
- `milvus-standalone` 为 healthy 或 running。
- `http://127.0.0.1:9091/healthz` 返回正常。

## 7. 应用连接配置

如果 Milvus 和应用同机：

```bash
VECTOR_TYPE=milvus
VECTOR_HOST=127.0.0.1
VECTOR_PORT=19530
VECTOR_COLLECTION=knowledge_chunks
```

如果 Milvus 独立部署在向量服务器：

```bash
VECTOR_TYPE=milvus
VECTOR_HOST=milvus-server-inner-ip
VECTOR_PORT=19530
VECTOR_COLLECTION=knowledge_chunks
```

建议只允许应用服务器访问 Milvus 服务器的 `19530`。

## 8. 防火墙建议

必须开放：

```text
19530/tcp  仅应用服务器访问
```

建议不开放或仅本机访问：

```text
9091/tcp   Milvus WebUI
9000/tcp   MinIO API
9001/tcp   MinIO Console
2379/tcp   etcd
```

本套 `docker-compose.yml` 默认将 MinIO 和 WebUI 绑定到 `127.0.0.1`，避免被其他机器直接访问。

## 9. 备份

文件系统级备份：

```bash
./scripts/backup.sh /backup
```

备份文件示例：

```text
/backup/milvus_20260611_103000.tar.gz
```

建议：

- 每日备份。
- 备份目录放到独立磁盘或备份服务器。
- 变更 Milvus 版本前必须先备份。

## 10. 停止和启动

停止：

```bash
./scripts/stop.sh
```

启动：

```bash
./scripts/start.sh
```

查看日志：

```bash
docker logs -f milvus-standalone --tail 200
docker logs -f milvus-etcd --tail 200
docker logs -f milvus-minio --tail 200
```

## 11. 常见问题

### 11.1 `/healthz` 返回 500，日志提示 no available datacoord/querycoord

先找根因日志：

```bash
docker logs milvus-standalone --tail 500 | grep -Ei 'Access Key|bucket|datacoord|querycoord|failed'
```

如果看到：

```text
The Access Key Id you provided does not exist in our records.
find no available datacoord
find no available querycoord
```

说明是 Milvus 连接 MinIO 的账号不匹配。新部署环境按下面步骤恢复：

```bash
docker compose down
sudo rm -rf /data/milvus/etcd /data/milvus/minio /data/milvus/milvus
vi .env
```

确保 `.env` 中是：

```bash
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
```

然后重启：

```bash
./scripts/start.sh
./scripts/healthcheck.sh
```

如果环境里已经有正式数据，不要直接删除 `/data/milvus`，先备份并确认是否需要迁移对象存储账号。

如果已经确认 `/data/milvus` 或整个 `/data` 都已删除并重新创建，旧 MinIO 口令不会继续影响启动。此时仍报同类错误，优先检查三件事：

1. `.env` 是否仍写成了自定义账号密码，例如 `Abcd1234`。
2. `docker compose config` 展开的 MinIO 和 standalone 账号是否一致。
3. 旧容器是否没有被重建，导致仍在使用旧环境变量。

推荐用下面命令强制回到已验证路径：

```bash
docker compose down
docker rm -f milvus-standalone milvus-minio milvus-etcd 2>/dev/null || true
sudo rm -rf /data/milvus
```

确认 `.env`：

```bash
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
```

启动前检查展开配置：

```bash
docker compose config | grep -A25 -E 'minio:|standalone:|MINIO'
```

展开结果中 MinIO 和 standalone 侧的 `MINIO_*` 账号必须都是 `minioadmin`。确认一致后再执行：

```bash
./scripts/start.sh
```

### 11.2 应用启动时报 Milvus 连接失败

检查：

```bash
docker ps | grep milvus
curl -fsS http://127.0.0.1:9091/healthz
```

确认应用配置：

```bash
VECTOR_TYPE=milvus
VECTOR_HOST=实际 Milvus 地址
VECTOR_PORT=19530
```

### 11.3 内存不足

Milvus Standalone 生产建议至少：

```text
CPU: 8 核
内存: 32 GB
磁盘: SSD 500 GB 起
```

小规模验证环境可降低到：

```text
CPU: 4 核
内存: 16 GB
磁盘: SSD 200 GB 起
```

### 11.4 需要临时关闭向量库

可以临时切回内存模式：

```bash
VECTOR_TYPE=memory
```

注意：内存模式不适合生产长期使用，重启后向量数据会丢失，需要重新构建。

## 12. 后续升级建议

当前固定 Milvus `v2.3.4` 是为了匹配项目中的 Java SDK。后续如果要升级到 Milvus `2.5/2.6`，建议同步升级：

- `milvus-sdk-java`
- 部署镜像版本
- 回归测试知识库导入、Wiki 编译、语义搜索、智能排查 Agent 查询

不要只升级服务端镜像。
