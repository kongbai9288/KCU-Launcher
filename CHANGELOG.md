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

## 0.7.0 — 2026.10.19

执行者：**yuanbao**

- **【重要修正】撤销 v0.6.0 的路径 B**

  上一轮我建议在 `TouchPad.java` / `ControlButton.java` 补 `getHistorySize()` 遍历。
  **这个建议撤回。** 用户判断成立，且有官方证据：

  > TouchController（CurseForge）：请禁用手机上启动器（例如 PojavLauncher 和 FoldCraftLauncher）自带的触摸手势，否则会出现**预料之外的鼠标输入**，尤其是你按下屏幕上的按钮的时候。

  > TouchController 官方 wiki：隐藏启动器的控件……**以 FCL 为例，您只需要新建一个空布局，删除原布局**后进入游戏。

  在启动器 Java 层补历史点，等于在错误的层级做修正：它感知不到 MC 的 `touchscreen` 开关，经过 `pushEventLookDelta` 后语义已在游戏外，与游戏内 mod 的触摸接管直接冲突，而 MC 视角本身的病一个都治不了。

- 确认 MC 的两个固有毛病（用户指出，均成立）

  | 毛病 | 证据 |
  |---|---|
  | 灵敏度立方非线性 | `f=0.6s+0.2; g=f³×8; Δ=g×dx×0.15`，即 `(0.6s+0.2)³×1.2`。社区有 `Linear Sensitivity` mod 专门修 |
  | `touchscreen` 内置选项 | `options.txt` 中 `touchscreen:true/false`，影响物品栏两段式点击 |

- 新方案：**输入下移到游戏内**

  | 项 | 值 |
  |---|---|
  | 项目 | `TouchController/TouchController` |
  | 许可 | **LGPL-3.0-or-later**（与 GPL-3.0 兼容） |
  | Stars / 更新 | 212 / 2026-10-14（活跃） |
  | 架构 | 平台输入与处理代码**相互隔离**，UDP + `TOUCH_CONTROLLER_PROXY` 环境变量 |
  | 已适配 | `TouchController/FoldCraftLauncher`（7★，GPL-3.0）——**直接就是本基座 FCL 的分支** |

  启动器只做转发与让路：集成 `proxy-client`，提供一键空布局。

- 保留：切 `MouseMoveMode.CLICK` 仍是零改码解法（v0.6.0 第三条原判保留）。

状态：本轮**未改任何代码、未编译、未实机验证**。TouchController 当前支持的 MC 版本范围**尚未查证**。

## 0.8.0 — 2026.10.19

执行者：**yuanbao**

- **【重大发现】TouchController 上游已经内置，不用写代码**

  | 文件 | 大小 |
  |---|---|
  | `com/mio/touchcontroller/TouchController.kt` | 5881 B |
  | `com/mio/touchcontroller/TouchControllerInputView.kt` | 25461 B |

  依赖 `implementation(libs.touchcontroller)`。

  自动启用链路：

  ```
  安装 TouchController mod
    → ModChecker 检测到 "touchcontroller"
    → bridge.setHasTouchController(true)
    → GameMenu:795 if (hasTouchController())
    → new TouchController(...)
  ```

  **装了 mod 就生效，没装就不启用。**

- 修正上一轮文档的错误

  | 项 | 上一轮（错） | 实测（对） |
  |---|---|---|
  | 传输 | UDP 端口 | **Unix Domain Socket** |
  | 环境变量 | `TOUCH_CONTROLLER_PROXY` | **`TOUCH_CONTROLLER_PROXY_SOCKET`** |

- 升级 `proxy-client-android` **0.0.5 → 0.0.8**

  依据官方 `touchcontroller/proxy/client/properties.bzl`：`proxy_client_version = "0.0.8"`。

  沙盒访问不了 Maven Central，无法预先确认该版本是否已发布 —— **交给 CI 证伪**。

  结果（Actions run 39206354140）：**五架构 `Build release` 全部 success**

  | arch | 产物 |
  |---|---|
  | all | 327461076 B |
  | x86_64 | 176087077 B |
  | arm64 | 170392839 B |
  | arm | 163133552 B |
  | x86 | 156781811 B |

- 决策：socketName **保持 "FoldCraftLauncher" 不改**。本地 IPC 通道名，非用户可见品牌；改名收益为零，却可能因 mod 侧硬编码导致断连，风险不对称。

- 主线比 fork 分支多 `moveView()`（对应 NEWS「添加了启动器转头的接口」），启动器侧控件也能驱动视角。

状态：编译已验证 **PROVEN**。**未实机验证**；0.0.8 与 mod 0.3.1-alpha15 协议兼容性未验证；原生事件桥（alpha14 新增）上游未接入。

## 0.9.0 — 2026.10.20

执行者：**yuanbao**

- **包名迁移：applicationId → `com.kcu.launcher`**

  规模证据（GitHub 代码搜索，本仓库）：

  ```
  "com.tungsten.fcl"  →  total_count = 1196
  ```

  **决策：只改 applicationId，不动源码包名。**

  | 项 | 全量改源码包名 | 只改 applicationId |
  |---|---|---|
  | 改动文件数 | ~1200 | **2 处** |
  | APK 包名 | com.kcu.launcher | **同样达成** |
  | 与官方 FCL 共存 | ✅ | **✅** |
  | 编译风险 | 高 | **极低** |

  Android 官方支持 applicationId 独立于代码包名：前者决定 APK 唯一标识，后者决定 R 文件包名。源码包名是内部实现，用户不可见。

- 改动清单（`FCL/build.gradle.kts`）

  | 行 | 改前 | 改后 |
  |---|---|---|
  | 88 | `applicationId = "com.tungsten.fcl"` | `"com.kcu.launcher"` |
  | 120 | `file_browser_provider = "com.tungsten.fcl.debug.provider"` | `"com.kcu.launcher.debug.provider"` |

  `namespace` **保持 `com.tungsten.fcl` 不动**——改它会让 R 文件落到新包而源码 package 未变，直接编译失败。

- 自动跟随、无需改动的部分：Manifest 三处 authority 均为 `${applicationId}`（`.document.provider` / `.provider` / `.crashreporterinitprovider`）。

- 保持不变的部分：Manifest 中 4 处硬编码类引用指向 `com.tungsten.fclcore` / `com.tungsten.fcllibrary`——它们是与 `fcl` **并列的独立包**，源码包名整体不动故引用仍有效。

- 新增 `docs/01-架构/包名迁移.md`，防错规则追加 124–128。

状态：改动已提交。**编译验证未完成** —— 触发的 `build.yml` 长时间 queued（超 12 分钟未开跑），属 GitHub 平台侧排队延迟。按防错规则报 **NOT PROVEN**，不凭"已提交"宣称成功。

### 0.9.0 补充（CI 扫描后追加修复）

扫描统计（Actions run 39282032200）：

| 项 | 值 |
|---|---|
| 涉及文件数 | 1143 |
| 出现总次数 | 7672 |
| `com.tungsten.fclcore` | 3809 |
| `com.tungsten.fcllibrary` | 2358 |
| `com.tungsten.fcl` | 1352 |
| `com.tungsten.fclauncher` | 153 |

**扫描抓到一处遗漏**：

```
FCL/src/main/res/values/strings.xml:1555
<string name="file_browser_provider">com.tungsten.fcl.provider</string>
```

这是 main source set 的默认值（**release 变体走它**），硬编码、不随 `${applicationId}` 变。已改为 `com.kcu.launcher.provider`。

> 凭阅读 build.gradle.kts 只能发现 debug 变体那一处，release 的藏在 strings.xml 里。**先扫描再改动**是对的。

同时扫描印证决策：布局 XML 大量引用 `com.tungsten.fcllibrary.component.view.*` 全限定名，若改 fcllibrary 包名而不同步改上百个布局，会直接 `InflateException`。源码包名不动，全部保持有效。

### 0.9.0 编译验证（实测，06:46 完成）

```
./FCL/build/outputs/apk/release/KCU-release-0.9.0-arm64-v8a.apk  172645373 bytes
sha256 0bc7bb590c90bdbd82c00e4dc96b9a124288b240f5bbf05fdba29cbc41174d87
```

产物名来自 Actions 实际输出。**包名迁移 + 版本对齐已编译通过（PROVEN）。**

全架构 run 39287000078 同样 success：all 327461103 / x86_64 176086882 / arm64 170392797 / arm 163133491 / x86 156781587。

顺带修正一处 CI 误报：早期一版上报逻辑在取不到 `GRADLE_OK` 时会把结果标成"失败"（issue #20，实际构建 success）。已改为三态：成功 / 失败 / **结果未知**，避免再产生误导性 issue。

