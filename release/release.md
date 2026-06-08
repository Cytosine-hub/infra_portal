# Release v1.0.9-20260608

**日期**: 2026-06-08
**分支**: feature/ops-agent
**发布范围**: frontend / backend / db / docs

**自上次发布以来的变更**:
- feat: Agent 工具列表增加 description 字段
- feat: Wiki 搜索工具集成到 Agent
- feat: Wiki 知识图谱导出
- fix: 智能排查 SSE 错误处理和加载状态
- fix: Agent 会话 created_by 硬化
- fix: Wiki 导出源引用修复
- refactor: Wiki Ingest/Lint Agent 生产化加固
- docs: Agent 排查计划文档

## 文件清单

| 文件 | 大小 |
|------|------|

| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 107M |
| backend/application.yml.example | 4.0K |
| frontend/favicon.svg | 4.0K |
| frontend/index.html | 4.0K |
| frontend/assets/index-CiXlGcCh.js | 644K |
| frontend/assets/index-DZyOvTZI.css | 108K |
| db/upgrade-v1.0.9.sql | 4.0K |
| docs/startup-manual.md | 8.0K |
| docs/production-deploy.md |  12K |

---

# Release v1.0.8-20260602

**日期**: 2026-06-02
**分支**: feature/ops-agent
**发布范围**: frontend / backend / db

**自上次发布以来的变更**:
- feat: 常用命令模块权限管控 — 迁移到 software_types + 分类级 RBAC
- fix: 新增命令表单按用户角色显示可选软件类型
- fix: 新增/删除命令后刷新侧边栏类型列表

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 105 MB |
| backend/application.yml.example | 2.9 KB |
| frontend/index.html | 0.5 KB |
| frontend/assets/index-CxKXAleL.js | 578 KB |
| frontend/assets/index-C_Heoq1F.css | 70 KB |
| frontend/favicon.svg | 295 B |
| db/upgrade-v1.0.8-commands.sql | 2.1 KB |

---

# Release v1.0.7-20260602

**日期**: 2026-06-02
**分支**: feature/ops-agent
**发布范围**: frontend / backend

**自上次发布以来的变更**:
- feat: LLM 调用重试机制（最多 5 次，指数退避，前端实时显示重试进度）
- feat: 智能排查接口改为 SSE 流式返回（支持重试进度推送）
- fix: ZabbixTool/ZabbixExportTool 参数类型转换修复（String → Number）
- refactor: 知识图谱从 3D (Three.js) 改为 2D (Canvas)，星空风格（JS 从 1.7MB 降至 577KB）
- perf: 知识图谱性能优化（实例复用、loading 状态、大图降精度）
- perf: 文档预览分页加载（每次渲染 20 个切片，滚动加载更多）
- style: 知识图谱改为黑色主题（黑底、白色节点/线、大小区分文档和切片）
- fix: 去掉前端 token 过期时间检查，统一由服务器验证
- fix: 修复 ForceGraph API 调用方式（先创建实例再挂载到容器）
- fix: 知识库文档列表加 loading 状态
- fix: SSE fetch 的 401 处理（跳转登录页）

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 105 MB |
| backend/application.yml.example | 2.9 KB |
| frontend/index.html | 0.5 KB |
| frontend/assets/index-D4WAG2EI.js | 577 KB |
| frontend/assets/index-CwBuPePp.css | 70 KB |
| frontend/favicon.svg | 0.3 KB |

---

# Release v1.0.6-20260601

**日期**: 2026-06-01
**分支**: feature/ops-agent
**发布范围**: frontend / backend / db

**自上次发布以来的变更**:
- refactor: 认证机制从 Basic Auth 改为 Token 认证（UUID token + localStorage + 2h 滑动过期）
- feat: 知识库保留原文件预览（PDF 新标签页、Markdown/Word 弹窗渲染）
- feat: 知识库文档预览 + 图谱样式优化（节点缩小、连线加粗加亮）
- feat: 参数标准批量导入功能（Excel 模板下载和批量导入）
- feat: 文件下载兼容 /软件名/文件名 路径格式
- perf: embedding 并行化（8 线程）+ 上传进度条 + 中断处理
- fix: 修复上传文件二次读取 InputStream 失败
- fix: 修复上传失败（切片过大超出 embedding 上下文长度）
- fix: PDF 预览改用 blob URL 绕过 iframe 无法携带 token 的问题
- fix: 优化上传/删除交互（清除按钮、系统风格删除弹窗）

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 105 MB |
| backend/application.yml.example | 3 KB |
| frontend/index.html | 0.5 KB |
| frontend/assets/index-CP6j0XbD.js | 1.7 MB |
| frontend/assets/index-CiPe1Nhm.css | 70 KB |
| frontend/favicon.svg | 0.3 KB |
| db/upgrade-v1.0.6.sql | 3.7 KB |
| docs/startup-manual.md | 6.5 KB |
| docs/production-deploy.md | 10 KB |

---

# Release v1.0.4-20260529

**日期**: 2026-05-29
**分支**: feature/ops-agent
**发布范围**: frontend / backend / db / docs

