# 后端模块拆分对照表与验收结果

## 1. 拆分结论

阶段 0 将 `backend/` 从单 Maven 工程调整为 16 个子模块加 1 个聚合父工程。运行时仍是单进程、单数据库、单个 Spring Boot 可执行 JAR；Java 包名、API 路径、鉴权规则、SQL 和数据库表结构均未改变。

模块依赖方向为：

```text
common-core <- common-security <- common-web
     ^              ^               ^
     +--------------+---------------+--- 平台模块 / 岗位模块
                                          ^
                                          +--- app（唯一启动模块）
```

每个平台模块和岗位模块的 POM 只声明 `common-core`、`common-security`、`common-web` 三个内部依赖；平台模块之间、平台模块到岗位模块均无 Maven 依赖。`app` 是唯一聚合全部模块并启用 `spring-boot-maven-plugin` 的模块。

## 2. 原包/类到目标模块

表中路径均相对于原目录 `backend/src/main/java/com/middleware/manager/`。

| 目标模块 | 原包/类 | 说明 |
|---|---|---|
| `common-core` | `constant/**`、`exception/**`、`util/TextUtil` | 错误码、错误消息、公共异常和文本工具 |
| `common-core` | `web/api/dto/**`、`web/form/**` | 保留全部既有请求、响应和表单契约 |
| `common-core` | `config/StorageProperties`、`service/StorageService` | 多个平台共同使用的文件存储能力 |
| `common-core` | `domain/{AdminAccount,ParameterStandard,ReleaseAsset,ReviewRecord,ReviewStatus,SoftwareType,StandardDocument,StandardParameter}` | 跨模块接口需要的共享数据契约 |
| `common-core` | `repository/{AdminAccountMapper,ReleaseAssetMapper,SoftwareTypeMapper,StandardDocumentMapper}` | 既有跨边界持久化契约；XML 与接口一并下沉，SQL 不变 |
| `common-core` | `knowledge/agent/{ChatMessage,ChatMessageMapper,ChatSession,ChatSessionMapper}` | knowledge 与 ops-agent 共用的会话契约 |
| `common-core` | `knowledge/loader/DocumentLoader`、`knowledge/store/{VectorStore,VectorSearchFilter}` | knowledge、wiki、ops-agent 共用的文档/向量契约 |
| `common-core` | `wiki/entity/{WikiPage,WikiSource}`、`wiki/repository/WikiSourceMapper` | Wiki 搜索端口和跨模块摄取所需契约 |
| `common-core` | 新增 `EmbeddingProvider`、`KnowledgeSearchPort/Result`、`AccountDirectory`、`SoftwareTypeLookup`、`StandardPackageOperations` | 下沉的跨平台端口 |
| `common-security` | `domain/RoleEntity`、`security/{PermissionService,TokenAuthenticationFilter}` | 角色、category 岗位权限和 Token 过滤 |
| `common-security` | 新增 `RolePermissionProvider`、`TokenValidator`、`WikiSearchPort/Result` | identity/wiki 实现的安全与检索端口 |
| `common-web` | `config/{AccessLogFilter,ApiAuditFilter,MultipartConfig,SecurityConfig}`、`web/api/ApiExceptionHandler` | Web 过滤、鉴权配置和全局异常处理 |
| `common-web` | 新增 `config/ApiAuditLogger` | 解除 common-web 到 identity 的实现依赖 |
| `identity` | `domain/{ApiAuditLog,SystemSetting}`、`repository/{ApiAuditLogMapper,RoleMapper,SystemSettingMapper,UserTokenMapper}` | 身份与访问数据 |
| `identity` | `service/{AdminAccountService,ApiAuditLogService,RoleService,SystemSettingService,TokenService}` | 身份、Token、角色、设置和审计实现 |
| `identity` | `web/api/{AdminAccountApiController,AdminSettingController,AdminUserController,AuthApiController,PublicConfigController}` | 原路径不变 |
| `identity` | 新增 `service/IdentityStartupInitializer` | 保持角色先于默认账号初始化；生产默认启用 |
| `catalog` | `domain/SoftwareCategory`、`repository/SoftwareCategoryMapper` | 软件目录数据 |
| `catalog` | `service/{ReleaseService,SoftwareTypeService}` | 发布包、软件分类/类型 |
| `catalog` | `web/api/{AdminReleaseApiController,AdminSoftwareCategoryApiController,AdminSoftwareTypeApiController,PublicReleaseApiController}`、`web/controller/FileDownloadController` | 目录和下载端点 |
| `catalog` | 新增 `service/CatalogStartupInitializer` | 保持默认软件分类初始化；生产默认启用 |
| `standards` | `domain/{DocumentRevision,DocumentStatus}` | 标准文档版本数据；其余共享标准模型下沉到 common-core |
| `standards` | `repository/{DocumentRevisionMapper,ParameterStandardMapper,ReviewRecordMapper,StandardParameterMapper}` | 标准与评审持久化 |
| `standards` | `service/{DocumentConversionService,ParameterStandardService,ReviewService,StandardDocumentService,StandardPackageService,StandardParameterService,VersionManager}` | 标准、文档、评审和标准包 |
| `standards` | `web/api/{AdminParameterStandardController,AdminStandardDocumentApiController,AdminStandardParameterApiController,DocumentRevisionController,ImageController,PublicParameterStandardController,PublicStandardDocumentApiController,PublicStandardParameterApiController,ReviewApiController}` | 原路径不变 |
| `knowledge` | 原 `knowledge/**`，不含已列入 common-core 的共享类 | 知识导入、切分、Embedding、向量库、RAG Agent 和控制器 |
| `wiki` | 原 `wiki/**`，不含 `WikiPage`、`WikiSource`、`WikiSourceMapper` | Wiki 摄取、检索、图谱、权限和控制器 |
| `community` | `domain/{ForumComment,ForumPost,ForumTag,PostLike}`、对应 `repository/**`、`service/ForumService`、`web/api/ForumController` | 社区论坛完整边界 |
| `ops-agent` | 原 `agent/**` | 运维 Agent、Tool、Skill、Zabbix 和 Excel 导出 |
| `job-middleware` | `domain/MiddlewareCommand`、`repository/MiddlewareCommandMapper`、`service/MiddlewareCommandService`、`web/api/MiddlewareCommandApiController` | 岗位专属 `/api/middleware-commands` |
| `job-database` | 新增 `job/database/package-info.java` | 与前端 `modules/database` 对齐的空边界 |
| `job-host` | 新增 `job/host/package-info.java` | 与前端 `modules/host` 对齐的空边界 |
| `job-network` | 新增 `job/network/package-info.java` | 与前端 `modules/network` 对齐的空边界 |
| `job-security` | 新增 `job/security/package-info.java` | 与前端 `modules/network-security` 对齐的安全岗位边界 |
| `app` | `MiddlewareResourceManagerApplication`、`config/{ChatModelConfig,ModuleProperties}` | 唯一启动/装配模块，扫描根包 `com.middleware.manager` |