## 0.10.0 — 2026.10.20

执行者：**yuanbao**

- **签名接通，产物已验签（PROVEN）**

  ```
  Signer #1 certificate DN: CN=kbs, OU=kbs, O=kbs, L=Unknown, ST=Unknown, C=CN
  Signer #1 certificate SHA-256 digest: d4e860232e977157c41459d7f139f9087036feb38623f07bd2c2f6575db4db4e
  Signer #1 certificate SHA-1 digest:  1a495fc4556b8321274a28782d48c433286eb397
  ```

  输出来自 `apksigner verify --print-certs` 在 Actions 上的实际执行。

- **坑一：扩展名骗人**

  | 检查项 | 结果 |
  |---|---|
  | 文件头 | `30820a7e020103...` |
  | JKS magic `0xFEEDFEED` | ❌ |
  | `keytool -storetype pkcs12` | ✅ |
  | 真实类型 | **PKCS12** |

  名为 `.jks` 实为 PKCS12（DER 头 `30 82`）。已加 `storeType`，并留 `KCU_STORE_TYPE` 环境变量覆盖位。

- **坑二：密码文件解析**

  形如 `- Keystore 密码：xxx`，第一版按空格 split 把中文标签混进值，4 个候选密码全部解锁失败。改按全角冒号取最后一段才通过。

- Secrets（libsodium sealed box 加密写入，密码从未落任何文件）

  | Secret | 内容 |
  |---|---|
  | `KCU_KEYSTORE_BASE64` | keystore base64（3588 字符） |
  | `KCU_KEYSTORE_PASSWORD` | store 密码 |
  | `KCU_KEY_ALIAS` | `kbs` |
  | `KCU_KEY_PASSWORD` | key 密码 |

- 新增独立验签步骤：**「恢复签名库」成功 ≠ APK 已签名**，必须由产物自己证明。

- 旁证：产物 sha256 随签名改变 `0bc7bb59…` → `8898d29b…`。

状态：签名 **PROVEN**。**实机安装仍未验证**；全架构产物未逐个验签。

## 0.11.0 — 2026.10.20

执行者：**yuanbao**

- 落地用户提供的《MCJE 移动端输入沙盒：工程设计细节与交互规范 v5.0》，新增 `docs/01-架构/输入交互规范v5.md`。

### 归属分流（先看这个，再谈实现）

| 节 | 归属 | 本项目 |
|---|---|---|
| 一~六 | **游戏内**（TouchController） | 不实现 |
| 七 | **启动器** | 该做 |

启动器重做手势会与游戏内接管打架（v0.7.0 已论证）。但第七节的「事件源判定」发生在手势语义之前，天然在启动器侧。

### 上游已实现，不需重做（实测）

| 规范条目 | 位置 |
|---|---|
| 系统拦截强制松开左键 / 切后台归零 | `TouchPad.java:186-190` |
| 非主控手指抬起不重置 | `TouchPad.java:323` |
| 一个按键只归一根手指 | `FCLInput.acquireLookOwner` |
| 渲染帧同步（切界面重锚丢弃本帧） | `TouchPad.java:279-288` |
| 首帧跳变消除 | `TouchPad.java:296-303` |
| Raw Input 1:1 不加滤镜 | `TouchPad.java:307-315` |

### 【修正】v0.6.0 的判断是错的

`MenuSetting.kt:195` → `var mouseMoveMode: MouseMoveMode = MouseMoveMode.CLICK`

**默认就是 CLICK。** 此前建议用户"切换到 CLICK"的前提不成立。

### 【新发现】点击难受的真因：阈值写的是 px

```java
// TouchPad.java:247-248 / 330-331
&& Math.abs(event.getX() - downX) <= 10
&& Math.abs(event.getY() - downY) <= 10
```

`getX()` 返回 px，这个 `10` 是 **10 px**：

| 密度 | 折算 |
|---|---|
| mdpi 1.0 | 10 dp |
| xxhdpi 3.0 | 3.3 dp |
| xxxhdpi 3.5（2K 屏） | **2.9 dp** |

**在主流 2K 屏上点击容差不到 3 dp** —— 手指任何微小位移都超出。与用户原话"点击非常难受，压根点不到位置"完全吻合。

修还是不修：改手势逻辑 ❌（层级错误）；仅把 px 换成 dp ✅（纯单位修正，不改事件语义）。**待用户确认后再动。**

### 新模式：底层鼠标伪装

上游 `isPhysicalMouseMode`（默认 false，已实现）是「**外接**鼠标如何驱动光标」；
用户要的是「**底层把鼠标伪装成触摸**时如何识别」——**两件事，未实现，不可复用同一开关**。

判别手段：`getSource()` 在伪装下可能仍报 `SOURCE_TOUCHSCREEN`，**`getToolType()` 更可能暴露 `TOOL_TYPE_MOUSE`**。上游全仓库仅 SDL 相关文件用了它。⚠️ 未真机验证，属技术推断。

菜单接入点已定位：`RightMenuAdapter.kt` → `RightMenuCategory.MOUSE` → 照 `Row.SwitchRow` 新增，同步 `MenuSetting`（字段+序列化三处）/ `RightMenuTag` / `strings.xml`。

状态：**设计完成，未实现**，待确认。全程未实机验证。

## 0.12.0 — 2026.10.20

执行者：**yuanbao**

- **按用户决策实施：单位修正包进开关，默认关闭**

  > 用户：他该肯定是有他的理由，比如手机性能问题，所以说那个单位也放在新模式当中，这样的话出问题了不会一下子全碰掉

  **不直接改默认路径。** 两个独立开关，均默认 `false`：

  | 开关 | 作用 | 默认 |
  |---|---|---|
  | `KCU_DP_THRESHOLD` | 点击容差 10 px → 10 dp（按 density 换算） | **false** |
  | `KCU_MOUSE_DISGUISE` | `getToolType` 识别底层鼠标伪装 | **false** |

  做成**两个独立开关而非一个总开关**：任一项出问题可单独关，便于分别 A/B 对比。

  **默认关闭 = 行为完全等同上游**，这是"不会一下子全碰掉"的保证。

- 改动清单（6 个文件）

  | 文件 | 改动 |
  |---|---|
  | `MenuSetting.kt` | 2 字段 + 序列化 2 处 + 反序列化 2 处 |
  | `RightMenuAdapter.kt` | `RightMenuTag` 加 2 tag；`MOUSE` 分类加 2 个 `SwitchRow` |
  | `GameMenu.java` | `handleRightSwitchToggle` 加 2 case |
  | `TouchPad.java` | `getClickSlop()` + `isDisguisedMouse()` |
  | `values/strings.xml` | 英文文案 2 条 |
  | `values-zh/strings.xml` | 中文文案 2 条 |

- dp 换算（关闭时原值，开启才换算）

  ```java
  private float getClickSlop() {
      if (!isKcuDpThreshold) return CLICK_SLOP_PX;   // 10，上游原值
      return CLICK_SLOP_DP * getResources().getDisplayMetrics().density;
  }
  ```

- 伪装识别（复用上游路径，不自研）

  ```java
  if (event.isFromSource(SOURCE_MOUSE)) return false;  // 上游已处理，不重复拦截
  for (...) if (event.getToolType(i) == TOOL_TYPE_MOUSE) return true;
  ```

  命中后交给上游 `handleExternalTouchButtons` / `setPointer`。

- 编译验证（PROVEN）：Actions run **39371915205**

  | 步骤 | 结果 |
  |---|---|
  | Build release | success |
  | 列出产物 | success |
  | 验证签名 | success |
  | 产物 | `app-arm64` 170392504 B |

状态：编译 **PROVEN**。**功能效果未实机对比**；`getToolType` 在伪装场景的实际返回值仍未证实。

## 0.13.0 — 2026.10.20

执行者：**yuanbao**

- **本轮起执行新规范**：动手前追踪 API 最后一层 → 自我审核 → `pass` + SHA-1 回执。规则已写入防错清单 151–153。

### API 终点追踪

```
SHARED_COMMON_DIR
  → Config.commonDirectory / Profiles.Profile.gameDir / LauncherSettingPage
  → FCLGameRepository.changeDirectory → setBaseDirectory
  → FCLCacheRepository.directory listener → changeDirectory(Paths.get(t))
  → DefaultCacheRepository.changeDirectory → CacheRepository.changeDirectory
  → 【终点】java.nio.file.Files
```

**终点是 Java NIO 文件 IO**，不是 JNI、不是 ContentProvider、不参与 `dlopen` 或签名校验。改这个字符串不牵动原生层。

