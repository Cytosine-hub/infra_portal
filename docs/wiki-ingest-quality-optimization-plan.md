# Wiki 文档编译质量优化方案

## 结论

当前 Wiki 编译质量问题的核心是现有编译链路缺少“文档类型识别 + 目录/章节级覆盖控制”。长文档被拆成多个 chunk 后独立交给 LLM，每个 chunk 只会生成它认为显眼的实体或概念页面，系统没有先判断文档是什么类型、应该按什么结构拆 section、整篇文档应该产出哪些页面，也没有在保存前检查章节覆盖率。

优化主线应改为通用的“目录驱动编译流程”：

1. 先抽取全文目录和章节结构。
2. 再生成 `page_plan`，明确每个计划页面覆盖哪些章节。
3. 按 `page_plan` 生成页面，而不是让每个 chunk 自由生成。
4. 保存页面时写入章节级 `source_refs`。
5. 通过质量门禁决定任务是否成功、部分成功或失败。

不同文档类型只影响 section 拆分、required section 判定和 page_plan 规划偏好，不应复制多套编译流程。安装指南、配置手册、监控手册、排障手册、产品说明、标准规范都走同一条主链路。

向量存储按中间件、数据库等分类拆 collection 可能减少跨领域召回噪声，但不是当前编译质量的主矛盾。更推荐先做“单 collection + category/software/source_type 标量过滤”，必要时再升级为 Milvus partition 或多 collection。

## 实施进度

更新时间：2026-06-12

总体进度：P0 主链路、P1 向量过滤和软件类型社区、P2 质量可视化均已完成第一版；当前进入 P0 性能与可观测性修正阶段。

标记说明：`[x]` 已完成；需要真实外部环境或固定样例的内容单独列为验收项。

已完成：

- 新增 `DocumentTypeClassifier`，支持产品概览、安装、配置、监控、排障、标准规范、版本说明等文档类型的启发式识别。
- 新增 `DocumentOutlineExtractor`，已支持 Markdown ATX/Setext 标题、PDF 书签/目录页/编号标题、Word `.docx` Heading 样式、中文章/节标题、短标题弱识别，并输出 `section_id`、`section_path`、`char_range`、`paragraph_range`、`page_range`、`source_signal`、`required`、`section_type`、`blocks`、`confidence`。
- 新增章节事实、页面计划、按计划生成页面三段 Prompt：`buildSectionFactsPrompt`、`buildPagePlanPrompt`、`buildPlannedPageGenerationPrompt`。
- `section_facts` 已从“整篇文档一次性 LLM 调用”改为“按 section 批次调用 LLM 后合并”，避免大文档在事实抽取阶段触发 `completion_tokens=4096` 输出截断。
- 页面生成已从“整个 page_plan 一次性生成所有页面”改为“按 page_plan 批次生成页面”，每批只携带相关 section outline、section_facts 和计划页面。
- `section_facts`、`page_plan` 和 planned page 生成均已增加解析失败兜底：单批 LLM JSON 截断或格式异常时，不直接让任务失败，而是用目录章节和已抽取事实生成保守 fallback，后续仍交给质量门禁判断 `SUCCESS/PARTIAL/FAILED`。
- `page_plan` 已增加程序级覆盖校验，所有 required section 必须映射到计划页面，否则编译失败。
- `IngestAgent` 新增 `ingestPlanned(...)`，编译链路已改为 `classify -> outline -> section_facts -> page_plan -> planned_pages -> quality_gate -> save -> link/vectorize`。
- `IngestTaskService` 已切换到目录驱动编译，不再按 chunk 循环调用 `ingestContent(...)` 直接落页面。
- 页面保存时已为 planned ingest 写入 `source_refs.sections`，包含 `source_id`、`source_title`、`source_type`、`section_id`、`section_path`、`char_range`、`paragraph_range`、`page_range`、`source_signal`。
- 新增 `WikiIngestQualityGate`，已检查 required section 覆盖率、缺失章节、短页面、重复标题、泛化标题、过度压缩页面、缺少 `source_refs.sections`，并输出 `SUCCESS/PARTIAL/FAILED`。
- 任务成功条件已收紧：质量门禁失败时任务失败；部分通过时任务 `PARTIAL`；`SUCCESS` 才标记 source 已编译。
- 数据库变更已记录在 `db/wiki_ingest_quality_optimization_20260612.md` 和 `db/wiki_ingest_quality_report_20260612.sql`。
- 向量过滤采用单 collection + 标量字段方案，Milvus 发布注意事项已记录在 `db/wiki_vector_filter_milvus_20260612.md`。
- 图谱社区已改为按 `category + software` 稳定聚类，并返回 `community_key`、社区名称、节点数和边数。
- `wiki_pages.canonical_title` 和 `wiki_pages.alias_titles` 已持久化，运行时匹配和跨任务候选检索都可使用。

后续增强：

- PDF 版式字号、双栏、OCR 噪声清洗和 Word 大纲级别可继续增强；当前第一版已接入 PDF 书签/目录文本、全角空格/点线目录、Word `.docx` Heading 样式和通用文本标题信号。
- `section_facts` 和 `page_plan` 当前作为编译阶段产物落在 `IngestAgent` 内，尚未持久化中间产物。
- `source_refs` 已覆盖 planned ingest 主路径；旧兼容方法 `ingest(...)`、`ingestContent(...)` 仍保留历史行为，生产任务入口已不再调用旧分段路径。
- 合并策略已把 LLM 解析失败默认值从 `OVERWRITE` 改为 `APPEND`，并在 APPEND 分支按 Markdown heading block 做章节补丁；复杂冲突仍交给 LLM 和人工审核。
- 质量报告目前已持久化到 `wiki_ingest_tasks.quality_report`，并已在来源详情展示覆盖率、必需章节、缺失章节、短页面、泛化标题、过度压缩页面、来源缺失页、任务详情和完整 JSON 报告。
- 向量检索已新增 `VectorSearchFilter`，InMemory 和 Milvus 均支持同构过滤；Milvus 对旧 collection 会回退到 metadata 过滤。
- 图谱社区已按软件类型聚类，保留关系边权重但不再用 Louvain 作为前端默认社区划分依据。
- 单元测试已覆盖目录抽取、PDF/Word loader 结构信号、质量门禁、page_plan 覆盖校验、标题规范化、向量过滤、图谱社区、PDF 全角空格目录识别，以及 `section_facts` JSON 截断时的 fallback 继续编译；尚缺固定长文档样例的端到端集成测试。

