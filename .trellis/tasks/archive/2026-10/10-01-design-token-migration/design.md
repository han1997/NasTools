# 技术设计 — Migrate design tokens into UI consumers

对应 `prd.md` 的 R1–R7。本文件只写技术设计，需求与验收见 `prd.md`。

---

## 1. 边界与不变量

**改动范围**：`app/src/main/java/com/nastools/app/presentation/**` 与
`app/src/test/java/com/nastools/app/presentation/**`。
`data/`、`domain/`、`service/`、`di/` **不动** —— 状态字符串是 `TaskEntity` 的持久化字段
（`data/database/entity/TaskEntity.kt:20`），本次不引入 `TaskStatus` 枚举，
避免牵出 Room 迁移。

**不变量**

1. 状态字符串的**字面量取值集合不变**：`waiting`/`running`/`paused`/`completed`/`failed`/`cancelled`。
   改动只发生在「如何渲染」，不发生在「如何存储」。
2. `TaskManager` 的状态机不变（`pause` → `paused`，`resume` → `waiting`，
   `cancel` → `cancelled`）。
3. 既有交互能力不变：每个状态可用的操作菜单项保持与 `TasksScreen.kt:372-388` 一致。

---

## 2. 状态呈现层：单一来源

### 2.1 新文件 `presentation/tasks/TaskStatusUi.kt`

```kotlin
package com.nastools.app.presentation.tasks

import com.nastools.app.presentation.theme.NasStatusTone

/** 状态中文标签。全仓唯一来源（原 TaskDetailScreen/TasksScreen 各一份）。 */
fun String.statusLabel(): String = when (this) {
    "waiting"   -> "等待"
    "running"   -> "运行中"
    "paused"    -> "已暂停"
    "completed" -> "完成"
    "failed"    -> "失败"
    "cancelled" -> "已取消"
    else        -> this
}

/** 状态 → 语义色档位。映射表见 prd.md R5。 */
fun String.statusTone(): NasStatusTone = when (this) {
    "running"   -> NasStatusTone.Progress
    "paused"    -> NasStatusTone.Warning
    "completed" -> NasStatusTone.Success
    "failed"    -> NasStatusTone.Danger
    "waiting", "cancelled" -> NasStatusTone.Neutral
    else        -> NasStatusTone.Neutral
}
```

**关键约束：本文件不得 import 任何 `androidx.compose.*`。**
`statusTone()` 的返回类型 `NasStatusTone` 是 `StatusColors.kt` 中的**纯枚举**
（无 Compose 超类），因此该文件可以在 JVM 单元测试中直接加载 —— 这是 R7 单元测试
能跑在 `app/src/test/`（而非需要设备的 `androidTest/`）的前提。

> ⚠️ 待实施时验证（见 `implement.md` 的 R-1 风险项）：
> `NasStatusTone` 与 `nasStatusColors()`(@Composable)、`NasStatusColors`(含 `Color` 字段)
> 同处 `StatusColors.kt`。Kotlin 会将其编译为独立 class，加载枚举理论上不触发
> `Color` 解析。若单元测试因 classpath 报 `NoClassDefFoundError`，
> 退化方案是把 `NasStatusTone` 拆到独立文件 `StatusTone.kt`（仍是纯 Kotlin）。

### 2.2 `NasStatusBadge` 签名变更

`AppChrome.kt:278`

```kotlin
// before
fun NasStatusBadge(text: String, positive: Boolean, modifier: Modifier = Modifier)

// after
fun NasStatusBadge(text: String, tone: NasStatusTone, modifier: Modifier = Modifier)
```

`AppChrome.kt` 需 import `nasStatusColors` 与 `NasStatusTone`。
函数体内部改为：

```kotlin
val colors = nasStatusColors(tone)
Surface(
    color = colors.container.copy(alpha = 0.76f),
    contentColor = colors.content,
    border = BorderStroke(1.dp, colors.content.copy(alpha = 0.16f)),
    shadowElevation = NasElevation.flat,        // 见 §4
    shape = NasShape.Badge,                     // CircleShape，值不变
    ...
)
```