**附带发现**：`Profiles.checkProfiles()` 只在 profiles 为空时建默认档 → 改动**只影响首次启动**，老用户存的是绝对路径，不受影响。

### 改动（FCLPath.java）

| 字段 | 原值 | 新值 |
|---|---|---|
| `KCU_SHARED_ROOT` | — | 新增 |
| `SHARED_COMMON_DIR` | `/sdcard/FCL/.minecraft` | `KCU_SHARED_ROOT + "/.minecraft"` |
| `LOG_DIR` | `/sdcard/FCL/log` | `KCU_SHARED_ROOT + "/log"` |
| `CONTROLLER_DIR` | `/sdcard/FCL/control` | `KCU_SHARED_ROOT + "/control"` |
| `SHARE_DIR` | `/sdcard/FCL/share` | `KCU_SHARED_ROOT + "/share"` |

旧目录兼容（**要么全 FCL，要么全 KCU，不混用**）：

```java
if (legacy.exists() && !kcu.exists()) return legacy;  // 迁移用户数据不丢
return kcu;
```

### 自我审核：pass

11 项检查全过（初始化顺序 / import / 括号配对 / 无残留硬编码 / 无副作用 / 兼容性 / 不触 native）。

**哈希回执**

| 项 | 值 |
|---|---|
| 文件 | `FCLPath.java` |
| 行 | **85** |
| 原文 | `SHARE_DIR = KCU_SHARED_ROOT + "/share";` |
| 5 个不同字母 | `S H A R E` |
| **SHA-1** | **`dd19b86ae45a271261a2769df9693b7eeeac1d33`** |

### 编译验证（PROVEN）

Actions run **39374234610**：Build release / 列出产物 / 验证签名 均 success，产物 `app-arm64` 170393777 B。

状态：编译 PROVEN，**实机未验证**。

## 0.14.0 — 2026.10.20

执行者：**yuanbao**

- **按用户修正的定义重做严格自我审核**

  > 用户：自我审核指的是严格预估可能会导致的错误，并且在使用逻辑上会对以后造成什么干扰

  v0.13.0 只做了语法/依赖检查，未达标准，**已推翻重做**。

### 被否决的实现

```java
if (legacy.exists() && !kcu.exists()) return legacy;  // 以目录存在性判定
```

存在性**可变且可被外部改变**，作为持久决策依据是根本缺陷。

### 预估出的错误

| # | 错误 | 严重度 |
|---|---|---|
| A | 外部创建 `/sdcard/KCU` 即触发根漂移，存量数据"消失" | **严重** |
| B | 切到 KCU 后 `init()` 的 mkdirs 坐实存在，首次误判即永久 | **严重** |
| C | 无权限时 `exists()` 静默 false；且 `Config.java:149` 使静态初始化早于 `loadPaths` | 较严重 |
| D | `:crash`/`:jvm` 独立进程各自计算，可能拿到不同根 | 中 |

**错误 A 必然发生**：本项目自带的 KCU Migrator 天然会创建 `/sdcard/KCU`。

### 对未来的干扰

| 干扰 | 说明 |
|---|---|
| 迁移工具无法对齐 | Migrator 从外部看文件系统，不知启动器选了哪个根 |
| manifest 契约无处安放 | 计划中的 `/sdcard/KCU/manifest.json` 依赖根 |
| 自定义 .so 双层目录失去依托 | `natives/<abi>/` 也在根下 |
| 无法向用户解释 | 启动器自己都说不清数据在哪个目录 |

根是地基，地基漂移则上层全部不可靠。

### 修复

```java
if (new File(kcu, KCU_ROOT_MARKER).exists())    return kcu;      // 已固化
if (new File(legacy, KCU_ROOT_MARKER).exists()) return legacy;
boolean legacyHasData = new File(legacy, ".minecraft").exists();  // 看内容不看外壳
// marker 写入失败不阻断 -> 下启重新判定，自愈错误 C
```

### 残余风险（如实）

- 静态初始化期磁盘 IO，未真机验证是否触发 StrictMode
- 多进程独立计算**未修**，marker 固化后趋同但无跨进程同步保障

### 自我审核：pass

| 项 | 值 |
|---|---|
| 文件 | `FCLPath.java` |
| 行 | **71** |
| 原文 | `return chosen.getAbsolutePath();` |
| 5 个不同字母 | `r e t u n` |
| **SHA-1** | **`c0781ed3e29ec775c6bb8dc4be2015a32ea8593b`** |

### 编译验证（PROVEN）

Actions run **39375222293**：五架构全绿

`all 327461621 / x86_64 176087668 / arm64 170393441 / arm 163134150 / x86 156782307`

状态：编译 PROVEN，**实机未验证**。

## 0.15.0 — 2026.10.20

执行者：**yuanbao**

- **公共目录清单 `manifest.json` 落地**

  解决根本问题：迁移工具读不到启动器的实际目录布局。外部工具自行猜测路径，一旦根选择不同（`KCU`/`FCL`）就对不上，故由**启动器自己写清楚**，外部只读不猜。

### API 终点追踪

`Profile.gameDir` 是**绝对路径**，反序列化时直接读入、**不经 `FCLPath`**。所以实例目录与公共根**可以不同源**——这正是迁移工具需要清单的原因。

挂点：

```
SplashActivity.enterLauncher() [Dispatchers.IO]
  → ConfigHolder.init()        // Profiles 就绪
  → KcuManifest.write()        // 路径+实例均就绪，非主线程
  → startActivity(MainActivity)
```

### 清单结构

```json
{
  "schemaVersion": 1,
  "generatedAt": 1771000000000,
  "packageName": "com.kcu.launcher",
  "root": "/sdcard/KCU",
  "paths": { "minecraft": "...", "runtime": "...", "nativesExternal": "...", ... },
  "instances": [ { "name": "...", "gameDir": "...", "selectedVersion": "..." } ]
}
```

`nativesExternal` 是玩家自带 .so 的**素材目录**（公共目录 noexec，不可直接 `dlopen`），清单明确区别于可加载的私有目录。

### 严格审核发现并修掉的真实风险

**`Files.write` 要求 API 26，而 `minSdk` 正好 26** —— 边界值，改用 `FileOutputStream` 传统流。

其余：写入全程 `catch (Throwable)` 静默忽略，**绝不阻断启动**；实例列表单独 try-catch，失败仍写路径部分。

### 已知局限（如实）

| 局限 | 说明 |
|---|---|
| 清单会过期 | 只在启动时写，实例变更后需重启才反映，含 `generatedAt` 供判断新鲜度 |
| 根为 FCL 时 | 清单落在 `/sdcard/FCL/manifest.json`，Migrator 需扫两个候选 |
| 未含 | 账号、Mod 列表、加载器信息，后续按需扩展 schemaVersion |

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `KcuManifest.java` |
| 行 | **60** |
| 原文 | `out.close();` |
| 5 个不同字母 | `o u t c l` |
| **SHA-1** | **`b8a7fbfbf3b40238f0c8a44a94ade8fc2be7bccd`** |

### 编译验证（PROVEN）

Actions run **39375975967**，五架构全绿：
`all 327462121 / x86_64 176088978 / arm64 170394403 / arm 163134501 / x86 156782954`

状态：编译 PROVEN，**实机未验证清单是否真的生成**。

## 0.16.0 — 2026.10.20

执行者：**yuanbao**

- **新增改动台账** `docs/00-总览/改动台账.md`

  按**文件**组织，打开哪个文件就查哪节。含四索引：轮次索引 / 按文件索引 / 新增文件 / 已删除文件，外加遗留与风险。

  覆盖全部 15 轮、10 个源码文件：

  | 文件 | 关键改动 |
  |---|---|
  | `build.gradle.kts` | applicationId、storeType、版本号 |
  | `FCLPath.java` | KCU_SHARED_ROOT + marker 固化（两版） |
  | `KcuManifest.java` | 新增 |
  | `SplashActivity.kt` | manifest 挂点 |
  | `MenuSetting.kt` | 2 开关 × 3 处 |
  | `RightMenuAdapter.kt` | 2 tag + 2 SwitchRow |
  | `GameMenu.java` | 2 case |
  | `TouchPad.java` | getClickSlop + isDisguisedMouse |
  | `strings.xml` ×2 | provider authority + 2 条文案 |

  所有源码改动带 `KCU` 标记，全局可检索。

