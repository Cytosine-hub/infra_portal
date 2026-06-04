# 代码审查规则

> 适用于 middleware_resource_manager 全项目（后端 Java + 前端 Vue）

---

## 一、安全类规则（S 级，必须修复）

### S-01 XSS 注入
- **前端**: `v-html` 渲染用户内容前必须消毒。`formatDetail()` 等手动拼 HTML 的函数必须先转义 `<>&"`。
- **前端**: wikilink 等动态生成 HTML 属性时，值必须经过 `escapeHtml()`。
- **前端**: `markdown-it` 必须配置 `html: false`，且后处理的正则替换不得引入未转义的用户数据。
- **后端**: 审计日志 JSON 必须用 Gson/Jackson 序列化，禁止 `String.format` 拼接。

### S-02 SQL 注入
- MyBatis 中只使用 `#{}` 参数绑定，禁止 `${}`。

### S-03 敏感信息泄露
- **后端**: 异常消息不得直接返回前端。`RuntimeException` 处理器返回通用错误信息。
- **后端**: 硬编码的 API Key、数据库密码必须改为纯环境变量，`application.yml` 中不得有明文密钥。
- **前端**: Token 存储在 localStorage 中，必须防御 XSS。
- **前端**: `getSavedAuth()` 必须校验 `expiresAt`，过期则清除。

### S-04 输入验证
- 文件上传校验大小、条目数、路径遍历（Zip Slip）、Zip Bomb。
- REST 接口必填参数在入口处非空校验。
- `@RequestBody` 参数使用 `@Valid` 注解。

### S-05 认证与授权
- 管理类接口必须校验用户身份和权限（Defense in Depth，不依赖 SecurityConfig 单一防线）。
- CORS 不得使用 `*` + `allowCredentials(true)`。
- 密码存储必须统一：前端 SHA-256 后传输，后端 BCrypt 存储，seed 脚本也必须遵循同一管道。

### S-06 CSRF 保护
- 使用 Cookie 认证的场景必须启用 CSRF。使用 Token 认证的 API 可禁用，但需记录决策理由。

---

## 二、架构类规则（A 级）

### A-01 分层架构
- Controller 不得直接注入 Mapper。必须通过 Service 层。
- 每个 Controller 端点必须有对应的 Service 方法。
- DTO 类统一使用 `@Data`（Lombok），禁止手写 getter/setter。

### A-02 组件拆分
- 单个 Vue 组件不得超过 500 行（含模板+脚本+样式）。超限必须拆分。
- App.vue 不得包含业务模态框。每个模态框应是独立组件。
- 路由名称使用常量，禁止魔法字符串。

### A-03 请求/响应类型化
- Controller 返回值必须使用 DTO 类型，禁止 `Map<String, Object>`。
- 请求体使用独立的 Request DTO 类（放在 `web/api/dto/` 包），禁止内部类。

### A-04 DDL 与实体对齐
- 每个 DDL 表必须有对应的 Java 实体类和 Mapper。
- 设计文档中的索引建议必须在 DDL 中体现。

---

## 三、模块复用规则（R 级）

### R-01 工具方法去重
- 相同逻辑不得在多处实现。发现 3 处以上重复必须提取为共享工具类。
- **已知需提取**:
  - `trimToNull()` — 5 个 Service 中重复
  - `requireText()` — 3 个 Service 中重复
  - `restorePublishedVersion()` — 2 个 Service 中重复，应移入 `VersionManager`
  - `sha256Hex()` — 4 处重复（WikiController、IngestTaskService、IngestAgent、AdminAccountService）
  - `formatDate()` — 前端 3 个组件中重复
  - `renderMarkdown()` — 前端 3 个组件中重复（各有不同消毒逻辑）

### R-02 PageResult 构建
- `PageResult.of(PageInfo)` 静态工厂方法已存在，所有 Controller 必须使用它，禁止手动映射。

### R-03 分页/排序工具
- `normalizeSize()` 在 2 个 Controller 中重复，应提取到基类或工具类。
- `loadSoftwareType()` 在 2 个 Controller 中重复，应移入 Service。

### R-04 前端工具模块
- 前端必须创建 `src/utils/` 目录，提取共享工具：`formatDate`、`escapeHtml`、`renderMarkdown`、`statusLabel`。

---

## 四、性能规则（P 级）

### P-01 N+1 查询
- 禁止在循环中逐条查询。必须批量查询 + 内存索引。
- **仍存在**: `ForumController.toSummary`（标签查询）、`AdminReleaseApiController.list`（软件类型查询）。

### P-02 全量加载
- 禁止 `findAll()` 后仅取 `.size()`。计数用 `COUNT(*)`，过滤在数据库层。
- `PublicStandardParameterApiController.list()` 无条件时加载全部参数，必须加分页。

