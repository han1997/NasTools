# 执行计划 — Migrate design tokens into UI consumers

对应 `prd.md`（R1–R7、AC1–AC16）与 `design.md`（§1–§7）。
按顺序执行；标 `[gate]` 处需停下确认。

---

## 0. 前置与基线

- [ ] `0.1` 确认工作区状态，记录基线：
  ```bash
  git status --short -- app/
  ```
  预期：`theme/Color.kt`、`theme/Theme.kt` 已修改；
  `theme/{Elevation,Shapes,Spacing,StatusColors}.kt` 未跟踪（上一轮地基）。

- [ ] `0.2` 确认基线编译通过（**联网**，`--offline` 会因 aapt2 未缓存失败）：
  ```bash
  ./gradlew :app:compileDebugKotlin
  ```
  预期：`EXIT=0`。若基线即失败，先停下报告，不要在此基础上叠加改动。

- [ ] `0.3` 提交边界预检（`design.md` §7）：确认后续所有 `git add` 均显式限定
  `app/` 与 `.trellis/tasks/10-01-design-token-migration/`，
  **不使用 `git add -A`** —— 工作区另有约 130 个 Trellis 工具链改动待用户单独处理。

---

## 1. 状态呈现层（R5 / R6 / AC1–AC4、AC12）

> ⚠️ `1.1`–`1.3` 必须**在同一次编辑内完成**，不留 `NasStatusBadge` 签名与调用点
> 不匹配的中间态（`design.md` §7 回滚说明）。

- [ ] `1.1` 新建 `app/src/main/java/com/nastools/app/presentation/tasks/TaskStatusUi.kt`：
  `statusLabel()` + `statusTone()`。**不得 import 任何 `androidx.compose.*`**
  （`design.md` §2.1 硬约束，是 JVM 单测能跑的前提）。

- [ ] `1.2` 改 `AppChrome.kt:278` `NasStatusBadge`：`positive: Boolean` → `tone: NasStatusTone`，
  函数体改用 `nasStatusColors(tone)`，`shadowElevation` 暂保持 `4.dp`
  （在步骤 3 统一改 `NasElevation.flat`，避免此处提前引入未扩展的 token）。

- [ ] `1.3` 改写 4 个调用点（`design.md` §2.3 表）：
  `TaskDetailScreen.kt:144`、`TasksScreen.kt:332`（→ `task.status.statusTone()`）、
  `SettingsScreen.kt:76`、`SettingsScreen.kt:89`（→ 显式 `Success`/`Danger`，保持现状红绿）。

- [ ] `1.4` 删除两份私有 `statusLabel()`：
  `TaskDetailScreen.kt:399-404`、`TasksScreen.kt:403-408`，改为 import 新文件。

- [ ] `1.5` 自检：
  ```bash
  # AC4：positive 参数应无残留
  grep -rn "positive" app/src/main/java/com/nastools/app --include=*.kt
  # AC12：statusLabel 定义应只剩一处
  grep -rn "fun String.statusLabel" app/src/main/java/com/nastools/app --include=*.kt
  ```
  预期：第一条无输出或仅剩无关命中；第二条恰好 1 处。

- [ ] `1.6` 新建单元测试
  `app/src/test/java/com/nastools/app/presentation/tasks/TaskStatusUiTest.kt`：
  断言六个状态各自的 `statusTone()`（AC3），并断言六态 tone 不全部相同（AC1 回归防线）。

- [ ] `1.7` 运行单测（AC3）：
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
  **若因 `NoClassDefFoundError`（Compose `Color` 类缺失）失败** → 走退化方案：
  把 `NasStatusTone` 从 `StatusColors.kt` 拆到独立文件 `StatusTone.kt`（纯 Kotlin），
  重跑。这是 `design.md` 已预告的唯一分支（风险项 `R-1`）。

---

## 2. `NasElevation` 扩展（R1 / AC9）

- [ ] `2.1` 扩 `Elevation.kt` 为 6 档（`design.md` §4.2）：新增 `resting = 2.dp`、
  `floating = 8.dp`、`lifted = 12.dp`，保留 `flat`/`raised`/`overlay` 原名与值。

