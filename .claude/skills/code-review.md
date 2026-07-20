---
name: code-review
description: 按照开发规范检查代码质量，自动修复不合规项
---

# 代码规范检查

按照 `docs/development-standards.md` 检查最近修改的代码，输出问题报告，经确认后自动修复。

## 执行流程

### 第一步：获取变更文件

```bash
git diff --name-only          # 未暂存的修改
git diff --cached --name-only # 已暂存的修改
# 若两者均为空，检查最近一次提交
git diff --name-only HEAD~1
```

只处理以下类型：`.java` / `.vue` / `.js` / `.ts`。  
跳过：`.css` `.xml` `.yml` `.sql` `.md` `.json` `.sh` `.properties`。

### 第二步：并行检查

对变更文件按类型分组，用子代理并行检查后端和前端。

---

## 后端规则（.java）

### ✅ 必须修复

| 问题 | 错误示例 | 正确写法 |
|------|---------|---------|
| 原生异常 | `throw new IllegalArgumentException("x")` | `throw new BusinessException(ErrorCode.X, ErrorMessages.X)` |
| 日志拼接 | `log.info("id=" + id)` | `log.info("id={}", id)` |
| 硬编码中文错误消息 | `return "用户不存在"` 直接写在业务方法 | 提取到 `ErrorMessages` 常量 |

### ⚠️ 注意判断，不要误报

- **DTO 字段、注解参数、方法名** 不是魔法值，无需提取
- **`catch (Exception e) { log.error("msg", e) }`** 是正确写法，不要修改
- **`response.setXxx("PUBLISHED")`** 等状态字符串若已在 `StatusConstants` 中定义过则不算魔法值；若是单次使用的枚举值可不提取
- **`@Transactional` 缺失**：只报告明确有数据库写操作（insert/update/delete）且没有加注解的 Service 方法，查询方法不需要加
- **`@Data @NoArgsConstructor @AllArgsConstructor`**：只对实体类（domain/entity 包）要求，DTO 类不强制

### 🚫 跳过不检查

- 已有的旧代码（本次未修改的行）
- 测试文件（`*Test.java`、`*IT.java`）
- 配置类（`*Config.java`）的常量定义

---

## 前端规则（.vue / .js / .ts）

### ✅ 必须修复

| 问题 | 错误示例 | 正确写法 |
|------|---------|---------|
| Hex 颜色 | `color: #2356a5` | `color: var(--color-primary)` |
| 错误堆栈 | `notify(error.toString(), 'error')` | `notify(error.message \|\| '操作失败', 'error')` |

### ⚠️ 注意判断，不要误报

- **`rgba()`、`hsla()`、`#000`/`#fff`/`#ffffff`** 是可接受的，不算硬编码颜色
- **`box-shadow`、`border`** 里的颜色值若无对应设计令牌，不强制替换
- **`styles.css` 全局样式文件** 不检查设计令牌，那是令牌的定义文件本身
- **已有组件**（非本次新建）不报告「缺少 `<script setup>`」
- **`error.message`** 已经是正确写法，不要报告
- **内联 style** 里的 `var(--color-*)` 已是正确写法

### 🚫 跳过不检查

- `*.css` 文件（全局样式，不适用组件规范）
- `api.js`、`utils.js` 等工具文件的内部实现细节

---

## 输出格式

```
## 代码规范检查报告

### 变更文件
- 后端: N 个 .java 文件
- 前端: N 个 .vue/.js 文件
- 跳过: N 个（css/yml/sql 等）

### 问题清单（共 N 个）

#### 后端（N 个）
1. ❌ `UserService.java:45` — 原生异常
   现在：`throw new IllegalArgumentException("用户不存在")`
   应为：`throw new BusinessException(ErrorCode.USER_NOT_FOUND, ErrorMessages.USER_NOT_FOUND)`

#### 前端（N 个）
1. ⚠️ `UserCard.vue:23` — Hex 颜色
   现在：`color: #2356a5`
   应为：`color: var(--color-primary)`

### 无问题 ✅
（若无问题则只写这一行）
```

---

## 使用方式

```
/code-review            # 检查未提交的变更（默认）
/code-review --last     # 检查最近一次提交
/code-review --fix      # 检查并自动修复（需确认）
```

## 参考文件

- 完整规范：`docs/development-standards.md`
- 设计令牌：`frontend/src/styles/tokens.css`
- 错误码：`backend/src/main/java/com/middleware/manager/constant/ErrorCode.java`
- 错误消息：`backend/src/main/java/com/middleware/manager/constant/ErrorMessages.java`
- 状态常量：`backend/src/main/java/com/middleware/manager/constant/StatusConstants.java`
