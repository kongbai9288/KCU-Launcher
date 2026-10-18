# 合规红线

## 1. 屏幕内容抓取

屏幕内容属敏感个人信息。

1. 依《个人信息保护法》，处理敏感个人信息需取得**单独同意**，不能混入一揽子用户协议。
2. 中国软件行业协会团体标准（2025-04）：应在隐私政策中声明无障碍/截屏权限的使用目的、使用场景、数据处理方式，仅在声明范围内使用。
3. Android 官方：无障碍权限必须由用户在设置中手动开启，不能代开。
4. Google Play 政策：无障碍服务若非为残障人士设计禁止上架。本项目用 MediaProjection（非无障碍）并以 GitHub Release 分发，规避此条。

实现清单：

- [ ] 首次抓取前弹独立授权页，写明用途、留存时长、是否上传
- [ ] 提供随时可关的总开关
- [ ] 默认端侧识别，截图不出设备
- [ ] 不记录、不上传、不用于模型训练
- [ ] 隐私政策单列一节
- [ ] `FLAG_SECURE` 黑屏不绕过，直接提示

## 2. 正版与分发

5. 不绕过微软正版验证，不内置共享 token，不提供 cracked 模式。
6. 不内置、不分发 Minecraft 游戏文件/资源/库，全部由用户自己登录后下载。
7. 不使用 Mojang 官方美术作为主视觉。
8. 全项目 GPL-3.0（继承 FCL / ZL2）；Apache-2.0 组件（ReLinker / SoLoader / overlay-translator）可兼容引入。
9. 不参考、不移植任何含 Crack / 破解意图的分支。

## 3. 不绕过 Android 安全机制

10. 不为"让 .so 能在公共目录加载"去 root、改 SELinux 策略、hook linker。
11. 需要的系统能力一律走官方 API：MediaProjection、SAF / MANAGE_EXTERNAL_STORAGE、PackageManager 释放机制。
12. 不放宽 `targetSdk` 来规避行为变更。
