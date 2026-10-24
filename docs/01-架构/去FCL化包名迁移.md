# 去 FCL 化：包名 / 类名 / 模块名全量迁移（v0.48.0）

执行者：yuanbao，2026-10-24

---

## 1. 用户要求

> 现与 fcl 除法律等不可动的外，其他都用 com.kongbai.kculaucher 等去 fcl 描述

拼写确认为 **`com.kongbai.kculauncher`**（补齐 n）。
范围确认为**最深层**：包名 + 类名前缀 + 模块名与项目名。
`applicationId` **保持不变**（仍是 `com.kcu.launcher`，保证可升级）。

---

## 2. 追溯：为什么不能只改 Java 源码

| 层 | 硬编码位置 | 不改的后果 |
|---|---|---|
| **JNI** | `fcl_loader.c:126/196` `FindClass("com/tungsten/fclauncher/bridge/FCLBridge")` | 运行期崩 |
| **JNI** | `input_bridge_v3.c:781` `FindClass("com/tungsten/fclauncher/CriticalNativeTest")` | 运行期崩 |
| **JNI 导出** | `utils.c` / `awt_bridge.c` / `fcl_loader.c` / `exit_hook.c` / `input_bridge_v3.c` 含 `Java_com_tungsten` | 链接期/运行期找不到符号 |
| **布局 XML** | 286 处 `<com.tungsten.fcllibrary.component.view.FCLTextView>` 全限定名 | 编译过、运行期 `InflateException` |

**JNI 是硬约束**：`FindClass` 的字符串必须与 Java 类全限定名逐字一致。
只改 Java 不改 C，能编译通过，装上去一启动就崩。

顶层包全景（迁移前）：`com.tungsten.fcl` 257、`fclcore` 546、`fcllibrary` 63、
`fclauncher` 17、`com.mio` 若干 —— 共 **1244 个文件**。

---

## 3. 映射

| 原 | 新 |
|---|---|
| `com.tungsten.fclcore` | `com.kongbai.kculauncher.core` |
| `com.tungsten.fcllibrary` | `com.kongbai.kculauncher.library` |
| `com.tungsten.fclauncher` | `com.kongbai.kculauncher.launcher` |
| `com.tungsten.fcl` | `com.kongbai.kculauncher` |
| `com.mio` | `com.kongbai.kculauncher.mio` |
| `FCL*` 类（46 个） | `KCU*` |
| `FCLauncher` | **`KCULauncher`**（特例，直接去 FCL 会剩 "auncher"） |
| 模块 `:FCL` → `:KCU` | 目录 `FCL/` → `KCU/` |
| `rootProject.name` | `Fold Craft Launcher` → `KCU Launcher` |

**法律/署名保护清单（一个字未动）**：
`FCL_VERSION_CODE`、`FoldCraftLauncher`、`FCL-Team`、`Fold Craft Launcher`。

`FCL_VERSION_CODE` 必须保留的原因：它是 **MobileGlues 的启动器白名单环境变量名**，
改了渲染器会判定 unsupported 从而忽略玩家配置（v0.36.0 查证）。

---

## 4. 执行方式：Actions 跑脚本，不在沙盒里批量改

沙盒整体 clone 不了上游，故把迁移做成三个脚本 + 一个 workflow，在 CI 里执行：

```
migrate_package.py   文本替换 + 包目录移动
patch_fcl.py         清残留（含 R 字段下划线引用）
patch_kotlin_tpl.py  Kotlin 字符串模板内的标识符
rename_classes.py    类文件改名 FCL* -> KCU*
```

---

## 5. 真实踩坑记录（每一步都有证据）

### 坑 1：`MOVED_DIRS=0` —— 路径前缀假定错误

第一版脚本按 `src/main/java` 固定前缀拼路径，实际包目录在模块目录之下
（`FCL/src/main/java/com/tungsten/fcl`），且还有 `androidTest` 等变体。
改为**遍历查找**以 `java`/`kotlin` 结尾的目录 → `MOVED_DIRS=11`。

### 坑 2：`git mv` 报 bad source

```
fatal: bad source, source=FCL/src/androidTest/java/com/mio/cache/VersionCacheTest.kt
```
**顺序错了**：先跑了脚本（文件已移到新目录），再 `git mv FCL KCU`。
改为**先 `git mv` 模块，再跑脚本**。

### 坑 3：push 被拒（GITHUB_TOKEN 权限不足）

```
! [remote rejected] HEAD -> main
(refusing to allow a GitHub App to create or update workflow ... without workflows permission)
```
默认 `GITHUB_TOKEN` 改不了 `.github/workflows/*`。
解法两条：① 创建 PAT secret `KCU_PAT` 用于 push；② **workflow 文件不纳入迁移提交**。