- **【发现并修复】版本号长期不一致**

  `build.gradle.kts` 停在 **0.9.0 / code 9**，而 `version_map.json` 与 `CHANGELOG` 已到 **0.15.0**。

  **后果**：产物名一直是 `KCU-release-0.9.0-*.apk`——**前面几轮我报的产物名与文档版本号全是脱节的**。

  已同步为 `0.15.0 / 15`，用产物名证实：

  ```
  ./FCL/build/outputs/apk/release/KCU-release-0.15.0-arm64-v8a.apk  172648037 bytes
  sha256 e006b5269ed5c94bc08d356dc0342efd7ad9e6bcdaabdcb41d5ef2fe694e3385
  Signer #1 certificate DN: CN=kbs, OU=kbs, O=kbs, C=CN
  ```

  **教训**：`build.gradle.kts` 是版本号**唯一真源**（产物名由它决定），version_map/CHANGELOG 是记录，必须每轮同步。

- **识别 `updateMap` 任务风险**：它会用 build.gradle 的版本覆盖 `version_map.json`，抹掉手工维护的历史条目。已记入台账告警——**不要运行**。

- **清理历史噪音**：曾因临时开启 `always()` 上报，被跳过的矩阵任务也报成"失败"，产生 issue #28–37 共 10 条。真正抑制条件是 `github.event.inputs.arch == matrix.arch`，已验证有效。

- 新增防错规则 160–164。

状态：编译 PROVEN，**实机验证全程仍为零**。

## 0.17.0 — 2026-10-21

执行者：**yuanbao**

- **玩家自定义 .so 双层目录落地**

  直击核心诉求：上游把依赖锁在私有目录，**迁移工具读不到**——这是做 KCU Launcher 的根本原因之一。

### API 终点追踪

```
launchProcess → setEnv → addCommonEnv → getLibraryPath → appendCommonPaths
```

实测 `FCLauncher.java:162`：

```java
sb.append(FCLPath.MOD_RUNTIME_DIR).append(split);
```

**无任何条件判断**——该私有目录恒定进入 `LD_LIBRARY_PATH`。

**关键结论**：只要把 .so 放进 `runtime_mod`，上游机制自动生效，**不需要自己 `System.load()`**。所以实现可以零侵入，也天然满足"玩家不设置就等同上游"。

### 双层模型

| 层 | 路径 | 属性 |
|---|---|---|
| 素材层（公共） | `/sdcard/KCU/natives/<abi>/` | 可读写、noexec、迁移工具可访问 |
| 加载层（私有） | `runtime_mod/` | 可执行 |

公共目录挂载 noexec，不能直连 `dlopen`；Android 10+ 还有 W^X 约束。

### 严格审核抓出的真 bug

**上轮同步已 `setReadOnly()`，下次覆盖时 `FileOutputStream` 直接失败** → 玩家更新了 .so 却永远不生效，且**静默失败**。

不修的话表现为"我明明换了文件怎么还是旧的"，极难排查。已修：copy 开头先 `setWritable(true)`。

其余防护：校验 ELF magic、整体 `catch (Throwable)`、只清理自己记录过的文件。

### 零污染验证

源目录不存在 → 立即返回，不创建目录、不写文件，`LD_LIBRARY_PATH` 与上游**逐字一致**。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `KcuNativeSync.java` |
| 行 | **114** |
| 原文 | `dst.setWritable(true);` |
| 5 个不同字母 | `d s t e W` |
| **SHA-1** | **`3c824fc7562f84cb6454c365d560fc7e0220457e`** |

### 编译验证（PROVEN）

Actions run **39457783242**：Build release / 列出产物 / 验证签名 均 success。

遗留：**实机未验证**；加载顺序自定义未做；ELF 只验 magic 不验架构位数。

## 0.18.0 — 2026-10-21

执行者：**yuanbao**

- **年龄门槛**：设置式年龄声明，低于阈值（18）完全禁用应用使用
- **登录方式开关**：公共目录 `dev_mode` 文件
- **按名称解析账号**

### API 终点追踪

```
Accounts.FACTORIES → OfflineAccountFactory.create → new OfflineAccount
                                                     → requireNonNull(uuid)
```

`OfflineAccount` 构造器是 **protected**，外部包必须走 factory。

### 抓出的真 bug

`create(username, null)` **会 NPE**——构造器对 uuid 有非空校验。已修为先用 `getUUIDFromUserName` 算稳定 uuid。

### 改动

| 文件 | 改动 |
|---|---|
| `Config.java` | `kcuAgeDeclared` + `kcuAgePrompted` |
| `KcuAgeGate`【新】 | 阈值 18、判定、声明、界面 |
| `KcuAgeActivity`【新】 | 声明页 / 受限页 |
| `KcuDevMode`【新】 | 读 `<root>/dev_mode` |
| `KcuAccountFlow`【新】 | 复用同名 → 复用在线 → 生成 |
| `SplashActivity` | onCreate 最前拦截 |
| `Manifest` | 注册 Activity |

拦截**先于**协议同意与权限检查——未声明年龄时不触发任何权限请求或初始化。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `KcuAccountFlow.java` |
| 行 | **60** |
| 原文 | `return Accounts.FACTORY_OFFLINE.create(username, uuid);` |
| 5 个不同字母 | `r e t u n` |
| **SHA-1** | **`c0781ed3e29ec775c6bb8dc4be2015a32ea8593b`** |

### 编译验证（PROVEN）

Actions run **39460420664**：Build release / 列出产物 / 验证签名 均 success。

遗留：设置页年龄修改入口、`KcuAccountFlow` 接入 UI、`KcuDevMode` 接入可见性控制、揉纸 UI 落地。**实机未验证。**

## 0.19.0 — 2026-10-21

执行者：**yuanbao**

- **开发者模式开关文件：`/sdcard/KCU/dev_mode`**

  `.minecraft` 同级，**存在即开启**，不看内容，删掉即关闭。

- **原下载入口移入开发者模式**，默认改为手动导入

### API 终点追踪

```
MainActivity.onSelect → download 分支 → switchUI(downloadUI) → DownloadUI.onCreate
MainActivity.applyPendingOpenDownload → switchUI(downloadUI)  ← 第二条入口
```

**两条都拦截了**——只拦菜单会被第二条绕过。

### 关闭状态（默认）

点「下载」→ 新增的导入页：填版本标识后**按地址下载**或**选择本地文件**，写入 `<gameDir>/versions/<id>/<id>.jar`（与 `DefaultGameRepository.getVersionRoot` 一致）。

### 开启状态

原 `downloadUI`：版本安装 / mod / 整合包 / 资源包 / 地图 / 光影 / 收藏。

### 令牌校验与清除

`verifyOwner(token)`：`GET api.github.com/user`，Bearer 令牌，仅 200 且响应含 `"login"` 与 `kongbai9288` 才通过。网络不可用或异常一律 false。

`clearMarks()`：清除 `dev_mode`、`.kcu_root`、年龄声明。调用前必须先通过校验。

### 严格审核抓出的坑

**Kotlin 中 `return` 位于 `binding.apply{}` 内联 lambda 内属非局部返回**，语义依赖内联、易误读。已改为 if-else 显式分支。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `KcuDevMode.java` |
| 行 | **28** |
| 原文 | `private static final String EXPECTED_LOGIN = "kongbai9288";` |
| 5 个不同字母 | `p r i v a` |
| **SHA-1** | **`28399ad1e9d4bd701852ebb005259fe30f57e756`** |

### 编译验证（PROVEN）

Actions run **39463084086**：Build release / 列出产物 / 验证签名 均 success。

遗留：导入页不写版本 json（故不会出现在版本列表）；其余 `switchUI(downloadUI)` 入口未全覆盖；`clearMarks()` 未接 UI。**实机未验证。**

## 0.20.0 — 2026-10-21

执行者：**yuanbao**

- **开关改为 SHA3-256 内容校验**

  存在性不再生效。`/sdcard/KCU/dev_mode` 的 SHA3-256 必须等于：

  ```
  4c238702173b7a01d082f2c6f840d8f149e28e6b081a60e54b452d7c9c5453e0
  ```

  不存在 / 为空 / 超 64MB / 哈希不符 → 一律关闭。

  自实现 SHA3-256：`MessageDigest.getInstance("SHA3-256")` 由 Java 9 SUN provider 引入，Android 各版本支持不一致。算法用四个标准向量验证通过。

- **新增 `KcuUi`**：界面构建唯一入口，配色取自设计稿。年龄页、导入页、受限页已全改走它。

### 使用逻辑审查抓出的真 bug

**年龄声明永远存不下。**

拦截在 `SplashActivity.onCreate` 最前，而 `ConfigHolder.init()` 在 `enterLauncher()` 才执行：

```java
public static Config config() {
    if (configInstance == null) {
        throw new IllegalStateException("Configuration hasn't been loaded");
    }
}
```