- [ ] `2.2` 改写 5 处（`design.md` §4.3 表）：
  `AppChrome.kt:208-215`（`nasCardElevation()` 四态 → `flat`/`overlay`/`raised`/`raised`）、
  `AppChrome.kt:301`（`NasStatusBadge` → `flat`）、
  `AppChrome.kt:171`（`NasEmptyState` → `overlay`）、
  `AppChrome.kt:335`（`NasMiniFab` → `floating`/`lifted`）、
  `AppChrome.kt:243`（`NasIconContainer` → `floating`/`resting`）、
  `HomeScreen.kt:132`（→ `raised`）。

- [ ] `2.3` 自检（**模式需宽于单行赋值** —— `levation = <digit>` 会漏掉
  `targetValue = if (selected) 8.dp else 2.dp` 这类形式，曾造成假阴性）：
  ```bash
  grep -rnE "(Elevation|elevation)[^=]*= *[^N][^a-zA-Z]*[0-9]+\.dp" \
    app/src/main/java --include=*.kt | grep -v "/theme/"
  ```
  预期：无输出（AC9 —— 所有高度引用均已是 token）。

---

## 3. 形状接入（R1 / AC6）

- [ ] `3.1` 删除 `AppChrome.kt:52` 的 `NasCardShape`，同时清理该文件内
  `RoundedCornerShape` 的相关 import（若 `CircleShape` 仍被用则保留）。

- [ ] `3.2` 13 处 `shape = NasCardShape` → `NasShape.Card`，并改各自 import。
  涉及文件：`BrowserScreen.kt`(2)、`HomeScreen.kt`(3)、`PresetListScreen.kt`(1)、
  `SettingsScreen.kt`(2)、`TaskDetailScreen.kt`(4)、`TasksScreen.kt`(1)。

- [ ] `3.3` 自检：
  ```bash
  grep -rn "NasCardShape" app/src --include=*.kt
  ```
  预期：无输出（AC6）。

---

## 4. 间距接入（R3 / R4 / AC7、AC8）

- [ ] `4.1` 按 `design.md` §3.2 逐文件替换阶梯内的间距类 dp（102 处出现）：
  优先 `TaskDetailScreen`(29) → `HomeScreen`(22) → `AppChrome`(21) →
  `SettingsScreen`(11) → `TasksScreen`(10) → `BrowserScreen`(8) →
  `PresetEditScreen`(3) → `ConfigEditScreen`(3) → `PresetListScreen`(2)。

- [ ] `4.2` 按 `design.md` §3.2 归并表处理离阶梯的间距类（6/10/14/18/28dp）。
  **每处归并前确认不会破坏布局意图**；若会，保留原值并在 `4.5` 清单中记录原因。

- [ ] `4.3` 替换三个列表页底部内边距为 `NasListPaddingWithFab`：
  `HomeScreen.kt:188`、`HomeScreen.kt:278`、`PresetListScreen.kt:137`（AC8，接受 96 → 88）。

- [ ] `4.4` **不得**替换 `design.md` §3.2 末段列出的组件固有尺寸
  （图标 `size()`、骨架条 `height(20/14.dp)`、`BorderStroke(1.dp, …)`）。

- [ ] `4.5` 自检并产出核对清单：
  ```bash
  grep -rn "[0-9]\+\.dp" app/src/main/java/com/nastools/app --include=*.kt \
    | grep -v "/theme/" | grep -v "\.size(" | grep -v "BorderStroke"
  ```
  预期：仅剩 R3 第 3 类。**逐条确认剩余项均属固有尺寸，把结论记入本步输出**（AC7 要求可逐条说明）。

---

## 5. 注释订正与预览基建（R2 / R7 / AC11、AC13、AC15）

- [ ] `5.1` 订正 `StatusColors.kt:31` 关于「用户可覆盖」的注释（`design.md` §5）。

- [ ] `5.2` 确认保留 `Theme.kt:78-81` 安全 cast 与 `Theme.kt:73` 的
  `isSystemInDarkTheme()`（AC11）。若已满足，本步仅核对、不改动。