本轮新增修复验证：

- 已完成 `section_facts` 分批抽取、合并和 fallback。
- 已完成 planned page 分批生成和 fallback。
- 已完成 PDF/Word 文本目录中全角空格、全角点线和 `httpd.conf` 这类标题的识别增强。
- 已完成超大 outline 瘦身：大纲过细时保留高层章节、必需章节、有正文内容和高价值结构信号，弱标题叶子不再全部进入后续链路。
- 已完成标题-only section 本地事实生成：无正文、无表格/代码/列表的标题章节不再调用 `section_facts` LLM。
- 已完成 planned ingest 阶段进度回写：目录抽取、章节事实批次、页面计划、页面生成批次、页面校验和质量门禁都会更新任务进度；批次阶段会同步更新 `total_chunks`，避免前端显示旧 chunk 分母。
- 已完成阶段与批次耗时日志：记录 outline section 数、section_facts batch、localOnlySections、page_plan、page_generation batch 的耗时。
- 已通过相关单元测试：`mvn test -Dtest=DocumentOutlineExtractorTest,IngestAgentTest,IngestTaskServiceTest -Dapp.vector.type=memory`，共 19 个测试通过。
- 已通过全量测试：`mvn test -Dapp.vector.type=memory`，共 87 个测试通过。
- 待完成：代码审查、服务重启验证、提交和推送。

本轮长 PDF 暴露的新问题：

- 5.9M PDF 被抽出 468+ 个 section，`section_facts` 阶段运行 70 分钟仍未进入 `page_plan`。
- 很多 section 只有标题，没有正文片段，LLM 最终只是把标题改写成 facts，成本高但信息增益很低。
- 单文档内 `section_facts` 批次串行执行，一个任务通常只使用 1 个 LLM 槽位。
- 后端没有在每个阶段和每个批次回写进度，前端长时间停留在 25%，用户无法判断是卡死还是慢跑。

新增执行策略：

- 大纲过细时不追求“每个标题一个 section”，而是保留高层章节、必需章节、有正文内容的章节和高价值结构信号；弱标题、图题、目录碎片、只有标题的叶子章节应合并或降级，不单独进入 LLM。
- `section_facts` 只让“有正文信息量”的 section 调用 LLM；标题-only section 使用本地 deterministic facts，避免为低价值标题消耗 1-2 分钟。
- 编译阶段必须记录阶段耗时、批次数、已完成批次和当前 section 范围，并同步更新任务进度。
- 单任务内批次并发作为第二步优化，先以大纲瘦身和跳过无效 LLM 为主，避免直接并发放大供应商限流和输出截断问题。

## 现状评估

### 已具备的基础能力

- 文档上传可携带分类和软件信息，并创建异步 Ingest 任务。
- 长文档已走目录驱动 planned ingest，任务可在质量门禁部分通过时进入 `PARTIAL`。
- 页面输出已有基础 JSON 校验，包括页面数组、标题、正文、页面类型和摘要长度。
- `wiki_pages` 已有 `category`、`software`、`version`、`source_refs` 等字段基础。
- 图谱已有 `wiki_links`、同软件、同分类、同源等关联信号，并按软件类型稳定聚类。
- 知识库向量导入 metadata 已包含 `category`、`software`、`sourceType`、`sourceId` 等信息。

### 主要缺口

- 生产任务入口已切到“整篇规划后编译”，但旧 `ingest(...)`、`ingestContent(...)` 兼容路径仍存在，不应再作为任务主入口。
- PDF/Word/Markdown 的第一版章节识别已落地；后续可继续增强 PDF 版式字号、OCR 清洗和 Word 大纲级别。
- Prompt 目标已从“实体/概念生成页面”扩展为“章节事实 -> page_plan -> planned pages”，中间产物目前不单独落库。
- `page_plan` 阶段已落地在 `IngestAgent` 内，并已增加 required section 覆盖校验。
- 质量门禁已检查覆盖率、来源引用、短页面、重复标题、泛化标题和过度压缩页面。
- planned ingest 主路径已写入章节级 `source_refs`，包含字符范围、段落范围、页码范围和结构信号。
- 合并策略已从纯 `title + page_type` 扩展到持久化 `canonical_title` 和 `alias_titles`。
- 任务成功条件已收紧，完整质量报告已持久化；前端来源详情已展示缺失章节、覆盖率和补编入口。
- 向量检索已支持结构化过滤；后续可增加更智能的软件识别、多路过滤召回和 rerank。

## 目标

1. 长文档编译从“抓显眼概念”升级为“按目录完整编译”。
2. 每个 Wiki 页面都能追溯到原文 source、章节、页码或段落范围。
3. 编译任务必须暴露覆盖率、缺失章节和质量问题。
4. 低覆盖率结果不能被静默标记为成功。
5. 安装指南、配置手册、监控手册、排障手册、产品说明等文档类型作为规划策略，而不是独立流程。
6. 检索阶段支持按分类、软件、来源类型过滤，减少向量召回噪声。
7. 图谱社区命名稳定、唯一、可解释。

## 设计原则

- 目录优先：先理解文章结构，再决定页面结构。
- 规划先行：所有长文档必须先产出 `page_plan`，再生成页面。
- 证据绑定：页面内容必须能回溯到 source 和 section。
- 质量可阻断：覆盖率不足时任务不能自动成功。
- 通用主链路：不同文档类型只切换解析和规划策略，不复制编译链路。
- 结构化检索优先：category/software/version/source_type 应作为检索过滤条件，不依赖物理拆库兜底。

