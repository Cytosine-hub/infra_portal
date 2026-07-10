# 数据迁移模块架构设计

## 目标

数据迁移模块用于提供常用数据库和对象存储之间的迁移能力，首批目标覆盖 MySQL、Oracle、OceanBase、OSS 等场景。模块只提供迁移执行能力，不保存源端或目标端数据库地址、账号、密码、Token、临时密钥等敏感连接信息。

## 设计原则

- 可插拔：新增数据库类型时，只增加插件实现，不修改迁移主流程。
- 无敏感持久化：连接参数只在本次请求或运行中的任务上下文中使用，日志、数据库、导出文件均不保存密码和密钥。
- 能力声明：每个插件声明支持的对象类型、DDL 能力、批量写入能力、分页策略、校验能力。
- 分层执行：连接、元数据读取、迁移计划、数据抽取、转换、写入、校验分层处理。
- 可灰度扩展：先支持 dry-run 和迁移计划预览，再逐步开放真实执行。

## 建议包结构

```text
src/main/java/com/middleware/manager/migration/
├── api/                  # REST API 和 DTO
├── core/                 # 迁移编排、计划、上下文、执行管道
├── plugin/               # 插件接口、注册中心、能力声明
├── connector/            # 数据源连接适配
├── dialect/              # 方言、类型映射、DDL 转换
├── pipeline/             # Reader / Transformer / Writer / Validator
├── security/             # 参数脱敏、敏感字段过滤
└── plugins/
    ├── mysql/
    ├── oracle/
    ├── oceanbase/
    └── oss/
```

前端建议：

```text
frontend/src/pages/DataMigrationPage.vue
frontend/src/components/migration/
├── MigrationPlanWizard.vue
├── ConnectionParameterForm.vue
├── MigrationCapabilityPanel.vue
└── MigrationDryRunResult.vue
```

## 核心对象

### MigrationRequest

一次迁移请求的入口对象，只在请求和任务运行期传递，不落库保存敏感连接信息。

```text
sourceType        源端类型，如 MYSQL / ORACLE / OCEANBASE / OSS
targetType        目标端类型
sourceParameters  源端连接参数，运行期使用
targetParameters  目标端连接参数，运行期使用
objects           待迁移对象，如 schema/table/prefix
options           批大小、并发度、是否迁移索引、是否校验数据等
```

### MigrationPlan

迁移计划不包含密码，只描述迁移动作。

```text
steps             结构迁移、数据迁移、索引迁移、校验等步骤
objectMappings    源对象到目标对象映射
typeMappings      字段类型映射
warnings          风险提示，例如不兼容类型、缺失主键、大对象字段
```

### MigrationPlugin

所有数据库插件实现统一接口。

```text
type()                       返回数据库类型
capability()                 返回插件能力声明
createConnector(parameters)  创建连接适配器
dialect()                    返回方言和类型映射
createReader(context)        创建数据读取器
createWriter(context)        创建数据写入器
createValidator(context)     创建校验器
```

## 设计模式

- Strategy：不同数据库的分页读取、批量写入、DDL 转换、类型映射采用不同策略。
- Factory：按数据库类型创建 Connector、Dialect、Reader、Writer、Validator。
- Template Method：迁移主流程固定为校验参数、生成计划、执行迁移、校验结果，具体步骤由插件覆盖。
- Adapter：屏蔽 MySQL JDBC、Oracle JDBC、OceanBase MySQL 模式、OSS SDK/API 差异。
- Chain of Responsibility：迁移前检查、字段映射、类型转换、脱敏、过滤器按链路组合。
- Registry：插件启动时注册到 MigrationPluginRegistry，由编排服务按类型选择插件。

## 安全边界

- 不保存源端/目标端地址、账号、密码、Token、AK/SK。
- 不在日志中输出完整 JDBC URL、密码、Token、AK/SK。
- API 响应中只返回脱敏后的参数摘要，例如 `host=10.***.***.12`。
- dry-run 结果和迁移报告只保存对象名、行数、校验摘要、错误码和中文错误描述。
- 后端异常使用业务错误码和中文消息，不向前端返回堆栈和驱动原始异常。

## 首期 API 设计

```text
GET  /api/admin/migration/plugins
返回当前可用插件、能力声明和参数 schema。

POST /api/admin/migration/dry-run
校验连接参数，读取元数据，生成迁移计划预览。请求体包含运行期连接参数，不持久化。

POST /api/admin/migration/execute
根据 dry-run 后的计划执行迁移。第一阶段可先不开放，或只支持小数据量实验。
```

## 分阶段路线

1. 架构骨架：插件接口、能力声明、参数 schema、脱敏工具、dry-run DTO。
2. MySQL 插件：连接校验、表结构读取、类型映射、分页读取、批量写入。
3. OceanBase 插件：优先支持 MySQL 模式，复用 MySQL 策略并覆盖兼容差异。
4. Oracle 插件：补齐 schema、sequence、number/date/clob/blob 类型映射。
5. OSS 插件：支持文件中转、导出导入、断点续传和校验摘要。
6. 执行观测：运行期进度、错误归类、迁移报告导出。敏感参数不落库。

## 暂不做

- 不保存数据库连接配置。
- 不做长期迁移任务恢复，除非后续引入外部密钥管理或一次性凭证机制。
- 不自动迁移存储过程、触发器、复杂权限，首期只给出兼容性报告。
