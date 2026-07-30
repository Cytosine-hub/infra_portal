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
| `standard_parameters` 空表 | 参数查询接口返回空是正常的，等录入 |
| `wiki_pages` 被清空 | 经验沉淀标签为空、健康度无数据、检索无经验类结果，都是预期 |
| 智能排查 Agent | **未重构**，仍是 YAML Skill 编排，`query_metrics` / `search_logs` 是空桩 |
| `.doc` 老版 Word 解析 | 用真实 DOCX 转换出的 OLE2 `.doc` 上传成功并生成 4 个切片，但 POI 输出大量 PAPX 修复 WARN；仍需补一份原生历史 `.doc` 样本 |
| 中文 PDF 解析 | 7 页真实中文 PDF 上传成功并生成 34 个切片；32 页 TongWeb 手册因 embedding 上下文超限失败，见 KBV-001/KBV-002 |
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
- 图谱、统计、体检 API 均返回 200；参数精确查询因 `standard_parameters` 空表返回空数组，符合已知状态。

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

### 11.4 未执行项

- 未验证撤下标准后的 `removed` 场景：当前标准同步幂等性已失败，且现有标准只有标题，无可验证的检索内容。
- 未运行质量基线：第八步 UI 验证未通过，按本文规则不继续执行；当前 golden set 也只有 10 条种子数据。
- 未验证「从文档起草」和 RAG 对话：本次环境未配置 `AI_API_KEY`，检索不受影响。
- 未在 Milvus 2.5.10 精确版本复测：Docker Hub 拉取超时，改用本机已有且高于最低要求的 2.6.13。

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
