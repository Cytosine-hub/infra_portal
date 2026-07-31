#!/usr/bin/env python3
"""测试阶段的语料补齐工具。

为什么需要
----------
召回覆盖率必须基于**系统内实际有的数据**统计——首轮 Recall@5 只有 10%，就是因为
评测集问的内容根本不在库里，测出来的是语料覆盖率而非检索质量。

但测试阶段常常语料不全：某些软件一份标准都没有，某些标准类型缺失。这时不能
凑合着测（结论无效），也不该等内容团队补完（阻塞验证）。本工具按目标清单造出
结构完整的测试标准与参数，让检索链路可以被完整验证。

造出来的都是**明确标记的测试数据**，标题统一带 `[TEST]` 前缀与 runId，
`--cleanup` 可一键清除，不会污染真实语料。

用法
----
    export MRM_TOKEN=$(通过 /test-login 获取)

    # 看当前缺什么（不写任何数据）
    python3 scripts/seed-test-corpus.py --dry-run

    # 按缺口造数据
    python3 scripts/seed-test-corpus.py --catalog "数据库:MySQL,数据库:Redis,中间件:Nginx"

    # 测完清理
    python3 scripts/seed-test-corpus.py --cleanup
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

DEFAULT_BASE = os.environ.get("MRM_BASE_URL", "http://localhost:8080")
STANDARD_TYPES = ["参数", "部署", "监控", "应急"]
TEST_PREFIX = "[TEST]"

# 每种标准类型的正文骨架。刻意写成真实运维文档的结构（章节 + 参数表），
# 这样切片器的 sectionPath 与表格保护逻辑才会被真正触发，测出来的才算数。
TEMPLATES = {
    "参数": """# {software} 参数标准

## 核心参数

| 参数名 | 默认值 | 建议值 | 说明 |
| --- | --- | --- | --- |
| {prefix}_buffer_size | 128M | 物理内存 70% | 缓冲区大小，内存充裕时上调 |
| {prefix}_max_connections | 100 | 1000 | 最大连接数，按业务并发估算 |
| {prefix}_timeout | 30 | 300 | 超时秒数，跨机房部署时需放大 |

## 调整注意事项

修改后需重启生效，变更前请确认已有回滚方案。
""",
    "部署": """# {software} 部署标准

## 环境要求

生产环境最低 4C8G，数据盘独立挂载。

## 部署步骤

1. 校验安装包完整性
2. 按参数标准写入配置文件
3. 启动并确认健康检查通过

## 高可用

至少两节点，跨机架部署。
""",
    "监控": """# {software} 监控标准

## 必采指标

| 指标 | 阈值 | 说明 |
| --- | --- | --- |
| {prefix}_cpu_usage | 80% | 持续 5 分钟触发告警 |
| {prefix}_memory_usage | 85% | 持续 5 分钟触发告警 |
| {prefix}_connection_count | 90% 上限 | 接近连接数上限时告警 |

## 告警分级

严重告警需电话通知，一般告警工单跟进。
""",
    "应急": """# {software} 应急处理

## 服务不可用

先确认进程与端口，再查看错误日志定位原因。

## 性能骤降

依次排查连接数、慢查询、磁盘 IO 与网络延迟。

## 数据异常

