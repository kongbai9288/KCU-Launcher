# 玩家自带原生库：双层目录（v0.17.0）

> 执行者：yuanbao，2026-10-21
> 这是用户做本项目的核心诉求之一：**上游把依赖锁在私有目录，迁移工具读不到**

---

## 1. API 终点追踪

改造前先追清楚 `.so` 究竟怎么被加载。

```
KCULauncher.launchProcess
  → setEnv(config, bridge, render)
      → addCommonEnv
          → envMap.put("LD_LIBRARY_PATH", getLibraryPath(...))
              → getLibraryPath → appendCommonPaths
                  → 【无条件】sb.append(KCUPath.MOD_RUNTIME_DIR).append(":")
```

实测 `KCULauncher.java:162`：

```java
sb.append(KCUPath.MOD_RUNTIME_DIR).append(split);
```

**这一行没有任何条件判断**——`MOD_RUNTIME_DIR` 恒定进入 `LD_LIBRARY_PATH`。

`MOD_RUNTIME_DIR = context.getDir("runtime_mod", 0)`，即**私有目录**，可被 `dlopen`。

### 终点结论

**只要把 .so 放进 `runtime_mod`，上游机制自动生效，不需要自己 `System.load()`。**

这条决定了实现方式：**零侵入**。不改任何已有加载逻辑，只往那个目录放文件。这天然满足"玩家不设置就完全等同上游"。

---

## 2. 双层目录模型

| 层 | 路径 | 属性 | 用途 |
|---|---|---|---|
| 素材层（公共） | `/sdcard/KCU/natives/<abi>/` | 可读写、noexec | 玩家/迁移工具放 .so |
| 加载层（私有） | `runtime_mod/` | 可执行 | 启动时同步过来 |

**为什么不能直接在公共目录加载**：`/sdcard` 挂载 noexec；Android 10+ 还有 W^X 约束——`getFilesDir()` 可写不可执行、`nativeLibraryDir` 可执行不可写。

`<abi>` 取值来自 `Architecture.archAsStringAndroid(getDeviceArchitecture())`：`arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86`。

---

## 3. 实现（KcuNativeSync.java）

新增 `com.kongbai.kculauncher.launcher.utils.KcuNativeSync`。

挂载点 `KCULauncher.launchProcess` 内、**`setEnv` 之前**：

```java
// 必须在 setEnv 之前，否则拼出的库搜索路径不含本次同步的文件
KcuNativeSync.sync();
setEnv(config, bridge, render);
```

流程：源目录不存在 → 直接返回；否则逐个 `.so` 校验 ELF magic → 按需复制 → 置只读 → 清理源已删除的旧文件。

---

## 4. 严格自我审核

### 4.1 会导致什么错误

| # | 错误 | 处置 |
|---|---|---|
| 1 | **覆盖只读文件失败**：上轮同步已 `setReadOnly()`，下次更新 .so 时 `FileOutputStream` 直接失败 → 玩家改了文件却永远不生效 | **已修**：copy 开头先 `setWritable(true)` |
| 2 | 非 ELF 文件（如误放 txt）被塞进库搜索路径，dlopen 报错 | **已防**：校验 ELF magic `7F 45 4C 46` |
| 3 | 同步失败阻断启动 | **已防**：整个 `sync()` 包 `catch (Throwable)` |
| 4 | ABI 目录不匹配（只放了别的架构） | 目录不存在 → 直接返回，无副作用 |
| 5 | 删除其他机制放入 `runtime_mod` 的文件 | **已防**：只清理 `.kcu_synced` 记录中、本次不在 kept 的条目 |
| 6 | 私有目录不存在 | 复制前 `mkdirs`（仅在确有源文件时才执行） |

**错误 1 是本轮审核抓出的真 bug**——写第一版时没考虑到只读标记会阻止后续覆盖。不修的话表现为"我明明换了 .so 怎么还是旧的"，且极难排查（静默失败）。

### 4.2 对今后使用逻辑的干扰

| # | 干扰 | 状态 |
|---|---|---|
| 1 | **ELF 只验 magic，不验架构/位数**。放错架构的 .so 会到运行时才 `dlopen` 失败 | **已知局限**，未做 ELF header 深度解析 |
| 2 | `.kcu_synced` 记录文件落在 `runtime_mod`，非 .so 无害 | 可接受 |
| 3 | 每次启动都比对 size+mtime，文件多时有轻微开销 | 可接受，仅在源目录存在时发生 |
| 4 | 上游若改变 `MOD_RUNTIME_DIR` 语义，此处受影响 | 已记录 |
| 5 | 加载顺序自定义（v0.3 规划的 `native_load_order.json`）**尚未实现** | 待做 |

### 4.3 零污染验证

玩家不放任何文件时：

- `getExternalDir()` 不是目录 → **立即返回**
- 不创建任何目录、不写任何文件
- `LD_LIBRARY_PATH` 与上游**逐字一致**（`MOD_RUNTIME_DIR` 本就无条件拼入）

---

## 5. 审核结论

**pass**

### 哈希回执

| 项 | 值 |
|---|---|
| 文件 | `FCL/src/main/java/com/kongbai/kculauncher/launcher/utils/KcuNativeSync.java` |
| 行号 | **114** |
| 该行原文 | `                dst.setWritable(true);` |
| 取出的 5 个不同字母 | `d` `s` `t` `e` `W` |
| 拼接结果 | `dsteW` |
| **SHA-1** | **`3c824fc7562f84cb6454c365d560fc7e0220457e`** |

取材行即修复错误 1 的那行——它是本轮最后落地的代码。

---

## 6. 编译验证

Actions run **39457783242**

| 步骤 | 结果 |
|---|---|
| Build release | success |
| 列出产物 | success |
| 验证签名 | success |

---

## 7. 状态与遗留

| 项 | 状态 |
|---|---|
| API 终点追踪 | ✅ 追到 `appendCommonPaths` 无条件拼入 |
| 严格自我审核 | ✅ pass |
| 编译 | ✅ PROVEN |
| **实机验证** | ❌ **未做** |

遗留：
- [ ] 实机放一个 .so 验证是否真的被加载
- [ ] `native_load_order.json` 加载顺序自定义
- [ ] ELF header 深度校验（架构/位数）
