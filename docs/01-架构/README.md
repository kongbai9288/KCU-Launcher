# 架构与目录

## 1. 分层

| 层 | 来源 | 是否自研 |
|---|---|---|
| UI（Compose + Material 3） | 参考 ZL2 | 否，改配色与布局 |
| 启动器逻辑（版本/实例/下载/账号） | FCL `fclcore`（源自 HMCL） | 否 |
| 原生启动桥（JNI / EGL / 输入） | Pojav / Amethyst-Android 系 | 否，不动 |
| Java 运行时 | FCL-Team/Android-OpenJDK-Build | 否 |
| 渲染栈（GL4ES / VirGL / Zink / MobileGlues） | 上游构建 | 否 |
| 自定义层（本项目新增） | — | 是，但只做配置读取与流程编排 |

自定义层的边界：**只读配置、只做校验、只做编排，不重新实现上游任何功能。**

## 2. 双层目录模型

Android 不允许从公共目录加载可执行代码：

| 路径 | SELinux | 特性 |
|---|---|---|
| `/sdcard`（外部存储） | noexec | `dlopen` 报 `couldn't map ... segment 1: Permission denied` |
| `getFilesDir()` / `getCacheDir()` | `app_data_file` | 可写，不可执行（Android 10+ W^X） |
| `nativeLibraryDir` | `exec_type` | 可执行，不可写 |

因此：

```
/sdcard/KCU/natives/<abi>/        玩家放 .so（素材库，可读写，Migrator 可读）
        │  启动时：SHA-256 比对 → ABI 校验 → 按需复制
        ▼
<私有>/runtime/natives/<abi>/     同目录内按 order 顺序 System.load()
```

关键点：参与加载的 .so 必须在**同一私有目录**内。依赖链的搜索起点是加载它的那个 .so 所在目录。

## 3. 完整公共目录布局

```
/sdcard/KCU/
├── .minecraft/          游戏实例
├── natives/<abi>/       自带 .so 素材库
├── config/              全局配置覆盖
├── instances/<id>/      实例级配置覆盖
├── scripts/             终端 .sh
├── logs/
├── control/             控制布局
├── share/
└── manifest.json        机器可读清单
```

改路径集中在一个类：`FCLPath.java`（FCL）与 `PathManager.kt`（ZL2）。不在各处硬编码。

## 4. 模块复用原则

用户要求：商店的加载器图标显示尽量复用同一个模块，缩小体积并便于定位函数。

- 加载器图标统一放一个 `LoaderIcon` 模块，按加载器名 → drawable 的映射表取图。
- 上游已有资源：`img_fabric.png` / `img_forge.png` / `img_neoforge.png` / `img_quilt.png` / `img_liteloader` / `img_optifine.png` / `img_cleanroom.png` / `img_legacyfabric.png`。
- 不新增多套图标资源，不按页面各建一份。
- 商店页、版本列表、实例详情共用同一函数。

## 5. 进程

上游 manifest 存在 `:jvm` 与 `:crash` 独立进程。`Application.onCreate` 会每进程执行一次，自定义层初始化必须加进程名守卫。

## 6. 已拆解 / 已停用的上游模块

相对上游 FCL，以下模块已被本项目改造或停用。改动均记录在 [改动台账](../00-总览/改动台账.md)。

| 模块 | 状态 | 说明 |
|---|---|---|
| 主题引擎（`ThemeEngine` / `ThemeData`） | **拆解** | 87 个 View 依赖其 API，不删类；改为配色常量化，换色/换背景接口 no-op。保留亮暗模式、全屏、皮肤模型、动画速度 |
| 上游公告（`Announcement` / `MainUI` 公告区） | **删除** | 原启动即请求 FCL 官方仓库公告文本，属隐式联网 |
| 上游更新源（`UpdateChecker`） | **停用** | KCU versionCode 与 FCL 不同源，不停用会把 FCL 的 APK 推给用户 |
| 上游网盘推广（`QuarkPromo`） | **删除** | 原按启动次数掷骰子弹窗，第 90 次必弹，中签会取消本次启动。v0.30.0 停用，v0.32.0 连同 `com.mio.promo` 包整体删除；`MainActivity` 启动路径已平铺 |
| 下载入口 | **默认改为导入** | 默认不提供 MC 下载，由用户提供 URL 或本地 jar；原下载页仅在开发者模式（`dev_mode` 哈希校验）下出现 |
| 账号流程 | **合并** | 离线与正版合并为"输入名称"入口 |
| 启动计数（`LaunchCountSetting`） | **删除** | 仅被推广机制使用，随之删除。`LEGACY_FILE_NAMES` 中对应文件名**保留**，用于识别并清理旧文件 |

**保留但暂不启用**：`RemoteVersion` / `ChangelogDialog` / `UpdateDialog` —— 将来接自有更新源时直接复用。
