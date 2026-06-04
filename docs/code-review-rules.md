# 代码审查规则

> 适用于 middleware_resource_manager 项目的 Wiki、Knowledge、Agent 模块

---

## 一、安全类规则（S 级，必须修复）

### S-01 XSS 注入
- **规则**: 禁止在 Vue 模板中使用 `v-html` 渲染用户可控内容。若必须使用，必须先通过 DOMPurify 等库进行消毒。
- **禁止**: 将数据库字段直接拼接进 HTML 字符串（如 `data-title="${title}"`），必须对 `"`、`<`、`>`、`&` 进行 HTML 实体转义。
- **检查点**: `v-html`、`innerHTML`、字符串模板拼接 HTML 属性。

### S-02 SQL 注入
- **规则**: MyBatis 中只使用 `#{}` 参数绑定，禁止使用 `${}` 字符串拼接。
- **检查点**: 所有 Mapper XML 文件中的 `${}` 使用。

### S-03 敏感信息泄露
- **规则**: 异常消息（`e.getMessage()`）不得直接返回给前端。Controller 层必须捕获异常并返回通用错误信息，详细错误只记录到日志。
- **规则**: 审计日志中的 JSON 字段必须使用 JSON 库序列化，禁止 `String.format` 手动拼接。
- **检查点**: `WikiExceptionHandler` 中 `RuntimeException` 处理器、`WikiController` 中审计日志构建。

### S-04 输入验证
- **规则**: 文件上传必须校验大小上限、条目数量、路径遍历（Zip Slip）。
- **规则**: ZIP 导入必须检查每个条目的解压后大小，防止 Zip Bomb。
- **规则**: 所有 REST 接口的必填参数必须在入口处做非空校验。
- **检查点**: `WikiImportService.importFromZip`、`IngestTaskService.createTextTask`。

### S-05 认证与授权
- **规则**: 管理类接口（删除、修改、导入）必须校验当前用户身份和操作权限。
- **规则**: Token 存储在 localStorage 中时，页面必须防御 XSS（因为 XSS 可窃取 token）。
- **检查点**: `WikiController` 中所有 DELETE/PUT/POST 端点的权限校验。

---

## 二、数据完整性类规则（D 级，高优先级）

### D-01 事务管理
- **规则**: 涉及多表写入的业务方法必须标注 `@Transactional`。
- **必须加事务的场景**:
  - `WikiImportService.importFromZip`（pages + links 写入）
  - `IngestTaskService.createTaskFromContent`（source + task 写入）
  - `LintAgent.runLint`（delete resolved + insert new）
  - `IngestAgent.ingest`（pages + links + vector + log 写入）
- **检查点**: Service 层多步写入方法是否有 `@Transactional`。

### D-02 级联删除
- **规则**: 删除主记录时，必须同步清理关联数据（外键引用、向量索引等）。
- **检查点**: `WikiController.deletePage` 是否清理 `wiki_links`、`wiki_page_permissions`、向量嵌入。

### D-03 空列表防御
- **规则**: MyBatis `<foreach>` 生成的 `IN (...)` 子句，调用方必须保证列表非空，或在 XML 中添加 `<if test="ids != null and ids.size() > 0">` 守卫。
- **检查点**: `WikiPageMapper.findByIds` 的所有调用方。

### D-04 幂等性
- **规则**: 使用 `INSERT IGNORE` 的写入，返回值不能作为"新增成功数"的依据。必须用 `insert` 的实际返回值（affected rows）判断是否真的插入。
- **检查点**: `LinkResolver.resolveLinks` 中 `created` 计数器。

---

## 三、性能类规则（P 级，按优先级排序）

### P-01 N+1 查询
- **规则**: 禁止在循环中逐条查询数据库。必须使用批量查询（`findByIds`、`findAll` + 内存索引）。
- **已知违规**:
  - `WikiController.getPageLinks` — 循环中调用 `findById`
  - `WikiController.batchUpdateCategory` — 循环中调用 `findById`
  - `WikiExportService.exportPages` — 循环中调用 `findByFromPageId`
  - `WikiSearchService` 图扩展 — 循环中调用 `findAllByPageId`

### P-02 全量加载
- **规则**: 禁止用 `findAll()` 加载全表数据后仅用于计数或过滤。计数必须使用 `COUNT(*)` SQL，过滤应在数据库层完成。
- **已知违规**:
  - `WikiController.getStats` — 加载所有页面和来源仅取 `.size()`
  - `LinkResolver.findBrokenLinks` — 每次调用加载所有页面
  - `LintAgent.detectBrokenLinks` — 重复加载所有页面两次
  - `IngestAgent.buildExistingPagesSummary` — 每次调用加载所有页面
  - `WikiGraphService.buildGraph` — 加载所有页面和链接

### P-03 OR 条件索引失效
- **规则**: `WHERE col_a = ? OR col_b = ?` 无法高效使用单列索引。应改写为 `UNION ALL` 两个各带索引的查询。
- **检查点**: `WikiLinkMapper.findAllByPageId`、`WikiLinkMapper.deleteByPageId`。

