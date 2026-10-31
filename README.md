[English](./English.md) | [中文](./README.md)

![kcu logo](./docs/assets/kcu_icon.png)

# KCU Launcher

安卓上的 Minecraft: Java Edition 启动器。全部由现有开源项目拼装而成，不自研底层。

[![Android CI](https://img.shields.io/github/actions/workflow/status/kongbai9288/KCU-Launcher/build.yml?branch=main&label=Android%20CI&style=flat-square&color=6C5CE7)](https://github.com/kongbai9288/KCU-Launcher/actions/workflows/build.yml)
![Downloads](https://img.shields.io/github/downloads/kongbai9288/KCU-Launcher/total?style=flat-square&color=6C5CE7)
![Release](https://img.shields.io/github/v/release/kongbai9288/KCU-Launcher?label=Release&style=flat-square&color=6C5CE7)
![License](https://img.shields.io/github/license/kongbai9288/KCU-Launcher?style=flat-square&color=6C5CE7)

名字与 [KCU Migrator](https://github.com/kongbai9288/KCU-Migrator) 一致，图标共用。两个项目**彼此独立**，不做代码合并，只在文件系统层面通过公共目录互通数据。

本项目使用 AI 生成代码，由人设计架构与实现方式。所有涉及底层运行时的部分一律复用上游实现，不重新造轮子。

## 它解决什么

现有启动器把依赖库、运行时、配置锁在应用私有目录里，第三方工具读不到。KCU Launcher 把可迁移面全部放到公共目录，并用一份机器可读的 manifest 描述出来。

## 功能

1. 运行 Minecraft: Java Edition（含 Forge / NeoForge / Fabric / Quilt / LiteLoader / OptiFine / Cleanroom）
2. 几乎一切可自定义：Java 运行时、渲染器 .so、原生扩展库、.so 加载顺序、控制布局、下载源（官方 / 国内镜像 / 自己填的备源，可勾选，也可多源同时下）
3. 内置终端（真实 `sh` 进程），可用来安装其他加载器
4. 版本列表显示加载器图标，一眼看清装了什么
5. 下载源可切换：默认官方与国内镜像都开，可只勾其中一个，也可自己添加备源；某个源失败只跳过它，不中断下载

## 相对上游移除的部分

| 项 | 说明 |
|---|---|
| 主题配色自定义 | 采用固定纸面观感，不提供换色与换背景入口 |
| 上游远程公告 | 不联网拉取第三方公告 |
| 上游更新检查 | 不请求第三方版本表 |
| 第三方推广弹窗 | 启动不被任何推广拦截 |
| 内置游戏下载 | 已重新向用户开放（原为仅导入）；受地区限制：处于中国大陆时该功能不可用并给出提示 |
| 按键布局格式 | 改用 `.kcul` 专用封装格式，上游 FCL 无法直接打开，避免解析出"能加载但缺字段"的半截布局 |

## 怎么用

1. 下载 Release 里的 apk 安装
2. 打开 App，登录微软账号
3. 选版本 → 装加载器 → 启动

想改东西：把配置文件放到公共目录 `/sdcard/KCU/config/`，同名文件会覆盖内置默认值。不设置就用内置默认，启动流程与上游一致。

## 目录布局

```
/sdcard/KCU/
├── .minecraft/          游戏实例
├── natives/<abi>/       玩家自带的 .so（素材库，只存不加载）
├── config/              配置覆盖（native_load_order / app_config ...）
├── instances/<id>/      实例级覆盖
├── scripts/             终端可用 .sh
├── logs/
├── control/             控制布局
├── share/
└── manifest.json        机器可读清单，供 KCU Migrator 读取
```

`.so` 不会从公共目录直接加载。启动时先校验、比对哈希、复制到私有目录，再从私有目录按你指定的顺序 `System.load()`。原因见 [docs/01-架构](./docs/01-架构/README.md)。

## 隐私

代码里没凭据。有后端，仅为 GitHub API、CurseForge API、Modrinth API 使用，且可替换。没统计，没广告。

启动时会从仓库取一份**远程策略**（地区限制 / 强制更新 / 强制停止），可随法律或运营变化随时调整，**无需重新发版**。生效的那份存在应用私有目录；公共目录里另有一份同名文件**仅供查看，本应用不会读取它**。

屏幕抓字功能默认关闭，开启前会单独弹授权页，默认端侧识别不出设备，不上传、不用于训练。完整说明见 [docs/05-合规](./docs/05-合规/README.md)。

## 第三方开源许可与致谢

本项目以 GPL-3.0 开源，继承上游许可。

- [FoldCraftLauncher](https://github.com/FCL-Team/FoldCraftLauncher) — GPL-3.0，主基座（HMCL 核心逻辑 + Pojav 后端）
- [HMCL](https://github.com/HMCL-dev/HMCL) — GPL-3.0，版本管理与账号体系来源
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) — LGPL-3.0，JNI 启动桥与渲染栈来源
- [ZalithLauncher2](https://github.com/ZalithLauncher/ZalithLauncher2) — GPL-3.0，Compose UI 与插件体系参考
- [Android-OpenJDK-Build](https://github.com/FCL-Team/Android-OpenJDK-Build) — JRE 8/11/17/21/25 构建脚本
- [ReLinker](https://github.com/KeepSafe/ReLinker) — Apache-2.0，原生库加载（预留）
- [SoLoader](https://github.com/facebook/SoLoader) — Apache-2.0，原生库加载（预留）
- [overlay-translator](https://github.com/ciddwd/overlay-translator) — Apache-2.0，屏幕抓字管线参考
- BMCLAPI / MCIM — 国内镜像

## 文档


| 目录 | 内容 |
|---|---|
| [docs/00-总览](./docs/00-总览/README.md) | 文档索引与项目状态 |
| [docs/01-架构](./docs/01-架构/README.md) | 模块分层、目录策略、双层目录模型 |
| [docs/02-契约](./docs/02-契约/README.md) | 玩家可改的配置文件格式 |
| [docs/03-防错](./docs/03-防错/README.md) | 每轮必读的错误清单 |
| [docs/04-上游分析](./docs/04-上游分析/README.md) | 上游实测数据、fork 分析、采纳清单 |
| [docs/05-合规](./docs/05-合规/README.md) | 法律与安全红线 |
| [docs/06-构建与CI](./docs/06-构建与CI/README.md) | 构建方式、密钥、ABI 策略 |
| [docs/07-兜底](./docs/07-兜底/README.md) | 失败降级策略 |
| [docs/08-Android版本适配](./docs/08-Android版本适配/README.md) | 16KB 页、存储权限、前台服务、抓屏授权 |

## 声明

本项目不内置、不分发 Minecraft 游戏文件，不绕过正版验证。使用前需自备正版账号。
