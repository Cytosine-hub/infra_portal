-- 清空 LLM 编译产出的 wiki 页面，经验区改为人工书写（2026-07-29）
--
-- 背景与决策：
--   wiki_pages 里现存的页面全部由已下线的 LLM 编译流水线生成。这批内容质量
--   不稳定（质量门禁长期 PARTIAL，存在过度压缩页），且检索路径早已改为命中
--   源文档切片、不再经过 wiki 页面。确认 standards 为唯一真相源后，wiki 降级
--   为「团队人工经验沉淀」，故清空重来。
--
--   源文档不受影响：wiki_sources 保留，其向量索引也保留，检索能力不下降。
--
-- 执行前请确认：
--   1. 如需留档，先用 GET /api/knowledge/pages/export 导出一份，或备份下面几张表
--   2. 应用已升级到不含 IngestAgent 的版本
--
-- 执行后的预期现象（正常，不是故障）：
--   - 知识库页面的「经验沉淀」标签为空，等团队写入
--   - 「健康度」标签的孤儿页/断链检测无数据——这些规则本就是给人工经验页用的
--   - 检索结果中不再出现经验类条目，只有原始文档片段
--
-- 可重复执行。

-- 页面间关系（图谱的边），随页面一起清空
DELETE FROM wiki_links;

-- 页面级权限覆盖与访问申请，失去关联对象后无意义
DELETE FROM wiki_page_permissions;
DELETE FROM wiki_access_requests;

-- Lint 结果全部指向即将删除的页面
DELETE FROM wiki_lint_results;

-- 页面本体
DELETE FROM wiki_pages;

-- 源文档的 ingested 标记复位：页面已清空，重新索引时按新的切片策略生成
-- （解析层与切片层已改造，旧向量的切分方式已过时，建议配合一次全量重建）
UPDATE wiki_sources SET ingested = FALSE, ingested_at = NULL;
