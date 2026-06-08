# Wiki 文档编译质量优化方案

## 实现进度（2026-06-08）

当前方案仍以“质量专项设计”为主，尚未完整实现。现有代码已经具备部分基础能力，但还不能解决 BES 安装指南这类文档的章节覆盖问题。

已完成：

- 文档上传已支持在前端先选择分类和软件类型，再批量选择文件创建异步编译任务。
- 后台 Ingest 任务已异步执行，分段失败会进入 `PARTIAL`，所有分段失败或零页面时会进入 `FAILED`，source 不再被错误标记为已编译。
- 长文档切分已从纯字符切分升级为按 Markdown 标题和空行切分语义块，并保留 chunk overlap。
- LLM 页面输出已有基础校验：页面数组非空、title/content 非空、title 长度、page_type 枚举、summary 长度。
- 图谱已支持登录用户查看当前有权限的 DRAFT 页面，解决新编译草稿页面在图谱中为空的问题。
- 图谱后端已有同源弱关联的雏形：当前按相同 `source_refs` 归组加边。

部分完成：

- `source_refs` 已写入 source id/title/type，但还没有章节路径、页码范围、section_ids 和 evidence_quotes。
- 语义切分能减少硬截断，但还没有全文目录盘点，也没有把目录结构传给 LLM。
- 社区检测已使用稳定节点顺序，但社区命名仍主要依赖 `software + category`，仍会出现多个同名社区。
- 前端能显示图谱节点、连接和社区数量，但还没有编译覆盖率、缺失章节、质量报告和补编入口。

未实现：

- `DocumentOutlineExtractor`
- Map-Reduce 编译：`section_facts -> page_plan -> pages`
- `WikiIngestQualityGate`
- 安装指南类固定页面模板强制覆盖
- 输出 schema 中的 `coverage.section_ids`、`source_refs.sections/page_range`
- 章节级合并、标题规范化、alias 管理
- 缺失章节自动补编
- 社区主题命名、同名社区去重、小社区合并
- 图谱 API 返回 `communityTopic`、`communityNodeCount`、`communityEdgeCount`

下一步建议先落地 P0：目录抽取、page_plan Prompt、覆盖率门禁和质量报告。否则继续优化单次 prompt 很可能只能改善个别样例，不能稳定解决长文档漏章节问题。

## 背景

当前 LLM Wiki 已能把上传文档编译为 Wiki 页面并构建图谱，但从实际文档效果看，编译质量还不满足生产使用。以“宝兰德应用服务器软件微服务版 V9.5.5”文档为例，原文包含安装环境要求、安装方式、产品注册、使用场景、产品配置、Actuator 监控、JMX 监控等内容，但当前只生成了 3 个页面：

- 宝兰德应用服务器软件微服务版V9.5.5
- Actuator监控
- JMX监控

这说明当前流程更像“从分段里抓几个显眼概念”，还没有做到“按文档结构完整编译知识”。同时图谱社区出现多个 `BES (中间件)`、多个 `TongWeb V7.0 (中间件)`，说明社区检测和社区命名还缺少稳定合并和主题命名策略。

## 根因判断

### 1. 缺少文档目录盘点

当前 Ingest 是两步：分析 JSON → 生成页面 JSON。长文档由 `IngestTaskService.splitContent()` 分段后，每段独立调用 `IngestAgent.ingestContent()`。这个流程没有先建立全局 `document_outline`，模型不知道整篇文档有哪些章节必须覆盖。

结果是：

- 某些章节如果被 PDF 提取成普通段落，重要性会被模型低估。
- 后续分段只看到局部上下文，容易重复生成已有概念，或漏掉前后关联章节。
- 最终没有“文档章节覆盖率”校验，生成 3 个页面也会被当成成功或部分成功。

### 2. 页面粒度策略不明确

Prompt 只说“每个实体/概念生成一个 Wiki 页面”，但安装指南类文档天然包含多种知识形态：

- 产品实体：软件是什么、版本是什么。
- 环境标准：OS/JDK/数据库/端口/资源要求。
- 安装运行手册：安装步骤、启动停止、验证方式。
- 注册授权：产品注册、License、授权文件。
- 配置手册：配置文件、控制台、关键参数。
- 监控手册：Actuator、JMX、指标、接入方式。
- 使用场景：适用架构、微服务治理、部署拓扑。

如果不把这些页面类型写进 schema 和验收规则，模型会倾向于生成少量概括页。

### 3. 合并策略会吞掉细节

