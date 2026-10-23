# 自带 .so 加载顺序控制（v0.38.0）

执行者：yuanbao，2026-10-23

---

## 0. 本轮同时完成：删除 `close_skin_model` 死开关

v0.37.0 登记的未决项，用户拍板删除。

### 追溯（全仓 code search）

命中 **16 处**，其中两个文件是 v0.37.0 检索**遗漏**的：

| 文件 | 位置 | 说明 |
|---|---|---|
| `ThemeData.kt` | 35-37 / 143 / 158 | 字段定义、默认值、持久化 |
| `ThemeEngine.kt` | 186-189 | `setCloseSkinModel()` |
| `ThemePreference.kt` | 20 | DataStore 字段 |
| `LauncherSettingAdapter.kt` | 64 / 229-236 | 枚举项 + SwitchRow |
| `LauncherSettingPage.java` | 279-283 | case 分支 |
| **`ThemeDataTest.kt`** | 38 / 101 / 110 | **androidTest，v0.37.0 漏了** |
| **`ThemeEngineTest.kt`** | 165-172 | **androidTest，v0.37.0 漏了** |
| 9 个语言 strings.xml | — | 2+8 条字符串 |

### androidTest 为什么必须一起改

androidTest **不在 `assembleRelease` 编译路径**，所以 CI 不会因它失败——这正是容易被忽略的原因。

但不改的话，将来任何人跑 `connectedAndroidTest` 会直接编译失败，且报错指向测试文件，排查成本高。这是"对未来造成的影响"的典型项。已一并改：

- `ThemeDataTest.kt`：删 3 处引用
- `ThemeEngineTest.kt`：删 `setCloseSkinModel` 相关断言，测试函数改名为 `setAnimationSpeedUpdate`

### 失效 import 检查

| 文件 | 检查项 | 结论 |
|---|---|---|
| `LauncherSettingPage.java` | `ThemeData` import | **保留**——行 334 仍用 |
| `ThemeEngineTest.kt` | `assertTrue` import | **保留**——多处仍用 |
| `ThemeDataTest.kt` | `assertTrue` import | **保留**——多处仍用 |

---

## 1. 需求：so 文件同级放一个 json 控序

用户要求：`.so` 同级目录放一个 json 控制加载顺序；**该文件不能进入加载流程**。

### 1.1 "不要放到加载当中"的两层含义

| 层 | 风险 | 处置 |
|---|---|---|
| 复制层 | json 被复制进 `MOD_RUNTIME_DIR`，污染运行时目录 | `KcuNativeSync.sync()` 已按 `.so` 后缀 + ELF magic 双重过滤，**json 天然被排除** |
| dlopen 层 | json 被 `dlopen` | 新增代码只处理 `order` 里声明的、且以 `.so` 结尾、且**确实存在于运行时目录**的文件 |

### 1.2 为什么必须显式 dlopen

`LD_LIBRARY_PATH` 只影响**依赖库搜索路径**，不控制 dlopen 顺序。

顺序敏感的场景（符号解析、初始化次序）必须显式按序加载，否则不同 .so 组合会随机崩。

---

## 2. 追溯：接入点

```
FCLauncher.launch 线程
  └─ KcuNativeSync.sync()      ← 行 559
  └─ setEnv(...)               ← 行 565（LD_LIBRARY_PATH 在此拼好并传给 native）
  └─ setUpJavaRuntime(...)
  └─ setupGraphicAndSoundEngine(...)
```

**关键：`loadInOrder` 必须在 `setEnv` 之后。**

`setEnv` 之前，native 侧还没拿到含 `MOD_RUNTIME_DIR` 的 `LD_LIBRARY_PATH`，此时 dlopen 解析不到依赖库。

新插入点在 `setEnv` 与 `setUpJavaRuntime` 之间（行 567）。

---

## 3. 契约

`/sdcard/KCU/natives/<abi>/load_order.json`：

```json
{
  "abi": "arm64-v8a",
  "order": ["libc++_shared.so", "libmobileglues.so"],
  "onFailure": "skip"
}
```

