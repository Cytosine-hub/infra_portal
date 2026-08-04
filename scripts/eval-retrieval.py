#!/usr/bin/env python3
"""检索质量评测：对 golden set 跑 Recall@k / MRR，并按查询类型与来源格式分桶。

用法：
    export MRM_TOKEN=$(...)            # /test-login skill 拿到的 token
    python3 scripts/eval-retrieval.py                      # 默认 k=5
    python3 scripts/eval-retrieval.py --k 10 --json out.json

只依赖标准库。评测**检索层**，不评测生成层——两者必须分开看，混在一起无法归因：
Recall 高但答案差 = prompt 问题；Recall 低 = 解析/切片/embedding 问题。
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict

DEFAULT_BASE = os.environ.get("MRM_BASE_URL", "http://localhost:8080")
GOLDEN_SET = os.path.join(os.path.dirname(__file__), "..", "docs", "eval", "golden-set.json")


def search(base_url, token, query, k):
    """调用检索接口，返回结果列表。失败时抛出带上下文的异常，不静默吞掉。"""
    url = f"{base_url}/api/knowledge/search?" + urllib.parse.urlencode({"q": query, "topK": k})
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"检索接口返回 {e.code}: {e.read().decode('utf-8', 'ignore')[:200]}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"无法连接 {base_url}（后端没起？）: {e.reason}") from e
    return payload if isinstance(payload, list) else payload.get("results", [])


def hit(result, expected):
    """单条检索结果是否命中期望。任一 expected 维度满足即算命中。"""
    content = (result.get("content") or "")
    title = (result.get("sourceTitle") or "")
    section = (result.get("sectionPath") or "")

    if "source_title" in expected and expected["source_title"] == title:
        return True
    if "section_path" in expected and section.startswith(expected["section_path"]):
        return True
    if "keywords" in expected:
        haystack = f"{title}\n{section}\n{content}"
        if all(kw in haystack for kw in expected["keywords"]):
            return True
    return False


def evaluate(cases, base_url, token, k):
    rows = []
    for case in cases:
        try:
            results = search(base_url, token, case["query"], k)
            error = None
        except RuntimeError as e:
            results, error = [], str(e)

        rank = next((i + 1 for i, r in enumerate(results) if hit(r, case["expected"])), None)
        rows.append({
            "id": case["id"],
            "query": case["query"],
            "query_type": case.get("query_type", "unknown"),
            "source_format": case.get("source_format", "unknown"),
            "hit": rank is not None,
            "rank": rank,
            "returned": len(results),
            "error": error,
        })
    return rows


def bucket_report(rows, key):
    buckets = defaultdict(list)
    for r in rows:
        buckets[r[key]].append(r)
    lines = []
    for name in sorted(buckets):
        group = buckets[name]
        hits = [r for r in group if r["hit"]]
        recall = len(hits) / len(group)
        mrr = sum(1 / r["rank"] for r in hits) / len(group) if group else 0.0
        lines.append(f"  {name:<16} Recall@k {recall:>6.1%}  MRR {mrr:>5.3f}  ({len(hits)}/{len(group)})")
    return lines


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--k", type=int, default=5)
    ap.add_argument("--base-url", default=DEFAULT_BASE)
    ap.add_argument("--golden-set", default=GOLDEN_SET)
    ap.add_argument("--json", help="把明细写到这个文件，用于跨次对比")
    args = ap.parse_args()

    token = os.environ.get("MRM_TOKEN")
    if not token:
        sys.exit("缺少 MRM_TOKEN 环境变量（用 /test-login skill 获取）")

    with open(args.golden_set, encoding="utf-8") as f:
        cases = json.load(f)["cases"]

    rows = evaluate(cases, args.base_url, token, args.k)

    total = len(rows)
    hits = [r for r in rows if r["hit"]]
    errors = [r for r in rows if r["error"]]

    print(f"\n检索质量评测  k={args.k}  用例 {total} 条  base={args.base_url}")
    print("=" * 64)
    print(f"总体   Recall@{args.k} {len(hits)/total:>6.1%}   "
          f"MRR {sum(1/r['rank'] for r in hits)/total:>5.3f}")
    if errors:
        print(f"\n⚠️  {len(errors)} 条请求失败，已计为未命中——结果不可信，先修连通性：")
        print(f"    {errors[0]['error']}")

    print("\n按查询类型（前三类是精确匹配场景，混合检索的主要收益来源）：")
    print("\n".join(bucket_report(rows, "query_type")))
    print("\n按来源格式（量化各格式解析质量差异）：")
    print("\n".join(bucket_report(rows, "source_format")))

    missed = [r for r in rows if not r["hit"] and not r["error"]]
    if missed:
        print(f"\n未命中 {len(missed)} 条：")
        for r in missed:
            print(f"  {r['id']}  {r['query']}  (返回 {r['returned']} 条)")

    if args.json:
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump({"k": args.k, "rows": rows}, f, ensure_ascii=False, indent=2)
        print(f"\n明细已写入 {args.json}")

    # 有请求失败时以非零退出，避免把连通性问题误读成质量结论
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
