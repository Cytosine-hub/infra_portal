# agent.md

## 1. 项目一句话定位与形态

**中间件资源管理平台**：一个面向基础设施团队的内部门户，管理软件下载包（ReleaseAsset）、参数标准（含草稿→审核→发布版本流）、标准文档、论坛，并集成 AI 知识库/RAG 排查（LangChain4j + Milvus）、Wiki 知识图谱和 Zabbix 监控 Agent。业务运行时为 Spring Boot 3.5.3（Java 17，MyBatis + MySQL 8）：大部分能力仍在模块化单体 app，论坛已由独立 community-service 提供，前置 Spring Cloud Gateway；`cloud` profile 下通过 Nacos 注册、发现和配置。前端为 Vue 3 单页应用（Vite，无 vue-router，hash 路由）。

## 2. 代码地图

```
.
├── backend/                         # Maven 多模块：业务 app + forum 服务 + api-gateway
│   ├── pom.xml                      # 聚合根父 POM，统一 Boot/Cloud/Alibaba BOM 与插件
│   ├── modular-monolith-parent/     # 既有业务模块的依赖父 POM，隔离 Gateway WebFlux 依赖
│   ├── common-core/                 # DTO、异常/错误码、常量、共享模型及跨模块业务端口
│   ├── common-security/             # Token 校验、PermissionService、角色/岗位权限端口
│   ├── common-web/                  # SecurityConfig、过滤器、全局异常处理、Web 通用配置
│   ├── identity/                    # 账号、Token、角色、系统设置、API 审计
│   ├── catalog/                     # 软件分类、软件类型、发布包与文件下载
│   ├── standards/                   # 参数标准、标准文档、审核、版本与转换
│   ├── knowledge/                   # 知识库、向量检索、RAG 排查
│   ├── wiki/                        # Wiki 摄取、检索与知识图谱
│   ├── community/                   # 论坛帖子、评论、标签与点赞
│   ├── ops-agent/                   # 运维 Agent、工具、Skill、Zabbix 与导出
│   ├── job-middleware/              # 中间件岗位专属命令端点
│   ├── job-database/                # 数据库岗位边界（待演进）
│   ├── job-host/                    # 主机岗位边界（待演进）
│   ├── job-network/                 # 网络岗位边界（待演进）
│   ├── job-security/                # 网络安全岗位边界（待演进）
│   ├── api-gateway/                 # 独立 Gateway 应用（:8080）；cloud 下经 Nacos lb:// 路由
│   ├── community-service/           # 独立论坛应用（:8082）；聚合 common-* + community
│   └── app/                         # 剩余业务启动模块（:8081）；不再包含 community
│       └── src/main/java/com/middleware/manager/MiddlewareResourceManagerApplication.java
├── frontend/
│   ├── src/main.js      # 前端入口
│   ├── src/App.vue      # 应用骨架 + hash 路由分发
│   ├── src/api.js       # fetch 封装：Bearer Token（localStorage：mrm.token）
│   ├── src/pages/       # HomePage/DownloadsPage/StandardsPage/CommandsPage/DataMigrationPage + admin/*Section.vue
│   ├── src/components/  # 业务组件（DocumentEditor、Forum*、KnowledgePanel、DiagnosticsPanel、WikiPanel…）
│   ├── src/components/ui/  # Base* 通用组件（BaseButton/BaseModal/DataTable/Toast…，仅用设计令牌）
│   ├── src/composables/ # useAuth/useNotify/useRoute/useAdmin（模块级单例状态）
│   └── src/styles/tokens.css  # 设计令牌（--color-* 等）
├── docs/                # development-standards.md、microservices-stage1-gateway-nacos.md 等
├── scripts/             # 启动/打包脚本（package-for-deploy.sh、start-local-*.ps1）
├── db/ deploy/ release/ # DDL、部署物、发布包
└── commands/ examples/  # 中间件命令库、示例
```

查看代码时**必须优先使用 CodeGraph MCP 工具**（`codegraph_search` 找定义、`codegraph_callers`/`codegraph_callees` 查调用链、`codegraph_impact` 评估影响、`codegraph_files` 看结构），禁止直接 Read 整个 Java/Vue 文件、禁止用 grep 找函数定义。

## 3. 必须模仿的核心模式

**模式一：Service 层 —— 构造器注入 + @Transactional + 常量化业务异常**（`service/SoftwareTypeService.java`）：

