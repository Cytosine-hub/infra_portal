package com.middleware.manager.wiki.service;

/**
 * Ingest 编译的两步 Chain-of-Thought Prompt 模板。
 * Step 1: 结构化分析（提取实体、概念、关系、矛盾）
 * Step 2: Wiki 页面生成（基于分析结果生成结构化 Markdown）
 *
 * 使用 {@code {{PLACEHOLDER}}} 占位符替代 String.formatted()，
 * 避免用户内容中的 %s/%d 等格式化字符被错误消费。
 */
public class IngestPromptTemplates {

    /**
     * Step 1: 结构化分析 Prompt
     */
    public static String buildAnalysisPrompt(String documentContent, String existingPagesSummary, String softwareReference) {
        return """
            你是银行基础架构运维知识库的编译器。分析以下文档，提取结构化知识。

            ## 输入文档
            {{DOCUMENT_CONTENT}}

            ## 已有 Wiki 页面（用于矛盾检测）
            {{EXISTING_PAGES}}

            ## 已知软件分类参考（必须严格参照此表确定 category）
            {{SOFTWARE_REFERENCE}}

            ## 输出规则
            1. 只提取文档中明确提到的实体，不要推断
            2. 版本号必须精确提取，不要省略
            3. 矛盾检测：如果新文档的建议与已有页面冲突，必须标记，即使你不确定
            4. category 必须从参考表中匹配，如果文档中的软件不在表中，根据软件性质推断最合适的分类
            5. 输出必须是合法 JSON，不要包含任何其他文字

            ## 输出 JSON 格式
            {
              "entities": [
                {
                  "name": "实体名称（含版本号）",
                  "type": "software/infrastructure/system",
                  "category": "中间件/数据库/主机/网络/安全",
                  "software": "软件名",
                  "version": "版本号",
                  "facts": ["关键事实1", "关键事实2"]
                }
              ],
              "concepts": [
                {
                  "name": "概念名称",
                  "description": "概念描述",
                  "related": ["相关实体1", "相关实体2"]
                }
              ],
              "dependencies": [
                {
                  "from": "源",
                  "to": "目标",
                  "type": "DEPENDS_ON/REFERENCES/RELATED"
                }
              ],
              "contradictions": [
                {
                  "existing_page": "已有页面标题",
                  "conflict": "冲突描述",
                  "reason": "原因分析",
                  "confidence": 0.8
                }
              ]
            }
            """.replace("{{DOCUMENT_CONTENT}}", documentContent)
              .replace("{{EXISTING_PAGES}}", existingPagesSummary)
              .replace("{{SOFTWARE_REFERENCE}}", softwareReference);
    }

    /**
     * Step 2: Wiki 页面生成 Prompt
     */
    public static String buildPageGenerationPrompt(String documentContent, String analysisJson) {
        return """
            你是银行基础架构运维知识库的编译器。根据以下分析结果，生成 Wiki 页面。

            ## 原始文档
            {{DOCUMENT_CONTENT}}

            ## 分析结果
            {{ANALYSIS_JSON}}

            ## 页面生成规则
            1. 每个实体/概念生成一个独立的 Wiki 页面
            2. 页面内容使用 Markdown 格式
            3. 用 [[页面名]] 标记交叉引用（引用其他实体或概念）
            4. 每个页面必须包含：标题、摘要（一句话）、正文
            5. 正文结构：概述 → 关键配置/要点 → 常见问题 → 关联知识
            6. 如果发现矛盾，在页面顶部用 > ⚠️ 矛盾提示 标注

            ## 输出 JSON 格式
            {
              "pages": [
                {
                  "title": "页面标题",
                  "page_type": "ENTITY/CONCEPT/RUNBOOK/EXPERIENCE/STANDARD",
                  "category": "中间件/数据库/主机/网络/安全",
                  "software": "软件名（如有）",
                  "version": "版本号（如有）",
                  "summary": "一句话摘要",
                  "content": "完整的 Markdown 内容，含 [[wikilink]] 交叉引用"
                }
              ],
              "source_summary": {
                "title": "原始文档摘要",
                "content": "文档内容的简要概述"
              }
            }
            """.replace("{{DOCUMENT_CONTENT}}", documentContent)
              .replace("{{ANALYSIS_JSON}}", analysisJson);
    }

