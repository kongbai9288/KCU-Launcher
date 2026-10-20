# KCU Launcher 防错清单

> 用法：每轮开工前先 `read` 本文件，再 `read` `KCU启动器_API地图_v0.md`。两条读取完成后才允许写代码。
> 更新：2026-10-19（v1）
> 语言规则：代码与注释禁止 AI 腔（禁止"优雅地""无缝地""强大的""助力"等词），描述性文字保持冷淡陈述。

---

## 0. 判决口径

每个结论必须落到四值之一，禁止中间态含糊表述：

| 值 | 定义 |
|---|---|
| PROVEN | 有可复现的外部证据：API 原始返回、命令输出、文件内容 |
| FAILED | 明确失败，附报错与返回码 |
| NOT PROVEN | 证据二手、过期、或窄于声明 |
| BLOCKED | 具名检查跑不动，给出原因 |

每轮回复前执行 `TZ='CST-8' date '+%a %b %e %H:%M:%S %Z %Y'`，把结果写进回复。

---

## 1. AI 自身的高频错误

### 1.1 幻觉类（最高频，直接导致编译/运行失败）

1. **编造 API / 方法 / 参数**：调用训练语料里"听起来合理"但库里不存在的方法或参数。包名幻觉率实测约 21.7%（开源模型）/ 5.2%（商业模型）；幻觉 API 约占 AI 生成代码错误的 31.67%。
   - 对策：任何未经 `grep`/IDE 解析确认的符号，一律不许写进代码。
2. **编造包名 / 编造依赖**：`implementation("xxx")` 里写不存在的库。属供应链风险。
   - 对策：依赖只允许来自上游仓库已声明的 `libs.versions.toml` / `build.gradle`，新增依赖必须先查证存在。
3. **版本错位**：用旧版 API 写新版、或反过来。
   - 对策：写任何 API 前确认它在当前 AGP / Gradle / Kotlin / minSdk 组合下存在。
4. **凭空路径**：引用不存在的文件、目录、环境变量。
   - 对策：写路径前用 `read` / `ls` 确认。

### 1.2 验证类（最危险，直接导致"谎报成功"）

5. **谎报完成**：改完文件就宣布完成，从未编译或运行。研究统计：智能体任务失败中 44%~76% 伴随"自信地报告成功"；AppWorld 编码轨迹中该比例达 75.8%。
   - 对策：没有命令输出就没有完成声明。跑不动就写 BLOCKED。
6. **伪造证据**：编造终端日志、API 返回、提交记录来圆谎。
   - 对策：输出必须是真实命令回显。
7. **假绿灯**：在失败用例旁新增一个通过用例，宣布套件全绿。
   - 对策：验收看目标状态，不看测试数量。
8. **子代理盲区**：委托出去的任务，父级不读子级 transcript 直接报绿。
   - 对策：委托结果必须回到原始产物核对。

### 1.3 工程类

9. **吞异常**：空 `catch`、`catch (Exception)`、`catch` 后 `return null`。超时、网络错、格式错全变成同一个 `null`。
10. **只写 happy path**：不处理 null、空集合、越界、除零、时区、编码。
11. **上下文遗忘**：会话后半段重写前半段已有的函数，产生两份 `formatXxx()` 逐渐分叉。
12. **跨语言串味**：Java 项目里写出 Kotlin 惯用法，或反之；`.length` 当 `len()` 用。
13. **过度重构**：顺手改无关文件，diff 超出任务范围。
14. **过度抽象**：给简单任务套设计模式。
15. **偷偷加依赖**：为省事引入新库而不是用现有实现。
16. **硬编码环境假设**：绝对路径、写死的架构名、写死的密钥。
17. **注释与代码不符**：注释很自信，行为是另一回事。

### 1.4 行为类

18. **谄媚**：用户框架错了也顺着走，然后在错误前提上建几层楼。
19. **雪球**：早期错误不回滚，后续推理全部为它辩护。
20. **死循环重构**：反复"优化"同一批文件，收敛不了。触发即停机，回到最小改动。

---

## 2. 安卓多版本 / 多架构常见错误

### 2.1 ABI 与 .so

21. **`UnsatisfiedLinkError: couldn't find "libxxx.so"`**：APK 里没有当前设备架构的 .so。
    - 核对 `ndk { abiFilters }` 是否覆盖设备架构；.so 是否真的打进 `lib/<abi>/`。
22. **64 位的"全有或全无"规则**：只要 APK 里出现了任意 64 位库，系统就只从 `lib/arm64-v8a` 找**所有**库。某个库只有 32 位版本 → 64 位设备直接无视它并崩。
    - 对策：每个架构目录下的库集合必须完整一致，不能某些库只有部分架构。
23. **`System.loadLibrary` 名字写错**：文件是 `libfoo.so`，调用必须写 `loadLibrary("foo")`，写 `libfoo.so` 必失败。
24. **`INSTALL_FAILED_NO_MATCHING_ABIS`**：装的 APK 不含设备架构。
    - 核对命令：`adb shell getprop ro.product.cpu.abi`；`unzip -l app.apk | grep "lib/"` 或 `aapt dump badging app.apk | grep native-code`。