## 目标架构

```text
上传文档
  |
  v
内容抽取
  |
  v
DocumentTypeClassifier
  - 识别文档类型
  - 选择拆分策略
  |
  v
DocumentOutlineExtractor
  - 标题
  - 章节层级
  - section_id
  - section_path
  - 页码/段落范围
  - required 标记
  |
  v
Section Fact Extraction
  - 按 section batch 提取事实
  - 合并所有批次 section_facts
  - 单批 JSON 截断时使用 fallback facts
  - 不直接生成页面
  |
  v
Page Planner
  - 根据全文目录和 section_facts 生成 page_plan
  - 明确每个页面覆盖哪些章节
  - page_plan 解析失败时生成保守计划
  |
  v
Page Generator
  - 按 page_plan batch 生成页面
  - 每批只携带相关 outline/facts/plans
  - 单批 JSON 截断时生成 fallback 页面
  - 写入 source_refs 和 coverage
  |
  v
Merge & Link Resolver
  - 章节级合并
  - 解析 wikilink
  |
  v
Quality Gate
  - 覆盖率
  - source_refs 完整性
  - 短页面
  - 重复标题
  - 泛化标题
  |
  v
DRAFT / PARTIAL / FAILED
```

## P0：目录驱动编译主链路

### 1. 新增 DocumentTypeClassifier

`DocumentTypeClassifier` 在目录抽取前运行，负责判断文档的业务类型和结构特征：

```json
{
  "document_type": "CONFIG_GUIDE",
  "confidence": 0.86,
  "format": "PDF",
  "structure_quality": "MEDIUM",
  "signals": [
    "存在目录页",
    "包含大量配置项表格",
    "章节标题使用 1.1/1.2 编号"
  ]
}
```

建议先支持这些文档类型：

| document_type | 典型内容 | section 拆分重点 | page_plan 偏好 |
| --- | --- | --- | --- |
| `PRODUCT_OVERVIEW` | 产品介绍、版本说明、能力说明 | 产品、版本、模块、特性 | ENTITY、CONCEPT |
| `INSTALL_GUIDE` | 环境准备、部署、启动、验证 | 前置条件、步骤、命令、验证 | RUNBOOK、STANDARD |
| `CONFIG_GUIDE` | 配置文件、参数、默认值、示例 | 配置项、表格、代码块、注意事项 | STANDARD、RUNBOOK |
| `MONITORING_GUIDE` | 指标、端点、接入方式 | 指标分组、采集方式、权限 | RUNBOOK、CONCEPT |
| `TROUBLESHOOTING` | 故障现象、日志、根因、修复 | 症状、检查、原因、处理、验证 | EXPERIENCE、RUNBOOK |
| `STANDARD_SPEC` | 规范、基线、检查项 | 条款、等级、适用范围 | STANDARD |
| `RELEASE_NOTE` | 版本变化、缺陷修复、兼容性 | 版本、变更项、影响范围 | CONCEPT、EXPERIENCE |

分类结果只决定拆分和规划策略，不决定是否走另一套编译流程。置信度低时使用通用目录驱动策略。

### 2. 新增 DocumentOutlineExtractor

`DocumentOutlineExtractor` 负责生成统一的 `document_outline`：

```json
{
  "document_type": "CONFIG_GUIDE",
  "format": "PDF",
  "title": "BES V9.5.5 配置手册",
  "category": "中间件",
  "software": "BES",
  "version": "V9.5.5",
  "sections": [
    {
      "id": "sec-001",
      "path": "配置文件说明",
      "level": 1,
      "order": 1,
      "page_range": "3-5",
      "char_range": [1200, 5600],
      "required": true,
      "section_type": "REFERENCE"
    },
    {
      "id": "sec-002",
      "path": "连接池配置/最大连接数",
      "level": 2,
      "order": 2,
      "page_range": "6-9",
      "char_range": [5601, 11200],
      "required": true,
      "section_type": "CONFIG_ITEM"
    }
  ]
}
```

结构来源优先级：

1. PDF 书签或目录。
2. 文本中的目录页。
3. Markdown 标题。
4. 编号标题，如 `1.1`、`第 1 章`、`一、`、`（一）`。
5. 视觉/版式线索，如短行标题、前后空行、编号连续性。
6. LLM 目录补全，只作为兜底，并必须保留置信度。

### 3. Word、PDF、Markdown 的 section 拆分方法

不同文件格式的结构信号不同，但最终都要归一成同一个 `document_outline.sections`。

#### Markdown 拆分

Markdown 结构最清晰，应优先使用语法树或 CommonMark 解析，不建议只用正则硬切。

拆分规则：

1. 以 ATX 标题 `#` 到 `######` 和 Setext 标题作为一级结构信号。
2. 标题下连续内容归属当前 section，直到遇到同级或更高级标题。
3. 保留代码块、表格、列表、引用块的完整性，不能把一个代码块或表格切到两个 section。
4. YAML front matter 作为文档元数据，不作为正文 section。
5. 如果 Markdown 没有标题，则用编号段落、粗体短行、列表层级做弱标题识别。
6. `section_path` 由标题栈生成，例如 `部署/启动服务/验证状态`。

Markdown section 输出建议包含：

```json
{
  "id": "sec-010",
  "path": "配置/连接池参数",
  "level": 2,
  "heading_text": "连接池参数",
  "char_range": [3200, 5100],
  "blocks": ["paragraph", "table", "code"],
  "required": true
}
```

#### Word 拆分

Word 文档应优先读取结构化样式，而不是只读取纯文本。

拆分规则：

1. 优先使用段落样式：`Heading 1/2/3`、`标题 1/2/3`、大纲级别。
2. 读取 Word 内置目录字段或超链接目录，提取标题层级和页码参考。
3. 识别编号标题：`1`、`1.1`、`1.1.1`、`第 1 章`、`一、`、`（一）`。
4. 表格不要按单元格拆散，应作为当前 section 的结构块；配置项表格可进一步抽取为 `CONFIG_ITEM` 子 section。
5. 图片、流程图、截图保留占位信息，记录附近标题、图片说明和段落范围。
6. 页眉页脚通常不进正文，但其中的软件名、版本号可作为元数据候选。
7. 如果标题样式混乱，用字号、加粗、居中、编号连续性和前后空行做弱标题判定。