```java
@Service
@Slf4j
public class SoftwareTypeService {
    private final SoftwareTypeMapper softwareTypeMapper;

    public SoftwareTypeService(SoftwareTypeMapper softwareTypeMapper, ...) {
        this.softwareTypeMapper = softwareTypeMapper;
    }

    public SoftwareType get(Long id) {
        SoftwareType type = softwareTypeMapper.findById(id);
        if (type == null) {
            throw new NotFoundException(ErrorCode.SOFTWARE_TYPE_NOT_FOUND, ErrorMessages.SOFTWARE_TYPE_NOT_FOUND);
        }
        return type;
    }

    @Transactional
    public SoftwareType create(SoftwareTypeRequest request) {
        if (softwareTypeMapper.existsByCategoryIgnoreCaseAndNameIgnoreCase(category, name)) {
            throw new BusinessException(ErrorCode.SOFTWARE_TYPE_DUPLICATE, ErrorMessages.SOFTWARE_TYPE_DUPLICATE);
        }
        ...
    }
}
```

**模式二：全局异常处理 —— 只返回错误码 + 中文消息，堆栈只进日志**（`web/api/ApiExceptionHandler.java`）：

```java
@RestControllerAdvice(basePackages = "com.middleware.manager.web.api")
@Slf4j
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        log.warn("资源未找到: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, ex.getCode(), ex.getMessage()));
    }
}
```

Controller 只做参数校验（`@Valid`）+ 权限判断（`PermissionService`）+ DTO 转换，业务规则全部下沉 Service。

**模式三：前端组合式函数 —— 模块级单例状态，返回 { state, methods }**（`composables/useNotify.js`）：

```js
import { ref } from 'vue'

const notice = ref(null)          // 模块级：所有调用方共享同一份状态

export function useNotify() {
  function notify(message, type = 'info') {
    notice.value = { message, type }
    const duration = type === 'error' ? 5000 : 3000
    window.setTimeout(() => {
      if (notice.value?.message === message) notice.value = null
    }, duration)
  }
  return { notice, notify, /* confirm 等 */ }
}
```

API 调用统一走 `api.js` 的 `request()`（自动附带 `Authorization: Bearer <token>`），错误处理统一 `catch (error) { notify(error.message, 'error') }`。

## 4. 新增一个功能的配方（以"新增一个受权限管控的管理端 CRUD"为例）

