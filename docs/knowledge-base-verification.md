# 知识库上环境验证清单

分支：`feature/req-knowledge-issue-parser`（已推送，**未合并 master**）

本次改造涉及解析层、切片层、向量存储、检索方式、权限模型五处变更。本文列出需要在具备条件的环境上逐项确认的点；2026-07-30 已完成一次本地真实环境验证，结果与遗留问题见第十一节。

按顺序执行。**第 0 步不通过，后面所有数字都没有意义**，不要跳过。

---

## 一、部署要求

完整部署参见 `docs/startup-manual.md` 与 `docs/production-deploy.md`，本节只列**验证本次改造所必需**的部分。

### 1.1 运行时版本

| 组件 | 版本要求 | 说明 |
|---|---|---|
| JDK | **17** | 项目编译目标 |
| Node | **>= 20.19**，无上界 | 高版本可直接用（实测 Node 26 通过）。CI 构建走 `deploy/Dockerfile.frontend` 的 `node:20.19-alpine`，与本地版本无关 |
| MySQL | **8.0** | ngram 全文解析器需 5.7.6+，本项目按 8.0 |
| Milvus | **2.5.10**（不能低于 2.5） | BM25 自 2.5 引入，2.3.x 无 `text` / `sparse` 字段 |
| Docker | 支持 compose v2 | Milvus 及其依赖 |

### 1.2 只需起三个服务

项目共 9 个服务（8080~8089），但**验证知识库只需要三个**：

| 服务 | 端口 | 为什么需要 |
|---|---|---|
| `api-gateway` | 8080 | 唯一入口，集中认证 |
| `core-service` | 8084 | 登录与 token introspect，网关依赖它校验身份 |
| `ai-service` | 8083 | 知识库本体（knowledge + wiki + ops-agent） |

其余六个（community 8082、middleware 8085、database 8086、host 8087、network 8088、security 8089）与知识库无关，不必启动。

```bash
cd backend && mvn clean package -DskipTests
# 三个终端分别执行，或用 scripts/ 下的启动脚本
mvn -pl core-service -am spring-boot:run
mvn -pl ai-service   -am spring-boot:run
mvn -pl api-gateway  -am spring-boot:run
```

### 1.3 必需环境变量（无默认值，缺失会直接失败）

以 `scripts/services.env.example` 为模板，**三个服务必须使用同一份环境**：

| 变量 | 必需性 | 缺失后果 |
|---|---|---|
| `GATEWAY_SIGNING_SECRET` | **必填**，至少 32 UTF-8 字节 | 三个服务必须**完全一致**。不一致或漏设 → 所有受保护端点 401，且现象是"登录成功但什么都调不通" |
| `APP_DB_PASSWORD` | 必填 | 无法连库 |
| `AI_API_KEY` | 起草与 RAG 问答需要 | 缺失时检索仍可用，但「从文档起草」和对话会失败 |
| `ADMIN_DEFAULT_PASSWORD` | 首次初始化需要 | 无法登录 |
| `EMBEDDING_BASE_URL` | 默认 `http://localhost:11434/v1` | 指向实际 embedding 服务 |
| `EMBEDDING_MODEL` | 默认 `bge-large` | 必须与 `VECTOR_DIMENSION` 匹配 |
| `VECTOR_HOST` / `VECTOR_PORT` | 默认 `localhost:19530` | 指向 Milvus |

`ZABBIX_*`、`NACOS_*`、`WIKI_EXPORT_SIGNATURE_SECRET` 本次验证用不到，可留空（Nacos 仅在 `cloud` profile 启用，默认关闭）。

### 1.4 Milvus（含两个依赖组件）

Milvus standalone 需要 **etcd + minio** 一起跑，离线环境三个镜像都要准备：

```bash
cd deploy/milvus-offline
cat images.txt          # milvusdb/milvus:v2.5.10 / etcd:v3.5.5 / minio

# 联网环境
docker compose pull && docker compose up -d

# 离线环境：先在有网机器上导出
docker pull $(cat images.txt | tr '\n' ' ')
docker save $(cat images.txt | tr '\n' ' ') -o milvus-images.tar
# 拷到目标机后
docker load -i milvus-images.tar && docker compose up -d

docker ps | grep milvus   # 确认是 v2.5.10
```

⚠️ **镜像版本本次已从 v2.3.4 升到 v2.5.10**，离线环境需要重新导出，不能沿用旧的 tar 包。

资源上，Milvus standalone 建议至少 4C8G；数据量小（几万切片）时磁盘占用不大。

### 1.5 其他

- **文件存储目录**：上传的原始文档落在 `./storage/`（`app.storage.location`），确保 ai-service 进程对该目录有写权限
- **MySQL 初始化**：`mysql -uroot -p < db/init.sql`

