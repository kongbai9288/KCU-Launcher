# 公共目录清单 manifest.json（v0.15.0）

> 执行者：yuanbao，2026-10-20
> 解决的核心问题：**迁移工具读不到启动器的实际目录布局**

---

## 1. 为什么需要它

用户的原始诉求：上游把依赖锁在私有目录，**迁移工具读不到相关目录**，这是要做 KCU Launcher 的根本原因。

外部工具若自行猜测路径，一旦根选择不同（`/sdcard/KCU` 或 `/sdcard/FCL`）就会对不上。所以由**启动器自己把布局写清楚**，外部工具只读不猜。

---

## 2. API 终点追踪（动手前必做）

改造前先追清楚两个被依赖的 API 最后落到哪里。

### 2.1 `Profiles.getProfiles()`

```
Profiles（Kotlin object，@JvmStatic val profiles）
  → Profile.name / gameDir / selectedVersion
  → Profile.gameDir setter → repository.changeDirectory(File)
  → KCUGameRepository.setBaseDirectory
  → 【终点】同样是文件系统路径
```

关键：`Profile.gameDir` 是**绝对路径**，反序列化时直接读入，不经过 `KCUPath`。所以实例目录与公共根**可以不同源**——这正是迁移工具需要清单的原因。

### 2.2 挂点追踪

```
SplashActivity.onCreate → init()
  → KCUPath.loadPaths(context)        // 路径就绪
  → initState()
  → enterLauncher() [Dispatchers.IO]
      → RendererManager.init / JavaManager.init / Controllers.init
      → ConfigHolder.init()            // Profiles 就绪
      → 【挂载点】KcuManifest.write()   // 两者都就绪
      → startActivity(MainActivity)
```

挂载在 `ConfigHolder.init()` **之后**、`startActivity` **之前**，且在 IO 线程——路径与实例都已可用，写文件不阻塞主线程。

---

## 3. 清单结构

写入 `<root>/manifest.json`：

```json
{
  "schemaVersion": 1,
  "generatedAt": 1771000000000,
  "packageName": "com.kcu.launcher",
  "root": "/sdcard/KCU",
  "paths": {
    "minecraft":        "/sdcard/KCU/.minecraft",
    "privateCommon":    "/sdcard/Android/data/.../files/.minecraft",
    "runtime":          "/data/user/0/.../app_runtime",
    "modRuntime":       "/data/user/0/.../app_runtime_mod",
    "java":             ".../runtime/java",
    "lwjgl":            ".../runtime/lwjgl",
    "log":              "/sdcard/KCU/log",
    "control":          "/sdcard/KCU/control",
    "share":            "/sdcard/KCU/share",
    "nativesExternal":  "/sdcard/KCU/natives",
    "nativeLibDir":     "/data/app/.../lib/arm64"
  },
  "instances": [
    { "name": "共享游戏目录", "gameDir": "/sdcard/KCU/.minecraft", "selectedVersion": "1.21.4" }
  ]
}
```

`nativesExternal` 是**玩家自带 .so 的素材目录**（可读写，外部工具可访问）。公共目录挂载 noexec，不能直接 `dlopen`，需校验后复制到私有 `runtime/natives` 才能加载——清单明确区分两者。

---

## 4. 严格自我审核

### 4.1 会导致什么错误

| # | 风险 | 处置 |
|---|---|---|
| 1 | **`Files.write` 要求 API 26，而 `minSdk` 正好 26** ——边界值，任何清单版本差异都可能翻车 | **已改**用 `FileOutputStream` 传统流 |
| 2 | 写入失败抛异常会**阻断启动** | **已包** `catch (Throwable)`，静默忽略 |
| 3 | `Profiles` 未初始化时调用会崩 | 挂点在 `ConfigHolder.init()` **之后**；且实例列表单独 try-catch，失败仍写路径部分 |
| 4 | `Profiles.getProfiles()` 并发修改 | 启动时单线程写入，风险低；已 try-catch 兜底 |
| 5 | 目录不存在导致写失败 | 写前 `mkdirs` |

### 4.2 对今后使用逻辑的干扰

| # | 干扰 | 状态 |
|---|---|---|
| 1 | **清单会过期**：只在启动时写，用户新建/删除实例后不更新，需重启才反映 | **已知局限**，含 `generatedAt` 供外部判断新鲜度 |
| 2 | 根为 `/sdcard/FCL` 时清单落在 `/sdcard/FCL/manifest.json`，迁移工具需扫两个候选位置 | **需在 Migrator 侧约定** |
| 3 | manifest 未含账号、Mod 列表、加载器信息 | 后续按需扩展 `schemaVersion` |
| 4 | 多进程：仅主进程 `SplashActivity` 写入，`:jvm` 不写 | 无竞争，但 `:jvm` 期间改实例不会反映 |

### 4.3 审核结论

**pass**

### 哈希回执

| 项 | 值 |
|---|---|
| 文件 | `FCL/src/main/java/com/kongbai/kculauncher/util/KcuManifest.java` |
| 行号 | **60** |
| 该行原文 | `                out.close();` |
| 取出的 5 个不同字母 | `o` `u` `t` `c` `l` |
| 拼接结果 | `outcl` |
| **SHA-1** | **`b8a7fbfbf3b40238f0c8a44a94ade8fc2be7bccd`** |

---

## 5. 编译验证

Actions run **39375975967**

| 步骤 | 结果 |
|---|---|
| Build release | success |
| 列出产物 | success |
| 验证签名 | success |

五架构全绿：`all 327462121 / x86_64 176088978 / arm64 170394403 / arm 163134501 / x86 156782954`

---

## 6. 状态

| 项 | 状态 |
|---|---|
| API 终点追踪 | ✅ |
| 严格自我审核 | ✅ pass |
| 编译 | ✅ PROVEN |
| **实机验证 manifest 是否真的生成** | ❌ **未做** |

---

## 7. 下一步

- [ ] 实机确认 `/sdcard/KCU/manifest.json` 是否生成、内容是否正确
- [ ] 实例变更时重写清单（当前需重启）
- [ ] 自定义 .so 双层目录落地（清单已预留 `nativesExternal` 字段）
- [ ] 与 KCU Migrator 约定：扫 `/sdcard/KCU/manifest.json` 与 `/sdcard/FCL/manifest.json` 两个候选
