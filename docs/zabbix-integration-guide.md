# Zabbix 监控数据集成指南

## 功能概述

本系统已集成 Zabbix 监控数据查询和 Excel 导出功能，支持：

1. **实时查询** - 通过 Agent 对话查询 Zabbix 监控数据
2. **数据导出** - 将监控数据导出为 Excel 文件
3. **批量导出** - 支持多主机数据批量导出

## 配置说明

### 环境变量配置

在 `application.yml` 中配置 Zabbix 连接信息：

```yaml
app:
  zabbix:
    url: ${ZABBIX_URL:http://localhost:8080/api_jsonrpc.php}
    username: ${ZABBIX_USERNAME:Admin}
    password: ${ZABBIX_PASSWORD:zabbix}
    timeout: ${ZABBIX_TIMEOUT:30}
```

**支持的环境变量：**

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `ZABBIX_URL` | Zabbix API 地址 | http://localhost:8080/api_jsonrpc.php |
| `ZABBIX_USERNAME` | Zabbix 用户名 | Admin |
| `ZABBIX_PASSWORD` | Zabbix 密码 | zabbix |
| `ZABBIX_TIMEOUT` | 连接超时时间（秒） | 30 |

### 启动示例

```bash
# 使用默认配置
mvn spring-boot:run

# 自定义 Zabbix 地址
ZABBIX_URL=http://zabbix.example.com/api_jsonrpc.php \
ZABBIX_USERNAME=your_user \
ZABBIX_PASSWORD=your_password \
mvn spring-boot:run
```

## 使用方式

### 1. 通过 Agent 对话查询

在前端智能排查面板中，可以直接询问 Agent 查询 Zabbix 数据：

**示例对话：**

```
用户: 查询主机 web-server-01 的 CPU 使用率
Agent: [调用 zabbix_query 工具]
       查询结果（10条）：
       ## CPU 使用率
       主机: web-server-01
       单位: %

       | 时间 | 值 |
       |------|-----|
       | 2026-05-26 10:30:00 | 45.2 |
       | 2026-05-26 10:29:00 | 42.8 |
       ...
```

**支持的参数：**

| 参数 | 说明 | 示例 |
|------|------|------|
| host | 主机名（必填） | web-server-01 |
| metric | 指标关键字（可选） | cpu, memory, disk |
| timeRange | 时间范围（可选） | 1h, 1d, 30m |
| limit | 返回条数（可选） | 100 |

### 2. 通过 REST API 查询

**查询监控数据：**

```bash
curl -X POST http://localhost:8080/api/ops-agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "查询主机 web-server-01 的内存使用情况",
    "context": {}
  }'
```

### 3. 导出 Excel 文件

**单主机导出：**

```bash
curl -o monitoring_data.xlsx \
  "http://localhost:8080/api/ops-agent/export/zabbix?host=web-server-01&metric=cpu&timeRange=1d&limit=1000"
```

**批量导出：**

```bash
curl -X POST http://localhost:8080/api/ops-agent/export/zabbix/batch \
  -H "Content-Type: application/json" \
  -o batch_monitoring.xlsx \
  -d '{
    "hosts": ["web-server-01", "web-server-02", "db-server-01"],
    "metric": "cpu",
    "timeRange": "1d",
    "limit": 1000
  }'
```

### 4. 通过 Agent 导出

在对话中请求 Agent 导出数据：

```
用户: 导出主机 web-server-01 最近 1 天的 CPU 监控数据为 Excel
Agent: [调用 zabbix_export 工具]
       ✅ 已成功导出 1440 条监控数据到 Excel 文件

       文件路径: /tmp/web-server-01_monitoring_data.xlsx
       文件大小: 125.50 KB
```

## Skill 自动匹配

系统内置了 Zabbix 监控相关的 Skill，当用户输入包含以下关键词时会自动触发：