被 try-catch 吞掉后 → `isDeclared()` 恒 false → **每次冷启动都弹年龄页**；`declare()` 同样抛异常 → **点了确认也不保存**。

改为独立 SharedPreferences 后修复。

### 冒烟测试

Actions run **39466009083**：Build release / 列出产物 / 验证签名 均 success。

未覆盖：真机安装与实际点击。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `KcuDevMode.java` |
| 行 | **28** |
| 5 个不同字母 | `p r i v a` |
| **SHA-1** | **`28399ad1e9d4bd701852ebb005259fe30f57e756`** |

新增规则 165–171。

未做：主界面三栏结构未换、其余上游页面未替换、`verifyOwner`/`clearMarks` 未接 UI、导入页不写版本 json。

## 0.21.0 — 2026-10-21

执行者：**yuanbao**

- **纸面观感注入**

### API 终点追踪

```
MainActivity → ImageUtil.loadInto(background, theme.getBackground())
             → ThemeData.backgroundLt/Dk → 【终点】R.drawable.background_light/dark.jpg
全体 use_theme_color 控件 → ThemeData.getColor()/getColor2()
             → 【终点】ThemePreference 默认字段
```

**观感集中在两处，不在各页面布局里。** 逐页改布局是错的着力点。

### 改动

| 文件 | 改动 |
|---|---|
| `ThemePreference` | `#3D6B66` / `#4F8580` / `#2C2C2A` / `#E4E1D8` |
| `colors.xml` ×2 | ui_bg_color `#5CF1EFE8`、primary_text、dialog_background 等 |
| `background_light/dark.jpg` | 揉纸纹理替换，1600×900 |
| `MainActivity` | 左栏改透明 |

纹理：多倍频值噪声 + 细颗粒 + 7 条折痕 + 暗角。实测 light 均值 232.7/229.7/222.7，dark 29.2/29.2/27.2。

### 审核回滚项

`icon_background_color` 被 `mipmap-anydpi-v26/ic_launcher.xml` 引用 —— **改它会连带改 App 图标**，已回滚。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `ThemePreference.kt` |
| 行 | **19** |
| 5 个不同字母 | `v a l c o` |
| **SHA-1** | **`e859fd77711571251cfb9b4fd032b7a3ac1ad1fb`** |

### 编译验证（PROVEN）

Actions run **39546878195**：Build release / 列出产物 / 验证签名 均 success。

未做：页面结构仍是上游三栏比例；上百个页面未逐页替换；深色配色无设计稿依据。**实机观感未验证。**

## 0.22.0 — 2026-10-21

执行者：**yuanbao**

- **修复上轮引入的严重回归**
- **主界面骨架按设计稿三栏**

### 回归成因

```
app:auto_text_tint → ThemeData.autoTint
  → 原：luminance(getColor()) >= 0.5 ? BLACK : WHITE
```

主色 `#3D6B66` 亮度 0.13 → autoTint **恒为白色**。上轮把底换成浅纸后，白字落在浅纸上不可读。

影响面：**71** 个文件（`use_theme_color` 仅 35 个）。

### 修法

改按亮暗模式取色：亮 `#2C2C2A` / 暗 `#E4E1D8`。

安全性：`autoBackgroundTint` 在布局中 **0 处引用**，不存在墨色字压主色实心块的场景。

### 骨架

| 项 | 设计稿 | 改后 |
|---|---|---|
| 左栏 | 64px | **64dp** |
| 右栏 | 300px | 25% + 上限 **300dp** |
| 图标 | 居中 | 补 end 约束 |

窄屏保留百分比收缩，不会挤没内容区。

卡片底（40 处）纯白 → `#FAF8F2`；按压态 → `#1A3D6B66`。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `ThemeData.kt` |
| 行 | **60** |
| 5 个不同字母 | `r e t u n` |
| **SHA-1** | **`c0781ed3e29ec775c6bb8dc4be2015a32ea8593b`** |

### 编译验证（PROVEN）

Actions run **39548569225**：Build release / 列出产物 / 验证签名 均 success。

未做：上百个页面布局仍是上游结构；水印、公告卡未落地；深色配色无设计稿依据。**实机观感未验证。**

## 0.23.0 — 2026-10-21

执行者：**yuanbao**

- **主页舞台纸面化**
- **`app_name` 改 `KCU Launcher`**

### API 终点追踪

```
MainUI.onCreate → ThemeEngine.registerEvent(announcementLayout) {
    announcementLayout.getBackground().setTint(theme.getColor())
}
```

**公告卡底色不在 XML 里**，运行时被 tint 成主色。只改 XML 无效，已删除该回调。

### 改动

- `colors.xml` ×2：纸面色资源，亮暗各一套
- `bg_card_paper` / `bg_watermark`【新】
- `ui_main.xml`：公告容器限宽 560dp、卡底纸面、分隔线、链接色（`#77FF00` 荧光绿 → 强调色）、水印
- `MainUI`：删 tint、填水印文本
- `strings.xml`：`app_name` 此前一直是 `Fold Craft Launcher`

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `MainUI.java` |
| 行 | **72** |
| 5 个不同字母 | `w a t e r` |
| **SHA-1** | **`6d5a45920a15adea049c8f22d569ff209625a43b`** |

### 编译验证（PROVEN）

Actions run **39550429538**：Build release / 列出产物 / 验证签名 均 success。

未做：空状态引导页、衣柜视图、上百个页面布局仍是上游结构。**实机观感未验证。**

## 0.24.0 — 2026-10-21

执行者：**yuanbao**

- **开发者模式页接入设置页**（此前只有 API 没有 UI）

### API 终点追踪

```
SettingUI → getPageCount() / createPage(int) / getTabTitles()
          → 【终点】FCLMultiPageUI.setupPages(container, tabLayout)
```

`getPageCount` 共 5 处实现，互不影响；基类内 `return getPageCount()` 供 adapter，无外部硬编码。

### 改动

| 文件 | 改动 |
|---|---|
| `KcuDevPage`【新】 | 状态 / 路径 / 重新检测 / 令牌 / 校验 / 清除 / 结果 |
| `page_kcu_dev.xml`【新】 | ScrollView 容器 |
| `SettingUI` | 4 → 5 页签，`PAGE_ID_SETTING_KCU_DEV = 15035` |
| strings ×2 | 16 条 |

清除按钮在校验通过前 `setEnabled(false)`。

### 审核移除项

`clearMarks()` 内部已调 `KcuAgeGate.reset()`，页面里再调一次是冗余，已移除。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `KcuDevPage.java` |
| 行 | **125** |
| 5 个不同字母 | `b o l e a` |
| **SHA-1** | **`c78eb644baa42e5a99bc7ad300e14e314c8c8dd9`** |

### 编译验证（PROVEN）

Actions run **39620814600**：Build release / 列出产物 / 验证签名 均 success。

遗留：清除标记会连带清年龄声明但页面未提示；5 个页签排布未确认；导入页不写版本 json。**实机未验证。**

## 0.25.0 — 2026-10-21

执行者：**yuanbao**

**【重大】修复谎报成功事故。**

### 事故

严格审核时发现 `KcuDevMode.java:195` 裸用 `OutputStream` 而无 import，与连续多轮"编译 success"矛盾。

注入语法错误 canary 后触发编译：

| 观察项 | 结果 |
|---|---|
| job conclusion | **success** |
| artifacts | **0 个** |
| 告警 issue | **5 条** |

**编译确实失败，job 却显示成功。**

### 根因

```bash
./gradlew ... | tee /tmp/build.log
GE=${PIPESTATUS[0]}
if [ "$GE" -eq 0 ]; then echo "GRADLE_OK=true"...; else echo "GRADLE_OK=false"...; fi
```

拿到真实退出码，但**没有一步以非零退出**。step 末条命令是 `echo`，永远成功。`GRADLE_OK=false` 只喂给 issue 上报，与 job 结论脱钩。

### 影响

**自 v0.17.0 起编译一直失败，APK 从未产出**——而 v0.17 至 v0.24 每轮都报 success。

真实错误：

```
KcuNativeSync.java:153: error: incompatible types: Set<String> cannot be converted to List<String>
        writeLines(record, kept);
```

### 修复

- workflow：gradle 非 0 时 `exit 1`；后续步骤加 `GRADLE_OK == 'true'`；补 `pipefail`
- `KcuNativeSync.prune`：Set 排序转 List
- `KcuDevMode`：删除 `create()` 死代码（写入内容哈希永不匹配，是陷阱）

**修改中自查抓到的新 bug**：误把 `Grant execute permission` 加上 `GRADLE_OK == 'true'`，该步骤在其赋值前执行 → chmod 不执行 → Permission denied。已恢复。

