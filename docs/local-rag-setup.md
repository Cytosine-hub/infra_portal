# 本地 RAG 完整实现方案

## 架构

```
本地开发：
  LM Studio (localhost:1235)  →  bge-large-zh-v1.5  →  embedding
  Milvus (Docker localhost:19530)                     →  向量存储
  小米 API (远程)              →  MiMo-v2.5-pro      →  对话推理

生产上线：
  智谱 API (远程)              →  embedding-3         →  embedding
  智谱 API (远程)              →  glm-4-plus          →  对话推理
  Milvus (生产服务器)                                    →  向量存储
```

## 第一步：本地安装

### 1. Docker Desktop（已有的话跳过）
- 下载：https://www.docker.com/products/docker-desktop/
- 安装后启动

### 2. LM Studio
- 下载：https://lmstudio.ai/
- 安装后打开，下载两个模型：
  - 搜索下载 `bge-large-zh-v1.5`（中文嵌入模型）
  - 启动本地服务器：设置 → Local Server → 启动在端口 1235
  - 模型选择 bge-large-zh-v1.5

### 3. Milvus（Docker 一键启动）
```bash
# 下载启动脚本
curl -sfL https://raw.githubusercontent.com/milvus-io/milvus/master/scripts/standalone_embed.sh -o standalone_embed.sh

# 启动 Milvus
bash standalone_embed.sh start

# 验证启动成功
docker ps | grep milvus
```

## 第二步：配置修改

### 创建 application-local.yml

```
langchain4j:
  open-ai:
    chat-model:
      base-url: https://token-plan-cn.xiaomimimo.com/v1
      api-key: tp-c917uu165jgq2dosv8104vtxi7i8uaqb3wqkq5zxlbwm5haz
      model-name: mimo-v2.5-pro
      max-tokens: 4096
      temperature: 0.1
    embedding-model:
      base-url: http://localhost:1235/v1
      api-key: lm-studio
      model-name: bge-large-zh-v1.5

app:
  vector:
    type: milvus
    host: localhost
    port: 19530
    collection: knowledge_chunks
```

### 创建 application-prod.yml

```
langchain4j:
  open-ai:
    chat-model:
      base-url: https://open.bigmodel.cn/api/paas/v4
      api-key: ${GLM_API_KEY}
      model-name: glm-4-plus
      max-tokens: 4096
      temperature: 0.1
    embedding-model:
      base-url: https://open.bigmodel.cn/api/paas/v4
      api-key: ${GLM_API_KEY}
      model-name: embedding-3

app:
  vector:
    type: milvus
    host: ${MILVUS_HOST:localhost}
    port: ${MILVUS_PORT:19530}
    collection: knowledge_chunks
```

## 第三步：代码修改

### 修改 MilvusVectorStore 向量维度

bge-large-zh-v1.5 的向量维度是 1024，不是 384。需要修改 MilvusVectorStore.java 中的 VECTOR_DIM。

### 修改 InMemoryVectorStore 向量维度

同上，fallback hash 向量维度也要改。

### 修改 EmbeddingService

去掉 hash fallback，embedding 失败直接抛异常，不要降级到随机向量（那会导致搜索结果无意义）。

## 第四步：本地启动流程

```bash
# 1. 启动 Milvus
docker start milvus

# 2. 启动 LM Studio（GUI 操作）
#    - 加载 bge-large-zh-v1.5
#    - 启动 Local Server 端口 1235

# 3. 启动后端
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. 启动前端
cd frontend && npm run dev
```

## 第五步：验证

1. 上传几个文档
2. 搜索 "破解密码" → 应该能找到 "解密" 相关文档（语义匹配）
3. 搜索 "连接超时" → 应该能找到网络、超时相关文档
4. 对话测试 → MiMo 基于搜索结果回答