### 1.6 执行四个 DDL（都可重复执行，**执行前先备份**）

```bash
mysql -uroot -p middleware_resource_manager < db/upgrade_20260729_drop_wiki_ingest_tables.sql
mysql -uroot -p middleware_resource_manager < db/upgrade_20260729_reset_wiki_pages.sql
mysql -uroot -p middleware_resource_manager < db/upgrade_20260730_drop_knowledge_chunks.sql
mysql -uroot -p middleware_resource_manager < db/upgrade_20260730_wiki_fulltext_ngram.sql
```

第二个会**清空 `wiki_pages`**（按既定决策：LLM 编译产物全部清除，经验区改为人工书写）。如需留档，先跑 `GET /api/knowledge/pages/export`。

### 1.7 删除旧 Milvus collection

2.3.4 建的 collection 没有 `text` / `sparse` 字段。**应用启动时会主动抛异常拦住**（`verifyHybridSchema`），不会带病运行——看到下面这条报错属于预期，按提示删除即可：

```
Milvus collection 'knowledge_chunks' 缺少 text / sparse 字段，是 2.5 之前的旧 schema…
```

用 attu 或 pymilvus 删除后重启应用，会自动按新 schema 重建。

> 命名提示：Milvus collection 仍叫 `knowledge_chunks`，与刚删掉的 MySQL 同名表**没有关系**。嫌混淆可以设 `VECTOR_COLLECTION=knowledge_index`，反正这次要重建。

