#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把剩余 FCL 资源标识符改名：settings_fcl_* -> settings_kcu_*

只针对资源 ID（用户不可见但在代码里到处出现），不动值。
保留 modpack_files_fclversion_cfg —— 那是文件格式名（.fclversion.cfg），
改成 kcuversion 会改变语义，且不是本应用名。
"""
import os, re, sys
ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
SKIP = {'.git', 'build', '.gradle', 'gradle', 'node_modules', '.github'}
EXT = {'.java', '.kt', '.xml'}
PAIRS = [('settings_fcl_', 'settings_kcu_')]
n = 0
hits = 0
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in SKIP]
    for fn in filenames:
        if os.path.splitext(fn)[1].lower() not in EXT:
            continue
        path = os.path.join(dirpath, fn)
        try:
            with open(path, encoding='utf-8') as f:
                orig = f.read()
        except Exception:
            continue
        t = orig
        for a, b in PAIRS:
            hits += t.count(a)
            t = t.replace(a, b)
        if t != orig:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(t)
            n += 1
print("SETTINGS_PATCHED_FILES=%d" % n)
print("SETTINGS_HITS=%d" % hits)