    public static String buildSectionFactsPrompt(String outlineJson) {
        return """
            你是银行基础架构运维知识库的章节事实抽取器。根据文档目录和章节摘录抽取事实。

            ## 文档目录和章节摘录
            {{OUTLINE_JSON}}

            ## 输出规则
            1. 只抽取章节摘录中明确出现的事实，不要编造。
            2. 每个输入中的 section_id 必须返回一条 section_facts，不要返回输入之外的 section。
            3. 操作步骤、配置项、指标、故障处理、强制条款必须保留。
            4. 信息不足时输出简短事实，不要扩写。
            5. 输出必须是合法 JSON，不要包含任何其他文字。

            ## 输出 JSON 格式
            {
              "section_facts": [
                {
                  "section_id": "sec-001",
                  "section_path": "章节路径",
                  "facts": ["事实1", "事实2"],
                  "operations": [
                    {"step": 1, "action": "操作", "command": "命令，如无则为空", "evidence": "原文短摘录"}
                  ],
                  "config_items": [
                    {"name": "参数名", "default_value": "默认值", "description": "说明"}
                  ],
                  "warnings": ["注意事项"],
                  "entities": ["实体1"]
                }
              ]
            }
            """.replace("{{OUTLINE_JSON}}", outlineJson);
    }

    public static String buildPagePlanPrompt(String outlineJson, String sectionFactsJson,
                                             String existingPagesSummary, String softwareReference) {
        return """
            你是银行基础架构运维知识库的页面规划器。根据文档目录和章节事实生成 page_plan。

            ## 文档目录
            {{OUTLINE_JSON}}

            ## 章节事实
            {{SECTION_FACTS_JSON}}

            ## 已有 Wiki 页面
            {{EXISTING_PAGES}}

            ## 已知软件分类参考
            {{SOFTWARE_REFERENCE}}

            ## 规划规则
            1. 每个 required section 必须映射到至少一个页面。
            2. 多个连续小节可以合并为一个页面，但不能把高价值章节压缩成泛泛概述。
            3. 页面标题不要包含软件名和版本号（这些信息由 category/software 标签承载），标题只描述本页面核心内容。
            4. 标题不能是”安装方式””产品配置””参数说明””问题处理”这类泛标题。
            5. 步骤类内容优先 RUNBOOK；参数和规范类内容优先 STANDARD；故障类内容优先 EXPERIENCE。
            6. 输出必须是合法 JSON，不要包含任何其他文字。

            ## 输出 JSON 格式
            {
              "pages": [
                {
                  "planned_title": "页面标题",
                  "page_type": "ENTITY/CONCEPT/RUNBOOK/EXPERIENCE/STANDARD/OVERVIEW",
                  "category": "中间件/数据库/主机/网络/安全",
                  "software": "软件名",
                  "version": "版本号",
                  "covered_section_ids": ["sec-001"],
                  "required": true,
                  "merge_strategy": "CREATE_OR_PATCH",
                  "expected_outline": ["一级小节", "二级小节"]
                }
              ]
            }
            """.replace("{{OUTLINE_JSON}}", outlineJson)
              .replace("{{SECTION_FACTS_JSON}}", sectionFactsJson)
              .replace("{{EXISTING_PAGES}}", existingPagesSummary)
              .replace("{{SOFTWARE_REFERENCE}}", softwareReference);
    }