`alpha = 0.76f` 与 `0.16f` 的现有取值**保留**（属 `Color.copy` 的透明度参数，非 dp 口径）。

### 2.3 调用点改写（5 处）

| 位置 | 现状 | 改为 |
|---|---|---|
| `TaskDetailScreen.kt:144` | `positive = task.status !in setOf("failed","cancelled")` | `tone = task.status.statusTone()` |
| `TasksScreen.kt:332` | 同上 | 同上 |
| `SettingsScreen.kt:76` | `positive = hasPermission` | `tone = if (hasPermission) NasStatusTone.Success else NasStatusTone.Danger` |
| `SettingsScreen.kt:89` | `positive = isIgnoring` | `tone = if (isIgnoring) NasStatusTone.Success else NasStatusTone.Danger` |

（`SettingsScreen` 两处按 `prd.md` R6 注记保持现状红/绿语义。）

同时删除两处私有 `statusLabel()`（`TaskDetailScreen.kt:399-404`、
`TasksScreen.kt:403-408`），改为 import 新文件。

> `TaskDetailScreen.kt:308` 的 `if (task.status in setOf("failed","cancelled"))`
> 不属本次映射范围 —— 它控制「显示错误/警告信息块」，是行为分叉而非配色分叉，**保留**。
> 同理 `:163`、`TasksViewModel.kt:30-32`、`TasksScreen.kt:272,313,319,373-388`。

---

## 3. 间距与形状接入

### 3.1 形状

- 删除 `AppChrome.kt:52` 的 `val NasCardShape = RoundedCornerShape(8.dp)`。
- 13 处引用改为 `NasShape.Card`（14dp），并清理
  `BrowserScreen.kt:71`、`HomeScreen.kt:26`、`PresetListScreen.kt:49`、
  `SettingsScreen.kt:13`、`TasksScreen.kt:25`、`TaskDetailScreen` 的
  `import com.nastools.app.presentation.components.NasCardShape`。
- `NasShapes`（M3 阶梯）已在 `Theme.kt` 接入，`AlertDialog` 经 `Shapes.extraLarge`
  自动变为 28dp，**无需逐处改动对话框**。

### 3.2 间距

按 `prd.md` R3 三类分治。归并规则（离阶梯的间距类 → 最近阶梯）：

| 现状值 | 归并到 | 典型位置 |
|---|---|---|
| `6.dp` | `NasSpacing.sm` (8) | `AppChrome.kt:184` `Spacer(Modifier.height(6.dp))` |
| `10.dp` | `NasSpacing.sm` (8) | `AppChrome.kt:305` 徽章内边距 |
| `14.dp` | `NasSpacing.md` (12) | `ConfigEditScreen.kt:119`、`HomeScreen.kt:135,245,323` |
| `18.dp` | `NasSpacing.lg` (16) | `AppChrome.kt:192` `Spacer(Modifier.height(18.dp))` |
| `28.dp` | `NasSpacing.xl` (24) | `AppChrome.kt:162` `padding(horizontal = 28.dp)` |

**就近归并一律向下取（6→8 除外，6 更接近 4，但 8 与相邻 10/12 更协调）：**
实施时若某处归并会明显改变布局（如同一行内两个间距归并后趋同），
在 `implement.md` 的核对清单中标注并保留原值 —— 不为了「替换干净」破坏布局意图。

**保留字面量（R3 第 3 类）**，实施时不得替换：

| 类别 | 位置 | 为何不替换 |
|---|---|---|
| 描边宽 | `AppChrome.kt:172,222,262,293`、`TaskDetailScreen.kt:227,329`（均 1dp）、`TasksScreen.kt:298`（2dp，选中态） | `BorderStroke` 的线宽，不是间距 |
| 图标尺寸 | `AppChrome.kt:180`（34）、`AppChrome.kt:274`（22）、`HomeScreen.kt:184`（18）、`TaskDetailScreen.kt:236,280`（20）、`TaskDetailScreen.kt:317,331,372`（18） | `Modifier.size()` 定义图标自身 |
| 图标容器内衬 | `AppChrome.kt:179`（18）、`AppChrome.kt:274`（9） | 定义圆形容器直径：34+18×2=70dp、22+9×2=40dp，替换即改变容器大小 |
| 骨架条尺寸 | `HomeScreen.kt:332,339`（20 / 14）、`HomeScreen.kt:324`（40） | 骨架占位块的高度/尺寸 |
| 进度条厚度 | `TaskDetailScreen.kt:191`（6）、`TasksScreen.kt:347`（5） | 组件固有厚度 |
| 网格列宽 | `BrowserScreen.kt:303`（`GridCells.Adaptive(132.dp)`） | 自适应网格的最小列宽 |
| 骨架布局对齐宽 | `HomeScreen.kt:344`（`Spacer(Modifier.width(48.dp))`） | 见下方说明 |

