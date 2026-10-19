# 更新日志

每次版本更新都会写入：版本号、日期、本次变更、执行者。

## 0.1.0 — 2026.10.19

执行者：**yuanbao**

- 建立公开仓库 `kongbai9288/KCU-Launcher`，GPL-3.0，Android .gitignore
- 落地分类文档目录

  | 目录 | 内容 |
  |---|---|
  | `docs/00-总览` | 索引、项目状态、决策记录 |
  | `docs/01-架构` | 分层、双层目录模型、模块复用 |
  | `docs/02-契约` | `native_load_order.json`、`manifest.json` |
  | `docs/03-防错` | 每轮必读的错误清单 |
  | `docs/04-上游分析` | 上游实测、fork 采纳清单、外部 API |
  | `docs/05-合规` | 法律与安全红线 |
  | `docs/06-构建与CI` | 构建参数、密钥、ABI 策略 |
  | `docs/07-兜底` | 失败分级与降级 |

- 图标：从 `KCU-Migrator` 取 `Kcu_icon.png`，blob sha `5631d47`，两仓库共用同一图标
- 确定 .so 双层目录模型（公共目录存素材 / 私有目录按序加载）
- 确定 `native_load_order.json` 契约；玩家不配置时代码路径与上游逐字一致
- 确定 CurseForge API Key 不写死：缺省构建照常，运行时自动走国内镜像
- 确定加载器图标统一走单一 `LoaderIcon` 模块，商店/版本列表/实例详情共用
- 写入失败分级兜底（L0–L3）与安全模式
- 确认离线登录沿用上游 `fclcore/auth/offline` 实现，不自研

状态：**尚未编写业务代码，尚未编译 APK，尚未实机验证。** 以上文档中所有技术结论在实机验证前均为 NOT PROVEN。

## 0.2.0 — 2026.10.19

执行者：**yuanbao**

- 新增 CI 工作流

  | 文件 | 作用 |
  |---|---|
  | `.github/workflows/build.yml` | 五架构矩阵（all/arm/arm64/x86/x86_64），密钥全走 secrets，产物 `KCU-<type>-<version>-<abi>.apk`；无源码基线时跳过并告警而非失败 |
  | `.github/workflows/sync-upstream.yml` | `workflow_dispatch` 一键导入 FCL 上游基线，保留 `docs/` / `version_map.json` / `CHANGELOG.md` / `.github/` |

- 新增 `docs/08-Android版本适配/README.md`

  - **16 KB 页大小**（最高优先级）：Android 15 起 AOSP 支持 16 KB 页；Google Play 自 2025-11-01 要求 target ≥ Android 15 的应用必须支持。本项目有大量 .so，受影响。需重编 + 去掉硬编码 4096 + 校验 ELF 段对齐
  - **MANAGE_EXTERNAL_STORAGE**：SAF 在 Android 11+ 拿不到根目录 / Download / Android/data / Android/obb，而本项目公共目录在根目录 → 该权限是唯一路径；未授予时回退应用专属目录
  - 前台服务类型（API 34+ 强制）
  - Android 14 动态代码加载：动态加载文件必须只读
  - MediaProjection：Android 14 起**每次捕获会话都要重新授权**，`MediaProjection` 实例只能 `createVirtualDisplay` 一次
  - Android 14 Zip 路径遍历抛 `ZipException`

- 新增 `docs/04-上游分析/上游API地图.md`（核心交付）

  - FCL 启动全链路 6 阶段
  - `setUpJavaRuntime`：dlopen 顺序固定（libjli → libjvm → …），再递归 `locateLibs` dlopen JRE 目录全部 .so
  - `setupGraphicAndSoundEngine`：插件渲染器按 `DLOPEN` 字段逐个加载
  - `launch()` 拼 `LD_LIBRARY_PATH` 的 10 段顺序，`rebaseArgs` 替换 `${natives_directory}` / `${gl_lib_name}` / `${egl_lib_name}`
  - **玩家自定义 .so 的正确接入点 = `MOD_RUNTIME_DIR` + `NativeLibPlugin.getPaths` + `LD_LIBRARY_PATH`**，复用上游机制，不自己 `System.load`，天然满足默认路径零污染
  - 12 个 JNI 库职责表、6 个内置渲染器 ID、8 个键码类、任务系统线程模型
  - 上游"非显然行为"4 条（不要当 bug 修）与"已修复的坑"10 条（不要重新引入）

