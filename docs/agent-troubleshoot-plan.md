# 线上问题自动排查 Agent 实现方案

## 一、目标

基于 AgentScope Java 框架 + DeepAgent 推理思想，在现有 Spring Boot 项目中构建**线上问题自动排查 Agent**：
- 输入：告警信息、异常现象描述
- 输出：根因分析、影响范围、修复建议、自动执行修复操作
- 数据源：内部知识库（Milvus）、监控系统、日志系统
- 核心模型：GLM-4.7（对话推理）+ BGE-large（向量检索）
- Skill/Tool 可插拔：新增排查能力无需改核心代码
- 单一技术栈：全部 Java，集成到现有 Spring Boot 项目

## 二、技术选型

| 组件 | 技术方案 | 说明 |
|------|---------|------|
| Agent 框架 | AgentScope Java v1.0.12+ | Java 98.2%，JDK 17+，Maven 中央仓库 |
| 推理引擎 | ReActAgent（AgentScope 内置） | 自主推理-行动循环，支持 Human-in-the-Loop |
| 推理范式 | DeepAgent 思想 | 统一推理 + 记忆折叠，增强 ReAct 全局视角 |
| 大模型 | GLM-4.7（智谱 API） | OpenAI 兼容格式，AgentScope 自定义 ChatModel 适配 |
| Embedding | Ollama BGE-large（本地） | 向量检索，已有基础设施 |
| 向量库 | Milvus（已有） | 知识库语义检索 |
| 知识库 | MySQL + Milvus（已有） | 文档切片存储 + 向量索引 |
| 记忆系统 | AgentScope Long-term Memory | 内置跨会话持久化 + 语义搜索 |
| 任务规划 | AgentScope PlanNotebook | 内置结构化任务分解 |
| 监控接入 | Prometheus API | 拉取指标数据 |
| 日志接入 | Elasticsearch API / Loki API | 检索日志 |
| Tool 协议 | MCP + Java 原生 Tool | AgentScope 原生支持 MCP，Java Tool 直接注册 |
| 部署 | 现有 Spring Boot 应用 | 无需额外服务，集成到现有部署 |

## 三、架构设计

```
┌──────────────────────────────────────────────────────────────┐
│                  Spring Boot 应用（现有）                      │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              AgentScope Java Agent 层                    │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │  ReActAgent  │  │  记忆系统     │  │  PlanNotebook │  │ │
│  │  │  GLM-4.7     │  │  Long-term   │  │  任务分解     │  │ │
│  │  │  统一推理     │  │  Memory      │  │  步骤追踪     │  │ │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │ │
│  │         └─────────────────┴─────────────────┘           │ │
│  └─────────────────────────┬───────────────────────────────┘ │
│                            │                                 │
│  ┌─────────────────────────┴───────────────────────────────┐ │
│  │                  Tool 层（可插拔）                        │ │
│  │                                                         │ │
│  │  Java 原生 Tools（Spring Bean）          MCP Tools       │ │
│  │  ┌──────────────┐ ┌──────────────┐    ┌──────────────┐ │ │
│  │  │KnowledgeSearch│ │QueryMetrics  │    │ 外部 MCP     │ │ │
│  │  │SearchLogs     │ │CheckAlerts   │    │ Server       │ │ │
│  │  │QueryTopology  │ │ExecCommand   │    │（可选扩展）   │ │ │
│  │  │CreateTicket   │ │SaveExperience│    │              │ │ │
│  │  └──────────────┘ └──────────────┘    └──────────────┘ │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              Skill 管理层                                │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │ SkillLoader  │  │ SkillMatcher │  │ SkillRunner  │  │ │
│  │  │ YAML 解析    │  │ 关键词匹配   │  │ 步骤编排执行  │  │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              已有业务层（复用）                            │ │
│  │  KnowledgeService  VectorStore  EmbeddingService         │ │
│  │  ForumService      ReleaseService  StorageService        │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

## 四、核心模块设计

### 4.1 推理引擎（AgentScope ReActAgent + DeepAgent 增强）

AgentScope 内置 ReActAgent，结合 DeepAgent 的统一推理思想进行增强：

```java
// Agent 配置
ReActAgent agent = ReActAgent.builder()
    .name("OpsAgent")
    .sysPrompt(OPS_SYSTEM_PROMPT)           // 排查专家系统提示词
    .model(GLM4ChatModel.builder()           // GLM-4.7 模型
        .apiKey(glmApiKey)
        .modelName("glm-4.7")
        .build())
    .tools(tools)                            // 可插拔 Tool 列表
    .memory(longTermMemory)                  // 长期记忆
    .build();
```

DeepAgent 增强点：
- **全局视角**：系统提示词引导 Agent 维持排查全局，不被单步结果绑架
- **并行调用**：ReActAgent 支持一次推理返回多个 Tool 调用
- **记忆折叠**：上下文过长时自动压缩为结构化摘要（自定义 MemoryHook）

### 4.2 记忆系统（AgentScope Long-term Memory + 记忆折叠）

```java
// AgentScope 内置长期记忆
LongTermMemory memory = LongTermMemory.builder()
    .embeddingModel(bgeEmbeddingModel)       // BGE-large 向量化
    .vectorStore(milvusVectorStore)          // Milvus 存储
    .maxEntries(1000)
    .build();

// 记忆折叠 Hook（自定义实现）
public class MemoryFoldingHook implements AgentHook {
    @Override
    public void beforeInference(AgentContext ctx) {
        if (ctx.getMessageCount() > FOLD_THRESHOLD) {
            // 压缩历史消息为结构化摘要
            List<Msg> compressed = foldMemory(ctx.getMessages());
            ctx.setMessages(compressed);
        }
    }
}
```

三层记忆结构：
- **情景记忆**：排查过程的关键事件和决策 → 存入 Long-term Memory
- **工作记忆**：当前排查的中间状态 → AgentScope Session Context
- **工具记忆**：每次 Tool 调用的结果 → 自动记录到对话历史

### 4.3 可插拔 Tool 系统

#### 4.3.1 Java 原生 Tool（推荐，性能最优）

每个 Tool 是一个 Spring Bean，实现 AgentScope 的 Tool 接口：

```java
@Component
public class KnowledgeSearchTool implements Tool {

    private final KnowledgeService knowledgeService;

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String description() {
        return "从内部知识库检索相关文档。用于查找排查手册、配置指南、历史案例。参数：query(查询内容), top_k(返回数量)";
    }

    @Override
    public String call(Map<String, Object> params) {
        String query = (String) params.get("query");
        int topK = params.containsKey("top_k") ? (int) params.get("top_k") : 5;

        float[] vector = knowledgeService.embed(query);
        List<VectorStore.VectorSearchResult> results = knowledgeService.search(vector, topK);

        return results.stream()
            .map(r -> "【" + r.getMetadata().get("sourceTitle") + "】\n"
                     + r.getMetadata().get("content")
                     + "\n相关度: " + String.format("%.2f", r.getScore()))
            .collect(Collectors.joining("\n---\n"));
    }
}
```

#### 4.3.2 内置 Tool 列表

| Tool 名称 | 实现方式 | 功能 | 数据源 |
|-----------|---------|------|--------|
| `knowledge_search` | Java Bean | 知识库语义检索 | 复用 KnowledgeService |
| `query_metrics` | Java Bean | 查询监控指标 | Prometheus HTTP API |
| `search_logs` | Java Bean | 搜索应用日志 | Elasticsearch / Loki API |
| `check_alerts` | Java Bean | 查看当前告警 | Alertmanager API |
| `query_topology` | Java Bean | 查询服务拓扑 | CMDB / 注册中心 API |
| `exec_command` | Java Bean（需审批） | 执行运维命令 | SSH / K8s API |
| `create_ticket` | Java Bean | 创建工单 | 工单系统 API |
| `save_experience` | Java Bean | 沉淀排查经验到知识库 | 复用 KnowledgeService |

新增 Tool 只需：写一个 `@Component` 实现 `Tool` 接口，Spring 自动注册。

#### 4.3.3 MCP Tool 扩展（可选）

对于非 Java 的外部工具，通过 MCP 协议接入：

```java
// application.yml
agentscope:
  mcp:
    servers:
      - name: browser
        command: npx
        args: ["@anthropic/mcp-browser"]
      - name: code_interpreter
        command: python
        args: ["tools/code_interpreter.py"]
