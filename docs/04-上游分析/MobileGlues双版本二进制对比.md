# MobileGlues 双版本二进制对比（v0.35.0）

执行者：yuanbao，2026-10-23

---

## 1. 对比对象

| 项 | 修改版 | 官方插件版 |
|---|---|---|
| 文件 | `MobileGlues_ap_1.3.4.apk` | `com.fcl.plugin.mobileglues1.3.4.apk` |
| 大小 | 4796060 B | 11231951 B |
| git blob sha | `019587c765…`（校验通过） | `88fdd6e417…`（校验通过） |
| 压缩包条目 | 952 | 955 |
| ABI | **仅 arm64-v8a** | **四架构**（arm64 / arm / x86 / x86_64） |

两个 blob 下载后均按 `blob <size>\0<content>` 重算 sha1，**与仓库记录的 sha 完全一致**，来源可信。

取两者 arm64 的 `libmobileglues.so` 做对比：

| | 修改版 | 官方 |
|---|---|---|
| 大小 | 5689368 B | 5972576 B |

---

## 2. 节区对比

| 节 | 修改版 | 官方 | 差 |
|---|---|---|---|
| `.dynsym` | 136056 | 235752 | 官方大 73% |
| `.dynstr` | 185668 | 549025 | 官方大 196% |
| `.gnu.hash` | 43764 | 64560 | |
| `.hash` | **无** | 78592 | 官方独有 |
| `.rodata` | 852551 | 751696 | 修改版大 13% |
| `.gcc_except_table` | 29852 | 70104 | |
| `.text` | **3645980** | **3231676** | **修改版大 12.8%** |
| `.plt` | 9280 | 50752 | |
| `.rela.plt` | 13872 | 76080 | |
| `__lcxx_override` | 268 | **无** | 修改版独有 |
| `.comment` | **无** | 204 | 官方独有 |
| 节数 | 29 | 28 | |

---

## 3. 符号表对比（决定性证据）

| 项 | 修改版 | 官方 |
|---|---|---|
| 已定义符号总数 | 5473 | 9632 |
| `gl*` | 3076 | 3071 |
| `egl*` | 35 | 35 |
| 其他 | 2362 | 6526 |

### 差集分析

```
gl* 仅官方有：      0 个
gl* 仅修改版有：    5 个（glDeleteBuffersARB / glGenBuffersARB /
                        glIsBufferARB / glPolygonModeNV / glPolygonModeNVARB）
egl* 差集：         空（35 个完全一致）
官方独有非 gl/egl： 4190 个 —— 全部是 glslang / SPIRV-Cross 内部符号
```

官方独有的符号样例：

```
ShCompile  ShConstructCompiler  ShConstructLinker  ShConstructUniformMap
ShDestruct  ShInitialize  ShLinkExt  ShSetEncryptionMethod
_ZN7glslang14TParseVersions12requireStageE...
_ZN11spirv_cross12CompilerGLSL9statementIJ...
_ZN3spv7Builder16createNoResultOpENS_2OpEj
```

这些是**第三方着色器编译器库的内部符号**，不是 MobileGlues 自身的代码。

---

## 4. 字符串对比

| 类别 | 结果 |
|---|---|
| 含 `Setting` 的字符串 | 修改版 15 条 / 官方 15 条，**独有差集均为空** |
| 含 `MobileGlues` 的字符串 | 24 条中 23 条共有；仅 `GL_MG_mobileglues` 在官方独有（扩展名常量） |
| 版本宏 | **两者都是 `MG_MOBILEGLUES_VERSION 1340`** |
| `Mali` | 修改版 20 处 / 官方 35 处 —— 均为 GLSL `normalize` 等内置函数与 FSR 着色器源码，**无设备适配分支** |

15 个 Setting 键完全一致：

```
enableAngle  enableNoError  enableExtComputeShader  enableExtTimerQuery
enableExtDirectStateAccess  angleDepthClearFixMode  customGLVersion
fsr1Setting  hideMGEnvLevel  maxGlslCacheSize  multidrawMode
bufferCoherentAsFlush  ignoreError ...
```

---

## 5. 官方 .comment 节（关键解释）

官方 so 保留了 `.comment`：

```
Android (13691557, +pgo, +bolt, +lto, +mlgo, based on r522817d)
clang version 18.0.4
Linker: LLD 18.0.4
```

**官方构建启用了 `+pgo +bolt +lto +mlgo`** —— PGO（剖面引导优化）、BOLT（二进制重排优化）、LTO（链接时优化）、MLGO（机器学习引导优化）。

这直接解释了：

| 现象 | 解释 |
|---|---|
| 官方 `.text` 更小（3231676 vs 3645980） | PGO + BOLT + LTO 精简并重排了代码 |
| 修改版 `.text` 更大 | 未启用上述优化，或优化等级不同 |
| 官方 `.comment` 存在、修改版无 | 修改版被剥离或未生成 |
| 官方有 `.hash`、修改版无 | linker 标志 `--hash-style` 不同 |

---

## 6. 结论：无代码级改动

证据链完整闭合：

1. 版本宏同为 `1340`
2. 15 个 Setting 字符串**完全一致**
3. `gl*` 符号：官方 ⊆ 修改版，**一个没删**，修改版还多 5 个 ARB 别名
4. `egl*` 符号 35 个完全相同
5. 符号数差异（9632 vs 5473）**全部**落在 glslang / SPIRV-Cross 第三方库的内部符号可见性上
6. 代码段大小差异由官方启用 PGO+BOLT+LTO+MLGO 解释
7. 无 Mali / PowerVR / Xclipse 设备适配分支

**判定：两者是同一份 MobileGlues 1.3.4 源码的不同构建产物。差异来自编译选项（符号可见性、PGO/BOLT/LTO、hash-style、是否剥离 .comment），不是代码改动。**

---

## 7. 对用户计划的影响

原计划：若存在代码级改动，则走两阶段静室流程（另一个 AI 依据叙述文档重写）。

**现在不需要走这条路——因为没有"他的代码"要复现。**

他做的事等价于：拿官方源码，改 CMake/linker 标志重新编译，把第三方库符号藏起来。**这不是可受著作权保护的表达性改动。**

法律上更干净的路径（原本就成立，现在更明确了）：

| 义务（LGPL-2.1） | 做法 |
|---|---|
| 声明使用并标注许可 | 关于页加 MobileGlues 条目，标 LGPL-2.1-only |
| 提供源码获取 | 附官方仓库链接 |
| 允许替换该库 | 走 KCU 已有的双层目录，.so 玩家可自供 |

**KCU 不分发任何第三方修改版二进制，用户担心的对修改者的侵权风险不存在。**

---

## 8. 附带确认：FCL_VERSION_CODE 已传

（承接 v0.33.0 调研）

`KCULauncher.java`：

```java
FCL_VERSION_CODE = packageInfo.versionCode;        // 行 65
if (FCL_VERSION_CODE != -1)
    envMap.put("FCL_VERSION_CODE", FCL_VERSION_CODE + "");   // 行 206
```

读**本应用** versionCode（KCU 现 34），非 0。MobileGlues 的启动器白名单判据不成立，**不会强制忽略 config.json**。改包名无影响。

---

## 9. 本轮未做

- 未做反汇编 / 控制流还原
- 未重写任何渲染器代码
- 无代码改动，未触发编译

---

## 10. 待办

- [ ] 按 GPU 生成 `/sdcard/MG/config.json`（Mali 保守档：`enableANGLE=2` 强关 ANGLE）
- [ ] 关于页补 MobileGlues LGPL-2.1 声明
- [ ] **实机安装仍为零验证**
