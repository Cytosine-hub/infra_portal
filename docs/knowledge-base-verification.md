# 知识库上环境验证清单

分支：`feature/req-knowledge-issue-parser`（已推送，**未合并 master**）

本次改造涉及解析层、切片层、向量存储、检索方式、权限模型五处变更，且**全部未在真实环境验证过**——本机没有 Milvus、embedding 模型与项目数据库。本文列出需要在具备条件的环境上逐项确认的点。

按顺序执行。**第 0 步不通过，后面所有数字都没有意义**，不要跳过。

---

## 一、前置准备

### 1.1 依赖服务

```bash
# Milvus 必须是 2.5+（BM25 自 2.5 引入）
cd deploy/milvus-offline
docker compose down
docker compose pull              # 离线环境改为 docker load -i milvus-images.tar
docker compose up -d
docker ps | grep milvus          # 确认版本是 v2.5.10

# embedding 模型
ollama serve && ollama pull bge-large     # 或按 application-prod.yml 指向智谱

# MySQL
mysql -uroot -p < db/init.sql
```

### 1.2 执行四个 DDL（都可重复执行，**执行前先备份**）

```bash
mysql -uroot -p middleware_resource_manager < db/upgrade_20260729_drop_wiki_ingest_tables.sql
mysql -uroot -p middleware_resource_manager < db/upgrade_20260729_reset_wiki_pages.sql
mysql -uroot -p middleware_resource_manager < db/upgrade_20260730_drop_knowledge_chunks.sql
mysql -uroot -p middleware_resource_manager < db/upgrade_20260730_wiki_fulltext_ngram.sql
```

第二个会**清空 `wiki_pages`**（按既定决策：LLM 编译产物全部清除，经验区改为人工书写）。如需留档，先跑 `GET /api/knowledge/pages/export`。

### 1.3 删除旧 Milvus collection

2.3.4 建的 collection 没有 `text` / `sparse` 字段。**应用启动时会主动抛异常拦住**（`verifyHybridSchema`），不会带病运行——看到下面这条报错属于预期，按提示删除即可：

```
Milvus collection 'knowledge_chunks' 缺少 text / sparse 字段，是 2.5 之前的旧 schema…
```

用 attu 或 pymilvus 删除后重启应用，会自动按新 schema 重建。

> 命名提示：Milvus collection 仍叫 `knowledge_chunks`，与刚删掉的 MySQL 同名表**没有关系**。嫌混淆可以设 `VECTOR_COLLECTION=knowledge_index`，反正这次要重建。

### 1.4 关键配置核对

| 配置 | 环境变量 | 默认 | 必须确认 |
|---|---|---|---|
| `app.vector.dimension` | `VECTOR_DIMENSION` | 1024 | **必须与 embedding 模型实际维度一致**，不一致 Milvus 直接拒绝写入 |
| `app.vector.analyzer` | `VECTOR_ANALYZER` | `chinese` | 中文分词器，**collection 创建后不可更改** |
| `app.vector.rrf-k` | `VECTOR_RRF_K` | 60 | RRF 融合平滑系数 |

---

## 二、第 0 步：验证中文分词（最高优先级）

**为什么最优先**：改造前两条检索路径的中文分词都是坏的（Milvus 走默认 standard 分词器把整段中文当一个 token；MySQL FULLTEXT 无 ngram 解析器）。本次都已修，但**分词效果只能实测**。分词不对，BM25 那一路等于没有，混合检索退化回单路，后面的 Recall 数字全部失真。

### 2.1 Milvus BM25 分词