Word section 输出建议包含：

```json
{
  "id": "sec-021",
  "path": "参数说明/线程池配置",
  "level": 2,
  "style": "Heading 2",
  "paragraph_range": [88, 105],
  "page_range": "12-13",
  "blocks": ["paragraph", "table"],
  "required": true
}
```

#### PDF 拆分

PDF 最不稳定，应把“目录/书签/版式/文本编号”组合使用，避免只按空行或固定长度切。

拆分规则：

1. 优先读取 PDF outline/bookmarks，作为章节层级主来源。
2. 如果没有书签，先识别目录页：匹配标题、点线、页码，例如 `2.3 参数配置 .... 15`。
3. 用目录项回填正文页码范围，建立 `section -> page_range`。
4. 对正文页按版式识别标题：字号较大、加粗、短行、编号连续、上下间距明显。
5. 识别页眉页脚和页码，避免把重复页眉页脚当成正文。
6. 表格区域尽量整体保留；参数表、端口表、兼容性表可抽取为子 section。
7. 对双栏、页内换行、断词、OCR 噪声做文本清洗，再做标题判断。
8. 如果目录项找不到正文锚点，保留目录 section，并标记 `confidence=LOW`，交给 LLM 补全和质量门禁处理。

PDF section 输出建议包含：

```json
{
  "id": "sec-034",
  "path": "监控指标/JMX 指标",
  "level": 2,
  "page_range": "28-31",
  "source_signal": "toc+layout",
  "confidence": 0.78,
  "blocks": ["paragraph", "table"],
  "required": true
}
```

#### section 归一化规则

所有格式最终统一成同一个 section 模型：

- `id`：稳定 section id，可由 sourceId + heading path + order hash 生成。
- `path`：标题层级路径。
- `level`：章节层级。
- `order`：文档内顺序。
- `page_range`：PDF/Word 优先提供，Markdown 可为空。
- `char_range` 或 `paragraph_range`：用于回溯原文。
- `blocks`：paragraph/table/list/code/image 等结构块。
- `section_type`：由文档类型和内容识别得到，如 `PROCEDURE`、`CONFIG_ITEM`、`METRIC`、`TROUBLESHOOTING_STEP`。
- `required`：是否必须进入 page_plan。
- `confidence`：结构识别置信度。

`required` 判定不要只看标题层级。不同文档类型应有不同规则：配置手册中的参数表、排障手册中的处理步骤、监控手册中的指标表、标准规范中的强制条款，即使层级较低，也应标为 required。

### 4. 长文档改为分批 Section Facts

长文档分段不再直接生成 Wiki 页面，也不再把整篇文档一次性丢给 LLM 抽取 `section_facts`。正确流程是先由 `DocumentOutlineExtractor` 得到全文 `document_outline.sections`，再按 section batch 分批抽取事实，最后合并为整体 `section_facts`。

原因：

- 大文档的 outline 和章节摘录可能非常长，一次性抽取会让 `prompt_tokens` 接近模型上下文上限。
- `section_facts` 输出条目数随章节数线性增长，一次性输出很容易打满 `AI_MAX_TOKENS`，出现 `completion_tokens=4096` 后 JSON 被截断。
- 分批抽取可以把失败范围限制在单批 section，配合 fallback 后不会让整篇文档直接失败。

批次策略：

- 每批按 section 数量和字符数双阈值切分。
- 每批输入只包含当前 batch 的 compact outline，不携带整篇全文。
- 每批输出只允许包含输入 batch 内的 `section_id`。
- 合并时按 `section_id` 去重，并补齐缺失 section。
- 单批 JSON 解析失败时，根据 section path、excerpt、section_type 生成 fallback facts。

每个 section 或 section group 先生成 `section_facts`：

```json
{
  "section_id": "sec-002",
  "section_path": "安装方式/命令行安装",
  "facts": [
    "命令行安装前需要确认 JDK 版本满足要求。",
    "安装过程包含解压、配置环境变量、执行安装脚本和启动验证。"
  ],
  "operations": [
    {
      "step": 1,
      "action": "解压安装包",
      "command": "tar -xf bes-*.tar.gz",
      "evidence": "原文短摘录"
    }
  ],
  "entities": ["BES", "JDK"],
  "warnings": ["安装路径不要包含中文或空格"]
}
```

`section_facts` 只提取事实、步骤、参数、注意事项、依赖和证据，不落库为页面。

当前完成情况：

- 已完成 `section_facts` 按 batch 调用 LLM。
- 已完成 batch 结果归一化和合并。
- 已完成字段形状补齐，保证 `facts`、`operations`、`config_items`、`warnings`、`entities` 都是数组。
- 已完成 JSON 截断/解析失败 fallback。
- 已补充单元测试覆盖截断 JSON 场景。

### 5. 新增 Page Planner

`Page Planner` 输入全文 `document_outline`、`section_facts`、已有页面摘要和软件分类参考，输出 `page_plan`：

```json
{
  "pages": [
    {
      "planned_title": "BES V9.5.5 连接池配置参数",
      "page_type": "STANDARD",
      "category": "中间件",
      "software": "BES",
      "version": "V9.5.5",
      "covered_section_ids": ["sec-001"],
      "required": true,
      "merge_strategy": "CREATE_OR_PATCH",
      "expected_outline": ["适用范围", "参数说明", "默认值", "配置示例", "生效方式"]
    },
    {
      "planned_title": "BES V9.5.5 连接池配置变更流程",
      "page_type": "RUNBOOK",
      "category": "中间件",
      "software": "BES",
      "version": "V9.5.5",
      "covered_section_ids": ["sec-002", "sec-003"],
      "required": true,
      "merge_strategy": "CREATE_OR_PATCH",
      "expected_outline": ["变更前检查", "修改配置", "重启或热加载", "验证方式", "回滚"]
    }
  ]
}
```