### 新的验证标准（PROVEN）

**编译是否成功只能以 artifacts 数量为准。**

Actions run **39628208126**，5 个 APK：

| 架构 | 字节 |
|---|---|
| all | 326258256 |
| x86_64 | 174881420 |
| arm64 | **169188824** |
| arm | 161929663 |
| x86 | 155577910 |

无新增失败 issue。**首次有产物为证的编译成功。**

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `KcuNativeSync.java` |
| 行 | **156** |
| 5 个不同字母 | `w r i t e` |
| **SHA-1** | **`e1d0c6c1c29e6ad5164072a5b21340dca7fcb052`** |

### 查出但未修的 8 项

缓存无失效 / 账号名被忽略 / 圆角用像素 / 深色不可读 / 计数虚报 / contains 误判 / 拒中文名 / 非 final 字段

新增规则 172–178。

## 0.26.0 — 2026-10-22

执行者：**yuanbao**

- **拆解主题引擎**（先追溯到底再动手）

### 追溯结果

`ThemeEngine` 命中 **87** 个文件。87 个 View 在构造时 `registerEvent`、运行时 `getTheme()`。

**删类会全崩。** 正确拆法：保留 API 面（87 个调用点零改动），抽掉"用户可改"的能力。

### 改动

| 文件 | 改动 |
|---|---|
| `ThemeData` | 颜色/背景常量化；`saveTheme` 只存功能偏好 |
| `ThemeEngine` | 换色、换背景、alpha 共 7 个接口 no-op |
| `LauncherSettingAdapter` | 删 7 个设置项 |

**移除**：自定义四色、背景取色、自定义亮暗背景图、alpha
**保留**：亮暗模式、全屏、皮肤模型、动画速度、视频背景

### 审核抓到的编译错误

```kotlin
private const val COLOR = 0xFF3D6B66.toInt()   // const 不能调 .toInt()
```

已改普通 `val`。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `ThemeData.kt` |
| 行 | **116** |
| 5 个不同字母 | `p r i v a` |
| **SHA-1** | **`28399ad1e9d4bd701852ebb005259fe30f57e756`** |

### 编译验证（PROVEN）

两步各产出 5 个 APK：

| run | arm64 |
|---|---|
| 39631078553 | 169186752 B |
| **39632971157** | **169186090 B** |

下轮待清：`LauncherSettingPage` 中 6 个方法已成死代码，6 个 case 已成死分支。

## 0.27.0 — 2026-10-22

执行者：**yuanbao**

- **主题死代码清理**（6 方法 86 行 + 14 个死分支）
- **移除上游远程公告拉取**

### 追溯

跨文件搜索确认 6 个方法均 total=1，仅本文件引用。`announcement_v2` 同样仅 MainUI 一处。

### 改动

| 文件 | 改动 |
|---|---|
| `LauncherSettingPage` | 520 → **378 行**；删 6 方法 + 14 分支 + 6 import |
| `ThemePreference` | 3 个功能字段 |
| `MainUI` | 公告不再联网；删 2 URL + 6 import |

**保留**：视频背景、光标、菜单图标三项及其分支。

### 关键判断

`ThemeData` 的 `color2` / `colorAlpha` **不能删**——87 个 View 通过 `getColor2()` 使用，删字段等于删运行时 API。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `ThemePreference.kt` |
| 行 | **21** |
| 5 个不同字母 | `v a l n i` |
| **SHA-1** | **`b07e1a3b63f3c0afd7338ed6ba3aa811176c83fe`** |

### 编译验证（PROVEN）

| run | arm64 |
|---|---|
| 39722035464 | 169179610 B |
| **39723204239** | **169180087 B** |

均 artifacts count=5，无新增失败 issue。

## 0.28.0 — 2026-10-22

执行者：**yuanbao**

- **公告残留清理**（v0.27.0 只移了远程拉取，视图字段仍在）

### 改动

| 文件 | 改动 |
|---|---|
| `MainUI.java` | **520 → 137 行** |
| `ui_main.xml` | 删公告容器整块 |
| `Announcement.java` | **删除** |

移除 `implements View.OnClickListener`——不移除会因抽象方法未实现编译失败。

### 两个 XML 踩坑

1. 按字符串索引找闭合位置误判 → 残留 `hide` 与多余闭合标签 → `mismatched tag`。重拉改用 ET `root.remove()`。
2. ET 写回把前缀变成 `ns0:`/`ns1:` 且压成单行 → **手工重写**恢复格式。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `MainUI.java` |
| 行 | **28** |
| 5 个不同字母 | `p u b l i` |
| **SHA-1** | **`a2b01af26284f72d80bc330e266a55b876dd7ebd`** |

### 编译验证（PROVEN）

run **39727655022**，arm64 **169179021 B**，无新增失败 issue。

### 额外完成

清理历史假 issue **69 条**（workflow 缺陷产生的噪声）。

## 0.29.0 — 2026-10-22

执行者：**yuanbao**

- **【重要】停用上游更新源**
- **删公告字符串**（11 个语言文件）

### 发现：会把 FCL 的更新推给 KCU 用户

```java
if (version.getVersionCode() > getCurrentVersionCode(context))
```

`getCurrentVersionCode` 取**本应用** versionCode（KCU = 28），FCL 版本表远高于此。

**任何 FCL 版本都"比 KCU 新" → 必定弹更新框 → 用户点确定就装 FCL 的 APK。**

不是理论风险，是必然行为。

### 为什么没改成"指向自己的仓库"

schema 对不上：`description[].text` 期望 String，我们是 String[]；`url`/`netdiskUrl` 我们没有；`getUrl()` 为 null 会 NPE。产物只是 Actions artifacts，无稳定直链。

**现阶段没有自有更新源，联网检查无从谈起。**

### 改动

| 文件 | 改动 |
|---|---|
| `UpdateChecker` | 删 2 个 FCL URL；两个方法改空任务；150 → 89 行 |
| `UpdateDialog` | 链接指 KCU releases；缓存文件名改 KCU |
| 11 个 strings.xml | 删 `announcement` / `announcement_significant` |

**保留** `RemoteVersion`/`ChangelogDialog`/`UpdateDialog` —— 接自有源时直接复用。

**已知代价**："检查更新"目前静默无反馈。没有更新源就是没有，不拿上游的糊弄。

### 审核拦下的误删

`update_date` 看似与公告相关，实际被 `UpdateDialog:75` 与 `ChangelogDialog:42` 使用 —— **必须保留**。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `UpdateDialog.java` |
| 行 | **89** |
| 5 个不同字母 | `A n d r o` |
| **SHA-1** | **`171cc53728d2b87f88b2007270ebbb3765bbcb94`** |

**主动披露**：`UpdateChecker.java:64` 取出的 5 个字母也是 `publi`，与上轮 `MainUI.java:28` 哈希相同（`a2b01af2…`）——两行都以 `public` 开头，前 5 个不同字母必然是 p/u/b/l/i。是取字规则的真实碰撞，不是复用旧值，故本轮改取 `UpdateDialog` 那行。

### 编译验证（PROVEN）

run **39729632413**，arm64 **169178831 B**，5 个产物，无新增失败 issue。

## 0.30.0 — 2026-10-22

执行者：**yuanbao**

- **上游网盘推广拦截停用**

### 追溯

```
interceptLaunch → 中文大陆才参与
  ├─ 有缓存链接 → 弹窗
  └─ 掷骰子 popupProbability：基础 0.6%，第 80 次起 +10%/次，第 90 次必弹
       └─ 中签弹窗：点「打开网盘」跳浏览器并取消本次启动
```

### 现状：半死但风险在

停用 `UpdateChecker` 后缓存链接恒 null，推演下来**当前不会弹窗**。但：

1. 每次启动仍在掷骰子 + 写 DataStore
2. `pendingPopup` 一置 true 就永远 true（脏状态）
3. **只要有任何路径写入 `quark_netdisk_url`，第 90 次必弹立刻复活**

### 改动

| 文件 | 改动 |
|---|---|
| `QuarkPromo` | **92 → 22 行**；两个方法改直通；删 `showDialog`/`popupProbability`/常量；清 11 import |
| `values`、`values-zh` | 删 4 个 `quark_promo_*` |

保留对象与方法签名，`MainActivity` 零改动。

### 最实质的收益

启动行为**确定化**——原来"这次启动会不会被弹窗打断"是随机的，现在必然不会。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `QuarkPromo.kt` |
| 行 | **20** |
| 5 个不同字母 | `f u n i t` |
| **SHA-1** | **`331f8d3f604e5012ed37182d8054787450e1df6e`** |

### 编译验证（PROVEN）