上传一篇中文文档后，搜一个**句子中间的词**（不是开头、不是标题）：

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/knowledge/search?q=主从延迟&topK=5"
```

| 结果 | 判断 |
|---|---|
| 能命中含「主从延迟」的正文片段 | ✅ 分词正常 |
| 返回空，但文档里确实有这个词 | ❌ 分词失效 |

失效时的处置：改 `VECTOR_ANALYZER`（可选 `chinese` / `icu` / 自定义 jieba 参数），**删除 collection 重建**，重新导入。

想直接看切分结果，可用 pymilvus：

```python
from pymilvus import MilvusClient
c = MilvusClient(uri="http://localhost:19530")
print(c.run_analyzer("MySQL主从延迟处理方案", analyzer_params={"type": "chinese"}))
# 期望切出「主从」「延迟」等独立 token，而非整句一个 token
```

### 2.2 MySQL 全文分词

```sql
SELECT title FROM wiki_pages
WHERE MATCH(title, summary, content) AGAINST('主从延迟' IN BOOLEAN MODE);
```

需要先有一篇正文含「主从延迟」的 ACTIVE 经验页。命中即正常；返回空说明 ngram 索引没建上，检查：

```sql
SHOW INDEX FROM wiki_pages WHERE Key_name = 'ft_content';   -- Comment 列应含 ngram
```

### 2.3 混合检索两路都要验

分词正常后，用两类查询各验一次——**这是本次升级的核心收益，必须分开看**：

| 查询类型 | 例子 | 靠哪一路 |
|---|---|---|
| 精确 token | `innodb_buffer_pool_size`、`ORA-01555` | BM25 稀疏路 |
| 语义描述 | 「主从延迟怎么处理」 | 稠密向量路 |

两类都能召回才算混合检索真正生效。**只有一类能召回 = 有一路没工作**。

---

## 三、解析层（四种格式）

每种格式各传一份**真实运维文档**，检查解析是否保住了结构。

```bash
curl -H "Authorization: Bearer $TOKEN" -F "file=@真实文档.pdf" \
  http://localhost:8080/api/knowledge/upload
```

| 格式 | 重点看 | 期望 |
|---|---|---|
| **PDF** | 有书签的 PDF | 切片带 `sectionPath`，说明书签标题回填成功 |
| **Excel** | 参数台账 | **同一行的参数名与取值在同一切片内**——这是本次最关键的修复 |
| **Word .docx** | 带标题样式和表格 | 标题层级正确、表格保留为 Markdown 表 |
| **Word .doc** | 老版格式 | 能解析出正文（⚠️ 见第八节，此路径无真实样本测试过） |

**失败信号**：后端日志出现 `XXX 结构化解析失败，降级为 Tika 扁平文本` 的 WARN。这条日志是本次新加的——此前降级是完全静默的。看到它说明该文档没走结构化路径，检索质量会下降。

---

## 四、切片层

检索任一结果，检查返回的 `sectionPath` 字段：

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/knowledge/search?q=<任意查询>&topK=3"
```

| 检查项 | 期望 |
|---|---|
| `sectionPath` 非空 | 形如 `MySQL / 应急处理 / 主从延迟` |
| 大章节的**每个**切片都带 `sectionPath` | 改造前只有首个子块带 |
| `content` 以面包屑开头 | 前缀已写入正文供检索命中 |
| 代码块里的 `# 注释` 没被当标题 | 传一篇含 ```bash 块的文档验证 |
| 参数表格未被拦腰切断 | 超大表格拆分时每片重复表头 |

---

## 五、标准自动进索引

这是本次补上的关键链路——此前参数标准这个唯一真相源完全不参与检索。

```bash
# 手动触发对账（也会在应用启动时自动异步执行一次）
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/knowledge/sync-standards
```

返回 `{indexed, skipped, removed, failed}`。验证三种场景：

1. **新发布标准** → `indexed` 增加，随后能检索到该标准内容
2. **再次触发** → 全部落入 `skipped`（内容哈希未变，不重复消耗 embedding）
3. **撤下某个标准的发布** → 再次触发后 `removed` 增加，且**检索不到**该内容

第 3 点尤其要验：撤下的标准还能被检索到，等于给出已经不作数的答案。

---

## 六、参数精确查询

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/knowledge/parameters?software=MySQL&name=innodb_buffer_pool_size"
```

期望返回带 `standardTitle` 和 `standardVersion` 的确定答案。

> ⚠️ **`standard_parameters` 目前是空表**，接口做好了但没数据，现在查什么都返回空。
> 这不是 bug。录入参数是收益最高的一件事——参数类问题的准确率会从「看运气」变成 100%，
> 且主要是内容工作不是代码工作。

---

## 七、权限模型（已按决定放开读权限）

用**非管理员、且岗位与内容分类不同**的账号（如数据库岗账号查中间件内容）验证：