- 官方文档核对补充：Minecraft `version.json` 的 `rules` / `${var}` / `assetIndex` 规范；CurseForge `gameId=432`、`x-api-key`

状态：**仍未编写业务代码，仍未编译 APK，仍未实机验证。** 文档中技术结论在实机验证前均为 NOT PROVEN。

## 0.3.0 — 2026.10.19

执行者：**yuanbao**

- **导入 FCL 上游基线**（`sync-upstream` 工作流）

  | 项 | 值 |
  |---|---|
  | 上游提交 | `cd14d179ce22a806c9eda6353836e5abfa93cf30` |
  | 落地模块 | `FCL`、`LWJGL`、`Terracotta`、`ZipFileSystem` |
  | 保留自有 | `docs/` 全部、`README.md`、`CHANGELOG.md`、`version_map.json`、`.github/` |
  | 已排除 | `key-store.jks`、`debug-key.jks`、`private_key.pepk` |

  新增 `UPSTREAM.txt` 记录上游来源与同步时间。

- 踩坑记录：Actions 内 `git push` 默认无凭据 → 改用 `x-access-token@github.com`；上游历史与本地纠缠导致 rebase 冲突 → 改用**孤立分支单次提交 + 强制推送**，得到干净基线。

- 改造 `FCL/build.gradle.kts`

  - 签名：密钥走 `KCU_KEYSTORE_PASSWORD` / `KCU_KEY_ALIAS` / `KCU_KEY_PASSWORD` 环境变量；**签名库缺失时回退 AGP 默认 debug 签名，构建不失败**（规则 66）
  - CurseForge / OAuth Key：空值写**空串**而非字符串 `"null"`，运行时据此切换国内镜像（规则 67）
  - 产物命名 `KCU-<buildType>-<version>-<abi>.apk`
  - `versionCode=3` / `versionName=0.3.0`

状态：基线已落地但**尚未编译验证**。`applicationId` 仍为 `com.tungsten.fcl`，改包名需连同源码目录与 Manifest 一起迁移，列为下一步。

## 0.4.0 — 2026.10.19

执行者：**yuanbao**

- **首次编译成功**（关键里程碑）

  `./gradlew assembleRelease` 在 GitHub Actions 上通过，产出真实 APK：

  | arch | 产物体积 |
  |---|---|
  | all | ~327 MB |
  | arm64 | ~170 MB |
  | arm | ~163 MB |
  | x86_64 | ~176 MB |
  | x86 | ~157 MB |

  五个架构体积各异，说明 `splits.abi` 与 `filterJreAssets` 确实生效，不是同一份产物复制五次。

- CI 修复清单

  | 问题 | 修法 |
  |---|---|
  | `secrets.*` 不能用在 step 级 `if` | 提到 workflow 顶层 `env:` 求值 |
  | 两个 step 用了同一个 id `gradle` | 第二个改 `gradle_debug` |
  | `keyAlias = keyAlias` 自赋值 | 变量改名 `kcuKeyAlias` |
  | 手动指定 arch 时 5 个矩阵任务都构建同一个 arch | 改步骤级条件过滤 |
  | **`continue-on-error` 把构建失败掩盖成任务成功** | **移除**，失败就是失败 |

- 新增"构建结果上报（始终）"步骤：无论成败都开 issue，附产物清单与 sha256，便于在无日志下载权限时核实。

