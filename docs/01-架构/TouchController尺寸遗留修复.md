# TouchController 尺寸遗留修复（v0.45.0）

执行者：yuanbao，2026-10-24

---

## 1. 承接

v0.44.0 遗留：`GameMenu:797-801` 把 `getScreenWidth()/getScreenHeight()` 传给
`TouchController` 构造与 `TouchControllerInputView.setSize()`，**窗口变化后不会更新**。

当时未修的原因是"TouchController 是否有 resize 接口未确认"。本轮读完实现后确认：
**它没有 resize 接口，但也不需要 —— 尺寸只用于归一化**。

---

## 2. 追溯：尺寸到底用来干什么

### 2.1 `TouchController`

```kotlin
private fun LauncherProxyClient.addPointer(...) =
    addPointer(pointerId, event.getX(index) / width, event.getY(index) / height)

client.moveView(true, deltaY / height, deltaX / width)
```

**只做分母**（把像素坐标归一化到 0~1 后发给游戏端）。构造参数声明为 `val width: Int`，
一旦构造完成即冻结。

### 2.2 `TouchControllerInputView`

```kotlin
private var width: Int = -1
fun setSize(width: Int, height: Int) { this.width = width; ... }
```

用于把 `cursorRect` / `inputAreaRect`（归一化 0~1）换算回像素
（`cursorRect.left * width` 等 8 处）。

---

## 3. 修复

两处都改为**动态 getter**，构造/传入值仅作兜底：

```kotlin
val width: Int get() = getScreenWidth().takeIf { it > 0 } ?: initialWidth
```

```kotlin
private val screenW: Int get() = getScreenWidth().takeIf { it > 0 } ?: fallbackWidth
```

**不需要任何外部 hook** —— 窗口变化后 `DisplayUtil` 被 `FCLActivity.onConfigurationChanged`
刷新，getter 下次调用自动拿到新值。

---

## 4. 真实编译失败一次，已修

run **40067197166** 五架构**全部失败**：

```
TouchControllerInputView.kt:71:28 Accidental override:
  The following declarations have the same JVM signature (getWidth()I)
```

**原因**：该类继承 `View`，Android 已有 `getWidth()`/`getHeight()`。
我自定义的 `private val width get() = ...` 编译后同样生成 `getWidth()`，JVM 签名冲突。

**修法**：属性改名 `screenW` / `screenH`，8 处使用点精确替换。

（日志取证是靠 v0.40.0 修的"上报步骤直接读 `/tmp/build.log`"拿到的，否则只能盲猜。）

---

## 5. 严格审核

### 5.1 会导致什么错误

| # | 错误 | 处置 |
|---|---|---|
| 1 | **自定义 width/height 与 View.getWidth() 冲突** → 五架构全编译失败 | 改名 screenW/screenH ✅ |
| 2 | 正则替换误伤 `cursorRect.width`（RectF 自身字段） | 用完整表达式精确替换 8 处，逐条 assert count==1 ✅ |
| 3 | `TouchController` 是否也冲突 | 它是普通类不继承 View，无冲突 ✅ |
| 4 | 构造参数改 `private val initialWidth` 是否破坏调用方 | 位置参数调用，签名不变 ✅ |
| 5 | `getScreenWidth()` 返回 0 时 | 兜底用构造初值，与原 -1 语义一致 ✅ |
| 6 | getter 在 onDraw 中每次调用 | 只读静态 int，开销可忽略 ✅ |

### 5.2 对用户使用逻辑的影响

| 路径 | 结果 |
|---|---|
| 小窗 → 全屏 | 触控归一化按新尺寸，指针/视角不再失控 |
| 光标与输入区矩形 | 按新尺寸换算 |
| 未装 TouchController mod | 完全不受影响 |

### 5.3 对未来的影响

| # | 影响 | 评估 |
|---|---|---|
| 1 | 尺寸快照类问题**已全量修完** | TouchPad/MenuView/ControlButton/ControlDirection/FCLInput/TouchController/InputView |
| 2 | 新增代码若再声明 `val width` on View 子类 | 会再次冲突，已写入规则 240 |

### 5.4 对代码逻辑的影响

| # | 影响 | 评估 |
|---|---|---|
| 1 | TouchController 构造参数名变（位置不变） | ✅ |
| 2 | 两处属性改 getter | 极小 |

---

## 6. 审核结论

**pass**

### 哈希回执（碰撞第四次，已换取材行）

第一次取 `TouchControllerInputView.kt:73`（`private val screenW...`）得到的
5 个字母是 **`priva`**，与 v0.19.0 `KcuDevMode.java:28` **完全相同**，SHA-1 同为
`28399ad1…`。原因是两行都以 `private` 开头。

按规则 193 改取 **`TouchController.kt:33`**（含 `val width` 特征）：

| 项 | 值 |
|---|---|
| 文件 | `TouchController.kt` |
| 行号 | **33** |
| 该行原文 | `    val width: Int get() = getScreenWidth().takeIf { it > 0 } ?: initialWidth` |
| 取出的 5 个不同字母 | `v` `a` `l` `w` `i` |
| 拼接结果 | `valwi` |
| **SHA-1** | **`0c816a93d5c6512f3a1a67a42d2421ba687d45b9`** |

---

## 7. 冒烟测试（PROVEN）

run **40067932184**（push 触发，全矩阵）**success**，5 个产物：

| 架构 | 字节 |
|---|---|
| all | 326243965 |
| x86_64 | 174872036 |
| arm64 | **169175968** |
| arm | 161916555 |
| x86 | 155564861 |

**未验证**：小窗切换行为（需实机）。
