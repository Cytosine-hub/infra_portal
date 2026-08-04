-- 下线 wiki 编译流水线遗留表（2026-07-29）
--
-- 背景：LLM 编译流水线（IngestAgent 及其编排）已整体移除，知识库收敛为
-- 「解析 → 切片 → 向量化」单一索引路线。下面两张表已无任何实体与 Mapper，
-- 不再有写入方。
--
-- 执行前请确认：
--   1. 如需保留历史编译记录用于审计，先自行备份这两张表
--   2. 应用已升级到不含 IngestAgent 的版本
--
-- 可重复执行。

DROP TABLE IF EXISTS wiki_ingest_tasks;
DROP TABLE IF EXISTS wiki_ingest_log;
