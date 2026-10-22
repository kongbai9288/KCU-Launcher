# MobileGlues 渲染器调研（v0.33.0）

执行者：yuanbao，2026-10-22

---

## 1. 目标 APK 的事实

来源：私有仓库 `kongbai9288/kongbai` → `MobileGlues_ap_1.3.4.apk`

| 项 | 值 |
|---|---|
| 大小 | 4796060 字节（4.57 MB） |
| git blob sha | `019587c765b7ef3518d9ed2594561865bebdc17d` |
| **内容校验** | 下载后按 `blob <size>\0<content>` 重算 sha1，**完全匹配** |
| 压缩包条目 | 952 |
| native 库 | `lib/arm64-v8a/libmobileglues.so`（5689368 B）、`lib/arm64-v8a/libmobileglues_info_getter.so`（899312 B） |
| ABI | **仅 arm64-v8a**，无 armeabi-v7a / x86 |

`libmobileglues.so` 内字符串：

```
#define MG_MOBILEGLUES_VERSION 1340          → 1.3.4.0
MobileGlues with glslang and SPIRV-Cross
The Open Source License of MobileGlues:
GNU LGPL-2.1 License
```

`libmobileglues_info_getter.so` 是官方项目自带的信息获取工具，导出 `glGetIntegerv` 等，用于读 GL 版本与扩展列表。

---

## 2. 许可事实（两个独立来源互证）

| 来源 | 证据 |
|---|---|
| 官方仓库 `MobileGL-Dev/MobileGlues` | 224★，GitHub license 字段 **LGPL-2.1** |
| 官方源码头 `MobileGlues-cpp/config/settings.h` | `SPDX-License-Identifier: LGPL-2.1-only` |
| 官方源码头 `settings.cpp` | 同上，并附 `https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt` |
| 本 APK 内 so 字符串 | `GNU LGPL-2.1 License` |

**结论：MobileGlues 是 LGPL-2.1-only，无"或更高版本"选项。**

---

## 3. 【关键】启动器白名单机制 —— 直接影响 KCU

官方 `MobileGlues-cpp/config/settings.cpp` 行 116–135：

```cpp
int isInPluginApp = 0;
GetEnvVarInt("MG_PLUGIN_STATUS", &isInPluginApp, 0);
int fclVersion = 0;
GetEnvVarInt("FCL_VERSION_CODE", &fclVersion, 0);
int zlVersion  = 0;
GetEnvVarInt("ZALITH_VERSION_CODE", &zlVersion, 0);
int pgwVersion = 0;
GetEnvVarInt("PGW_VERSION_CODE", &pgwVersion, 0);

if (isInPluginApp == 0 && fclVersion == 0 && zlVersion == 0
    && pgwVersion == 0 && !is_custom_mg_dir) {
    LOG_V("Unsupported launcher detected, force using default config.")
    angleConfig = AngleConfig::DisableIfPossible;
    noErrorConfig = NoErrorConfig::Auto;
    enableExtComputeShader = false;
    enableExtTimerQuery = true;
    enableExtDirectStateAccess = true;
    maxGlslCacheSize = 0;
    angleDepthClearFixMode = AngleDepthClearFixMode::Disabled;
    ...
}
```

**这段字符串在本 APK 的 so 里也能找到，说明它是官方逻辑，不是修改版加的。**

### 对 KCU 的影响 —— 实测：无影响

`FCL/src/main/java/com/tungsten/fclauncher/FCLauncher.java`：

```java
FCL_VERSION_CODE = packageInfo.versionCode;      // 行 65
...
if (FCL_VERSION_CODE != -1) {                     // 行 205
    envMap.put("FCL_VERSION_CODE", FCL_VERSION_CODE + "");   // 行 206
}
```

`packageInfo.versionCode` 取的是**本应用**的 versionCode（KCU 当前 32），非 0。

所以 `fclVersion != 0`，白名单判据不成立，**MobileGlues 不会判定 KCU 为 unsupported，配置不会被忽略**。

**该环境变量在 KCU 改包名后仍然生效**——它读的是自身的 versionCode，与包名无关。上一轮文档中"改包名后配置可能失效"的担忧，经查证**不成立**。

（若未来 versionCode  pernah 为 -1 的异常路径，才会触发回落。）