25. **jniLibs 路径错**：必须在 `src/main/jniLibs/<abi>/`，非默认路径要在 `sourceSets` 里声明。
26. **JNI 方法签名不匹配**：重载方法名后要加 `__` + 参数签名；动态注册要检查 `JNI_OnLoad` / `RegisterNatives` 返回值与 `FindClass` 路径（用 `/` 不是 `.`）。

### 2.2 版本适配

27. **分区存储**：Android 11（API 30）起限制 `Android/data` 访问；Android 13（API 33）起 `READ/WRITE_EXTERNAL_STORAGE` 被自动拒绝，不再弹窗，随后 `File` 操作抛 `EACCES (Permission denied)`。
    - 对策：需要全域访问用 `MANAGE_EXTERNAL_STORAGE` + `Environment.isExternalStorageManager()` 并跳转设置页；否则走 SAF。
28. **Android 13 通知权限 `POST_NOTIFICATIONS`** 忘记申请。
29. **"安装未知应用"权限** 分来源授权，跳设置页粒度是来源应用。
30. **权限被"不再询问"后** 申请无弹窗，必须引导跳设置页。
31. **minSdk / targetSdk 与依赖的 SDK 要求冲突**：抬高 targetSdk 会连带触发上述存储/通知/后台限制行为变更。

### 2.3 构建与打包

32. **64K 方法数**：`DexIndexOverflowException` / `Cannot fit requested classes in a single dex`。开 `multiDexEnabled true`。注意：启动必需类必须在主 DEX，否则运行时 `ClassNotFoundException` / `NoClassDefFoundError`；用 `multiDexKeepFile` 钉住。
33. **R8/ProGuard 裁掉 JNI 与反射目标**：XML 里的自定义 View 构造函数、JNI 回调方法、反射调用的类被裁 → Debug 正常 Release 崩。
    - 对策：写窄的 keep 规则；禁止 `-keep class com.xxx.** { *; }` 这种全包宽规则，也不要用 `-dontoptimize` / `-dontobfuscate` 全局开关。
    - 用 `proguard-android-optimize.txt`，不要用已废弃的 `proguard-android.txt`。
34. **Resource merge 冲突**：多 module 同名资源内容不一致 → `Duplicate resources` / `Attribute xxx already defined`。
35. **InflateException**：复制第三方 layout 但没复制对应的自定义 View 类，或主题不是 `Theme.MaterialComponents` 系 → `Error inflating class`。自定义 View 必须有 `(Context, AttributeSet)` 构造。
36. **AGP / Gradle / Kotlin / JDK 版本矩阵不锁**：多模块项目里子模块用不同 AGP 版本、CI 与本地不一致。锁定 `gradle-wrapper.properties` + 根 `build.gradle` 版本常量。
37. **PNG / 9-patch / vector 资源问题**：9-patch 黑边断点、vector 用了高于 minSdk 的属性（如 `?attr/` 在 fillColor）→ merge 阶段崩。
38. **多进程导致 Application 重复初始化**：manifest 里带独立进程名的组件（含第三方库偷偷声明的）会让 `Application.onCreate` 每进程跑一遍；多进程同时写同一文件/SharedPreferences 会互相覆盖。用进程名守卫，跨进程共享数据走 ContentProvider。

### 2.4 运行时

39. **主线程阻塞**：下载、解压、JNI 初始化放在主线程 → ANR。
40. **内存**：批量加载未压缩位图、回调持有 Activity/View → OOM，表现为无 Java 异常的 `SIG: 9`。
41. **文件描述符耗尽**：大量未关闭的流/进程句柄。
42. **View 层级过深**：嵌套 RelativeLayout / LinearLayout weight 触发多次 measure；用 ConstraintLayout 扁平化，非立即可见用 ViewStub。

---

## 3. 本项目专属硬规则

43. **基座**：FCL-Team/FoldCraftLauncher（GPL-3.0）。核心逻辑一律复用上游，不自造轮子（版本解析、下载调度、JRE 管理、JNI 启动桥、渲染栈）。
44. **JDK 来源**：FCL-Team/Android-OpenJDK-Build（JRE 8/11/17/21/25，aarch64/arm/x86/x86_64）。不自研 OpenJDK。
45. **布局引入上限（用户经验，强制）**：单次改动从开源项目搬进来的第三方布局文件**不超过 4 个**。每搬一个必须先核对三件事：
    - layout 里引用的自定义 View 类是否已随迁；
    - 使用的主题属性（尤其 `?attr/`）在当前 theme 下是否可解析；
    - 引用的资源名是否与其他 module 冲突。
    搬完立即编译验证，不许攒着一起验。
