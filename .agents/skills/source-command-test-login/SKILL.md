---
name: "source-command-test-login"
description: "登录获取 Token，用于后续 API 测试"
---

# source-command-test-login

Use this skill when the user asks to run the migrated source command `test-login`.

## Command Template

# 登录测试

获取 Bearer Token 供后续 API 测试使用。

## 执行步骤

### 1. 获取 Token

```bash
PWHASH=$(echo -n "admin123" | shasum -a 256 | cut -d' ' -f1)
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Authorization: Basic $(echo -n "sysadmin:$PWHASH" | base64)" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo "TOKEN=$TOKEN"
```

### 2. 验证 Token

```bash
curl -s http://localhost:8080/api/knowledge/pages -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys, json
pages = json.load(sys.stdin)
print(f'认证成功，Wiki 页面数: {len(pages)}')
"
```

### 3. 输出结果

将 Token 输出为环境变量格式，后续测试可直接使用：

```bash
echo "export WIKI_TOKEN=$TOKEN"
```

## 使用方式

获取 Token 后，后续 API 测试直接使用 `$WIKI_TOKEN`：

```bash
curl -s http://localhost:8080/api/knowledge/pages -H "Authorization: Bearer $WIKI_TOKEN"
```
