# 上游 API 地图（FCL）

> 来源：FCL 官方 `docs/source-reading/`（作者自己的源码通读报告）+ 本仓库实测的 GitHub API 返回 + 直接读取的源码。
> 上游规模：889 个 Java + 196 个 Kotlin，约 17.6 万行；源码通读报告基于 2026-09-27 的 main。
> 用途：改任何东西之前先查这里，确认该改哪个类、怎么接入，不要凭空造轮子。

---

## 0. 总体分层

```
FCLApp（轻量 Application，仅跟踪 currentActivity）
 ├─ SplashActivity → EulaFragment / RuntimeFragment（EULA、权限、运行时解压）
 │   └─ enterLauncher：RendererManager → JavaManager → Controllers → ConfigHolder.init
 ├─ MainActivity（ViewPager2 纵向承载 8 个 UI，UIManager 管理）
 ├─ JVMActivity（游戏 / Jar 执行器共用，TextureView 驱动，静态 fclBridge）
 ├─ JVMCrashActivity（独立 :crash 进程）＋ ProcessService（独立 :jvm 进程）
 └─ 底层：fclcore（HMCL 移植核心）
          + fclauncher（Pojav 移植启动后端）
          + fcllibrary（组件库）
          + com.mio（新代码层）
          + org.libsdl（SDL3 嵌入式改造）
```

**注意**：重初始化不在 Application。强杀进程可能不经过 Splash 直接落 MainActivity，所以 `MainActivity.onCreate` 有 `ConfigHolder.isInit()` 兜底。→ 本项目的自定义层初始化必须跟着这个模式走，不能只放 `Application.onCreate`。

---

## 1. 启动全链路（最重要）

```
Versions.launch
  → LauncherHelper（TaskDialog 六阶段）
      1. MaintainTask 版本维护
      2. 全量 mod 扫描 + lwjgl3ify 补丁
      3. Java 版本校验
      4. 完整性检查 + 释放 MioLibPatcher / MioLaunchWrapper
      5. 登录（进度回调实时写弹窗）
      6. 构建 LaunchOptions + FCLGameLauncher + 五个人工确认点
  → DefaultLauncher.generateCommandLine（组装完整 java 命令行）
  → FCLauncher.launchMinecraft
  → JVMActivity.onSurfaceTextureAvailable 才真正 fclBridge.execute(surface, callback)
  → native 侧 setenv / dlopen / VMLauncher 在同进程内拉起 OpenJDK（不是 OS 进程）
  → mio.Wrapper 包装主类
  → 游戏跑 GL4ES / Zink / SDL 渲染路径
```

### 1.1 `FCLauncher.launchProcess` 内部顺序

```
new FCLBridge()                ← 静态块 System.loadLibrary("fcl") + ("pojavexec_awt")
setLogPath(LATEST_GAME_LOG)
创建 MAX_PRIORITY 线程（不 start）
  ↓ JVMActivity.onSurfaceTextureAvailable 触发 execute()
execute():
  redirectStdio(日志路径)
  handleWindow()                ← 有 gameDir 走 setupBridgeWindow(surface)；无则起软绘制线程
  thread.start()
启动线程内：
  logStartInfo
  setEnv                        ← addCommonEnv / addModLoaderEnv / addRendererEnv / addCustomEnv
  setUpJavaRuntime              ← 见 §2
  setupGraphicAndSoundEngine    ← 见 §3
  bridge.chdir(workingDir)
  launch()                      ← 见 §4
```

---

## 2. JRE 与 .so 的加载方式（本项目自定义 natives 的接入点）

### 2.1 `setUpJavaRuntime`

1. 读 JRE 的 `release` 文件取 `OS_ARCH` → 定位 `lib/<arch>`
2. JDK8 多一层 `/jre`；x86 归一为 `i386/i486/i586`
3. **dlopen 顺序固定**：`libjli` → `libjvm` → `libfreetype / libverify / libjava / libnet / libnio / libawt / libawt_headless / libfontmanager`
4. 然后 **递归 `locateLibs` 把 JRE 目录里所有 .so 全部 dlopen**