原有 194 个生产 Java 文件和 16 个测试类已全部迁移；新增文件仅为跨模块端口、启动初始化器和空岗位边界标记。

## 3. 资源归属

| 目标模块 | 原资源 |
|---|---|
| `app` | `application.yml`、`application-local.yml`、`application-prod.yml` |
| `common-core` | `AdminAccountMapper.xml`、`ChatMessageMapper.xml`、`ChatSessionMapper.xml`、`ReleaseAssetMapper.xml`、`SoftwareTypeMapper.xml`、`StandardDocumentMapper.xml`、`WikiSourceMapper.xml` |
| `identity` | `ApiAuditLogMapper.xml`、`RoleMapper.xml`、`SystemSettingMapper.xml`、`UserTokenMapper.xml`、`db/api_audit_log_ddl.sql` |
| `catalog` | `SoftwareCategoryMapper.xml` |
| `standards` | `DocumentRevisionMapper.xml`、`ParameterStandardMapper.xml`、`ReviewRecordMapper.xml`、`StandardParameterMapper.xml`、`db/migration/**` |
| `knowledge` | `KnowledgeChunkMapper.xml`、`db/knowledge_ddl.sql` |
| `wiki` | 7 份 Wiki Mapper XML、`db/wiki*.sql` |
| `community` | 4 份 Forum Mapper XML |
| `ops-agent` | `AgentToolInvocationMapper.xml`、`skills/*.yaml`、`db/agent*.sql` |
| `job-middleware` | `MiddlewareCommandMapper.xml`、`commands/commands.json`、`db/middleware_commands*.sql` |