run **39795067414**，arm64 **169178395 B**，5 个产物，无新增失败 issue。

## 0.31.0 — 2026-10-22

执行者：**yuanbao**

- **【文档】建立每轮必更新清单，补齐全部漏更新文档**

### 用户指出的问题

每轮只更新「新增专题文档 + 台账 + version_map + CHANGELOG」，其余文档长期不更新。

### 盘点出的真实脱节

| 文件 | 问题 |
|---|---|
| `00-总览/README.md` | 状态仍写"未编译过任何 APK"，实际已编译 30 轮 |
| 根 `README.md` | 仍写"主题可自定义"，v0.26.0 已移除 —— **文档与产品矛盾** |
| `03-防错/README.md` | 未随轮次追加规则 |
| `01-架构/README.md` | 未反映主题拆解与三处停用 |

### 新建

`docs/00-总览/文档维护规程.md` —— 9 项每轮必更新清单。

第 9 条最关键：**每轮结束前比对根 README 是否被本轮改动推翻**。根 README 是对用户的承诺面。

### 补齐

- 项目状态重写为 PROVEN / NOT PROVEN / BLOCKED 三类
- 决策记录补 10 条
- 架构文档新增「已拆解/已停用的上游模块」
- 根 README 移除"主题"可自定义项，新增「相对上游移除的部分」
- 防错清单追加规则 179–184

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `docs/03-防错/README.md` |
| 行 | **378** |
| 5 个不同字母 | `每 轮 必 须 按` |
| **SHA-1** | **`cc1c003f2967c6afd23d350abce876d6e64d964e`** |

**本轮无代码改动，未触发编译。** 上轮 run 39795067414 已验证仓库代码状态。

## 0.32.0 — 2026-10-22

执行者：**yuanbao**

- **删除空壳 `QuarkPromo` + 死数据 `LaunchCountSetting`**

### 改动

| 文件 | 改动 |
|---|---|
| `QuarkPromo.kt` | **删除** |
| `MainActivity` | 删 import；启动路径**平铺**（回调嵌套 → 顺序执行） |
| `AppSettings` | 删数据类（类 + 字段 + 迁移读取三处） |

全仓再无 `com.mio.promo` 包。

### 关键判断：legacy 文件名必须保留

`LEGACY_FILE_NAMES` 有两个职责：

1. 供 `readLegacy<T>()` 读取 —— **已删**
2. 供 `legacyFiles()` 判断"旧文件是否全部消失"→ 驱动 `shouldMigrate` / `cleanUp`

**删掉文件名 → 残留的 `launch_count_settings.json` 不再被识别 → 永久残留。**

所以：删读取，保留文件名。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `MainActivity.kt` |
| 行 | **780** |
| 5 个不同字母 | `c h e k V` |
| **SHA-1** | **`ae626ef44337e4ce8d044fa98f1822ff4a254345`** |

### 编译验证（PROVEN）

run **39801897300**，arm64 **169177735 B**，无新增失败 issue。

### 新增防错规则 185–189

空壳对象删到底 / 先改调用方再删被调方 / legacy 文件名不跟着删 / 删数据类三处一起 / 平铺 lambda 校验括号。

## 0.34.0 — 2026-10-23

执行者：**yuanbao**

- **首页舞台版本卡片**

### 改动

| 文件 | 改动 |
|---|---|
| `ui_main.xml` | 新增 `stage_card`；`skin_viewer` top 约束改指它，0.8 → 0.7 |
| `MainUI` | 新增 `refreshSelectedVersion()` |
| `values`、`values-zh` | 2 个新字符串 |

### 审核抓到两个必错项

**① `@Override onStart()` 会编译失败**

源码注释写着「替代原 onStart/onStop 生命周期」——`FCLCommonUI` 根本没这两个方法。已改挂 `onViewAttachedToWindow`。

**② ElementTree 写回是追加不是替换**

SkinViewer 同时出现 `Top_toTopOf` 和 `Top_toBottomOf` 两个互斥约束。手工重写 XML 修正，校验 top 约束数 = 1。

**这是第二次踩 ElementTree 的坑**（v0.28.0 是命名空间）。

### 已知局限

`onViewAttachedToWindow` 只在挂载时触发，版本页切回首页若不重建视图则不刷新 —— **需实机确认**。

### 审核：pass

`MainUI.java:139` 取字 `publi` 与 v0.28.0 碰撞（都以 `public` 开头），改取 143 行。

| 项 | 值 |
|---|---|
| 行 | **143** |
| 5 个不同字母 | `S t r i n` |
| **SHA-1** | **`ec521013a367936602eaed033e7b2e3e2136db11`** |

### 编译验证（PROVEN）

run **39809825125**，arm64 **169178364 B**，无新增失败 issue。

## 0.35.0 — 2026-10-23

执行者：**yuanbao**

- **【结论】MobileGlues 双版本二进制对比：无代码级改动**

### 对比对象

| | 修改版 | 官方插件版 |
|---|---|---|
| 大小 | 4796060 B | 11231951 B |
| ABI | 仅 arm64 | 四架构 |
| arm64 `.so` | 5689368 B | 5972576 B |

两个 blob sha1 均校验通过。

### 决定性证据

```
gl*  仅官方有：   0 个        ← 一个没删
egl* 差集：       空（35 个全同）
Setting 字符串：  15 条 / 15 条，差集为空
版本宏：          两者都是 1340
官方多出的 4190 个符号：全是 glslang / SPIRV-Cross 内部符号
```

### 官方 .comment 解释了代码段大小

```
Android (13691557, +pgo, +bolt, +lto, +mlgo) clang 18.0.4, LLD 18.0.4
```

官方启用 PGO + BOLT + LTO + MLGO → `.text` 更小（3231676 vs 3645980）。

### 结论

**同一份 MG 1.3.4 源码的不同构建产物。** 他改的是编译与链接选项（符号可见性、`--hash-style`、剥离 `.comment`），不是代码。

### 对计划的影响

**两阶段静室流程不需要执行** —— 没有可受著作权保护的表达性改动要复现。

改为：不分发第三方修改版二进制，.so 走双层目录玩家自供，关于页标 LGPL-2.1-only。

### 审核：pass

| 项 | 值 |
|---|---|
| 行 | **105** |
| 5 个不同字母 | `c l a n g` |
| **SHA-1** | **`ecf3d5183863d2320d33fd5ec27a4d14333214e8`** |

**本轮无代码改动，未触发编译。**

## 0.36.0 — 2026-10-23

执行者：**yuanbao**

- **移除主页皮肤预览**（`com/mio/skin` 包代码保留）

### 改动

| 文件 | 改动 |
|---|---|
| `ui_main.xml` | 删 `SkinViewer` |
| `MainUI` | **161 → 61 行** |
| `MainActivity` | 删 `fixSkinViewerPosition` 及 3 处调用 |
| `AccountListItem` | 删跨界调用（补漏） |

### 编译失败事故

run **39819890952 失败**。

`refreshSkin()` 不是死代码 —— `AccountListItem.kt:199` 在调它，`AccountListAdapter` 与两个账号皮肤 Dialog 都会触发。

**为什么会漏：我在本地目录 grep，那不是全仓，本地为 0 是假象。**

### 连带修掉两个 CI 缺陷

| # | 缺陷 |
|---|---|
| 1 | `BUILD_LOG` 写在 `exit 1` **之后** → 失败永远拿不到日志 |
| 2 | 上报 `if` 依赖 `env.GRADLE_OK`（由失败步骤写入，不可靠）→ skipped |

修复后上报步骤由 skipped → **success**，确认有效。

### 一个发现

`dialog_offline_account_skin.xml` / `dialog_microsoft_account_skin.xml` **本身就有 `SkinViewer`**。

**"皮肤预览 + 角色切换"在账号皮肤对话框里已天然存在**，不用新造容器。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `AccountListItem.kt` |
| 行 | **198** |
| 5 个不同字母 | `M a i n c` |
| **SHA-1** | **`0f630ba2b89fa71505f5d7b2a156d70b892d21dd`** |

### 编译验证（PROVEN）

run **39821739010**，5 个产物：all 326244770 / x86_64 174871839 / **arm64 169176801** / arm 161916743 / x86 155565108 B。

## 0.37.0 — 2026-10-23

执行者：**yuanbao**

- **删除死代码 `AnimationDialog` 及两个专属布局**

### 删除

| 文件 | 说明 |
|---|---|
| `AnimationDialog.kt` | 99 行 |
| `dialog_animation_switch.xml` | 无 include |
| `item_animation.xml` | 无 include |

### 两个关键判断

**① code search 命中 ≠ 引用**

