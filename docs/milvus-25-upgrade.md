# Milvus 2.3.4 → 2.5.10 升级说明

## 为什么升

2.3.4 只有稠密向量单路召回。运维查询里充斥参数名（`innodb_buffer_pool_size`）、
错误码（`ORA-01555`）、命令和版本号这类稀有精确 token，embedding 会把它们抹平；
而「主从延迟怎么处理」这类语义查询又只有稠密向量能处理。两者必须互补。

Milvus 2.5 内置 BM25（集成 Tantivy），可以在同一个 collection 里同时保存稠密向量
与稀疏向量，检索时用原生 `RRFRanker` 融合，不必自己实现分数归一化，也不必额外引入
Elasticsearch。

顺带解决一个既有缺陷：正文此前塞在 `metadata` 的 JSON 里，受 `VarChar(4096)` 限制，
中文按 3 字节/字符算，切片一大就 insert 失败——而失败路径只 `log.error`，切片静默
丢失，还会把标量过滤永久关闭。现在正文是独立的 `text` 列（65535，同时作为 BM25 的
输入），`metadata` 只放过滤用的轻量字段，问题从根上消失。

## 改了什么

| 项 | 前 | 后 |
|---|---|---|
| 服务端镜像 | `milvusdb/milvus:v2.3.4` | `milvusdb/milvus:v2.5.10` |
| Java SDK | `milvus-sdk-java:2.3.4`（v1 `MilvusServiceClient`） | `2.5.10`（v2 `MilvusClientV2`） |
| 向量维度 | 代码硬编码 1024 | `app.vector.dimension`，默认 1024 |
| 索引 | `IVF_FLAT` + 手动 `nprobe` | `AUTOINDEX`（稠密 COSINE / 稀疏 BM25） |
| 检索 | 单路稠密 | `hybridSearch` 稠密 + BM25，`RRFRanker(app.vector.rrf-k)` |
| 正文存放 | `metadata` JSON 内，受 4096 字节限制 | 独立 `text` 列（65535） |
| 写入 | `insert`，失败只记日志 | `upsert`，按主键覆盖不留旧切片 |

collection schema：

```
id          VarChar(100)  主键
vector      FloatVector(app.vector.dimension)
text        VarChar(65535) enableAnalyzer=true   ← BM25 输入
sparse      SparseFloatVector                    ← BM25 Function 输出
metadata    VarChar(4096)                        ← 过滤用轻量字段（不含正文）
source / source_type / source_id / category / software / status   VarChar(200)
```

## 升级步骤

**旧 collection 必须删除重建**。2.3.4 建的 collection 没有 `text` / `sparse` 字段，
应用启动时 `verifyHybridSchema` 会直接抛异常并说明处置方式，不会带病运行。

反正解析层（PDF/Word/Excel 结构化）与切片层（sectionPath、overlap、表格保护）都变了，
向量本就需要全量重建，这里没有额外损失。

```bash
# 1. 停应用
# 2. 升级 Milvus
cd deploy/milvus-offline
docker compose down
docker compose pull        # 离线环境改为 docker load -i milvus-images.tar
docker compose up -d

# 3. 删除旧 collection（二选一）
#    a) 用 attu / pymilvus 手工 drop knowledge_chunks
#    b) 起应用后调用 MilvusVectorStore.recreateCollection()

# 4. 起应用，重新导入全部文档
```

## 配置项

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `app.vector.dimension` | `VECTOR_DIMENSION` | 1024 | 必须与 embedding 模型一致。换模型要改这里并重建 collection |
| `app.vector.rrf-k` | `VECTOR_RRF_K` | 60 | RRF 融合平滑系数，越大越平滑 |

## 验收

升级前后跑同一份 golden set 对比（见 `docs/eval/README.md`）：

```bash
python3 scripts/eval-retrieval.py --k 5 --json .scratch/after.json
```

重点看 `exact_param` 与 `error_code` 两个分桶——如果混合检索生效，这两桶的 Recall@5
应显著高于升级前；`semantic` 桶不应下降（下降说明 RRF 参数需要调）。

---

## 附：切换 embedding 模型到 bge-m3

`bge-large` 的 512 token 上下文是 KBV-001（长 PDF 上传失败）的根因。切到 `bge-m3`
后上下文提升到 8192 token，参数表格可以整块进一个切片，不必再为迁就模型把切片
砍碎。

**维度不变**：bge-m3 的稠密输出同样是 1024 维，`VECTOR_DIMENSION` 不用改。

| | bge-large | bge-m3 |
|---|---|---|
| 参数量 | 326M | 568M |
| 上下文 | 512 token | 8192 token |
| 输出维度 | 1024 | 1024 |
| Ollama 体积 | ~670MB | ~1.2GB |
| 运行内存 | ~1GB | ~2~3GB |

```bash
ollama pull bge-m3

# 环境变量
EMBEDDING_MODEL=bge-m3
EMBEDDING_MAX_TOKENS=8192     # 切片预算与 embedding 截断长度都由它推导
```

改完必须**删除 collection 重建并重新导入**——向量由新模型生成，与旧向量不可混用。

### 内存吃紧时（16G 机器）

不必把 8192 token 跑满。切片预算有 1600 字符的实际上限（`MAX_PRACTICAL_CHUNK_SIZE`），
切片过大反而稀释语义。16G 机器上的分配建议：

- Docker 给 Milvus 栈 4G（小数据量够用，不必按 8G 配）
- Ollama + bge-m3 约 2~3G
- 三个 JVM 各 512M（`-Xmx512m`）
- 其余留给 MySQL 与系统

### 切片预算怎么算的

```
预算 = min(1600, token上限 × 0.85)
```

中文最坏情况约 1 字 1 token，0.85 是留给面包屑前缀与分词波动的余量。
bge-large → 435 字符；bge-m3 → 1600 字符（触到实际上限）。

此前 splitter 与 embedding 各自持有不同的字符上限（900 vs 1500/300），两个数字对不上，
结果要么撑爆模型上下文，要么切片后半段被静默丢弃。现在统一由 `app.embedding.max-tokens`
推导，只有一个来源。