MyBatis 的 `mapper-locations` 由 `classpath:mapper/*.xml` 调整为 `classpath*:mapper/*.xml`，只扩展资源查找范围以支持模块 JAR；30 份 XML 内容哈希与拆分前完全一致。

## 4. 跨边界耦合处理

| 原耦合 | 解耦方式 | 实现模块 |
|---|---|---|
| `common-security -> identity` 的 `RoleService` / `TokenService` / `AdminAccountService` | `RolePermissionProvider`、`TokenValidator`、标准 `UserDetailsService` | `identity` |
| `common-web -> identity` 的 `ApiAuditLogService` | `ApiAuditLogger` | `identity` |
| `catalog -> standards` 的 `StandardPackageService` | `StandardPackageOperations` | `standards` |
| `standards -> catalog` 的 `SoftwareTypeService` | `SoftwareTypeLookup` | `catalog` |
| `standards -> identity` 的账号显示名查询 | `AccountDirectory` | `identity` |
| `wiki -> knowledge` 的 Embedding 实现 | `EmbeddingProvider` | `knowledge` |
| `knowledge/ops-agent -> wiki` 的混合检索 | `WikiSearchPort` + `WikiSearchResult` | `wiki` |
| `ops-agent -> knowledge` 的知识检索 | `KnowledgeSearchPort` + `KnowledgeSearchResult` | `knowledge` |

端口方法签名和迁移前具体类的公开方法保持一致；调用逻辑未改变。少量原有 Mapper 和领域对象因被多个边界直接使用，作为共享持久化/数据契约下沉到 `common-core`，避免制造平台模块间 Maven 依赖；后续微服务阶段可再用服务 API 替代。

## 5. 构建与部署

- 聚合父工程：`backend/pom.xml`
- 唯一启动模块：`backend/app`
- 唯一可执行产物：`backend/app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar`
- 可执行 JAR 的 `BOOT-INF/lib` 已包含 3 个 common、7 个平台和 5 个岗位模块 JAR。
- `scripts/package-for-deploy.sh`、两个本地 JAR PowerShell 脚本及部署文档均已切换到新路径。

## 6. 验收结果

执行日期：2026-07-20。

| 验收项 | 结果摘要 |
|---|---|
| `cd backend && mvn -DskipTests clean package` | `BUILD SUCCESS`，17/17 reactor 项成功；生成单一 `app/target/*-exec.jar` |
| `cd backend && mvn test` | `BUILD SUCCESS`，17/17 reactor 项成功；83 tests，0 failures，0 errors，0 skipped |
| `mvn spring-boot:run` 聚合行为 | 前 16 个库模块跳过运行，仅 `app` 启动；完整加载 Spring 上下文及 6 个内置 Skill。受执行沙箱禁止监听 TCP 端口限制，Tomcat bind 被拒绝；`@SpringBootTest` 上下文验收通过 |
| API 契约静态核对 | 拆分前后 174 条 Spring Mapping 注解逐字一致 |
| 鉴权规则核对 | `SecurityConfig.java` 拆分前后逐字一致 |
| DB 访问核对 | 30 份 Mapper XML 逐文件哈希一致；无 schema/DML 改动 |
| Maven 边界核对 | 7 个平台模块无岗位依赖、无平台互依；5 个岗位模块与前端岗位一一对应 |
| 测试语义 | 16 个原测试类全部保留；仅 `IngestAgentTest` 的 mock 声明由具体类改为等价端口 |

