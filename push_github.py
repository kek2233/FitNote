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
import os
import subprocess
import sys
import time

import requests

REPO = "kek2233/FitNote"
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


def local_head_message():
    """远端提交信息 = 本地 HEAD 的提交信息（含正文）；可用第 2 个参数覆盖。"""
    if len(sys.argv) > 2 and sys.argv[2].strip():
        return sys.argv[2].strip()
    try:
        msg = run("log", "-1", "--pretty=%B").stdout.decode("utf-8").strip()
    except Exception:
        msg = ""
    return msg or "chore: 同步本地最新版本"


def main():
    token = sys.argv[1].strip() if len(sys.argv) > 1 else os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        print("用法: python3 push_github.py <GITHUB_TOKEN> [自定义提交信息]"
              "（令牌也可用环境变量 GITHUB_TOKEN）", file=sys.stderr)
        return 2
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
        "message": local_head_message(),
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