**关于 `HomeScreen.kt:344`**：该 Spacer 位于 `ConfigCardSkeleton` 内，作用是让骨架行与真实
`ConfigCard` 的尾部区域对齐。**注意真实卡片的尾部元素是 `Icon(Icons.Default.ChevronRight)`，
不是 `IconButton`** —— 此处 48dp 是布局对齐宽度，**不是**触摸目标宽度，
故不适用 `NasSpacing.minTouchTarget`。它也不在 Spacing 阶梯上，
按规则 2 归并会把 48 压到 32，使骨架明显错位。故保留字面量。

### 3.3 列表底部内边距

`HomeScreen.kt:188`、`HomeScreen.kt:278`、`PresetListScreen.kt:137`
三处 `PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp)`
统一替换为 `NasListPaddingWithFab`（`Spacing.kt:44`，bottom = 88dp）。
按 R4，**接受 96 → 88 的 8dp 收窄**。

---

## 4. 高度：扩展 `NasElevation`

### 4.1 缺口

`NasElevation` 现仅 flat 0 / raised 1 / overlay 6，无法表达实际用到的 4、8、12。

### 4.2 方案

```kotlin
object NasElevation {
    val flat     = 0.dp    // 卡片/徽章：靠 1dp 描边建立层次
    val raised   = 1.dp    // 贴地元素
    val resting  = 2.dp    // 图标容器未选中态（值为现状，非重新设计）
    val overlay  = 6.dp    // 浮层、空态图标容器
    val floating = 8.dp    // FAB 常态；图标容器选中态
    val lifted   = 12.dp   // FAB 按下
}
```

> `resting = 2.dp` 是唯一一个**仅为消除字面量而存在**的档位 ——
> `NasIconContainer`（`AppChrome.kt:243`）的未选中态用它。
> 该点不在本次评审过的重设计范围内，故**保值为 2dp、零视觉变化**，
> 不套用「卡片走 flat + 描边」的规则（图标容器是选中态指示器，与卡片不同类）。

### 4.3 映射与视觉影响

| 位置 | 现状 | 改为 | 视觉 |
|---|---|---|---|
| `AppChrome.kt:208-215` `nasCardElevation()` default | 4dp | `flat` | **变化**：13 处卡片去掉静止阴影 |
| … pressed | 8dp | `overlay` | 变化：8 → 6 |
| … focused / hovered | 6dp | `raised` | 变化：6 → 1 |
| `AppChrome.kt:301` `NasStatusBadge` shadow | 4dp | `flat` | **变化**：徽章去掉阴影 |
| `AppChrome.kt:171` `NasEmptyState` shadow | 6dp | `overlay` | 不变（仅命名） |
| `AppChrome.kt:335` `NasMiniFab` default / pressed | 8 / 12 | `floating` / `lifted` | 不变（仅命名） |
| `AppChrome.kt:243` `NasIconContainer` selected / unselected | 8 / 2 | `floating` / `resting` | 不变（仅命名） |
| `HomeScreen.kt:132` shadow | 1dp | `raised` | 不变（仅命名） |

### 4.4 依据与取舍

`Elevation.kt` 注释已定调：「卡片走 flat + 1dp 描边而不是阴影：深色模式下阴影几乎不可见，
描边才是跨主题都成立的层次手段」。而 `nasCardBorder()`（`AppChrome.kt:218`）**已存在** ——
卡片目前同时有 4dp 阴影和 1dp 描边，即描边机制早已就位，只差把阴影摘掉。

