#!/usr/bin/env python3
"""从实际入库的语料反向生成候选 golden set。

为什么需要这个工具
------------------
2026-07-30 首次实测 Recall@5 只有 10%，但那不是检索算法的问题——种子集问的是
Oracle / Redis / Kafka / TongWeb，而库里灌的是另外几份文档，期望答案根本不存在。
那次测的是**语料覆盖率**，不是检索质量。

只要评测集不绑定实际语料，这个问题就会一直重复。本工具从已入库的切片反向出题，
保证每道题的答案确实在库里，这样 Recall 才真正反映检索能力。

生成策略（全部确定性，不依赖大模型）
------------------------------------
1. exact_param —— 从切片中抽取技术标识（带下划线/点/连字符的配置项、错误码），
   出「XXX 是多少 / 怎么配置」类问题。这类题最能暴露 BM25 那一路是否生效。
2. semantic —— 从 sectionPath 的末级章节名出题，期望命中该章节路径。
   这类题考察稠密向量：问法是章节标题的自然语言化，与正文用词不同。
3. 每道题都记录来源文档与格式，供按 source_format 分桶统计。

输出是**候选集**，必须人工筛一遍再用：机器出的题可能过于贴近原文措辞，
显得比真实工单容易。真实问法仍应从工单和群聊里捞。

用法
----
    export MRM_TOKEN=$(通过 /test-login 获取)
    python3 scripts/gen-golden-set.py --out docs/eval/golden-set-generated.json
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict

DEFAULT_BASE = os.environ.get("MRM_BASE_URL", "http://localhost:8080")

# 与 AnswerGroundingVerifier 保持同一口径：带分隔符的标识才算「具体断言」，
# 单纯的产品名（MySQL、Redis）太泛，不适合作为精确匹配题的答案锚点。
SPECIFIC_IDENTIFIER = re.compile(r"[A-Za-z][A-Za-z0-9]*(?:[_.-][A-Za-z0-9]+)+")
ERROR_CODE = re.compile(r"\b[A-Z]{2,}-\d{3,}\b")

# 这些标识是文件名、URL 片段等，不适合出题
NOISE = re.compile(r"^(www\.|http|.*\.(png|jpg|jpeg|gif|pdf|docx?|xlsx?|md|html?|json|xml|yml|yaml)$)", re.I)

FORMAT_BY_SUFFIX = {
    ".pdf": "pdf", ".docx": "docx", ".doc": "doc",
    ".xlsx": "xlsx", ".xls": "xlsx", ".md": "md",
}


def api(base_url, token, path):
    req = urllib.request.Request(base_url + path, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"{path} 返回 {e.code}: {e.read().decode('utf-8', 'ignore')[:200]}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"无法连接 {base_url}（后端没起？）: {e.reason}") from e


def source_format(title, source_type):
    if source_type == "STANDARD_DOC":
        return "standards"
    for suffix, fmt in FORMAT_BY_SUFFIX.items():
        if title.lower().endswith(suffix):
            return fmt
    return "unknown"


def collect_chunks(base_url, token):
    """枚举全部来源及其切片。preview 接口按来源返回切片列表。"""
    sources = api(base_url, token, "/api/knowledge/sources")
    for src in sources:
        title = src.get("title") or ""
        stype = src.get("sourceType") or ""
        if not title:
            continue
        params = urllib.parse.urlencode({"title": title, "sourceType": stype})
        try:
            detail = api(base_url, token, f"/api/knowledge/docs/preview?{params}")
        except RuntimeError as e:
            print(f"  跳过 {title}: {e}", file=sys.stderr)
            continue
        fmt = source_format(title, stype)
        for chunk in detail.get("chunks", []):
            yield title, fmt, chunk.get("content") or ""


def identifiers_in(text):
    found = []
    for m in ERROR_CODE.finditer(text):
        found.append(m.group())
    for m in SPECIFIC_IDENTIFIER.finditer(text):
        token = m.group()
        if not NOISE.match(token) and len(token) >= 6:
            found.append(token)
    return found


def section_of(content):
    """切片正文首行是 sectionPath 面包屑（切片器写入），末级即当前章节名。"""
    first = content.split("\n", 1)[0].strip()
    if " / " not in first:
        return None
    return first


def build_cases(chunks):
    exact, semantic = {}, {}
    for title, fmt, content in chunks:
        for ident in identifiers_in(content):
            # 同一标识只出一题，优先保留首次出现的来源
            exact.setdefault(ident, (title, fmt))

        path = section_of(content)
        if path:
            leaf = path.split(" / ")[-1].strip()
            if 2 <= len(leaf) <= 20:
                semantic.setdefault(path, (leaf, title, fmt))

    cases = []
    for i, (ident, (title, fmt)) in enumerate(sorted(exact.items()), start=1):
        is_error = bool(ERROR_CODE.fullmatch(ident))
        qtype = "error_code" if is_error else "exact_param"
        # 错误码问「怎么处理」，配置项问「设置多少」——贴近同事真实问法
        query = f"{ident} 怎么处理" if is_error else f"{ident} 设置多少"
        cases.append({
            "id": f"GEN-E{i:03d}",
            "query": query,
            "query_type": qtype,
            "source_format": fmt,
            "expected": {"keywords": [ident]},
            "note": f"自动生成，锚定 {title} 中出现的标识。精确匹配题，主要考察 BM25 一路",
        })
    for i, (path, (leaf, title, fmt)) in enumerate(sorted(semantic.items()), start=1):
        cases.append({
            "id": f"GEN-S{i:03d}",
            "query": f"{leaf}怎么处理",
            "query_type": "semantic",
            "source_format": fmt,
            "expected": {"section_path": path},
            "note": f"自动生成，锚定 {title} 的章节。语义题，问法与正文用词不同，考察稠密向量一路",
        })
    return cases


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default=DEFAULT_BASE)
    ap.add_argument("--out", default="docs/eval/golden-set-generated.json")
    ap.add_argument("--max-exact", type=int, default=40)
    ap.add_argument("--max-semantic", type=int, default=40)
    args = ap.parse_args()

    token = os.environ.get("MRM_TOKEN")
    if not token:
        sys.exit("缺少 MRM_TOKEN 环境变量（用 /test-login skill 获取）")

    print(f"从 {args.base_url} 枚举语料…")
    chunks = list(collect_chunks(args.base_url, token))
    if not chunks:
        sys.exit("没有取到任何切片。确认知识库已导入文档、且 /api/knowledge/sources 有返回。")

    by_fmt = defaultdict(int)
    for _, fmt, _ in chunks:
        by_fmt[fmt] += 1
    print(f"共 {len(chunks)} 个切片：" + "、".join(f"{k} {v}" for k, v in sorted(by_fmt.items())))

    cases = build_cases(chunks)
    exact = [c for c in cases if c["query_type"] in ("exact_param", "error_code")][: args.max_exact]
    semantic = [c for c in cases if c["query_type"] == "semantic"][: args.max_semantic]
    selected = exact + semantic

    payload = {
        "_comment": [
            "本文件由 scripts/gen-golden-set.py 从实际入库语料自动生成。",
            "",
            "⚠️ 这是【候选集】，必须人工筛过再作为正式基线：",
            "  1. 机器出的题贴近原文措辞，会比真实工单容易，跑出的分数偏乐观",
            "  2. 真实问法仍应从工单和群聊里捞，本文件用于补足覆盖面与快速回归",
            "  3. 删掉不合理的题、把问法改成同事真实会问的说法，再合并进 golden-set.json",
            "",
            "价值在于每道题的答案确实在库里——这样 Recall 才反映检索能力，",
            "而不是像首次实测那样反映语料覆盖率。",
        ],
        "cases": selected,
    }

    out = pathlib_write(args.out, payload)
    print(f"\n已生成 {len(selected)} 条候选（精确 {len(exact)} / 语义 {len(semantic)}）-> {out}")
    print("下一步：人工筛选后合并进 docs/eval/golden-set.json，再跑 scripts/eval-retrieval.py")


def pathlib_write(path, payload):
    import pathlib
    p = pathlib.Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return p


if __name__ == "__main__":
    main()