- zabbix
- 监控
- 监控数据
- 主机监控
- 性能监控
- 导出监控

**示例：**

```
用户: 查看 zabbix 监控数据
Agent: [自动匹配 zabbix-monitor Skill]
       请提供以下信息：
       1. 主机名（必填）
       2. 指标关键字（可选，如 cpu, memory, disk）
       3. 时间范围（可选，默认 1 小时）
```

## API 接口说明

### 1. Agent 对话接口

**POST** `/api/ops-agent/chat`

请求体：
```json
{
  "message": "查询主机 xxx 的监控数据",
  "context": {
    "host": "web-server-01",
    "metric": "cpu",
    "timeRange": "1h"
  }
}
```

响应：
```json
{
  "response": "查询结果...",
  "skill": "zabbix-monitor",
  "availableTools": ["zabbix_query", "zabbix_export", ...]
}
```

### 2. Excel 导出接口

**GET** `/api/ops-agent/export/zabbix`

参数：
- `host` (必填) - 主机名
- `metric` (可选) - 指标关键字
- `timeRange` (可选) - 时间范围，默认 1h
- `limit` (可选) - 返回条数，默认 1000

响应：Excel 文件下载

### 3. 批量导出接口

**POST** `/api/ops-agent/export/zabbix/batch`

请求体：
```json
{
  "hosts": ["web-server-01", "web-server-02"],
  "metric": "cpu",
  "timeRange": "1d",
  "limit": 1000
}
```

响应：Excel 文件下载

## 常见问题

### 1. 连接 Zabbix 失败

**错误信息：** `查询 Zabbix 失败: Connection refused`

**解决方案：**
- 检查 Zabbix 服务是否正常运行
- 确认 `ZABBIX_URL` 配置是否正确
- 检查网络连接和防火墙设置

### 2. 认证失败

**错误信息：** `Zabbix API error: Login name or password is incorrect`

**解决方案：**
- 检查 `ZABBIX_USERNAME` 和 `ZABBIX_PASSWORD` 配置
- 确认 Zabbix 用户有 API 访问权限

### 3. 找不到主机

**错误信息：** `Host not found: xxx`

**解决方案：**
- 确认主机名拼写正确
- 检查 Zabbix 中是否已添加该主机
- 使用 Zabbix 前端确认主机名称

### 4. 导出数据为空

**提示信息：** `未找到匹配的监控数据，无法导出。`

**解决方案：**
- 检查时间范围内是否有数据
- 确认指标关键字是否正确
- 尝试扩大时间范围或移除指标过滤

## 扩展开发

### 添加新的监控工具

1. 在 `src/main/java/com/middleware/manager/agent/tool/` 目录下创建新的 Tool 类
2. 实现 `Tool` 接口
3. 使用 `@Component` 注解注册为 Spring Bean
4. 系统会自动将其注册到 Agent

**示例：**

```java
@Component
public class CustomMonitorTool implements Tool {
    @Override
    public String name() { return "custom_monitor"; }

    @Override
    public String description() { return "自定义监控工具"; }

    @Override
    public String call(Map<String, Object> params) {
        // 实现监控逻辑
        return "监控结果";
    }
}
```

### 添加新的 Skill

在 `src/main/resources/skills/` 目录下创建 YAML 文件：

```yaml
name: custom-skill
description: "自定义排查技能"
trigger:
  keywords: ["关键词1", "关键词2"]
steps:
  - tool: zabbix_query
    args:
      host: "{{host}}"
    description: "查询监控数据"
  - prompt: "分析监控数据"
```

## 最佳实践

1. **合理设置时间范围** - 避免查询过大时间范围导致数据量过大
2. **使用指标过滤** - 通过 metric 参数过滤特定指标，提高查询效率
3. **分批导出** - 大量数据建议分多次导出，避免内存溢出
4. **定期清理** - 临时导出文件会占用磁盘空间，建议定期清理 `/tmp` 目录