46. **目录策略**：所有可变内容尽量落到公共目录，前提是实测不影响 I/O 性能与分区存储权限；每条迁移都要记录"为什么能放公共目录"。
47. **功能对齐**：别的启动器有的功能就加，不自行削减；加之前先在对应上游仓库确认该功能的实现模块名与依赖。
48. **第三方登录**：支持第三方登录，走上游已有的 auth 抽象（HMCL `auth` 包体系），不自建协议。
49. **内置终端**：模拟输入式终端（类 GitHub 工作流输入），用于安装其他加载器。必须是真实 shell 执行 + 输出回显，不允许模拟输出。
50. **UI / 图标**：全部使用开源第三方资源。图标来自 `kongbai9288/KCU-Migrator` 的 `Kcu_icon.png`；README 模板沿用该仓库 `README.md` 结构改写。
51. **签名**：keystore 在私有仓库 `kongbai9288/kongbai`。密钥值不写进任何公开仓库、不写进工作流明文，走 CI secrets。
52. **与 KCU Migrator 的关系**：两个独立项目，不做代码合并，只在文件系统层面通过公共目录互通数据。
53. **构建方式**：GitHub Actions 工作流构建，公开仓库 `KCU-Launcher`。

---

## 4. 每轮自检（回复前逐条过）

- [ ] 本轮有没有写出未经 `grep`/`read` 确认的符号、路径、包名？
- [ ] 声明完成的地方，有没有真实命令输出或 API 原始返回？没有 → 改成 NOT PROVEN 或 BLOCKED。
- [ ] 有没有空 `catch` / 只写 happy path？
- [ ] 新增或修改的 layout 是否 ≤ 4 个，且自定义 View、主题属性、资源名已核对？
- [ ] 新增依赖是否来自上游已声明清单？
- [ ] .so 相关改动是否覆盖全部目标 ABI，且各架构目录库集合一致？
- [ ] 是否碰了 R8 keep 规则 / 多进程 / 存储权限？碰了要对应验证。
- [ ] 回复里有没有写本轮真实时间？
- [ ] 代码与注释有没有 AI 腔？

---

## 5. 追加硬规则（v2，2026-10-19 01:00）

54. **.so 不能从公共目录直接加载**：`/sdcard` 是 noexec，`dlopen` 会报 `couldn't map ... segment 1: Permission denied`。结构必须是"公共目录存素材 + 启动时校验复制到私有目录 + `System.load` 只认私有路径"。禁止 root / 改 SELinux / hook linker 绕过。
55. **可执行文件不能从 filesDir execve**：Android 10+ W^X，`getFilesDir()` label 是 `app_data_file`（可写不可执行），`nativeLibraryDir` 是 `exec_type`（可执行不可写）。需执行的东西只能打包进 `jniLibs/<abi>/` 命名成 `libxxx.so`，且 ELF interpreter 必须是 `/system/bin/linker64`（Bionic）。`sh` 走系统 `/system/bin/sh`，不受此限。
56. **无障碍服务读不到游戏画面**：游戏由 `FCLBridge` 用 `Surface` + `SurfaceTexture` + EGL 渲染，不在 View 层级内，`AccessibilityNodeInfo` 树里是空的。抓屏只能走 MediaProjection + OCR。任何基于无障碍树的读屏方案一律判定为不可行，不许写。
57. **屏幕内容属敏感个人信息**：需《个人信息保护法》要求的**单独同意**，不能混进一揽子协议。实现必须包含：独立授权页、可关总开关、默认端侧不出设备、不上传不训练、隐私政策单列。`FLAG_SECURE` 黑屏不绕过。
58. **ABI 全打（4 架构）**：x86 / x86_64 的 natives substitution 修正为 P0。任一架构目录缺失某个 .so 会导致该架构设备直接崩（64 位全有或全无规则，见 #22）。
59. **NDK 统一 27.0.12077973**：FCL 用 27、ZL2 用 25，混用冲突。引入 ZL2 native 模块前必须验证其能在 NDK 27 下编译。
60. **Compose 与 View 混用**：FCL 是 `viewBinding` View 体系，ZL2 是 Compose。新增界面用 Compose，改存量界面前先确认 `ComposeView` 挂载点，不整体重写。

---

## 6. 追加硬规则（v3，2026-10-19 01:03，.so 加载顺序）

61. **默认路径零污染**：玩家不配置 `native_load_order.json`（文件不存在 / `mode=default` / `order` 为空 / abi 不匹配）时，代码路径必须与上游逐字一致，不得引入任何自定义层对象、回调、包装。这是本项目最高风险点——加了抽象层把原路径带崩，等于"什么都不设置就启动不了"。
62. **不自己写 ELF 拓扑排序**：需要自动解析 DT_NEEDED 依赖顺序时，用 `KeepSafe/ReLinker`（Apache-2.0，3311★）或 `facebook/SoLoader`（Apache-2.0，1447★）。两者对"自定义目录"的支持尚未亲验，`auto` 模式在验证前不实现。
63. **不改 LD_LIBRARY_PATH**：动态链接器持有进程创建时的环境副本，native 代码改它无效，这是设计上的安全约束。不 hook linker、不改 SELinux。正确做法是：依赖库与被依赖库放**同一私有目录**，因为依赖链的搜索起点是加载它的那个 .so 所在目录。
64. **加载顺序 = 依赖顺序**：被依赖的先加载。`RTLD_LOCAL` 是自 API 23 起的默认且被正确实现，先加载的库不会把符号暴露给后续 `dlopen` 的库（DT_NEEDED 引用除外）。
65. **每一步加载都要可见**：记录序号、文件名、结果、`dlerror` 原文。失败必须报出"第几个 / 哪个文件 / 什么原因"，禁止 `catch` 后 swallow。典型错误形如 `dlopen failed: cannot locate symbol "xxx" referenced by "libA.so"`。