**自上次发布以来的变更**:
- feat: RBAC 权限管理优化（14 角色、分类审核、系统设置）
- feat: 增加参数标准和标准文档修订历史功能
- fix: 类型管理限制系统管理员 + 文件管理限制本岗位分类
- fix: 修订记录序列化参数列表 + 渲染 markdown 内容
- fix: 审核管理弹窗加宽 + diff 内容红绿背景色
- fix: 修复常用命令按类型筛选失效 + 增加编辑功能
- refactor: 迁移 JPA/Hibernate 到 MyBatis
- fix: 修复 admin section v-else-if 链断裂
- fix: 修复审核弹窗宽度不生效（CSS 优先级问题）

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 105 MB |
| backend/application.yml.example | 4 KB |
| frontend/index.html | 4 KB |
| frontend/assets/index-CkCtdzpq.js | 1.7 MB |
| frontend/assets/index-BdXRUm5v.css | 68 KB |
| frontend/favicon.svg | 4 KB |
| frontend/nginx.conf | 4 KB |
| db/full_schema.sql | 24 KB |
| db/seed_data.sql | 48 KB |
| docs/startup-manual.md | 8 KB |
| docs/production-deploy.md | 12 KB |

---

# Release v1.0.3-20260528

**日期**: 2026-05-28
**分支**: feature/ops-agent
**发布范围**: frontend / backend / docs

**自上次发布以来的变更**:
- refactor: 移除 WarmupRunner 启动预热
- fix: 后台管理参数标准页面显示为空及关联文档缺失
- fix: 修复常用命令按类型筛选失效 + 增加编辑功能
- fix: 审核管理弹窗加宽 + diff 内容红绿背景色
- fix: 修复审核弹窗宽度不生效（CSS 优先级问题）

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 105 MB |
| backend/application.yml.example | 4 KB |
| frontend/index.html | 4 KB |
| frontend/assets/index-D5f4L3Zj.js | 1.6 MB |
| frontend/assets/index-CRAn0Fks.css | 64 KB |
| frontend/favicon.svg | 4 KB |
| frontend/nginx.conf | 4 KB |
| docs/startup-manual.md | 8 KB |
| docs/production-deploy.md | 12 KB |

---

# Release v1.0.2-20260528

**日期**: 2026-05-28
**分支**: feature/ops-agent
**发布范围**: frontend / backend / docs

**自上次发布以来的变更**:
- refactor: 迁移 JPA/Hibernate 到 MyBatis（14 实体、14 Mapper、14 XML 映射）
- fix: 后台管理参数标准页面显示为空及关联文档缺失
- fix: seed 方法补回时间戳设置
- fix: 修复软件类型创建 500 错误
- fix: 修复文件上传 500 错误
- fix: 修复论坛标签筛选 500 错误
- fix: 参数标准详情页不渲染 Markdown 内容
- fix: 恢复手册详情页内容显示
- feat: 标准详情页无手册和参数时显示提示
- fix: 删除标准详情页的 参数标准 标题文字

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 105 MB |
| backend/application.yml.example | 4 KB |
| frontend/index.html | 4 KB |
| frontend/assets/index-D5f4L3Zj.js | 1.6 MB |
| frontend/assets/index-CRAn0Fks.css | 64 KB |
| frontend/favicon.svg | 4 KB |
| frontend/nginx.conf | 4 KB |
| docs/startup-manual.md | 8 KB |
| docs/production-deploy.md | 12 KB |

---

# Release v1.0.1-20260528

**日期**: 2026-05-28
**分支**: feature/ops-agent

**自上次发布以来的变更**:
- fix: 参数标准详情页不渲染 Markdown 内容
- fix: 恢复手册详情页内容显示
- feat: 标准详情页无手册和参数时显示提示
- fix: 删除标准详情页的 参数标准 标题文字
- fix: 修复参数标准详情页文档列表不显示
- fix: 手册详情页不显示文档导航列表
- fix: 修复手册详情页不显示内容的 bug
- docs: 添加服务器 Milvus 安装手册
- fix: embedding model 改为本地 Ollama BGE

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 127 MB |
| backend/application.yml.example | 4 KB |
| frontend/index.html | 4 KB |
| frontend/assets/index-ucAKFYoF.js | 1.6 MB |
| frontend/assets/index-CRAn0Fks.css | 64 KB |
| frontend/favicon.svg | 4 KB |
| frontend/nginx.conf | 4 KB |
| db/full_schema.sql | 20 KB |
| db/seed_data.sql | 40 KB |
| docs/startup-manual.md | 8 KB |
| docs/production-deploy.md | 12 KB |

---

# Release v1.0.0-20260528

**日期**: 2026-05-28
**分支**: feature/ops-agent

**自上次发布以来的变更**:
首次发布

## 文件清单

| 文件 | 大小 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 127 MB |
| backend/application.yml.example | 4 KB |
| frontend/index.html | 4 KB |
| frontend/assets/index-CBdp7zCY.js | 1.6 MB |
| frontend/assets/index-CRAn0Fks.css | 64 KB |
| frontend/favicon.svg | 4 KB |
| frontend/nginx.conf | 4 KB |
| db/full_schema.sql | 20 KB |
| db/seed_data.sql | 40 KB |
| docs/startup-manual.md | 4 KB |
| docs/production-deploy.md | 4 KB |

---