    public static String buildPlannedPageGenerationPrompt(String outlineJson, String sectionFactsJson,
                                                          String pagePlanJson, String sourceMetaJson) {
        return """
            你是银行基础架构运维知识库的 Wiki 页面生成器。必须严格按照 page_plan 生成页面。

            ## 来源信息
            {{SOURCE_META_JSON}}

            ## 文档目录
            {{OUTLINE_JSON}}

            ## 章节事实
            {{SECTION_FACTS_JSON}}

            ## 页面计划
            {{PAGE_PLAN_JSON}}

            ## 生成规则
            1. 只能生成 page_plan 中列出的页面，不要自由新增泛化页面。
            2. 页面标题不要包含软件名和版本号（这些信息由 category/software 标签承载），标题只描述本页面核心内容。
            3. 页面内容必须使用 Markdown，并保留操作步骤、参数、指标、故障处理和注意事项。
            4. 内容中的小标题（##、###）只写当前层级的标题，不要重复父级路径。例如：如果页面标题是"服务器实例管理"，内容小标题应该写"录入实例"而不是"集群管理/服务器管理/录入实例"。
            5. 用 [[页面名]] 标记同一软件下的强相关页面。
            6. 每个页面必须写入 source_refs.sections，section_id 必须来自 page_plan.covered_section_ids。
            7. 每个页面必须写入 coverage.section_ids，必须等于或覆盖 page_plan.covered_section_ids。
            8. 不要编造原文没有的配置项、命令、指标或故障原因。
            9. 如果原文或常用叫法中存在等价标题，可写入 alias_titles；没有就输出空数组。
            10. 输出必须是合法 JSON，不要包含任何其他文字。

            ## 输出 JSON 格式
            {
              "pages": [
                {
                  "title": "页面标题",
                  "page_type": "ENTITY/CONCEPT/RUNBOOK/EXPERIENCE/STANDARD/OVERVIEW",
                  "category": "中间件/数据库/主机/网络/安全",
                  "software": "软件名",
                  "version": "版本号",
                  "alias_titles": ["等价标题或简称"],
                  "summary": "一句话摘要",
                  "content": "完整 Markdown 内容，含 [[wikilink]] 交叉引用",
                  "source_refs": {
                    "source_id": 12,
                    "source_title": "来源标题",
                    "source_type": "UPLOAD",
                    "sections": [
                      {
                        "section_id": "sec-001",
                        "section_path": "章节路径",
                        "char_range": "0-1000",
                        "page_range": "3-5",
                        "paragraph_range": "12-18",
                        "source_signal": "numbered-heading"
                      }
                    ]
                  },
                  "coverage": {
                    "section_ids": ["sec-001"],
                    "evidence_quotes": ["原文短摘录，不超过 50 字"]
                  }
                }
              ]
            }
            """.replace("{{SOURCE_META_JSON}}", sourceMetaJson)
              .replace("{{OUTLINE_JSON}}", outlineJson)
              .replace("{{SECTION_FACTS_JSON}}", sectionFactsJson)
              .replace("{{PAGE_PLAN_JSON}}", pagePlanJson);
    }

    /**
     * 合并决策 Prompt
     */
    public static String buildMergeDecisionPrompt(String existingContent, String newContent) {
        return """
            你是知识库合并决策器。判断新内容与已有页面的关系。

            ## 已有页面内容
            {{EXISTING_CONTENT}}

            ## 新内容
            {{NEW_CONTENT}}

            ## 判断规则
            1. OVERWRITE: 新内容完全替代旧内容（旧内容已过时或有误）
            2. APPEND: 新内容是旧内容的补充（不冲突，追加即可）
            3. CONTRADICT: 新旧内容存在矛盾（需要人工裁决）

            ## 输出 JSON 格式
            {
              "action": "OVERWRITE/APPEND/CONTRADICT",
              "reason": "决策原因"
            }
            """.replace("{{EXISTING_CONTENT}}", existingContent)
              .replace("{{NEW_CONTENT}}", newContent);
    }
}