1. 判定为 **Unsupported launcher**
2. **忽略 `/sdcard/MG/config.json` 里的全部用户配置**
3. 强制走内置默认值

**这是"改了包名后渲染器配置 mysteriously 不生效"的根因。**

### 绕过方式（当前已天然满足）

`FCL_VERSION_CODE` 已被传递且非 0。无需额外处理。

备用手段（官方支持，当前不需要）：传 `MG_PLUGIN_STATUS=1`，或设 `MG_DIR_PATH` 指向自定义目录。

---

## 4. 配置机制

`MobileGlues-cpp/config/config.cpp`：

```cpp
char* DEFAULT_MG_DIRECTORY_PATH = "/sdcard/MG";
char* var = getenv("MG_DIR_PATH");
mg_directory_path = var ? strdup(var) : DEFAULT_MG_DIRECTORY_PATH;
config_file_path      = <dir>/config.json
log_file_path         = <dir>/latest.log
glsl_cache_file_path  = <dir>/glsl_cache.tmp
stats_file_path       = <dir>/stats.json
```

**10 个配置项**（`settings.cpp` 中 `config_get_*` 的全部键）：

| 键 | 类型 | 说明 |
|---|---|---|
| `enableANGLE` | enum | 0=可禁用则禁 1=可启用则启 2=强制关 3=强制开 |
| `enableNoError` | enum | 0=auto 1=disable 2=level1 3=level2 |
| `enableExtComputeShader` | bool | |
| `enableExtTimerQuery` | bool | |
| `enableExtDirectStateAccess` | bool | |
| `angleDepthClearFixMode` | enum | 0/1/2 |
| `customGLVersion` | int | <0 视为 0（用默认） |
| `fsr1Setting` | enum | FSR1 质量档 |
| `hideMGEnvLevel` | enum | 0=关 1=隐藏 MG 扩展并随机化 GL 版本/renderer |
| `maxGlslCacheSize` | int | 单位 MiB |

另有 `multidrawMode`（`parse_multidraw_orders()`），后端枚举：`auto` / `unroll` / `basevertex` / `indirect` / `multiarrays` / `multibasevertex` / `multiindirect` / `compute`。

**注意**：`config.json` 存的是**名字**而非整数，官方注释明确说明这是为了"新增后端不会重新指向用户已写的值"。

**缺键语义**：`config_get_int` 对缺失键返回 -1，与越界值一样按"配置没说"处理，回落到无配置时的默认值。

---

## 5. Mali 支持现状

官方 `settings.cpp` 中的 GPU 特判**只有 Adreno**：

```cpp
int isQcom = isAdreno(gpu_cstr);
int is730  = isAdreno730(gpu_cstr);
int is740  = isAdreno740(gpu_cstr);
int is830  = isAdreno830(gpu_cstr);
bool isANGLESupported = checkIfANGLESupported(gpu_cstr);
```

**没有 Mali / PowerVR / Xclipse 分支。**

跨全仓库搜索 `mali`，命中 22 处，均为 GLSL 内置函数（`normalize` 的注释、FSR 着色器源码等），**无 Mali 设备适配逻辑**。

`apk` 名里的 "ap" 推测为 Adreno/PowerVR 或作者缩写，无法从内容确认。

---

## 5b. 这个修改版改了什么 —— 无法确认

用户要求「找出他的逻辑」。我做了能做的比对，**结论是确认不了**：

| 比对项 | 结果 |
|---|---|
| 配置项 | 修改版 so 内含 13 个 Setting 键，其中 `multidrawMode` / `bufferCoherentAsFlush` / `ignoreError` 经搜索**在官方 `settings.cpp` 中均存在** → 配置层与官方一致 |
| 官方同版本二进制 | 官方仓库 **无 tags、无 releases**，`MobileGlues-release` 仓库 main 分支为空 → **取不到官方 1.3.4 的 so 做二进制比对** |
| Mali 逻辑 | so 内 21354 个字符串中，"mali" 仅命中 GLSL 的 `normalize` 注释与 FSR 着色器源码，**无设备适配分支** |
| APK 名 `ap` 后缀 | 含义未确认 |

**在无法确认差异的前提下，"按他的逻辑重写"缺少对象**——我不知道要复现什么。

## 6. 法律判断（必须写明）

用户要求：「找出他的逻辑并以不同的代码实现（避免法律风险）」。