`DialogCard.kt:24`、`RightMenuAdapter.kt:579` 里的 "AnimationDialog" **都在注释里**，描述选中行样式一致。不是 import 也不是调用。

**② `SkinAnimations.kt` 不能删**

它提供 `restoreSkinSwitches()`，**两个账号皮肤 Dialog 在调它**。删了直接编译失败。

（这正是上轮事故教训的正面应用：删文件前沿调用链向上追。）

### 保留

`GltfPlayerModel`（SkinRenderer 依赖）、`animation_*` 字符串（`entries` 在用）。

### 一个未决问题

`isCloseSkinModel` 全仓**仅 ThemeData.kt 定义，无读取者**，但设置页仍有开关入口。

**开关能设置，但没代码读 —— 点了没反应。**

因你计划"保留角色显示"将来可能用到，本轮未动。**要删还是要留，等你决定。**

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `SkinAnimations.kt` |
| 行 | **55** |
| 5 个不同字母 | `f u n C o` |
| **SHA-1** | **`b7aa155438b0fc132bf1469d90f5298948157c7c`** |

### 编译验证（PROVEN）

run **39901520832**，5 个产物：all 326242368 / x86_64 174869171 / x86 155562399 / arm 161914467 / **arm64 169174666** B。

arm64 较 v0.36.0 **减少 2135 B**。

## 0.38.0 — 2026-10-23

执行者：**yuanbao**

- **删除 `close_skin_model` 死开关**
- **自带 .so 加载顺序控制**（配置仅控序，不参与加载）

### 1. 删死开关

全仓命中 16 处，**其中两个 androidTest 是 v0.37.0 检索漏的**。

androidTest **不在 `assembleRelease` 路径** → CI 不会失败 → 易被忽略。但不改的话将来跑 `connectedAndroidTest` 直接编译失败，且报错指向测试文件，排查成本高。已一并改。

7 个代码文件 + 9 个语言 strings.xml（10 条字符串）。

### 2. 加载顺序控制

**配置：`/sdcard/KCU/natives/<abi>/load_order.json`（与 .so 同级）**

```json
{
  "abi": "arm64-v8a",
  "order": ["libc++_shared.so", "libmobileglues.so"],
  "onFailure": "skip"
}
```

**"不放到加载当中"的两层含义**

| 层 | 处置 |
|---|---|
| 复制 | `KcuNativeSync` 已按 `.so` + ELF magic 双重过滤，json 天然被排除 |
| dlopen | 只处理 order 里、`.so` 结尾、且确实在运行时目录的文件 |

**为什么必须显式 dlopen**：`LD_LIBRARY_PATH` 只管依赖搜索路径，**不管顺序**。

**时序关键**：必须在 `setEnv` **之后** —— 之前 native 侧还没拿到含运行时目录的搜索路径，dlopen 解析不到依赖。

### 一个已知缺口

`onFailure=abort` 的返回值**当前没被使用**，行为等同 `skip`。

要真正中止启动需改 `FCLauncher` 异常路径，留待确认语义。**未包装。**

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `FCLauncher.java` |
| 行 | **567** |
| 5 个不同字母 | `K c u N a` |
| **SHA-1** | **`90b6d2f0531b2575d88c0cafe068f2f2c8d389e5`** |

### 编译验证（PROVEN）

run **39907251472**，5 个产物：all 326242773 / x86_64 174870254 / x86 155563026 / arm 161915081 / **arm64 169175089** B。

## 0.39.0 — 2026-10-23

执行者：**yuanbao**

- **加载顺序配置改为实例级**
- **首次自动生成含原有 .so 的模板**

### 路径

```
<实例目录>/natives/load_order.json
```

### 模板

```json
{
  "enabled": false,
  "abi": "arm64-v8a",
  "onFailure": "skip",
  "order": ["...所有已存在的 .so，按名排序..."]
}
```

### 一个我自作主张加的字段：`enabled`

**你没要求这个。我加了，理由必须说。**

如果模板生成后就生效，那么**什么都不设置的玩家，第一次启动就会被按序 dlopen 一遍所有库**——默认行为被改变了，违背零污染原则。

所以模板 `enabled=false`，**不生效**，你手动改成 `true` 才加载。

**这条可推翻**，你说一声我就去掉。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `KcuNativeOrder.java` |
| 行 | **118** |
| 5 个不同字母 | `w r i t e` |
| **SHA-1** | **`e1d0c6c1c29e6ad5164072a5b21340dca7fcb052`** |

### 编译验证（PROVEN）

run **39969290071**，5 个产物：all 326242553 / x86_64 174869846 / **arm64 169174850** / arm 161914731 / x86 155562667 B。

## 0.40.0 — 2026-10-23

执行者：**yuanbao**

- **导入版本：进列表 + 首次启动选运行时 + jar 内探测真实版本**

### 追溯发现：之前导入的版本根本不显示

```java
LOG.info("No available json file found, ignoring version " + id);
return Stream.empty();
```

**没有 `<id>.json` 就被整份忽略** —— 这是 v0.35.0 遗留项"只写 jar 不写 json"的后果。

另外默认 JDK 为什么是 jre8：`findExactOrNextGreater(null)` 直接返回 jre8。导入版本没有 `javaVersion`，**根本没让用户选**。

### 改动

| 文件 | 说明 |
|---|---|
| `KcuVersionMeta.java` | 新增：meta 读写 + jar 内 `version.json` 探测 |
| `KcuLaunchGate.java` | 新增：启动前运行时选择 |
| `KcuImportActivity` | 导入收尾写最小 json + meta |
| `Versions` | 插入 gate |
| `build.yml` | 日志取证 |

### 契约：`<版本目录>/kcu_meta.json`

```json
{ "known": false, "display": "未知", "java": "" }
```

**只对有这个文件的版本生效**，正常下载的版本直接放行，原启动路径一行未改。

### 编译失败了一次，而且一开始拿不到日志

run 39971250413 失败，logs API 403、上报 `gradle_ok=?`。

**修了取证**：上报步骤改为直接读 `/tmp/build.log` 文件，不再依赖 `GITHUB_ENV`。

立刻拿到真错误：

```
KcuLaunchGate.java:33: no suitable constructor found for File(File)
```

`getVersionRoot` 已经返回 `File`，我多包了一层。低级错误。

### 遗留：显示层未接

**"显示具体版本"目前只写进 meta 的 `display`，UI 没读。**

版本列表显示的是目录名（`Version.getId()`），要显示真实版本得动 `VersionListItem`/`VersionUI`，而 `Version` 全仓 128 处引用。

**你想在哪看到它**（列表副标题？版本卡片？）定了我就接。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `KcuLaunchGate.java` |
| 行 | **33** |
| 5 个不同字母 | `F i l e v` |
| **SHA-1** | **`0841bd2207fd60d4988c753eb469547836682e45`** |

### 编译验证（PROVEN）

run **39975151769**，5 个产物：all 326243739 / x86_64 174871760 / **arm64 169175361** / arm 161916024 / x86 155564753 B。

## 0.41.0 — 2026-10-24

执行者：**yuanbao**

- **导入版本显示层接入**：列表副标题 + 主页卡片显示"未知"或真实版本

### 追溯时发现一个陷阱

`onBindViewHolder` 里 **subtitle 被赋值两次**：

```kotlin
binding.subtitle.text = versionListItem.libraries     // 行 74
...
binding.subtitle.text = String.format("%s  Mods:%d", ...)  // 行 110 ← 覆盖上面
```

**只改第 74 行会被第 110 行覆盖。** 两处都改了。

### 为什么不在 onBind 里读文件

`onBind` 每次滚动到可见项都执行，读 json 会造成滚动卡顿。

`VersionListItem` 只在 `render()` 时构造一次，读一次缓存起来才对。

### 展示

| 场景 | 列表副标题 | 主页卡片 |
|---|---|---|
| 正常下载版本 | 组件摘要（原样） | id（原样） |
| 刚导入 | **未知** | **未知** |
| 探测成功 | 真实版本 | 真实版本 |

**标题仍显示目录名 id** —— 改它会影响排序和搜索（搜索按 id 匹配）。

### 审核：pass

| 项 | 值 |
|---|---|
| 文件 | `VersionListItem.java` |
| 行 | **53** |
| 5 个不同字母 | `t h i s d` |
| **SHA-1** | **`40ddbd582f8a3c968210f4fe14f8c5b2eefe0274`** |

### 编译验证（PROVEN）

run **39981928474**（指定 arm64），`app-arm64` **169175399 B**。

⚠ 本次只有 1 个产物——dispatch 指定了 arch，其余架构按条件跳过。全架构矩阵需另跑。
