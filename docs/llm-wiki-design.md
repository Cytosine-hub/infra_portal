# LLM Wiki 知识库改造方案

> 基于 Andrej Karpathy 提出的 LLM Wiki 概念，结合城商行基础架构运维场景设计。
> 参考项目：[nashsu/llm_wiki](https://github.com/nashsu/llm_wiki)、[llm-wiki-agent](https://github.com/SamurAIGPT/llm-wiki-agent)

## 1. 背景与目标

### 1.1 现状

当前知识库采用传统 RAG（Retrieval-Augmented Generation）架构：

```
文档上传 → 文本提取 → 固定长度切片 → 向量化 → 存储
用户提问 → 向量检索 → 拼接上下文 → LLM 回答
```

**存在的问题：**

| 问题 | 影响 |
|---|---|
| 切片无语义理解 | 500 字符硬切，上下文断裂 |
| 查询时才做知识整合 | 每次都要重新推导，效率低、质量不稳定 |
| 无交叉引用 | 相关知识之间没有关联，无法做跨文档推理 |
| 矛盾知识共存 | 新旧文档建议冲突时无人知晓 |
| 知识无结构 | 所有文档平等，无法区分实体、概念、经验、标准 |
| 知识图谱粗糙 | 基于关键词共现，非真正的知识关联 |

### 1.2 LLM Wiki 核心理念

> "知识在入库时就被 LLM 编译成结构化的、互相引用的 Wiki 页面。查询时直接读编译好的知识，而非每次从原始切片重新推导。" —— Andrej Karpathy

**核心转变：**

```
传统 RAG：  文档 → 切片 → 查询时 LLM 整合 → 回答
LLM Wiki： 文档 → LLM 编译 → 结构化 Wiki 页面 → 查询时直接读取 → 回答
```

**与传统 RAG 的关键区别：**

| 维度 | 传统 RAG | LLM Wiki |
|---|---|---|
| 知识形态 | 原始切片，无结构 | 结构化页面，有类型、有引用 |
| 编译时机 | 无编译 | 入库时 LLM 编译 |
| 交叉引用 | 无 | `[[页面名]]` 双向链接 |
| 矛盾处理 | 查询时才发现 | 编译时检测，人工裁决 |
| 知识积累 | 每次独立回答 | 好的回答回写为新页面，持续积累 |
| 维护方式 | 人工管理切片 | LLM 自动维护，人工审核 |

### 1.3 改造目标

1. **回答稳定性**：编译后的知识结构化、一致，查询结果可预期
2. **合规可控**：完整的审核流程、权限管控、审计日志
3. **低维护成本**：自动化 Lint、自动修复、经验自动沉淀，减少人工干预
4. **知识编译化**：文档入库时由 LLM 编译为结构化 Wiki 页面
5. **交叉引用**：建立知识之间的关联网络
6. **矛盾前置检测**：编译时标记冲突，人工裁决
7. **版本感知**：区分不同软件版本的知识
8. **内外网分离**：外网强模型编译，内网轻模型查询

### 1.4 方案对比：RAG vs LLM Wiki

#### 1.4.1 核心区别

```
当前 RAG：  文档 → 切片 → 向量化 → 查询时检索拼接 → LLM 回答
LLM Wiki： 文档 → LLM 编译 → 结构化 Wiki 页面 → 查询时直接读取 → LLM 回答
```

本质区别：**知识整合的时机不同。** RAG 在查询时临时整合，LLM Wiki 在入库时预先编译。

#### 1.4.2 LLM Wiki 优势

| 维度 | RAG 现状 | LLM Wiki | 优势程度 |
|---|---|---|---|
| **回答稳定性** | 每次检索到的切片可能不同，同问题问两次回答可能不一致 | 读的是同一份编译好的 Wiki 页面，回答稳定可预期 | **高** |
| **回答质量** | 从原始切片拼接，LLM 需自己理解、组织、综合 | 读结构化页面，LLM 只需"阅读理解"，质量上限更高 | **高** |
| **交叉引用** | 无。"Nginx 502"和"upstream 配置"是独立切片 | `[[upstream 配置]]` 显式链接，查询时自动扩展关联页面 | **高** |
| **矛盾处理** | 新旧文档冲突时无人知晓，LLM 可能同时引用矛盾切片 | 编译时检测矛盾，标记 CONTRADICTED，人工裁决 | **高** |
| **合规审计** | 无审核、无审计、无权限管控 | 完整审核流程 + 操作审计日志 + RBAC 权限隔离 | **高** |
| **知识积累** | 每次回答独立，好洞察不会沉淀 | 好回答回写为 SYNTHESIS 页面，知识持续增长 | **高** |
| **版本感知** | 切片无版本信息，不同版本建议可能混杂 | Wiki 页面有 version 字段，版本隔离 | **中** |
| **知识图谱** | 基于关键词共现，无语义 | 基于 wiki_links 的 5 信号评分 + 社区检测 | **高** |
| **健康检查** | 无。过时、孤立、矛盾的知识无人管理 | Lint Agent 定期扫描，问题推送审核面板 | **高** |
| **可维护性** | 切片是"死数据"，无法编辑、审核、无生命周期 | Wiki 页面有状态流转（DRAFT→ACTIVE→STALE），可审核 | **高** |
| **LLM 权限隔离** | 向量检索无权限感知，可能泄露用户无权查看的内容 | 检索过滤 + Prompt 约束 + 输出审查三层防护 | **高** |
| **上下文利用** | 500 字符硬切，上下文断裂 | Wiki 页面按语义组织，上下文完整 | **中** |

#### 1.4.3 LLM Wiki 劣势

| 维度 | RAG 现状 | LLM Wiki | 劣势程度 | 缓解方案 |
|---|---|---|---|---|
| **编译成本** | 无编译，零额外成本 | 每篇文档需 1-2 次 LLM 调用 | 已接受 | 外网强模型编译，成本不敏感 |
| **编译延迟** | 上传即入库，秒级 | 编译需 LLM 推理，分钟级 | 低 | 有外网编译窗口，延迟不敏感 |
| **编译质量风险** | 切片是确定性的，无质量波动 | LLM 编译可能遗漏、误判 | 中 | 两步编译 + 程序校验 + 人工审核 |
| **实现复杂度** | 已有完整实现 | 需重新设计数据模型、编译流程、权限体系 | 中 | 分阶段实施，渐进替换 |
| **运维负担** | 低（切片管理简单） | 需管理 Wiki 生命周期、审核、Lint | 已优化 | **自动化维护体系**（见 1.4.5） |
| **存储开销** | 只存切片 | Wiki 页面 + 切片 + 链接 + 审计日志 | 低 | 千级页面存储可忽略 |

#### 1.4.4 为什么选择 LLM Wiki

基于本项目的核心诉求——**回答稳定性 > 合规可控 > 低维护成本**：

- **回答稳定性**：RAG 每次检索的切片组合不同，导致同问题的回答可能不一致。LLM Wiki 读的是编译好的固定页面，回答稳定可预期。这是银行运维场景的硬需求——排障建议不能每次不一样。
- **合规可控**：RAG 无审核、无审计、无权限管控。银行等保要求知识变更可追溯、操作有审计、访问有权限。LLM Wiki 的审核流程 + 审计日志 + RBAC 天然满足。
- **低维护成本**：RAG 表面维护简单，但知识质量问题（过时、矛盾、孤立）无人管理，长期积累导致知识库退化。LLM Wiki 通过自动化 Lint + 自动修复 + 经验自动沉淀，维护成本反而更低。

#### 1.4.5 自动化维护体系（低维护成本的关键）

**设计原则：自动化为默认路径，人工审核为必须环节，人工编辑随时可用。**

```
┌─────────────────────────────────────────────────────────────────┐
│                     自动化维护体系                                │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 定时任务层                                                │    │
│  │                                                          │    │
│  │  [每日 Lint]        [每周全量 Lint]      [每月知识空白分析] │    │
│  │  检测断链+过时       孤立页+矛盾+社区      高频查询无覆盖   │    │
│  │  ↓                  ↓                    ↓               │    │
│  │  自动修复           推送审核面板          触发 Research    │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 自动修复层                                                │    │
│  │                                                          │    │
│  │  断链 → 自动创建占位页面（DRAFT）→ 通知管理员补充内容        │    │
│  │  过时 → 自动标记 STALE → 通知分类管理员 review              │    │
│  │  孤立 → 不自动处理（可能有意为之）→ 仅报告                   │    │
│  │  矛盾 → 不自动处理（需要业务判断）→ 推送审核面板             │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 经验自动沉淀层                                            │    │
│  │                                                          │    │
│  │  Agent 对话中检测到排障经验 → 自动提取 → 走 Ingest 流程    │    │
│  │  → 标记为 DRAFT → 通知经验提供者确认 → 确认后 ACTIVE       │    │
│  │                                                          │    │
│  │  触发条件：                                               │    │
│  │  - 用户点击"保存经验"按钮                                  │    │
│  │  - Agent 回答中包含排障步骤且用户反馈"有用"                  │    │
│  │  - 对话中出现"解决了"、"成功了"等正向反馈                    │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 增量编译层                                                │    │
│  │                                                          │    │
│  │  文档上传 → SHA-256 哈希比对 → 内容未变则跳过               │    │
│  │  内容变化 → 增量编译（只编译变更部分）→ 合并到现有页面       │    │
│  │  新增文档 → 全量编译 → 与已有页面建立交叉引用               │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 人工介入层（审核 + 编辑，按需选择）                          │    │
│  │                                                          │    │
│  │  【审核路径 — 日常默认】                                    │    │
│  │  DRAFT → [审核通过] → ACTIVE                              │    │
│  │  CONTRADICTED → [裁决] → ACTIVE / ARCHIVED / MERGED      │    │
│  │  STALE → [确认过时] → ARCHIVED                            │    │
│  │  操作：一键通过/拒绝/标记已解决                             │    │
│  │                                                          │    │
│  │  【编辑路径 — 按需使用】                                    │    │
│  │  - 审核时发现问题 → 直接编辑内容后再通过                     │    │
│  │  - 补充排障经验 → 编辑已有页面追加内容                       │    │
│  │  - 修正编译错误 → 手动纠正 LLM 编译的不准确之处              │    │
│  │  - 合并页面 → 将多个相关页面合并为一个                       │    │
│  │  - 拆分页面 → 将过长页面按主题拆分                           │    │
│  │  - 调整引用 → 手动修正 [[wikilink]] 交叉引用                │    │
│  │                                                          │    │
│  │  编辑后自动触发：                                          │    │
│  │  - 链接校验（检查新增/删除的 [[引用]] 是否有效）             │    │
│  │  - 审计记录（谁改了什么、改了哪里）                          │    │
│  │  - 重新向量化（仅当 Milvus 可用时，更新 title+summary 向量） │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

**日常维护工作量估算（稳定运行后）：**

| 工作项 | 频率 | 耗时 | 说明 |
|---|---|---|---|
| 审核新页面 | 每周 | 10-20 分钟 | 一键操作，发现问题可直接编辑 |
| 处理 Lint 结果 | 每周 | 5-10 分钟 | 大部分已自动修复，只看剩余问题 |
| 处理矛盾裁决 | 偶发 | 每次 2-3 分钟 | 业务判断，选择保留哪个 |
| 人工编辑/补充 | 按需 | 每次 5-15 分钟 | 修正编译错误、补充经验、合并拆分页面 |
| 导入外网编译包 | 每月 | 5 分钟 | 自动化脚本，人工确认即可 |
| **合计** | — | **每周 20-40 分钟（不含按需编辑）** | — |

> 注：人工编辑是可选能力，不是日常必须。自动化流程处理大部分维护工作，人工编辑用于修正、补充、优化等按需场景。

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                          用户界面层                              │
│                                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐│
│  │ 知识录入  │ │ Wiki 浏览 │ │ 智能问答  │ │ 知识图谱  │ │Lint面板││
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └────────┘│
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                         Agent 编排层                             │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │ Ingest Agent  │  │  Query Agent │  │  Lint Agent  │           │
│  │ (编译文档)     │  │  (回答问题)   │  │  (健康检查)   │           │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │
│         │                 │                 │                    │
│  ┌──────▼───────┐  ┌──────▼───────┐  ┌──────▼───────┐           │
│  │Research Agent │  │  Ops Agent   │  │  Skill Agent │           │
│  │ (深度研究)     │  │ (运维工具)    │  │ (经验沉淀)    │           │
│  └──────────────┘  └──────────────┘  └──────────────┘           │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                          知识存储层                              │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │  wiki_pages   │  │ wiki_sources │  │    wiki_links        │   │
│  │  (结构化页面)  │  │ (原始文档)    │  │   (页面间关系)        │   │
│  └──────────────┘  └──────────────┘  └──────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Vector Store (Milvus, 可选)                   │   │
│  │    Wiki 页面向量（语义兜底，无 Milvus 时系统仍可用）          │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.1 三层数据架构（Karpathy 原始设计）

| 层 | 说明 | 存储位置 | 谁维护 |
|---|---|---|---|
| **Raw Sources** | 原始文档，不可变 | `wiki_sources` 表 + 文件系统 | 用户上传 |
| **Wiki** | LLM 编译的结构化页面 | `wiki_pages` 表 | LLM 编译，人工审核 |
| **Schema** | 编译规则和配置 | Agent System Prompt | 人机协作 |

### 2.2 内外网分离架构

```
┌─────────────────────────────────────┐
│          外网环境（强模型）            │
│                                      │
│  文档录入                             │
│     ↓                                │
│  Ingest Agent（GLM-4-Plus / Claude） │
│     ↓                                │
│  两步 Chain-of-Thought 编译           │
│     ↓                                │
│  导出包（JSON + Markdown + 签名）      │
└─────────────────┬───────────────────┘
                  │
            物理导入（文件摆渡）
                  │
┌─────────────────▼───────────────────┐
│          内网环境（轻模型）            │
│                                      │
│  导入程序：解析 → 写入 MySQL（+ Milvus 可选）│
│                                      │
│  ┌────────┐ ┌──────┐ ┌───────────┐  │
│  │ Query  │ │ Lint │ │ Wiki 浏览  │  │
│  │(GLM-   │ │(大部分│ │ 知识图谱   │  │
│  │ 4.7-fp8)│ │纯程序)│ │(纯程序)   │  │
│  └────────┘ └──────┘ └───────────┘  │
│                                      │
│  Ops Agent / Skill Agent             │
└─────────────────────────────────────┘
```

**模型分工：**

| 任务 | 模型 | 理由 |
|---|---|---|
| Ingest 编译 | 强模型（外网） | 一次性重活，质量最重要 |
| Query 回答 | GLM-4.7-fp8（内网） | 读编译好的页面，本质是阅读理解 |
| Lint 矛盾判断 | GLM-4.7-fp8（内网） | 页面已结构化，矛盾通常明显 |
| Lint 孤立/断链/过时 | 纯程序（内网） | SQL + 正则，不需要 LLM |
| Ops Agent 技能执行 | GLM-4.7-fp8（内网） | 工具调用编排为主 |

## 2.3 技术栈

### 2.3.1 现有技术栈（保留）

| 层 | 技术 | 版本 | 用途 |
|---|---|---|---|
| **后端框架** | Spring Boot | 3.5.3 | Web 应用框架 |
| **语言** | Java | 17 | 后端开发语言 |
| **ORM** | MyBatis | 3.0.4 | 全模块统一数据访问（已迁移 JdbcTemplate） |
| **数据库** | MySQL | 8.0 | 主存储（Wiki 页面、链接、审计日志） |
| **全文索引** | MySQL FULLTEXT | 8.0 | Wiki 页面标题+摘要+内容全文检索 |
| **向量数据库** | Milvus | 2.3.4 | Wiki 页面向量存储（可选，语义兜底） |
| **LLM 框架** | LangChain4j | 1.0.0 | LLM 调用、Embedding 生成 |
| **文件解析** | Apache Tika | 2.9.1 | PDF/Word/Excel 文本提取 |
| **HTTP 客户端** | OkHttp | 4.12.0 | 外部 API 调用 |
| **JSON** | Gson | 2.10.1 | JSON 序列化/反序列化 |
| **Excel** | Apache POI | 5.2.5 | Excel 文件处理 |
| **配置** | Jackson YAML | — | Skill YAML 文件解析 |
| **前端框架** | Vue 3 | 3.x | 前端 SPA |
| **构建工具** | Vite | 5.x | 前端构建 |
| **认证** | Spring Security | — | HTTP Basic Auth + RBAC |

### 2.3.2 新增技术（LLM Wiki 改造需要）

| 技术 | 版本 | 用途 | 必要性 |
|---|---|---|---|
| **JGraphT** | 1.5.2 | 知识图谱的 Louvain 社区检测、图遍历、最短路径 | 必须 |
| **MySQL FULLTEXT 索引** | — | Wiki 页面全文检索（替代向量搜索作为主检索手段） | 必须（MySQL 8.0 原生支持） |
| **Commonmark** | 0.22.0 | Markdown 解析（提取 `[[wikilink]]` 交叉引用） | 必须 |
| **SHA-256 哈希** | — | 文档内容哈希（增量编译判断，Java 原生 `MessageDigest`） | 必须（无需额外依赖） |
| **Quartz / Spring Scheduler** | — | 定时 Lint 任务调度 | 必须（Spring 内置 `@Scheduled` 即可） |

**JGraphT 依赖引入：**

```xml
<dependency>
    <groupId>org.jgrapht</groupId>
    <artifactId>jgrapht-core</artifactId>
    <version>1.5.2</version>
</dependency>
```

**Commonmark 依赖引入：**

```xml
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.22.0</version>
</dependency>
```

### 2.3.3 LLM 模型选型

| 场景 | 模型 | 部署位置 | 说明 |
|---|---|---|---|
| **Ingest 编译**（两步 CoT） | GLM-4-Plus / Claude Opus | 外网 API | 质量最重要，一次性调用 |
| **Query 回答** | GLM-4.7-fp8 | 内网本地部署 | 读编译好的页面，阅读理解为主 |
| **Lint 矛盾判断** | GLM-4.7-fp8 | 内网本地部署 | 页面已结构化，矛盾通常明显 |
| **Ops Agent** | GLM-4.7-fp8 | 内网本地部署 | 工具调用编排为主 |
| **Embedding**（可选） | bge-large-zh-v1.5 / embedding-3 | 内网 Ollama 或外网 API | 仅向量兜底时使用 |

### 2.3.4 前端技术选型

| 组件 | 技术 | 说明 |
|---|---|---|
| Markdown 渲染 | markdown-it | Wiki 页面内容渲染，支持自定义插件 |
| Wiki 链接跳转 | 自定义 markdown-it 插件 | 解析 `[[页面名]]` 为可点击链接 |
| 知识图谱可视化 | force-graph（已有） | 基于 wiki_links 数据渲染 |
| 代码高亮 | highlight.js | Wiki 页面中的代码块高亮 |
| 树形导航 | 自定义组件 | 按分类/软件组织 Wiki 页面树 |

### 2.3.5 技术选型决策记录

**为什么用 MySQL FULLTEXT 而不是 Elasticsearch？**

- Wiki 页面量级在千级，MySQL FULLTEXT 完全胜任
- 引入 ES 增加运维复杂度（独立服务、索引同步、内存占用）
- 已有 MySQL 基础设施，零额外运维成本
- 如果未来数据量级超过万级，可平滑迁移到 ES

**为什么用 JGraphT 而不是 Neo4j？**

- 知识图谱规模在千节点+万边级别，内存图算法足够
- 引入 Neo4j 需要独立服务、学习 Cypher 查询语言、数据同步
- JGraphT 是纯 Java 库，无外部依赖，集成简单
- Louvain 社区检测、最短路径、连通分量等算法 JGraphT 都有

**为什么用 Commonmark 而不是其他 Markdown 库？**

- Commonmark 是 CommonMark 规范的参考实现，解析严格
- 支持自定义扩展（可以添加 `[[wikilink]]` 语法解析）
- 轻量，无额外依赖
- 已有 markdown-it 用于前端渲染，后端用 Commonmark 解析保持一致

**LangChain4j 版本说明**

- 当前使用 1.0.0-beta5（spring-boot-starter 最高版本）
- 1.0.0 稳定版仅发布核心模块（langchain4j-core、langchain4j-open-ai），spring-boot-starter 尚无 1.0.0
- 保持 beta5 不变，功能完全满足需求，API 无实质差异

## 3. 数据模型

### 3.1 核心表结构

```sql
-- ============================================================
-- 1. Wiki 页面：LLM 编译后的结构化知识
-- ============================================================
CREATE TABLE wiki_pages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    page_type ENUM(
        'ENTITY',      -- 实体：Nginx 1.24, MySQL 8.0, 某行核心系统
        'CONCEPT',     -- 概念：连接池调优, 负载均衡策略
        'RUNBOOK',     -- 操作手册：部署流程, 扩容步骤
        'EXPERIENCE',  -- 排障经验：从事故中沉淀
        'STANDARD',    -- 配置标准：参数规范
        'SYNTHESIS',   -- 综合分析：跨文档对比、总结
        'OVERVIEW'     -- 概览页：每个分类的入口页
    ) NOT NULL,
    category VARCHAR(50),          -- 中间件/数据库/主机/网络/安全
    software VARCHAR(100),         -- Nginx/MySQL/Redis/...
    version VARCHAR(50),           -- 版本号（运维知识版本敏感）
    content TEXT NOT NULL,          -- 完整 Markdown，含 [[wikilink]] 交叉引用
    summary VARCHAR(500),          -- 一句话摘要
    source_refs JSON,              -- 来源文档列表 [{title, type, id}]
    status ENUM('DRAFT','ACTIVE','STALE','CONTRADICTED') DEFAULT 'ACTIVE',
    contradiction_note TEXT,        -- 矛盾说明（CONTRADICTED 状态时填写）

    -- 编译元信息
    compiled_by VARCHAR(100),      -- LLM 模型 + 版本
    compiled_at TIMESTAMP NULL,

    -- 审核信息（银行合规）
    reviewed_by BIGINT,            -- 审核人
    reviewed_at TIMESTAMP NULL,

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_title_type (title, page_type),
    INDEX idx_category_software (category, software),
    INDEX idx_status (status),
    INDEX idx_software_version (software, version),
    FULLTEXT INDEX ft_content (title, summary, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 2. 页面间关系（知识图谱的边）
-- ============================================================
CREATE TABLE wiki_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_page_id BIGINT NOT NULL,
    to_page_id BIGINT NOT NULL,
    link_type ENUM(
        'REFERENCES',    -- 引用：A 页面提到了 B
        'CONTRADICTS',   -- 矛盾：A 和 B 的建议冲突
        'SPECIALIZES',   -- 特化：A 是 B 的某个版本/场景的具体化
        'DEPENDS_ON',    -- 依赖：部署 A 之前必须先部署 B
        'RELATED'        -- 相关：同一领域的不同方面
    ) DEFAULT 'REFERENCES',
    confidence DECIMAL(3,2),       -- LLM 判断的置信度 0.00-1.00
    context VARCHAR(500),          -- 建立链接的上下文说明
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_link (from_page_id, to_page_id, link_type),
    INDEX idx_to_page (to_page_id),
    FOREIGN KEY (from_page_id) REFERENCES wiki_pages(id) ON DELETE CASCADE,
    FOREIGN KEY (to_page_id) REFERENCES wiki_pages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. 原始文档（不可变，Raw Sources 层）
-- ============================================================
CREATE TABLE wiki_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    source_type ENUM('UPLOAD','STANDARD_DOC','EXPERIENCE','WEB','MANUAL') NOT NULL,
    file_path VARCHAR(500),
    content_hash VARCHAR(64),      -- SHA-256，用于增量编译判断
    content TEXT,                   -- 提取后的纯文本
    category VARCHAR(50),
    software VARCHAR(100),
    ingested BOOLEAN DEFAULT FALSE,
    ingested_at TIMESTAMP NULL,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_ingested (ingested),
    INDEX idx_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4. 编译日志（审计 + 可追溯）
-- ============================================================
CREATE TABLE wiki_ingest_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    pages_created INT DEFAULT 0,
    pages_updated INT DEFAULT 0,
    links_created INT DEFAULT 0,
    contradictions_found INT DEFAULT 0,
    llm_model VARCHAR(100),
    llm_tokens_used INT,
    duration_ms INT,
    status ENUM('SUCCESS','PARTIAL','FAILED') NOT NULL,
    error_detail TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_source (source_id),
    INDEX idx_operator (operator_id),
    FOREIGN KEY (source_id) REFERENCES wiki_sources(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. Lint 检查结果
-- ============================================================
CREATE TABLE wiki_lint_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lint_type ENUM('ORPHAN','STALE','BROKEN_LINK','CONTRADICTION','GAP') NOT NULL,
    page_id BIGINT,
    description TEXT NOT NULL,
    severity ENUM('LOW','MEDIUM','HIGH') DEFAULT 'MEDIUM',
    resolved BOOLEAN DEFAULT FALSE,
    resolved_by BIGINT,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_unresolved (resolved, severity),
    FOREIGN KEY (page_id) REFERENCES wiki_pages(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 6. 页面级权限覆盖
-- ============================================================
CREATE TABLE wiki_page_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_id BIGINT NOT NULL,
    permission_type ENUM('VISIBLE','HIDDEN','RESTRICTED') NOT NULL,
    target_roles JSON NOT NULL,
    reason VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_page_permission (page_id, permission_type),
    FOREIGN KEY (page_id) REFERENCES wiki_pages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 7. 审核记录
-- ============================================================
CREATE TABLE wiki_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_id BIGINT NOT NULL,
    action ENUM('SUBMIT','APPROVE','REJECT','REQUEST_CHANGES') NOT NULL,
    actor_id BIGINT NOT NULL,
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_page (page_id),
    INDEX idx_actor (actor_id),
    FOREIGN KEY (page_id) REFERENCES wiki_pages(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 8. 操作审计日志
-- ============================================================
CREATE TABLE wiki_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action ENUM(
        'PAGE_VIEW','PAGE_CREATE','PAGE_EDIT','PAGE_DELETE',
        'PAGE_SUBMIT','PAGE_APPROVE','PAGE_REJECT',
        'INGEST_RUN','INGEST_EXPORT','INGEST_IMPORT',
        'LINT_RUN','LINT_RESOLVE',
        'PERMISSION_CHANGE','ACCESS_DENIED'
    ) NOT NULL,
    target_type ENUM('PAGE','SOURCE','LINK','PERMISSION','SYSTEM') NOT NULL,
    target_id BIGINT,
    actor_id BIGINT NOT NULL,
    actor_role VARCHAR(50),
    actor_ip VARCHAR(50),
    detail JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_actor (actor_id),
    INDEX idx_action_time (action, created_at),
    INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 9. 敏感页面访问审批
-- ============================================================
CREATE TABLE wiki_access_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    approver_id BIGINT,
    reason VARCHAR(500) NOT NULL,
    status ENUM('PENDING','APPROVED','REJECTED','EXPIRED') DEFAULT 'PENDING',
    approved_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_requester (requester_id),
    INDEX idx_status (status),
    FOREIGN KEY (page_id) REFERENCES wiki_pages(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 Wiki 页面示例

```markdown
---
title: Nginx 1.24 配置要点
page_type: ENTITY
category: 中间件
software: Nginx
version: "1.24"
summary: Nginx 1.24 的关键配置变更、注意事项和排障要点
source_refs: [{"title":"2024-Q2中间件运维周报","type":"UPLOAD","id":42}]
compiled_by: glm-4-plus
status: ACTIVE
---

# Nginx 1.24 配置要点

## 与 1.22 的关键差异

- 默认启用 HTTP/2 server push，需要手动关闭（见 [[HTTP2 Server Push 配置]]）
- proxy_next_upstream 行为变更（见 [[Nginx upstream 故障转移]]）

## 推荐配置

​```nginx
upstream backend {
    server 10.0.0.1:8080;
    keepalive 32;
}
​```

## 常见问题

- 502 错误排查见 [[Nginx 502 排障手册]]
- 与 [[Keepalive 配置矛盾说明]] 存在场景差异，需按实际情况选择

## 关联知识

- [[反向代理健康检查]] — upstream 健康检查配置
- [[CentOS 7.9 内核参数调优]] — 操作系统层优化
```

## 4. 核心流程

### 4.1 Ingest Agent（文档编译）

这是与传统 RAG 最大的区别——**入库时 LLM 编译，而非查询时 LLM 拼接。**

#### 4.1.1 两步 Chain-of-Thought 编译

**Step 1：结构化分析（LLM 第一次调用）**

输入原始文档，让 LLM 先"想"，输出结构化分析 JSON：

```json
{
  "entities": [
    {
      "name": "Nginx 1.24",
      "type": "software",
      "category": "中间件",
      "version": "1.24",
      "facts": ["支持HTTP/3", "默认启用HTTP/2 server push", "keepalive 默认 32"]
    }
  ],
  "concepts": [
    {
      "name": "反向代理健康检查",
      "description": "通过 health_check 指令配置 upstream 主动健康检查",
      "related": ["Nginx", "upstream", "高可用"]
    }
  ],
  "contradictions": [
    {
      "existing_page": "Nginx keepalive 配置",
      "conflict": "本文档建议 keepalive 32，但现有页面建议 keepalive 64",
      "reason": "场景不同：本文档针对低并发，现有页面针对高并发",
      "confidence": 0.8
    }
  ],
  "dependencies": [
    {
      "from": "Nginx 1.24 部署",
      "to": "CentOS 7.9 基础环境",
      "type": "DEPENDS_ON"
    }
  ]
}
```

**Step 2：Wiki 页面生成（LLM 第二次调用）**

基于 Step 1 的分析结果，生成结构化 Wiki 页面：

```json
{
  "pages": [
    {
      "title": "Nginx 1.24 配置要点",
      "page_type": "ENTITY",
      "category": "中间件",
      "software": "Nginx",
      "version": "1.24",
      "content": "# Nginx 1.24 配置要点\n\n## 与 1.22 的关键差异\n\n...",
      "summary": "Nginx 1.24 的关键配置变更、注意事项和排障要点",
      "links": [
        {"to": "HTTP2 Server Push 配置", "type": "REFERENCES", "confidence": 0.9},
        {"to": "Nginx 502 排障手册", "type": "REFERENCES", "confidence": 0.85}
      ]
    }
  ],
  "source_summary": {
    "title": "Nginx 1.24 部署指南（原始文档摘要）",
    "content": "本文档介绍了在 CentOS 7.9 上部署 Nginx 1.24 的完整流程..."
  }
}
```

#### 4.1.2 编译流程伪代码

```java
public class IngestAgent {

    public IngestResult ingest(WikiSource source, AdminAccount operator) {
        // 1. 权限检查
        assertCanIngest(operator);

        // 2. 提取文本
        String text = extractText(source);

        // 3. 检查是否需要重新编译（增量判断）
        String hash = sha256(text);
        if (hash.equals(source.getContentHash()) && source.isIngested()) {
            return IngestResult.skipped("内容未变化");
        }

        // 4. 获取已有 Wiki 页面上下文（用于矛盾检测）
        List<WikiPage> existingPages = findRelatedPages(source.getCategory(), source.getSoftware());

        // 5. Step 1: 结构化分析
        AnalysisResult analysis = llm.analyze(text, existingPages);

        // 6. 程序化校验 Step 1 输出
        validateAnalysis(analysis);

        // 7. Step 2: 生成 Wiki 页面
        List<WikiPage> pages = llm.generatePages(text, analysis);

        // 8. 存储
        int created = 0, updated = 0, contradictions = 0;
        for (WikiPage page : pages) {
            WikiPage existing = pageRepo.findByTitleAndType(page.getTitle(), page.getPageType());
            if (existing == null) {
                page.setStatus(DRAFT);
                pageRepo.insert(page);
                created++;
            } else {
                MergeDecision decision = llm.decideMerge(existing, page);
                switch (decision.getAction()) {
                    case OVERWRITE:
                        existing.setContent(page.getContent());
                        existing.setUpdatedAt(LocalDateTime.now());
                        pageRepo.update(existing);
                        updated++;
                        break;
                    case APPEND:
                        existing.setContent(existing.getContent() + "\n\n" + page.getContent());
                        pageRepo.update(existing);
                        updated++;
                        break;
                    case CONTRADICT:
                        existing.setStatus(CONTRADICTED);
                        existing.setContradictionNote(decision.getReason());
                        pageRepo.update(existing);
                        contradictions++;
                        break;
                }
            }
        }

        // 9. 建立链接
        int linksCreated = linkResolver.resolveLinks(pages);

        // 10. 向量化（可选，仅当 Milvus 可用时）
        if (vectorStore.isAvailable()) {
            for (WikiPage page : pages) {
                float[] vector = embeddingService.embed(page.getTitle() + "\n" + page.getSummary());
                vectorStore.add("wiki_" + page.getId(), vector, Map.of("pageId", page.getId()));
            }
        }

        // 11. 标记原始文档已编译
        source.setIngested(true);
        source.setIngestedAt(LocalDateTime.now());
        sourceRepo.update(source);

        // 12. 审计日志
        auditLog.record(INGEST_RUN, PAGE, operator.getId(), Map.of(
            "source", source.getTitle(),
            "created", created, "updated", updated,
            "contradictions", contradictions, "links", linksCreated
        ));

        return new IngestResult(created, updated, contradictions, linksCreated);
    }
}
```

#### 4.1.3 Ingest Prompt 模板

```
你是一个银行基础架构运维知识库的编译器。

## 输入
原始文档内容：
{document_content}

已有相关 Wiki 页面：
{existing_pages_summary}

## 任务
分析上述文档，提取结构化知识。

## 输出规则
1. 只提取文档中明确提到的实体，不要推断
2. 版本号必须精确提取，不要省略
3. 矛盾检测：如果新文档的建议与已有页面冲突，必须标记，即使你不确定
4. 每个实体/概念生成一个 Wiki 页面
5. 用 [[页面名]] 标记交叉引用
6. 输出必须是合法 JSON

## 输出格式
{json_schema}
```

### 4.2 Query Agent（智能问答）

#### 4.2.1 混合检索 Pipeline（向量 + FULLTEXT 并行）

**实现状态：** ✅ 已实现。向量语义搜索 + MySQL FULLTEXT 并行执行，合并结果，互补短板。向量搜索失败时自动降级到纯 FULLTEXT。

**向量化在 LLM Wiki 中的定位：** 传统 RAG 中向量搜索是唯一检索手段（切片无结构）。LLM Wiki 编译后的页面有标题、摘要、分类、交叉引用等结构化信息，MySQL FULLTEXT 已能覆盖大部分检索场景。向量搜索降级为**语义兜底**，仅在结构化检索结果不足时触发。Milvus 为可选组件，无 Milvus 时系统仍可用。

```
用户提问："Nginx 502 错误怎么排查"
        │
        ▼
┌───────────────────────────────────────────┐
│ 阶段 1: 结构化检索（主路径，无 LLM 依赖）      │
│                                              │
│  1a. MySQL FULLTEXT 搜索                     │
│      → wiki_pages 表的 title + summary +     │
│        content 全文检索                       │
│      → 按 category / software / version 过滤 │
│      → 返回 top 10                           │
│                                              │
│  1b. 精确匹配补充                             │
│      → 标题精确匹配（用户直接提到页面名）       │
│      → 软件+版本精确匹配                      │
│                                              │
│  结果 ≥ 3 个 → 跳过阶段 2，直接进入阶段 3     │
│  结果 < 3 个 → 进入阶段 2 向量兜底            │
└───────────────────┬───────────────────────┘
                    │
        结果不足 ↓   │
                    ▼
┌───────────────────────────────────────────┐
│ 阶段 2: 向量语义搜索（兜底，可选）              │
│                                              │
│  → 仅在阶段 1 结果不足时触发                   │
│  → 需要 Milvus 可用 + Embedding 服务可用      │
│  → embedding 查询 → Milvus 搜索 → top 5      │
│  → 与阶段 1 结果合并去重                      │
│                                              │
│  如果 Milvus 不可用 → 跳过，仅用阶段 1 结果    │
└───────────────────┬───────────────────────┘
                    │
                    ▼
┌───────────────────────────────────────────┐
│ 阶段 3: 图扩展（关联知识发现）                  │
│                                              │
│  → 对阶段 1/2 命中的页面                      │
│  → 沿 wiki_links 扩展 1 跳                   │
│  → 找到关联的实体/概念/经验页面                │
│  → 例如：命中"Nginx 502"                     │
│    → 扩展到"upstream 健康检查"、"PHP-FPM 配置" │
└───────────────────┬───────────────────────┘
                    │
                    ▼
┌───────────────────────────────────────────┐
│ 阶段 4: 合并去重 + 权限过滤 + 上下文组装       │
│  → 按相关性排序                              │
│  → 过滤无权查看的页面                         │
│  → 控制 token 预算（4K-32K）                 │
└───────────────────┬───────────────────────┘
                    ▼
┌───────────────────────────────────────────┐
│ 阶段 5: LLM 生成回答                        │
│  → 基于编译好的 Wiki 页面（非原始切片）         │
│  → 每个引用标注来源页面                       │
│  → System Prompt 注入权限上下文               │
└───────────────────┬───────────────────────┘
                    ▼
┌───────────────────────────────────────────┐
│ 阶段 6: 回写（可选）                         │
│  → 如果回答中有新的综合洞察                    │
│  → 写回为 SYNTHESIS 类型的 Wiki 页面          │
│  → 知识持续积累                              │
└───────────────────────────────────────────┘
```

**检索策略对比：**

| 场景 | 阶段 1 FULLTEXT | 阶段 2 向量兜底 | 阶段 3 图扩展 |
|---|---|---|---|
| "Nginx 502 怎么排查" | 直接命中标题含"Nginx"、"502"的页面 | 不需要 | 扩展到 upstream、PHP-FPM 相关页面 |
| "服务挂了怎么恢复" | 标题/摘要无"服务挂了"，结果不足 | 语义匹配到"故障恢复"、"高可用"页面 | 扩展到具体软件的排障手册 |
| "keepalive 配置" | 直接命中"Keepalive 配置"页面 | 不需要 | 扩展到 Nginx、Redis 的 keepalive 相关页面 |
| 用户用词与文档差异大 | 结果不足 | **向量兜底发挥作用** | 扩展到关联知识 |

**结论：** 大部分运维查询（按软件名、错误码、配置项提问）阶段 1 就够了。向量搜索是"用户用词与文档差异大"时的兜底。

#### 4.2.2 Query Prompt 模板

```
你是银行基础架构运维知识助手。

## 用户信息
- 角色：{user_role}
- 可访问分类：{accessible_categories}

## 规则
1. 只基于提供的 Wiki 页面内容回答，不要编造
2. 如果 Wiki 中没有相关信息，明确告知用户
3. 不要推测或暗示用户无权查看的内容
4. 回答中引用时标注来源页面标题
5. 如果发现 Wiki 知识可能过时，提醒用户

## 相关 Wiki 页面
{wiki_pages_context}

## 用户提问
{user_query}
```

### 4.3 Lint Agent（健康检查）

#### 4.3.1 检查项

| 检查类型 | 实现方式 | 严重度 | 说明 |
|---|---|---|---|
| 孤立页面 | SQL 查询无入向链接的页面 | LOW | 可能是有价值但未被引用的知识 |
| 断链 | 正则匹配 `[[页面名]]`，查 wiki_pages 表 | MEDIUM | 引用了不存在的页面 |
| 过时页面 | 查 `updated_at` + 软件版本 | MEDIUM | 超过 1 年未更新 |
| 矛盾未解决 | 查 `status=CONTRADICTED` | HIGH | 需要人工裁决 |
| 知识空白 | 分析用户高频查询但无覆盖的主题 | MEDIUM | 需要补充知识 |

#### 4.3.2 Lint 流程

```java
public class LintAgent {

    public LintReport runLint(AdminAccount operator) {
        assertCanLint(operator);

        List<LintResult> results = new ArrayList<>();

        // 1. 孤立页面检测（纯 SQL）
        results.addAll(detectOrphanPages());

        // 2. 断链检测（纯程序）
        results.addAll(detectBrokenLinks());

        // 3. 过时检测（纯 SQL）
        results.addAll(detectStaleContent());

        // 4. 矛盾未解决（纯 SQL）
        results.addAll(detectUnresolvedContradictions());

        // 5. 知识空白检测（需要 LLM）
        results.addAll(detectKnowledgeGaps());

        // 6. 存储结果
        for (LintResult r : results) {
            lintResultRepo.insert(r);
        }

        // 7. 审计
        auditLog.record(LINT_RUN, SYSTEM, operator.getId(),
            Map.of("total", results.size(),
                   "high", countBySeverity(results, HIGH)));

        return new LintReport(results);
    }

    private List<LintResult> detectOrphanPages() {
        // 找出没有被任何页面通过 [[引用]] 或 wiki_links 引用的页面
        return jdbcTemplate.query("""
            SELECT p.id, p.title, p.page_type, p.category
            FROM wiki_pages p
            LEFT JOIN wiki_links l ON p.id = l.to_page_id
            WHERE l.id IS NULL
              AND p.status = 'ACTIVE'
              AND p.page_type != 'OVERVIEW'
            """, (rs, i) -> new LintResult(
                ORPHAN, rs.getLong("id"),
                "页面 '" + rs.getString("title") + "' 无任何引用",
                LOW
            ));
    }

    private List<LintResult> detectBrokenLinks() {
        // 正则提取所有 [[页面名]] 引用，检查目标是否存在
        List<WikiPage> allPages = pageRepo.findAll();
        List<LintResult> results = new ArrayList<>();
        Set<String> existingTitles = allPages.stream()
            .map(WikiPage::getTitle).collect(Collectors.toSet());

        for (WikiPage page : allPages) {
            List<String> links = extractWikiLinks(page.getContent());
            for (String linkTarget : links) {
                if (!existingTitles.contains(linkTarget)) {
                    results.add(new LintResult(
                        BROKEN_LINK, page.getId(),
                        "页面 '" + page.getTitle() + "' 引用了不存在的页面 '" + linkTarget + "'",
                        MEDIUM
                    ));
                }
            }
        }
        return results;
    }

    private List<LintResult> detectStaleContent() {
        return jdbcTemplate.query("""
            SELECT id, title, category, software, updated_at
            FROM wiki_pages
            WHERE status = 'ACTIVE'
              AND updated_at < DATE_SUB(NOW(), INTERVAL 1 YEAR)
            """, (rs, i) -> new LintResult(
                STALE, rs.getLong("id"),
                "页面 '" + rs.getString("title") + "' 超过 1 年未更新",
                MEDIUM
            ));
    }
}
```

### 4.4 Research Agent（深度研究）

当 Lint 发现知识空白，或用户主动触发时运行：

```
触发：Lint 发现"Redis 集群故障转移"无覆盖 / 用户请求研究某个主题
  │
  ▼
Step 1: 生成研究问题（LLM）
  → "Redis Cluster 的 failover 机制是什么？"
  → "故障转移期间数据一致性如何保证？"
  → "常见故障场景和恢复步骤？"
  │
  ▼
Step 2: 搜索信息
  → 检索内部知识库已有内容
  → （可选）调用搜索引擎获取外部资料
  │
  ▼
Step 3: Ingest 编译
  → 将搜索到的内容走标准 Ingest 流程
  → 生成 Wiki 页面，建立交叉引用
  │
  ▼
Step 4: 人工审核
  → 新页面标记为 DRAFT
  → 推送到审核队列
  → 运维人员确认后变为 ACTIVE
```

## 5. 知识图谱

### 5.1 整体工作流程

```
原始文档
  │
  ▼ LLM 编译（IngestAgent）
wiki_pages (title, content, page_type, category, software, version, status)
wiki_links (from_page_id, to_page_id, link_type)
  │
  ▼ 5 信号评分（WikiGraphService.buildGraph()）
JGraphT 加权图 (节点=页面, 边=综合权重)
  │
  ▼ Louvain 社区检测（WikiGraphService.louvain()）
社区划分 (社区ID → 页面列表)
  │
  ▼ 社区命名
前端图谱可视化 (颜色=pageType, 大小=度, 聚类=社区)
```

### 5.2 LLM 编译（数据生产）

LLM 编译是知识图谱的数据来源，产出 `wiki_pages` 和 `wiki_links` 两张表。

#### 编译流程

```
原始文档 → IngestAgent
  ├─ 内容截断（max-content-chars: 20000，约 6K token）
  ├─ 长文档分段串行编译（每段独立编译，串行保证合并正确性）
  │
  ├─ Step 1: LLM 结构化分析
  │   └─ 输入: 文档内容 + 已有页面摘要 + software_types 分类参考
  │   └─ 输出: JSON {entities, concepts, dependencies, contradictions}
  │
  ├─ Step 2: LLM 页面生成
  │   └─ 输入: 文档内容 + Step 1 分析结果
  │   └─ 输出: JSON {pages: [{title, page_type, content, summary, ...}]}
  │
  ├─ 合并决策（已有同名同类型页面时）
  │   └─ LLM 判断: OVERWRITE(覆盖) / APPEND(追加) / CONTRADICT(标记冲突)
  │
  ├─ 写入 wiki_pages
  │   └─ 每个 entity/concept 生成一个 Wiki 页面
  │   └─ 元数据: page_type, category, software, version, status
  │
  └─ LinkResolver → 解析 [[wikilink]] → 写入 wiki_links
```

#### 元数据说明

| 字段 | 说明 | 示例 |
|------|------|------|
| page_type | 页面类型 | ENTITY / CONCEPT / RUNBOOK / EXPERIENCE / STANDARD / SYNTHESIS / OVERVIEW |
| category | 软件分类 | 中间件 / 数据库 / 主机 / 网络 / 安全 |
| software | 软件名称 | Nginx / BES / CentOS / MySQL |
| version | 版本号 | 1.24 / V9.5.5 / 7.9 |
| status | 页面状态 | DRAFT / PENDING_REVIEW / ACTIVE / STALE / CONTRADICTED / REJECTED |

#### 分类自动匹配

编译时查询 `software_types` 表，按分类注入 LLM 提示词作为参考：

```
【中间件】Redis nginx tomcat JDK Nginx ...
【数据库】MySQL PostgreSQL Oracle ...
【主机】Linux Windows Server CentOS ...
```

LLM 根据此表自动确定页面的 `category` 和 `software`，无需人工指定。

#### 编译控制

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `WIKI_INGEST_MAX_CHARS` | 20000 | 单次 LLM 调用的最大字符数 |
| `WIKI_CHUNK_OVERLAP` | 500 | 分段重叠字符数 |
| `WIKI_INGEST_MAX_CONCURRENT` | 2 | 最大同时编译任务数 |
| `LLM_MAX_CONCURRENT` | 3 | 最大 LLM API 并发调用数 |
| `LLM_TIMEOUT_SECONDS` | 300 | LLM 调用超时时间 |

### 5.3 5 信号评分（构建加权图）

`WikiGraphService.buildGraph()` 将 `wiki_pages` 和 `wiki_links` 转换为 JGraphT 加权图。

#### 信号定义

| 信号 | 权重 | 数据来源 | 应用范围 | 说明 |
|------|------|----------|----------|------|
| 直接链接 | 3.0 | wiki_links 表 | 所有有引用关系的页面对 | [[wikilink]] 产生的显式关系 |
| 同软件 | 4.0 | wiki_pages.software | 同一 software 下的所有页面 | 运维场景核心信号，同软件页面强关联 |
| 同分类 | 2.0 | wiki_pages.category | **仅 OVERVIEW 页面之间** | 避免不同软件被拉到同一社区 |
| 共现 | 1.5 | wiki_sources.source_id | 来自同一源文档的页面 | 同一文档编译出的页面有天然关联 |
| 传递依赖 | 2.5 | wiki_links 链式 | A→B→C 则 A→C | 依赖链的传递关系 |

#### 权重叠加

页面对可能同时满足多个信号，权重累加。例如 Nginx 1.24 和 upstream配置：
- 直接链接 3.0 + 同软件 4.0 = 7.0

#### 同分类信号的优化

**问题**：同分类（如"中间件"）的所有页面之间都加边，会导致 Nginx 和 BES 等不同软件被拉到同一社区。

**解决方案**：同分类信号只应用于 OVERVIEW 类型的页面（概览页）：
- OVERVIEW 页面之间加 `W_SAME_CATEGORY = 2.0` 边
- OVERVIEW 到同分类其他页面加 `W_SAME_CATEGORY × 0.5 = 1.0` 边（分类入口连接）
- 实体页面之间只靠直接链接和同软件连接

**效果**：不同软件（如 Nginx 和 BES）之间没有强边，自然分成独立社区。

### 5.4 Louvain 社区检测（聚类）

#### 算法流程

```
加权图 → Louvain 算法
  ├─ 初始化: 每个页面 = 1 个社区
  ├─ 迭代(最多 20 轮):
  │   └─ 对每个节点（随机顺序）:
  │       └─ 遍历邻居社区，计算模块度增益 ΔQ
  │       └─ 移动到增益最大的社区（如果 ΔQ > 0）
  │   └─ 直到无节点移动
  └─ 输出: 社区ID → 节点列表
```

#### 模块度增益公式

```
ΔQ = (ki_in_to / 2m) - (ki × ki_to) / (2m)²
```
- `ki_in_to`：节点到目标社区的边权重之和
- `ki`：节点的加权度
- `ki_to`：目标社区的加权度之和
- `m`：图的总权重

#### 社区命名

```
社区内页面 → 取出现最多的 software → "{software} ({category})"
```
- 如果社区内所有页面的 software 都相同 → 用 software 名（如 "Nginx (中间件)"）
- 如果 software 混杂 → 用 category 名（如 "中间件"）
- 如果都没有 → "社区 {id}"

### 5.5 当前数据示例

```
社区 0 (Nginx):  6 节点 — Nginx + CentOS 页面，靠同软件连接
社区 1 (测试):   1 节点 — 独立测试文档
社区 2 (BES):   10 节点 — 宝兰德页面，靠同软件+直接链接连接
```

### 5.6 前端可视化

```
知识图谱页面：
┌─────────────────────────────────────────────────────────┐
│  [搜索: ___________]  [筛选: 全部▼]  [布局: 力导向▼]    │
├─────────────────────────────────────────────────────────┤
│                                                         │
│         ┌─────────┐                                     │
│         │ MySQL   │ ← 节点大小 = 被引用次数               │
│         │ 8.0     │ ← 节点颜色 = page_type               │
│         └────┬────┘                                     │
│              │                                          │
│    ┌─────────┼─────────┐                                │
│    │         │         │                                │
│ ┌──▼───┐ ┌──▼───┐ ┌───▼──┐                             │
│ │主从   │ │连接池 │ │慢查询 │                             │
│ │配置   │ │调优   │ │优化   │                             │
│ └──┬───┘ └──────┘ └──────┘                             │
│    │                                                    │
│ ┌──▼──────┐     实线 = REFERENCES                       │
│ │读写分离  │     虚线 = CONTRADICTS                      │
│ │架构     │     箭头 = DEPENDS_ON                        │
│ └─────────┘                                             │
│                                                         │
├─────────────────────────────────────────────────────────┤
│ 节点详情：                                               │
│ 标题：MySQL 8.0 主从配置                                 │
│ 类型：ENTITY | 分类：数据库 | 状态：ACTIVE                │
│ 引用数：12 | 被引用：8                                   │
│ 关联：读写分离架构, 连接池调优, 慢查询优化                  │
│ [查看页面] [编辑] [标记矛盾]                              │
└─────────────────────────────────────────────────────────┘
```

## 6. 权限控制

### 6.1 角色 × 操作矩阵

基于现有 14 角色体系，扩展 Wiki 相关权限：

| 操作 | 系统管理员 | 分类管理员 | 分类管理岗 | 开发/运维经理 | 普通运维 |
|---|:---:|:---:|:---:|:---:|:---:|
| 查看本分类 Wiki | ✅ | ✅ | ✅ | ✅ | ✅ |
| 查看跨分类 Wiki | ✅ | ❌ | ❌ | ✅(只读) | ❌ |
| 创建 Wiki 页面 | ✅ | ✅ | ✅ | ❌ | ❌ |
| 编辑 ACTIVE 页面 | ✅ | ✅ | ✅ | ❌ | ❌ |
| 编辑 RESTRICTED 页面 | ✅ | 需审批 | ❌ | ❌ | ❌ |
| 审核 DRAFT → ACTIVE | ✅ | ✅ | ❌ | ❌ | ❌ |
| 标记矛盾 / 置为 STALE | ✅ | ✅ | ✅ | ❌ | ❌ |
| 触发 Ingest 编译 | ✅ | ✅ | ❌ | ❌ | ❌ |
| 运行 Lint | ✅ | ✅ | ❌ | ❌ | ❌ |
| 导出（外网编译） | ✅ | ❌ | ❌ | ❌ | ❌ |
| 导入（内网加载） | ✅ | ❌ | ❌ | ❌ | ❌ |
| 审批 RESTRICTED 访问 | ✅ | ✅ | ❌ | ❌ | ❌ |
| 查看审计日志 | ✅ | 本分类 | ❌ | ❌ | ❌ |

### 6.2 LLM 权限隔离（防止越权泄露）

```
三层防护：

第一层：检索过滤
  向量搜索返回候选集 → canView() 过滤无权页面 → LLM 只能看到有权页面

第二层：Prompt 约束
  System Prompt 注入用户角色和可访问分类
  → LLM 被告知不要推测受限内容

第三层：输出审查（可选）
  对 LLM 回答做关键词扫描
  → 检查是否包含 RESTRICTED 页面的标题或敏感内容
```

```java
public class PermissionAwareSearchService {

    public List<WikiPage> search(String query, AdminAccount user, int topK) {
        // 第一层：向量搜索（不限权限，获取候选集）
        float[] queryVector = embeddingService.embed(query);
        List<VectorSearchResult> candidates = vectorStore.search(queryVector, topK * 3);

        // 第一层：权限过滤
        List<WikiPage> results = candidates.stream()
            .map(c -> pageRepo.findById(c.getPageId()))
            .filter(Objects::nonNull)
            .filter(page -> canView(user, page))
            .limit(topK)
            .collect(Collectors.toList());

        return results;
    }

    public boolean canView(AdminAccount user, WikiPage page) {
        // 1. 系统管理员看所有
        if (user.getRole() == Role.SYS_ADMIN) return true;

        // 2. 检查页面级覆盖（优先级最高）
        WikiPagePermission override = permissionRepo.findByPageId(page.getId());
        if (override != null) {
            if (override.getPermissionType() == HIDDEN
                && override.getTargetRoles().contains(user.getRole().name())) {
                return false;
            }
            if (override.getPermissionType() == RESTRICTED) {
                return accessRequestRepo.hasApprovedAccess(user.getId(), page.getId());
            }
        }

        // 3. 默认：按分类权限
        return permissionService.canAccess(user.getRole(), page.getCategory());
    }
}
```

### 6.3 审核流程

```
Wiki 页面生命周期：

  DRAFT ──提交审核──→ PENDING_REVIEW ──审核通过──→ ACTIVE
    ↑                    │                        │
    │                    │ 审核拒绝                 │ 发现矛盾
    │                    ▼                        ▼
    └──────────── REJECTED              CONTRADICTED
                                                │
                                          人工裁决
                                                │
                                    ┌───────────┼───────────┐
                                    ▼           ▼           ▼
                                  ACTIVE     ARCHIVED    MERGED
                               (解决矛盾)  (废弃)     (合并到其他页面)
```

## 7. 导入/导出（内外网数据交换）

### 7.1 导出包格式

```
wiki-export-{date}/
├── manifest.json              # 元信息 + 数字签名
├── pages/
│   ├── Nginx_1.24_ENTITY.md   # Wiki 页面（Markdown + YAML frontmatter）
│   ├── upstream_CONCEPT.md
│   ├── 502排障_EXPERIENCE.md
│   └── ...
├── links.json                 # 页面间关系
├── sources.json               # 原始文档索引
└── vectors.json               # 预计算的向量（可选）
```

**manifest.json：**

```json
{
  "version": "20260603",
  "compiled_by": "glm-4-plus",
  "compiled_at": "2026-06-03T10:00:00Z",
  "exported_by": 1,
  "total_pages": 342,
  "total_links": 1280,
  "categories": {
    "中间件": 120,
    "数据库": 85,
    "主机": 78,
    "网络": 35,
    "安全": 24
  },
  "signature": "sha256:abcdef1234567890..."
}
```

### 7.2 导出流程（外网侧）

```
1. 系统管理员发起导出请求
2. 二级审批（系统管理员 + 安全管理员）
3. 内容合规扫描
   → 程序扫描：IP 地址、密码、客户数据等敏感信息
   → 人工抽查：抽样检查编译质量
4. 生成导出包 + 数字签名
5. 审计日志记录
```

### 7.3 导入流程（内网侧）

```
1. 系统管理员发起导入
2. 验证数字签名（确保未被篡改）
3. 合规扫描（二次检查）
4. 冲突检测（与现有 Wiki 页面对比）
5. 导入（新页面标为 DRAFT，冲突页面标为 CONTRADICTED）
6. 推送审核队列
7. 审计日志记录
```

## 8. 前端设计

### 8.1 页面结构

```
顶部导航：
┌─────────────────────────────────────────────────────────────┐
│  [知识库]  [智能问答]  [知识图谱]  [Lint 面板]  [系统设置]     │
└─────────────────────────────────────────────────────────────┘

知识库页面：
┌──────────┬──────────────────────────────────────────────┐
│ 左侧树    │ 右侧内容                                     │
│          │                                             │
│ ▼ 中间件  │  # Nginx 1.24 配置要点                       │
│   Nginx  │  **类型:** ENTITY  **状态:** ACTIVE           │
│   Redis  │  **分类:** 中间件  **版本:** 1.24              │
│   Tomcat │  **审核人:** 张三  **审核时间:** 2026-05-20     │
│ ▼ 数据库  │                                             │
│   MySQL  │  ## 与 1.22 的关键差异                        │
│   Oracle │  - 默认启用 HTTP/2 server push...             │
│ ▼ 主机    │                                             │
│          │  ## 关联知识                                  │
│          │  → [[HTTP2 Server Push 配置]]                 │
│          │  → [[Nginx 502 排障手册]]                     │
│          │  → ⚠ 与 [[Keepalive 配置矛盾说明]] 存在矛盾    │
│          │                                             │
│          │  ## 来源                                     │
│          │  📎 2024-Q2 中间件运维周报                     │
└──────────┴──────────────────────────────────────────────┘

Lint 面板：
┌──────────────────────────────────────────────────────────────┐
│  🔴 HIGH   3 个矛盾未解决                                      │
│  🟡 MEDIUM 12 个页面超过 6 个月未更新                            │
│  🔵 LOW    8 个孤立页面（无引用）                                │
│                                                              │
│  [运行 Lint]  [全部标记已读]  [导出报告]                         │
│                                                              │
│  问题列表：                                                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ 🔴 Nginx keepalive 配置 与 Keepalive 配置矛盾说明 冲突    │  │
│  │    建议：合并两篇页面，按场景区分                             │  │
│  │    [查看详情] [标记已解决] [忽略]                            │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ 🟡 Redis 集群部署 超过 1 年未更新                          │  │
│  │    建议：检查是否适用于当前 Redis 版本                       │  │
│  │    [查看详情] [标记已解决] [忽略]                            │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

## 9. 实施计划

### 阶段一：数据模型 + Ingest 基础 ✅ 已完成

**目标：** 跑通"文档 → LLM 编译 → Wiki 页面"主线

- [x] 引入 JGraphT、Commonmark 依赖
- [x] 新建 `wiki_pages`、`wiki_links`、`wiki_sources`、`wiki_ingest_log` 表
- [x] 实现 `WikiPageRepository`、`WikiSourceRepository`（JdbcTemplate）+ WikiLinkRepository、WikiIngestLogRepository
- [x] 实现 `IngestAgent` 两步编译流程（Step 1 分析 + Step 2 生成 + 合并决策）
- [x] 实现 `LinkResolver`（解析 `[[页面名]]` 建立 wiki_links）
- [x] 实现导出包生成（ZIP: manifest.json + pages/*.md + links.json）
- [x] 实现导入程序（解析导出包写入内网数据库，冲突标记 CONTRADICTED）
- [x] 编译 Prompt 模板设计（分析、生成、合并决策 3 个模板）
- [x] 向量化写入（可选，IngestAgent 中条件执行，Milvus 可用时自动触发）
- [x] REST API：`/api/wiki/*`（页面 CRUD、Ingest、导入导出、统计）

### 阶段二：Query 改造 ✅ 已完成

**目标：** 问答优先读 Wiki 页面，fallback 到原始 chunks

- [x] 实现 `WikiSearchService`（混合检索 Pipeline：向量 + FULLTEXT 并行 → 图扩展）
- [x] 改造 `TroubleshootAgent`：Wiki 优先检索，结果不足时 fallback 到 chunk 搜索
- [x] 混合检索：向量语义搜索 + MySQL FULLTEXT 并行执行，合并结果
- [x] Wiki 页面向量化（title + summary 写入 Milvus，含 content/sourceTitle 元数据）
- [x] 向量搜索失败时自动降级到纯 FULLTEXT
- [x] 向量搜索增加重试逻辑（最多 2 次）
- [x] 新增 `POST /api/wiki/pages/reindex` 重新向量化接口
- [x] 回答中引用标注来源页面（`【Wiki：页面标题】` 格式）
- [x] 引用返回结构化数据（wikiPageId + title），前端可点击跳转
- [x] WikiPageMapper 新增 `findByIds` 批量查询
- [x] IngestAgent 向量化时标记 `source: "wiki"` 元数据
- [x] 新增 `app.wiki.search.*` 配置项（max-context-pages、max-content-chars、graph-hop-limit、vector-top-k、fulltext-top-k）
- [x] SecurityContext 继承到异步线程（MODE_INHERITABLETHREADLOCAL）
- [x] FULLTEXT 搜索限制 ACTIVE 状态页面
- [ ] 可选：好答案回写为 SYNTHESIS 页面（后续阶段）

### 阶段三：知识图谱 + 前端 ✅ 已完成

**目标：** 可视化知识关联，Wiki 浏览体验

- [x] WikiController 新增 `GET /api/wiki/graph` 知识图谱 API（nodes + edges from wiki_pages + wiki_links）
- [x] WikiController 新增 `GET /api/wiki/pages/{id}/links` 页面关联 API
- [x] 前端新增 `WikiPanel.vue` 组件（左侧树形导航 + 右侧 Markdown 渲染 + 知识图谱可视化）
- [x] App.vue 新增 Wiki Tab（路由 `#/wiki`、导航按钮、组件集成）
- [x] 知识图谱使用 force-graph 渲染，节点颜色按 pageType 区分，边按 linkType 区分
- [x] `[[wikilink]]` 交叉引用可点击跳转到目标页面
- [x] 页面详情显示关联页面列表（incoming/outgoing 方向）
- [x] 实现 `WikiGraphService`（5 信号评分 + Louvain 社区检测）
- [x] 前端树形目录导航（分类 → 软件 → 页面三层树）
- [x] 前端页面编辑功能（Markdown 编辑器）
- [x] 前端审核流程（提交审核、审核通过/拒绝）
- [x] 前端冲突裁决（保留当前版本、使用导入版本、手动合并）
- [x] 前端导入/导出 UI
- [x] 前端上传文档 + 手动录入
- [x] 前端来源文档查看
- [x] 后端审计日志记录（WikiAuditLogMapper）

### 阶段四：Lint + 权限 + 审计 ✅ 已完成

**目标：** 健康检查、权限管控、合规审计

- [x] 实现 `LintAgent`（孤立、断链、过时、矛盾检测）
- [x] 实现 `wiki_lint_results`、`wiki_page_permissions`、`wiki_audit_log` 表
- [x] 实现 `WikiPermissionService`（页面级权限覆盖：VISIBLE/RESTRICTED/HIDDEN）
- [x] 审计日志记录（状态变更、导入导出操作）
- [x] 前端 Lint 面板（严重度统计 + 问题列表 + 运行 Lint + 标记已解决）
- [x] 前端审核流程界面（提交审核、审核通过/拒绝、冲突裁决）
- [x] Lint API：`GET /api/wiki/lint/results`、`POST /api/wiki/lint/run`、`PUT /api/wiki/lint/results/{id}/resolve`
- [x] 权限 API：`GET /api/wiki/pages/{id}/permission`、`PUT /api/wiki/pages/{id}/permission`

### 阶段五：经验沉淀 + Research（1-2 周）

**目标：** 排障经验自动沉淀，知识空白自动补充

- [ ] Agent 对话中"保存经验"走 Ingest 流程
- [ ] 实现 `ResearchAgent`（知识空白 → 搜索 → 编译 → 审核）
- [ ] 浏览器扩展（可选，采集运维文档网页）

### 阶段六：增量编译 + 生产优化（1 周）

**目标：** 增量更新、性能优化、生产就绪

- [x] 审计日志查询界面（WikiAuditLogMapper 已实现）
- [x] 异步编译任务系统（IngestTaskService + wiki_ingest_tasks 表）
- [x] 长文档分段编译（自动拆分 + 串行处理保证合并正确性）
- [x] LLM 并发控制（PooledChatModel 信号量池化 + 任务级 Semaphore）
- [x] 编译进度追踪（前端进度弹窗 + 任务轮询）
- [x] 批量上传支持（多文件选择 + 独立任务）
- [x] LLM 上下文窗口可配置（WIKI_INGEST_MAX_CHARS）
- [x] 软件分类自动匹配（注入 software_types 到 LLM 提示词）
- [x] 批量设置分类（前端批量分类对话框）
- [x] 全局异常处理（WikiExceptionHandler）
- [x] 去除外键约束（应用层保证数据一致性）
- [x] 文件上传大小限制（前端 10MB 校验）
- [x] 来源文档删除（清理关联记录）
- [x] 页面状态扩展（DRAFT/PENDING_REVIEW/ACTIVE/STALE/CONTRADICTED/REJECTED）
- [x] Wiki 页面向量化（title + summary 写入 Milvus）
- [x] 混合检索（向量 + FULLTEXT 并行，自动降级）
- [x] 知识库检索集成 Wiki 向量（元数据含 content/sourceTitle）
- [x] embedding 模型配置修正（bge-large）
- [x] 重启 Skill（/restart 命令，含 Docker/Milvus/后端/前端）
- [x] 登录测试 Skill（/test-login 命令）
- [ ] 增量编译（SHA-256 哈希判断，避免重复编译）
- [ ] 知识图谱缓存（避免每次请求重新计算）
- [ ] 性能测试（千级 Wiki 页面）
- [ ] 内外网导入/导出流程联调

## 10. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| LLM 编译质量不稳定 | Wiki 页面可能遗漏关键信息 | 两步编译 + 程序校验 + 人工审核 |
| 弱模型（GLM-4.7-fp8）查询效果差 | 回答质量下降 | 查询读的是编译好的页面，难度低；效果不佳时升级模型 |
| 编译成本高 | 大量文档一次性编译消耗 token | 分批编译 + 增量更新 + 导出包复用 |
| 交叉引用不准确 | `[[页面名]]` 指向错误页面 | LinkResolver 程序校验 + Lint 断链检测 |
| 内外网数据同步复杂 | 导入导出流程繁琐 | 标准化导出包格式 + 自动化脚本 |
| 银行合规审查 | 系统需要过等保/审计 | 审计日志 + 权限管控 + 审核流程 |

## 11. 参考资料

| 资料 | 链接 |
|---|---|
| Karpathy LLM Wiki 原始概念 | https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f |
| nashsu/llm_wiki（桌面应用） | https://github.com/nashsu/llm_wiki |
| llm-wiki-agent（Agent Skill） | https://github.com/SamurAIGPT/llm-wiki-agent |
| Chain-of-Thought 论文 | https://arxiv.org/abs/2201.11903 |
| Louvain 社区检测算法 | https://en.wikipedia.org/wiki/Louvain_method |
