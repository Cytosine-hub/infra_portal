---
name: code-review
description: 按照开发规范检查代码质量，自动修复不合规项
---

# 代码规范检查

按照 `docs/development-standards.md` 检查最近修改的代码，自动修复不合规项。

## 检查流程

### 1. 获取变更文件

```bash
# 获取最近修改的文件（未提交的变更）
git diff --name-only
git diff --cached --name-only

# 如果没有未提交变更，检查最近一次提交
git diff --name-only HEAD~1
```

### 2. 启动子代理并行检查

对变更文件按类型分组，并行检查：

**后端检查（*.java）：**
- 异常处理：是否使用 `BusinessException`，是否有堆栈返回前端风险
- 常量管理：是否有魔法值（硬编码字符串/数字）
- 日志规范：是否使用 `@Slf4j`，是否参数化日志
- 代码规范：实体注解、Service 注入、Controller 返回类型

**前端检查（*.vue, *.js）：**
- 样式规范：是否使用设计令牌，是否有硬编码颜色
- 组件规范：是否使用 `<script setup>`， Props/Events 是否正确
- API 处理：错误处理是否显示中文描述
- 组合式函数：是否遵循模块级单例模式

### 3. 检查项清单

#### 后端检查

| 检查项 | 规则 | 自动修复 |
|--------|------|----------|
| 异常类 | 使用 `BusinessException(ErrorCode)` | 替换 `throw new IllegalArgumentException` |
| 错误消息 | 使用 `ErrorMessages.*` 常量 | 提取硬编码中文到常量类 |
| 错误码 | 使用 `ErrorCode.*` 常量 | 创建缺失的错误码 |
| 日志声明 | 使用 `@Slf4j` 注解 | 替换手动 Logger 声明 |
| 日志格式 | 参数化 `log.info("k={}", v)` | 转换字符串拼接 |
| 敏感信息 | 日志不含密码/Token | 删除敏感信息日志 |
| 堆栈返回 | 异常处理器不返回堆栈 | 确保全局处理器只返回中文 |
| 魔法值 | 使用常量类 | 提取到 `constant/` |
| 实体注解 | `@Data @NoArgsConstructor @AllArgsConstructor` | 添加缺失注解 |
| 注入方式 | 构造器注入 | 转换 `@Autowired` 字段注入 |
| 事务注解 | 写操作加 `@Transactional` | 添加缺失注解 |

#### 前端检查

| 检查项 | 规则 | 自动修复 |
|--------|------|----------|
| 颜色值 | 使用 `var(--color-*)` | 替换硬编码 hex 颜色 |
| 间距值 | 使用 `var(--space-*)` | 替换硬编码 px 值 |
| 组件结构 | `<script setup>` + `defineProps` | 调整组件结构 |
| 错误处理 | `notify(error.message, 'error')` | 替换 `error.toString()` |
| 组件命名 | UI 用 `Base*`，业务用 PascalCase | 重命名文件 |
| 模态框 | 使用 `FormModal` 或 `BaseModal` | 替换自定义模态框 |

### 4. 执行检查

```javascript
// 伪代码：检查流程
const changedFiles = getChangedFiles()
const backendFiles = changedFiles.filter(f => f.endsWith('.java'))
const frontendFiles = changedFiles.filter(f => f.endsWith('.vue') || f.endsWith('.js'))

// 并行检查
const [backendIssues, frontendIssues] = await parallel([
  checkBackend(backendFiles),
  checkFrontend(frontendFiles)
])

// 输出报告
report(backendIssues, frontendIssues)

// 自动修复（如果用户确认）
if (hasAutoFix(issues)) {
  await autoFix(issues)
}
```

### 5. 输出报告格式

```
## 代码规范检查报告

### 检查文件
- 后端: 5 个文件
- 前端: 3 个文件

### 发现问题: 8 个

#### 后端问题 (5 个)
1. ❌ `UserService.java:45` - 使用 `throw new IllegalArgumentException` 
   → 应使用 `throw new BusinessException(ErrorCode.USER_NOT_FOUND)`
   → [自动修复]

2. ⚠️ `UserService.java:67` - 日志使用字符串拼接
   → 应使用 `log.info("user id={}", id)`
   → [自动修复]

3. ❌ `OrderService.java:23` - 硬编码错误消息 "订单不存在"
   → 应使用 `ErrorMessages.ORDER_NOT_FOUND`
   → [自动修复]

#### 前端问题 (3 个)
1. ⚠️ `UserList.vue:15` - 硬编码颜色 `#2356a5`
   → 应使用 `var(--color-primary)`
   → [自动修复]

2. ⚠️ `UserList.vue:42` - 错误处理显示堆栈
   → 应使用 `notify(error.message, 'error')`
   → [自动修复]

### 自动修复: 5 个
### 需手动处理: 3 个
```

### 6. 自动修复示例

```java
// 修复前
throw new IllegalArgumentException("用户不存在");

// 修复后
throw new BusinessException(ErrorCode.USER_NOT_FOUND, ErrorMessages.USER_NOT_FOUND);
```

```javascript
// 修复前
color: #2356a5;

// 修复后
color: var(--color-primary);
```

```javascript
// 修复前
catch (error) {
  notify(error.toString(), 'error')
}

// 修复后
catch (error) {
  notify(error.message || '操作失败', 'error')
}
```

## 使用方式

```
/code-review          # 检查未提交的变更
/code-review --last   # 检查最近一次提交
/code-review --fix    # 检查并自动修复
```

## 相关文档

- 完整规范：`docs/development-standards.md`
- 设计令牌：`frontend/src/styles/tokens.css`
- 错误码：`src/main/java/com/middleware/manager/constant/ErrorCode.java`
- 错误消息：`src/main/java/com/middleware/manager/constant/ErrorMessages.java`