长文档分段编译时，同名同类型页面会触发 merge。当前合并决策只在 `OVERWRITE / APPEND / CONTRADICT` 间选择，缺少“按章节合并”的结构化策略。模型如果生成同名 Overview 或 Concept，很容易覆盖或追加成粗粒度页面，导致页面数量少、内容浅。

### 4. 缺少质量门禁

当前校验主要检查 JSON 合法性、枚举、标题、摘要长度等格式问题，缺少内容质量校验：

- 没有检查每个一级/二级章节是否被引用到 Wiki 页面。
- 没有检查关键章节是否生成对应页面。
- 没有检查页面正文是否过短。
- 没有检查 `source_refs` 是否包含章节路径和页码范围。
- 没有把低覆盖率任务标记为 `PARTIAL/FAILED_REVIEW_REQUIRED`。

### 5. 图谱社区命名过粗

`WikiGraphService.computeCommunityNames()` 当前按社区内最多的 `software + category` 命名。如果同一个软件被 Louvain 拆成多个社区，多个社区都会显示成 `BES (中间件)`，看起来像重复聚类。

另外，同软件边权较强，直接链接、同源、同软件叠加后，小数据集下社区划分容易受边结构影响，产生“同软件多个社区但名字相同”的体验问题。

## 目标

1. 文档编译要从“抓概念”升级为“按目录完整编译”。
2. 每个生成页面都能追溯到原文章节和页码范围。
3. 对安装指南类文档形成稳定页面模板。
4. 编译结果低覆盖时不自动视为成功，必须暴露给用户重编或人工补齐。
5. 图谱社区名称唯一、稳定、可解释，不再出现多个同名社区。

## 优化设计

### P0：增加文档结构盘点和覆盖率门禁

#### 1. 新增 Step 0：文档目录抽取

在现有 Step 1 前增加 `DocumentOutlineExtractor`：

```json
{
  "document_type": "INSTALL_GUIDE",
  "title": "宝兰德应用服务器软件微服务版V9.5.5安装部署手册",
  "software": "BES",
  "version": "V9.5.5",
  "sections": [
    {
      "id": "1",
      "path": "安装环境要求",
      "level": 1,
      "page_range": "3-5",
      "required": true,
      "expected_page_type": "STANDARD",
      "expected_title": "BES 微服务版 V9.5.5 安装环境要求"
    },
    {
      "id": "2",
      "path": "安装方式",
      "level": 1,
      "page_range": "6-15",
      "required": true,
      "expected_page_type": "RUNBOOK",
      "expected_title": "BES 微服务版 V9.5.5 安装方式"
    }
  ]
}
```

来源优先级：

1. PDF 目录/书签。
2. 文本中的目录页。
3. Markdown 标题。
4. 数字编号标题，如 `1.1`、`第 1 章`、`一、`。
5. LLM 目录补全。

#### 2. 长文档改成 Map-Reduce 编译

当前：每个 chunk 独立生成页面。

目标：

1. Step 0 全文抽取目录。
2. Step 1 每个章节生成 `section_facts`，只提取事实，不直接落页面。
3. Step 2 全局汇总 `section_facts`，生成 `page_plan`。
4. Step 3 按 `page_plan` 生成 Wiki 页面。
5. Step 4 覆盖率校验，缺失章节自动补编一次。

这样模型不会因为单个 chunk 视野太窄而漏掉安装、注册、配置等章节。

#### 3. 增加覆盖率质量门禁

新增 `WikiIngestQualityGate`，对每次编译输出生成质量报告：

```json
{
  "source_id": 12,
  "coverage_ratio": 0.86,
  "required_sections_total": 7,
  "required_sections_covered": 6,
  "missing_sections": ["产品注册"],
  "short_pages": ["BES 微服务版 V9.5.5 安装方式"],
  "duplicate_titles": [],
  "status": "PARTIAL"
}
```

门禁建议：

- `coverage_ratio < 0.7`：任务 `FAILED`，不标记 source ingested。
- `0.7 <= coverage_ratio < 0.9`：任务 `PARTIAL`，页面保留 DRAFT，但前端显示缺失章节。
- `coverage_ratio >= 0.9`：任务可进入 DRAFT 审核。
- 任何 required section 未覆盖：至少 `PARTIAL`。
- 页面正文少于 300 字且不是概览页：标记 `LOW_CONTENT_QUALITY`。

### P1：安装指南类文档的页面模板

对 `INSTALL_GUIDE` 文档固定生成或尝试生成以下页面。没有原文证据时允许标记 `not_applicable`，但不能静默漏掉。