Planner 规则：

- 每个 required section 必须映射到至少一个 page。
- 一个页面可以覆盖多个连续小节，但不能把多个高价值章节压成过短概括页。
- 标题必须包含软件名和版本，避免“安装方式”“产品配置”“参数说明”“问题处理”这类泛标题。
- 页面类型优先服务运维使用场景，步骤类内容优先 `RUNBOOK`。
- 文档类型只影响规划偏好，不改变主链路。

### 6. 按 Page Plan 分批生成页面

页面生成阶段不应一次性生成所有计划页面。正确方式是按 `page_plan.pages` 分批生成，每批只携带这些页面覆盖的 section outline、section_facts 和计划页面，输出完整 Wiki 页面：

```json
{
  "title": "BES V9.5.5 安装步骤",
  "page_type": "RUNBOOK",
  "category": "中间件",
  "software": "BES",
  "version": "V9.5.5",
  "summary": "BES V9.5.5 的前置检查、安装、启动、验证和回滚步骤。",
  "content": "...",
  "source_refs": {
    "source_id": 12,
    "source_title": "BES V9.5.5 安装部署手册.pdf",
    "source_type": "UPLOAD",
    "sections": [
      {
        "section_id": "sec-002",
        "section_path": "安装方式/命令行安装",
        "page_range": "6-9"
      }
    ]
  },
  "coverage": {
    "section_ids": ["sec-002", "sec-003"],
    "evidence_quotes": ["原文短摘录，不超过 50 字"]
  },
  "links": [
    {
      "to": "BES V9.5.5 安装环境要求",
      "type": "DEPENDS_ON",
      "confidence": 0.9
    }
  ]
}
```

分批策略：

- `page_plan` 仍以整篇文档为视角生成，确保页面规划不丢章节。
- `Page Generator` 只按 page batch 生成内容，降低单次 prompt 和 completion 长度。
- 每批只传入该批计划页覆盖的 section outline 和 section_facts。
- 每个页面必须写入 `coverage.section_ids`，并由服务端按 `page_plan.covered_section_ids` 做补齐。
- 每个页面必须写入 `source_refs.sections`，并由服务端根据 outline 注入 source、section、页码、段落和字符范围。
- 单批页面 JSON 解析失败时，服务端根据 `page_plan + section_facts + outline` 生成保守 fallback 页面，后续由质量门禁判断是否 `PARTIAL` 或 `FAILED`。

当前完成情况：

- 已完成 planned page 按 batch 调用 LLM。
- 已完成每批相关 outline/facts/plans 的过滤输入。
- 已完成页面生成 JSON 截断/解析失败 fallback。
- 已完成服务端补齐 `coverage.section_ids` 和 `source_refs.sections`。

## P0：编译并发与阶段耗时可观测性

目录驱动编译会把一次长文档编译拆成多个 LLM 阶段和多个 batch。为了后续优化吞吐、定位慢点、判断是否需要并行 batch，必须记录并发模型和各阶段耗时。

### 当前并发模型

当前有两层并发限制：

| 层级 | 配置 | 默认值 | 作用范围 |
| --- | --- | --- | --- |
| Wiki 编译任务并发 | `app.wiki.ingest.max-concurrent` | `2` | 最多同时执行 2 个 Wiki 编译任务 |
| LLM 调用全局并发 | `app.llm.max-concurrent` / `LLM_MAX_CONCURRENT` | `3` | 全系统所有 `ChatModel.chat(...)` 调用最多同时 3 个 |

注意：

- 单个 `ingestPlanned(...)` 内部目前是顺序执行 batch。
- `section_facts` batch 当前一个批次完成后才调用下一个批次。
- planned page batch 当前也是一个批次完成后才调用下一个批次。
- 因此，只有一个文档在编译时，通常只占用 1 个 LLM 调用槽位；`LLM_MAX_CONCURRENT=3` 主要用于多个编译任务或其他 Agent 同时调用模型时限流。

当前实际链路：

```text
最多 2 个 Wiki 编译任务同时执行
  |
  v
所有任务共享最多 3 个 LLM 调用槽位
  |
  v
单个任务内部 section_facts/page_generation batch 顺序调用
```

后续如需提升单篇大文档速度，可以评估在单个任务内部并行执行 `section_facts` batch，但必须同时控制：

- 不超过 `LLM_MAX_CONCURRENT`。
- 保持 batch 结果按 section order 合并。
- 单批失败不能影响其他批。
- page_plan 仍必须在所有 section_facts 合并完成后再执行。

### 阶段耗时记录方案

建议在 `IngestAgent.ingestPlanned(...)` 内记录结构化耗时，并写入任务 `quality_report` 或单独的 `metrics` 字段。第一版可以先写入 `quality_report.timing`，避免立即新增表结构。

建议记录字段：

```json
{
  "timing": {
    "outline_ms": 800,
    "section_facts_ms": 420000,
    "section_facts_batches": 18,
    "section_facts_batch_ms": [12000, 18000, 9000],
    "page_plan_ms": 35000,
    "page_generation_ms": 180000,
    "page_generation_batches": 5,
    "page_generation_batch_ms": [32000, 41000, 29000],
    "save_pages_ms": 1200,
    "link_resolve_ms": 600,
    "vectorize_ms": 3000,
    "quality_gate_ms": 50,
    "total_ms": 640000
  },
  "llm": {
    "calls": 24,
    "retries": 1,
    "json_parse_fallbacks": 2,
    "section_facts_fallbacks": 1,
    "page_generation_fallbacks": 1
  }
}
```

日志层面建议同步输出阶段摘要：

```text
Planned ingest timing sourceId=12 outlineMs=800 sectionFactsMs=420000 sectionFactBatches=18 pagePlanMs=35000 pageGenerationMs=180000 pageGenerationBatches=5 saveMs=1200 linkMs=600 vectorizeMs=3000 qualityGateMs=50 totalMs=640000 llmCalls=24 retries=1 fallbacks=2
```

