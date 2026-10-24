#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""包名与命名空间迁移：com.tungsten.fcl* / com.mio -> com.kongbai.kculauncher*

保护清单（绝不改写）：
  - FCL_VERSION_CODE      MobileGlues 启动器白名单环境变量名
  - FoldCraftLauncher     上游项目名（法律/署名）
  - FCL-Team              上游团队名（法律/署名）
  - 字符串字面量里的 FCL（除含 com.tungsten 的）
"""
import os, re, sys, json

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."

# ---------- 替换规则（顺序：长前缀优先） ----------
PKG_DOT = [
    ("com.tungsten.fclcore",    "com.kongbai.kculauncher.core"),
    ("com.tungsten.fcllibrary", "com.kongbai.kculauncher.library"),
    ("com.tungsten.fclauncher", "com.kongbai.kculauncher.launcher"),
    ("com.tungsten.fcl",        "com.kongbai.kculauncher"),
    ("com.mio",                 "com.kongbai.kculauncher.mio"),
]
PKG_SLASH = [
    ("com/tungsten/fclcore/",    "com/kongbai/kculauncher/core/"),
    ("com/tungsten/fcllibrary/", "com/kongbai/kculauncher/library/"),
    ("com/tungsten/fclauncher/", "com/kongbai/kculauncher/launcher/"),
    ("com/tungsten/fcl/",        "com/kongbai/kculauncher/"),
    ("com/mio/",                 "com/kongbai/kculauncher/mio/"),
]
PKG_UNDER = [
    ("com_tungsten_fclcore_",    "com_kongbai_kculauncher_core_"),
    ("com_tungsten_fcllibrary_", "com_kongbai_kculauncher_library_"),
    ("com_tungsten_fclauncher_", "com_kongbai_kculauncher_launcher_"),
    ("com_tungsten_fcl_",        "com_kongbai_kculauncher_"),
    ("com_mio_",                 "com_kongbai_kculauncher_mio_"),
]

CLASSES = ['FCLActivity','FCLAdapter','FCLAlertDialog','FCLApp','FCLAppBarLayout','FCLBaseUI',
 'FCLBridge','FCLBridgeCallback','FCLButton','FCLCacheRepository','FCLCheckBox',
 'FCLCheckBoxTreeAdapter','FCLCheckBoxTreeItem','FCLCheckedTextView','FCLColorPickerDialog',
 'FCLCommonUI','FCLConfig','FCLConstraintLayout','FCLDialog','FCLDynamicIsland','FCLEditText',
 'FCLFragment','FCLGameLauncher','FCLGameRepository','FCLGameRepositoryTest','FCLImageButton',
 'FCLImageView','FCLInput','FCLKeycodes','FCLLinearLayout','FCLMenuView','FCLMultiPageUI',
 'FCLNumberSeekBar','FCLPage','FCLPath','FCLPreciseSeekBar','FCLProgressBar','FCLRadioButton',
 'FCLSeekBar','FCLSpinner','FCLSwitch','FCLTabLayout','FCLTextView','FCLUILayout','FCLView']
# 特例：FCLauncher 去掉 FCL 后剩 "auncher"，需单独映射
CLASS_SPECIAL = {"FCLauncher": "KCULauncher"}

PROTECT = ["FCL_VERSION_CODE", "FoldCraftLauncher", "FCL-Team", "FCLauncher_"]

TEXT_EXT = {'.java', '.kt', '.xml', '.c', '.h', '.cpp', '.hpp', '.kts', '.gradle',
            '.pro', '.yml', '.yaml', '.txt', '.json', '.sh', '.py', '.md'}

# ---------- 字符串字面量保护（仅 java/kt） ----------
STR_RE = re.compile(r'"(?:[^"\\\n]|\\.)*"')
def protect_java(text):
    """把不含 com.tungsten 的字符串字面量换成占位符"""
    store = []
    def rep(m):
        s = m.group(0)
        if 'com.tungsten' in s or 'com/mio' in s:
            return s
        store.append(s)
        return '\x00STR%d\x00' % (len(store) - 1)
    text = STR_RE.sub(rep, text)
    return text, store

def restore_java(text, store):
    for i, s in enumerate(store):
        text = text.replace('\x00STR%d\x00' % i, s)
    return text

def apply_rules(text):
    for old, new in PKG_DOT:   text = text.replace(old, new)
    for old, new in PKG_SLASH: text = text.replace(old, new)
    for old, new in PKG_UNDER: text = text.replace(old, new)
    # 类名：先特例，再普通（词边界，避免误伤字符串内已保护的）
    for old, new in CLASS_SPECIAL.items():
        text = re.sub(r'\b%s\b' % re.escape(old), new, text)
    for c in sorted(CLASSES, key=len, reverse=True):
        text = re.sub(r'\b%s\b' % re.escape(c), 'KCU' + c[3:], text)
    return text

def apply_pkg_only(text):
    for old, new in PKG_DOT:   text = text.replace(old, new)
    for old, new in PKG_SLASH: text = text.replace(old, new)
    return text

def protect_tokens(text):
    for i, tok in enumerate(PROTECT):
        text = text.replace(tok, '\x00TOK%d\x00' % i)
    return text

def restore_tokens(text):
    for i, tok in enumerate(PROTECT):
        text = text.replace('\x00TOK%d\x00' % i, tok)
    return text

changed = []
skipped_dirs = {'.git', 'build', '.gradle', 'gradle', 'node_modules'}

for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in skipped_dirs]
    for fn in filenames:
        path = os.path.join(dirpath, fn)
        ext = os.path.splitext(fn)[1].lower()
        if ext not in TEXT_EXT:
            continue
        try:
            with open(path, 'r', encoding='utf-8') as f:
                orig = f.read()
        except Exception:
            continue
        text = orig
        text = protect_tokens(text)
        if ext in ('.java', '.kt'):
            text, store = protect_java(text)
            text = apply_rules(text)
            text = restore_java(text, store)
        elif ext in ('.c', '.h', '.cpp', '.hpp'):
            # native 侧：包名与 JNI 函数名必须同步，类名不保护
            text = apply_rules(text)
        else:
            text = apply_pkg_only(text)
            # xml 里的控件全限定名含类名，也要改
            for old, new in CLASS_SPECIAL.items():
                text = re.sub(r'\b%s\b' % re.escape(old), new, text)
            for c in sorted(CLASSES, key=len, reverse=True):
                text = re.sub(r'\b%s\b' % re.escape(c), 'KCU' + c[3:], text)
        text = restore_tokens(text)
        if text != orig:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(text)
            changed.append(os.path.relpath(path, ROOT))

print("MODIFIED_FILES=%d" % len(changed))
# ---------- 目录移动 ----------
# 注意：包目录在模块目录之下（如 KCU/src/main/java/com/tungsten/fcl），
# 必须遍历查找，不能假定固定的 base 前缀（上一版因此 MOVED_DIRS=0）。
MOVES = []
PREFIX_MAP = [
    ('com/tungsten/fclcore',    'com/kongbai/kculauncher/core'),
    ('com/tungsten/fcllibrary', 'com/kongbai/kculauncher/library'),
    ('com/tungsten/fclauncher', 'com/kongbai/kculauncher/launcher'),
    ('com/tungsten/fcl',        'com/kongbai/kculauncher'),
    ('com/mio',                 'com/kongbai/kculauncher/mio'),
]
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in skipped_dirs]
    # 只在源码根（java / kotlin）下识别包目录
    if not (dirpath.endswith('/java') or dirpath.endswith('/kotlin') or dirpath == os.path.join(ROOT,'java')):
        continue
    for old_rel, new_rel in PREFIX_MAP:
        src = os.path.join(dirpath, old_rel)
        dst = os.path.join(dirpath, new_rel)
        if os.path.isdir(src):
            MOVES.append((src, dst))

MOVES.sort(key=lambda x: len(x[0]), reverse=True)
moved = 0
for src, dst in MOVES:
    if not os.path.exists(src):
        continue
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    for dp, dn, filenames in os.walk(src):
        rel = os.path.relpath(dp, src)
        target = os.path.join(dst, rel) if rel != '.' else dst
        os.makedirs(target, exist_ok=True)
        for fn in filenames:
            os.replace(os.path.join(dp, fn), os.path.join(target, fn))
    for dp, dn, filenames in os.walk(src, topdown=False):
        try: os.rmdir(dp)
        except OSError: pass
    try: os.rmdir(src)
    except OSError: pass
    moved += 1
print("MOVED_DIRS=%d" % moved)
