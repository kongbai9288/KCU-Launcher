# apiupdata.md — 更新记录：连接了什么、同步了什么、当时怎么想

> 用户要求：记录**每一次更新所调用的库如何连接**、**同步上游的哪些内容**，
> 并为**前面的内容建立索引**；每一条都要写清
> **①当时怎么想的 ②看到代码觉得应该怎么做 ③安全性怎么样**。
>
> 维护者：yuanbao　　最后更新：2026-10-24　v0.51.0（编译已验证 run 40096102606）

---

## 0. 怎么用这份文档

- **倒序排列**，最新的在最上面
- 早期轮次（v0.13–v0.42）为**概括条目**，细节见 `CHANGELOG.md`
- 近期轮次（v0.43 起）为**完整条目**，三要素齐全
- 每次更新后**照下面模板追加一条**，不要改写历史

### 模板（下次照抄）

```
## vX.YZ.0 — 日期

**做了什么**：一句话

**调用了什么库 / API，怎么连的**
| 库 / API | 怎么连 | 用途 |
|---|---|---|

**同步上游哪些内容**
- （有则写，无则写"本轮未同步上游"）

**① 当时怎么想的**

**② 看到代码觉得应该怎么做**

**③ 安全性怎么样**
- 会导致什么错误：
- 对用户：
- 对未来：
- 对代码逻辑：

**审核**：pass / 不通过
**哈希回执**：文件:行 → 5 字母 → SHA-1
**编译证据**：run id，产物字节数
**未验证**：
```

---

## 1. 通用：本项目更新时用到的外部连接

这些是**每次都可能用到**的连接，集中记一次，各轮不再重复。

### 1.1 GitHub REST API

| 用途 | 怎么连 |
|---|---|
| 认证 | `Authorization: Bearer <PAT>`；PAT 存于仓库 secrets，也用于沙盒调用 |
| 读写文件 | `GET/PUT/DELETE /repos/{owner}/{repo}/contents/{path}`，内容 **base64**，`PUT` 必须带 `sha` |
| 全仓搜索 | `GET /search/code?q=...+repo:...`（**本地目录 grep 会漏，必须用它**） |
| 触发构建 | `POST /repos/.../actions/workflows/{name}/dispatches`，body `{"ref":"main"}` |
| 查构建结果 | `GET /repos/.../actions/workflows/build.yml/runs?per_page=N` |
| 查产物 | `GET /repos/.../actions/runs/{id}/artifacts`（**以产物数量为准，不看 conclusion**） |
| 留诊断 | `POST /repos/.../issues`（把 CI 日志贴成 issue，绕开 logs API 403） |

> **踩过的坑**：logs API 常返回 403；`GITHUB_ENV` 在失败步骤后不保留。
> 所以诊断一律写成 issue，不依赖 Actions 的日志下载。

### 1.2 GitHub Licenses API

| 用途 | 怎么连 |
|---|---|
| 拉许可证全文 | `GET https://api.github.com/licenses/{spdx_id}`，响应 `body` 字段即官方原文 |
| 查上游项目的 SPDX | `GET /repos/{owner}/{repo}` → `license.spdx_id` |

> **为什么用它**：许可证是法律文本，默写/转述/摘要都可能失真。官方 API 给的就是官方发布原文。
> v0.47 用它拉了 5 份共 71682 B。

### 1.3 GitHub Actions 上的迁移脚本

| 用途 | 怎么连 |
|---|---|
| 大规模重构 | 沙盒整体 clone 不了上游 → **把脚本提交到 `.github/scripts/`，用 workflow 在 CI 里跑** |
| push 权限 | 默认 `GITHUB_TOKEN` **改不了 `.github/workflows/*`**，需 PAT secret；或把 workflow 文件排除出提交 |

### 1.4 沙盒本地

Python 标准库（`hashlib`、`xml.etree`、`base64`、`urllib`）+ `PyYAML`。
中文文档用 **WenQuanYi Micro Hei**（若涉及字体）。

---

## 2. 上游同步总账

