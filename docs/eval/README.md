# 知识库质量评价

分三层，**不能混着看**——混了无法归因：Recall 高但答案差 = prompt 问题；Recall 低 = 解析/切片/embedding 问题。

## 1. 检索层（主战场，确定性、可自动跑）

```bash
export MRM_TOKEN=<用 /test-login skill 获取>
python3 scripts/eval-retrieval.py --k 5 --json .scratch/eval-$(date +%Y%m%d).json
```

**前置条件**（缺一不可，否则脚本会以非零退出并提示，不要把连通性问题误读成质量结论）：

- Milvus 已启动（`deploy/milvus-offline/docker-compose.yml`）
- Embedding 模型可达（`EMBEDDING_BASE_URL`，本地默认 Ollama `:11434`）
- MySQL `middleware_resource_manager` 可连
- 后端 ai-service + 网关已起（`/restart` skill）
- 知识库里**已经导入文档**——空库跑出来的 0% 没有意义

主指标 **Recall@5**，辅以 MRR。脚本按两个维度分桶：

| 维度 | 桶 | 为什么重要 |
|---|---|---|
| `query_type` | `exact_param` / `error_code` / `command` / `semantic` / `cross_doc` | 前三类是精确匹配场景，稠密向量召回最弱，是混合检索改造的**主要收益来源**。改造前后对比这三桶最能说明问题 |
| `source_format` | `pdf` / `docx` / `doc` / `xlsx` / `md` / `standards` / `wiki` | 直接量化各格式解析质量差距。docx 是基准线（解析质量最好），pdf 与 xlsx 与它的差值就是解析层改造的验收依据 |

**Golden set 在 `docs/eval/golden-set.json`。当前只有 10 条种子模板，不构成任何质量结论。**
真实条目必须从团队工单、群聊、实际排查场景里捞，目标 50~100 条。没有它，任何检索改动都无法判断是变好还是变坏。

## 2. 生成层（检索层达标后再做）

行业已收敛到 **RAGAS** 的四个核心指标，DeepEval / TruLens / ARES 与 LangSmith / Arize Phoenix 等平台实现同一套：

| 指标 | 含义 | 常用门槛 |
|---|---|---|
| **Faithfulness** 忠实度 | 答案是否只基于检索到的内容，无编造 | ≥ 0.75 |
| **Answer Relevancy** 答案相关性 | 答案是否真的回答了问题 | ≥ 0.80 |
| **Context Precision** 上下文精确率 | 检索回来的内容有多少是相关的 | ≥ 0.70 |
| **Context Recall** 上下文召回率 | 需要的内容检索回来多少 | ≥ 0.80 |

**Context Recall 最该先盯**——它直接反映切片和解析做得好不好，且不受 LLM 生成质量干扰。

20 人规模用 LLM-as-judge 足够，不必人工全标。RAGAS 支持从自有语料自动生成测试集，能省掉大部分标注工作。

## 3. 语料健康层（真正的瓶颈所在）

| 指标 | 口径 | 现状 |
|---|---|---|
| **覆盖率矩阵** | 22 类软件 × 4 类标准（参数/部署/监控/应急）≈ 80 个格子填了多少 | 待建。**这个矩阵本身就是团队的内容路线图** |
| 参数结构化率 | `standard_parameters` 已录入条数 / 应录入条数 | 接近 0（表几乎是空的） |
| 时效性 | 软件版本已升级但标准未更新的条目数 | 待建 |
| 参数矛盾 | 同一参数在不同文档给出不同值 | `LintAgent` 已有 `CONTRADICTION` 骨架可扩展 |
| 切片健康度 | 超短切片比例、被截断表格数、无 `sectionPath` 的切片数 | 待建 |

## 4. 线上信号（免费且最真实）

- **无结果率**：检索 Top1 分数 < 阈值的请求占比
- **重问率**：同一会话内用户换着说法再问的比例
- **点踩率**：`DiagnosticsPanel` 加一个「有帮助/没帮助」按钮即可开始积累

## 已知会拉低分数的因素（改造过程中的预期现象）

- `wiki_pages` 已按决策清空，`source_format=wiki` 桶会全挂，等团队写入经验后恢复
- `standard_parameters` 尚未录入，`exact_param` 桶目前只能靠 RAG 碰运气；录入后这类问题应改由精确 SQL 回答，准确率从「看运气」变成 100%
