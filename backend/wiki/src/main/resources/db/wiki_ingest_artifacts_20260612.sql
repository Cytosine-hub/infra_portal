-- wiki_ingest_tasks 增加 section_facts 和 page_plan 中间产物列
ALTER TABLE wiki_ingest_tasks
    ADD COLUMN section_facts JSON NULL COMMENT '章节事实中间产物' AFTER quality_report,
    ADD COLUMN page_plan JSON NULL COMMENT '页面计划中间产物' AFTER section_facts;
