# agent.md —— Middleware Resource Manager 给 Agent 的项目说明书

> 动手前必须先完整读一遍。根目录的 `CLAUDE.md` / `AGENTS.md` 应指向本文件（内容：
> `开发规范见 agent.md，必须先完整阅读并严格遵循。`）。
>
> 元规则：① 保持精简高信号，每行都应改变你的行为；② 目录/约定/命令变了，在同一个 PR 里更新本文件。

## 1. 这是什么

`Spring Boot 3.5 + Java 17 + MySQL 8.0` 的**中间件文件与标准管理平台**。前后端分离：
后端 Spring Boot 提供 REST API + Thymeleaf 少量 SSR 页；前端 Vue3 + Vite 单页应用。
核心功能：下载中心、参数标准管理（草稿→审核→发布→修改）、标准文档、论坛、知识库 + RAG 智能排查。
按岗位分组（中间件/数据库/主机/网络/安全）做数据范围隔离——**这条贯穿全系统，务必遵守（见 §3）**。

## 2. 代码地图

```
src/main/java/com/middleware/manager/
  config/            SecurityConfig、StorageProperties、WarmupRunner、AccessLogFilter
  domain/            JPA 实体（映射 MySQL 表）+ 枚举（ReviewStatus/DocumentStatus）
  repository/        Spring Data JPA 接口
  service/           业务逻辑（新功能主要写这里）
  security/          Role 枚举 + PermissionService（按 category 限权，核心）
  web/api/           REST 控制器 /api/admin/** /api/public/** /api/auth/** /api/forum/**
    dto/             请求/响应 DTO（XxxRequest / XxxResponse.from()）
  web/form/          Thymeleaf 表单绑定对象
  web/controller/    Thymeleaf 视图控制器（/login、/admin/** 等 SSR 页）
  knowledge/         独立的知识库 + AI 排查模块，用 JdbcTemplate（非 JPA），勿与上面混用
    config client loader splitter embedding store retriever entity agent repository service web
src/main/resources/
  application.yml    唯一配置文件（DB / langchain4j / app.ai）
  db/knowledge_ddl.sql  知识库表需手动执行的 DDL
frontend/src/
  App.vue            SPA 外壳：hash 路由（route.name 分支）、部分旧页面内联于此
  components/*.vue    抽出的功能面板（ForumPostList、KnowledgePanel… 新功能进这里，见 §4）
  api.js             fetch 封装（HTTP Basic Auth，前端 SHA-256 处理密码）
```

- 新功能通常只动：后端 `service/` + `web/api/` + `domain/`（+ `repository/`），前端 `components/`。
- 入口：后端 `MiddlewareResourceManagerApplication`（:8080）；前端 `App.vue`（:5173，Vite 代理 /api、/files 到 :8080）。

## 3. 必须遵守的核心模式

**① 按 category 数据范围隔离（最重要）。** 管理岗（中间件/数据库/主机/网络/安全）只能操作本类目的数据，
靠 `PermissionService.getManagedCategory(authentication)` 强制。任何 admin 查询/写入都要照这个过滤：

```java
List<SoftwareType> types = softwareTypeService.list(activeOnly);
String category = permissionService.getManagedCategory(authentication);
if (category != null) {
    types = types.stream().filter(t -> category.equals(t.getCategory())).collect(Collectors.toList());
}
```

**② REST 控制器：构造器注入 + DTO 映射，不返回实体。**

```java
@RestController
@RequestMapping("/api/admin/software-types")
public class AdminSoftwareTypeApiController {
    private final SoftwareTypeService softwareTypeService;
    private final PermissionService permissionService;
    public AdminSoftwareTypeApiController(SoftwareTypeService s, PermissionService p) { … } // 构造器注入

    @PostMapping
    public SoftwareTypeResponse create(@Valid @RequestBody SoftwareTypeRequest req, Authentication auth) {
        checkTypeCategoryAccess(req.getCategory(), auth);
        return SoftwareTypeResponse.from(softwareTypeService.create(req)); // 用 XxxResponse.from() 转 DTO
    }
}
```

**③ 错误处理：抛标准异常，不要自己拼错误响应。** 全局 `ApiExceptionHandler` 已统一：
- 参数非法 → `throw new IllegalArgumentException(msg)` → 400
- 状态冲突（如重复、非法状态流转）→ `throw new IllegalStateException(msg)` → 409
- 校验失败 → `@Valid` + `@RequestBody` 自动 400
- 不要在控制器里 new ResponseEntity 拼错误体。

## 4. 新增一个功能的配方

**后端（一条需求通常）:** `domain/` 加/改实体 → `repository/` 加查询 → `service/` 写业务 →
`web/api/` 加控制器（构造器注入、`@Valid` DTO、category 限权、抛标准异常）→ `dto/` 加 Request/Response。
实体变更靠 Hibernate `ddl-auto: update` 自动建表/加列——**只加列/表，避免删改会丢数据的破坏性变更**。