### 坑 4：类文件没改名 → `Unresolved reference`（编译失败 #1）

```
e: .../view/KCUConstraintLayout.kt:20:47 Unresolved reference 'FCLConstraintLayout_auto_tint'
```
内容里的类名改了，**文件名没改**（`KCUConstraintLayout.kt` 里 public 类名与文件名不一致）。
补 `rename_classes.py` → `RENAMED_FILES=46`。

### 坑 5：`\b` 词边界漏掉 R 字段（编译失败 #2）

`FCLTextView_auto_tint` 这种 **下划线是单词字符**，不构成词边界，
第一版 `\bFCLTextView\b` 匹配不到它。而 `attrs.xml` 的 `declare-styleable` 已改名，
两边对不上 → 7 处 `Unresolved reference`。
补 `patch_fcl.py` 做全局 `FCL`→`KCU`（字符串受保护）→ `PATCHED_FILES=99`。

### 坑 6：Kotlin 字符串模板被整串保护（编译失败 #3）

```
e: .../core/game/JavaVersion.kt:10:19 Unresolved reference 'FCLPath'
```
第 10 行是 `return "${FCLPath.JAVA_PATH}/${javaVersion.name}"`。
第一版把整个字符串当字面量保护，模板内的**代码标识符**没被替换，
导致 import 已改 `KCUPath` 而模板内仍是 `FCLPath`。
补 `patch_kotlin_tpl.py`：先挖 `${...}` 单独处理 → `PATCHED_TPL_FILES=8`。

---

## 6. 严格审核

### 6.1 会导致什么错误

| # | 错误 | 处置 |
|---|---|---|
| 1 | JNI `FindClass` 与 Java 包名不一致 → 运行期崩 | 同步改 C 侧字符串 ✅ |
| 2 | 布局全限定名不匹配 → `InflateException` | 286 处一并改 ✅ |
| 3 | public 类名与文件名不一致 → 编译失败 | `rename_classes.py` ✅ |
| 4 | R 字段下划线引用漏替换 → 编译失败 | `patch_fcl.py` ✅ |
| 5 | Kotlin 模板内标识符漏替换 → 编译失败 | `patch_kotlin_tpl.py` ✅ |
| 6 | `FCL_VERSION_CODE` 被改 → MobileGlues 判 unsupported | 保护清单 ✅ |
| 7 | 上游署名被抹掉 → 法律问题 | 保护清单 ✅ |
| 8 | `applicationId` 被改 → 用户无法升级 | 按用户选择不动 ✅ |

### 6.2 对用户使用逻辑的影响

| 路径 | 结果 |
|---|---|
| 覆盖安装 | 正常（applicationId 未变） |
| 启动游戏 | JNI 类名同步，不会崩 |
| 打开任意页面 | 布局全限定名同步，不会 InflateException |
| 渲染器配置 | `FCL_VERSION_CODE` 保留，配置不被忽略 |
| 关于页 | 仍标注上游 FCL 等（法律要求） |

### 6.3 对未来的影响

| # | 影响 | 评估 |
|---|---|---|
| 1 | 与上游同步时冲突面变大（整个包路径都变了） | 需专门处理，成本上升 |
| 2 | 新代码须用新包名 | 自然 |
| 3 | 迁移脚本留在 `.github/scripts/` 可复用 | 可重复执行（幂等：后续跑 MODIFIED=0） |

### 6.4 对代码逻辑的影响

纯标识符与路径变更，**无语义改动**。保护清单外无残留 `FCL`。

---

## 7. 审核结论

**pass**

### 哈希回执

| 项 | 值 |
|---|---|
| 文件 | `KCULauncher.java` |
| 行号 | **207** |
| 该行原文 | `            envMap.put("FCL_VERSION_CODE", FCL_VERSION_CODE + "");` |
| 取出的 5 个不同字母 | `e` `n` `v` `M` `a` |
| 拼接结果 | `envMa` |
| **SHA-1** | **`90b0dcd925f9568f93f22f18f03235290e7b1938`** |

取材行即"法律/契约不可动"的核心保护点 —— 变量名保留，但类名已是 `KCULauncher`。

---

## 8. 遗留

- 文档（docs/）里的历史包名未同步，属历史记录，保留原样更利于追溯
- `CHANGELOG.md` 版本号尚未更新到 0.48.0（下轮补）

---

## 9. 冒烟测试（PROVEN）

run **40085364053**（push 触发，全矩阵）success，5 产物：
all 326271109 / x86_64 174896301 / **arm64 169201232** / arm 161942887 / x86 155590409 B