### 1.8 关键配置核对

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
| **Word .doc** | 老版格式 | 能解析出正文（已用 DOCX 转换样本跑通；仍需补原生历史 `.doc`，见第十、十一节） |

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
node -v          # >= 20.19 即可，无上界
npm install
npm run build    # 应通过
npm test         # 60 用例应全绿
```

> 若 `npm install` 报 `ETARGET: No matching version found for wrap-ansi-cjs`，
> 是 lockfile 与 npm 版本不兼容，**与 Node 版本无关**。确认分支已 rebase 到最新
> master（远端已重写 `package-lock.json` 修复此问题）。

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
| `standard_parameters` 数据不足 | 本地评分时只有 5 条 ACTIVE 测试参数，覆盖 nginx/MySQL，尚不能代表真实参数库 |
| `wiki_pages` 语料不足 | 本地评分时只有 2 页（1 ACTIVE、1 DRAFT），Wiki 召回与图谱质量不能据此下结论 |
| 智能排查 Agent | **未重构**，仍是 YAML Skill 编排，`query_metrics` / `search_logs` 是空桩 |
| `.doc` 老版 Word 解析 | 用真实 DOCX 转换出的 OLE2 `.doc` 上传成功并生成 4 个切片，但 POI 输出大量 PAPX 修复 WARN；仍需补一份原生历史 `.doc` 样本 |
| 中文 PDF 解析 | 7 页真实中文 PDF 生成 34 个切片；2026-07-31 使用 bge-m3 复测后，32 页 TongWeb 手册生成 30 个切片、158 页集群手册生成 144 个切片，均无 embedding 上下文超限，见 11.8 |
| Milvus 混合检索 | 已在 Milvus 2.6.13 验证稠密向量 + BM25、`chinese` analyzer 和 RRF 检索；2.5.10 镜像因 Docker Hub 超时未完成同版本验证 |
| 三个 UI 未补回 | 富预览、批量改分类、页面权限——后端能力都在，界面够不着 |
| Wiki 导入 UI | 后端 `POST /pages/import` 在，前端只有导出没有导入 |

**已知安全债**（未修，若启用导入功能需先处理）：
`WikiExportService` 的签名是 `sha256(secret + payload)` 而非 HMAC，默认密钥硬编码为 `middleware-resource-manager`，比较非常量时间。当前导入无前端入口，风险有限；若要启用导入，先修签名。

---

## 十一、2026-07-30 本地验证记录

### 11.1 环境与数据保护

| 项 | 实测值 |
|---|---|
| 分支 / 提交 | `feature/req-knowledge-issue-parser` / `753e380` |
| JDK / Node / npm | JDK 17.0.15 / Node 23.11.0 / npm 11.14.1 |
| MySQL | 本机 MySQL 8.0.42；四个升级 DDL 已执行 |
| Milvus | 2.6.13 standalone，4C8G；etcd 3.5.5 + MinIO，三个容器 healthy |
| Embedding | Ollama 0.24.0 + `bge-large`，实测维度 1024 |
| 生成模型 | Codex 配置的 `gpt-5.6-terra`，通过 OpenAI 兼容 `/v1/chat/completions` 临时注入进程环境 |
| 备份 | `.scratch/backups/knowledge-base-preupgrade-20260730T163716/`；含 MySQL dump、旧 Milvus/etcd 镜像和旧 collection 描述 |

MySQL 备份 SHA-256：
`a3dde6a80f64cb55cbdc553d56ec255e4e0176163eb8a696419fa987e2491e8a`

旧 Milvus/etcd 镜像包 SHA-256：
`8e45bc65faec85077a72a88b8dd7457b6940e55c88b67f259fab9a506edc957e`

### 11.2 已通过项

- `mvn clean package` 完成，后端 Surefire 共 208 个测试，0 failure / 0 error。
- 前端 `npm test` 共 60 个用例通过，`npm run build` 成功；Node 23 只有 `EBADENGINE` 告警。
- `wiki_pages.ft_content` 使用 `WITH PARSER ngram`，正文中间词「主从延迟」可通过 SQL 与页面搜索命中。
- Milvus collection 自动重建，字段包含 1024 维 `vector`、启用 `chinese` analyzer 的 `text`、BM25 `sparse` 及对应索引。
- 精确 token `innodb_buffer_pool_size` 与语义查询「MySQL 缓存应该分配多少物理内存」均将 XLSX 参数台账排在第一位。
- DOCX、XLSX、转换后的 `.doc`、7 页真实中文 PDF 分别生成 19、1、4、34 个切片，共写入 58 条向量。
- XLSX 同一行的参数名、值、场景与命令保持在同一 Markdown 表格行；DOCX/PDF 检索结果带非空 `sectionPath`。
- 非管理员账号可跨岗位检索 ACTIVE 内容并查看文档；检索不到 DRAFT；发布、批量改分类和新建页面均返回 403。
- 图谱、统计、体检 API 均返回 200；初次验证时参数精确查询因 `standard_parameters` 空表返回空数组，后续评分前已录入 5 条测试参数。

### 11.3 实测问题

#### KBV-001：较长 PDF 会因 embedding 上下文超限导致整份上传失败

- 样本：32 页 `TongWeb-V7-quick-start.pdf`，上传接口返回 HTTP 400。
- 日志：`the input length exceeds the context length`，错误来自本地 `bge-large` embedding 接口。
- 对照：7 页真实中文 PDF 能成功生成 34 个切片，说明不是 PDF 路径整体不可用。
- 影响：切片器产出的单片长度没有按 embedding 模型上下文限制兜底，较长或结构特殊的文档无法入库。
- 建议：embedding 前按模型 token 上限二次切分，并增加超长 PDF 集成测试；错误响应应返回稳定业务错误码。

#### KBV-002：上传失败后仍残留文件和 `wiki_sources` 记录

- KBV-001 返回 400 后，`storage/knowledge/` 中保留了原文件，`wiki_sources` 中也保留了 `TongWeb-V7-quick-start.pdf`。
- 文档列表显示该文档有 42 个预览切片，但 Milvus 中没有对应向量；用户会看到一个实际无法检索的文档。
- 影响：数据库、文件存储与向量库状态不一致，重复失败会持续积累悬空文件和来源记录。
- 建议：上传流程在 embedding/向量写入成功后再提交来源记录，或失败时补偿删除文件、来源记录和已写入向量。

#### KBV-003：标准索引对账在相同正文的不同标准之间不幂等

- 两次连续调用 `/api/knowledge/sync-standards` 都返回 `{"indexed":1,"skipped":1,"removed":0,"failed":0}`，没有达到第二次全部 `skipped`。
- 当前两条已发布标准标题不同，但正文均为 `# 参数标准`，内容哈希相同。
- `KnowledgeService.upsertSource` 会按内容哈希复用 `wiki_sources`，两条标准在同一条来源记录上反复改写标题，导致下一轮总有一条被判定为未索引。
- 标题-only 正文还会生成 0 个切片，但同步报告仍计为 `indexed`，因此当前标准实际不可检索。
- 影响：每次启动或手动对账都会重复处理，浪费 embedding 调用；报告成功但索引中无可检索内容。
- 建议：标准来源使用稳定的标准 ID 作为唯一键，不应跨标准按内容哈希合并；0 切片应计为 skipped/failed 并给出明确原因。

#### KBV-004：前端登录表单返回 Forbidden，阻断 UI 验证

- `sysadmin/admin123` 通过 `8080` 网关直接登录为 200，经 `5173` Vite 代理用同一 Basic 请求登录也为 200。
- 在浏览器登录页输入相同账号密码并提交，页面稳定提示 `Forbidden`，无法进入知识库。
- 影响：无法完成「上传文档」文件选择器、「新建页面 / 从文档起草」、图谱画布和「运行体检」的人工交互验证。
- 建议：抓取浏览器实际请求的 Authorization 值并与 CLI 请求对比，重点检查 CryptoJS SHA-256、Basic header 以及浏览器侧请求是否被覆盖。

