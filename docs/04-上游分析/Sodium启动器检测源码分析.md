# Sodium 启动器检测：源码取证与可自定义项

日期：2026-10-28　执行者：yuanbao
源码来源：**CaffeineMC/sodium**，`common/src/boot/java/net/caffeinemc/mods/sodium/client/compatibility/checks/PostLaunchChecks.java`
取证方式：按你授权开了**临时私有仓库** `kongbai9288/sodium-probe` 存档该文件（用完已删除）。

---

## 一、检测到底读什么（源码原文要点）

**触发时机**：OpenGL 上下文创建**之后**立即执行（`onContextInitialized`）。

### 判定 1：环境变量

```java
if (System.getenv("POJAV_RENDERER") != null) {
    return true;   // ← 命中即崩
}
```

### 判定 2：库搜索路径

```java
var librarySearchPaths = System.getProperty("java.library.path", null);
for (var path : librarySearchPaths.split(":")) {
    if (isKnownAndroidPathFragment(path)) return true;   // ← 命中即崩
}
```

### 判定 3：主目录 —— **只警告，不崩**

```java
var workingDirectory = System.getProperty("user.home", null);
if (isKnownAndroidPathFragment(workingDirectory)) {
    LOGGER.warn(...);     // ← 注意：这里没有 return true
}
return false;
```

**这段有个细节值得留意**：`user.home` 分支**只打日志不返回 true**，
即仅凭 `user.home` 不会被判定为 Pojav。

### 判定用的路径特征（硬编码）

```java
private static boolean isKnownAndroidPathFragment(String path) {
    return path.matches("/data/user/[0-9]+/net\\.kdt\\.pojavlaunch");
}
```

**只认 `net.kdt.pojavlaunch` 这一个包名。**

### 命中后的行为

```java
throw new RuntimeException("It appears that you are using PojavLauncher, ...");
```

**不是警告，是直接抛异常终止启动。**

---

## 二、我们这边现在会不会被命中

| 判定 | 我们的取值 | 结果 |
|---|---|---|
| `POJAV_RENDERER` | `addCommonEnv` **未设置**该变量（只设 `POJAV_NATIVEDIR`） | ✅ 不命中 |
| `java.library.path` | 本机路径，包名 `com.kongbai.kculauncher` | ✅ 不匹配那个正则 |
| `user.home` | 同上 | ✅ 只警告 |

**结论：当前默认不会被命中。**

**例外**：`Renderer.kt` 里 v2 插件渲染器有 `pojavRendererId`
（注释写明会设置 `POJAV_RENDERER`）。
**若用户选用了这类插件渲染器（如 MobileGlues 走 v2 配置），判定 1 就会命中。**
这正是社区规避模组存在的原因。

---

## 三、可让用户自定义的三个对象

按你的目标"让钠抓的特征尽量可被用户自定义"，可控项只有这三项：

| # | 对象 | 谁在设 | 能否自定义 |
|---|---|---|---|
| 1 | `POJAV_RENDERER` 环境变量 | v2 插件渲染器 | ✅ 可改名/清空 |
| 2 | `java.library.path` | 启动参数 | ✅ 可调整 |
| 3 | `user.home` | 系统属性 | ✅ 可改（且它本来就只警告） |
| — | `net.kdt.pojavlaunch` 正则 | **钠自己** | ❌ 我们改不了 |

**注意**：改成什么都不影响游戏本身 —— 这些只是**给钠看的标识**，
不参与渲染。所以自定义它们**没有副作用**，风险很低。

---

## 四、本轮为什么没直接改

1. 需要你定**放在哪个界面**（版本设置 / 全局设置 / 开发者模式内）
2. 需要你定**默认值**：保持原样（推荐，当前本就不命中），还是默认隐藏
3. 我不替你决定"要不要默认规避" —— 这是你的产品取向

确认后我再动手，改动面很小（环境变量覆写一处）。

---

## 五、未验证

以上是**源码文本**的结论，没有在我们的环境里跑 Sodium 实测过；
`POJAV_RENDERER` 实际由哪些插件渲染器设置，只看了代码注释，未逐个验证。