```

AgentScope Java 原生支持 MCP 协议，可直接连接外部 MCP Server。

### 4.4 Skill 管理系统（自建，2-3 天工作量）

#### 4.4.1 Skill YAML 定义

```yaml
# backend/src/main/resources/skills/oom-troubleshoot.yaml
name: oom-troubleshoot
description: "排查 JVM OOM 问题"
trigger:
  keywords: ["OOM", "OutOfMemoryError", "内存溢出", "heap space"]
steps:
  - tool: query_metrics
    args:
      promql: "process_memory_usage{service='{{service}}'}"
      start: "30m ago"
    description: "查看内存使用趋势"

  - tool: search_logs
    args:
      query: "OutOfMemoryError OR Java heap space OR GC overhead"
      service: "{{service}}"
      timerange: "1h"
    description: "搜索 OOM 相关日志"

  - tool: knowledge_search
    args:
      query: "JVM OOM 排查 heap dump 分析 内存泄漏"
      top_k: 5
    description: "检索知识库中的 OOM 排查经验"

  - tool: query_topology
    args:
      service: "{{service}}"
    description: "查看服务依赖关系"

  - prompt: "综合以上数据，分析 OOM 根因（内存泄漏/GC 配置/堆大小不足），给出修复建议"
```

#### 4.4.2 Skill 加载器

```java
@Component
public class SkillLoader {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        // 扫描 classpath:skills/*.yaml
        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath:skills/*.yaml");
        for (Resource resource : resources) {
            Skill skill = parseYaml(resource);
            skills.put(skill.getName(), skill);
        }
    }

    public Skill match(String input) {
        return skills.values().stream()
            .filter(s -> s.getTrigger().getKeywords().stream()
                .anyMatch(kw -> input.toUpperCase().contains(kw.toUpperCase())))
            .findFirst().orElse(null);
    }

    private Skill parseYaml(Resource resource) {
        // 使用 Jackson YAML 解析
        return new ObjectMapper(new YAMLFactory())
            .readValue(resource.getInputStream(), Skill.class);
    }
}
```

#### 4.4.3 Skill 编排执行器

```java
@Component
public class SkillRunner {

    private final ReActAgent agent;
    private final Map<String, Tool> toolRegistry;

    public String run(Skill skill, Map<String, String> context) {
        StringBuilder accumulated = new StringBuilder();

        for (SkillStep step : skill.getSteps()) {
            if (step.getTool() != null) {
                // 执行 Tool 调用
                Tool tool = toolRegistry.get(step.getTool());
                Map<String, Object> args = resolveArgs(step.getArgs(), context);
                String result = tool.call(args);
                accumulated.append("[").append(step.getDescription()).append("]\n")
                          .append(result).append("\n\n");
            } else if (step.getPrompt() != null) {
                // 将累积数据 + 分析提示词发给 Agent
                String fullPrompt = accumulated + "\n" + resolveArgs(step.getPrompt(), context);
                Msg response = agent.call(Msg.builder()
                    .textContent(fullPrompt)
                    .build()).block();
                return response.getTextContent();
            }
        }
        return accumulated.toString();
    }

    private Map<String, Object> resolveArgs(Map<String, String> template, Map<String, String> context) {
        // {{service}} → 实际值替换
        Map<String, Object> resolved = new HashMap<>();
        template.forEach((k, v) -> {
            String val = v;
            for (Map.Entry<String, String> ctx : context.entrySet()) {
                val = val.replace("{{" + ctx.getKey() + "}}", ctx.getValue());
            }
            resolved.put(k, val);
        });
        return resolved;
    }
}
```

### 4.5 知识库集成（已有基础设施复用）

```
已有：MySQL knowledge_chunks → BGE-large embedding → Milvus
新增：排查经验自动沉淀

排查流程结束 → Agent 总结排查过程 → SaveExperienceTool → 写入 MySQL → 向量化 → Milvus
下次遇到类似问题 → KnowledgeSearchTool 命中历史排查经验
```

知识库数据来源：
- 技术文档（已有）
- 标准操作手册（已有）
- 历史排查记录（新增，Agent 自动沉淀）
- 告警处理 SOP（新增，人工录入或 Agent 生成）

### 4.6 排查流程示例

```
告警：Redis 连接池耗尽，服务 xxx 响应超时

Agent 推理过程：
┌─────────────────────────────────────────────────────────┐
│ Step 1: 理解问题（ReActAgent 推理）                      │
│   - 服务 xxx 出现 Redis 连接池耗尽                       │
│   - 表现为响应超时                                       │
│   - 匹配 Skill: connection-pool-troubleshoot             │
│   - 需要查明：为什么连接池耗尽？是泄漏还是并发过高？      │
├─────────────────────────────────────────────────────────┤
│ Step 2: 执行 Skill 步骤（并行调用多个 Tool）             │
│   ├─ [QueryMetricsTool] Redis 连接数、活跃连接、等待队列 │
│   ├─ [SearchLogsTool] 搜 "Connection pool exhausted"     │
│   ├─ [KnowledgeSearchTool] "Redis 连接池耗尽排查"        │
│   └─ [QueryTopologyTool] 服务 xxx 的 Redis 依赖关系      │
├─────────────────────────────────────────────────────────┤
│ Step 3: 分析结果（Agent 综合推理）                       │
│   - 监控：连接数从 10:00 开始持续上升，10:30 触发上限     │
│   - 日志：发现大量 "connection not returned to pool"      │
│   - 知识库：匹配到"连接泄漏常见原因：未在 finally 关闭"  │
│   - 拓扑：xxx 服务连接了 3 个 Redis 实例                 │
├─────────────────────────────────────────────────────────┤
│ Step 4: 深入排查（Agent 自主发现新线索）                 │
│   - [SearchLogsTool] 搜最近部署记录                      │
│   - [QueryMetricsTool] 对比部署前后的连接数变化           │
│   - 结论：10:00 有一次代码部署，新增了缓存查询未关闭连接  │
├─────────────────────────────────────────────────────────┤
│ Step 5: 输出结论                                         │
│   根因：10:00 部署的 v2.3.1 版本引入连接泄漏 bug         │
│   影响：xxx 服务 Redis 连接池耗尽，所有依赖请求超时       │
│   建议：回滚到 v2.3.0，同时修复连接关闭逻辑              │
│   操作：[ExecCommandTool] 可选自动回滚（Human-in-the-Loop │
│         审批）                                            │
├─────────────────────────────────────────────────────────┤
│ Step 6: 沉淀经验                                         │
│   [SaveExperienceTool] 自动生成知识条目写入知识库         │
└─────────────────────────────────────────────────────────┘
```

## 五、与现有系统集成

### 5.1 Maven 依赖

```xml
<!-- AgentScope Java -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope</artifactId>
    <version>1.0.12</version>
</dependency>
```

### 5.2 GLM-4.7 模型适配

AgentScope 支持自定义 ChatModel，适配智谱 API（OpenAI 兼容格式）：

```java
@Component
public class GLM4ChatModel implements ChatModel {

    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient client = new OkHttpClient();

    public GLM4ChatModel(@Value("${app.ai.api-key}") String apiKey,
                         @Value("${app.ai.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public Msg generate(List<Msg> messages) {
        // 构造 OpenAI 兼容请求
        Map<String, Object> body = new HashMap<>();
        body.put("model", "glm-4.7");
        body.put("messages", toOpenAIMessages(messages));
        body.put("temperature", 0.1);
        body.put("max_tokens", 8192);

        Request request = new Request.Builder()
            .url(baseUrl + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(
                MediaType.parse("application/json"),
                new Gson().toJson(body)))
            .build();

        // 发送请求，解析响应
        try (Response response = client.newCall(request).execute()) {
            // 解析 OpenAI 格式响应，返回 Msg
        }
    }
}
```

### 5.3 Agent API 控制器

```java
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ReActAgent agent;
    private final SkillLoader skillLoader;
    private final SkillRunner skillRunner;

    // 对话式排查
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody AgentChatRequest req) {
        // 1. 匹配 Skill
        Skill skill = skillLoader.match(req.getMessage());

        String response;
        if (skill != null) {
            // 2. 执行 Skill 编排
            response = skillRunner.run(skill, req.getContext());
        } else {
            // 3. 通用推理
            Msg msg = agent.call(Msg.builder()
                .textContent(req.getMessage())
                .build()).block();
            response = msg.getTextContent();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("response", response);
        result.put("skill", skill != null ? skill.getName() : null);
        return result;
    }

    // 告警 Webhook
    @PostMapping("/webhook/alert")
    public Map<String, Object> alertWebhook(@RequestBody AlertWebhookRequest alert) {
        String service = alert.getService();
        String description = alert.getDescription();

        // 触发排查
        Map<String, String> context = Map.of("service", service);
        Skill skill = skillLoader.match(description);
        String result = skill != null
            ? skillRunner.run(skill, context)
            : agent.call(Msg.builder()
                .textContent("告警：" + description + "，服务：" + service + "，请排查")
                .build()).block().getTextContent();

        return Map.of("status", "completed", "result", result);
    }

    // Skill 管理
    @GetMapping("/skills")
    public List<Skill> listSkills() {
        return skillLoader.getAll();
    }

    @PostMapping("/skills")
    public void saveSkill(@RequestBody Skill skill) {
        skillLoader.save(skill);
    }

    @DeleteMapping("/skills/{name}")
    public void deleteSkill(@PathVariable String name) {
        skillLoader.delete(name);
    }
}
```

### 5.4 前端扩展（集成到现有 Vue 前端）

在现有 `App.vue` 中新增排查面板：

```
导航栏 → [智能排查] 按钮
  ├─ 对话式排查：输入问题 → Agent 分析 → 展示结论
  ├─ 告警接入：展示最近告警 → 一键触发排查
  ├─ Skill 管理：列表/编辑/新增/删除 Skill
  └─ 排查历史：查看历史排查记录和报告
```

## 六、部署架构

无需额外部署，集成到现有 Spring Boot 应用：

```yaml
# docker-compose.yml（在现有基础上新增 Prometheus 等监控组件）
version: '3.8'

services:
  # 现有后端（已集成 AgentScope Java Agent）
  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - APP_AI_API_KEY=${GLM_API_KEY}
      - APP_AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
    depends_on:
      - mysql
      - milvus

  # 现有 Milvus
  etcd:
    image: quay.io/coreos/etcd:v3.5.18
    command: etcd -listen-client-urls=http://0.0.0.0:2379 -advertise-client-urls=http://0.0.0.0:2379
    ports:
      - "2379:2379"

  milvus:
    image: milvusdb/milvus:latest
    command: milvus run standalone
    ports:
      - "19530:19530"
      - "9091:9091"
    environment:
      - ETCD_ENDPOINTS=etcd:2379
      - COMMON_STORAGETYPE=local
    depends_on:
      - etcd

  # 现有 MySQL
  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"

  # 新增：Prometheus（监控数据源）
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
```

## 七、实现路线

### Phase 1：Agent 核心（1.5 周）
- [ ] 引入 AgentScope Java Maven 依赖
- [x] 实现 OpenAI 兼容 ChatModel 配置，当前通过 LangChain4j 接入大模型
- [~] 配置 Agent 对话 + BGE-large Embedding + Milvus 记忆
  - 已完成：知识库向量检索、Milvus 启动加载、Agent 可调用知识搜索。
  - 未完成：AgentScope/ReActAgent 尚未引入，仍是当前 Ops Agent 编排。
- [x] 实现 `KnowledgeSearchTool`（复用现有 KnowledgeService）
- [ ] 实现 `QueryMetricsTool`（对接 Prometheus HTTP API）
- [ ] 实现 `SearchLogsTool`（对接 ES/Loki API）
- [x] 基础 Ops Agent API（当前为 `/api/ops-agent/chat`，含 SSE）

### Phase 2：Skill 系统（1 周）
- [x] 实现 `SkillLoader`（YAML 解析 + 关键词匹配）
- [~] 实现 `SkillRunner`（步骤编排 + Tool 调用 + 变量替换）
  - 已完成：内置 Skill 加载、关键词触发、步骤级 SSE 事件和 ToolGateway 调用。
  - 未完成：变量 schema、dry-run、版本治理和前端编辑闭环。
- [x] 编写 3-5 个基础排查 Skill YAML
- [ ] 告警 Webhook API（`/api/agent/webhook/alert`）

### Phase 3：前端 + 经验沉淀（1.5 周）
- [x] 前端智能排查面板（对话式排查 + 排查历史）
- [ ] Skill 管理界面（列表/编辑/新增/删除）
- [ ] `SaveExperienceTool`（排查经验自动沉淀到知识库）
- [ ] 记忆折叠机制（上下文过长时自动压缩）
- [ ] 排查报告自动生成（Markdown 格式）

### Phase 4：检索增强（2 周）
- [ ] **重排序（Reranking）**：粗筛 top 50 → BGE Reranker 精排 → 取 top 5 送给 Agent
- [ ] **查询改写（Query Rewriting）**：用户原始问题 → GLM 改写为多个子查询 → 分别检索 → 合并结果
- [ ] **意图识别（Intent Recognition）**：判断用户输入是排查请求/知识查询/闲聊，路由到不同处理链路
- [ ] **混合检索（Hybrid Retrieval）**：向量检索 + 关键词检索，RRF 融合排序
- [ ] **切片策略优化**：切片大小调整、重叠切片、保留章节上下文前缀

### Phase 5：权限隔离与安全（2 周）
- [ ] **Agent 权限体系**：基于现有 RBAC（8 角色）扩展，不同角色可调用的 Tool 不同
  - 系统管理员：全部 Tool（含 ExecCommand）
  - 运维岗：只读 Tool（查监控/日志/知识库）+ ExecCommand（需审批）
  - 开发经理/运维经理：只读 Tool（查监控/日志/知识库）
  - 其他角色：仅 KnowledgeSearchTool
- [ ] **知识库查询权限隔离**：
  - 敏感文档（安全配置、密码策略等）按角色过滤，查询时注入权限过滤条件
  - Milvus metadata 中增加 `accessLevel` 字段，检索后按角色二次过滤
  - Agent 对话历史按用户隔离，不可跨用户查看
- [ ] **安全过滤（Safety Filter）**：
  - 输入过滤：拦截 Prompt Injection 攻击（检测系统提示词覆盖、角色伪装等模式）
  - 输出过滤：Agent 回复中脱敏（自动识别并遮蔽 IP、密码、密钥、内网地址等）
  - Tool 调用审计：所有 Tool 调用记录持久化，支持事后审计和异常检测
  - 速率限制：单用户/单角色 Agent 调用频率限制，防止滥用
- [ ] **敏感操作二次确认**：
  - ExecCommandTool 执行前生成操作预览，需人工审批（AgentScope Human-in-the-Loop）
  - 高危命令（rm、drop、restart）自动拦截，必须系统管理员审批

### Phase 6：智能进化（持续）
- [ ] `ExecCommandTool` + Human-in-the-Loop 审批
- [ ] 排查准确率统计 + 反馈循环
- [ ] 多 Agent 协作（A2A 协议，复杂问题拆分子任务）
- [ ] Skill 触发条件自动优化（基于历史数据）
- [ ] 排查经验自动沉淀为 Reranker 训练数据，持续优化排序

## 八、关键技术点

### 8.1 为什么选 AgentScope Java

| 维度 | AgentScope Java | QwenPaw (Python) | LangGraph4j |
|------|----------------|-------------------|-------------|
| 语言 | Java，集成到现有项目 | Python，需跨语言调用 | Java |
| 推理引擎 | ReActAgent 内置 | 内置 | 需自己实现 |
| 记忆系统 | Long-term Memory 内置 | 内置 | 需自己实现 |
| MCP 支持 | 原生 | 原生 | 无 |
| A2A 协议 | 原生（Nacos 注册） | CloudPaw 插件 | 无 |
| Human-in-the-Loop | 内置 Hook | 内置工具守卫 | 需自己实现 |
| 部署 | 集成到 Spring Boot | 独立 Python 服务 | 集成到 Spring Boot |
| 生产就绪 | GraalVM + OpenTelemetry | Docker | 需额外配置 |

### 8.2 DeepAgent 思想的落地

DeepAgent 的核心是**统一推理 + 记忆折叠**，在 AgentScope Java 中的落地方式：
- **统一推理**：ReActAgent + GLM-4.7 长上下文，系统提示词引导全局视角
- **记忆折叠**：自定义 AgentHook，上下文超过阈值时压缩为结构化摘要
- **工具动态发现**：Spring Bean 自动注册 + MCP 协议扩展

### 8.3 检索增强技术方案

**重排序（Reranking）**

```
用户问题 → BGE embedding → Milvus 粗筛 top 50
                                    ↓
                          BGE Reranker（交叉编码）
                                    ↓
                          精排 top 5 → 送给 Agent
```

- 模型：`bge-reranker-v2-m3`（本地 Ollama 或远程 API）
- 原理：Embedding 是压缩式表征，信息有损失；Reranker 对问题-文档逐对打分，精度更高
- 实现：新增 `RerankerService`，`KnowledgeSearchTool` 内部调用

**查询改写（Query Rewriting）**

```
用户："Redis 慢"
  ↓ GLM 改写
子查询 1："Redis 慢查询 slowlog 配置排查"
子查询 2："Redis 延迟 latency 分析"
子查询 3："Redis 性能优化 maxmemory-policy"
  ↓ 分别检索
合并结果（去重 + RRF 排序）
```

- 实现：新增 `QueryRewriteService`，用 GLM-4.7 生成 2-3 个子查询
- 适用于：用户问题太短、模糊、或包含多种可能含义

**意图识别（Intent Recognition）**

```
用户输入 → GLM 意图分类 → 路由到不同处理链路
  ├─ 排查请求："Redis 连接池满了" → Agent 排查流程
  ├─ 知识查询："Nginx 配置 keepalive" → 知识库检索直接返回
  ├─ 操作请求："帮我重启 Redis" → ExecCommandTool + 审批
  └─ 闲聊/无关："今天天气" → 礼貌拒绝
```

- 实现：Agent 系统提示词中加入意图分类指令，或用独立轻量模型做分类
- 好处：避免所有请求都走完整 Agent 推理，节省 token 和时间

### 8.4 权限隔离技术方案

**Agent Tool 权限控制**

```java
@Component
public class ToolPermissionFilter {

    private final PermissionService permissionService;

    public List<Tool> filterTools(String username, List<Tool> allTools) {
        Role role = permissionService.getRole(username);
        return allTools.stream()
            .filter(tool -> isAllowed(role, tool))
            .collect(Collectors.toList());
    }

    private boolean isAllowed(Role role, Tool tool) {
        return switch (role) {
            case SYS_ADMIN -> true;  // 全部权限
            case MIDDLEWARE_MGR, DATABASE_MGR, HOST_MGR,
                 NETWORK_MGR, SECURITY_MGR -> isReadOnly(tool) || "exec_command".equals(tool.name());
            case DEV_MGR, OPS_MGR -> isReadOnly(tool);
        };
    }
}
```

**知识库数据级权限隔离**

```java
// KnowledgeSearchTool 中注入权限过滤
public String call(Map<String, Object> params) {
    String query = (String) params.get("query");
    Role role = getCurrentUserRole();

    float[] vector = knowledgeService.embed(query);
    List<VectorSearchResult> results = knowledgeService.search(vector, 50);

    // 按角色过滤敏感文档
    results = results.stream()
        .filter(r -> {
            String accessLevel = r.getMetadata().get("accessLevel");
            return accessLevel == null
                || "public".equals(accessLevel)
                || role == Role.SYS_ADMIN
                || (role.isManager() && !"confidential".equals(accessLevel));
        })
        .limit(topK)
        .collect(Collectors.toList());

    return formatResults(results);
}
```

### 8.5 安全过滤技术方案

**输入过滤（Prompt Injection 防护）**

```java
@Component
public class InputSafetyFilter {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)ignore.*previous.*instructions"),
        Pattern.compile("(?i)you\\s+are\\s+now"),
        Pattern.compile("(?i)system\\s*prompt"),
        Pattern.compile("(?i)forget.*rules"),
        Pattern.compile("(?i)act\\s+as\\s+if")
    );

    public SafetyCheckResult check(String input) {
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return SafetyCheckResult.blocked("检测到潜在的 Prompt Injection 攻击");
            }
        }
        return SafetyCheckResult.passed();
    }
}
```

**输出过滤（敏感信息脱敏）**

```java
@Component
public class OutputSafetyFilter {

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
        Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),           // IP 地址
        Pattern.compile("(?i)(password|passwd|secret|token|key)\\s*[:=]\\s*\\S+"),  // 密码/密钥
        Pattern.compile("-----BEGIN.*PRIVATE KEY-----"),                // 私钥
        Pattern.compile("\\b[A-Za-z0-9+/]{40,}={0,2}\\b")             // Base64 密钥
    );

    public String filter(String output) {
        String filtered = output;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            filtered = pattern.matcher(filtered).replaceAll(m -> {
                String matched = m.group();
                if (matched.length() <= 8) return "***";
                return matched.substring(0, 4) + "***" + matched.substring(matched.length() - 4);
            });
        }
        return filtered;
    }
}
```

## 九、当前修复计划（2026-06-07）

### 9.1 本轮 P0 修复范围

1. **会话一致性**
   - 修复 Ops Agent 首次回复时用 `insert` 更新标题导致的会话分裂。
   - RAG 和 Ops 写入前必须校验会话存在、归属和模式。

2. **会话用户隔离**
   - `chat_sessions` 增加创建人字段。
   - 会话列表、会话详情、聊天写入、模式切换均按当前用户过滤。
   - 系统管理员可作为审计角色查看全部会话，普通用户只能查看自己的会话。

3. **Wiki/RAG 权限传递**
   - SSE 异步线程不再从 `SecurityContextHolder` 隐式取用户。
   - Controller 显式传入 `Authentication`，避免线程池复用导致权限上下文丢失。

4. **Skill 管理权限**
   - Skill 新增、编辑、删除、保存经验只允许系统管理员或专业管理员操作。
   - 普通登录用户只允许查看 Skill/Tool 列表和发起排查。

### 9.2 后续 P1/P2 修复方向

- 增加 `agent_tool_calls` 审计表，记录工具名、参数摘要、调用人、耗时、结果状态。
- 引入 ToolGateway，统一做权限校验、参数 schema、超时、脱敏和错误收敛。
- 将“保存经验”从直接写 Skill YAML 调整为生成 LLM Wiki `EXPERIENCE` DRAFT 页面，审核通过后再参与检索。
- 增加 Agent 状态机：`PLANNING / WAITING_FOR_PARAMS / RUNNING_TOOL / NEED_APPROVAL / SUMMARIZING / COMPLETED / FAILED / CANCELLED`。

### 9.3 本轮验收标准

- 普通用户无法读取或写入他人的排查会话。
- RAG Agent 调用 Wiki 搜索时使用当前请求用户权限。
- Ops Agent 首轮回答后刷新页面，问答仍在同一个会话下。
- 普通用户调用 Skill 写接口返回 403。
- 后端编译通过，关键 Controller/Service 单元测试覆盖新增权限逻辑。

## 十、目标架构优化方案（2026-06-08）

### 10.0 实施进度更新（2026-06-08）

本轮已先落地 P0 中“稳定和可观测”的第一批基础能力，提交为 `d8b9c96 Add agent tool gateway observability`。

最新进度补充（截至 2026-06-08）：

- 后端、前端已围绕 `/api/ops-agent/chat` 完成 SSE 回归，前端 loading 残留问题已修复。
- 本地启动配置已补齐默认 MySQL 密码提交 `ed2e5a7 Set default MySQL password for local startup`，减少重启后端时因 `APP_DB_PASSWORD` 缺失导致的启动失败。
- Tool/Skill 热插拔仍停留在设计和文档层，尚未形成前端可新增 Tool/Skill、dry-run、审核、启停、回滚的完整闭环。
- 当前智能排查仍偏固定 Skill 编排，尚未具备“接 CMDB 自动补齐服务实例/日志索引/监控模板”的生产级上下文能力。

已完成：

- `ToolResult` 已落地，工具调用结果统一包含 `success`、`errorCode`、`summary`、`data`、`confidence`、`latencyMs`。
- `ToolGateway` 已落地，现有 Skill 工具调用统一经过网关执行，并已支持工具异常收敛、调用审计、请求/响应脱敏和日志参数脱敏。
- `agent_tool_invocations` 已落库，用于记录工具名、请求摘要、响应摘要、状态、耗时和调用人。
- Ops Agent SSE 已增加步骤级事件：`run_started`、`step_started`、`tool_result`、`completed`。
- 前端智能排查面板已展示步骤进度，并在 `result`、`completed`、`error`、用户停止时清理 loading 状态。
- 已修复 SSE 异步响应完成后触发 Spring Security 二次拦截的问题，放行 `DispatcherType.ASYNC` 和 `DispatcherType.ERROR`。
- 已补充回归测试：工具成功/失败事件、审计落库、敏感字段脱敏、Ops Agent 权限测试。
- 已用 `mwadmin` 完成真实 SSE 回归，请求事件链为 `run_started -> step_started -> tool_result -> result -> completed`，审计表记录同步增加。

部分完成：

- `ToolGateway` 目前已覆盖执行、审计、脱敏和错误收敛；权限分级、参数 schema 校验、工具级超时和重试策略仍需继续实现。
- `agent_tool_invocations` 已实现；`agent_runs`、`agent_steps`、`agent_evidence` 尚未实现，因此完整排查回放还没有闭环。
- `knowledge_search` 已通过统一网关；`query_metrics`、`search_logs` 仍因缺少 `service` 上下文返回缺参，需要后续接入 CMDB/上下文抽取自动补齐。

本轮验证结果：

- `mvn -Dtest=AgentServiceTest,OpsAgentControllerSecurityTest test` 通过。
- `npm run build` 通过，仅存在既有 chunk size warning。
- `mvn clean package -DskipTests` 通过，仅存在既有 deprecation warning。
- 后端 `8080`、前端 `5173` 已重启并通过 smoke 测试。

遗留风险：

- Milvus 搜索偶发 `Encountered end-of-stream mid-frame`，客户端重试后本轮请求可恢复完成；后续建议增加向量检索降级、超时和失败事件显式返回。
- 当前 Skill 对服务名、环境、时间范围的抽取能力不足，用户只描述现象时，监控和日志工具无法自动补齐参数。

### 10.1 目标定位

下一阶段的目标不是继续堆固定问答模板，而是把当前 Ops Agent 升级为一个可审计、可扩展、可热插拔的线上问题自动排查系统。

目标能力：

1. 输入告警、异常现象或服务名后，自动补齐排查上下文。
2. 接入日志、监控、CMDB、知识库和后续自动化平台。
3. 支持 Skill 和 Tool 热插拔，新增排查能力尽量不改核心 Agent 代码。
4. 每一步工具调用、证据、结论都可回放、审计和复盘。
5. 写操作必须人工确认，只读诊断可以自动执行。

### 10.2 当前设计评估

当前实现已经具备 MVP 基础：

- `SkillLoader` 支持内置 YAML Skill 和外部目录 Skill。
- `Tool` 使用 Spring Bean 注册，开发 Java 工具较简单。
- `AgentService` 能匹配 Skill、顺序执行步骤并调用 LLM 总结。
- 已有知识库检索、Zabbix 查询、SSE 对话和会话记录。

但它更接近“Skill 驱动的排查脚本 + LLM 总结器”，还不是完整自动排查 Agent：

- Skill 是固定流程，缺少 ReAct 式动态规划和分支决策。
- Tool 只能通过 Spring Bean 静态注册，新增工具仍需发版重启。
- Tool 只返回字符串，缺少结构化结果、错误码、证据和置信度。
- 日志和 Prometheus 工具仍是占位实现，CMDB 工具缺失。
- 缺少排查运行态模型，无法完整回放每次工具调用和证据链。
- 缺少工具权限分级、人工确认、超时、熔断和脱敏的统一网关。

### 10.3 推荐目标架构

建议拆成五层：

```text
用户问题 / 告警 Webhook
        |
        v
Incident Context 层
  - 抽取 service/env/host/timeRange/symptom/severity
  - 调用 CMDB 补齐实例、owner、依赖、日志索引、监控模板
        |
        v
Planner 层
  - Skill 匹配优先
  - Skill 不完整时由 LLM 生成或补全排查计划
  - 输出结构化 Plan，而不是直接自由文本回答
        |
        v
Tool Gateway + Tool Registry
  - 权限、参数 schema、超时、重试、脱敏、审计
  - Java / HTTP / MCP / Script 多种执行器
        |
        v
Evidence 层
  - 保存工具结果、日志片段、监控摘要、CMDB 拓扑、知识库引用
        |
        v
Summary 层
  - 基于 Evidence 输出根因候选、置信度、影响范围、修复建议
```

### 10.4 核心领域模型

建议新增以下核心表或等价持久化模型。

#### 10.4.1 `agent_runs`

记录一次完整排查任务。

| 字段 | 说明 |
|------|------|
| `id` | 排查运行 ID |
| `session_id` | 所属会话 |
| `created_by` | 发起人 |
| `mode` | `SKILL` / `REACT` / `HYBRID` |
| `status` | `PLANNING` / `WAITING_FOR_PARAMS` / `RUNNING` / `NEED_APPROVAL` / `COMPLETED` / `FAILED` / `CANCELLED` |
| `input_text` | 用户原始问题 |
| `context_json` | 结构化排查上下文 |
| `plan_json` | 结构化排查计划 |
| `final_answer` | 最终回答 |
| `started_at` / `finished_at` | 开始和结束时间 |

#### 10.4.2 `agent_steps`

记录计划中的每个步骤。

| 字段 | 说明 |
|------|------|
| `id` | 步骤 ID |
| `run_id` | 关联排查运行 |
| `step_order` | 步骤序号 |
| `step_type` | `TOOL` / `PROMPT` / `ASK_USER` / `APPROVAL` |
| `name` | 步骤名称 |
| `status` | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `SKIPPED` |
| `input_json` | 步骤输入 |
| `output_json` | 步骤输出 |
| `error_code` / `error_message` | 失败原因 |
| `duration_ms` | 耗时 |

#### 10.4.3 `agent_tool_invocations`

记录每一次工具调用，作为审计和排障依据。

| 字段 | 说明 |
|------|------|
| `id` | 调用 ID |
| `run_id` / `step_id` | 关联运行和步骤 |
| `tool_name` / `tool_version` | 工具名和版本 |
| `executor_type` | `JAVA` / `HTTP` / `MCP` / `SCRIPT` |
| `request_json` | 脱敏后的请求 |
| `response_json` | 脱敏后的响应 |
| `status` | `SUCCESS` / `FAILED` / `TIMEOUT` / `FORBIDDEN` |
| `latency_ms` | 耗时 |
| `created_by` | 调用人 |

#### 10.4.4 `agent_evidence`

记录最终回答可引用的证据。

| 字段 | 说明 |
|------|------|
| `id` | 证据 ID |
| `run_id` | 关联排查运行 |
| `source_type` | `CMDB` / `METRICS` / `LOGS` / `ALERTS` / `WIKI` / `COMMAND` |
| `source_name` | 数据源名称 |
| `summary` | 证据摘要 |
| `raw_ref` | 原始数据引用，如日志查询 ID、指标查询 ID |
| `confidence` | 证据可信度 |
| `sensitive_level` | 敏感级别 |

### 10.5 Tool Registry 设计

当前 `Tool` 只有 `name/description/call`，下一阶段应升级为注册表驱动。

#### 10.5.1 Tool 元数据

```yaml
name: query_logs
version: 1.0.0
description: 查询日志系统中的日志
executor_type: HTTP
permission_level: READ_SENSITIVE
timeout_ms: 10000
retry:
  max_attempts: 2
  backoff_ms: 500
input_schema:
  type: object
  required: [service, query, timeRange]
  properties:
    service:
      type: string
    query:
      type: string
    timeRange:
      type: string
    level:
      type: string
output_schema:
  type: object
  properties:
    summary:
      type: string
    records:
      type: array
enabled: true
owner: ops-platform
```

#### 10.5.2 Tool 执行器类型

| 类型 | 用途 | 是否需重启 |
|------|------|------------|
| `JAVA` | 高性能内置工具，如知识库、Zabbix | 需要发版 |
| `HTTP` | 调用日志、CMDB、工单、自动化平台 API | 不需要 |
| `MCP` | 接外部 MCP Server | 通常不需要 |
| `SCRIPT` | 受控脚本工具，适合临时诊断 | 不需要，但必须受限 |

#### 10.5.3 Tool Gateway 职责

所有工具调用必须经过 Tool Gateway：

- 参数 schema 校验。
- 当前用户和角色权限校验。
- 工具级超时和重试。
- 请求和响应脱敏。
- 调用审计落库。
- 错误码统一收敛。
- 写操作触发人工确认。
- 返回结构化 `ToolResult`。

建议 `ToolResult` 结构：

```json
{
  "success": true,
  "errorCode": null,
  "summary": "过去 1 小时 CPU 峰值 97%，持续 12 分钟",
  "data": {},
  "evidence": [],
  "confidence": 0.82,
  "latencyMs": 342
}
```

### 10.6 Skill 设计升级

当前 Skill YAML 只包含关键词和顺序步骤。下一阶段需要增加版本、输入参数、适用范围、依赖工具、输出要求和风控信息。

建议 Skill 格式：

```yaml
name: redis-cpu-high
version: 1.2.0
status: ACTIVE
owner: middleware-team
description: Redis CPU 高排查
scope:
  categories: ["中间件"]
  products: ["redis"]
trigger:
  keywords: ["redis cpu高", "redis cpu飙高", "redis cpu high"]
inputs:
  required: ["service"]
  optional: ["env", "timeRange"]
defaults:
  timeRange: "1h"
steps:
  - id: resolve_context
    type: tool
    tool: cmdb_query_service
    args:
      service: "{{service}}"
    output: service_context

  - id: query_cpu_metric
    type: tool
    tool: query_metrics
    args:
      service: "{{service}}"
      metric: "cpu"
      timeRange: "{{timeRange}}"
    output: cpu_metric

  - id: query_slowlog
    type: tool
    tool: search_logs
    args:
      service: "{{service}}"
      query: "slowlog OR latency OR commandstats"
      timeRange: "{{timeRange}}"
    output: log_evidence

  - id: summarize
    type: prompt
    prompt: |
      基于 service_context、cpu_metric、log_evidence 和知识库证据，
      输出根因候选、证据、置信度、影响范围和修复建议。
risk:
  write_actions: false
  requires_approval: false
```

Skill 发布流程：

1. 新建或编辑 Skill 生成 `DRAFT`。
2. 运行 dry-run，验证 schema、工具存在、参数可解析。
3. 管理员审核后变为 `ACTIVE`。
4. 保存历史版本，可回滚。
5. 禁用后不再参与匹配，但历史排查仍可回放。

### 10.7 CMDB 接入设计

CMDB 应作为自动排查的上下文入口，而不是普通工具。

首批 CMDB 能力：

| Tool | 输入 | 输出 |
|------|------|------|
| `cmdb_query_service` | `service` | 服务 ID、名称、owner、等级、环境 |
| `cmdb_query_instances` | `service/env` | 主机/IP/容器/集群/状态 |
| `cmdb_query_dependencies` | `service` | 上下游依赖、端口、协议 |
| `cmdb_query_observability` | `service` | 日志索引、监控模板、告警规则 |

Agent 首步应做：

1. 从用户输入抽取服务名和时间范围。
2. 如果服务名不明确，向用户追问或列出候选服务。
3. 通过 CMDB 补齐实例、owner、日志索引、监控指标模板。
4. 后续日志/监控工具使用 CMDB 输出作为参数来源。

### 10.8 日志和监控接入设计

#### 10.8.1 日志工具

建议先支持 Loki 或 Elasticsearch 其中一种，抽象统一接口：

```text
query_logs(service, query, timeRange, level, limit)
```

返回内容必须包含：

- 命中条数。
- 时间范围。
- Top N 日志片段。
- 聚类摘要，如相同异常堆栈聚合。
- 是否命中 ERROR/WARN。
- 原始查询引用 ID。

#### 10.8.2 监控工具

统一 Prometheus 和 Zabbix 输出：

```text
query_metrics(service, metric, timeRange, aggregation)
```

返回内容必须包含：

- 当前值、最大值、平均值、P95。
- 异常时间窗口。
- 与基线对比。
- 是否超过阈值。
- 图表或查询引用 ID。

### 10.9 Agent 编排策略

建议采用混合模式：

1. **Skill 优先**：命中明确 Skill 时，先使用 Skill 作为主流程，保证生产可控。
2. **LLM 补全**：Skill 缺参数或缺步骤时，LLM 只负责补全计划，不直接执行高风险操作。
3. **ReAct 辅助**：未命中 Skill 时，进入受限 ReAct：每轮只能调用只读工具，最多 N 步。
4. **证据驱动总结**：最终回答必须引用 Evidence，不允许只凭模型常识下结论。
5. **人工确认**：写操作、重启、变更配置、执行命令必须暂停到 `NEED_APPROVAL`。

推荐状态机：

```text
CREATED
  -> PLANNING
  -> WAITING_FOR_PARAMS
  -> RUNNING_TOOL
  -> SUMMARIZING
  -> COMPLETED

异常分支：
  -> NEED_APPROVAL
  -> FAILED
  -> CANCELLED
```

### 10.10 前端交互优化

前端不应只显示最终回答，还应显示排查过程：

- 当前状态：规划中、查 CMDB、查监控、查日志、总结中。
- 每个步骤的开始、成功、失败和耗时。
- 工具调用的摘要结果。
- 最终回答引用的证据。
- 缺少参数时显示表单，而不是让模型长篇提示。
- 写操作展示审批卡片：操作内容、风险、回滚方案、确认按钮。

SSE 事件建议：

| 事件 | 说明 |
|------|------|
| `run_started` | 排查开始 |
| `plan_created` | 计划生成 |
| `step_started` | 步骤开始 |
| `tool_result` | 工具结果 |
| `need_input` | 缺少参数 |
| `need_approval` | 需要人工确认 |
| `final` | 最终回答 |
| `error` | 失败 |
| `completed` | 流结束 |

前端收到 `final/error/completed` 任一事件后必须清理 loading 状态。

### 10.11 前端 Tool / Skill 管理设计

为了让运维人员可以在前端页面新增 Tool 和 Skill，建议把当前 Diagnostics 页里的 Skill 管理升级为独立的“Agent 能力中心”。它不只是 YAML 编辑器，而是面向生产排查能力的配置、测试、审核和发布入口。

#### 10.11.1 页面结构

建议新增或拆分以下页面：

| 页面 | 用途 | 主要用户 |
|------|------|----------|
| `Tool 管理` | 注册、测试、启停外部 Tool | 系统管理员、专业管理员 |
| `Skill 编排` | 用可视化步骤编排排查流程 | 专业管理员 |
| `发布审核` | 审核 Tool / Skill 变更 | 系统管理员、分类管理员 |
| `运行记录` | 查看 Agent Run、Step、Tool 调用和证据 | 运维人员、管理员 |
| `能力市场` | 查看已启用 Tool 和 Skill，支持复制为新版本 | 全部登录用户只读 |

#### 10.11.2 Tool 管理表单

Tool 新增不应要求写 Java 代码。前端优先支持 HTTP Tool，后续再支持 MCP Tool 和受控 Script Tool。

Tool 表单字段：

| 字段 | 说明 |
|------|------|
| `name` | 工具唯一名，如 `query_loki_logs` |
| `displayName` | 页面展示名 |
| `version` | 版本号 |
| `executorType` | `HTTP` / `MCP` / `SCRIPT` / `JAVA` |
| `description` | 给 LLM 和管理员看的说明 |
| `permissionLevel` | `READ_PUBLIC` / `READ_INTERNAL` / `READ_SENSITIVE` / `WRITE_SAFE` / `WRITE_RISKY` |
| `endpoint` | HTTP Tool 的 URL |
| `method` | `GET` / `POST` |
| `authType` | `NONE` / `BASIC` / `BEARER` / `HEADER` |
| `timeoutMs` | 超时时间 |
| `retryPolicy` | 重试次数和退避 |
| `inputSchema` | JSON Schema，生成动态参数表单 |
| `outputSchema` | JSON Schema，约束工具输出 |
| `requestTemplate` | 请求模板，引用输入参数 |
| `responseMapping` | 响应映射，提取 summary/data/evidence |
| `maskRules` | 敏感字段脱敏规则 |
| `enabled` | 是否启用 |

HTTP Tool 示例：

```yaml
name: query_loki_logs
displayName: Loki 日志查询
version: 1.0.0
executorType: HTTP
permissionLevel: READ_SENSITIVE
endpoint: https://loki.example.com/loki/api/v1/query_range
method: GET
timeoutMs: 10000
inputSchema:
  type: object
  required: [service, query, timeRange]
  properties:
    service:
      type: string
      title: 服务名
    query:
      type: string
      title: 查询关键字
    timeRange:
      type: string
      title: 时间范围
      default: 1h
requestTemplate:
  query: '{service="{{service}}"} |= "{{query}}"'
  limit: 100
responseMapping:
  summary: "$.data.result[*].stream"
  records: "$.data.result[*].values"
maskRules:
  - field: records
    pattern: "(?i)(password|token|secret)=\\S+"
```

#### 10.11.3 Tool 测试与发布流程

前端保存 Tool 之前必须提供 dry-run：

1. 管理员填写 Tool 基本信息和 schema。
2. 点击“测试调用”，前端根据 `inputSchema` 生成参数表单。
3. 后端通过 ToolGateway 执行 dry-run，返回结构化 `ToolResult`。
4. 页面展示请求摘要、响应摘要、脱敏后原始响应、耗时和错误码。
5. 通过 dry-run 后才能提交审核。
6. 审核通过后状态从 `DRAFT` 变为 `ACTIVE`。

Tool 状态机：

```text
DRAFT -> TESTED -> PENDING_REVIEW -> ACTIVE
   |         |             |
   v         v             v
FAILED    DISABLED      REJECTED
```

#### 10.11.4 Skill 可视化编排器

Skill 不建议继续只暴露 YAML。前端应提供步骤编排器：

- 左侧：可用 Tool 列表，按 CMDB、监控、日志、知识库、自动化分类。
- 中间：排查流程画布或步骤列表。
- 右侧：当前步骤参数配置。
- 底部：dry-run、保存草稿、提交审核、发布、回滚。

步骤类型：

| 类型 | 用途 |
|------|------|
| `TOOL` | 调用 ToolRegistry 中的工具 |
| `PROMPT` | 基于前面证据做 LLM 总结 |
| `ASK_USER` | 缺少参数时向用户追问 |
| `CONDITION` | 根据上一步结果分支 |
| `APPROVAL` | 高风险操作前人工确认 |

Skill 步骤参数应支持引用：

```text
{{input.service}}                  用户输入
{{context.env}}                    CMDB 补齐上下文
{{steps.query_cpu_metric.max}}     上一步工具结果
{{evidence.logs.errorCount}}       证据摘要
```

#### 10.11.5 Skill 校验规则

提交 Skill 前必须校验：

- 名称、版本、owner、适用分类不能为空。
- 所有引用的 Tool 必须存在且已启用。
- 所有 Tool 参数必须满足 `inputSchema`。
- `{{...}}` 引用必须可解析，或标记为运行时必填。
- 高风险 Tool 前必须有 `APPROVAL` 步骤。
- 最终必须有 `PROMPT` 或 `SUMMARY` 步骤。
- dry-run 必须至少覆盖一个样例输入。

#### 10.11.6 前端接口建议

建议新增 API：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/ops-agent/tools` | 查询 Tool 列表 |
| `POST` | `/api/ops-agent/tools` | 新建 Tool 草稿 |
| `PUT` | `/api/ops-agent/tools/{name}` | 更新 Tool 草稿 |
| `POST` | `/api/ops-agent/tools/{name}/dry-run` | Tool 测试调用 |
| `POST` | `/api/ops-agent/tools/{name}/submit` | 提交审核 |
| `POST` | `/api/ops-agent/tools/{name}/approve` | 审核通过 |
| `POST` | `/api/ops-agent/tools/{name}/disable` | 禁用 Tool |
| `GET` | `/api/ops-agent/skills` | 查询 Skill 列表 |
| `POST` | `/api/ops-agent/skills` | 新建 Skill 草稿 |
| `PUT` | `/api/ops-agent/skills/{name}/versions/{version}` | 更新 Skill 版本 |
| `POST` | `/api/ops-agent/skills/{name}/dry-run` | Skill 测试运行 |
| `POST` | `/api/ops-agent/skills/{name}/submit` | 提交审核 |
| `POST` | `/api/ops-agent/skills/{name}/approve` | 审核通过 |
| `POST` | `/api/ops-agent/skills/{name}/rollback` | 回滚版本 |

#### 10.11.7 前端权限

| 能力 | 普通用户 | 专业管理员 | 系统管理员 |
|------|----------|------------|------------|
| 查看 Tool / Skill | 允许 | 允许 | 允许 |
| dry-run 只读 Tool | 禁止 | 允许 | 允许 |
| 新建/编辑 Skill 草稿 | 禁止 | 允许本分类 | 允许全部 |
| 新建/编辑 Tool 草稿 | 禁止 | 允许本分类只读 Tool | 允许全部 |
| 发布审核 | 禁止 | 允许本分类 | 允许全部 |
| 高风险 Tool | 禁止 | 提交审批 | 审批后执行 |

### 10.12 安全和合规要求

工具权限分级：

| 等级 | 示例 | 执行策略 |
|------|------|----------|
| `READ_PUBLIC` | 查询公开知识库 | 自动执行 |
| `READ_INTERNAL` | 查询 CMDB、普通监控 | 自动执行，记录审计 |
| `READ_SENSITIVE` | 查询日志、敏感配置 | 角色校验，结果脱敏 |
| `WRITE_SAFE` | 创建工单、保存经验草稿 | 人工确认或管理员权限 |
| `WRITE_RISKY` | 重启服务、变更配置 | 必须审批、记录回滚方案 |
| `DESTRUCTIVE` | 删除数据、清空缓存 | 默认禁止 |

必须实现：

- Tool 输入输出脱敏。
- Tool 参数白名单和 schema 校验。
- 用户、角色、时间、参数摘要、结果状态审计。
- 只读命令白名单。
- 高风险操作二次确认。
- Prompt Injection 检测和工具指令隔离。

### 10.13 分阶段实施路线

#### P0：稳定和可观测

- [ ] 新增 `agent_runs`、`agent_steps`、`agent_tool_invocations`、`agent_evidence`。
- [x] 新增 `agent_tool_invocations`，记录工具调用审计。
- [x] Tool 返回结构化 `ToolResult`。
- [~] ToolGateway 统一超时、重试、错误码、审计。
  - 已完成：统一执行入口、异常收敛、调用审计、请求/响应脱敏、日志脱敏。
  - 未完成：参数 schema 校验、工具级超时、重试策略、权限分级、人工确认。
- [x] SSE 增加步骤级事件，前端展示执行进度。
- [x] 修复工具异常或 SSE 流结束后前端残留 loading 的问题。
- [x] 修复 SSE 异步响应完成后触发 Spring Security 二次拦截的问题。

验收标准：

- [x] 任意工具失败都能返回明确步骤失败事件。
- [ ] 每次排查可在数据库中完整回放。
  - 当前只能回放工具调用审计，缺少 Run、Step、Evidence。
- [x] 前端不会因为 SSE 未正常关闭而残留 loading。

#### P1：接入真实数据源

- 实现 CMDB Tool：服务、实例、依赖、可观测配置。
- 实现日志 Tool：Loki 或 Elasticsearch 先接一种。
- [~] 统一 Zabbix/Prometheus 监控结果结构。
  - 已完成：Zabbix Client、ZabbixTool、Zabbix 导出接口和内置 `zabbix-monitor` Skill。
  - 未完成：Prometheus 接入、统一指标 schema、与 CMDB 自动补参联动。
- Skill 参数可引用 CMDB 输出和上一步工具输出。

验收标准：

- 用户只输入服务名和现象，Agent 能自动补齐实例、日志索引和监控查询。
- Redis CPU 高、连接池耗尽、磁盘满、OOM 至少 4 个场景可自动跑完只读排查链路。

#### P2：热插拔和治理

- 建立 Tool Registry。
- 支持 HTTP Tool 热注册。
- Skill 支持版本、启停、审核、dry-run 和回滚。
- 前端 Skill 编辑器改为工具选择 + 参数表单。
- 前端 Tool 管理支持 HTTP Tool 新增、测试、提交审核、启停。

验收标准：

- 新增一个 HTTP Tool 不需要重启后端。
- 新增 Skill 必须通过 dry-run 才能启用。
- 禁用 Skill 后不影响历史排查回放。
- 管理员可以在前端新增一个只读 HTTP Tool，并在 Skill 编排器中引用它完成 dry-run。

#### P3：智能规划和经验沉淀

- 引入受限 ReAct 模式。
- 未命中 Skill 时自动生成排查计划。
- 好回答生成 `EXPERIENCE` 草稿，审核后进入 Wiki 或 Skill。
- 形成问题类型、根因、证据、处置动作的数据闭环。

验收标准：

- 未命中 Skill 的常见问题也能自动规划只读排查。
- 最终回答包含根因候选、证据、置信度和下一步动作。
- 经验沉淀必须经过人工审核。

### 10.14 推荐下一步

P0 第一批能力已经完成。下一轮建议继续补齐 P0 剩余闭环，再进入 P1 数据源接入：

1. 新增 `agent_runs`、`agent_steps`、`agent_evidence`，把一次排查从会话消息升级为可回放的运行记录。
2. 为 `ToolGateway` 补参数 schema 校验、工具级超时、重试策略、权限分级和人工确认。
3. 增加 Incident Context 抽取，先从用户输入中解析 `service/env/timeRange/symptom`。
4. 接入 CMDB 查询服务实例、日志索引、监控模板，用 CMDB 输出自动补齐 `query_metrics`、`search_logs` 参数。
5. 给 Milvus/知识检索增加超时、降级和失败事件，避免外部向量库抖动拖慢整个 SSE。
6. 再进入 Tool Registry 和前端 Tool/Skill 管理，实现 HTTP Tool 热插拔和 Skill dry-run 审核。

这一步完成后，再接 CMDB、日志和监控会更稳，也更容易排查线上问题。