**前端（避免并行冲突，务必这样做）:** 新功能抽成 `frontend/src/components/XxxPanel.vue`（参照
`KnowledgePanel.vue`：`props` 收 `:auth`/`:notify`，`$emit` 抛事件），然后在 `App.vue` 里**只加一处**：
一个 `route.name === 'xxx'` 分支挂载 `<XxxPanel>`。**不要把大段新页面内联进 App.vue**——多个需求
同时改 App.vue 必冲突（这正是门户独立分支要避免的）。

## 5. 命名与约定

- Java：类 PascalCase、方法/变量 camelCase；控制器 `Xxx(Api)Controller`、服务 `XxxService`、
  请求/响应 DTO `XxxRequest`/`XxxResponse`。
- Vue 组件文件 PascalCase（`XxxPanel.vue`）；`<script setup>` 风格与现有组件一致。
- 知识库模块用 JdbcTemplate，其余用 JPA——**不要在同一模块混用两套数据访问**。

## 6. 风格由工具强制（目标态；基建单独需求搭建中）

风格一致以**工具**为准，不靠手工对齐。以下为约定标准，配置正在通过独立的「工具基建」需求引入：

- 后端 **Spotless**（google-java-format）：提交前 `mvn spotless:apply`，CI 跑 `mvn spotless:check`。
- 前端 **Prettier**：提交前 `npm run format`，CI 校验。
- **约定优先级：工具配置 > 本文件文字。** 冲突以工具为准并在 PR 指出。
- 基建落地前：严格模仿 §3 的现有代码写法（构造器注入、DTO.from、标准异常）。

## 7. 命令速查

```bash
# 后端（:8080）
mvn clean package -DskipTests     # 编译打包
mvn spring-boot:run               # 运行
mvn test                          # 跑 JUnit 测试（spring-boot-starter-test 已就绪）
# mvn spotless:apply / spotless:check   ← 工具基建需求引入后可用

# 前端（frontend/，:5173）
npm install
npm run dev
# npm test / npm run format             ← 工具基建需求引入后可用
```

数据库：MySQL 8.0 `127.0.0.1:3306/middleware_resource_manager`，`ddl-auto: update` 自动建表。

## 8. 测试（严格 TDD，全覆盖）

- **测试先行**：先把 Issue 每条验收用例转成自动化测试，**测试名注明用例编号**（如 `TC-01`），
  再写实现，直到全绿。每条验收用例都必须有对应测试。
- 后端：JUnit（`mvn test` 现即可用）。业务逻辑写在 `service/` 且尽量抽成可单测的方法；
  控制器层用 `@WebMvcTest` 或 `@SpringBootTest`。**权限/category 隔离逻辑必须有测试覆盖**（易错高危）。
- 前端：目标用 Vitest + @vue/test-utils（测试运行器由「工具基建」需求引入）；组件内可计算逻辑
  抽成具名导出的纯函数来测。基建就绪前，前端需求至少保证 `npm run build` 通过并说明手工验证步骤。
- 不得破坏已有测试。

## 9. 分支与提交

- 一条需求 = 一个 Issue = 一个分支（`feature/req-<门户ID>-issue-<Issue号>`）= 一个 PR。
- 禁止直接向 main 提交；PR 正文含 `Closes #<issue>`。提交信息中文：`feat(<模块>): <说明> (#<issue>)`。

## 10. 禁区与反模式（会被评审直接打回）

- ❌ admin 接口不做 category 限权（越权访问别的岗位数据）。
- ❌ 控制器直接返回 JPA 实体、或自己拼错误响应（应 DTO + 抛标准异常）。
- ❌ 把大段新页面内联进 `App.vue`（应抽 `components/` 组件，仅在 App.vue 加挂载分支）。
- ❌ 知识库模块混用 JPA / 其余模块混用 JdbcTemplate。
- ❌ 破坏性的实体/表结构变更（删列、改类型）导致数据丢失。
- ❌ 提交未跑测试、（基建就绪后）未跑 format 的代码。

## 11. 踩坑与经验（持续沉淀）

- **认证**：前端把密码 SHA-256 后再放进 Basic Auth 头；后端库里存 `{bcrypt}...`。改认证相关逻辑要两端对齐。
- **文件存储**：上传文件落在 `./storage/<middlewareName>/`，`/files/**` 需登录才能下载。
- **知识库表不走 ddl-auto**：`knowledge_chunks` 等表需手动执行 `src/main/resources/db/knowledge_ddl.sql`。
- **配置/密钥**：`application.yml` 里 DB 密码、`app.ai.api-key` 有默认值，勿把真实密钥硬编码进提交。
- <后续每踩一个坑就补一条，比会话记忆持久且团队共享>
