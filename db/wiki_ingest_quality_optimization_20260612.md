# Wiki Ingest Quality Optimization DB Notes

Date: 2026-06-12

## Summary

This change implements the first phase of directory-driven Wiki ingest:

- document type classification
- document outline and section extraction
- section-facts prompt
- page-plan prompt
- planned page generation
- source_refs section coverage
- quality gate status for ingest tasks

## Schema Changes

No DDL change is required in this phase.

The implementation reuses existing columns:

- `wiki_pages.source_refs`
  - now stores section-level references in JSON:

    ```json
    {
      "source_id": 12,
      "source_title": "example.pdf",
      "source_type": "UPLOAD",
      "sections": [
        {
          "section_id": "sec-001",
          "section_path": "配置/连接池参数",
          "char_range": "1200-2200"
        }
      ]
    }
    ```

- `wiki_ingest_tasks.status`
  - existing `COMPLETED`, `PARTIAL`, and `FAILED` values are reused for quality gate results.

- `wiki_ingest_tasks.error_message`
  - reused for quality gate summary when status is `PARTIAL` or `FAILED`.

- `wiki_ingest_log.error_detail`
  - reused for quality gate summary.

## Release Notes

No SQL migration is needed for this change. If future releases need persistent quality reports, add a dedicated JSON column such as `wiki_ingest_tasks.quality_report` or a separate `wiki_ingest_quality_reports` table.
