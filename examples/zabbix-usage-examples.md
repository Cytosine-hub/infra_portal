# Zabbix 集成使用示例

## 快速开始

### 1. 配置环境变量

```bash
# 设置 Zabbix 连接信息
export ZABBIX_URL="http://your-zabbix-server/api_jsonrpc.php"
export ZABBIX_USERNAME="your_username"
export ZABBIX_PASSWORD="your_password"

# 启动应用
mvn spring-boot:run
```

### 2. 测试连接

```bash
# 通过 Agent 测试连接
curl -X POST http://localhost:8080/api/ops-agent/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -d '{
    "message": "列出所有 Zabbix 主机",
    "context": {}
  }'
```

## 常用查询示例

### 示例 1: 查询 CPU 使用率

**Agent 对话方式：**

```
用户: 查询主机 web-server-01 的 CPU 使用率
```

**REST API 方式：**

```bash
curl -X POST http://localhost:8080/api/ops-agent/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -d '{
    "message": "查询主机 web-server-01 的 CPU 使用率",
    "context": {
      "host": "web-server-01",
      "metric": "cpu",
      "timeRange": "1h"
    }
  }'
```

### 示例 2: 查询内存使用情况

**Agent 对话方式：**

```
用户: 查看 db-server-01 最近 1 天的内存使用趋势
```

**REST API 方式：**

```bash
curl -X POST http://localhost:8080/api/ops-agent/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -d '{
    "message": "查看 db-server-01 最近 1 天的内存使用趋势",
    "context": {
      "host": "db-server-01",
      "metric": "memory",
      "timeRange": "1d"
    }
  }'
```

### 示例 3: 查询磁盘空间

**Agent 对话方式：**

```
用户: 检查 app-server-01 的磁盘使用情况
```

**REST API 方式：**

```bash
curl -X POST http://localhost:8080/api/ops-agent/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -d '{
    "message": "检查 app-server-01 的磁盘使用情况",
    "context": {
      "host": "app-server-01",
      "metric": "disk"
    }
  }'
```

### 示例 4: 综合监控分析

**Agent 对话方式：**

```
用户: 分析 web-server-01 的整体健康状态
```

**REST API 方式：**

```bash
curl -X POST http://localhost:8080/api/ops-agent/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -d '{
    "message": "分析 web-server-01 的整体健康状态",
    "context": {
      "host": "web-server-01",
      "timeRange": "1h"
    }
  }'
```

## Excel 导出示例

### 示例 1: 导出单主机数据

```bash
# 导出 web-server-01 的 CPU 数据
curl -o web-server-01-cpu.xlsx \
  "http://localhost:8080/api/ops-agent/export/zabbix?host=web-server-01&metric=cpu&timeRange=1d&limit=1000" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)"

# 导出所有指标
curl -o web-server-01-all.xlsx \
  "http://localhost:8080/api/ops-agent/export/zabbix?host=web-server-01&timeRange=1d" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)"
```

### 示例 2: 批量导出多主机数据

```bash
# 创建请求文件
cat > export-request.json << EOF
{
  "hosts": [
    "web-server-01",
    "web-server-02",
    "db-server-01",
    "app-server-01"
  ],
  "metric": "cpu",
  "timeRange": "1d",
  "limit": 1000
}
EOF

# 批量导出
curl -X POST http://localhost:8080/api/ops-agent/export/zabbix/batch \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -d @export-request.json \
  -o batch-monitoring.xlsx
```

### 示例 3: 通过 Agent 导出

```
用户: 导出 web-server-01 和 db-server-01 最近 24 小时的 CPU 和内存数据
Agent: [调用 zabbix_export 工具]
       ✅ 已成功导出监控数据到 Excel 文件

       文件信息：
       - 主机: web-server-01, db-server-01
       - 指标: CPU, 内存
       - 时间范围: 24 小时
       - 数据条数: 2880 条
       - 文件大小: 256.80 KB
       - 文件路径: /tmp/zabbix_batch_20260526_173000.xlsx
```

## 高级用法

### 1. 自定义查询参数