- [ ] `5.3` 加 `@Preview`（`design.md` §6）：6 个页面 × 明/暗两组，
  外加一条六态对照预览。`darkTheme` **必须显式传参**。
  不得为预览改动生产签名。

- [ ] `5.4` 编译验证预览代码路径：
  ```bash
  ./gradlew :app:compileDebugKotlin
  ```
  预期：`EXIT=0`（AC14）。

---

## 6. 全量核对 `[gate]`

- [ ] `6.1` 逐条走 `prd.md` AC1–AC15，确认可验证项全部成立。

- [ ] `6.2` 剩余裸 dp 清单复核：确认每一项都属 R3 第 3 类且已记录理由。

- [ ] `6.3` 提交边界复核（`design.md` §7）：
  ```bash
  git status --short | grep -v "^ M \.agents\|^ M \.codex\|^ M \.opencode\|^ M \.trellis/scripts"
  ```
  预期：仅 `app/` 与本任务目录下的改动。

- [ ] `6.4` **停下，交由用户目视终验**（AC16）。本机无 adb / 模拟器，
  无法代跑。请用户在 Android Studio 中查看 12 组 `@Preview` 并/或安装 debug 包，
  重点确认：
  1. `waiting`/`paused`/`completed` 三态颜色确实两两可分（AC1）；
  2. `failed` 与 `cancelled` 不再同色（AC2）；
  3. 卡片圆角 8 → 14dp、对话框圆角 → 28dp 观感可接受（R1）；
  4. 卡片去掉阴影后层次是否仍成立（`design.md` §4.4 取舍）；
  5. 深色模式下六个页面无对比度问题（首次上线的深色 UI）。

- [ ] `6.5` 用户确认后，进入 Phase 3：`trellis-check` → spec 更新 → 提交。

---

## 验证命令汇总

| 目的 | 命令 | 环境约束 |
|---|---|---|
| 编译 | `./gradlew :app:compileDebugKotlin` | 需联网 |
| 单元测试 | `./gradlew :app:testDebugUnitTest` | 需联网 |
| 出包（备选） | `./gradlew :app:assembleDebug` | 需联网 |
| 状态参数残留 | `grep -rn "positive" app/src/main/java --include=*.kt` | — |
| statusLabel 单一定义 | `grep -rn "fun String.statusLabel" app/src/main/java --include=*.kt` | — |
| NasCardShape 残留 | `grep -rn "NasCardShape" app/src --include=*.kt` | — |
| 高度字面量残留 | `grep -rnE "(Elevation\|elevation)[^=]*= *[^N][^a-zA-Z]*[0-9]+\.dp" app/src/main/java --include=*.kt \| grep -v /theme/` | 模式须覆盖整行任意形式 |
| 裸 dp 残留 | `grep -rn "[0-9]\+\.dp" app/src/main/java --include=*.kt \| grep -v /theme/` | — |

---

## 风险与回滚点

| ID | 风险 | 触发条件 | 应对 |
|---|---|---|---|
| `R-1` | JVM 单测无法加载 `NasStatusTone` | `testDebugUnitTest` 报 `NoClassDefFoundError` | 把枚举拆到独立文件 `StatusTone.kt`（`design.md` §2.1 已预告） |
| `R-2` | `NasStatusBadge` 签名与调用点不同步 | 编辑中途编译 | `1.1`–`1.3` 同批完成；出错则整体回退该批 |
| `R-3` | 归并间距破坏布局 | `4.2` 中某处归并后同行间距趋同 | 保留原值 + 在 `4.5` 记录，不强行替换 |
| `R-4` | 预览因 `context` 非 Activity 崩溃 | 预览渲染 | 已由 `Theme.kt` 安全 cast 覆盖（AC11）；若仍崩，检查是否绕过 `NasToolsTheme` |
| `R-5` | 误提交工具链改动 | `git add -A` | 全程显式限定路径（`0.3`、`6.3` 双重检查） |
| `R-6` | 深色 UI 首次上线观感不可接受 | `6.4` 目视 | 单独回退 `Theme.kt:73` 一行，不影响 token 接入（`design.md` §7） |

**回滚点**：全部改动在 `presentation/` 内，单次提交可整体 `git revert`。
步骤 1、2、3、4 相互独立，可分别回退。
