#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 FCL* 前缀的源文件重命名为 KCU*（内容已改名，文件名必须同步，否则 javac 报
public 类名与文件名不一致）。"""
import os, re, sys
ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
SKIP = {'.git', 'build', '.gradle', 'gradle', 'node_modules'}
SPECIAL = {"FCLauncher": "KCULauncher"}
n = 0
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in SKIP]
    for fn in filenames:
        if not re.match(r'^FCL[A-Za-z0-9_]*\.(java|kt)$', fn):
            continue
        stem = fn.rsplit('.', 1)[0]
        ext = fn.rsplit('.', 1)[1]
        new_stem = SPECIAL.get(stem, 'KCU' + stem[3:])
        src = os.path.join(dirpath, fn)
        dst = os.path.join(dirpath, new_stem + '.' + ext)
        if os.path.exists(dst):
            print("跳过（目标已存在）:", os.path.relpath(src, ROOT))
            continue
        os.replace(src, dst)
        n += 1
print("RENAMED_FILES=%d" % n)