→ 这就是"顺序会崩"的第一现场：JRE 自己的 .so 是按固定顺序 + 递归补全加载的。

### 2.2 `setupGraphicAndSoundEngine`

1. `dlopen libopenal`
2. 插件渲染器按 `pojavEnv` 的 `DLOPEN` 字段**逐个**加载
3. 再 dlopen GL 库
4. Android < R 时把句柄写入 `RENDERER_HANDLE` 环境变量（供 egl_bridge `dlsym` 复用）

→ 渲染器插件的加载顺序由 `DLOPEN` 字段顺序决定，这正好对应 root-S7 fork 的 `custom_renderer.json` 里 `pojavEnv` 的写法。

### 2.3 `launch()` 拼 `LD_LIBRARY_PATH`（关键）

按顺序包含：

1. JRE lib
2. jli
3. jvm 的 server / client 目录
4. jna
5. **插件渲染器路径**
6. **`NativeLibPlugin.getPaths`**
7. **`MOD_RUNTIME_DIR`**
8. `/system` `/vendor` 的 lib64
9. `LWJGL_DIR/<版本>/natives/<abi>`
10. APK `nativeLibraryDir`

然后 `rebaseArgs`：

- `${natives_directory}` → 强制替换为上面拼好的路径
- `${gl_lib_name}` / `${egl_lib_name}` → 替换为渲染器库

最后 `com.oracle.dalvik.VMLauncher.launchJVM(args)` —— **在当前 Android 进程内**拉起 OpenJDK，`args[0]` 的 `bin/java` 只是占位 argv[0]。

### 2.4 对 KCU Launcher 的接入结论

玩家自定义 .so 的正确接入位置是 **`MOD_RUNTIME_DIR` + `NativeLibPlugin.getPaths` + `LD_LIBRARY_PATH`**，而不是自己 `System.load`。

公开的两个现成扩展点：

| 扩展点 | 位置 | 用途 |
|---|---|---|
| `MOD_RUNTIME_DIR` | `context.getDir("runtime_mod")` | native mod / 自定义运行时库 |
| `NativeLibPlugin.getPaths` | 插件体系 | 插件提供的额外库路径 |

→ 双层目录模型落地为：公共目录 `natives/<abi>/` 存素材 → 同步到 `MOD_RUNTIME_DIR` 或插件路径 → 由上游的 `LD_LIBRARY_PATH` 机制自然生效。

**这比自己 `System.load()` 安全得多**：它复用上游已验证的机制，不动原生加载逻辑，天然满足"默认路径零污染"。

**待实测**：`NativeLibPlugin` 的确切接口签名尚未亲验（未读源码）。接入前必须先读。

### 2.5 JNI 库清单（12 个）

| 库 | 职责 |
|---|---|
| `pojavexec` | 核心。egl_bridge / input_bridge_v3 / jre_launcher / environ / ctxbridges / virgl / jvm_hooks / native_hooks |
| `pojavexec_awt` | awt_bridge：Dalvik 与 OpenJDK 双 VM 间的 AWT 事件桥 |
| `fcl` | 基于 ByteHook 的加载器（exit trap） |
| `linkerhook` | 拦截 libvulkan 加载器的 `android_dlopen_ext`，懒创建 Turnip 驱动命名空间（`-z global`） |
| `androidnsbypass` | Android linker namespace 绕过工具（vendor 自 Amethyst，MIT） |
| `glxshim` | 为 LWJGL < 3.3.5 提供 `glXGetProcAddress` 符号 |
| `jsound` | OpenJDK libjsound + OpenAL 后端 + jdk8 兼容，服务 jre8/17/21/25 四代 |
| `flite` / `fliteWrapper` / `flite_cmu_us_kal16` | 游戏 TTS |
| `awt_headless` / `awt_xawt` | 占位空库 / `java.awt.*` initIDs 假实现 |

---

## 3. 渲染器

Java 侧**没有** RendererKind 枚举。`com.mio.data.Renderer` 是数据类，内置 6 个 ID：