---

## 7. 追加硬规则（v4，2026-10-19 01:17，构建与兜底）

66. **密钥不写死**：签名密码、CurseForge Key、OAuth Key 一律走环境变量或 CI secrets。读不到就是空字符串，禁止 `!!` / `error()` 让构建失败。未填 CurseForge Key 时构建照常，运行时自动切国内镜像。
67. **CurseForge Key 缺省判定**：上游是 `resValue(..., curseApiKey.toString())`，空值时写入字符串 `"null"`，靠 `!apiKey.equals("null")` 判断。本项目改为空值时写空串 + 运行时切 MCIM 镜像，不依赖 `"null"` 字符串比较。
68. **模块复用优先**：加载器图标统一走一个 `LoaderIcon` 模块（按名 → drawable 映射表），商店页/版本列表/实例详情共用同一函数。不新增多套图标资源，不按页面各建一份。
69. **任何失败都不许崩**：见 `docs/07-兜底`。分级 L0 致命（明确报错不崩）/ L1 功能失效（禁用并提示）/ L2 降级（换路并提示）/ L3 忽略（记日志）。
70. **诊断日志统一格式**：`[KCU] <域> <动作> <目标> <结果> <详情>`。加载失败必须带序号、文件名、`dlerror` 原文。
71. **安全模式**：连续启动失败达阈值自动忽略全部自定义配置，用内置默认启动；入口常驻设置页可手动开关。

---

## 8. 追加硬规则（v5，2026-10-19 01:21，官方文档与版本记录）

72. **Modrinth 版本不硬编码**：base URL 与 API 版本号走配置。官方文档本次抓取显示 v2.7.0（Labrinth），v3 以将来时提及，且沙盒无法直连 `api.modrinth.com` 验证 → v3 存在性当前为 NOT PROVEN。默认 v2，预留 v3 切换位，收到 410 提示并允许切换。**不赌哪一边，也不自创迁移步骤。**
73. **Modrinth 必须设唯一 UA**：`github_username/project_name/version (contact)`。只带 `okhttp/4.9.3` 会提高被封概率。限流 300 次/分按 IP，有无 token 都一样；读 `X-Ratelimit-Reset` 决定重试时机。
74. **Modrinth 用 ID 不用 slug**：slug 与 username 随时可变，长期存储必须用 8 位 base62 ID。
75. **API 弃用返回 410**：必须显式处理，不能当普通错误吞掉。
76. **不得链接非 NDK 平台库**：API 24 起由动态链接器强制执行，`System.loadLibrary` / `DT_NEEDED` / `dlopen` 三种方式行为一致，绕不过。玩家自带 .so 同样受此约束。
77. **targetSdk ≥ 31 需 `<uses-native-library>` 声明**：声明的库在设备上不存在会导致**无法安装**，不是运行时崩溃。
78. **`complianceLevel == 0` 必须提示**：版本清单里该值为 0 表示版本过旧、不支持最新玩家安全特性，启动器应给出可见警告。
79. **离线登录沿用上游**：`fclcore/auth/offline/` 已有 `OfflineAccount` / `OfflineAccountFactory` / `Skin` / `Texture`，不自研。技术事实：可玩单人世界与局域网，`online-mode=true` 的服务器会拒绝（服务端行为，非启动器可控）。
80. **每次版本更新必须写仓库**：`version_map.json`（机器可读）+ `CHANGELOG.md`（人读），均含版本号、日期、变更条目、执行者 `yuanbao`。这是对抗长对话吞上下文的手段，不写则视为该次更新未完成。

---

## 9. 追加硬规则（v6，2026-10-19 01:30，平台适配与接入点）