| 页面 | page_type | 内容要求 |
|------|-----------|----------|
| 产品概览 | ENTITY | 产品定位、版本、适用范围、核心组件 |
| 安装环境要求 | STANDARD | OS/JDK/数据库/端口/磁盘/内存/网络要求 |
| 安装方式 | RUNBOOK | 图形化/命令行/静默安装/容器化等方式，按原文列出 |
| 安装步骤 | RUNBOOK | 前置检查、安装过程、启动、验证、回滚 |
| 产品注册 | RUNBOOK | License、授权文件、注册入口、校验方法 |
| 使用场景 | CONCEPT | 单机、集群、微服务、灰度、监控等适用场景 |
| 产品配置 | RUNBOOK | 配置入口、配置文件、关键参数、注意事项 |
| Actuator 监控 | RUNBOOK | 端点、接入方式、指标含义、安全限制 |
| JMX 监控 | RUNBOOK | 连接方式、MBean、指标、权限和端口 |
| 常见问题 | EXPERIENCE | 安装失败、注册失败、端口冲突、监控不可用等 |

Prompt 要求：

- 先输出 `page_plan`，再输出 `pages`。
- 每个 required section 必须映射到至少一个 page。
- 每个页面必须包含 `source_refs.sections`。
- 页面标题统一使用 `{software} {version} + 主题`，避免短标题冲突。
- 监控类内容用 RUNBOOK 优先于 CONCEPT，因为运维用户更需要操作步骤。

### P1：增强输出 Schema

现有 `pages` 输出增加字段：

```json
{
  "title": "BES 微服务版 V9.5.5 安装环境要求",
  "page_type": "STANDARD",
  "category": "中间件",
  "software": "BES",
  "version": "V9.5.5",
  "summary": "BES 微服务版 V9.5.5 的操作系统、JDK、资源和端口要求。",
  "content": "...",
  "source_refs": {
    "source_id": 12,
    "source_title": "宝兰德应用服务器软件微服务版V9.5.5安装部署手册.pdf",
    "sections": ["安装环境要求", "运行环境", "端口要求"],
    "page_range": "3-5"
  },
  "coverage": {
    "section_ids": ["1", "1.1", "1.2"],
    "evidence_quotes": ["原文短摘录，不超过 50 字"]
  },
  "links": [
    {"to": "BES 微服务版 V9.5.5 安装方式", "type": "DEPENDS_ON", "confidence": 0.9}
  ]
}
```

校验器必须拒绝：

- 无 `source_refs` 的页面。
- `coverage.section_ids` 为空的非 Overview 页面。
- 标题不含软件名且容易撞名的页面，例如单独的“安装方式”“产品配置”。
- `content` 太短但覆盖多个 required section 的页面。

### P1：章节级合并策略

合并从“同名页面整体覆盖/追加”改为“章节级 Patch”：

- 页面内容拆成 Markdown heading block。
- 新页面只更新相同章节，不覆盖其他章节。
- 相同标题但 `source_refs.sections` 不同，则追加新章节。
- 如果两个页面标题相近但主题不同，创建新页面，不强行合并。
- 使用 `canonical_title` 和 `alias_titles` 做标题规范化。

示例：

- `Actuator监控` 规范化为 `BES 微服务版 V9.5.5 Actuator 监控`
- `JMX监控` 规范化为 `BES 微服务版 V9.5.5 JMX 监控`
- `产品配置` 规范化为 `BES 微服务版 V9.5.5 产品配置`

### P1：图谱社区稳定化和去重命名

#### 1. 社区命名加入主题

社区名称不能只用 `software + category`。改为：

```text
{software} · {topic} ({category})
```

topic 选择优先级：

1. 社区内 OVERVIEW/ENTITY 页面标题中的主题。
2. 社区内 RUNBOOK 页面最多的主题词，如“安装”“注册”“配置”“监控”。
3. 社区内最高度数节点标题。
4. `社区 {id}` 兜底。

例如：

- `BES · 安装与注册 (中间件)`
- `BES · 监控 (中间件)`
- `TongWeb V7.0 · 集群管理 (中间件)`

#### 2. 同名社区自动加后缀

如果命名后仍重复，按社区主题或序号加后缀：

- `BES · 监控 (中间件)`
- `BES · 配置 (中间件)`
- `BES · 其他 1 (中间件)`

前端展示 `communityName`，不要直接展示 `software/category`。

#### 3. 社区合并规则

对小图或同软件强关联图，增加后处理：

- 同一 software 下，两个社区之间如果跨社区边权总和超过阈值，则合并。
- 节点数小于 3 且同 software 的社区，合并到最近的大社区。
- 如果用户选择“按软件分组”，直接以 software 作为一级 cluster，Louvain 只作为二级 topic cluster。

#### 4. 稳定社区编号