`GL4ES` / `VGPU` / `VIRGL` / `ZINK` / `FREEDRENO` / `NGGL4ES`

插件渲染器 = `path` 非空者。

`addRendererEnvInner` 按 ID 设置：

- `POJAV_RENDERER`（`opengles2` / `opengles3_desktopgl_zink_kopper` / `gallium_virgl` / `gallium_freedreno` 等）
- `POJAVEXEC_EGL`
- gl4es 的 `LIBGL_*` 调优项
- MESA 的 4.6/460 版本覆写 + `MESA_GLSL_CACHE_DIR`

插件渲染器透传其 `pojavEnv`（`LIB_MESA_NAME` / `MESA_LIBRARY` 自动加插件路径前缀）。

`addRendererEnv` 再为 SDL 路径算 `SDL_EGL_LIBRARY` / `SDL_OPENGL_LIBRARY` 绝对路径（**仅当库真实存在于渲染器目录**）。

Surface 生命周期由 JVMActivity / TextureView 驱动。Java 侧不参与帧循环，FPS 经 `FCLBridge.getFps()` 轮询 native。

---

## 4. 输入

**上行**（Android → 游戏）：

```
FCLBridge.pushEvent* → CallbackBridge.sendXxx → @CriticalNative native 写输入管道
```

**下行**（游戏 → Android）四类入口：

| 入口 | 触发方 | 作用 |
|---|---|---|
| `notifyLauncher` / `nativeNotifyLauncher` | JRE 侧 `sdl_hook.c` | `ACTION_INIT_LAUNCHER_INTEGRATION` 时加载 SDL3/SDL2 + `SdlBridge.setupJNI` |
| `onGrabStateChanged` | — | 鼠标捕获状态，Choreographer 延迟 16ms 防抖 |
| `accessAndroidClipboard` | — | 剪贴板 |
| `onDirectInputEnable` | — | GLFW direct gamepad 模式 |

键码体系（8 个文件，多套并存）：

| 类 | 体系 |
|---|---|
| `FCLKeycodes` | Linux evdev 键码（触屏控件层的中间键码） |
| `LwjglGlfwKeycode` | GLFW 3.4 键码 + `GLFW_MOD_*` + `GLFW_MOUSE_BUTTON_*` |
| `EfficientAndroidLWJGLKeycode` | Android → GLFW，112 项并行数组 + 二分查找 |
| `AndroidKeycodeMap` | Android → FCL |
| `LwjglKeycodeMap` | FCL → GLFW（`CallbackBridge.sendKeycode` 用它） |
| `GamepadKeycodeMap` | 手柄 → FCL（伪鼠标码 1000-1004，摇杆伪键码 2000+） |
| `MinecraftKeyBindingMapper` | MC `options.txt` 的 `key_key.*` → FCL 键码 |
| `AWTInputEvent` | Oracle JDK `java.awt.event` 常量拷贝（913 行） |

**坑**：`AndroidKeycodeMap` / `EfficientAndroidLWJGLKeycode` 依赖数组**有序**才能 `binarySearch`，加映射必须保序。

---

## 5. 任务系统（HMCL 移植）

DAG 任务编排。模板方法：

```
preExecute() → dependents → execute() → dependencies → postExecute() → onDone
```

| 类 | 作用 |
|---|---|
| `Task<T>` | 抽象基类，状态 READY/RUNNING/EXECUTED/SUCCEEDED/FAILED |
| `TaskExecutor` / `AsyncTaskExecutor` | 调度器，翻译成 `CompletableFuture` 链 |
| `Schedulers` | `io()`（8 线程 IO 池）、`androidUIThread()`（Handler::post）、`defaultScheduler()`（ForkJoinPool.commonPool） |
| `FetchTask` | 网络抓取基类，跑在专用 Download 池 |
| `GetTask` | 文本抓取，恒 CHECK_E_TAG |
| `FileDownloadTask` | 下载到临时文件 + 完整性校验 |

`FetchTask` 关键行为：