#### KBV-005：无相关上下文时生成结果存在幻觉风险

- `GS-002 ORA-01555` 的 Top-5 全部无关，但回答仍生成详细诊断和处理步骤，忠实度只有 `0.10`。
- `GS-003 MySQL 主从延迟` 和 `GS-010 Nginx/F5 健康检查` 的忠实度分别为 `0.35`、`0.18`，均包含上下文外技术结论。
- 另一些缺语料问题能明确拒答，忠实度接近 `1.0`，但答案相关性只有 `0.25~0.35`，当前行为不一致。
- 影响：语料缺失或召回失败时，用户可能收到看似专业但无法由知识库证实的运维建议。
- 建议：增加最低召回分数与有效上下文门禁；低于阈值强制拒答；生成提示词要求逐条引用来源，并在输出后校验事实是否有上下文支撑。

### 11.4 质量评分

评分时间为 2026-07-30。检索层运行：

```bash
python3 scripts/eval-retrieval.py --k 5 --json .scratch/eval-current-20260730.json
```

当前 Golden Set 只有 10 条种子模板，以下结果用于暴露链路和语料问题，**不构成正式质量结论**。正式基线仍需补充到 50~100 条真实工单/群聊问题。

#### 11.4.1 检索层

总体 `Recall@5 = 10.0%`，`MRR = 0.100`，10 条请求全部正常返回 Top-5，但只有 `GS-001 innodb_buffer_pool_size` 在第 1 位命中。

| 查询类型 | Recall@5 | MRR | 命中 |
|---|---:|---:|---:|
| `exact_param` | 20.0% | 0.200 | 1/5 |
| `error_code` | 0.0% | 0.000 | 0/1 |
| `command` | 0.0% | 0.000 | 0/1 |
| `semantic` | 0.0% | 0.000 | 0/2 |
| `cross_doc` | 0.0% | 0.000 | 0/1 |

| 来源格式 | Recall@5 | MRR | 命中 |
|---|---:|---:|---:|
| `xlsx` | 50.0% | 0.500 | 1/2 |
| `pdf` | 0.0% | 0.000 | 0/3 |
| `docx` | 0.0% | 0.000 | 0/1 |
| `standards` | 0.0% | 0.000 | 0/2 |
| `wiki` | 0.0% | 0.000 | 0/1 |
| `md` | 0.0% | 0.000 | 0/1 |

低分首先反映当前语料与种子集不匹配：库内没有 Oracle、Redis、Kafka、TongWeb、Nginx/F5 等期望内容。它不能单独证明混合排序算法失效，但可以确定当前知识库无法覆盖这些问题。

#### 11.4.2 生成层

生成答案由项目 `/api/agent/chat` 产生，再使用 `gpt-5.6-terra` 做 LLM-as-judge。每条评分都携带问题、Top-5 上下文、生成答案和 Golden Set 的 `expected` 证据。种子集没有标准答案，因此 `Context Recall` 只能按 `expected` 关键词/章节覆盖程度计算代理值。

| 指标 | 平均分 | 手册门槛 | 结果 |
|---|---:|---:|---|
| Faithfulness | 0.663 | >= 0.75 | 未达标 |
| Answer Relevancy | 0.482 | >= 0.80 | 未达标 |
| Context Precision | 0.103 | >= 0.70 | 严重不足 |
| Context Recall（代理） | 0.175 | >= 0.80 | 严重不足 |

| 用例 | 忠实度 | 答案相关性 | 上下文精确率 | 上下文召回代理值 |
|---|---:|---:|---:|---:|
| GS-001 | 0.78 | 0.95 | 0.55 | 1.00 |
| GS-002 | 0.10 | 0.90 | 0.00 | 0.00 |
| GS-003 | 0.35 | 0.90 | 0.32 | 0.75 |
| GS-004 | 1.00 | 0.25 | 0.00 | 0.00 |
| GS-005 | 0.98 | 0.25 | 0.00 | 0.00 |
| GS-006 | 0.30 | 0.30 | 0.00 | 0.00 |
| GS-007 | 0.98 | 0.25 | 0.00 | 0.00 |
| GS-008 | 0.98 | 0.25 | 0.08 | 0.00 |
| GS-009 | 0.98 | 0.35 | 0.08 | 0.00 |
| GS-010 | 0.18 | 0.42 | 0.00 | 0.00 |

四项简单平均为 `0.356`，仅用于直观参考；手册明确各层不能混合归因，因此不把它作为正式综合分。

#### 11.4.3 语料健康与线上信号