| 轮次 | 同步了上游什么 | 处理方式 |
|---|---|---|
| v0.48 | 包名/类名全量替换 | 保留 `FCL_VERSION_CODE` 等 4 项法律标识 |
| v0.47 | 5 份许可证全文 | 官方 API 原文，内置本地 |
| v0.46 | 上游项目 SPDX 数据 | 官方 API 查询，不凭印象 |
| v0.36 | MobileGlues 双版本 so 比对 | 结论：无代码级改动，仅重编译 |
| v0.29 | 上游更新源 | **停用**（FCL 版本号会误判为更新） |
| v0.30/0.32 | 夸克推广拦截 | **停用并删除** |
| v0.27/0.28 | 远程公告 | **移除**（对上游域名的隐式联网） |
| v0.26 | 主题引擎 | **拆解**（保留 API 面，抽掉用户可改能力） |

**原则**：上游的**功能**可保留，**对上游域名的联网**和**替上游做推广**一律移除。

---

## 3. 按轮次记录（倒序）

---

## v0.51.0 — 2026-10-24

**做了什么**：仅保留 cn/en 语言资源；`fcl_styles.xml` → `kcu_styles.xml`；建立索引与本文档。

**调用了什么库 / API，怎么连的**

| 库 / API | 怎么连 | 用途 |
|---|---|---|
| GitHub Contents API | `GET /contents/KCU/src/main/res` | 列出全部 15 个 values* 目录 |
| GitHub Code Search | `q=fcl_styles+repo:...` | 查 `fcl_styles` 引用点 |
| GitHub Contents API | `GET/PUT/DELETE` | 读→创建 `kcu_styles.xml`→删旧文件 |
| Actions dispatch | `trim-languages.yml` | 在 CI 里 `git rm -r` 并提交 |

**同步上游哪些内容**
- 本轮未同步上游功能。但**发现并修正**了一处上游遗留：资源文件名 `fcl_styles.xml`。

**① 当时怎么想的**
用户说"去除非 cn,en 以外的语言"。第一反应不是直接删——而是**先确认 `values-*` 里有没有伪装成语言的非语言目录**。
果然：`values-night`（夜间模式）和 `values-v31`（API31+）**不是语言**，删了会直接坏。
所以设计成**显式白名单**，而不是 `rm -rf values-*`。

关于 `zh-rHK` / `zh-rTW`：它们是中文变体（香港/台湾），我判断属于"cn"范畴予以保留，
只删明确的外语 9 个。**这个判断用户可推翻**（删掉很容易，加回来要重新翻译）。

**② 看到代码觉得应该怎么做**
- 先看目录清单：每个待删目录**只有 `strings.xml`**（诊断输出证实），没有隐藏的其他资源 → 删安全
- `values/` 里有 `attrs.xml colors.xml fcl_styles.xml strings.xml themes.xml`
- 搜 `fcl_styles` 只命中 `CcStyles.kt`，且**两处都是注释**（`zl_styles_to_fcl`、`fcl_rocker_style_to_zl_joystick` 是上游 `cc.py` 的函数名）
- 内容里 style name 已是 `KCUSpinnerPopupAnimation`，无 FCL → **只需改文件名**

**③ 安全性怎么样**
- **会导致什么错误**：
  - 误删 `values-night` / `values-v31` → 夜间模式和 API31+ 资源丢失，直接坏 → 已用白名单规避，并加校验步骤回显"白名单目录必须都还在"
  - 删除语言目录触发 Lint `ExtraTranslation`？不会——该问题只在**默认 locale 缺 key 而其他语言有**时出现；删语言是反向操作
  - 改资源文件名 → Android 只认 `R.style.X`（资源名），**不认文件名** → 安全
- **对用户**：外语用户将回退到默认英文；中文用户不受影响
- **对未来**：语言维护面从 15 降到 6，v0.49 遗留的"8 个语言目录值未同步"自动消解
- **对代码逻辑**：纯资源文件增删，零逻辑改动

**保留的注释说明**：`CcStyles.kt` 里 `fcl_rocker_style_to_zl_joystick` 等是**上游 `cc.py` 的真实函数名**，
性质类似 `FCL_VERSION_CODE` —— 它是对照关系说明，改名会丢失与上游脚本的对应关系，故保留。

**审核**：pass
**哈希回执**：`kcu_styles.xml:2` → `resou` → `d04f59619c2e4ba830506a18c0ff17f9774f439d`
**编译证据**：run 40096102606，5 产物：all 326109611 / x86_64 174732673 / **arm64 169038506** / arm 161780888 / x86 155428126 B
　　arm64 比上轮（169200989）**少约 162 KB**，与删掉 9 个语言目录的体量吻合 —— 是"东西真删了"的旁证
**未验证**：外语用户实际回退表现；真机安装仍为零

---

## v0.50.0 — 2026-10-24