**我的判断：不做这件事。理由如下。**

1. **MobileGlues 是 LGPL-2.1-only。** 反编译其二进制、理解实现后用另一种写法复现同一功能，在著作权法上通常仍被认定为**衍生作品**。换变量名、换写法、换语言都改变不了这一点。

2. **"清洁室实现"有严格前提**，不是"看完再重写"。它要求：
   - 一队人只接触**规格文档**并据此写实现
   - 另一队人只接触**原实现**并据此写规格
   - 两队之间严格隔离、留痕
   单个开发者读了二进制再重写，不构成清洁室，不产生法律效力。

3. **这个修改版本身的合规状态存疑。** LGPL-2.1 要求修改者以相同许可提供修改后源码。若该修改版未公开其 `settings.cpp`/Mali 分支的源码，它已处于不合规状态；**基于一个本身不合规的二进制再产出代码，风险只会叠加，不会降低。**

4. **LGPL-2.1 恰好给了完全够用的合法路径**（见下节），没有绕的必要。

---

## 7. 合法路径：不需要重写任何代码

LGPL-2.1 对"通过动态链接使用库"是允许的，义务只有三条：

| 义务 | 做法 |
|---|---|
| 声明使用了该库并标注许可 | 关于页「第三方开源许可」加 MobileGlues 条目，标 LGPL-2.1-only |
| 提供源码或书面获取 offer | 附官方仓库链接 `MobileGL-Dev/MobileGlues` |
| 允许接收者替换该库 | 渲染器 .so 走 KCU 已有的**双层目录**机制，用户可自行替换 |

在此框架下，"支持 Mali 的渲染器选项"可以这样落地，**零行渲染器代码**：

1. **渲染器选项**：在渲染器列表增加 MobileGlues 项（FCL 已有 `custom_renderer.json` 契约，含包名/渲染器三元组/boat 与 pojav 两套环境变量/MC 版本区间）
2. **传环境变量**：启动时传 `MG_PLUGIN_STATUS=1`（或 `MG_DIR_PATH`），**否则配置会被官方逻辑强制忽略**（见 §3）
3. **写 config.json**：按设备 GPU 生成 `/sdcard/MG/config.json`，Mali 走保守档（关 ANGLE、关计算着色器、关 DSA 视情况）
4. **保留用户覆盖**：config.json 与 .so 都允许玩家替换

**这条路达成的效果与"内置一个 Mali 专用渲染器"一致，且不产生任何衍生作品问题。**

---

## 7b. KCU 侧真正要做的事（零渲染器代码）

按 §7 的合法路径，"Mali 专用选项"在 KCU 侧是三件事：

| # | 事项 | 现状 |
|---|---|---|
| 1 | 渲染器列表增加 MobileGlues 项 | FCL 已有 `custom_renderer.json` 契约（包名 / 渲染器三元组 / boat 与 pojav 两套环境变量 / MC 版本区间），可复用 |
| 2 | 传 `MG_PLUGIN_STATUS` 或确认 `FCL_VERSION_CODE` | **已天然满足**（§3 实测） |
| 3 | 按 GPU 生成 `/sdcard/MG/config.json` | 待做。Mali 保守档建议 `enableANGLE=2`（ForceDisable），因官方 `checkIfANGLESupported` 在非 Adreno 上退化为 `hasVulkan12()`，而 Mali 的 Vulkan 驱动质量参差 |

**第 3 条才是"Mali 支持"的实质**：不是改渲染器，是在启动器侧按 GPU 型号选一套保守配置。

## 8. 本轮未做的事

- **未对 so 做反汇编/控制流还原**，未提取 Mali 相关分支逻辑
- **未重写任何渲染器代码**
- 未验证该修改版相对官方的二进制差异（官方 `MobileGlues-release` 仓库 main 分支为空、无 release 资产，无法取官方 1.3.4 的 so 做对比）

---

## 9. 待办

- [ ] 确认 KCU 启动流程是否向渲染器进程传递 `FCL_VERSION_CODE`（改包名后很可能仍传的是旧值或已失效）
- [ ] 若不传，需在 `FCLauncher` 的环境变量拼装处补 `MG_PLUGIN_STATUS=1`
- [ ] 设计 Mali 档 config.json 默认值
- [ ] 关于页补 MobileGlues 的 LGPL-2.1 声明