81. **16 KB 页大小**：Android 15 起 AOSP 支持 16 KB 页设备；Google Play 自 2025-11-01 要求 target ≥ Android 15 的应用必须支持。本项目有大量 .so。必须：重编 native、去掉硬编码 4096/PAGE_SIZE（改 `getpagesize()`）、校验 ELF 段对齐。**玩家自带 .so 同样要校验 16 KB 对齐**，不对齐明确提示不加载。
82. **`MANAGE_EXTERNAL_STORAGE` 是本项目必需**：SAF 在 Android 11+ 无法授予根目录 / `Download` / `Android/data` / `Android/obb`，本项目公共目录 `/sdcard/KCU/` 在根目录下 → 该权限是唯一路径。未授予时回退应用专属目录并引导授权，不是禁用功能。
83. **动态加载的文件必须只读**：Android 14 起可写文件抛 `SecurityException: Writable dex file ... is not allowed`。从公共目录复制到私有目录的 .so，复制完成即置只读。`dlopen` 只需读+执行，不需要写。
84. **MediaProjection 每次都要重新授权**：Android 14 起缓存 Intent 复用或同一实例二次 `createVirtualDisplay` 会抛 `SecurityException`。抓屏只能做成"按需单次"，不能常驻后台。
85. **自定义 .so 走上游接入点，不自己 `System.load`**：正确位置是 `MOD_RUNTIME_DIR` + `NativeLibPlugin.getPaths` + `launch()` 拼的 `LD_LIBRARY_PATH`（`rebaseArgs` 会替换 `${natives_directory}`）。复用上游已验证机制 = 天然满足默认路径零污染。接入前必须先读 `NativeLibPlugin` 源码确认签名（当前未亲验）。
86. **不要把上游"非显然行为"当 bug 修**：`HttpRequest` GET 不校验状态码（Modrinth SHA-1 反查依赖，不可改）；`OSRestriction.allow()` 恒 false（Android 特化）；`GameVersionNumber` 类加载时建静态表；`OAuth.IS_CANCELED` 同一时刻仅支持一次设备码登录。
87. **不要重新引入上游已修的坑**：`OAuth.IS_CANCELED` volatile、`GameMenu.initCursorView` Y 偏移笔误、Room `MIGRATION_1_2`、`ClassicAccountLoginDialog` 回调死锁、`ModrinthInstallTask` TypeToken 误用 `CurseManifest`、`GameOption` 静态参数表多实例覆盖、`ControlDirectionData.equals` 笔误、`CacheRepository.storages` 竞态。
88. **键码映射数组必须保序**：`AndroidKeycodeMap` / `EfficientAndroidLWJGLKeycode` 依赖有序数组做 `binarySearch`，加映射不保序 = 查找失败。
89. **初始化不能只放 `Application.onCreate`**：强杀进程可能绕过 SplashActivity 直接落 MainActivity，上游靠 `MainActivity.onCreate` 的 `ConfigHolder.isInit()` 兜底。自定义层必须跟同一模式。

---

## 10. 追加硬规则（v7，2026-10-19 07:00，CI 踩坑实录）

90. **Actions 内 `git push` 默认没有凭据**：直接 `git push` 会报 `fatal: could not read Username for 'https://github.com'`。必须 `git remote set-url origin "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"`。
91. **导入上游不要保留历史**：上游历史与本地纠缠会导致 rebase 冲突（本次实际遇到 `.gitignore` / `LICENSE` / `README.md` 等 add/add 冲突）。正确做法：`git checkout --orphan <分支>` 生成干净的单一提交，再 `git push --force origin <分支>:main`。
92. **`secrets.*` 不能用在 step 级 `if`**：报 `Unrecognized named-value: 'secrets'`。改为在 workflow 顶层 `env:` 里求值，再在 `if` 里判断 `env.X == 'true'`。
93. **上游的签名库绝不能进公开仓库**：导入时显式 `rm -f key-store.jks debug-key.jks private_key.pepk`，并在 `.gitignore` 末尾追加 `*.jks` / `*.pepk` / `*.keystore`（上游 .gitignore 会覆盖我们的，必须导入后再追加）。
94. **`curl` 直接请求含中文路径的 contents API 会 400**：中文路径必须做 URL 编码。
95. **验证仓库状态时不要只信一次 API 返回**：本次出现过 `contents` / `trees` 与实际 git 状态不一致的情况，关键结论要用 `git/refs/heads/<分支>` + `contents/<具体路径>` 双重确认。

---

## 11. 追加硬规则（v8，2026-10-19 12:54，首次编译通过后）

96. **禁止用 `continue-on-error` 掩盖构建失败**：它会让失败的 gradle 步骤显示为任务成功，这正是"谎报成功"的温床。构建失败必须让任务失败，需要抓取日志就配 `if: always()` 的独立上报步骤。
97. **`matrix` 不能用在 job 级 `if`**：报 `Unrecognized named-value: 'matrix'`。需要按矩阵过滤时改到 **step 级 `if`**（step if 可以用 `matrix`）。
98. **`PIPESTATUS[0]` 只能取一次**：第二次引用已被重置。必须先存变量 `GE=${PIPESTATUS[0]}` 再判断。
99. **同一个 job 内 step id 不能重复**：报 `The identifier 'gradle' may not be used more than once`。
100. **无日志下载权限时用 issue 上报**：沙盒/受限环境拿不到 Actions 日志压缩包（`results-receiver` 被策略拒绝），但 Issues API 可用。把关键输出（产物清单、sha256、错误尾部）通过开 issue 回传，作为可核验证据。
101. **产物体积是反证**：多架构产物若体积完全相同，说明过滤没生效、是同一份复制。本次 all/arm64/arm/x86_64/x86 体积各异，证明 `splits.abi` + `filterJreAssets` 生效。

---

## 12. 追加硬规则（v9，2026-10-19 13:2x，上滑与图标）