### P-03 线程池管理
- 热路径禁止每次创建线程池。使用类级别共享池或 Spring `TaskExecutor`。
- `@Async` 必须配置自定义执行器。

### P-04 内联清理
- `TokenService.validateToken()` 每次调用都 DELETE 过期 token，应改为定时任务。

### P-05 事件监听器清理
- 前端 `window.addEventListener` 必须在 `onMounted` 中注册，`onBeforeUnmount` 中移除。

---

## 五、代码质量规则（Q 级）

### Q-01 死代码
- 注入但未使用的依赖必须删除。
- 声明但未使用的 `@Value` 配置必须删除或使其生效。
- 未使用的 Logger 字段必须删除。

### Q-02 命名一致性
- Logger 字段统一命名为 `log`（小写）。
- 实体类命名风格统一（Wiki 模块全部带 `Wiki` 前缀）。
- Lombok 使用：Domain + DTO 统一使用 `@Data`。

### Q-03 Java 枚举映射
- DDL ENUM 字段在 Java 端使用对应 enum，至少在 Service 层做值校验。
- 状态值使用枚举常量，禁止魔法字符串。

### Q-04 配置项生效
- `@Value` 注入的配置必须实际被使用。声明了但硬编码的配置等同于死代码。

### Q-05 方法长度
- 单个方法不得超过 50 行。超限必须拆分。
- Controller 方法不得超过 30 行（含异常处理）。

---

## 六、前端 UI 统一性规则（U 级）

### U-01 设计令牌
- 必须创建 `src/styles/tokens.css`，定义全局 CSS 变量：
  - 颜色：`--color-primary`、`--color-bg`、`--color-text`、`--color-border`
  - 间距：`--space-xs/sm/md/lg/xl`（4/8/12/16/24px）
  - 圆角：`--radius-sm/md/lg`（4/8/12px）
  - 字号：`--text-xs/sm/md/lg/xl`（12/13/14/16/18px）
- 所有组件必须使用这些变量，禁止硬编码颜色/间距值。

### U-02 主题一致性
- 全站统一使用浅色主题（或统一使用深色主题）。禁止混用。
- WikiPanel 的 GitHub-dark 主题必须与 App.vue 的浅色主题统一。

### U-03 共享组件库
- 必须创建 `src/components/ui/` 目录，包含：
  - `BaseButton.vue` — 统一按钮样式（primary/success/danger/ghost）
  - `BaseModal.vue` — 统一模态框（backdrop + panel + 关闭 + 焦点陷阱）
  - `BaseInput.vue` — 统一输入框样式
  - `EmptyState.vue` — 统一空状态展示
  - `LoadingSpinner.vue` — 统一加载状态
- 所有业务组件必须使用这些基础组件。

### U-04 错误反馈一致性
- 所有组件统一使用 `notify(message, type)` 展示错误。
- `DiagnosticsPanel` 必须接收 `notify` prop，禁止使用 `alert()`。
- `catch {}` 块必须至少 `console.warn()`，推荐使用 `notify`。

### U-05 Prop 定义
- 必填 prop 必须声明 `required: true`。
- `notify` prop 在所有需要错误反馈的组件中必须是 required。

---

## 七、日志规则（L 级）

### L-01 日志级别
- 登录尝试/成功降级为 DEBUG（含用户名的 INFO 日志存在 PII 风险）。
- Token 创建/删除降级为 DEBUG。
- 客户端错误（`IllegalArgumentException`）只记录消息，不记录堆栈。

### L-02 敏感数据
- 日志中不得包含密码、Token、API Key。
- AccessLog 的 query string 需脱敏处理。

### L-03 日志完整性
- 所有 Service 层公共方法的入口和异常必须有日志。
- 禁止空的 `catch {}` 块——至少 `log.warn()`。

### L-04 全局异常处理
- `unhandledrejection` 处理器不得 `event.preventDefault()`（会抑制 DevTools 错误报告）。
- Toast 通知区分错误（5s）和成功（3s）的显示时长。

---

## 八、检查清单

每次 PR 审查必须确认：

- [ ] 无硬编码密钥/密码
- [ ] 无 `v-html` 渲染未消毒内容
- [ ] Controller 不直接注入 Mapper
- [ ] 返回值使用 DTO 类型
- [ ] 无 N+1 查询
- [ ] 多步写入有 `@Transactional`
- [ ] 删除操作有级联清理
- [ ] 无空 `catch {}` 块
- [ ] 重复代码已提取
- [ ] 组件 < 500 行
- [ ] 使用设计令牌（颜色/间距/字号）
- [ ] 使用共享 UI 组件
