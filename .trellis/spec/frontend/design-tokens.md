# 设计 Token 使用规范

本文档定义本项目的设计 token 体系及其使用契约。所有 Compose UI 代码在写样式前应先读本文。

来源：任务 `10-01-design-token-migration`。相关：`./compose-optimization.md`（remember 依赖键）。

---

## Token 清单

全部定义在 `app/src/main/java/com/nastools/app/presentation/theme/`。

| 家族 | 文件 | 成员 |
|---|---|---|
| 间距 | `Spacing.kt` | `NasSpacing.xxs/xs/sm/md/lg/xl/xxl` = 2/4/8/12/16/24/32dp；语义别名 `screenH` `listTop` `listBottom` `listBottomWithFab` `cardPadding` `sectionGap` `minTouchTarget`；`NasListPadding` / `NasListPaddingWithFab` |
| 形状 | `Shapes.kt` | `NasShapes`（M3 阶梯，接入 `MaterialTheme.shapes`）；`NasShape.Card`(14dp) `NasShape.Field`(10dp) `NasShape.Badge`(CircleShape) |
| 高度 | `Elevation.kt` | `NasElevation.flat`(0) `raised`(1) `resting`(2) `overlay`(6) `floating`(8) `lifted`(12) |
| 状态色 | `StatusColors.kt` | `NasStatusTone` 枚举、`nasStatusColors(tone)`、`LocalNasDarkTheme` |
| 颜色 | `Color.kt` | M3 角色色 + 语义状态色明暗各一套 |

---

## 契约 1：裸 dp 字面量的三分法

**这是本规范最容易做错的一条。** 不要按值一刀切替换，按用途分三类：

| 类别 | 处置 | 判据 |
|---|---|---|
| ① 间距，且值在阶梯上（2/4/8/12/16/24/32） | 替换为 `NasSpacing.*` | 是布局间隙 |
| ② 间距，但值不在阶梯上 | 就近归并到最近阶梯 | 是布局间隙 |
| ③ 组件固有尺寸 | **保留字面量** | 定义组件自身的大小，不是间隙 |

**类别 ③ 的完整清单**（不得替换）：

- `Modifier.size(...)` —— 图标、头像等自身尺寸
- 骨架占位块的 `height()` / `size()`
- `BorderStroke(1.dp / 2.dp, …)` —— 描边线宽
- 进度条 `height(5.dp / 6.dp)` —— 组件固有厚度
- `GridCells.Adaptive(132.dp)` —— 自适应网格最小列宽
- **图标容器内衬 `padding`** —— 它与 `size()` 共同定义圆形容器直径（如 34+18×2=70dp），替换即改变容器大小
- **骨架布局对齐宽** —— 如 `Spacer(Modifier.width(48.dp))` 用于让骨架行与真实卡片尾部对齐。注意它**不是**触摸目标宽度，即便数值同为 48 也不该用 `NasSpacing.minTouchTarget`

```kotlin
// ✅ 正确（类别 ①）
Spacer(Modifier.height(NasSpacing.md))
Column(modifier = Modifier.padding(NasSpacing.cardPadding))

// ✅ 正确（类别 ③：保留字面量）
Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
Surface(modifier = Modifier.height(20.dp), ...)   // 骨架条
border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

// ❌ 错误：把图标尺寸硬套间距语义名
Icon(Icons.Default.Add, null, modifier = Modifier.size(NasSpacing.lg))
```

**为什么**：类别 ③ 的数值与设计系统无关。给它们造语义名会把「图标多大」抬高成设计决策，
让 token 表膨胀到组件细节层，下次调图标时反而要走设计评审。

**验证**（提交前跑，应只剩类别 ③）：

```bash
grep -rn "[0-9]\+\.dp" app/src/main/java/com/nastools/app --include=*.kt | grep -v "/theme/"
```

> **Warning**：验证时必须用**宽于单行赋值**的模式。`grep "levation = [0-9]"` 会漏掉
> `targetValue = if (selected) 8.dp else 2.dp` 这类跨行表达，产生「0 命中」的**假阴性**。
> 高度类校验请用：
> ```bash
> grep -rnE "(Elevation|elevation)[^=]*= *[^N][^a-zA-Z]*[0-9]+\.dp" \
>   app/src/main/java --include=*.kt | grep -v "/theme/"
> ```

---

## 契约 2：任务状态的语义色

**单一来源**：`presentation/tasks/TaskStatusUi.kt`。该文件**不得 import 任何 `androidx.compose.*`**
—— 这是它的映射函数能在 JVM 单元测试（`app/src/test/`）中直接加载的前提。

```kotlin
fun String.statusLabel(): String   // 六个状态的中文标签
fun String.statusTone(): NasStatusTone
```

映射契约（改动即破坏用户对状态的辨识，须同步改测试）：