- URL 顺序 × retry（默认 3）双重循环
- ETag 三态：`CHECK_E_TAG` / `NOT_CHECK_E_TAG` / **`CACHED`（execute 直接 return，静默成功）**
- 4xx 直接放弃（FileNotFoundException 不重试）
- 重试**退避序列 500ms / 2s / 5s**，分片 sleep 以便快速响应取消
- 静态 `speedEvent` EventBus + 常驻 Timer，每秒派发 `SpeedEvent`（即使无下载也发 0）

**线程模型**：流水线胶水跑 `ForkJoinPool.commonPool`；`execute/preExecute/postExecute` 跑 `task.getExecutor()`；**所有监听器回调与 onDone 都在完成线程上同步执行，绝不自动切 UI 线程** —— UI 侧自己用 `Schedulers.androidUIThread()` 包。只有 progress/message 属性更新被 `InvocationDispatcher` 自动合并投递到主线程（AtomicReference 去抖 + 1000ms 节流）。

---

## 6. 构建与产物

- 3 个 buildType：`release` / `debug`（都用正式证书，不混淆）、`fordebug`（testBuildType，id 加 `.debug`）
- `updateMap` 任务把版本号/日期回写根 `version_map.json`
- checkstyle：toolVersion 10.12.5，`maxErrors/maxWarnings = 0`，只查 `src/main/java` 的 Java，单文件上限 2000 行，K&R 花括号，排除 org/lwjgl、org/libsdl、fakefx
- 根 build.gradle.kts 强制覆盖 R8 为 9.4.17（捆绑版本不支持 Kotlin 2.4）
- `FCL/libs/` 7 个 aar：lwjgl-3.3.3-natives、lwjgl-3.4.1-natives、NG-GL4ES、kopper-zink、spirv-cross-natives、openal-soft、SDL

---

## 7. 非显然行为（设计使然，不是 bug，别当 bug 修）

| 行为 | 说明 |
|---|---|
| `HttpRequest` GET **不校验状态码** | Modrinth SHA-1 反查依赖此行为，**不可改** |
| `OSRestriction.allow()` **恒 false** | 所有带 OS 规则的库一律不适用（Android 特化） |
| `GameVersionNumber` 静态表 | 类加载时解析 versions.txt 建表，首次触达成本高 |
| `OAuth.IS_CANCELED` | 已加 volatile；同一时刻仍仅支持一次设备码登录 |

---

## 8. 上游已修复的坑（不要重新引入）

- `OAuth.IS_CANCELED` 缺 volatile（跨线程可见性）
- `GameMenu.initCursorView` 光标 Y 偏移误用 `getMouseOffsetX()`
- favorite Room 库缺 `MIGRATION_1_2`（破坏式重建）
- `ClassicAccountLoginDialog` 空壳补全（原回调永不触发，`latch.await()` 永久阻塞）
- `ModrinthInstallTask` 读旧配置 TypeToken 误用 `CurseManifest`（Modrinth 整合包更新必 CCE 回滚）
- `GameOption` 静态 `parameterMap` / `FileObserver` 多实例互相覆盖
- `ControlDirectionData.equals` 参数类型笔误
- `InstantTypeAdapter` 最内层 catch 误引外层异常变量
- `CacheRepository.storages` 读锁内 `computeIfAbsent` 写竞态（已改 ConcurrentHashMap）
- `LwjglKeycodeMap` 重复 KEY_3
- fakefx 死代码第一轮删了 113/322 类

---

## 9. 已知待办（上游自己记的）

- fakefx 剩余"仅 Bindings 内部可达"类（When/ListBinding 族等）暂缓删除，收益低回归风险高
- `rebaseArgs` 里有一行孤立的 `config.getRenderer();` 死代码
- `DownloadUI` 与 `FCLMultiPageUI` 并存两套临时页栈，跨 UI 弹栈需注意调用侧

---

## 10. 未亲验项

以下尚未读源码，接入前必须先读：

- `NativeLibPlugin` 的接口签名与注册方式
- `RendererManager` / `JavaManager` 的具体 API
- `CustomControl` / 控制器仓库的加载流程
- `Terracotta` 模块的联机流程
- `com.mio` 包（新代码层）全貌
