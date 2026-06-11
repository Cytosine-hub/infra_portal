# Wiki 文档编译质量优化方案

## 结论

当前 Wiki 编译质量问题的核心不是“安装指南需要一套特殊流程”，而是现有编译链路缺少“目录/章节级覆盖控制”。长文档被拆成多个 chunk 后独立交给 LLM，每个 chunk 只会生成它认为显眼的实体或概念页面，系统没有先规划整篇文档应该产出哪些页面，也没有在保存前检查章节覆盖率。

优化主线应改为通用的“目录驱动编译流程”：

1. 先抽取全文目录和章节结构。
2. 再生成 `page_plan`，明确每个计划页面覆盖哪些章节。
3. 按 `page_plan` 生成页面，而不是让每个 chunk 自由生成。
4. 保存页面时写入章节级 `source_refs`。
5. 通过质量门禁决定任务是否成功、部分成功或失败。

安装指南只作为文档类型策略参与规划，提醒 planner 关注环境准备、安装步骤、配置、启动、验证、回滚和故障处理，不应成为硬编码的独立主流程。

向量存储按中间件、数据库等分类拆 collection 可能减少跨领域召回噪声，但不是当前编译质量的主矛盾。更推荐先做“单 collection + category/software/source_type 标量过滤”，必要时再升级为 Milvus partition 或多 collection。

## 现状评估

### 已具备的基础能力

- 文档上传可携带分类和软件信息，并创建异步 Ingest 任务。
- 长文档已有分段处理，任务可在部分分段失败时进入 `PARTIAL`。
- 页面输出已有基础 JSON 校验，包括页面数组、标题、正文、页面类型和摘要长度。
- `wiki_pages` 已有 `category`、`software`、`version`、`source_refs` 等字段基础。
- 图谱已有 `wiki_links`、同软件、同分类、同源等关联信号雏形。
- 知识库向量导入 metadata 已包含 `category`、`software`、`sourceType`、`sourceId` 等信息。

### 主要缺口

- 长文档仍然是“分段独立编译”，不是“整篇规划后编译”。
- 切块策略对 PDF/Word 安装手册不稳定，不能可靠识别 `1.1`、`第 1 章`、`一、环境准备` 等结构锚点。
- Prompt 目标仍偏向“实体/概念生成页面”，不是“覆盖文章目录”。
- 没有 `page_plan` 阶段，页面生成后直接落库。
- 校验只检查 JSON 结构，不检查章节覆盖率、来源引用、短页面和泛化标题。
- 长文档分段路径新建页面时 `source_refs` 不完整，缺少章节路径、页码、section id 和证据。
- 合并策略按 `title + page_type` 粗匹配，容易让后续分段覆盖前面细节。
- 任务成功条件过低，只要生成或更新了页面，就可能把 source 标记为已编译。
- 向量检索接口没有结构化过滤，容易跨分类召回不相关 chunk。

## 目标

1. 长文档编译从“抓显眼概念”升级为“按目录完整编译”。
2. 每个 Wiki 页面都能追溯到原文 source、章节、页码或段落范围。
3. 编译任务必须暴露覆盖率、缺失章节和质量问题。
4. 低覆盖率结果不能被静默标记为成功。
5. 安装指南、配置手册、监控手册等文档类型作为规划策略，而不是独立流程。
6. 检索阶段支持按分类、软件、来源类型过滤，减少向量召回噪声。
7. 图谱社区命名稳定、唯一、可解释。

## 设计原则

- 目录优先：先理解文章结构，再决定页面结构。
- 规划先行：所有长文档必须先产出 `page_plan`，再生成页面。
- 证据绑定：页面内容必须能回溯到 source 和 section。
- 质量可阻断：覆盖率不足时任务不能自动成功。
- 通用主链路：安装指南只是文档类型策略，不复制一套编译链路。
- 结构化检索优先：category/software/version/source_type 应作为检索过滤条件，不依赖物理拆库兜底。

## 目标架构

