# 上游分析

数据来源：GitHub API 原始返回（`/repos`、`/git/trees`、`/contents`、`/compare`、`/commits`、`/forks`、readme）。

## 1. 上游事实

| 项目 | 许可 | Stars | 状态 |
|---|---|---|---|
| FCL-Team/FoldCraftLauncher | GPL-3.0 | 4970 | 活跃，1.3.4.0（2026-10-15） |
| ZalithLauncher/ZalithLauncher2 | GPL-3.0 | 2046 | 活跃，2.7.1（2026-10-18） |
| MojoLauncher/MojoLauncher | LGPL-3.0 | 1040 | 活跃 |
| PojavLauncherTeam/PojavLauncher | LGPL-3.0 | 9438 | 已归档（2025-09-23） |
| FCL-Team/Android-OpenJDK-Build | — | 18 | JRE 8/11/17/21/25，四架构（25 无 x86） |

FCL 模块：`FCL`、`Terracotta`、`ZipFileSystem`、`LWJGL`。

FCL 已有、不需要自研：
- 终端：`ShellUtil.java` + `ShellActivity.java`（真实 `sh` 进程）
- 账号：`fclcore/auth/` 下 microsoft / offline / yggdrasil / authlibinjector 四套
- 下载源：`MojangDownloadProvider`、`BMCLAPIDownloadProvider`、`AutoDownloadProvider`
- 加载器：fabric / legacyfabric / forge / neoforge / liteloader / cleanroom / quilt
- 联机：Terracotta 模块 + VPNService

## 2. Fork 采纳清单

| 编号 | 项 | 来源 fork | 优先级 |
|---|---|---|---|
| A1 | app_config 自定义契约 | root-S7（52★） | P0 |
| A2 | 改为内置默认 + 外部覆盖双来源 | root-S7 + 本项目 | P0 |
| A3 | 终端复用 ShellUtil，提为一级入口 | FCL 官方 | P0 |
| A4 | 公共目录策略集中改 FCLPath / PathManager | FCL / ZL2 | P0 |
| A14 | x86/x86_64 natives substitution 修正 | Copper（21★） | P0（全 ABI 后） |
| A5 | 披风、Chroma 名字、主屏快捷方式 | ZL2Plus（146★） | P1 |
| A6 | 设置/账号/皮肤整体导入导出 | ZL2Plus | P1 |
| A7 | 内置文件管理器、Ely.by 登录 | Zeryth（27★） | P1 |
| A9 | 内置 MobileGlues + Kopper 渲染器 | Zeryth / Copper | P1 |
| A11 | 日志工具栏（清除/自动滚动/滚到底） | maulana-id（32★） | P1 |
| A12 | 版本级渲染器环境变量独立文件 | ZaliaBetter（7★） | P1 |
| A13 | 断点续传（partfile + NIO hash） | Copper | P1 |
| A16 | 陶瓦联机回退 HTTP/1.1 | ZL2Plus | P1 |
| A17 | MC 26.2+ 小窗崩溃、Vulkan 回落 OpenGL | maulana-id | P1 |
| B1-B3 | MediaProjection + ML Kit OCR + 优化 | overlay-translator（1049★，Apache-2.0） | P1-P2 |
| A8 | 内置录屏与截图音效 | Zeryth | P2 |
| A15 | 版本收藏置顶、Modrinth 无图标兜底 | hyplant（3★） | P2 |
| C5 | auto 加载顺序（ReLinker / SoLoader） | — | P2，需先验证 |

## 3. 排除

- `AhmedGhaith-0/ZalithLauncher2-Crack`（15★）：名称指向破解
- `Ester-Mol/amethyst-launcher-virus-edition-with-crypto-miner-`：自述含木马/挖矿
- `simpicutew/ZalithLauncher2-Local`：README 自述 ZL2 2.7 已吸收，做了白做
- `ashmeet-op/HyperLauncher`：已归档

## 4. 外部 API

| 用途 | 端点 | 备注 |
|---|---|---|
| GitHub | `api.github.com` | 沙盒唯一可达 |
| 版本清单 | `piston-meta.mojang.com/mc/game/version_manifest_v2.json` | 未亲验 |
| 微软登录 | login.microsoftonline.com → user.auth.xboxlive.com → xsts.auth.xboxlive.com → api.minecraftservices.com | 未亲验，链路不可改 |
| Modrinth | `api.modrinth.com/v2` | 300 次/分，必须设唯一 User-Agent |
| CurseForge | `api.curseforge.com/v1` | 需 `x-api-key` |
| MCIM 镜像 | `mod.mcimirror.top/modrinth`、`mod.mcimirror.top/curseforge` | 100% 兼容官方结构，需提交 UA |
| BMCLAPI | BMCL 系资源镜像 | 开放给所有启动器 |
