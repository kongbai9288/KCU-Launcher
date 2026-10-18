# 构建与 CI

## 1. 上游基线

| 项 | FCL 实测值 |
|---|---|
| compileSdk / minSdk / targetSdk | 35 / 26 / 34 |
| AGP | 8.13.2 |
| Kotlin | 2.4.10 |
| NDK | 27.0.12077973 |
| JDK | 17（source/target） |
| 混淆 | 关闭（release 与 debug 均 `isMinifyEnabled = false`） |
| 签名 | v1 关，v2 + v3 开 |
| 打包 | `jniLibs.useLegacyPackaging = true` |

NDK 统一 27：ZL2 用 25.2.9519653，混用冲突。

## 2. 密钥

**不写死，不落明文。**

| 用途 | 环境变量 | 未设置时的行为 |
|---|---|---|
| 签名库密码 | `KCU_KEYSTORE_PASSWORD` | 走 debug key 构建，产物标记 unsigned |
| CurseForge Key | `CURSE_API_KEY` | 构建照常；运行时自动走国内镜像 |
| OAuth Key | `OAUTH_API_KEY` | 构建照常；相关功能禁用 |

读不到就是空字符串，不允许 `!!` 或 `error()` 让构建失败。

## 3. CurseForge Key 缺省行为

上游逻辑：

```
resValue("string", "curse_api_key", curseApiKey.toString())
```

`curseApiKey` 为空时写入字符串 `"null"`，运行时靠 `!apiKey.equals("null")` 判断源是否可用。

本项目改为：

1. 构建期：Key 为空 → 写入空字符串，构建继续。
2. 运行期：Key 为空 → 不注册 CurseForge 官方源，改为 MCIM 镜像源。
3. UI：商店页标注当前源，可在设置里手动填 Key 后切换。
4. 填了 Key 但无效（401/403）→ 提示并自动回落镜像。

## 4. ABI

四架构全打：`armeabi-v7a` / `arm64-v8a` / `x86` / `x86_64`。

CI 矩阵与上游一致（`-Darch=all|arm|arm64|x86|x86_64`）。全打意味着：

- x86 / x86_64 的 natives substitution 必须正确（上游 Copper fork 修过这个 bug）
- 任一架构目录缺某个 .so 会导致该架构设备直接崩（64 位全有或全无规则）

## 5. 产物命名

`KCU-<buildType>-<versionName>-<abi>.apk`

## 6. 沙盒限制

当前开发沙盒只能访问 `api.github.com`，无法整体 clone 仓库（tarball 会 302 到 codeload），也没有 Android SDK/NDK。**沙盒内无法编译 APK。** 编译与实机验证由 GitHub Actions 或本地承担。