102. **差异化只落在数据与资源层，不动 Java/Kotlin**：布局差异靠自写 `controllers/` 布局 JSON，样式靠 `ControlButtonStyle` 配置，事件靠 4 个事件槽组合。上游控件原样复用。
103. **不要引入第三方手势库做上滑**：上游 `ControlButton.handleSwipeMove` 已实现滑动链（坐标命中 + `handleSwipeEnter/Exit` + 与点击语义分离）。第三方库需接管 `onTouchEvent`，会与 `pointerFollow` 和 `acquireLookOwner` 视角独占逻辑冲突，导致滑动时视角乱飘。
104. **滑动链是坐标命中，不是方向映射**：必须真的滑到联动按钮上才触发。手指滑出所有按钮区域会返回 null 不触发任何动作。联动按钮要紧贴主按钮并开 `swipable` + `VISIBLE`，可部分重叠缩短滑动距离。
105. **`findSwipableButtonAt` 四个硬性准入**：是 `ControlButton`、不是自己、`VISIBLE`、`isSwipable()==true`。任一不满足直接跳过，表现为"上滑没反应"。
106. **图标一律本地**：不在运行时拉远程头像/项目图标，不在开源许可页嵌外链图片。许可证全文放 `assets/licenses/`，条目与图标映射放本地 JSON。
107. **收录开源许可必须带上游客项目**：至少含 FoldCraftLauncher、PojavLauncher、HMCL、Android-OpenJDK-Build、Terracotta、SDL3、LWJGL。开发者页每项都要有图标。

---

## 13. 追加硬规则（v10，2026-10-19 13:2x，输入诊断）

108. **触摸 ACTION_MOVE 必须遍历 `getHistorySize()`**：Android 批量投递 motion event，单个 ACTION_MOVE 可能含多个历史采样点。只取 `getX()` 会丢弃中间轨迹，表现为快速滑动不跟手。上游 `TouchPad.java` / `ControlButton.java` 均未处理，是全行业共性短板（ZL2 / Pojav / Amethyst 检索均为 0 次）。
109. **点击判定阈值不要照抄 100ms / 10px**：上游 `TouchPad` 与 `ControlButton` 用此值判定单击，对触摸过严，手指正常点击常超 100ms 导致落空。若改动建议 250ms / 16px。
110. **`MouseMoveMode` 两种语义差别极大**：`CLICK` 是绝对定位（手指位置即光标位置），`SLIDE` 是相对位移（按下点非光标位置）。用户报"点不到位置"时应先确认是否处于 SLIDE 模式，切 CLICK 即可零改码解决。
111. **不要为采样精度问题整体替换输入层**：输入最终过 `CallbackBridge` → `libpojavexec` 的 `input_bridge_v3.c`（native）。换 Java 层要重新对齐 native 事件协议，风险高，且目标实现同样没解决历史点问题。
112. **指针捕获路径的历史点累加是正确写法不是 bug**：`FCLInput.handleMouse` 里累加 `getHistoricalX()` 看起来像把绝对坐标相加，但指针捕获模式下 `getX()` 语义本就是相对增量，累加正确。不要"修复"它。

---

## 14. 追加硬规则（v11，2026-10-19 13:3x，输入方案修正）

113. **【撤销】不要在启动器 Java 层补 `getHistorySize()` 遍历**：这是错误的修正层级。启动器手势与游戏内 mod 的触摸接管会直接冲突——TouchController 官方明确警告"请禁用启动器自带的触摸手势，否则会出现预料之外的鼠标输入"。改启动器触摸层既治不了 MC 视角的病，又会跟原本正常的转动打架。
114. **触摸输入应下移到游戏内**：由 TouchController（LGPL-3.0，游戏内 mod）接管。启动器只集成 `proxy-client` 转发，通过 `TOUCH_CONTROLLER_PROXY` 环境变量（UDP 端口）传给游戏。启动器不实现任何手势逻辑。
115. **必须能一键关闭启动器自带控件**：提供「空布局」入口。官方 wiki 就是这么教 FCL 用户的（新建空布局 → 删除原布局）。两套控件并存必然打架。
116. **MC 灵敏度是立方非线性**：`(0.6*s + 0.2)^3 * 1.2`。换 DPI 后手感完全对不上，这不是 bug 是 MC 设计。需要线性灵敏度时用 `Linear Sensitivity` mod，不要在启动器侧补偿。
117. **`touchscreen` 是 MC 内置选项**：`options.txt` 里 `touchscreen:true/false`（默认 false）。开启后物品栏走基岩版式两段式点击。启动器不同步处理这个开关会导致 GUI"检测不到"输入。
118. **不要在启动器侧补偿 MC 的视角病**：立方灵敏度、符号、帧同步这些 MC 侧的问题，在启动器层做修正是层级错误。

---

## 15. 追加硬规则（v12，2026-10-19 13:5x，TouchController 实况）