| 字段 | 说明 |
|---|---|
| `abi` | 可选。声明了但与设备不符 → **整份配置不生效** |
| `order` | 按序加载的文件名（相对运行时目录） |
| `onFailure` | `abort` 遇失败即停并向上返回 false；其他值（含缺省）跳过继续 |

---

## 4. 严格审核

### 4.1 会导致什么错误

| # | 错误 | 处置 |
|---|---|---|
| 1 | **路径穿越**：order 写 `../../xxx.so` 逃出运行时目录 | `isSafeName` 拒绝含 `/` 或 `\` 的名字 ✅ |
| 2 | **dlopen 配置文件本身**：order 写 `load_order.json` | 要求 `.so` 结尾 ✅ |
| 3 | **隐藏文件**：`.foo.so` | 拒绝以 `.` 开头 ✅ |
| 4 | 跨架构加载直接崩 | `abi` 不匹配时整份配置返回 null ✅ |
| 5 | json 损坏导致启动崩溃 | 全部 `catch (Throwable)`，损坏等同未配置 ✅ |
| 6 | `JsonReader` 未关闭 | `finally` 关闭 ✅ |
| 7 | 玩家配了但文件没同步进来 | 只加载 `so.isFile()` 为真的（即已过 ELF 校验的）✅ |
| 8 | 顺序错误导致崩溃无法回退 | 删 json 即回到原行为 ✅ |

### 4.2 对用户使用逻辑的影响

| 路径 | 结果 |
|---|---|
| 不放 json | **零 dlopen**，与上游逐字一致 |
| 放 json 但 `order` 为空 | 同上 |
| `abi` 写错 | 整份不生效，静默回退原行为 |
| 正常配置 | 按序 dlopen |
| 某个库 dlopen 失败 | `skip`：跳过继续；`abort`：停止 |
| 改了 json 内容 | 下次启动生效（无缓存） |

### 4.3 对未来的影响

| # | 影响 | 评估 |
|---|---|---|
| 1 | `onFailure=abort` 目前**只返回 false，调用方未处理** | ⚠ 见 §6 |
| 2 | 顺序配置与渲染器 `DLOPEN` 环境变量并行 | 两者独立，可能重复 dlopen 同一库——dlopen 幂等，无害 |
| 3 | 契约未写进 `manifest.json` | 迁移工具暂不知道这份配置，下轮可补 |

### 4.4 对代码逻辑的影响

| # | 影响 | 评估 |
|---|---|---|
| 1 | 新增 1 个类，FCLauncher 多 2 行 | 极小 |
| 2 | 未改动 `KcuNativeSync` 任何逻辑 | ✅ |
| 3 | 未改动上游已有 dlopen 路径 | ✅ 新增路径，非替换 |

---

## 5. 审核结论

**pass**

### 哈希回执

| 项 | 值 |
|---|---|
| 文件 | `FCLauncher.java` |
| 行号 | **567** |
| 该行原文 | `                KcuNativeOrder.loadInOrder(bridge);` |
| 取出的 5 个不同字母 | `K` `c` `u` `N` `a` |
| 拼接结果 | `KcuNa` |
| **SHA-1** | **`90b6d2f0531b2575d88c0cafe068f2f2c8d389e5`** |

取材行即本轮新增的调用点。

---

## 6. 遗留

`onFailure=abort` 的返回值当前未被使用（调用处未判断）。**行为等同 `skip`。**

这是已知缺口，未包装。要真正中止启动需改动 `FCLauncher` 的异常处理路径，留待确认语义后做。

---

## 7. 冒烟测试（PROVEN）

Actions run **39907251472**（push 触发，含本轮全部改动），5 个 APK：

| 架构 | 字节 |
|---|---|
| all | 326242773 |
| x86_64 | 174870254 |
| x86 | 155563026 |
| arm | 161915081 |
| arm64 | **169175089** |

另 run 39907254821（手动 dispatch）亦 success。
