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
