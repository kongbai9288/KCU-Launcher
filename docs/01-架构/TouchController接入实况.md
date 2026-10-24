# TouchController 接入实况（v0.8.x）

> **重大发现：不需要写任何接入代码。FCL 主线 1.3.4.0 已经内置了完整的 TouchController 支持。**
> 执行者：yuanbao，2026-10-19。全部结论来自本仓库已落地源码的实测阅读。

---

## 1. 上游已内置（实测）

### 1.1 源码位置

| 文件 | 大小 | 作用 |
|---|---|---|
| `FCL/src/main/java/com/kongbai/kculauncher/mio/touchcontroller/TouchController.kt` | 5881 B | 代理客户端封装 |
| `FCL/src/main/java/com/kongbai/kculauncher/mio/touchcontroller/TouchControllerInputView.kt` | 25461 B | 触摸输入视图 |

`build.gradle.kts` 第 316 行：`implementation(libs.touchcontroller)`

依赖坐标（`gradle/libs.versions.toml`）：

```
touchcontroller = "0.0.5"
touchcontroller = { group = "top.fifthlight.touchcontroller",
                    name = "proxy-client-android",
                    version.ref = "touchcontroller" }
```

### 1.2 自动启用链路

```
用户安装 TouchController mod
        ↓
ModChecker.kt 扫描已装模组
        ↓
"touchcontroller" -> bridge.setHasTouchController(true)
        ↓
KCUBridge.hasTouchController() == true
        ↓
GameMenu.java:795  if (getBridge() != null && getBridge().hasTouchController())
        ↓
new TouchController(activity, screenWidth, screenHeight, ...)
        ↓
touchControllerInputView.setClient(...)
```

**全自动。用户装了 mod 就生效，没装就不启用。**

### 1.3 通信方式（修正上一轮的错误）

上一轮我根据 Gitee 上 v0.0.9 的旧说明写的是「UDP 端口 + `TOUCH_CONTROLLER_PROXY`」。**错了。**

实际源码：

```kotlin
val socketName = "FoldCraftLauncher"
val transport = UnixSocketTransport(socketName)
Os.setenv("TOUCH_CONTROLLER_PROXY_SOCKET", socketName, true)
client = LauncherProxyClient(
    transport = transport,
    capabilities = setOf(PlatformCapability.TEXT_STATUS),
)
```

| 项 | 上一轮（错） | 实测（对） |
|---|---|---|
| 传输 | UDP 端口 | **Unix Domain Socket** |
| 环境变量 | `TOUCH_CONTROLLER_PROXY` | **`TOUCH_CONTROLLER_PROXY_SOCKET`** |
| 值 | 端口号 | **socket 名字符串** |

### 1.4 主线比分支多出的能力

主线 `TouchController.kt`（5881 B）比 `TouchController/FoldCraftLauncher` 分支的（3839 B）多了 `moveView()`：

```kotlin
client.moveView(true, deltaY / height, deltaX / width)
```

对应 TouchController NEWS 0.2.1-beta12 的「添加了启动器转头的接口」。即启动器侧的控件也能驱动视角，与 mod 内控件共存。

---

## 2. 本轮实际做的改动

### 2.1 升级 proxy-client：0.0.5 → 0.0.8

依据：`TouchController/TouchController` 仓库 `touchcontroller/proxy/client/properties.bzl`

```
proxy_client_version = "0.0.8"
```

升级理由（来自 NEWS）：

| 版本 | 相关条目 |
|---|---|
| 0.3.1-alpha14 | 为 Android 启动器添加了**原生事件桥**，避免 Unix Domain Socket 的 IPC 开销 |
| 0.3.1-alpha15 | 修复了 Android 启动器**原生事件桥可能死锁**，进而导致游戏卡住的问题 |

**状态：✅ 已验证通过。**

沙盒无法访问 Maven Central，无法预先确认 0.0.8 是否已发布——**由 CI 构建来证伪**。

验证结果（Actions run 39206354140）：

| 项 | 结果 |
|---|---|
| 五个架构 `Build release` | 全部 success |
| 产物 | `app-all` 327461076 B / `app-x86_64` 176087077 / `app-arm64` 170392839 / `app-arm` 163133552 / `app-x86` 156781811 |

**`top.fifthlight.touchcontroller:proxy-client-android:0.0.8` 确实存在于公共仓库，且与主线代码 API 兼容。**

### 2.2 socketName 保持 "FoldCraftLauncher" —— 不改

决策：不改。

理由：

- 它是 **Unix Domain Socket 的本地通道名**，不是用户可见的品牌标识
- mod 侧通过 `TOUCH_CONTROLLER_PROXY_SOCKET` 环境变量读取，理论上改名自洽
- 但**无法确认 mod 侧是否另有硬编码**；若存在，改名会直接导致连接断开，且用户极难排查
- 改名收益为零，风险不对称

**这是"收益为零但可能致命"的改动，不做。**

### 2.3 未做的：空布局入口

防错清单 115 要求「必须能一键关闭启动器自带控件」。经查，FCL 本身已支持新建空白布局（官方 wiki 就是这么教用户的）。**这一项降级为待确认**：需确认默认布局是否允许用户删除全部控件，若不能才需要新增入口。

---

## 3. 与上一轮方案的关系

| 上一轮结论 | 本轮状态 |
|---|---|
| 不要改启动器 Java 层 | **成立，且更强**——根本不用改 |
| 输入下移到游戏内 | **成立**，上游已实现 |
| 用 TouchController | **成立**，且上游已集成 |
| 启动器只做转发 | **成立**，`TouchController.handleTouchEvent` 就是这个角色 |
| 需自行集成 proxy-client | **撤回**，上游已集成 |

---

## 4. 待办 / 未验证

- [x] 确认上游已内置（源码实测）
- [x] **proxy-client 0.0.8 编译通过**（Actions run 39206354140，五架构全绿）
- [ ] 0.0.8 与 TouchController mod 0.3.1-alpha15 的协议兼容性**未验证**
- [ ] 原生事件桥（0.3.1-alpha14 新增）上游是否已接入——主线代码用的是 `UnixSocketTransport`，**未使用原生事件桥**
- [ ] 空布局入口是否需要新增
- [ ] **全程未实机验证**