**做了什么**：关于页文案改为"由架构师 kongbai9288 以 FCL 为主体"。

**调用了什么库 / API，怎么连的**

| 库 / API | 怎么连 | 用途 |
|---|---|---|
| GitHub Code Search | `q=R.string.about_desc+repo:...` | 定位唯一引用点 `AboutPage.kt:126` |
| GitHub Code Search | `q=about_launcher / about_developer` | 确认二者是**死字符串** |
| GitHub Contents API | 读写两份 `strings.xml` | 改文案 |

**同步上游哪些内容**
- 未同步功能。修正了中文那份里"开发者是 FCL-Team"的**事实错误**。

**① 当时怎么想的**
用户给的文案里 `Flod craft laucher` 有两处笔误。
我判断**这跟 v0.48 的包名案例是同一性质**——专有名词且带法律署名，不能照抄笔误，所以改成 `Fold Craft Launcher`。
另一件事：新文案说"架构师 kongbai9288"，而 `about_developer` 存着 `FCL-Team`，**会不会冲突**？
这个疑问促使我去查——结果二者**源码引用为 0**，根本不显示，所以不冲突，保留不动。

**② 看到代码觉得应该怎么做**
`AboutPage.kt:126` 是 `R.string.about_desc` 的**唯一引用点**，整页关于描述就这一个字符串 → 改动面极小。
第二段"点击「开源许可」可查看…"是 **UI 引导**，不能删——删了用户不知道有那个入口。
中文那份里"如果你要传播分发该软件，请遵循 GPL-v3"是法律声明，用新文案的"遵循 GPL-v3 许可发布"**承接**，不能丢。

**③ 安全性怎么样**
- **会导致什么错误**：全角（）「」在 XML 合法；`\n\n` 是标准转义 → 无；默认 locale 有 `about_desc`，不触发 Lint
- **对用户**：关于页信息现在准确了（此前中文那份把开发者写成 FCL-Team）
- **对未来**：`about_launcher` / `about_developer` 仍是死字符串，将来启用"上游组件"组正好用得上
- **对代码逻辑**：纯文案，零代码改动

**审核**：pass
**哈希回执**：`strings.xml:61` → `strin` → `11ad20e26a410fd5dde5c207487faf383880a4ee`
⚠ 与 v0.41 的 `Strin`（Java `String`）字母集合相同、大小写不同，哈希不同——主动说明。
**编译证据**：run 40094103201，5 产物，arm64 169200989 B
**未验证**：约 90 字在小屏/大字号下可能截断

---

## v0.49.0 — 2026-10-24

**做了什么**：文案层与文档层去 FCL 化（v0.48 只做了代码层）。

**调用了什么库 / API，怎么连的**

| 库 / API | 怎么连 | 用途 |
|---|---|---|
| GitHub Contents API | 读取 `README.md` / `README_EN.md` / `README_RU.md` | 发现 EN/RU 是上游原文复刻 |
| GitHub Code Search | `q=upload_fcl_log` / `R.string.folder_fcl_log` | 定位引用点 |
| Actions | `migrate-package.yml` + `patch_settings.py` | 遍历改名 |

**同步上游哪些内容**
- README 的 Related Projects / Dependencies **全部保留**（法律署名）。
- 移除**上游作者的赞助入口**（afdian.com/@tungs）——那不是我们的。

**① 当时怎么想的**
v0.48 把 `Fold Craft Launcher` 整词保护，导致 `splash_title`（Welcome to **Fold Craft Launcher**）这种
**本应用自称**也被留住了。同一个词——**上游署名该留、自称该改**——保护粒度过粗了。
所以这轮按**条目**甄别，不再整词一刀切。

**② 看到代码觉得应该怎么做**
- EN/RU README 完全是上游原文：标题、徽章指向 FCL-Team、**还挂着上游作者的赞助链接**
- 图片路径 `/FCL/src/...` **已经挂了**（目录早改名 KCU），必须修
- `modpack_files_fclversion_cfg` 对应文件格式 `.fclversion.cfg` → **不能改**，改了变语义

**③ 安全性怎么样**
- **会导致什么错误**：本轮**真实编译失败一次**——只改了 values/values-zh/values-vi，
  漏了 de/uk/ru/zh-rHK，触发 Lint `ExtraTranslation`，**`lintVitalRelease` 是 fatal，直接中断 release**