| 指标 | 当前结果 |
|---|---|
| 覆盖率矩阵 | nginx、MySQL 两个参数格子 / 约 80 个目标格子，约 2.5% |
| 参数结构化率 | 有 5 条 ACTIVE 测试参数，但系统没有“应录入参数总数”，无法计算正式比例 |
| 时效性 | 未建立软件版本与标准版本对账，无法评分 |
| 参数矛盾 | 当前未发现，但样本过少且检测能力未闭环，无法评分 |
| 切片健康度 | 已验证部分样本结构，未建立全量超短切片、截断表格、空 `sectionPath` 统计，无法评分 |
| Wiki 健康 | 2 页中 1 页 ACTIVE；7 个来源全部未编译；ACTIVE 页面存在 `ORPHAN` 和 `GAP` 两个 MEDIUM 问题 |
| 线上信号 | 无结果率、重问率、点踩率均未采集 |

生成评分经用户明确授权后使用 Codex 配置。密钥只通过进程环境注入，未写入仓库或结果文件。该 Codex 网关使用 HTTP 而非 HTTPS，传输不具备 TLS 保护，只用于本地测试，不应发送生产敏感语料。

### 11.5 未执行项

- 未验证撤下标准后的 `removed` 场景：为避免修改现有发布数据，没有临时撤下一条标准。
- 已在 11.8 扩充并执行 50 条预发布检索基线；评测语料仍来自本轮验证文档，不代表生产工单分布。
- 未验证「从文档起草」；RAG 对话与 LLM-as-judge 已通过 Codex 配置完成。
- 未在 Milvus 2.5.10 精确版本复测：Docker Hub 拉取超时，改用本机已有且高于最低要求的 2.6.13。

### 11.6 针对 11.4.3「无法评分」项的补充工具

首次实测时语料健康层 7 项有 5 项标注「无法评分」——不是数据不好，是没有工具去算。
已补上可确定性计算的部分：

| 原「无法评分」项 | 现在怎么看 |
|---|---|
| 覆盖率矩阵 | `GET /api/knowledge/corpus-health` 返回 `coverage`、`coveredCells/totalCells`，以及 **`missingCells` 空缺清单——可直接当内容待办** |
| 参数矛盾 | 同上接口的 `parameterConflicts`：同一软件下同名参数在不同已发布标准取值不一致时列出。这类问题最危险，两份标准都生效，按哪个做都能找到依据 |
| 未索引来源 | `unindexedSources`：文档列表里看得到、检索却命中不了的那些 |
| 参数结构化率 | `totalParameters` 给出实际条数；「应录入总数」需要业务先定义目标清单，工具不臆造分母 |
| 时效性 | 仍未建立软件版本与标准版本的对账，无法评分 |

前端「健康度」标签已接入，无需调接口即可查看。

### 11.7 问题优化与回归结果

本轮针对 11.3 的实测问题做了代码级优化。11.4 的 10 条种子评分是修复前基线，未用新逻辑覆盖；检索语料没有因此增加，不能把防幻觉门禁的改动误记为 Recall 提升。

| 问题 | 优化 | 回归结果 | 状态 |
|---|---|---|---|
| KBV-001 | 文档默认切片由 900/150 字符调整为 300/50；embedding 增加可配置的 300 字符输入上限与非法配置校验 | 32 页 `TongWeb-V7-quick-start.pdf` 由 HTTP 400 变为 HTTP 200，生成 134 个切片 | 已解决 |
| KBV-002 | 解析、切片、embedding 成功后才写来源；失败补偿清理新文件、来源和向量；同名更新成功后再删除旧文件 | 同名 PDF 更新前后文件数均为 10，旧引用文件已删除，新来源为 134 个切片；失败路径有 `TC-KNOWLEDGE-IMPORT-001/002/004/006/007` | 已解决 |
| KBV-003 | 标准来源不再按内容哈希跨标题复用；零切片标准清理历史来源并计为 skipped | 两次连续同步均为 `indexed=0, skipped=2, removed=0, failed=0`，历史 `STANDARD_DOC` 来源清空 | 已解决 |
| KBV-004 | 登录认证与登录后数据加载拆开；管理数据并行加载且单项失败不再伪装成登录失败；默认 CORS 同时允许 `localhost` 与 `127.0.0.1` | `TC-AUTH-001`、`TC-WEB-004/005` 通过；带两种本地 Origin 的请求不再返回 CORS 403；11.9 已在远端真实浏览器登录并进入知识库 | 已解决 |
| KBV-005 | 新增技术 token 与中文短语覆盖门禁；无可靠证据时直接稳定拒答，不调用生成模型 | `ORA-01555` 无关 Top-5 场景返回固定拒答、空引用且不调用模型；`TC-RAG-001~006` 通过 | 已解决 |
| KBV-006 | 代码审查发现同名更新会先删旧向量，Milvus 中途失败时数据库虽回滚但旧文档已不可检索；改为单次批量 upsert，成功后只清理旧余量 | `TC-KNOWLEDGE-IMPORT-007` 先红后绿；内存实现补齐同等来源清理语义 `TC-VECTOR-001/002` | 已解决 |