### 进度回写方案

当前前端进度不够准确的原因是 `IngestTaskService` 只在调用 `ingestPlanned(...)` 前写入一次 `25% 正在生成章节事实和页面计划...`，而 `ingestPlanned(...)` 内部的 section facts、page plan、page generation、保存、质量门禁没有继续回写任务进度。

建议增加阶段回调：

```java
interface IngestProgressReporter {
    void report(int progress, String step, int completedUnits, int totalUnits);
}
```

推荐进度区间：

| 阶段 | 进度区间 | 文案示例 |
| --- | --- | --- |
| 文档类型和目录抽取 | `10% - 20%` | `正在抽取文档类型和目录结构...` |
| section_facts batch | `20% - 55%` | `正在抽取章节事实 6/18...` |
| page_plan | `55% - 65%` | `正在生成页面计划...` |
| planned page batch | `65% - 85%` | `正在生成 Wiki 页面 3/5...` |
| 保存和合并 | `85% - 90%` | `正在保存页面并合并已有知识...` |
| 链接、向量化、质量门禁 | `90% - 98%` | `正在解析交叉引用并执行质量门禁...` |
| 完成 | `100%` | `编译完成` |

前端继续展示后端返回的 `task.progress` 和 `task.step` 即可，不需要在前端猜测阶段。

当前完成情况：

- 已确认当前 Wiki 编译任务并发默认 `2`，LLM 全局并发默认 `3`。
- 已确认单个 planned ingest 内部 batch 当前顺序执行。
- 已确认前端进度显示不准的直接原因是后端阶段回写不足。
- 已接入 planned ingest 阶段进度回调，后端按阶段和批次更新任务 `progress/step/completed_chunks/total_chunks`。
- 已记录关键阶段和批次耗时到后端日志。
- 待完成：将结构化 `timing/llm` 指标写入质量报告或任务详情；如单篇大文档仍慢，再评估单任务内 batch 并发。

## P0：质量门禁

新增 `WikiIngestQualityGate`，在页面保存前后生成质量报告：

```json
{
  "source_id": 12,
  "coverage_ratio": 0.86,
  "required_sections_total": 7,
  "required_sections_covered": 6,
  "missing_sections": ["产品注册"],
  "short_pages": ["BES V9.5.5 安装步骤"],
  "generic_titles": ["安装方式"],
  "duplicate_titles": [],
  "pages_without_source_refs": [],
  "status": "PARTIAL"
}
```

门禁规则：

- `coverage_ratio < 0.7`：任务 `FAILED`，不标记 source 已编译。
- `0.7 <= coverage_ratio < 0.9`：任务 `PARTIAL`，页面可保留为 DRAFT，但前端必须显示缺失章节。
- `coverage_ratio >= 0.9`：任务可进入 DRAFT 审核。
- 任何 required section 未覆盖：最高只能是 `PARTIAL`。
- 非 Overview 页面没有 `source_refs.sections`：拒绝保存或标为失败。
- 非 Overview 页面正文少于 300 字：标记 `LOW_CONTENT_QUALITY`。
- 标题不含软件名且属于泛标题：标记 `GENERIC_TITLE`。
- 一个页面覆盖多个 required section 但正文过短：标记 `OVER_COMPRESSED_PAGE`。

## P1：文档类型策略

文档类型策略的作用是指导 section 识别和 page_plan 规划，不是为每种文档类型复制一套流程。所有文档类型都必须先输出 `document_outline.sections`，再根据 section 生成 `page_plan`。

| document_type | required section 判定 | section_facts 重点 | page_plan 重点 |
| --- | --- | --- | --- |
| `PRODUCT_OVERVIEW` | 产品定位、架构、组件、版本、适用范围 | 实体、模块、能力、版本差异 | 产品概览页、核心概念页 |
| `INSTALL_GUIDE` | 环境准备、操作步骤、启动验证、回滚、授权 | 前置条件、命令、步骤、注意事项 | 操作手册页、环境要求页 |
| `CONFIG_GUIDE` | 参数表、配置文件、默认值、生效方式、示例 | 参数名、默认值、单位、依赖关系 | 参数标准页、配置变更流程页 |
| `MONITORING_GUIDE` | 指标表、接入方式、权限、端口、告警规则 | 指标、采集方式、阈值、权限要求 | 监控接入页、指标说明页 |
| `TROUBLESHOOTING` | 故障现象、日志、检查命令、根因、修复、验证 | 症状、检查项、根因、处理步骤 | 排障经验页、操作手册页 |
| `STANDARD_SPEC` | 强制条款、基线要求、适用范围、例外条件 | 条款、等级、约束、检查项 | 标准规范页 |
| `RELEASE_NOTE` | 版本号、变更项、兼容性、修复缺陷、升级影响 | 变更、影响范围、风险、兼容性 | 版本说明页、风险说明页 |

通用规则：

- 原文存在明确章节、表格、条款或步骤时，必须映射到 page_plan。
- 原文没有证据时不能编造；可在质量报告中记录 `not_applicable_topics`。
- 多个小 section 可以合并为一个页面，但必须保留 `coverage.section_ids`。
- 低层级但高价值内容必须标记为 required，例如参数表、指标表、故障处理步骤、强制条款。
- 文档类型置信度低时，按通用目录结构规划，质量门禁仍以 section 覆盖率为准。

## P1：章节级合并

现有合并按 `title + page_type` 找已有页面，再让 LLM 判断 `OVERWRITE / APPEND / CONTRADICT`。这对长文档不稳定，容易把后续分段生成的泛标题页面覆盖前面细节。

目标合并策略：

- 先通过 `canonical_title`、`software`、`version`、`page_type` 定位候选页面。
- 再按 `source_refs.sections` 和 Markdown heading block 做章节级 patch。
- 相同章节更新对应 block，不覆盖其他章节。
- 不同章节追加为新 block。
- 标题相近但覆盖章节不同且主题不同，应创建新页面。
- 合并判断失败时不能默认 `OVERWRITE`，应降级为人工审核或 `APPEND_WITH_REVIEW`。