### P-04 内存风险
- **规则**: 大数据量操作（导出、重建索引）必须分批处理，禁止一次性加载全部数据到内存。
- **检查点**: `WikiExportService.exportAll`、`WikiController.reindexPages`。

### P-05 线程池管理
- **规则**: 禁止使用 `Executors.newFixedThreadPool` 等无界工厂方法在热路径创建线程池。应使用共享线程池或 `@Async` 配置的执行器。
- **规则**: `@Async` 方法必须配置自定义 `TaskExecutor`，禁止依赖默认的 `SimpleAsyncTaskExecutor`。
- **检查点**: `WikiSearchService.search` 中每次调用创建 `newFixedThreadPool(2)`。

---

## 四、代码质量类规则（Q 级）

### Q-01 死代码
- **规则**: 注入但未使用的依赖必须删除。
- **已知违规**:
  - `LintAgent` 中 `linkMapper` 未使用
  - `WikiSearchService` 中 `maxContentChars` 未使用
  - `WikiSearchService` 中 `graphHopLimit` 声明但未使用（硬编码为 1）
  - `WikiSearchService.search` 的 `topK` 参数被忽略
  - `IngestTaskService` 中 `maxConcurrent` 未使用（Semaphore 硬编码为 2）
  - `WikiGraphService` 中 `W_CO_OCCURRENCE` 声明但未实现
  - `api.js` 中 `saveAuth` 的 `username` 参数未使用
  - `api.js` 中 `fileUrl()` 是恒等函数

### Q-02 命名一致性
- **规则**: 同一模块的实体类命名风格必须统一。
- **违规**: `IngestTask` 和 `LintResult` 缺少 `Wiki` 前缀，与 `WikiPage`、`WikiSource`、`WikiLink` 等不一致。

### Q-03 代码重复
- **规则**: 相同逻辑不得在多处实现。应提取为共享方法。
- **已知违规**:
  - `WIKILINK_PATTERN` 正则在 `LinkResolver` 和 `LintAgent` 中重复定义
  - 断链检测逻辑在 `LinkResolver.findBrokenLinks` 和 `LintAgent.detectBrokenLinks` 中重复实现

### Q-04 字段类型一致性
- **规则**: 同一概念（如"操作人"）在不同实体中的字段类型必须一致。
- **违规**: `WikiPage.compiledBy` 是 `String`，而 `WikiPage.reviewedBy` 是 `Long`。

### Q-05 Java 枚举映射
- **规则**: DDL 中的 ENUM 类型在 Java 端应使用对应的 Java enum，而非 String。至少应在 Service 层做值校验。
- **涉及字段**: `page_type`、`status`、`source_type`、`link_type`、`permission_type`、`lint_type`、`severity`。

### Q-06 配置项生效
- **规则**: 通过 `@Value` 注入的配置项必须实际被代码使用。声明了但硬编码的配置等同于死代码。
- **已知违规**: `IngestTaskService.maxConcurrent`、`WikiSearchService.graphHopLimit`。

---

## 五、前端规则（F 级）

### F-01 错误反馈
- **规则**: 所有异步操作的错误必须通知用户（通过 toast/notification），禁止仅 `console.error`。
- **已知违规**: `WikiPanel` 中 `loadPages`、`loadSources`、`loadLintResults`、`loadGraph`、`refreshIngestTasks` 均仅 `console.error`。

### F-02 轮询退出
- **规则**: 轮询逻辑必须有最大重试次数或超时机制，禁止无限重试。
- **检查点**: `WikiPanel.pollTasks` 无限重试。

### F-03 可访问性
- **规则**: 交互元素必须支持键盘操作。模态框需要 `role="dialog"`、`aria-modal`、Escape 键关闭、焦点陷阱。
- **规则**: 输入框必须有关联的 `<label>` 或 `aria-label`。
- **规则**: 动态内容区域使用 `aria-live` 通知屏幕阅读器。

### F-04 XSS 防御
- **规则**: `v-html` 的内容必须经过消毒处理。动态拼接 HTML 字符串时，用户数据必须转义。
- **检查点**: `WikiPanel` 中 wikilink 渲染的 `data-title` 属性注入。

### F-05 Token 过期
- **规则**: 存储在客户端的认证 token 必须校验过期时间，过期后自动清除并跳转登录。
- **检查点**: `api.js` 中 `getSavedAuth()` 未校验 `expiresAt`。

---

## 六、设计文档一致性规则（A 级）

### A-01 设计与实现对齐
- **规则**: 设计文档中承诺的功能必须在代码中完整实现，或在文档中标注为"未实现"。
- **规则**: 设计文档中的 API 接口定义必须与实际 Controller 端点一致。

### A-02 DDL 与实体对齐
- **规则**: 每个 DDL 表必须有对应的 Java 实体类和 Mapper。
- **已知缺失**: `wiki_audit_log` 和 `wiki_access_requests` 表无对应实体。

### A-03 索引设计
- **规则**: 设计文档中列出的索引建议必须在 DDL 中体现。
- **检查点**: `wiki_ddl.sql` 中是否包含设计文档建议的所有索引。