最终自动化回归：后端 26 模块共 229 个测试，0 failure / 0 error / 0 skipped；前端 61 个测试全部通过，`npm run build` 成功。构建仍有既存的 `vendor-pdf` 超过 900 kB 告警，本轮未改 PDF 前端依赖加载方式。

当时的运行态复测边界：为加载 CORS 改动已停止旧 core-service；新进程因当前终端没有 `APP_DB_PASSWORD`，启动期连接本地 MySQL 被拒绝。运行环境不允许从其他进程提取凭据，因此没有绕过限制。该项已在 11.9 的远端环境完成真实浏览器验收。

### 11.8 远端 bge-m3 复测（2026-07-31）

本轮在 `192.168.126.1:/app/infra-portal` 复测。应用镜像为 `0d0ea0a-bgem3`，前端、网关、8 个业务服务、MySQL、Nacos、Milvus、MinIO、etcd 均为 healthy。Milvus 为 `2.6.13`；embedding 由本机 Ollama 提供，经 SSH 反向通道供远端调用，模型为 `bge-m3`、向量维度 1024、模型上限 8192 token。切片实用预算按模型上限推导为 1600 字符。

升级前备份位于远端 `/app/infra-portal/backups/pre-bgem3-20260730T225151`，包含 MySQL dump、配置和旧 Milvus 数据树。

#### 11.8.1 语料与结构验证

| 文档 | 格式 | 页数/结构 | 切片数 | 结果 |
|---|---|---:|---:|---|
| `mysql-parameter-ledger.xlsx` | XLSX | 10 行参数表 | 1 | 表头和 10 行完整保留在同一切片 |
| `dataworks-recovery-plan.docx` | DOCX | 标题层级、表格 | 19 | 上传、分段、检索通过 |
| `dataworks-recovery-plan-legacy.doc` | DOC | 历史 Word | 4 | 上传成功；仍有 POI PAPX 修复 WARN |
| `dataworks-recovery-plan.pdf` | PDF | 7 页 | 34 | 结构化导入通过 |
| `TongWeb-V7-quick-start.pdf` | PDF | 32 页 | 30 | 长 PDF 通过，无 embedding 上下文超限 |
| `100_TongWeb_V7.0-cluster-guide.pdf` | PDF | 158 页 | 144 | 长 PDF 通过，无 embedding 上下文超限 |
| **合计** | 4 种格式 | 6 份文档 | **232** | 全部导入成功 |

`innodb_buffer_pool_size` 精确查询将 XLSX 排在第 1；`主从延迟` 也将 XLSX 排在第 1，但其余 4 条结果均为无关 TongWeb 内容，说明首条精确命中有效、长尾精确率仍有优化空间。TongWeb 集群状态、JDBC 资源池等自然语言查询能召回对应手册章节，结果的 `sectionPath` 非空。

#### 11.8.2 50 条检索基线

评测集为 50 条人工筛选并改写的实际文档绑定问题，覆盖 22 条精确参数题、3 条命令题、25 条语义题。它已达到手册的最小样本数，但来源仍是本轮验证文档，不等同于生产工单分布，因此作为预发布基线使用。

执行命令：

```bash
python3 scripts/eval-retrieval.py --k 5 --json .scratch/baseline-bgem3.json
```

总体 `Recall@5 = 94.0%`，`MRR = 0.751`，50 条请求均正常返回。

| 查询类型 | Recall@5 | MRR | 命中 |
|---|---:|---:|---:|
| `command` | 100.0% | 0.389 | 3/3 |
| `exact_param` | 95.5% | 0.744 | 21/22 |
| `semantic` | 92.0% | 0.801 | 23/25 |

| 来源格式 | Recall@5 | MRR | 命中 |
|---|---:|---:|---:|
| `pdf` | 97.1% | 0.820 | 34/35 |
| `xlsx` | 100.0% | 0.653 | 10/10 |
| `docx` | 60.0% | 0.467 | 3/5 |

3 条未命中：

- `BGEM3-012`：节点代理后台运行参数 `Dnodeagent.background` 未进入 Top-8，属于真实漏召回。
- `BGEM3-048`：目标 DOCX 章节在第 8 位，未达到 Top-5。
- `BGEM3-050`：相同内容的 legacy DOC 在第 2 位，但评测绑定的 DOCX 章节未进入 Top-5；这是跨格式重复语料下的格式定向失败。

#### 11.8.3 配置、数据库与生成层边界

