#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""补丁 2：处理 Kotlin 字符串模板内的标识符。

上一版把整个 "...${FCLPath.JAVA_PATH}..." 当成字符串字面量保护，导致模板内的
代码标识符未被重命名，出现 import 已改为 KCUPath 而模板内仍是 FCLPath 的不一致。
本脚本先把 ${...} 挖出来单独保护，再保护纯字符串，替换后按逆序还原。
"""
import os, re, sys
ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
SKIP = {'.git', 'build', '.gradle', 'gradle', 'node_modules', '.github'}
PROTECT = ["FCL_VERSION_CODE", "FoldCraftLauncher", "FCL-Team", "Fold Craft Launcher"]
TPL = re.compile(r'\$\{[^}]*\}')
STR_RE = re.compile(r'"(?:[^"\\\n]|\\.)*"')

n = 0
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in SKIP]
    for fn in filenames:
        if not fn.endswith(('.kt', '.java')):
            continue
        path = os.path.join(dirpath, fn)
        try:
            with open(path, encoding='utf-8') as f:
                orig = f.read()
        except Exception:
            continue
        if 'FCL' not in orig:
            continue
        t = orig
        for i, tok in enumerate(PROTECT):
            t = t.replace(tok, '\x00T%d\x00' % i)
        # 1) 挖出 Kotlin 模板表达式
        tpls = []
        if fn.endswith('.kt'):
            def tp(m):
                tpls.append(m.group(0))
                return '\x00P%d\x00' % (len(tpls) - 1)
            t = TPL.sub(tp, t)
        # 2) 保护剩余字符串字面量
        strs = []
        def sp(m):
            strs.append(m.group(0))
            return '\x00S%d\x00' % (len(strs) - 1)
        t = STR_RE.sub(sp, t)
        # 3) 替换（模板占位符内含 FCL 的也要改）
        t = t.replace('FCL', 'KCU')
        for i, s in enumerate(tpls):
            t = t.replace('\x00P%d\x00' % i, s.replace('FCL', 'KCU'))
        # 4) 逆序还原
        for i, s in enumerate(strs):
            t = t.replace('\x00S%d\x00' % i, s)
        for i, tok in enumerate(PROTECT):
            t = t.replace('\x00T%d\x00' % i, tok)
        if t != orig:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(t)
            n += 1
print("PATCHED_TPL_FILES=%d" % n)
