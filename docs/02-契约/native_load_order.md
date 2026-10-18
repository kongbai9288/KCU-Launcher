# native_load_order.json

控制 .so 的加载顺序。不配置则完全不干预启动流程。

位置：`/sdcard/KCU/config/native_load_order.json`（全局）或 `/sdcard/KCU/instances/<id>/native_load_order.json`（实例级）

```json
{
  "schemaVersion": 1,
  "mode": "default",
  "abi": "arm64-v8a",
  "order": [
    { "file": "libc++_shared.so",   "required": true  },
    { "file": "libmobileglues.so",  "required": false }
  ],
  "onFailure": "abort"
}
```

## 字段

| 字段 | 值 | 行为 |
|---|---|---|
| `mode` | `default` | 不干预，直接走上游原生流程 |
| | `order` | 严格按 `order` 数组顺序加载 |
| | `auto` | 预留，本期不实现，读到按 `default` 处理并打日志 |
| `abi` | 架构名或省略 | 省略取当前设备架构；不匹配则整个文件忽略 |
| `order[].file` | 文件名 | 相对 `natives/<abi>/` |
| `order[].required` | bool | true 失败即中止；false 跳过并记日志 |
| `onFailure` | `abort` / `skip` / `fallback` | 中止 / 跳过继续 / 回退内置默认 |

## 走默认分支的条件（满足任一）

1. 文件不存在
2. `mode` 为 `default`
3. `order` 是空数组
4. `abi` 与当前设备不匹配
5. JSON 解析失败（同时报错）

**默认分支不得引入任何自定义层对象、回调、包装。** 代码路径必须与上游逐字一致。

## 为什么顺序会崩

- 动态链接器持有进程创建时的环境副本，native 代码改 `LD_LIBRARY_PATH` 无效（设计上的安全约束）
- 应用私有目录不在动态链接器搜索路径
- `RTLD_LOCAL` 自 API 23 起正确实现：先加载的库不会把符号暴露给后续 `dlopen` 的库（DT_NEEDED 引用除外）
- 依赖库搜索起点是加载它的那个 .so 所在目录，所以参与者必须同目录

典型错误：

```
java.lang.UnsatisfiedLinkError: dlopen failed: cannot locate symbol "xxx" referenced by "libA.so"
link_image[1995]: failed to link libA.so
```

## 加载日志

每一步记录序号、文件名、结果、`dlerror` 原文：

```
[KCU] native load  3/5  libmobileglues.so  FAIL  dlopen failed: cannot locate symbol "..."
```