| status | 标签 | tone | 理由 |
|---|---|---|---|
| `waiting` | 等待 | `Neutral` | 排队态；`resume()` 亦回落到此 |
| `running` | 运行中 | `Progress` | 复用 M3 `primary` |
| `paused` | 已暂停 | `Warning` | 唯一「不干预就不推进」的状态 |
| `completed` | 完成 | `Success` | |
| `failed` | 失败 | `Danger` | 复用 M3 `error` |
| `cancelled` | 已取消 | `Neutral` | 用户主动终止的终态，非故障 |

**已知取舍**：`waiting` 与 `cancelled` 同为中性色。二者处于生命周期两端且操作菜单不同，可接受。

### 禁止：用 Boolean 表达多状态

```kotlin
// ❌ 错误 —— 两个分支把三个状态压成同一色
fun NasStatusBadge(text: String, positive: Boolean)

NasStatusBadge(
    text = task.status.statusLabel(),
    positive = task.status !in setOf("failed", "cancelled")
)

// ✅ 正确 —— 档位数由语义决定
fun NasStatusBadge(text: String, tone: NasStatusTone)

NasStatusBadge(text = task.status.statusLabel(), tone = task.status.statusTone())
```

**为什么禁止**：`positive: Boolean` 只有两个分支。当状态有六个、且业务语义需要区分
「等待 / 暂停 / 完成」时，调用方被迫把三个状态压进同一个 `true` 分支，
界面上完全不可区分 —— 这正是本任务修复的缺陷。

**判据**：当一个呈现函数的取值维度超过 2 且未来可能增长时，用枚举而非 Boolean。

### 非任务状态的徽章

`NasStatusBadge` 也被用于权限状态（设置页「已授权/未授权」「已忽略/优化中」）。
这类调用**显式传 tone**，不走 `statusTone()`：已授权/已忽略 → `Success`，
未授权/优化中 → `Danger`。勿把任务状态语义强加到权限语义上。

---

## 契约 3：Compose 预览

每个被改动的页面都应提供**浅色/深色各一组** `@Preview`。

```kotlin
@Preview(name = "浅色", showBackground = true)
@Composable
private fun FooLightPreview() {
    NasToolsTheme(darkTheme = false) { FooScreen(/* 假数据 */) }
}

@Preview(name = "深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun FooDarkPreview() {
    NasToolsTheme(darkTheme = true) { FooScreen(/* 假数据 */) }
}
```

**必须显式传 `darkTheme`**，不可依赖 `isSystemInDarkTheme()` —— 后者在预览中跟随
IDE 设置而非预览声明，会导致深色预览渲染成浅色。

**不得为预览改动生产签名。** 依赖 ViewModel 的页面，预览其无状态子组件，或抽一个
`@Composable` 预览专用重载，而不是给生产函数加默认参数。

> **Warning**：预览里调用 `LocalContext.current` 的 Android 系统服务时要留意。
> 例如 `PermissionHelper.isIgnoringBatteryOptimization()` 内部做
> `context.getSystemService(POWER_SERVICE) as PowerManager`，在预览（layoutlib）下可能抛异常。
> 遇到此类情况，用安全转换（`as? PowerManager ?: return false`）而非绕过预览。

**主题与系统设置的关系**：`NasToolsTheme` 的 `darkTheme` 参数是主题的**唯一真源**。
组件内一律读 `LocalNasDarkTheme`，不要读 `isSystemInDarkTheme()` —— 前者与 theme 实际
采用的值一致，后者只反映系统设置，二者在预览和未来可能的用户覆盖场景下会分叉。

---

## 契约 4：Token 值与现状值冲突时的裁决

当 token 的既有值与代码现状不一致（例：`NasSpacing.listBottomWithFab` 为 88dp，
而三处列表页实际写 `96.dp`），**一律以 token 值为准**，并在提交说明中记录该视觉变化。

**为什么**：token 的值是按新设计意图算出来的（88 = SmallFAB 40 + 边距 16 + 呼吸 32），
写法是照抄历史遗留。默默取现状会让 token 体系退化成「给常量起个名」，
失去集中调整的能力。

**例外**：若某处不在本次评审过的重设计范围内（如仅为一个孤立用法新增的档位），
则**保值**，只做命名替换、零视觉变化 —— 并在 token 上注明「值为现状，非重新设计」。

---

## 测试要求

`statusTone()` 的映射**必须有单元测试**，断言点包括：

1. 六个状态各自映射到 `prd` 约定的 tone；
2. `waiting` / `paused` / `completed` 三者 tone **互不相等**（防回归到本次修复的缺陷）；
3. `failed` 与 `cancelled` **不相等**。

测试放在 `app/src/test/java/com/nastools/app/presentation/tasks/`，纯 JVM，
无需设备。参考 `TaskStatusUiTest`。

**不得**写只断言「函数不抛异常」的空测试 —— 它无法阻止上述任一回归。