```bash
# 查询特定时间段的数据
curl -X POST http://localhost:8080/api/ops-agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "查询 web-server-01 在 2026-05-25 10:00 到 2026-05-25 12:00 之间的 CPU 使用率",
    "context": {}
  }'
```

### 2. 对比分析

```
用户: 对比 web-server-01 和 web-server-02 的 CPU 使用率
Agent: [调用 zabbix_query 查询两个主机]
       对比分析结果：

       ## web-server-01
       - 平均 CPU 使用率: 45.2%
       - 峰值: 78.5%
       - 趋势: 稳定

       ## web-server-02
       - 平均 CPU 使用率: 62.8%
       - 峰值: 95.2%
       - 趋势: 上升

       结论: web-server-02 CPU 使用率明显偏高，建议检查该服务器上的进程。
```

### 3. 告警关联分析

```
用户: web-server-01 响应变慢，帮我分析原因
Agent: [自动触发 zabbix-monitor Skill]
       正在排查 web-server-01 响应变慢问题...

       ## 监控数据分析
       1. CPU 使用率: 正常（平均 35%）
       2. 内存使用: 偏高（85%）
       3. 磁盘 I/O: 异常（等待时间长）
       4. 网络流量: 正常

       ## 结论
       根因: 磁盘 I/O 瓶颈导致响应变慢
       建议:
       1. 检查磁盘空间是否不足
       2. 检查是否有大量写入操作
       3. 考虑升级 SSD 或优化存储
```

### 4. 定期报告生成

```bash
# 生成每日监控报告
#!/bin/bash

DATE=$(date +%Y%m%d)
HOSTS=("web-server-01" "web-server-02" "db-server-01")

for host in "${HOSTS[@]}"; do
  curl -o "${host}-${DATE}.xlsx" \
    "http://localhost:8080/api/ops-agent/export/zabbix?host=${host}&timeRange=1d" \
    -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)"
done

echo "每日监控报告已生成"
```

## 故障排查

### 问题 1: 连接 Zabbix 失败

**症状：**
```
查询 Zabbix 失败: Connection refused
```

**解决方案：**
```bash
# 1. 检查 Zabbix 服务状态
curl http://your-zabbix-server/api_jsonrpc.php

# 2. 验证配置
echo $ZABBIX_URL
echo $ZABBIX_USERNAME

# 3. 检查网络连通性
telnet your-zabbix-server 80
```

### 问题 2: 认证失败

**症状：**
```
Zabbix API error: Login name or password is incorrect
```

**解决方案：**
```bash
# 1. 验证用户名密码
curl -X POST http://your-zabbix-server/api_jsonrpc.php \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "user.login",
    "params": {
      "username": "your_username",
      "password": "your_password"
    },
    "id": 1
  }'

# 2. 检查用户权限
# 在 Zabbix 前端检查用户是否有 API 访问权限
```

### 问题 3: 查询数据为空

**症状：**
```
未找到匹配的监控数据
```

**解决方案：**
```bash
# 1. 检查主机是否存在
curl -X POST http://localhost:8080/api/ops-agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "列出所有 Zabbix 主机"}'

# 2. 检查指标名称
# 在 Zabbix 前端确认正确的指标 Key

# 3. 扩大时间范围
curl -X POST http://localhost:8080/api/ops-agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "查询 web-server-01 最近 7 天的 CPU 数据",
    "context": {"timeRange": "7d"}
  }'
```

## 最佳实践

### 1. 查询优化

- 使用 `metric` 参数过滤特定指标，减少数据量
- 合理设置 `limit` 参数，避免查询过多数据
- 使用适当的时间范围，避免查询过大时间跨度

### 2. 导出优化

- 大量数据建议分批导出
- 使用批量导出接口处理多主机数据
- 定期清理临时导出文件

### 3. 安全建议

- 使用环境变量存储敏感配置
- 限制 API 访问权限
- 定期更换 Zabbix 密码

### 4. 监控建议

- 设置合理的查询超时时间
- 监控 API 调用频率
- 记录查询日志便于审计
