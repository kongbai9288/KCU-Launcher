# manifest.json

供 KCU Migrator 读取。放在 `/sdcard/KCU/manifest.json`，由启动器维护。

```json
{
  "schemaVersion": 1,
  "launcher": { "package": "com.kcu.launcher", "versionName": "0.1.0", "versionCode": 1 },
  "abi": "arm64-v8a",
  "paths": {
    "instances": "/sdcard/KCU/.minecraft",
    "natives":   "/sdcard/KCU/natives/arm64-v8a",
    "config":    "/sdcard/KCU/config",
    "scripts":   "/sdcard/KCU/scripts",
    "logs":      "/sdcard/KCU/logs",
    "control":   "/sdcard/KCU/control"
  },
  "runtimes": [
    { "name": "jre8",  "path": "...", "version": "8" }
  ],
  "instances": [
    { "id": "...", "version": "1.20.1", "loader": "fabric", "loaderVersion": "0.15.7" }
  ]
}
```

## 约定

1. Migrator 只读这个文件与 `paths` 指向的目录，不碰私有目录。
2. 启动器在实例增删改、运行时安装删除时重写本文件。
3. `schemaVersion` 变化时必须向后兼容，旧字段不删。
4. 写入用临时文件 + 原子 rename，避免读到半截。