**取舍**：卡片静止态由「阴影 + 描边」双手段变为「仅描边」单手段。
在深色模式下这是净改善（阴影本就近乎不可见）；浅色模式下层次感变弱，
改由描边承担 —— 这正是 R1「有意重设计」所接受的方向。
交互反馈（press/hover）保留，只是幅度调整为 token 阶梯内的值。

**此方案需在 1.4 评审闸门确认**（`prd.md` 声明为 `design.md` 待确认项）。

---

## 5. `Theme.kt` 注释订正

`StatusColors.kt:31` 现注释：

> 不能用 `isSystemInDarkTheme()` —— 那是系统设置，而用户可以显式覆盖成浅色或深色，两者会不一致。

按 R2，本次**不引入**用户覆盖能力，该注释的前提不成立。改为：

> 当前主题由 `NasToolsTheme` 的 `darkTheme` 参数决定（跟随系统）。
> 此处不能用 `isSystemInDarkTheme()` —— 它读的是系统设置，
> 与 theme 实际采用的值可能不一致（例如预览中显式传参）。

同时确认保留 `Theme.kt:78-81` 的安全 cast：`(view.context as? Activity)?.window ?: return@SideEffect`
—— 这是 R7 中 `@Preview` 能正常渲染的前提（预览的 `context` 不是 `Activity`）。

---

## 6. 预览基建（R7）

新增 `presentation/tasks/TaskStatusPreview.kt`（或就近置于各页面文件末尾）：
每页面一对 `@Preview`，用 `NasToolsTheme(darkTheme = false/true)` 显式包裹：

```kotlin
@Preview(name = "浅色", showBackground = true)
@Composable
private fun TasksScreenLightPreview() {
    NasToolsTheme(darkTheme = false) { TasksScreen(/* 假数据 */) }
}

@Preview(name = "深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun TasksScreenDarkPreview() {
    NasToolsTheme(darkTheme = true) { TasksScreen(/* 假数据 */) }
}
```

**约束**

- `darkTheme` **必须显式传参**，不可依赖 `isSystemInDarkTheme()` —— 后者在预览中
  跟随 IDE 设置而非预览声明（`StatusColors.kt:31` 注释所述的正是这个陷阱）。
- 预览需要假数据。各 Screen 的入参形态不同（部分依赖 ViewModel / `context`），
  实施时按页面实际签名决定：能直接传参的直接传，依赖 ViewModel 的抽一个
  `@Composable` 预览专用重载或渲染其无状态子组件 —— **不得为预览改动生产签名**。
- 覆盖 6 个页面：TasksScreen、TaskDetailScreen、HomeScreen、SettingsScreen、
  BrowserScreen、PresetListScreen（`NasStatusBadge` 所在的两个 tasks 页面必须覆盖）。
- 另加一条**六态对照预览**：同一 `@Preview` 内纵排六个 `NasStatusBadge`，
  便于一眼确认六个状态两两可区分（直接对应 AC1/AC2）。

---

## 7. 兼容性与回滚

**兼容性**

- 无数据库迁移、无网络协议变更、无对外接口变更。
- 唯一持久化相关风险：无 —— 状态字符串取值集合未变（§1 不变量 1）。
- 深色模式上线后用户首次可能看到与以往不同的界面，属 R2 已接受的取舍。

**回滚**

- 全部改动集中在 `presentation/`，`git revert` 单次提交即可整体回退。
- 深色模式可单独回退：把 `Theme.kt:73` 改回 `darkTheme: Boolean = false`，
  不影响其余 token 接入。
- `NasStatusBadge` 签名变更会波及 4 个调用点，回滚需一并回退 ——
  故实施时 §2.2 与 §2.3 应在**同一次编辑**内完成，不留中间态。

**提交边界**

- 提交前须确认 `.agents/`、`.codex/`、`.opencode/`、`.trellis/scripts/` 约 130 个
  工具链改动**未被纳入**（`prd.md` Out of Scope）。实施时用
  `git add app/ .trellis/tasks/10-01-design-token-migration/` 显式限定路径，
  不使用 `git add -A`。
