---
name: "source-command-code-review"
description: "按照本项目开发规范和代码审查规则检查当前变更或最近一次提交，重点检查后端异常/常量/日志/事务/安全和前端设计令牌/API 错误处理/XSS；用户提到 code-review、代码规范检查、审查当前改动时使用。"
---

# source-command-code-review

Use this skill when the user asks for `/code-review`, code-review, 代码规范检查, 审查当前改动, or when project instructions require a post-change review.

Default to a local diff review. If the user explicitly names a GitHub PR, use PR mode with `gh` and review only the PR diff.

## Required References

Read these files before judging project-specific rules:

- `docs/development-standards.md`
- `docs/code-review-rules.md`
- `frontend/src/styles/tokens.css` when checking frontend style tokens
- `backend/src/main/java/com/middleware/manager/constant/ErrorCode.java` when checking backend errors
- `backend/src/main/java/com/middleware/manager/constant/ErrorMessages.java` when checking backend messages
- `backend/src/main/java/com/middleware/manager/constant/StatusConstants.java` when checking backend status constants

## Scope

Review only changed code:

1. Prefer uncommitted changes:
   - `git diff --name-only`
   - `git diff --cached --name-only`
2. If both are empty, review the latest commit with `git diff --name-only HEAD~1`.
3. Check only `.java`, `.vue`, `.js`, and `.ts` files by default.
4. Skip `.css`, `.xml`, `.yml`, `.sql`, `.md`, `.json`, `.sh`, and `.properties` unless the user explicitly asks or the changed file is a runtime/security-sensitive config.
5. Skip old code outside the changed lines unless it directly affects a changed line's correctness.
6. Skip test files (`*Test.java`, `*IT.java`) for project-style enforcement unless the issue is a real correctness or security bug.

## PR Mode

Use PR mode only when the user explicitly asks to review a PR or provides a PR number/URL.

1. Use `gh pr view` and `gh pr diff` to inspect the PR.
2. Check whether the PR is closed, draft, automated, or already reviewed; if so, report that and stop.
3. Focus on high-confidence correctness bugs and direct project-standard violations.
4. Do not post GitHub comments unless the user asks for `--comment`.
5. If posting comments, cite full file/line context and keep comments brief.

## Review Workflow

1. Group changed files by backend and frontend.
2. Inspect backend and frontend independently. Use parallel exploration when the changed files are independent.
3. Report findings first, ordered by severity.
4. If the user requests `--fix` or explicitly asks to fix findings, apply only clear, low-risk fixes after reporting or confirming the findings.

## Backend Rules (`.java`)

Must fix:

- Native business exceptions: use `BusinessException(ErrorCode, ErrorMessages...)` or the project-standard exception type instead of returning or throwing raw technical details.
- Exception leaks: do not expose stack traces or internal exception messages to the frontend.
- Logging concatenation: use parameterized logging, for example `log.info("id={}", id)`.
- Sensitive logging: never log plaintext passwords, tokens, API keys, or secrets.
- Hard-coded Chinese business error messages in services/controllers: extract to `ErrorMessages`.
- Multi-step writes or explicit insert/update/delete service methods without `@Transactional`.
- Controller directly injecting Mapper instead of going through Service.
- DTO/domain consistency where the changed code introduces the issue.

Avoid false positives:

- DTO fields, annotation parameters, method names, and route strings are not magic values by themselves.
- `catch (Exception e) { log.error("msg", e) }` is valid when the exception is intentionally logged server-side.
- Status strings such as `"PUBLISHED"` are acceptable when already defined in `StatusConstants` or when they are single-use enum-like values with no existing constant.
- Only require `@Transactional` when there is a clear database write operation.
- Require `@Data @NoArgsConstructor @AllArgsConstructor` for domain/entity classes; do not force it on DTO classes unless project rules specifically require that DTO style in the changed package.
- Do not enforce constant extraction in `*Config.java` for configuration key definitions.

## Frontend Rules (`.vue`, `.js`, `.ts`)

Must fix:

- Hard-coded hex colors in components when an existing design token is available, for example use `var(--color-primary)` instead of `#2356a5`.
- API/UI error display using `error.toString()`, stack traces, or raw technical details. Prefer `notify(error.message || '操作失败', 'error')`.
- Unsafe `v-html` or manually generated HTML from user-controlled content without escaping/sanitization.
- `markdown-it` must use `html: false`, and post-processing must not inject unescaped user data.
- `window.addEventListener` should be paired with cleanup in `onBeforeUnmount`.
- Empty `catch {}` blocks should at least `console.warn()` or surface a user-facing error through `notify`.

Avoid false positives:

- `rgba()`, `hsla()`, `#000`, `#fff`, and `#ffffff` are acceptable unless they conflict with a clear local token convention.
- `box-shadow` and `border` colors are not mandatory token replacements when no matching token exists.
- `frontend/src/styles.css` and `frontend/src/styles/tokens.css` are not checked as component style-token violations.
- Existing components are not flagged for missing `<script setup>` unless the changed code moves them further away from local conventions.
- `error.message` is already the expected pattern.
- Inline style using `var(--color-*)` is valid.
- Skip `api.js`, generic utilities, and third-party integration internals unless the change creates a correctness, security, or user-facing error-handling issue.

## Security and Architecture Rules

Prioritize high-confidence issues from `docs/code-review-rules.md`, especially:

- XSS injection through `v-html`, wikilink rendering, markdown post-processing, or manual HTML string construction.
- SQL injection through MyBatis `${}`.
- Hard-coded secrets or credentials.
- Missing authorization checks on admin endpoints.
- Controller return values exposing maps or raw internal objects where a DTO is expected.
- N+1 queries introduced in changed code.
- New duplicated utility logic that clearly should reuse an existing helper.

## Output Format

Use code-review style. Findings must come first.

If there are findings:

```text
Findings
- [P1] path/to/File.java:45 returns raw exception details to the client. ...
- [P2] path/to/Component.vue:23 uses unsanitized v-html with user content. ...

Tests
- Not run / Passed ...

Summary
- Brief context only if useful.
```

If no issues are found:

```text
Findings
- No issues found.

Tests
- Not run / Passed ...

Residual Risk
- Mention any focused risk that was not verified.
```

When using the Chinese report format requested by the user, include:

```text
## 代码规范检查报告

### 变更文件
- 后端: N 个 .java 文件
- 前端: N 个 .vue/.js/.ts 文件
- 跳过: N 个

### 问题清单（共 N 个）

### 无问题
（若无问题则写明）
```

## Fix Mode

If the user asks for `/code-review --fix`, `--fix`, or explicitly asks to fix findings:

1. Apply only clear, low-risk fixes.
2. Preserve unrelated user changes.
3. Run focused verification after edits.
4. Commit only if the user or repository instructions require it.
5. Re-run this review after fixes.
