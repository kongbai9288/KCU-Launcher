# 契约文件

玩家可改的配置文件。全部放公共目录，同名覆盖内置默认。

| 文件 | 位置 | 作用 |
|---|---|---|
| `native_load_order.json` | `config/` 或 `instances/<id>/` | .so 加载顺序 |
| `custom_renderer.json` | `config/` | 自带渲染器定义 |
| `launcher_rules.json` | `config/` | 按版本的启动约束 |
| `theme.json` | `config/` | 主题色与全屏 |
| `config.json` | `config/` | 启动器默认设置 |
| `menu_setting.json` | `config/` | 游戏内菜单 |
| `authlib_injector_server.json` | `config/` | 皮肤站列表 |
| `general_setting.properties` | `config/` | 设置默认值 |
| `manifest.json` | 根目录 | 供 KCU Migrator 读取的机器清单 |

契约格式沿用 root-S7 整合包版 `assets/app_config/` 的既有结构，改为"内置默认 + 公共目录覆盖"双来源。

## 覆盖优先级

```
instances/<id>/<file>   >   config/<file>   >   assets/app_config/<file>
```

## 通用规则

1. 文件不存在 → 用内置默认，不报错。
2. 解析失败 → 报出文件名与错误位置，用内置默认，不崩溃。
3. 字段缺失 → 该字段用默认值，其余照常解析。
4. 未知字段 → 忽略，不报错。
