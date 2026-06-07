---
name: "source-command-code-review"
description: "按照本项目开发规范审查当前变更或最近一次提交，重点检查后端异常/常量/日志/事务和前端设计令牌/API 错误处理；用户提到 code-review、代码规范检查、审查当前改动时使用。"
---

# source-command-code-review

Use this skill when the user asks for `/code-review`, code-review, 代码规范检查, or when project instructions require a post-change review.

Default to a local diff review. If the user explicitly names a GitHub PR, use PR mode with `gh` and review only the PR diff.

## Scope

Review only the changed code:

1. Prefer uncommitted changes: `git diff --name-only` and `git diff --cached --name-only`.
2. If the worktree is clean, review the latest commit with `git diff --name-only HEAD~1`.
3. Read `docs/development-standards.md` before judging project-specific rules.

## PR Mode

Use PR mode only when the user explicitly asks to review a PR or provides a PR number/URL.

1. Use `gh pr view` and `gh pr diff` to inspect the PR.
2. Check whether the PR is closed, draft, automated, or already reviewed; if so, report that and stop.
3. Focus on high-confidence correctness bugs and direct project-standard violations.
4. Do not post GitHub comments unless the user asks for `--comment`.
5. If posting comments, cite full file/line context and keep comments brief.

## Review Checklist

Backend Java:

- Exceptions: use `BusinessException(ErrorCode, message)` or project exception types; do not expose stack traces or technical internals to the frontend.
- Constants: avoid new magic values; use `ErrorCode`, `ErrorMessages`, `StatusConstants`, or an appropriate existing constant class.
- Logging: use `@Slf4j`, `log`, parameterized logging, and never log plaintext password/token/secret values.
- Services: constructor injection; write operations should be `@Transactional`.
- Controllers: return response DTOs or project-standard response objects; do not return raw exception details.
- MyBatis changes: mapper interface and XML must stay in sync.

Frontend Vue/JS:

- `components/ui/` must use design tokens from `frontend/src/styles/tokens.css`; avoid hard-coded colors.
- Components should use `<script setup>`, props down/events up, and avoid `$refs`/`$parent` for communication.
- API catches should surface `error.message` via notify/toast, not stack traces or `error.toString()`.
- Composables should follow module-level singleton state where the project already does so.

## Output Format

Use code-review style:

1. Findings first, ordered by severity.
2. Include file and line references.
3. If no issues are found, state that clearly and mention residual test risk.
4. Keep summary secondary.

Example:

```text
Findings
- [P1] src/.../FooService.java:42 returns exception detail to client. ...

Tests
- Not run / Passed ...
```

## Fix Mode

If the user asks for `--fix` or explicitly asks to fix findings:

1. Apply only clear, low-risk fixes.
2. Preserve unrelated user changes.
3. Run focused verification after edits.
4. Commit only if the user or repository instructions require it.