- **对用户**：首次启动、权限提示、崩溃提示都变成 KCU；关于页仍标注上游
- **对未来**：改资源 ID 必须同步**全部**语言目录（已写成规则 262）
- **对代码逻辑**：纯标识符与文案，无语义改动

**审核**：pass
**哈希回执**：`patch_settings.py:3` → `FCLse` → `f73b791ef2fd219f93332e82dabded392d800342`
（首次自动取材撞到中文注释行——`isalpha()` 把中文也认成字母，已修为只取 ASCII）
**编译证据**：run 40092760318，5 产物，arm64 169201210 B
**未验证**：实机安装

---

## v0.48.0 — 2026-10-24

**做了什么**：全量去 FCL 化——包名 / 类名 / 模块名 / 项目名，1244 个源文件。

**调用了什么库 / API，怎么连的**

| 库 / API | 怎么连 | 用途 |
|---|---|---|
| Actions | `migrate-package.yml` + 4 个脚本 | 在 CI 里跑（沙盒 clone 不了上游） |
| GitHub Code Search | 查 `FindClass`、`Java_com_tungsten` | 找 JNI 硬约束点 |
| PAT secret `KCU_PAT` | workflow checkout + push | 绕过 GITHUB_TOKEN 权限不足 |

**同步上游哪些内容**
- 结构全量重命名；保留 `FCL_VERSION_CODE`、`FoldCraftLauncher`、`FCL-Team`、`Fold Craft Launcher`。

**① 当时怎么想的**
第一反应是"改包名而已"。**追溯后发现不能只改 Java**：
JNI `FindClass("com/tungsten/fclauncher/bridge/FCLBridge")` 是硬字符串——
**改 Java 不改 C，能编译过，装上去一启动就崩**。这改变了整个方案的可行性判断。

**② 看到代码觉得应该怎么做**
布局 XML 另有 286 处 `<com.tungsten.fcllibrary...>` 全限定名，不改会 `InflateException`。
`applicationId` **不动**——它是 APK 唯一标识，改了用户无法覆盖安装升级。

**③ 安全性怎么样**
- **会导致什么错误**：JNI 类名不一致（启动崩）、布局全限定名不匹配（InflateException）、
  public 类名与文件名不一致（编译失败）、R 字段下划线引用漏替换（编译失败）、
  Kotlin 模板内标识符漏替换（编译失败）——**五个全部真实踩到并修复**
- **对用户**：覆盖安装正常；渲染器配置不被忽略（因 `FCL_VERSION_CODE` 保留）
- **对未来**：与上游同步时冲突面变大，需专门处理
- **对代码逻辑**：纯标识符与路径变更，无语义改动

**审核**：pass
**哈希回执**：`KCULauncher.java:207` → `envMa` → `90b0dcd925f9568f93f22f18f03235290e7b1938`
（取材行即"不可动"核心：变量名保留，类名已是 KCULauncher）
**残留扫描**：`com/tungsten` **0**、`com/mio` **0**、`FCL/` **0**、`com/kongbai` **1004**
**编译证据**：run 40085364053，5 产物，arm64 169201232 B
**未验证**：实机安装

---

## v0.47.0 — 2026-10-24

**做了什么**：五份许可证全文内置（71682 B）；`about_desc` 替换（当时为上游文案）。

**调用了什么库 / API，怎么连的**

| 库 / API | 怎么连 | 用途 |
|---|---|---|
| **GitHub Licenses API** | `GET /licenses/{gpl-3.0,lgpl-2.1,lgpl-3.0,bsd-3-clause,zlib}` | **拉官方原文**，不手写 |
| GitHub Contents API | 写入 `assets/licenses/` | 内置本地 |

**同步上游哪些内容**
- 许可证全文（官方 API 原文）。

**① 当时怎么想的**
法律文本不能默写。转述、摘要都可能失真 → 必须用官方 API 取原文。

**② 看到代码觉得应该怎么做**
点条目 → 本地全文页，**不联网**。Android-OpenJDK-Build 官方 `license` 字段为空 → 标"未声明"+官方链接，不编造。

**③ 安全性怎么样**
- **会导致什么错误**：漏 `import android.content.Intent` → **五架构全崩**（本轮真实发生）
- **对用户**：许可页完全本地，零联网
- **对未来**：合规基础扎实
- **对代码逻辑**：新增 assets，无逻辑改动