1. **建表**：DDL 加入所属模块的 `src/main/resources/db/`（例如知识模块放 `backend/knowledge/src/main/resources/db/`），在 MySQL `middleware_resource_manager` 库执行。
2. **domain/**：新建 Lombok POJO（`@Data @NoArgsConstructor @AllArgsConstructor`）。
3. **repository/**：新建 Mapper 接口 + `resources/mapper/XxxMapper.xml`（方法命名仿现有：`findById`、`findAllByOrderBy…`、`existsBy…`、`insert`、`update`、`deleteById`）。
4. **constant/**：在 `ErrorCode.java` 加错误码常量、`ErrorMessages.java` 加中文消息（成对出现）。
5. **先写测试**（TDD，见第 8 节）：`backend/<所属模块>/src/test/java/.../service/XxxServiceTest.java`，Mockito mock Mapper，覆盖正常/重复/不存在/无权限分支。
6. **service/**：实现业务逻辑，构造器注入，写操作 `@Transactional`，校验失败抛 `BusinessException(ErrorCode.X, ErrorMessages.X)`。
7. **web/api/dto/**：新建 `XxxRequest`（带 `jakarta.validation` 注解）和 `XxxResponse`。
8. **web/api/**：新建 `AdminXxxApiController`，路径 `/api/admin/xxx`，注入 `PermissionService` 做类目权限过滤；公共只读接口另建 `PublicXxxApiController` 于 `/api/public/xxx`。
9. **SecurityConfig**：为新路径配置角色映射（参照现有 `/api/admin/**` 规则）。
10. **前端**：在 `pages/admin/` 新建 `XxxSection.vue`（`<script setup>`），复用 `components/ui/` 的 `DataTable`/`FormModal`/`BaseButton`；在 `api.js` 加 API 函数；在 `AdminPage.vue` 挂载入口。
11. **验证**：`mvn test`（在 `backend/` 下执行）全绿 → git commit → 调用 `/code-review` skill 检查规范 → 修复问题 → 重启服务（`/restart` skill）人工验证。

## 5. 命名与文件布局约定

**后端**
- 包根：`com.middleware.manager`；独立子域（knowledge/wiki/agent）自带完整分层（entity/repository/service/web）。
- Controller：管理端 `Admin*ApiController`，公共 `Public*ApiController`，路径与实体复数 kebab-case（`/api/admin/software-types`）。
- Service：`XxxService`；Mapper：`XxxMapper`（接口）+ 同名 XML；DTO：`XxxRequest` / `XxxResponse`。
- 常量类 `final` + 私有构造器；异常继承 `BusinessException`。
- 日志：`@Slf4j`，变量名 `log`，参数化占位符 `log.info("msg key={}", value)`。

**前端**
- UI 通用组件：`components/ui/Base*.vue`（或功能名如 `DataTable.vue`）；业务组件 PascalCase 放 `components/`；页面放 `pages/`；管理模块 `pages/admin/*Section.vue`。
- 组合式函数：`composables/useXxx.js`。
- 组件用 `<script setup>` + `defineProps` + `defineEmits`；Props 向下、Events 向上，禁止 `$refs`/`$parent` 通信。
- 样式只用 `styles/tokens.css` 中的 `var(--color-*)` 等设计令牌。

**门户岗位模块**
- 岗位模块编码独立：中间件、数据库、主机、网络和网络安全分别放在 `frontend/src/modules/<job>/`，各自维护入口、路由能力与接口配置；单个岗位可通过独立的 `VITE_<JOB>_API_BASE_URL` 连接自己的后端，也可回退使用门户后端，禁止跨岗位直接依赖业务实现。
- 通用能力代码复用：岗位导航、列表筛选、空状态、详情查看、复制与错误提示等跨岗位能力必须沉淀到 `frontend/src/shared/`、`frontend/src/components/ui/` 或公共组合式函数，禁止复制核心逻辑形成多个版本。
- UI 风格保持一致：公共模块与岗位模块统一使用 `styles/tokens.css` 设计令牌和共享 UI 组件；同类功能的布局、按钮、列表、空状态、错误提示和交互流程必须一致，岗位模块不得自行硬编码颜色、间距或另建重复组件。

## 6. 风格由工具强制

当前仓库**未配置** Checkstyle/Spotless/ESLint/Prettier，风格靠 `docs/development-standards.md` + `/code-review` skill（规则见 `docs/code-review-rules.md`）人工/Agent 审查强制。**建议引入**：后端 `spotless-maven-plugin`（google-java-format）+ Checkstyle；前端 ESLint（`eslint-plugin-vue`）+ Prettier，并挂 pre-commit hook。在引入之前，每次提交后必须执行 `/code-review` skill 作为替代强制手段。

## 7. 命令速查

| 目的 | 命令 |
|------|------|
| 后端编译打包 | `cd backend && mvn clean package -DskipTests` |
| app 启动（:8081） | `cd backend && mvn -pl app -am spring-boot:run` |
| community-service 启动（:8082） | `cd backend && mvn -pl community-service -am spring-boot:run` |
| Gateway 启动（:8080） | `cd backend && mvn -pl api-gateway -am spring-boot:run` |
| 后端测试 | `cd backend && mvn test` |
| 前端安装 | `cd frontend && npm install` |
| 前端开发（:5173，代理 /api、/files 到 Gateway :8080） | `cd frontend && npm run dev` |
| 前端构建 | `cd frontend && npm run build` |
| 打部署包 | `scripts/package-for-deploy.sh` |
| 重启前后端（含 Docker/Milvus） | `/restart` skill |
| 登录拿 Token 做 API 测试 | `/test-login` skill |
| 规范检查 | `/code-review` skill |
| Lint | 暂无（见第 6 节） |

数据库：MySQL 8.0 `127.0.0.1:3306/middleware_resource_manager`，用户 `root`，凭据在 `~/.my.cnf`；连接可用 `APP_DB_*` 环境变量覆盖。

Nacos：默认 profile 明确关闭注册与配置；仅 `cloud` profile 启用，服务名为 `middleware-resource-manager-app`、`community-service` 和 `api-gateway`。Gateway 将 `/api/forum/**` 路由到 community-service，其余 API 和 `/files/**` 路由到 app。端口、环境变量和联通清单见 `docs/microservices-stage2-community-service.md`。

## 8. 测试（严格 TDD）

**测试先行**：任何功能/修复，先在 `backend/<所属模块>/src/test/java` 写失败的测试（Red），再实现（Green），再重构。技术栈：JUnit 5 + Mockito + `spring-boot-starter-test`；Web 层安全用 `@WebMvcTest` 风格的 `*ControllerSecurityTest`（参照 `ops-agent` 模块的 `agent/web/OpsAgentControllerSecurityTest.java`）。

组织方式仿 `wiki/service/LinkResolverTest.java`：`@Mock` mock Mapper，`@BeforeEach` 中 `MockitoAnnotations.openMocks(this)` 后手动 new 被测类；用 `@Nested` + `@DisplayName` 按方法分组。

**每条验收用例带 TC 编号**，写在 `@DisplayName` 中，格式 `TC-<模块>-<三位序号>`，例如：

```java
@Test
@DisplayName("TC-TYPE-001 创建重复的软件类型应抛出 SOFTWARE_TYPE_DUPLICATE")
void createDuplicateThrows() { ... }

@Test
@DisplayName("TC-TYPE-002 查询不存在的 ID 应抛出 NotFoundException")
void getMissingThrows() { ... }
```

最低要求：每个 Service 公开方法覆盖正常路径 + 每个 `BusinessException` 分支；受权限管控的 Controller 必须有 SecurityTest 验证未授权 401/越权 403。PR 中的验收用例清单需列出全部 TC 编号及通过状态。

## 9. 分支与提交

- **一需求 = 一 Issue = 一分支 = 一 PR**。分支命名：`feature/req-<门户ID>-issue-<Issue号>`（例：`feature/req-10086-issue-42`），从 `master` 拉出。
- **禁止直推** `master`；所有变更必须走 PR，评审通过后合入。
- PR 描述必须含 `Closes #<issue>`，并附验收用例 TC 清单与 `mvn test` 结果。
- 提交信息**中文**，格式：`feat(<模块>): <说明> (#<issue>)`，例：`feat(标准管理): 新增参数标准复制功能 (#42)`；类型沿用 feat/fix/docs/chore/release（与现有历史一致，如 `docs: add migration solution reuse principles`）。
- 每次提交后执行 `/code-review` skill，修复问题再进入下一步。

## 10. 禁区与反模式（会被评审打回）

- ❌ 把堆栈、异常类名、SQL 等技术细节返回给前端；只允许 `ApiError(状态码, 错误码, 中文消息)`。
- ❌ 抛原生 `IllegalArgumentException`/`RuntimeException` 表达业务错误；必须用 `BusinessException` 家族 + `ErrorCode`/`ErrorMessages` 常量。
- ❌ 魔法值：错误码、错误消息、状态字符串直接写在业务代码里。
- ❌ 日志字符串拼接 `log.info("x" + var)`；记录密码、Token 明文等敏感信息。
- ❌ Service 用字段注入 `@Autowired`；写操作漏 `@Transactional`；Controller 里写业务规则。
- ❌ Controller 返回 domain 实体而非 Response DTO。
- ❌ 绕过 `PermissionService` 自行判断角色字符串；新增管理端接口不配 SecurityConfig 路由规则。
- ❌ 前端硬编码颜色/间距（必须用 tokens.css 令牌）；`ui/` 组件里出现业务逻辑。
- ❌ 组件间用 `$refs`/`$parent` 通信；`catch` 中 `alert()`、显示 `error.toString()` 或堆栈（必须 `notify(error.message, 'error')`）。
- ❌ 绕过 `api.js` 直接 `fetch`；自行管理 token 存取。
- ❌ 无测试的功能提交；实现先于测试；提交后跳过 `/code-review`。
- ❌ 用 Read 读整个大文件、grep 找定义（必须走 CodeGraph 工具）。

## 11. 踩坑与经验

- **认证已是 Bearer Token**：前端登录后 token 存 `localStorage`（`mrm.token`/`mrm.user`/`mrm.expiresAt`，见 `api.js`），带过期校验；后端有 `TokenService` + `user_token` 表。旧文档提到的 HTTP Basic + sessionStorage 已过时，勿照抄。
- **两套数据访问并存**：主业务用 MyBatis（Mapper 接口 + XML），knowledge 模块用 JdbcTemplate——在 knowledge 下不要引入 MyBatis Mapper，反之亦然。
- **Mapper 接口方法名是 JPA 风格但实现在 XML**：新增查询要同时改接口和 `resources/mapper/*.xml`，两边 id 必须一致，漏改 XML 只在运行时报错。
- **种子数据在 Service 里**：`SoftwareTypeService` 实现 `ApplicationRunner` 在启动时 seed 类目/类型；改类目相关逻辑注意幂等，别造成重复插入。
- **`@RestControllerAdvice` 限定了 basePackages**：`ApiExceptionHandler` 只覆盖 `web.api` 包；wiki/knowledge/agent 的 Controller 若不在该包需确认各自异常处理，否则会漏出 500 白页。
- **外部服务全部走环境变量**：Zabbix（`ZABBIX_URL` 等）、大模型（`AI_BASE_URL`/`AI_API_KEY`）、Milvus（`VECTOR_HOST`/`VECTOR_PORT`）；本地没起 Milvus 时知识库相关功能会失败，开发可用 `InMemoryVectorStore`。
- **模块开关在 `system_settings` 表**（knowledge-enabled、diagnostics-enabled）：功能"不见了"先查开关再查代码。
- **权限模型有两级**：管理员（`isCategoryAdmin`，可改可审）vs 管理岗（`isManagement`，只能改不能审），审核相关接口必须走 `PermissionService.canReview(auth, category)`，只按角色名判断会放过管理岗越权审核。
- **文件存储路径**：上传文件落在 `./storage/<middlewareName>/`，下载走 `/files/**` 且需登录；本地调试删库不删 storage 会出现悬空记录。