- 仍未做：**包名迁移**（当前 `applicationId` 仍是 `com.tungsten.fcl`）、公共目录改造、自定义 .so 接入、实机安装验证。

### 0.4.0 编译证据（实测，非推断）

```
./FCL/build/outputs/apk/release/KCU-release-0.3.0-arm64-v8a.apk  172644393 bytes
sha256 930657a44c9e1dbcbd4e5b203f567bcc11b1556efee6fd2e0d381e2fe8d88a75
```

产物名来自 Actions 实际输出，非配置文件推断。命名与 `KCU-<buildType>-<version>-<abi>.apk` 约定一致。
首版成功 APK 打的是 0.3.0（当时的 versionCode=3），此后对齐为 0.4.0 / versionCode=4。

## 0.5.0 — 2026.10.19

执行者：**yuanbao**

- 新增 `docs/01-架构/上滑多动作.md`

  读源码实测的结论：**上游 `ControlButton` 已经实现了滑动链，不需要第三方库，也不需要改代码**。

  | 机制 | 位置 |
  |---|---|
  | 4 个独立事件槽 | `ButtonEventData`：press / longPress(400ms) / click / doubleClick |
  | 滑动链移动 | `ControlButton.handleSwipeMove` |
  | 目标查找 | `ViewManager.findSwipableButtonAt`（坐标命中 + VISIBLE + isSwipable） |
  | 进入/退出 | `handleSwipeEnter` / `handleSwipeExit` |
  | 与点击分离 | `swipeEngaged` 为真时抬起不触发单击/双击 |

  方案：把联动按钮放在主按钮上方并紧贴（可部分重叠），上滑即自动切换按住的动作。**零 Java 改动。**

  明确限制：上游是坐标命中不是方向映射，必须滑到联动按钮上才触发；滑出所有按钮区域不触发。

- 新增 `docs/01-架构/本地化图标与关于页.md`：图标全部本地不联网；开源许可与开发者页每项带图标，收录上游项目（FCL / Pojav / HMCL / Android-OpenJDK-Build / Terracotta / SDL3 / LWJGL）。

- 确定差异化原则：**差异全部落在数据与资源层**（布局 JSON、样式配置、事件组合、本地图标），Java/Kotlin 一行不动。

状态：本轮**未编写业务代码、未改 Java、未编译**。文档结论基于已落地源码的实测阅读。

## 0.6.0 — 2026.10.19

执行者：**yuanbao**

- 新增 `docs/01-架构/输入问题诊断.md`

  三个触摸问题的源码级定位：

  | 问题 | 根因 | 位置 |
  |---|---|---|
  | 滑动过程检测不到 | ACTION_MOVE 未遍历 `getHistorySize()`，中间轨迹丢弃 | `TouchPad.java` / `ControlButton.java` |
  | 点击难受 | 单击判定 100ms / 10px 对触摸过严 | `TouchPad.java` 两处 `ACTION_UP` |
  | 点不到位置 | `MouseMoveMode.SLIDE` 下手指位置 ≠ 光标位置 | `MouseMoveMode.java` |

- **换别人的代码能解决吗：不能。** 检索 5 个仓库的 `getHistorySize`：

  | 项目 | 命中 |
  |---|---|
  | FoldCraftLauncher | 1（仅外接鼠标/指针捕获路径）|
  | ZalithLauncher2 | **0** |
  | PojavLauncher | **0** |
  | Amethyst | **0** |
  | MojoLauncher | 1（仅指针捕获路径）|

  所有项目的触摸板路径都没处理历史点批处理。换任何一家，根因一原样保留。

- 三条路径评估：路径 A 零改码调设置（切 CLICK 模式）→ 路径 B 最小补丁约 30-50 行 → 路径 C 整体替换输入层（**不建议**，需重新对齐 native `input_bridge_v3.c` 协议，风险高且核心问题仍在）。

状态：本轮**未改任何代码、未编译、未实机验证**。结论源于源码阅读与官方文档对照。