| 编号 | 问题与处理 | 回归 | 状态 |
|---|---|---|---|
| KBV-007 | Nacos 初始化器原先对已存在 Data ID 一律跳过，导致配置更新不下发；改为内容相同才跳过、内容变化则更新 | `TC-DOCKER-032`，远端仅更新变化配置 | 已解决 |
| KBV-008 | `application.yml` 仍使用废弃的 `EMBEDDING_MAX_CHARS=300`，覆盖了 Nacos 的 8192 token 配置；改为 `EMBEDDING_MAX_TOKENS=512` 兜底 | `TC-CI-014`；XLSX 从 3 个切片恢复为 1 个，长 PDF 通过 | 已解决 |
| KBV-009 | AI 启动时仍出现 `Nacos Config ... ai-service.properties ... is empty`，监听稍后才建立；构造期配置不能只依赖 Nacos | 环境变量可确保本次 embedding/生成模型配置正确，但启动顺序问题仍在 | 待解决 |
| KBV-010 | Docker 新库挂载的 `db/init.sql` 缺少 `api_audit_log`，API 审计写入持续失败；远端执行幂等 DDL，并把表加入初始化脚本 | 表内已成功写入 2 条记录，主键及 3 个业务索引齐全；`TC-DOCKER-033` | 已解决 |
| KBV-011 | bge-m3 基线仍有 1 条 PDF 技术参数漏召回、2 条 DOCX 格式定向未命中，且精确命中后的 Top-2~5 存在无关结果 | 记录 `BGEM3-012/048/050` 及实际 Top-8，用作下一轮排序和切片优化基线 | 待优化 |

生成模型已按授权配置为 Codex 网关和 `gpt-5.6-terra`，密钥未写入仓库。由于运行态 RAG 会把检索到的内部知识片段发送到该外部网关，本轮自动安全审批要求额外的显式数据外发授权；因此没有重复执行生成答案和 LLM-as-judge，不能用 11.4.2 的旧分数代表 bge-m3 本轮结果。无证据门禁与出口技术标识校验已有 `TC-RAG-001~006` 自动化回归，但本轮远端生成层仍标记为未完成。

### 11.9 LinkResolver 与语料健康度跟进验证（2026-07-31）

本轮将包含 `44d378e` 修复的最新源码同步到 `192.168.126.1:/app/infra-portal`，AI 服务和前端重建为 `ed8f077-bgem3` 并健康运行。AI 服务镜像构建执行 160 个测试，0 failure / 0 error；本地针对 LinkResolver、语料健康度和页面写入的 38 个后端测试以及 61 个前端测试全部通过。

#### 11.9.1 LinkResolver 运行态回归

使用 4 个带唯一前缀的临时草稿页覆盖创建、更新和断链场景，测试结束后页面及关联边均已清理。

| 场景 | 期望 | 实际 | 状态 |
|---|---|---|---|
| 创建页面，正文引用 `[[目标 A]]` | 保存页面并建立 A 引用边 | A 引用边存在 | 通过 |
| 更新同一页面，正文改为只引用 `[[目标 B]]` | 建立 B 引用边并删除 A 旧边 | B 边已建立，但 A 旧边仍保留 | **失败** |
| 创建页面，正文引用不存在的标题 | 页面保存成功，不建立引用边 | 页面保存成功，边数为 0 | 通过 |
| 临时数据清理 | 页面和关联边均无残留 | 页面 0 条、关联边 0 条 | 通过 |

`KBV-012`：`resolveLinks` 当前只执行幂等插入，没有在页面更新前删除该页已有的出边。页面反复编辑后会累积过期引用，影响图谱、图扩展检索和孤儿页判断。服务日志与接口结果一致：创建和更新各解析出 1 条边，断链页面解析 0 条；本轮没有 WARN / ERROR。

#### 11.9.2 `corpus-health` 确定性验证

先用 3 份临时已发布标准和 2 条同名异值参数构造固定夹具，再调用接口并自动清理数据库记录。

| 指标 | 期望 | 实际 | 状态 |
|---|---:|---:|---|
| `coveredCells` | 3 | 3 | 通过 |
| `totalCells` | 8 | 8 | 通过 |
| `coverage` | 37.5% | 37.5% | 通过 |
| `missingCells` | 5 项 | 5 项，内容与预期一致 | 通过 |
| `parameterConflicts` | 检出 `100` / `200` 冲突 | 已检出 | 通过 |
| 清理后临时标准 / 参数 | 0 / 0 | 0 / 0 | 通过 |

真实语料状态为 6 份文档、232 个可检索切片，50 条基线 `Recall@5 = 94.0%`；但接口返回 `unindexedSources = 6`，前端也将这 6 份文档全部展示为“待索引文档”。

