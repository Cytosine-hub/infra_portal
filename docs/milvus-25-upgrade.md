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