社区编号按稳定键排序，而不是按算法遍历顺序：

```text
stable_key = software + "/" + topic + "/" + min(pageId)
```

这样同一批数据连续请求不会出现颜色和社区编号跳动。

### P2：前端编译质量可视化

上传/来源列表增加编译质量入口：

- 覆盖率百分比。
- 已覆盖章节。
- 缺失章节。
- 短页面/重复页面/无 source_refs 页面。
- “补编缺失章节”按钮。
- “按模板重编”按钮。

图谱页增加：

- 按 `软件 / 主题 / 页面类型 / 状态` 过滤。
- 社区列表显示节点数、边数、主题，不只显示名字。
- 空图或低质量图提示“当前页面仍是 DRAFT/编译覆盖率低/链接不足”。

## 具体落地步骤

### 第一步：先修质量门禁和 Prompt

1. [ ] 新增 `DocumentOutlineExtractor`，先基于正则和 PDF 文本提取目录。
2. [ ] 修改 `IngestPromptTemplates`，增加 `document_outline` 和 `page_plan`。
3. [~] 修改 `IngestAgent.validateGeneratedPages()`，校验 `source_refs`、`coverage.section_ids`、标题规范和正文长度。
   - 已完成：title/content/page_type/summary 基础校验。
   - 未完成：source_refs/coverage/标题规范/正文质量校验。
4. [ ] 新增 `WikiIngestQualityGate`，输出覆盖率报告。
5. [ ] 任务结果增加质量信息，低覆盖率标记 `PARTIAL`。

### 第二步：重构长文档编译

1. [ ] `IngestTaskService` 分段不直接生成页面，先生成 `section_facts`。
2. [ ] `IngestAgent` 增加 `compileSectionFacts()` 和 `generatePagesFromPlan()`。
3. [ ] 所有分段完成后统一生成页面，避免局部 chunk 抢先生成粗粒度页面。
4. [ ] 失败分段进入补偿队列，允许只补编缺失章节。

### 第三步：修图谱社区

1. [ ] `computeCommunityNames()` 引入 topic 提取和同名去重。
2. [ ] Louvain 后增加同软件小社区合并后处理。
3. [~] 社区编号按稳定键重新映射。
   - 当前已有稳定节点顺序和重映射基础；仍需按 `software/topic/min(pageId)` 形成稳定业务键。
4. [ ] 图谱 API 返回 `communityTopic`、`communityNodeCount`、`communityEdgeCount`。

### 第四步：前端展示质量

1. [ ] 来源列表展示质量状态。
2. [ ] 编译任务详情展示缺失章节。
3. [ ] 图谱社区 legend 展示唯一名称和节点数。
4. [ ] 增加“按软件分组/按主题聚类”切换。

## 针对 BES 文档的验收样例

重新编译“宝兰德应用服务器软件微服务版 V9.5.5”后，至少应生成：

- BES 微服务版 V9.5.5 产品概览
- BES 微服务版 V9.5.5 安装环境要求
- BES 微服务版 V9.5.5 安装方式
- BES 微服务版 V9.5.5 安装步骤
- BES 微服务版 V9.5.5 产品注册
- BES 微服务版 V9.5.5 使用场景
- BES 微服务版 V9.5.5 产品配置
- BES 微服务版 V9.5.5 Actuator 监控
- BES 微服务版 V9.5.5 JMX 监控
- BES 微服务版 V9.5.5 常见问题

质量指标：

- required sections 覆盖率 >= 90%。
- 每个页面都有 source_refs。
- 每个页面至少有 1 个入边或出边，产品概览页除外。
- 图谱社区不出现重复的 `BES (中间件)`。
- 社区示例：`BES · 安装与注册 (中间件)`、`BES · 配置 (中间件)`、`BES · 监控 (中间件)`。

## 测试计划

- 单元测试：目录抽取、标题规范化、页面 schema 校验、覆盖率计算、社区命名去重。
- 集成测试：用固定 BES 文本样例跑完整编译，断言生成页面数和 required section 覆盖率。
- 回归测试：TongWeb 文档重新编译后页面数不下降，图谱节点/边不为空。
- 权限测试：DRAFT 图谱仍只返回当前用户可见分类。
- 前端测试：上传后任务详情展示覆盖率和缺失章节，图谱社区名称唯一。

## 优先级建议

先做 P0/P1 的“目录盘点 + 覆盖率门禁 + 安装指南模板”。这三项能直接解决“文档明明有很多章节，但 Wiki 只生成几个概念”的核心问题。图谱社区命名可以并行做，改动小、收益明显。章节级合并和补编队列属于第二阶段，能进一步提升长期质量。