标题规范化示例：

- `Actuator监控` -> `BES V9.5.5 Actuator 监控`
- `JMX监控` -> `BES V9.5.5 JMX 监控`
- `产品配置` -> `BES V9.5.5 产品配置`
- `安装方式` -> `BES V9.5.5 安装方式`

## P1：向量检索分型优化

### 当前问题

知识库向量导入时 metadata 已写入 `category`、`software`、`sourceType`、`sourceId`，但 `VectorStore.search()` 只有 `topK` 参数，Milvus schema 也只把 metadata 作为 JSON 字符串保存，无法使用表达式过滤。

这会导致：

- “安装失败”“连接超时”“配置参数”这类通用问题跨中间件、数据库、主机混召回。
- 用户在 BES 页面提问时，仍可能召回 MySQL 或主机文档。
- 向量搜索结果只能靠相似度排序，缺少业务上下文约束。

### 推荐方案：单 collection + 标量过滤

优先改为一个逻辑 collection，并增加可过滤字段：

- `category`
- `software`
- `version`
- `source_type`
- `source_id`
- `doc_type`
- `page_type`
- `status`

接口改为：

```java
List<VectorSearchResult> search(float[] queryVector, int topK, VectorSearchFilter filter);
```

Milvus 搜索表达式示例：

```text
category == "中间件" && software == "BES" && status == "ACTIVE"
```

召回策略：

1. 如果用户当前上下文已有 `category/software`，强过滤。
2. 如果 query 明确提到软件名，先做软件识别，再过滤或加权。
3. 如果 query 跨软件或跨分类，如 “BES 连接 MySQL 超时”，允许多路过滤搜索后合并。
4. 如果分类无法判断，先全局召回较大的 topK，再按 metadata rerank。

### 何时考虑拆 collection

只有在以下场景才建议按分类拆 collection 或 partition：

- 单 collection 数据量达到性能瓶颈。
- 不同分类使用不同 embedding 模型或维度。
- 不同分类有强隔离要求。
- Milvus 标量过滤性能不能满足延迟要求。

优先级建议：

1. 单 collection + 标量字段过滤。
2. Milvus partition by category。
3. collection per category。

不建议一开始就拆成中间件、数据库多个 collection，因为查询路由、跨分类问题、迁移、删除、统计和权限过滤都会变复杂。

## P1：图谱社区按软件类型聚类

图谱社区不要拆得过细。当前业务视角下，社区只需要按软件类型或软件维度划分，避免同一个软件被拆成“安装”“配置”“监控”等多个小社区，造成用户误以为知识重复或割裂。

### 社区划分规则

社区主键建议：

```text
community_key = category + "/" + software
```

如果 `software` 为空，则退化为：

```text
community_key = category + "/未指定软件"
```

如果后续引入 `software_type_id`，优先使用稳定 ID：

```text
community_key = category + "/" + software_type_id
```

社区名称建议：

```text
{software} ({category})
```

示例：

- `BES (中间件)`
- `TongWeb V7.0 (中间件)`
- `MySQL (数据库)`

### 图算法定位

Louvain 这类社区检测不再作为主社区划分依据。它可以保留为内部分析能力，但前端默认展示和接口默认返回应使用软件类型社区。

建议：

- 主社区：按 `category + software` 或 `software_type_id` 固定分组。
- 社区内排序：可按页面类型、链接度、更新时间、文档类型排序。
- 社区内标签：可展示安装、配置、监控、排障等 topic，但 topic 不产生新的社区。
- 跨软件边：仍保留，用于展示依赖关系，例如 BES 依赖 MySQL，但不把两个软件合并为同一社区。

### 稳定社区编号

社区编号按稳定业务键排序：

```text
stable_key = category + "/" + software
```

这样同一批数据连续请求不会出现颜色和社区编号跳动。

## P2：前端质量可视化

来源列表和任务详情增加：

- 编译状态。
- 覆盖率百分比。
- required sections 总数和已覆盖数量。
- 缺失章节。
- 短页面。
- 泛化标题。
- 无 source_refs 页面。
- “补编缺失章节”入口。
- “按目录重新规划”入口。

图谱页增加：

- 按软件、分类、页面类型、状态过滤。
- 社区列表显示软件类型社区名称、节点数、边数。
- 空图或低质量图提示具体原因，如 DRAFT 不可见、覆盖率低、链接不足。

## 落地步骤

### 第一步：修主链路

1. [x] 新增 `DocumentOutlineExtractor`，支持 Markdown、PDF/Word 解析后文本和编号标题。
   - 已完成第一版：Markdown ATX/Setext 标题、Word `.docx` Heading 样式、PDF 书签/目录页、编号标题、中文章/节标题、短标题弱识别、页码范围、段落范围和结构信号。
   - 后续增强：PDF 版式字号、双栏/OCR 清洗、Word 大纲级别、CommonMark AST。
2. [x] 新增章节事实阶段，长文档分段只产出 facts。
   - 已完成 Prompt 阶段：`buildSectionFactsPrompt(...)`。
   - 后续增强：中间产物持久化、section group 批处理和失败重试。
3. [x] 新增页面规划阶段，生成 `page_plan`。
   - 已完成 Prompt 阶段：`buildPagePlanPrompt(...)`。
   - 已增加 required section 覆盖校验。
4. [x] 修改 `IngestAgent`，按 `page_plan` 生成页面。
   - 已新增 `ingestPlanned(...)` 和 planned page generation prompt。
5. [x] 修改长文档任务流程，不再每个 chunk 直接落页面。
   - `IngestTaskService.executeTask(...)` 已切到 `ingestAgent.ingestPlanned(...)`。

### 第二步：修质量门禁

1. [x] 新增 `WikiIngestQualityGate`。
2. [x] 扩展页面输出 schema，要求 `source_refs.sections` 和 `coverage.section_ids`。
   - 已在 planned ingest 中自动补齐缺失的 `coverage` 和 `source_refs`。