| 场景 | 期望 |
|---|---|
| 检索其他岗位的内容 | ✅ 能查到（读权限已对所有岗位开放） |
| 查看文档列表 | ✅ 能看到全部文档 |
| 检索到 `DRAFT` 状态的页面 | ❌ **不应该**——草稿只有作者和管理员可见 |
| 把草稿改为 `ACTIVE` 发布 | ❌ 403，发布需审核权限 |
| 批量改分类 | ❌ 403，需管理岗 |
| 新建经验页面 | ✅ 管理岗可以，落为草稿 |

设计取舍：运维排查天然跨域（数据库慢可能是主机 IO 或网络丢包），按岗位隔离**读**权限会废掉跨域排查能力。敏感内容用页面级 `RESTRICTED` / `HIDDEN` 单独收口，**写**权限仍按岗位限制。

---

## 八、前端

```bash
cd frontend
node -v          # 必须 20.x（package.json engines: >=20.19 <21）
npm install
npm run build    # 应通过
npm test         # 60 用例应全绿
```

页面在「知识库」入口，五个标签：检索 / 文档 / 经验沉淀 / 图谱 / 健康度。

**重点点一遍这几处**（自动化测试覆盖不到交互细节）：

- 文档标签的「上传文档」按钮——能否真的弹出文件选择框
- 经验沉淀的「新建页面」和「从文档起草」
- 图谱标签能否正常渲染（依赖已有经验页面之间的 `[[wikilink]]` 引用）
- 健康度的「运行体检」

---

## 九、质量基线

前八步都通过后，才有意义跑量化评测。

```bash
# 1. 扩充 golden set 到 50~100 条（当前只有 10 条种子模板，不构成结论）
vim docs/eval/golden-set.json

# 2. 跑基线
export MRM_TOKEN=$(通过 /test-login 获取)
python3 scripts/eval-retrieval.py --k 5 --json .scratch/baseline.json
```

**怎么读数**：总体 Recall@5 参考价值有限，要看两个分桶——

| 分桶对比 | 说明什么 |
|---|---|
| `exact_param` / `error_code` 桶 vs `semantic` 桶 | 前者明显偏低 → BM25 那一路没生效，回第 2 节查分词 |
| `pdf` / `xlsx` 桶 vs `docx` 桶 | docx 是解析质量基准线，差值就是这两种格式还欠的账 |

详细口径见 `docs/eval/README.md`（含 RAGAS 生成层指标与语料健康层指标）。

---

## 十、已知不工作 / 未验证的部分

**必须知道，避免误判为故障：**

| 项 | 状态 |
|---|---|
| `standard_parameters` 空表 | 参数查询接口返回空是正常的，等录入 |
| `wiki_pages` 被清空 | 经验沉淀标签为空、健康度无数据、检索无经验类结果，都是预期 |
| 智能排查 Agent | **未重构**，仍是 YAML Skill 编排，`query_metrics` / `search_logs` 是空桩 |
| `.doc` 老版 Word 解析 | 实现了但**无真实样本测试过**，POI HWPF 路径需要你用真实 `.doc` 验证 |
| 中文 PDF 解析 | 单元测试用的是 ASCII（PDFBox Standard14 字体编不了中文），中文 PDF 路径需实测 |
| Milvus 混合检索 | **本机无实例，全部代码未运行过**，仅编译通过 + API 签名核实 |
| 三个 UI 未补回 | 富预览、批量改分类、页面权限——后端能力都在，界面够不着 |
| Wiki 导入 UI | 后端 `POST /pages/import` 在，前端只有导出没有导入 |

**已知安全债**（未修，若启用导入功能需先处理）：
`WikiExportService` 的签名是 `sha256(secret + payload)` 而非 HMAC，默认密钥硬编码为 `middleware-resource-manager`，比较非常量时间。当前导入无前端入口，风险有限；若要启用导入，先修签名。

---

## 十一、回滚

本分支未合并 master，回滚即切回 master 分支。

数据侧：四个 DDL 中 `reset_wiki_pages.sql` 和 `drop_knowledge_chunks.sql` 是**破坏性**的，回滚需要从备份恢复。Milvus collection 重建后旧向量不可恢复，但可通过重新导入文档再生。

---

## 反馈方式

验证中发现问题，请带上：
1. 后端日志中的 WARN / ERROR 原文（尤其含「结构化解析失败」「metadata 超出」「Milvus」字样的）
2. 出问题的查询语句与期望命中的文档
3. `scripts/eval-retrieval.py --json` 的输出文件

有这三样才能定位是解析、切片、分词还是融合排序的问题。
