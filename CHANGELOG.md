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
