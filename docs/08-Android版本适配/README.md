# Android 平台版本适配

> 来源：Android Developers 官方文档原文（行为变更 / 兼容性 / Google Play 政策）。
> 抓取时间：2026-10-19 01:25–01:31 CST
> 用途：升 targetSdk、加原生库、改存储方案之前必读。

---

## 1. 16 KB 页大小（最高优先级，会直接导致装不上/跑不起来）

- Android 15 起，AOSP 支持 **16 KB 页大小**设备。
- 只要 app 用了任何 NDK 库，**必须重新构建**才能在 16 KB 设备上运行。不重新编译，未来 Android 版本上**直接不能工作**。
- Google Play：**自 2025-11-01 起**，target 到 Android 15 及以上的新应用和更新**必须支持 16 KB 页大小**。
- 纯 Java/Kotlin 应用天然支持；本项目有大量 .so，**受影响**。

合规做法（官方原文）：

- 用最新 AGP 与较新 NDK
- 重新编译所有生成 .so 的模块
- 若依赖方提供预编译 .so，需升级到声明支持 16 KB 的版本
- **代码里去掉硬编码的 4096 / PAGE_SIZE**，改用 `getpagesize()` 或 `sysconf(_SC_PAGESIZE)`
- 检查 `mmap` / `mprotect` 的对齐假设
- 短期无法升 NDK 时加链接参数：

```
CMake:    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")
ndk-build: APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
           或 LOCAL_LDFLAGS += "-Wl,-z,max-page-size=16384"
```

验证：`adb shell getconf PAGE_SIZE` 应返回 `16384`；APK Analyzer 可查 ELF 段对齐。

**对本项目的影响（新增约束）**：玩家自带 .so 同样必须 16 KB 对齐。校验环节需增加 ELF 段对齐检查，不对齐的给出明确提示而不是加载后崩。

---

## 2. 存储：MANAGE_EXTERNAL_STORAGE 与 SAF

### 2.1 政策

Google Play 把 `MANAGE_EXTERNAL_STORAGE`（"所有文件访问权限"）列为高风险权限：

- 仅适用于 target API ≥ 30 且声明该权限的应用
- 必须通过 **Permissions Declaration Form** 声明并获得批准，否则可能被下架
- 允许用途：**文件管理、备份与恢复、杀毒、文档管理、设备内搜索、磁盘/文件夹加密、设备迁移/换机**
- 明确不允许：仅访问媒体文件、用户手动选单个文件的选择场景
- 官方推荐替代：Storage Access Framework 或 MediaStore

### 2.2 为什么本项目仍需要它

SAF 在 Android 11+ **无法授予**以下目录：根目录、`Download`、`Android/data`、`Android/obb`。

本项目公共目录设计为 `/sdcard/KCU/`，位于**根目录**下 → SAF 拿不到。**要拿到根目录级目录，MANAGE_EXTERNAL_STORAGE 是唯一路径。**

兜底：未授予该权限时回退到应用专属目录（`getExternalFilesDir`），并在设置里引导跳系统授权页。见 `docs/07-兜底`。

### 2.3 其他存储要点

- `requestLegacyExternalStorage` **只在 API 29 有效，API 30+ 被忽略**
- API 30+ 应用**不能**自己在外部存储创建专属目录，须用 `getExternalFilesDirs()`
- API 33+ `READ_EXTERNAL_STORAGE` 被细分为 `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO`
- 卸载保留：Android 11 上重装后 `preserveLegacyExternalStorage` 失效

---

## 3. 前台服务类型（targetSdk 34+）

- **API 34 起必须为每个前台服务声明 service type**，并申请对应权限（如 `FOREGROUND_SERVICE_DATA_SYNC`），否则抛 `SecurityException`
- API 31+ 不允许从后台启动前台服务（有少数例外）
- API 35 起：`dataSync` 类型有运行时长限制；`BOOT_COMPLETED` 不能再启动某些前台服务
- API 36 起：从前台服务发起的后台作业需遵守各自配额

上游 FCL 已声明 `dataSync` / `connectedDevice` / `specialUse` 三种权限，保留不动。若升 targetSdk 到 35+，需复查 `dataSync` 的超时限制是否影响下载任务。

---

## 4. Android 14 动态代码加载

- Android 14 起，动态加载的文件**必须以只读方式打开**，否则抛 `SecurityException: Writable dex file ... is not allowed`
- 推荐放 `getCodeCacheDir()`（ART 优化过）；`DexClassLoader` 用只读路径
- 禁止 `MODE_WORLD_WRITEABLE` / 打开可写的 dex

**对本项目的影响**：从公共目录复制 .so 到私有目录后，**复制完成即置为只读**（`chmod 444` 等价）。这既符合该规则，也不影响 `dlopen`（它需要读+执行，不需要写）。已写入兜底与校验流程。

---

## 5. MediaProjection（抓屏功能）

- **Android 14（API 34）起，每次捕获会话都必须重新征得用户同意**
- 缓存 `createScreenCaptureIntent()` 返回的 Intent 并多次传给 `getMediaProjection` → `createVirtualDisplay` 抛 `SecurityException`
- 同一个 `MediaProjection` 实例**只能调用一次** `createVirtualDisplay`
- 单次捕获会话 = 一次 `createVirtualDisplay` 调用
- 处理配置变更（旋转/尺寸）用 `VirtualDisplay#resize` + `setSurface`，不要新建 MediaProjection
- 应注册回调处理用户中途不同意的情况

**对本项目的影响**：抓屏功能**不能做成"授权一次长期生效"的后台常驻**。每次触发都要走一次系统确认。设计上应做成"按需单次"，并在 UI 明示。

---

## 6. Android 14 其他相关变更

- **Zip 路径遍历**：条目名含 `..` 或以 `/` 开头时，`ZipFile(String)` 与 `ZipInputStream.getNextEntry()` 抛 `ZipException`。可用 `dalvik.system.ZipPathValidator.clearCallback()` 停用（不推荐）。→ 影响整合包/modpack 解压，非法路径必须显式处理
- **OpenJDK 17 更新**：正则无效组引用会抛 `IllegalArgumentException`；`UUID.fromString()` 校验更严格
- **ProGuard/R8**：`java.lang.ClassValue` 相关裁剪问题
- **前台服务类型**：见 §3

---

## 7. 私有原生库（API 24+）

见 `docs/04-上游分析/官方文档核对.md` §1.1。要点：不得链接非 NDK 平台库，由动态链接器强制执行，三种加载方式行为一致。

---

## 8. 升 targetSdk 前的自检

- [ ] 所有 .so 是否 16 KB 页对齐？
- [ ] 前台服务是否都声明了 type 并申请了对应权限？
- [ ] 动态加载的文件是否已置只读？
- [ ] 抓屏是否按"每次会话重新授权"实现？
- [ ] Zip 解压是否处理了路径遍历异常？
- [ ] 有没有依赖非 NDK 平台库？
- [ ] targetSdk ≥ 31 时 manifest 的 `<uses-native-library>` 是否需要同步？
