#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""补丁：清掉迁移后残留的 FCL 标识符。

上一版类名替换用了 \b...\b 词边界，而 R 字段引用形如 FCLTextView_auto_tint
（下划线是单词字符），不构成词边界，故未被替换；attrs.xml 里的
declare-styleable name 已被改名，两边对不上导致 Unresolved reference。
本脚本改为对代码标识符做全局 FCL -> KCU（字符串与法律相关标识受保护）。
"""
import os, re, sys
ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
SKIP = {'.git', 'build', '.gradle', 'gradle', 'node_modules', '.github'}
# 法律/署名与运行时契约，绝不改写
PROTECT = ["FCL_VERSION_CODE", "FoldCraftLauncher", "FCL-Team", "Fold Craft Launcher"]
EXT = {'.java', '.kt', '.xml', '.c', '.h', '.cpp', '.hpp', '.kts', '.gradle', '.pro'}
STR_RE = re.compile(r'"(?:[^"\\\n]|\\.)*"')

def protect_tokens(t):
    for i, tok in enumerate(PROTECT):
        t = t.replace(tok, '\x00T%d\x00' % i)
    return t

def restore_tokens(t):
    for i, tok in enumerate(PROTECT):
        t = t.replace('\x00T%d\x00' % i, tok)
    return t

n = 0
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
        t = protect_tokens(orig)
        store = []
        if os.path.splitext(fn)[1].lower() in ('.java', '.kt'):
            def rep(m):
                s = m.group(0)
                store.append(s)
                return '\x00S%d\x00' % (len(store) - 1)
            t = STR_RE.sub(rep, t)
        t = t.replace('FCL', 'KCU')
        for i, s in enumerate(store):
            t = t.replace('\x00S%d\x00' % i, s)
        t = restore_tokens(t)
        if t != orig:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(t)
            n += 1
print("PATCHED_FILES=%d" % n)
