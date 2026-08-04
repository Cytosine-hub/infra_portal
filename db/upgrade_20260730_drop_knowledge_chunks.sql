-- 下线 knowledge_chunks 表（2026-07-30）
--
-- 背景：这张表从建库起就没有被写入过（persistVectors 只写 Milvus，从未调用
-- chunkMapper.insert），因此依赖它的关键词兜底检索、知识图谱、重建任务一直是
-- 空转的。升级到 Milvus 2.5 后它彻底失去存在理由：
--   * 关键词索引 -> 由 Milvus 原生 BM25 稀疏向量承担
--   * 重建索引   -> 真相源是 wiki_sources.content 全文，重新切片即可，比切片表更可靠
--   * 删除定位   -> 改为 Milvus 按 source_type + source_id 条件删除，
--                   不再靠「重切旧内容数片数」猜 ID（那会留下孤儿向量）
--
-- 保留它等于维持三方同步（MySQL 行 / Milvus 向量 / 存储文件），而这套同步本就是坏的。
--
-- 可重复执行。

DROP TABLE IF EXISTS knowledge_chunks;