3. [x] 任务结果记录质量报告。
   - 已写入 `IngestResult.qualityReport`、任务/日志错误摘要和 `wiki_ingest_tasks.quality_report`。
4. [x] 低覆盖率任务标记 `PARTIAL/FAILED`，不静默成功。
   - `coverage_ratio < 0.7` 为 `FAILED`；`0.7 <= coverage_ratio < 0.9` 或有缺失/无来源引用为 `PARTIAL`。
5. [x] 前端展示覆盖率和缺失章节。
   - 已在来源详情展示最近一次终态任务的质量报告摘要、任务号、页面创建/更新数、缺失章节、短页面、泛化标题、过度压缩页面、完整 JSON 报告和重新编译入口。

### 第三步：修合并和 source_refs

1. [x] 新增 `canonical_title` 和 `alias_titles` 策略。
   - 已在运行时使用 canonical title 和 LLM 输出的 `alias_titles` 匹配已有页面，并已持久化到 `wiki_pages.canonical_title`、`wiki_pages.alias_titles`。
2. [x] 合并从整页 overwrite/append 改为 heading block patch。
   - 已在 APPEND 分支按 Markdown 标题块合并；复杂冲突仍交给 LLM 的 `CONTRADICT/OVERWRITE` 判断。
3. [x] 合并判断失败时禁止默认 overwrite。
   - planned ingest 路径已默认 `APPEND`，避免解析失败时覆盖已有页面。
4. [x] 所有生产长文档生成路径写入完整 `source_refs`。
   - planned ingest 主路径已写入 `source_refs.sections`，包含 `char_range`、`paragraph_range`、`page_range`、`source_signal`。
   - 旧兼容生成路径仍保留，但生产任务入口不再使用旧分段路径。

### 第四步：修检索

1. [x] `VectorStore.search()` 增加 `VectorSearchFilter`。
2. [x] Milvus schema 增加 `category/software/source_type/source_id/status` 等标量字段。
   - 新 collection 会创建标量字段；旧 collection 支持 metadata fallback，表达式过滤需要重建 collection。
3. [x] InMemoryVectorStore 支持同样过滤逻辑。
4. [x] `KnowledgeService.search()` 根据上下文传入过滤条件。
5. [x] Agent 工具调用支持传入分类、软件和来源范围。

### 第五步：修图谱

1. [x] 社区划分改为 `category + software` 或 `software_type_id`。
   - 当前实现使用 `category + software`；后续引入 `software_type_id` 后可替换为稳定 ID。
2. [x] 社区名称固定为 `{software} ({category})`。
3. [x] 前端默认展示软件类型社区，不再按安装/配置/监控等 topic 拆社区。
4. [x] 社区编号按稳定业务键排序。
5. [x] 图谱 API 返回社区 key、社区名称、节点数、边数。

## 长文档验收样例

以“宝兰德应用服务器软件微服务版 V9.5.5”这类长文档为例，重新编译后应达到：

- [x] required sections 覆盖率 >= 90%。
  - 质量门禁已具备能力；低于 90% 或存在质量问题会进入 `PARTIAL/FAILED`。
- [x] 每个 planned ingest 生成的非 Overview 页面都有 `source_refs.sections`。
- [x] 页面标题包含 `BES` 和 `V9.5.5`。
  - Prompt 已要求包含软件名和版本；泛化标题会被质量门禁标记为 `PARTIAL`。
- [x] 安装环境、安装方式、安装步骤、产品注册、产品配置、Actuator 监控、JMX 监控等原文章节均被覆盖。
  - `page_plan` 校验和质量门禁会阻断 required section 漏映射；真实固定样例仍需在有样例文件后执行验收。
- [x] 不能只生成“产品概览、Actuator 监控、JMX 监控”这类少量概念页。
  - 低覆盖率会被质量门禁阻断或标为 `PARTIAL`。
- [x] 图谱只出现一个 `BES (中间件)` 软件类型社区，安装、配置、监控、排障等页面都归入该社区。
  - 图谱社区已由 `category + software` 决定。

期望页面示例：

- BES V9.5.5 产品概览
- BES V9.5.5 安装环境要求
- BES V9.5.5 安装方式
- BES V9.5.5 安装步骤
- BES V9.5.5 产品注册
- BES V9.5.5 产品配置
- BES V9.5.5 Actuator 监控
- BES V9.5.5 JMX 监控
- BES V9.5.5 常见问题

## 测试计划

- [x] 单元测试：目录抽取、编号标题识别、section range 计算、覆盖率计算。
- [x] 单元测试：Word `.docx` Heading 样式和 PDF bookmark 结构信号。
- [x] 单元测试：page_plan 校验、source_refs schema 校验、短页面检测、泛化标题检测、过度压缩页面检测。
- [x] 单元测试：标题规范化和 alias title 持久化。
- [x] 单元测试：VectorSearchFilter 在 InMemory 中的过滤行为。
  - Milvus 实现已完成 schema、表达式过滤和 metadata fallback；真实 Milvus 连接属于集成环境验收。
- 固定 BES 文本样例验收：待提供样例文件后跑完整编译，断言页面数、覆盖率、source_refs 和缺失章节。
- [x] 回归测试：普通短文档仍可编译。
  - 旧 `ingest(...)`、`ingestContent(...)` 单元测试仍保留；生产任务已统一走 planned ingest。
- [x] 图谱测试：社区名称唯一、稳定，同一软件只归入一个软件类型社区。
- [x] 前端验证：来源详情展示覆盖率、缺失章节、质量问题和补编入口。
  - 已通过前端构建校验；后续可补充组件自动化测试。

## 优先级建议

最高优先级是“目录抽取 + page_plan + 质量门禁”。这三项直接决定长文档是否能完整编译。

第二优先级是“source_refs 完整化 + 章节级合并”。这能解决页面追溯和分段互相覆盖的问题。

第三优先级是“向量标量过滤 + 图谱软件类型社区”。它们能改善检索和展示体验，但不能替代目录驱动编译。