**审核**：pass
**哈希回执**：`KcuLicenseTextActivity.java:31` → `prote` → `a2d455d65e5ea85773bcf5e76b57b6178053f62e`
⚠ 哈希碰撞：先取到 `EXTRA_FILE` 行得 `publi`，与 v0.28/0.29 完全相同——**取字从行首开始，行首是 public 就必撞**，规则 193 据此修正。
**编译证据**：run 40081537478
**未验证**：全文页排版

---

## v0.46.0 — 2026-10-24

**做了什么**：关于页重排三组；开源许可条目带图标；SPDX 全部查官方 API。

**调用了什么库 / API，怎么连的**

| 库 / API | 怎么连 | 用途 |
|---|---|---|
| GitHub Repos API | `GET /repos/{owner}/{repo}` → `license.spdx_id` | 查各上游项目 SPDX |

**同步上游哪些内容**
- 上游项目列表与许可信息（**官方数据，不凭印象**）。

**① 当时怎么想的**
原来关于页 6 条链接**条条指向上游，连赞助都指向上游作者**——我们叫 KCU。这是身份错位。

**② 看到代码觉得应该怎么做**
图标用**基础字符**（`↓ # ◇ ≡ !`）配圆形强调色底——不新增 drawable、不会 emoji 化。
图标位必须设 `auto_text_tint="false"` + 白字，否则**重踩 v0.22 的"黑字压强调色底"坑**。

**③ 安全性怎么样**
- **会导致什么错误**：Kotlin 顶层函数不能 `import com.mio.util.openLink`（编译成 `AndroidUtilKt`）→ 需用 `AndroidUtilKt.openLink`；新建 Activity **没注册 Manifest** → 点进去直接崩，编译期发现不了
- **对用户**：关于页身份正确了
- **对未来**：图标方案可复用
- **对代码逻辑**：新增页面，无既有逻辑改动

**审核**：pass
**哈希回执**：`KcuLicenseActivity.java:97` → `rowse` → `4ed3e64a49466f0cc9a5b2e700579fc6bef0bd6e`
**关键发现**：PojavLauncher 是 **LGPL-3.0 不是 GPL-3.0**——我印象是 GPL，写下去就是错的。**该查。**

---

## v0.43–0.45 — 小窗尺寸快照修复（三连贯）

**做了什么**：修复小窗/分屏下"按钮变小、布局偏移、MC 无法操控"。

**调用了什么库 / API，怎么连的**

| 库 / API | 怎么连 | 用途 |
|---|---|---|
| GitHub Code Search | `q=getScreenWidth+repo:...` | 命中 17 个文件，分三类 |
| Actions | 全矩阵构建 | 验证 |

**同步上游哪些内容**
- 未同步。修的是**上游固有缺陷**。

**① 当时怎么想的**
用户说"正常 FCL 在这一块都有毛病"。追溯后确认：
`TouchPad.java` 把 `screenWidth` 存成 **final 字段**，然后 `targetX = clamp(initialX + deltaX, 0, screenWidth)`
→ **指针被 clamp 在旧的小窗尺寸内** → 扩大后只能操控左上角一小块。
这是**上游固有缺陷**，不是我们引入的——所以"横屏小窗"这条路**能走**，不必禁用。

**② 看到代码觉得应该怎么做**
改成用时动态获取。**不能全仓正则替换**——会误伤 `cursorRect.width`（RectF 自己的字段）。
`ControlDirection` 有**两个构造函数各一处赋值**，只删一处会编译失败。

**③ 安全性怎么样**
- **会导致什么错误**：v0.45 真实踩到——自定义 `private val width` 编译后生成 `getWidth()`，
  与 Android `View.getWidth()` **JVM 签名冲突**，五架构全崩 → 改名 `screenW`/`screenH`
- **对用户**：小窗体验修复（**未实机验证**）
- **对未来**：其余类若也快照尺寸，同样问题仍在
- **对代码逻辑**：字段改 getter，语义不变

**审核**：pass
**哈希回执**：v0.44 `ControlButton.java:577` → `baseI`；v0.45 `TouchController.kt:33` → `valwi`
（v0.44 首次取材撞 `float`，与 v0.43 完全相同——两行都以 `float x = Math.max` 开头，必然碰撞）
**编译证据**：run 40065239693 / 40067932184
**未验证**：小窗切换行为（连续三轮未验）

---

## 4. 早期轮次概括（v0.13–v0.42）

> 以下为**浓缩条目**。完整细节见 `CHANGELOG.md` 与 `docs/00-总览/改动台账.md`。

