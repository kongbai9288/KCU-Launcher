# 设计稿

两份样品已归档，不再只留在对话里。

| 文件 | 用途 |
|---|---|
| `移动端揉纸试卷UI.html` | 主界面骨架样品：64dp 左图标栏 7 项、中央舞台（空状态 / 公告卡 / 衣柜）、右侧 300dp 账号卡与版本卡、底部水印 |
| `桌面端v3.txt` | 组件与交互规范样品：10 个视图、三段式弹窗 `md-head / md-body / md-foot`、浅深色 token、左导航 9 项、可拖拽分隔条、窄屏收成 64px |

图标已在 `docs/assets/kcu_icon.png`，不重复归档。

## 全局 token（输出格式）

- 颜色：`#RRGGBB` / `#AARRGGBB`，命名 `kcu_*`；`values/colors.xml` 与 `values-night/colors.xml` 同名覆盖
- 尺寸：`kcu_sp_*`（4/8/12/16/24/40dp）、`kcu_r_*`（4/8/10dp），单位 dp
- drawable：`shape` + `corners/solid/stroke`，命名 `bg_kcu_*`，深浅靠引用 color，不另建文件
- 布局：新增 id 前缀 `kcu_`；**必须保留 `activity_main.xml` 全部现有 `R.id`**，只换皮不换 id，否则 binding 一启动就崩
- 文案：四份同步（`values` / `values-zh` / `values-zh-rHK` / `values-zh-rTW`），占位符 `%1$d`、`%1$s` 不丢、不带撇号
- 弹窗：标题固定、正文自适应、按钮行移出滚动区固定底部；验收口径是标题 + 全部选项 + 两个按钮同屏可见，无需滑动；超长文本走「展开全文」或独立全屏页

## 浅色 / 深色

| token | 浅 | 深 |
|---|---|---|
| paper | `#F1EFE8` | `#292A28` |
| paper_hi | `#FAF8F2` | `#323330` |
| ink | `#2C2C2A` | `#E9E7DE` |
| accent | `#3D6B66` | `#7FB8AF` |
| warn | `#B8543F` | `#D9755C` |

<!--✓-->

## 落地进度

| 轮次 | 内容 |
|---|---|
| 0.99.0 | token 全局化、两份样品归档、弹窗一次性显示完整、年龄周岁输入 |
| 0.99.1 | 移动端稿的中央舞台：双视图 tab、空状态、公告卡、衣柜 8 格、版本卡、水印；左右栏揉纸换皮 |

未落地：桌面端 v3 的 10 个视图与三段式向导弹窗。`activity_main.xml` 全部既有 `R.id` 在三栏换皮时原样保留。<!--✓-->