119. **不要重新实现上游已有的集成**：FCL 主线 1.3.4.0 已内置 TouchController（`com/mio/touchcontroller/`，`libs.touchcontroller`）。自动链路：ModChecker 检测 mod → `setHasTouchController(true)` → GameMenu 创建实例。装了 mod 就生效。**动手前先搜上游是否已实现。**
120. **TouchController 走 Unix Domain Socket 不是 UDP**：环境变量是 `TOUCH_CONTROLLER_PROXY_SOCKET`（值 = socket 名），不是 `TOUCH_CONTROLLER_PROXY`（值 = 端口）。旧版文档（v0.0.9 时代）的 UDP 说法已过时。
121. **socketName 保持 "FoldCraftLauncher" 不改**：它是本地 IPC 通道名，非用户可见品牌。改名收益为零、但可能触发 mod 侧硬编码导致断连，风险不对称。
122. **依赖版本以官方 `properties.bzl` 为准**：TouchController 的 proxy client 版本定义在 `touchcontroller/proxy/client/properties.bzl`（当前 0.0.8），不要用 fork 里锁定的旧版本（FCL 分支是 0.0.3，主线是 0.0.5）。
123. **沙盒验证不了 Maven 依赖是否存在时，让 CI 来证伪**：改版本号 → 触发构建 → 看结果。比猜测可靠。

---

## 16. 追加硬规则（v13，2026-10-20，包名迁移）

124. **applicationId 与 namespace 可以且应当分离**：`applicationId` 决定 APK 唯一标识（用户可见、可共存、可签名），`namespace` 决定 R 文件包名。改品牌只需改 `applicationId`，不要动 `namespace`——动它就必须同步改上千个源码文件的 package 声明，否则 R 文件找不到直接编译失败。
125. **不要全量重命名源码包名**：本仓库含 "com.tungsten.fcl" 的文件约 1200 个（GitHub code search 实测 total_count=1196）。收益为零（内部实现用户不可见），风险极高（漏一个 import 就崩）。
126. **改 applicationId 时必须同步检查硬编码 authority**：`${applicationId}` 会自动跟随，但**硬编码字符串不会**。本例中 debug 变体的 `file_browser_provider` resValue 是硬编码的 `"com.tungsten.fcl.debug.provider"`，必须手动改。
127. **`com.tungsten.fclcore` / `com.tungsten.fcllibrary` 是独立包不是子包**：Manifest 里 4 个硬编码类引用指向它们。因为源码包名整体不动，这些引用保持有效，不要"顺手改"。
128. **CI 排队超过 10 分钟不等于失败，也不等于成功**：必须看到 conclusion 才能下结论。排队期间只能报 NOT PROVEN，不能凭"已提交"宣称成功。

---

## 17. 追加硬规则（v14，2026-10-20 06:4x，CI 上报准确性）

129. **CI 上报必须区分三态，不能二值**：成功 / 失败 / **结果未知**。早期版本在取不到 `GRADLE_OK` 环境变量时把成功构建标成"失败"（issue #20，实际 conclusion=success）。二值判断会把"没拿到信号"误当成"失败"，是另一种谎报。
130. **产物名是版本与包名的最强证据**：`KCU-release-0.9.0-arm64-v8a.apk` 这个文件名同时证明了 applicationId 改名生效、versionName 生效、命名约定生效。比读配置文件可靠，因为它是构建实际输出。
131. **改 applicationId 后必须查 strings.xml / resources**：`${applicationId}` 会在 Manifest 自动跟随，但 **res/values 里的硬编码 authority 不会**。本例漏了一处 `file_browser_provider`（release 变体走它），靠 CI 全仓 grep 才发现。

---

## 18. 追加硬规则（v15，2026-10-20 12:3x，签名）

132. **不要相信 keystore 的扩展名，看文件头**：本例文件名 `*.jks`，但文件头是 `30 82`（ASN.1 DER SEQUENCE），实际是 **PKCS12**。不显式声明 `storeType` 时 AGP 按 JKS 解析会失败。判定方法：`keytool -list -storetype pkcs12` 能否打开，或看前 4 字节是否 `0xFEEDFEED`。
133. **`storeType` 留环境变量覆盖位**：`System.getenv("KCU_STORE_TYPE") ?: "pkcs12"`，将来换真 JKS 不用改代码。
134. **解析密码文件按内容分隔符而非空格**：形如 `- Keystore 密码：xxx` 的行，按空格 split 会把中文标签混进值里，导致所有候选密码解锁失败。应按全角冒号取最后一段。
135. **「恢复签名库」成功 ≠ APK 已签名**：必须有独立的 `apksigner verify --print-certs` 步骤，由产物自己证明。否则可能签名为空却一路绿灯。
136. **密码/keystore 绝不进任何仓库**：只走 CI secrets（libsodium sealed box 加密）。沙盒内处理完立即删除，不留在待上传目录。
137. **产物 sha256 变化可作签名旁证**：签名会改变 APK 字节。本例未签名 `0bc7bb59…` → 签名后 `8898d29b…`。

---

## 19. 追加硬规则（v16，2026-10-20 12:4x，输入规范 v5.0）