```text
上传文档
  |
  v
内容抽取
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
  - 每个章节提取事实
  - 不直接生成页面
  |
  v
Page Planner
  - 根据全文目录和 section_facts 生成 page_plan
  - 明确每个页面覆盖哪些章节
  |
  v
Page Generator
  - 按 page_plan 生成页面
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

### 1. 新增 DocumentOutlineExtractor

`DocumentOutlineExtractor` 负责生成统一的 `document_outline`：

```json
{
  "document_type": "INSTALL_GUIDE",
  "title": "宝兰德应用服务器软件微服务版 V9.5.5 安装部署手册",
  "category": "中间件",
  "software": "BES",
  "version": "V9.5.5",
  "sections": [
    {
      "id": "sec-001",
      "path": "安装环境要求",
      "level": 1,
      "order": 1,
      "page_range": "3-5",
      "char_range": [1200, 5600],
      "required": true,
      "section_type": "PREREQUISITE"
    },
    {
      "id": "sec-002",
      "path": "安装方式/命令行安装",
      "level": 2,
      "order": 2,
      "page_range": "6-9",
      "char_range": [5601, 11200],
      "required": true,
      "section_type": "PROCEDURE"
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

### 2. 长文档改为 Section Facts

长文档分段不再直接生成 Wiki 页面。每个 section 或 section group 先生成 `section_facts`：

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

### 3. 新增 Page Planner

`Page Planner` 输入全文 `document_outline`、`section_facts`、已有页面摘要和软件分类参考，输出 `page_plan`：

```json
{
  "pages": [
    {
      "planned_title": "BES V9.5.5 安装环境要求",
      "page_type": "STANDARD",
      "category": "中间件",
      "software": "BES",
      "version": "V9.5.5",
      "covered_section_ids": ["sec-001"],
      "required": true,
      "merge_strategy": "CREATE_OR_PATCH",
      "expected_outline": ["适用范围", "操作系统要求", "JDK 要求", "端口和资源要求"]
    },
    {
      "planned_title": "BES V9.5.5 安装步骤",
      "page_type": "RUNBOOK",
      "category": "中间件",
      "software": "BES",
      "version": "V9.5.5",
      "covered_section_ids": ["sec-002", "sec-003"],
      "required": true,
      "merge_strategy": "CREATE_OR_PATCH",
      "expected_outline": ["前置检查", "安装过程", "启动服务", "验证安装", "回滚"]
    }
  ]
}
```

Planner 规则：

- 每个 required section 必须映射到至少一个 page。
- 一个页面可以覆盖多个连续小节，但不能把多个高价值章节压成过短概括页。
- 标题必须包含软件名和版本，避免“安装方式”“产品配置”这类泛标题。
- 页面类型优先服务运维使用场景，步骤类内容优先 `RUNBOOK`。
- 文档类型只影响规划偏好，不改变主链路。

### 4. 按 Page Plan 生成页面

页面生成阶段只处理一个计划页面对应的 facts 和原文证据，输出完整 Wiki 页面：

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

### 安装指南策略

识别为 `INSTALL_GUIDE` 时，Planner 应优先检查这些主题是否存在原文证据：

- 产品概览。
- 安装环境要求。
- 安装方式。
- 安装步骤。
- 产品注册或授权。
- 配置入口和关键配置。
- 启动、停止、重启。
- 安装验证。
- 回滚或卸载。
- 常见故障。
- 监控接入，如 Actuator、JMX、日志、指标。

这些主题不是硬编码必生页面。规则是：

- 原文存在明确章节或证据时，必须映射到 page_plan。
- 原文没有证据时，不能编造；可在质量报告中记录 `not_applicable_topics`。
- 多个小节可合并为一个页面，但必须保留 section 覆盖关系。

### 配置手册策略

识别为 `CONFIG_GUIDE` 时，Planner 应关注：

- 配置文件路径。
- 配置项名称、默认值、可选值、单位。
- 生效方式和重启要求。
- 参数依赖关系。
- 配置示例。
- 风险和回滚方式。

### 监控/排障手册策略

识别为 `TROUBLESHOOTING` 或 `MONITORING_GUIDE` 时，Planner 应关注：

- 症状。
- 影响范围。
- 检查命令。
- 日志位置。
- 指标含义。
- 根因判断。
- 修复步骤。
- 验证方式。

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

## P1：图谱社区稳定化

### 社区命名加入主题

社区名称不能只用 `software + category`。建议改为：

```text
{software} · {topic} ({category})
```

topic 选择优先级：

1. 社区内 `ENTITY/OVERVIEW` 页面标题中的主题。
2. 社区内 `RUNBOOK` 页面最多的主题词，如“安装”“注册”“配置”“监控”。
3. 社区内最高度数节点标题。
4. `社区 {id}` 兜底。

示例：

- `BES · 安装与注册 (中间件)`
- `BES · 配置 (中间件)`
- `BES · 监控 (中间件)`

### 同名社区去重

命名后仍重复时，按主题词或序号加后缀。前端展示 `communityName`，不要直接展示 `software/category`。

### 小社区合并

- 同一 software 下，两个社区之间跨社区边权总和超过阈值时合并。
- 节点数小于 3 且同 software 的社区，合并到最近的大社区。
- 支持“按软件分组/按主题聚类”两种视图。

### 稳定社区编号

社区编号按稳定业务键排序：

```text
stable_key = software + "/" + topic + "/" + min(pageId)
```

避免同一批数据连续请求时颜色和编号跳动。

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
- 社区列表显示社区名称、主题、节点数、边数。
- 空图或低质量图提示具体原因，如 DRAFT 不可见、覆盖率低、链接不足。

## 落地步骤

### 第一步：修主链路

1. [ ] 新增 `DocumentOutlineExtractor`，支持 Markdown、PDF 文本和编号标题。
2. [ ] 新增 `SectionFactExtractor`，长文档分段只产出 facts。
3. [ ] 新增 `PagePlanner`，生成 `page_plan`。
4. [ ] 修改 `IngestAgent`，按 `page_plan` 生成页面。
5. [ ] 修改长文档任务流程，不再每个 chunk 直接落页面。

### 第二步：修质量门禁

1. [ ] 新增 `WikiIngestQualityGate`。
2. [ ] 扩展页面输出 schema，强制 `source_refs.sections` 和 `coverage.section_ids`。
3. [ ] 任务结果记录质量报告。
4. [ ] 低覆盖率任务标记 `PARTIAL/FAILED`，不静默成功。
5. [ ] 前端展示覆盖率和缺失章节。

### 第三步：修合并和 source_refs

1. [ ] 新增 `canonical_title` 和 `alias_titles` 策略。
2. [ ] 合并从整页 overwrite/append 改为 heading block patch。
3. [ ] 合并判断失败时禁止默认 overwrite。
4. [ ] 所有长文档生成路径写入完整 `source_refs`。

### 第四步：修检索

1. [ ] `VectorStore.search()` 增加 `VectorSearchFilter`。
2. [ ] Milvus schema 增加 `category/software/source_type/source_id/status` 等标量字段。
3. [ ] InMemoryVectorStore 支持同样过滤逻辑。
4. [ ] `KnowledgeService.search()` 根据上下文传入过滤条件。
5. [ ] Agent 工具调用支持传入分类、软件和来源范围。

### 第五步：修图谱

1. [ ] 社区命名加入 topic。
2. [ ] 同名社区去重。
3. [ ] 同软件小社区合并。
4. [ ] 社区编号按稳定业务键排序。
5. [ ] 图谱 API 返回社区主题、节点数、边数。

## BES 文档验收样例

重新编译“宝兰德应用服务器软件微服务版 V9.5.5”后，应达到：

- required sections 覆盖率 >= 90%。
- 每个非 Overview 页面都有 `source_refs.sections`。
- 页面标题包含 `BES` 和 `V9.5.5`。
- 安装环境、安装方式、安装步骤、产品注册、产品配置、Actuator 监控、JMX 监控等原文章节均被覆盖。
- 不能只生成“产品概览、Actuator 监控、JMX 监控”这类少量概念页。
- 图谱不出现多个同名 `BES (中间件)` 社区。

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

- 单元测试：目录抽取、编号标题识别、section range 计算、page_plan 校验、覆盖率计算。
- 单元测试：标题规范化、source_refs schema 校验、短页面检测、泛化标题检测。
- 单元测试：VectorSearchFilter 在 Milvus/InMemory 两种实现中的行为一致性。
- 集成测试：固定 BES 文本样例跑完整编译，断言页面数、覆盖率、source_refs 和缺失章节。
- 回归测试：普通短文档仍可直接编译，不被长文档流程过度复杂化。
- 图谱测试：社区名称唯一、稳定，同软件多主题可解释。
- 前端测试：任务详情展示覆盖率、缺失章节和补编入口。

## 优先级建议

最高优先级是“目录抽取 + page_plan + 质量门禁”。这三项直接决定长文档是否能完整编译。

第二优先级是“source_refs 完整化 + 章节级合并”。这能解决页面追溯和分段互相覆盖的问题。

第三优先级是“向量标量过滤 + 图谱社区命名”。它们能改善检索和展示体验，但不能替代目录驱动编译。