| 版本 | 做了什么 | ①怎么想 | ②看到代码怎么做 | ③安全性要点 |
|---|---|---|---|---|
| v0.42 | 构建稳定性：并发控制 + gradle 重试 | 一次上传触发 3 个并发构建，互相打架 | `concurrency` + `cancel-in-progress`；`tee` 不带 `-a` 保证重试日志干净 | 重试不掩盖真失败；已知限制：连续 dispatch 会互相取消 |
| v0.41 | 导入版本显示层 | subtitle 被**赋值两次**，只改第 74 行会被 110 行覆盖 | 两处都改；读一次缓存在 `VersionListItem` | 单架构构建，未冒充全矩阵 |
| v0.40 | 导入 jar → 启动选 JDK | 之前导入的版本**根本进不了列表**（无 json 被忽略） | 生成最小 json + `kcu_meta.json` | 编译失败过一次，`File(File)` 构造错误 |
| v0.39 | 实例级 `load_order.json` | 加 `enabled` 字段防默认行为被改变 | 模板默认 `enabled:false`，生成那次直接 return | 零污染：不改默认路径行为 |
| v0.38 | 自带 so 加载顺序 | 输入模式切换不能做成"死开关" | 只加字段会变成能点没反应 → **下轮一次性做完** | dlopen 名字不含 `/`、必须 `.so` 结尾 |
| v0.37 | 动画弹窗清理 | code search 命中 ≠ 引用 | 两处命中**都在注释里**；`SkinAnimations` 不能删（有调用） | 删文件前沿调用链追 |
| v0.36 | 移除主页皮肤预览 | 保留 `com/mio/skin` 包不动 | `refreshSkin()` 不是死代码——**本地 grep 为 0 是假象** | 必须用 code search |
| v0.35 | MobileGlues 双版本二进制对比 | 想知道"他改了什么" | 符号差集为空；`.comment` 显示官方开了 PGO/BOLT/LTO | 结论：**无代码级改动，仅重编译** |
| v0.32 | 空壳对象与死数据清理 | `LEGACY_FILE_NAMES` 里的文件名**必须保留** | 它驱动 `shouldMigrate`/`cleanUp`，删了旧文件永久残留 | 只删读取，保留文件名 |
| v0.30 | 推广拦截停用 | 半死状态不可接受 | 删概率逻辑、清脏状态 → 启动行为**确定化** | 保留签名，等可整体删除 |
| v0.29 | 上游更新源停用 | KCU versionCode 28 < FCL 版本号 → **必定误报更新** | 比对 schema 后确认无自有更新源 → 停用是正确解 | 保留类，将来接回 |
| v0.26 | 主题引擎拆解 | 87 个文件调用，删类会全崩 | **抽掉"用户可改"能力**，保留 API 面 | `const` 不能调 `.toInt()`（真实错误） |
| v0.22 | autoTint 可读性回归 | v0.21 换浅纸底后，白字落在浅纸上 | 影响面 71 个文件——**白字是多数派** | 确认 `autoBackgroundTint` 0 引用才改 |
| v0.20 | 哈希开关 + 界面统一 | 上游写死 px 未必是疏忽 | 做成两个默认关的开关，**关掉时走原值** | 零污染：关闭时不引入副作用 |
| v0.14 | manifest 契约 | 迁移工具读不到目录 | `Profile.gameDir` 是绝对路径不经 `FCLPath` → **必须有清单** | `Files.write` 要求 API 26，`minSdk` 正好 26 → 改传统流 |
| v0.13 | 公共目录改造 | 存在性是可变状态 | `if (legacy.exists())` 会被 Migrator 自己触发 → **marker 文件固化** | 首次误判即永久 |

---

## 5. 反复出现的教训（跨轮次）

1. **本地 grep 会漏**（v0.36 教训）→ 判断"是否死代码"必须用 **code search**
2. **哈希取字从行首开始** → 行首是 `public`/`private`/`float` 就必然碰撞，已四次
3. **改资源 ID 必须同步全部语言目录**（v0.49 Lint 中断）
4. **改 Java 包名必须同步 JNI**（v0.48，否则编译过、启动崩）
5. **保护清单粒度要细分到条目**（v0.49 纠正 v0.48 的整词保护）
6. **CI 取证不能依赖 logs API / GITHUB_ENV**（v0.40 修），诊断写成 issue
7. **不能把未验证写成已验证**——本文件所有"未验证"都是真话