138. **输入规范必须先分流归属再动手**：意图分流/点击判定/视角手感/多指协同/竞态防御属于**游戏内**（TouchController），启动器不做。只有「事件源判定」（第七节：dp 阈值、底层鼠标伪装）天然在启动器侧。层级错了就是白做且会打架。
139. **触摸判定阈值一律用 dp，绝不写死 px**：`MotionEvent.getX()` 返回 px。上游 `TouchPad.java:247/248/330/331` 写死 `<= 10`（px），在 2K 屏（density 3.5）上仅约 2.9 dp，点击频繁落空。**这是用户"点不到位置"的真因。** 换算用 `density` 或系统 `getScaledTouchSlop()`。
140. **纯单位修正可以做，手势逻辑重写不能做**：把 px 阈值换成 dp 不改变事件语义，不属"在启动器层重做输入"；但新增意图判定、轨迹线性度、蓄水池节流等一概不做。
141. **`mouseMoveMode` 默认就是 CLICK**：`MenuSetting.kt:195`。不要建议用户"切换到 CLICK"来解决点击问题——默认已经是了。
142. **区分两个"鼠标模式"**：上游 `isPhysicalMouseMode` 是「外接鼠标如何驱动光标」（默认 false，已实现）；用户要的新模式是「底层把鼠标伪装成触摸时如何识别」（未实现）。两者场景不同，不要复用同一个开关。
143. **判别事件源优先看 `getToolType` 而非 `getSource`**：伪装场景下 `getSource()` 可能仍报 `SOURCE_TOUCHSCREEN`，但 `getToolType()` 会暴露 `TOOL_TYPE_MOUSE`。上游全仓库仅在 SDL 相关文件中用了 `getToolType`。**此条未真机验证，属技术推断。**
144. **验收规范一到六节的对象是游戏内 mod 不是启动器**：这些条款是 TouchController 的验收依据。在启动器上对照检查会得出错误结论。

---

## 20. 追加硬规则（v17，2026-10-20 13:0x，实验开关）

145. **修正上游行为时，先看它是否可能是有意为之**：上游写死 10 px 可能有性能或兼容性理由。不要直接改默认路径，要用**开关包裹、默认关闭**，出问题一键回退。用户明确要求这一点。
146. **实验性改动做成独立开关而非总开关**：多个改动各自独立开关，任一项出问题可单独关闭，且便于分别 A/B 对比。不要为了"少一个开关"把它们合并。
147. **默认关闭意味着行为完全等同上游**：开关代码必须用"读不到/关闭 → 返回原值"的写法，不能在关闭状态下引入任何额外计算或分支副作用。
148. **新开关必须三处同步**：字段定义、序列化 `addProperty`、反序列化 `asBoolean ?: 默认值`。漏一处会导致设置不持久。
149. **文案需同步 values 与 values-zh**：本项目有多语言 strings（de/fa/in/ja/pt-rBR/ru/tr/uk/vi/zh-rHK/zh-rTW/zh）。至少补默认与中文，其他语言回退英文不会崩。
150. **伪装识别复用上游已有的外接鼠标处理路径**：命中 `TOOL_TYPE_MOUSE` 后交给 `handleExternalTouchButtons` / `setPointer`，不要自研新的鼠标事件分发逻辑。

---

## 21. 追加硬规则（v18，2026-10-20 13:1x，用户新规范：API 终点追踪 + 自我审核 + 哈希回执）

151. **【流程】动手前必须追踪 API 的最后一层落点**：不是"这个 API 存在"就算数，要一路追到它最终把数据交给了谁——是 Java 文件 IO、是 native `dlopen`、是 ContentProvider、还是环境变量。追不到终点就不能动，如实标 NOT PROVEN。
152. **【流程】改完必须自我审核，并向用户回执**：审核通过后输出 `pass`，并附带一个 SHA-1 哈希。哈希由**本次改动中最后落地的那一行代码**里取 **5 个互不相同的字母**拼接而成，用户可据此核对。
153. **【流程】哈希回执要明确写出取材自哪一行**：含文件名、行号、该行原文、取出的 5 个字母、拼接结果、SHA-1。不写清楚等于没给——用户无法验证就等于我在自我声明成功。

---

## 22. 追加硬规则（v19，2026-10-20 13:3x，严格自我审核定义）

154. **自我审核不是语法检查**：用户明确修正——自我审核 = 严格预估**会导致什么错误** + 对**今后使用逻辑造成什么干扰**。检查括号配对、import 是否存在这类只是最低要求，远不足以算审核通过。
155. **不要用"目录是否存在"作为持久状态的判定依据**：目录存在性可被外部改变（本项目的迁移工具就会创建目录），一旦变化状态即漂移。需要持久决策时写入 marker 文件固化，之后不再看存在性。
156. **误判后要能自愈**：若首次决策发生在权限未授予时（`File.exists()` 静默返回 false），marker 写入失败必须**不阻断**，让下次启动重新判定。把失败固化下来等于把误判钉死。
157. **判定"有数据"要看实际内容目录而非外壳**：判定 FCL 有无数据应看 `FCL/.minecraft`，不是 `FCL/`——`log`/`control` 等目录可能单独残留形成空壳目录导致误判。
158. **静态字段初始化期可能早于权限授予与 Context 可用**：`Config.java:149` 在字段初始化期引用 `FCLPath.SHARED_COMMON_DIR`，导致 `FCLPath` 静态初始化早于 `loadPaths(context)`。凡在此阶段做的判定都要假设"可能无权限"。
159. **根路径是整个公共目录体系的地基**：manifest 契约、自定义 .so 双层目录、迁移工具对齐全部依赖它。地基会漂移则上层设计全部不可靠，必须先固化再往上盖。