- `KBV-013`：`unindexedSources` 使用 `wiki_sources.ingested` 判断，该字段表示 Wiki 编译状态，不表示向量索引是否存在，因此会把已可检索的上传文档误报为未索引。
- `KBV-014`：当前覆盖率分母只从已发布参数标准里出现的软件推导。真实库没有已发布参数标准时返回 `0 / 0` 和空 `missingCells`，无法提示“某软件整套标准尚未录入”的空白；需要由业务目标软件清单提供独立分母。

#### 11.9.3 前端与检索回归

在 `http://192.168.126.1:15173` 使用真实浏览器完成登录，进入“知识库 -> 健康度”，接口数据、覆盖率、参数数、文档数和未索引清单均正常渲染；“运行体检”可执行，浏览器控制台无 WARN / ERROR，页面未发现遮挡或溢出。

部署后再次执行 50 条检索基线，结果仍为 `Recall@5 = 94.0%`、`MRR = 0.751`，三个未命中用例仍是 `BGEM3-012/048/050`，与 11.8 基线一致，无检索回退。

### 11.10 KBV-012 / 013 / 014 修复（2026-07-31）

严格 TDD：三项均先补失败测试再修实现。测试夹具一律使用唯一 `runId` 前缀构造，
不复用既有 6 份问题语料，`tearDown` 清空并校验无残留。

| 问题 | 根因 | 修复 |
|---|---|---|
| KBV-012 | `resolveLinks` 只做幂等插入，页面正文把 `[[A]]` 改成 `[[B]]` 后 A 的旧边一直保留 | 写入前先删本页出边再按当前正文重建。**新增 `deleteOutgoingReferences` 而非复用 `deleteByPageId`**——后者删的是双向边，会连带删掉别的页面指向本页的入边，那些边归属对方页面 |
| KBV-013 | `unindexedSources` 用 `wiki_sources.ingested` 判定，该字段表示 Wiki 编译状态；上传类文档从不参与编译、恒为 `false`，导致 6 份已可检索文档全部被误报 | 改以**向量是否存在**为准，新增 `VectorStore.countBySource`。向量库不可用时降级为「不下结论」并置 `indexStatusReliable=false`，不输出清单——宁可不报，也不能把「查不到」误读成「没索引」 |
| KBV-014 | 覆盖率分母只从已发布标准反推，整套语料空白时返回 `0/0` 和空 `missingCells`，反而看不出问题 | 分母改为**业务目标清单 ∪ 已录入标准的软件**。清单由 `app.corpus.target-catalog`（`分类:软件`）配置，代码不臆造业务数据；未配置时显式给出 `coverageHint` 说明分母局限，不用 `0/0` 掩盖 |

新增接口字段：`indexedChunks`、`indexStatusReliable`、`targetCatalogConfigured`、`coverageHint`，
前端「健康度」标签已同步展示；索引状态不可信时不渲染未索引清单。

**同时更正了一条既有用例**：`TC-HEALTH-004` 原本断言「`ingested=false` 即未索引」，
那正是 KBV-013 的错误口径，已改为按向量判定。

#### 验收用例

| TC | 场景 | 结果 |
|---|---|---|
| TC-LINK-004 | 改换引用目标后删除失效旧出边 | 通过 |
| TC-LINK-005 | 清理只针对出边，不波及入边 | 通过 |
| TC-LINK-006 | 清空全部引用后出边全删且不新增 | 通过 |
| TC-LINK-007 | 未落库页面不触发清理 | 通过 |
| TC-LINK-008 | 夹具 runId 隔离校验 | 通过 |
| TC-HEALTH-006 | 有向量的文档不被误报未索引 | 通过 |
| TC-HEALTH-007 | 确无向量的文档被列出 | 通过 |
| TC-HEALTH-008 | 向量库故障时降级不下结论 | 通过 |
| TC-HEALTH-009 | 目标清单驱动分母，空白软件计入 | 通过 |
| TC-HEALTH-010 | 已发布标准正确落格 | 通过 |
| TC-HEALTH-011 | 未配置清单时显式标注 | 通过 |
| TC-HEALTH-012 | 清单外已录标准的软件也计入 | 通过 |

后端全量 254 个用例、前端 61 个用例、`npm run build` 均通过。

---

## 十二、回滚

本分支未合并 master，回滚即切回 master 分支。

数据侧：四个 DDL 中 `reset_wiki_pages.sql` 和 `drop_knowledge_chunks.sql` 是**破坏性**的，回滚需要从备份恢复。Milvus collection 重建后旧向量不可恢复，但可通过重新导入文档再生。

---

## 反馈方式

验证中发现问题，请带上：
1. 后端日志中的 WARN / ERROR 原文（尤其含「结构化解析失败」「metadata 超出」「Milvus」字样的）
2. 出问题的查询语句与期望命中的文档
3. `scripts/eval-retrieval.py --json` 的输出文件

有这三样才能定位是解析、切片、分词还是融合排序的问题。