立即停止写入，保留现场后按备份恢复流程处理。
""",
}


def api(base_url, token, path, method="GET", body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(base_url + path, data=data, method=method,
                                 headers={"Authorization": f"Bearer {token}",
                                          "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"{method} {path} 返回 {e.code}: "
                           f"{e.read().decode('utf-8', 'ignore')[:200]}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"无法连接 {base_url}（后端没起？）: {e.reason}") from e


def parse_catalog(raw):
    entries = []
    for item in (raw or "").split(","):
        item = item.strip()
        if not item:
            continue
        category, _, software = item.partition(":")
        if not software:
            category, software = "未分类", category
        entries.append((category.strip() or "未分类", software.strip()))
    return entries


def corpus_snapshot(base_url, token):
    """读当前语料健康度，用于展示补齐前的缺口全貌。"""
    return api(base_url, token, "/api/knowledge/corpus-health")


def latin_prefix(software):
    ascii_part = "".join(ch for ch in software if ch.isascii() and ch.isalnum())
    return (ascii_part or "svc").lower()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default=DEFAULT_BASE)
    ap.add_argument("--catalog", default=os.environ.get("CORPUS_TARGET_CATALOG", ""),
                    help="目标清单，形如 数据库:MySQL,中间件:Nginx")
    ap.add_argument("--dry-run", action="store_true", help="只报告缺口，不写数据")
    ap.add_argument("--cleanup", action="store_true", help="清除本工具造过的全部测试数据")
    args = ap.parse_args()

    token = os.environ.get("MRM_TOKEN")
    if not token:
        sys.exit("缺少 MRM_TOKEN 环境变量（用 /test-login skill 获取）")

    if args.cleanup:
        cleanup(args.base_url, token)
        return

    entries = parse_catalog(args.catalog)
    if not entries:
        sys.exit("请通过 --catalog 或 CORPUS_TARGET_CATALOG 指定目标清单，"
                 "格式：数据库:MySQL,中间件:Nginx")

    health = corpus_snapshot(args.base_url, token)
    print(f"当前语料：覆盖 {health.get('coveredCells')}/{health.get('totalCells')} 格"
          f"（{health.get('coverage', 0) * 100:.1f}%），"
          f"文档 {health.get('totalSources')} 份，参数 {health.get('totalParameters')} 条")
    print(f"缺口 {len(health.get('missingCells', []))} 项\n")

    plan = [(cat, sw, t) for cat, sw in entries for t in STANDARD_TYPES]
    if args.dry_run:
        print(f"[dry-run] 将补齐 {len(plan)} 份测试标准：")
        for cat, sw, t in plan:
            print(f"  {TEST_PREFIX} {sw} {t}标准（分类 {cat}）")
        print("\n未写入任何数据。去掉 --dry-run 执行。")
        return

    run_id = uuid.uuid4().hex[:8]
    created, failed = 0, 0
    for cat, sw, stype in plan:
        title = f"{TEST_PREFIX}{run_id} {sw} {stype}标准"
        content = TEMPLATES[stype].format(software=sw, prefix=latin_prefix(sw))
        try:
            api(args.base_url, token, "/api/admin/parameter-standards", "POST", {
                "title": title, "category": cat, "software": sw, "content": content,
            })
            created += 1
        except RuntimeError as e:
            print(f"  失败 {title}: {e}", file=sys.stderr)
            failed += 1

    print(f"\n已创建 {created} 份测试标准，失败 {failed} 份，runId={run_id}")
    print("注意：标准需**发布**后才会被索引对账收录。发布后执行：")
    print("  curl -X POST -H \"Authorization: Bearer $MRM_TOKEN\" \\")
    print(f"    {args.base_url}/api/knowledge/sync-standards")
    print(f"\n测试完成后清理：python3 {sys.argv[0]} --cleanup")


def cleanup(base_url, token):
    """删除全部带 [TEST] 前缀的标准。真实语料不带该前缀，不会被误删。"""
    standards = api(base_url, token, "/api/admin/parameter-standards")
    items = standards if isinstance(standards, list) else standards.get("records", [])
    targets = [s for s in items if (s.get("title") or "").startswith(TEST_PREFIX)]

    if not targets:
        print("没有找到本工具造的测试数据（标题前缀 [TEST]）。")
        return

    removed, failed = 0, 0
    for s in targets:
        try:
            api(base_url, token, f"/api/admin/parameter-standards/{s['id']}", "DELETE")
            removed += 1
        except RuntimeError as e:
            print(f"  删除失败 {s.get('title')}: {e}", file=sys.stderr)
            failed += 1

    print(f"已清除 {removed} 份测试标准，失败 {failed} 份。")
    print("提示：清除后再跑一次 /api/knowledge/sync-standards，让索引同步移除对应向量。")


if __name__ == "__main__":
    main()
