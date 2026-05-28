# 服务器 Milvus 向量数据库安装手册

## 一、离线镜像打包与上传

### 1.1 本地打包镜像

```bash
# 在本地机器执行（已有镜像）
docker save milvusdb/milvus:latest quay.io/coreos/etcd:v3.5.18 -o milvus-images.tar

# 压缩（可选，约 2.5GB）
gzip milvus-images.tar
```

### 1.2 上传到服务器

```bash
# 方式一：scp 传输
scp milvus-images.tar.gz user@server:/tmp/

# 方式二：rsync（支持断点续传）
rsync -avz --progress milvus-images.tar.gz user@server:/tmp/
```

### 1.3 服务器导入镜像

```bash
# 解压（如果压缩过）
gunzip milvus-images.tar.gz

# 导入镜像
docker load -i milvus-images.tar

# 验证
docker images | grep -E "milvus|etcd"
# 应该看到：
# milvusdb/milvus       latest    xxx
# quay.io/coreos/etcd   v3.5.18   xxx
```

## 二、部署 Milvus

### 2.1 创建数据目录

```bash
mkdir -p /data/milvus/etcd
mkdir -p /data/milvus/storage
```

### 2.2 启动 etcd

```bash
docker run -d --name milvus_etcd \
  -p 2379:2379 -p 2380:2380 \
  -v /data/milvus/etcd:/etcd \
  --restart always \
  quay.io/coreos/etcd:v3.5.18 \
  etcd -listen-client-urls=http://0.0.0.0:2379 \
       -advertise-client-urls=http://0.0.0.0:2379 \
       -data-dir=/etcd
```

### 2.3 启动 Milvus

```bash
docker run -d --name milvus \
  --link milvus_etcd:etcd \
  -p 19530:19530 -p 9091:9091 \
  -v /data/milvus/storage:/var/lib/milvus \
  -e ETCD_ENDPOINTS=etcd:2379 \
  -e COMMON_STORAGETYPE=local \
  --restart always \
  milvusdb/milvus:latest \
  milvus run standalone
```

### 2.4 验证部署

```bash
# 检查容器状态
docker ps | grep milvus

# 健康检查
curl http://localhost:9091/healthz
# 返回 OK 即成功

# 检查端口
ss -tlnp | grep 19530
```

## 三、应用配置

### 3.1 环境变量

在应用启动脚本或 systemd 服务文件中添加：

```bash
# Milvus 连接配置
export VECTOR_TYPE=milvus
export VECTOR_HOST=localhost    # 如果 Milvus 部署在同一台服务器
export VECTOR_PORT=19530
export VECTOR_COLLECTION=knowledge_chunks

# Embedding 模型配置（使用本地 Ollama）
export EMBEDDING_BASE_URL=http://localhost:11434/v1
export EMBEDDING_API_KEY=ollama
export EMBEDDING_MODEL=bge-large
```

### 3.2 systemd 服务文件示例

```ini
[Unit]
Description=Middleware Resource Manager
After=network.target docker.service

[Service]
Type=simple
User=app
WorkingDirectory=/opt/middleware-resource-manager/backend
ExecStart=/usr/bin/java -jar middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar

# 数据库配置
Environment="APP_DB_HOST=127.0.0.1"
Environment="APP_DB_PORT=3306"
Environment="APP_DB_NAME=middleware_resource_manager"
Environment="APP_DB_USERNAME=root"
Environment="APP_DB_PASSWORD=your_password"

# Milvus 配置
Environment="VECTOR_TYPE=milvus"
Environment="VECTOR_HOST=localhost"
Environment="VECTOR_PORT=19530"

# AI 模型配置
Environment="AI_BASE_URL=https://your-ai-api.com/v1"
Environment="AI_API_KEY=your-api-key"

# Embedding 模型配置
Environment="EMBEDDING_BASE_URL=http://localhost:11434/v1"
Environment="EMBEDDING_API_KEY=ollama"
Environment="EMBEDDING_MODEL=bge-large"

Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

## 四、启动影响分析

### 4.1 Milvus 未安装/未启动

**影响：应用无法启动**

- 默认配置 `app.vector.type=milvus`
- `MilvusVectorStore.init()` 使用 `@PostConstruct`，连接失败会抛异常
- Spring 容器初始化失败，应用退出

**解决方案：**
1. 安装并启动 Milvus（本手册）
2. 或临时切换为内存模式：`export VECTOR_TYPE=memory`

### 4.2 AI 模型未配置

**影响：应用可以启动，AI 功能不可用**

- LangChain4j 的 ChatModel 和 EmbeddingModel 通过 Spring Boot 自动配置
- 如果 API 地址无效，应用仍可启动
- 调用 AI 功能时会报错（对话、知识库检索等）

**解决方案：**
1. 配置有效的 AI API 地址和 Key
2. 或确保本地 Ollama 服务运行

### 4.3 Embedding 模型未配置

**影响：应用可以启动，向量检索不可用**

- Embedding 模型用于将文本转换为向量
- 如果 Embedding API 不可用，知识库的向量搜索会失败
- 但应用本身可以正常启动和运行其他功能

**解决方案：**
1. 配置本地 Ollama + BGE 模型
2. 或使用远程 Embedding API

## 五、Ollama 安装（Embedding 模型）

如果服务器需要本地 Embedding 模型：

```bash
# 安装 Ollama
curl -fsSL https://ollama.com/install.sh | sh

# 启动 Ollama 服务
ollama serve &

# 下载 BGE 模型
ollama pull bge-large

# 验证
curl http://localhost:11434/api/embeddings -d '{
  "model": "bge-large",
  "prompt": "测试"
}'
```

## 六、完整启动顺序

```bash
# 1. 启动 MySQL
systemctl start mysql

# 2. 启动 Milvus（Docker）
docker start milvus_etcd
docker start milvus

# 3. 启动 Ollama（如果使用本地 Embedding）
ollama serve &

# 4. 启动应用
systemctl start middleware-resource-manager

# 5. 验证
curl http://localhost:8080/api/public/parameter-standards
```

## 七、故障排查

### 7.1 Milvus 连接失败

```bash
# 检查 Milvus 是否运行
docker ps | grep milvus

# 检查日志
docker logs milvus --tail 100

# 检查端口是否监听
ss -tlnp | grep 19530

# 测试连接
curl http://localhost:9091/healthz
```

### 7.2 应用启动失败

```bash
# 查看应用日志
journalctl -u middleware-resource-manager -f

# 常见错误：
# - "Failed to connect to Milvus" → Milvus 未启动或地址配置错误
# - "Connection refused" → 端口未监听
```

### 7.3 切换为内存模式（临时）

如果 Milvus 暂时无法使用：

```bash
export VECTOR_TYPE=memory
systemctl restart middleware-resource-manager
```

注意：内存模式下向量数据不持久化，重启后丢失。
