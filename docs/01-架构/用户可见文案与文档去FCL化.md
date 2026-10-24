# 用户可见文案与文档去 FCL 化（v0.49.0）

执行者：yuanbao，2026-10-24

---

## 1. 承接

v0.48.0 完成了**代码层**去 FCL（包名 / 类名 / 模块名）。
但用户可见的**文案层**和**文档层**仍有大量 FCL 残留 —— 这轮补上。

---

## 2. 追溯：残留都在哪

| 位置 | 状态 |
|---|---|
| `values/strings.xml` 值 | v0.48 的 patch_fcl.py **已顺带改掉**大部分 plain `FCL`（值不在引号内故未受保护） |
| 含 `Fold Craft Launcher` / `FCL-Team` 的值 | **被 PROTECT 挡住，全部保留** ← 本轮要甄别 |
| 资源 ID `settings_fcl_*` | **未改**（106 处） |
| 资源 ID `folder_fcl_log` / `upload_fcl_log` | **未改** |
| `README_EN.md` / `README_RU.md` | **完全是上游原文**：标题、徽章、赞助入口 |
| 图片路径 `/FCL/src/...` | **已失效**（目录已改名 KCU） |

**关键发现**：v0.48 的保护清单（`Fold Craft Launcher`、`FCL-Team`）在起作用 —— 所以
`splash_title`（Welcome to **Fold Craft Launcher**）这类**应用自身**的文案也被一并保护了。
**保护粒度过粗**：同一个词，作为"上游署名"该留，作为"本应用自称"该改。

---

## 3. 分类处置

### 3.1 应用自身 → 改 KCU（用户看到的是"这个应用叫什么"）

| 文件 | 条目 |
|---|---|
| values | `splash_permission_msg`、`splash_title`、`crash_reporter_hint` |
| values-zh | `splash_permission_msg`、`splash_title`、`modpack_unsupported`、`crash_reporter_hint` |

### 3.2 上游署名 / 法律声明 → 保留

| 条目 | 原因 |
|---|---|
| `about_launcher` = `Fold Craft Launcher` | 上游项目名 |
| `about_developer` = `FCL-Team` | 上游团队名 |
| `about_desc` 中"本应用基于 Fold Craft Launcher 开发" | GPL 衍生关系声明 |
| values-zh `about_desc` 中"由FCL-Team基于HMCL" | 同上 |
| `FCL_VERSION_CODE` | MobileGlues 白名单环境变量 |

### 3.3 资源 ID

| 原 | 新 | 处数 |
|---|---|---|
| `settings_fcl_*` | `settings_kcu_*` | **106**（13 文件） |
| `folder_fcl_log` | `folder_kcu_log` | 同步 `ManagePage.kt` |
| `upload_fcl_log` | `upload_kcu_log` | — |
| `modpack_files_fclversion_cfg` | **不改** | 是文件格式名 `.fclversion.cfg`，改了会变语义 |

### 3.4 README_EN / README_RU

| 项 | 处理 |
|---|---|
| `src="/FCL/src/..."` | → `/KCU/src/...`（**失效路径，必须修**） |
| `<h1>Fold Craft Launcher</h1>` | → `KCU Launcher` |
| CI / Downloads / Release 徽章 | → `kongbai9288/KCU-Launcher` |
| **赞助入口（afdian.com/@tungs）** | **移除** —— 那是上游作者的赞助 |
| 项目介绍 | 改为 "a derivative of Fold Craft Launcher"，**保留上游署名** |
| Contributors Wall | → 本仓库 |
| Weblate | 加说明"继承自上游" |
| Related Projects / Dependencies | **保留**（法律署名） |

---

## 4. 真实编译失败：Lint ExtraTranslation

run **40092091151** 失败：

```
Lint found 4 errors.
values-de/strings.xml:311: Error: "folder_fcl_log" is translated here
  but not found in default locale [ExtraTranslation]
values-uk / values-ru / values-zh-rHK  同样
Execution failed for task ':KCU:lintVitalRelease'
```

**原因**：我手动改了 `values` / `values-zh` / `values-vi` 三个文件，**漏了 de / uk / ru / zh-rHK**。
默认 locale 已无 `folder_fcl_log`，但这些语言还翻译着它 → Lint 判定为多余翻译 →
**release 构建的 lintVital 是 fatal，直接中断**。

**修法**：补丁脚本改为遍历**所有** `values-*/strings.xml` 统一改名 → `SETTINGS_PATCHED_FILES=4`。

**教训**：改资源 ID 必须同步全部语言目录，不是改默认 + 中文就够了。
（对比：`settings_fcl_*` 那 106 处因为脚本遍历了所有 .xml，一次通过。）

---

## 5. 严格审核

### 5.1 会导致什么错误

| # | 错误 | 处置 |
|---|---|---|
| 1 | **Lint ExtraTranslation 中断 release** | 同步全部语言目录 ✅ |
| 2 | 资源 ID 改了代码没同步 → 编译失败 | `ManagePage.kt` 已同步，CI 证伪 ✅ |
| 3 | 法律署名被误删 | 逐条分类，保留 4 类 ✅ |
| 4 | README 图片 404 | 路径已改 ✅ |
| 5 | `modpack_files_fclversion_cfg` 误改 | 保留（文件格式名）✅ |

### 5.2 对用户使用逻辑的影响

| 路径 | 结果 |
|---|---|
| 首次启动欢迎语 | "Welcome to KCU Launcher" |
| 权限申请提示 | "grant KCU Launcher the permission" |
| 崩溃报告提示 | "KCU Launcher has encountered..." |
| 管理页日志项 | 文案与 ID 都是 KCU |
| 设置页渲染器/驱动项 | ID 已改，功能不变 |
| 关于页 | **仍标注上游 FCL**（法律要求） |

### 5.3 对未来的影响

| # | 影响 | 评估 |
|---|---|---|
| 1 | 新增 string 时用 `settings_kcu_` 前缀 | 自然 |
| 2 | 再改资源 ID 必须全语言同步 | 已写成规则 262 |
| 3 | EN/RU README 与中文 README 结构仍不完全一致 | 遗留 |

### 5.4 对代码逻辑的影响

纯标识符与文案变更，**无语义改动**。

---

## 6. 审核结论

**pass**

### 哈希回执

| 项 | 值 |
|---|---|
| 文件 | `patch_settings.py` |
| 行号 | **3** |
| 该行原文 | `"""把剩余 FCL 资源标识符改名：settings_fcl_* -> settings_kcu_*` |
| 取出的 5 个不同字母 | `F` `C` `L` `s` `e` |
| 拼接结果 | `FCLse` |
| **SHA-1** | **`f73b791ef2fd219f93332e82dabded392d800342`** |

（首次自动取材取到中文注释行，中文字符也被 `isalpha()` 判为"字母"，
已修正为只取 ASCII 字母，规则 263 记录。）

---

## 7. 遗留

- 其余 8 个语言目录（ja / in / tr / fa / pt-rBR 等）的**值**仍未同步 KCU，会回退到默认英文
- `docs/` 内历史文档的包名未同步（历史记录，保留利于追溯）

---

## 8. 冒烟测试（PROVEN）

run **40092760318**（push 触发，全矩阵）success，5 产物：
all 326271045 / x86_64 174896285 / **arm64 169201210** / arm 161942816 / x86 155590355 B
