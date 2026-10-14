#!/usr/bin/env python3
"""
GitHub 推送脚本（REST API 版）
背景：本沙箱 git smart-HTTP 协议端点（*/info/refs*）被拦截超时，只能走 API。
用法：python3 push_github.py <YOUR_TOKEN>
说明：始终以「远端当前 HEAD」为父提交、以「本地 HEAD 的文件树」为内容，
      因此不依赖本地 commit sha，可重复执行（幂等）。
"""
import base64
import json
import subprocess
import sys
import time

import requests

REPO = "kek2233/bodyfat-calculator"
BRANCH = "main"
API = "https://api.github.com/repos/" + REPO
WORKDIR = "/workspace"


def run(*args):
    # core.quotepath=false 必须：否则中文文件名会被 git 转义成 "\344\275\223..."，
    # 提交到 API 后仓库里会出现乱码文件名
    return subprocess.run(["git", "-c", "core.quotepath=false"] + list(args),
                          cwd=WORKDIR, check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def tracked_files():
    """返回当前 HEAD 跟踪的文件列表: [(mode, type, blob_sha, path)]"""
    out = run("ls-tree", "-r", "--full-tree", "HEAD").stdout.decode("utf-8").strip()
    rows = []
    for line in out.splitlines():
        meta, path = line.split("\t", 1)
        mode, typ, blob = meta.split()
        if path.startswith('"'):
            sys.exit("检测到被转义的文件名 %r，请确认 core.quotepath=false 已生效" % path)
        rows.append((mode, typ, blob, path))
    return rows


def blob_bytes(blob_sha):
    return run("cat-file", "blob", blob_sha).stdout


def main():
    if len(sys.argv) < 2:
        print("用法: python3 push_github.py <GITHUB_TOKEN>", file=sys.stderr)
        return 2
    token = sys.argv[1].strip()
    headers = {
        "Authorization": "Bearer " + token,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "bodyfat-push-script",
    }

    # 1) 远端当前 HEAD（本次提交的父节点）
    r = requests.get(API + "/commits/" + BRANCH, headers=headers, timeout=30)
    if r.status_code != 200:
        print("读取远端 HEAD 失败:", r.status_code, r.text[:400])
        return 1
    parent = r.json()["sha"]
    print("远端 HEAD =", parent)

    # 2) 本地文件 -> blobs
    files = tracked_files()
    print("待上传文件数 =", len(files))
    entries = []
    for i, (mode, typ, blob, path) in enumerate(files, 1):
        if typ != "blob":
            print("  跳过非 blob 条目:", path)
            continue
        data = blob_bytes(blob)
        if len(data) > 95 * 1024 * 1024:
            print("  文件超过 95MB，GitHub API 不接受:", path)
            return 1
        ok = False
        for attempt in range(3):
            rr = requests.post(API + "/git/blobs", headers=headers, timeout=120,
                               json={"content": base64.b64encode(data).decode("ascii"),
                                     "encoding": "base64"})
            if rr.status_code in (200, 201):
                entries.append({"path": path, "mode": mode, "type": "blob",
                                "sha": rr.json()["sha"]})
                ok = True
                break
            time.sleep(1.5)
        if not ok:
            print("  blob 上传失败:", path, rr.status_code, rr.text[:300])
            return 1
        print("  [%2d/%d] %-46s %8d B" % (i, len(files), path, len(data)))

    # 3) tree
    r = requests.post(API + "/git/trees", headers=headers, timeout=60,
                      json={"tree": entries})
    if r.status_code not in (200, 201):
        print("创建 tree 失败:", r.status_code, r.text[:500])
        return 1
    tree_sha = r.json()["sha"]
    print("tree =", tree_sha)

    # 4) commit
    r = requests.post(API + "/git/commits", headers=headers, timeout=60, json={
        "message": "feat: v1.2.0 离线食物识别 + 食物库 + 数据图表 + 本地备份\n\n"
                   "识别（P3）\n"
                   "- 修复致命缺陷：HTML 从未加载 ort.min.js，离线模型永远无法运行\n"
                   "- 接入 Food-101 MobileNetV2 ONNX：101 类离线识别 + 中文名/kcal 映射\n"
                   "- 拍照后自动记入当前餐，支持点候选纠正与一键撤销\n"
                   "- logits 需 softmax；实测加载 ~0.4s、推理 ~50ms\n\n"
                   "功能（P4）\n"
                   "- 新增「食物库」页：232+ 种食物，搜索/分类筛选/一键记餐/自定义增删\n"
                   "- 记录页新增 4 张图表：体重趋势、近 7 天热量 vs 目标、签到完成率、今日热量来源\n"
                   "- 本地自动备份：每次写入留快照，可一键恢复\n"
                   "- 安卓导出改走原生桥（MediaStore/私有目录），解决 WebView a[download] 失效\n\n"
                   "工程\n"
                   "- MainActivity 增加 HostBridge.saveText\n"
                   "- 版本升至 1.2.0 (versionCode 3)\n"
                   "- README 与截图同步更新",
        "tree": tree_sha,
        "parents": [parent],
    })
    if r.status_code not in (200, 201):
        print("创建 commit 失败:", r.status_code, r.text[:500])
        return 1
    commit_sha = r.json()["sha"]
    print("commit =", commit_sha)

    # 5) 更新 ref
    r = requests.patch(API + "/git/refs/heads/" + BRANCH, headers=headers, timeout=60,
                       json={"sha": commit_sha})
    if r.status_code not in (200, 201):
        print("更新 ref 失败:", r.status_code, r.text[:500])
        return 1
    print("ref updated ->", BRANCH, commit_sha)

    # 6) 校验
    v = requests.get(API + "/commits/" + BRANCH, headers=headers, timeout=30).json()
    print("远端 HEAD =", v["sha"], "| tree =", v["commit"]["tree"]["sha"])
    print("内容文件数 =", len(requests.get(
        API + "/git/trees/" + v["commit"]["tree"]["sha"] + "?recursive=1",
        headers=headers, timeout=30).json().get("tree", [])))
    print("\n推送完成：https://github.com/%s/commits/%s" % (REPO, BRANCH))
    return 0


if __name__ == "__main__":
    sys.exit(main())
