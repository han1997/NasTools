# Migrate design tokens into UI consumers

## Goal

把上一轮铺好但**零消费**的设计 token 接入实际 UI，让三件事同时成立：

1. **状态可区分** —— 修掉「等待中/已暂停/已完成三者同绿」的真实缺陷；
2. **样式可集中修改** —— 间距、圆角、高度改由语义 token 承载，不再散落为字面量；
3. **深色模式真正上线** —— `DarkColorScheme` 从死代码变为可达路径。

用户价值：任务列表一眼能看出哪些在跑、哪些停了要处理、哪些失败了；深色模式用户首次获得可用的深色界面；后续调整视觉只需改 `theme/` 一处。

---

## Background

### 已有地基（工作区未提交）

| 文件 | 状态 | 内容 |
|---|---|---|
| `app/.../presentation/theme/Elevation.kt` | 新增 | `NasElevation`：flat 0 / raised 1 / overlay 6 |
| `app/.../presentation/theme/Shapes.kt` | 新增 | `NasShapes`（M3 阶梯 6/10/14/20/28dp）+ `NasShape`（Card 14 / Field 10 / Badge Circle） |
| `app/.../presentation/theme/Spacing.kt` | 新增 | `NasSpacing`（xxs…xxl + screenH/listTop/listBottom/listBottomWithFab/cardPadding/sectionGap/minTouchTarget）+ `NasListPadding` / `NasListPaddingWithFab` |
| `app/.../presentation/theme/StatusColors.kt` | 新增 | `NasStatusTone` 枚举、`NasStatusColors`、`nasStatusColors()`、`LocalNasDarkTheme` |
| `app/.../presentation/theme/Color.kt` | 修改 | 新增 6 组语义状态色（Neutral/Success/Warning × 明暗） |
| `app/.../presentation/theme/Theme.kt` | 修改 | `darkTheme` 默认 `false` → `isSystemInDarkTheme()`；`view.context as Activity` → 安全 cast；`AppShapes` → `NasShapes` |

### 确认事实（均已查证）

**规模与分布**

- `app/src/main/java` 下 56 个 `.kt`、7051 行。
- 裸 dp 字面量（`theme/` 之外）：**158 处出现 / 109 行**，分布为
  TaskDetailScreen 29 行、HomeScreen 22、AppChrome 21、SettingsScreen 11、
  TasksScreen 10、BrowserScreen 8、PresetEditScreen 3、ConfigEditScreen 3、PresetListScreen 2。
  其中 **102 处出现**正好落在 `NasSpacing` 阶梯（2/4/8/12/16/24/32）上。
- `theme/` 之外**没有任何裸 `Color(0x…)`** —— 颜色早已走 M3 角色，状态色是唯一缺口。
- `NasSpacing` / `NasShape` / `NasElevation` / `NasStatusTone` / `nasStatusColors()`
  在 `theme/` 目录外**零引用**。

**形状冲突**

- `AppChrome.kt:52` 定义 `NasCardShape = RoundedCornerShape(8.dp)`，
  被 **13 处、6 个页面**引用（BrowserScreen 2、HomeScreen 3、PresetListScreen 1、
  SettingsScreen 2、TaskDetailScreen 4、TasksScreen 1）。
- 新增的 `NasShape.Card = RoundedCornerShape(14.dp)` 与之值不一致。
- M3 `Shapes.extraLarge` 会被 `AlertDialog` 默认读取（`Shapes.kt` 注释指明），
  旧值 8dp → 新值 28dp。

**状态缺陷**

- 状态词表：`waiting` / `running` / `paused` / `completed` / `failed` / `cancelled`
  （`data/database/entity/TaskEntity.kt:20`）。
- `NasStatusBadge(text, positive: Boolean)` 位于 `AppChrome.kt:278`，调用点 4 处：
  `SettingsScreen.kt:76`、`SettingsScreen.kt:89`、
  `TaskDetailScreen.kt:144`、`TasksScreen.kt:332`。
- 后两处传 `positive = task.status !in setOf("failed", "cancelled")`，
  导致 `waiting`、`paused`、`completed` 渲染成**完全相同的绿色**。
- `paused` 仅在 `TaskManager.kt:84` 的 `pause()` 中赋值，且只由 UI 菜单触发
  （`TasksScreen.kt:372-388`）—— 全仓**无任何自动暂停**（网络中断、电池优化均不触发）。
- `resume()` 把状态回落到 `waiting`（`TaskManager.kt:90`），故 `waiting` 是排队态。
- 重复实现：`statusLabel()` 在 `TaskDetailScreen.kt:399-404` 与 `TasksScreen.kt:403-408`
  **各定义一份**（两处均为私有同名扩展函数）。

**深色模式现状**

- `NasToolsTheme` 全仓仅 `MainActivity.kt:20` 一个调用点且**不传参**，旧默认值 `false`
  —— 线上**永远走浅色**。`DarkColorScheme`（`Theme.kt:20-68`）与 Color.kt 新增的
  12 组 `Dark*` 状态色**从未在任何设备上渲染过**。
- 无任何持久化层：全仓无 `DataStore` / `SharedPreferences`。
- `SettingsScreen` 仅「通知权限 / 电池优化 / 版本」三项，无主题或动效开关。
- `LocalNasDarkTheme`（`StatusColors.kt:31`）注释称
  「用户可显式覆盖成浅色或深色」—— 但该覆盖能力**当前并不存在**，注释前提为空。

**高度 token 缺口**

- `NasElevation` 仅 3 档（flat 0 / raised 1 / overlay 6），实际用到
  1、4、6、8、12 dp，其中 **4 和 8 才是最常用的**：
  - `AppChrome.kt:208-215` `nasCardElevation()`：default 4 / pressed 8 / focused 6 / hovered 6
    —— 这是全部 13 处卡片的**唯一入口**；
  - `AppChrome.kt:301` `NasStatusBadge` shadowElevation 4；
  - `AppChrome.kt:171` `NasEmptyState` 图标容器 shadowElevation 6；
  - `AppChrome.kt:335` `NasMiniFab` defaultElevation 8 / pressedElevation 12；
  - `HomeScreen.kt:132` shadowElevation 1。
- `Elevation.kt` 注释已定调实现方式：「卡片走 flat + 1dp 描边而不是阴影，
  深色模式下阴影几乎不可见，描边才是跨主题都成立的层次手段」。
  而 `nasCardBorder()`（`AppChrome.kt:218`）已存在 —— 卡片目前**同时**有 4dp 阴影和 1dp 描边。

**验证基建**

- 全仓 **0 个 `@Preview`**；测试仅 `app/src/test/java/com/nastools/app/service/UploadProgressTrackerTest.kt`
  一个文件（JUnit + `kotlinx-coroutines-test` 可用）。
- 本机 **无 adb（不在 PATH）、`ANDROID_HOME` 未设、无模拟器**。
- Gradle 可用（`local.properties` 配 `sdk.dir`）；
  `./gradlew :app:compileDebugKotlin` **联网下通过**（离线因 aapt2 未缓存而失败）。
  即：**本会话的验证天花板是编译通过，跑起来只能由用户执行。**

**相关规范**

- `.trellis/spec/frontend/compose-optimization.md`（remember 依赖键）
- `.trellis/spec/guides/code-reuse-thinking-guide.md`（禁止跨文件复制）
- `.trellis/spec/guides/cross-layer-thinking-guide.md`

---

## Requirements

### R1 · 视觉契约：有意重设计 `[已定调]`

新 token 的值即目标值，接受界面外观变化。圆角卡片 8dp → 14dp（13 处 / 6 页面）、
对话框 8dp → 28dp；间距改由 `NasSpacing` 语义名承载；状态色按 R5 分档。
验收含**跑起来目视确认**，不是像素级比对。

### R2 · 深色模式跟随系统 `[已定调]`

保留 `darkTheme = isSystemInDarkTheme()`。零新依赖，不引入持久化层。
需覆盖明/暗两套验收 —— 深色 UI 属首次上线。

已知取舍：`StatusColors.kt:31` 关于「用户可覆盖」的注释在本任务后仍不成立，
需同时修正该注释，避免留下与实现不符的描述。

### R3 · 裸 dp 按用途分治 `[已定调]`

不按值一刀切：

1. 落在 `NasSpacing` 阶梯（2/4/8/12/16/24/32）上的 **102 处出现**直接替换；
2. 间距用途但离阶梯的（`Spacer(Modifier.width(14.dp))`、`Arrangement.spacedBy(14.dp)`、
   `padding(horizontal = 28.dp)` 等）就近归并到最近阶梯；
3. 组件固有尺寸保留字面量 —— `Modifier.size(18.dp)`（图标）、`height(20.dp)`（骨架条）、
   `BorderStroke(1.dp, …)`（描边宽）等。它们不是间距，硬套语义名会把无关数值抬成设计决策。

### R4 · listBottom 取值 `[由 R1 推导]`

`NasSpacing.listBottomWithFab = 88.dp` 与三个列表页现状 `bottom = 96.dp`
（`HomeScreen.kt:188`、`HomeScreen.kt:278`、`PresetListScreen.kt:137`）冲突，
按「token 值为准」**取 88**。`NasListPaddingWithFab`（`Spacing.kt:44`）即为此三处的模式而写。

### R5 · 状态 → 语义色映射 `[已定调]`

五档语义色覆盖六个状态：

| status | 标签 | tone | 依据 |
|---|---|---|---|
| `waiting` | 等待 | `Neutral` | 排队态；`resume()` 亦回落到此（`TaskManager.kt:90`） |
| `running` | 运行中 | `Progress` | 复用 M3 `primary` 角色 |
| `paused` | 已暂停 | `Warning` | 唯一「不干预就不推进」的状态，且最易被遗忘 |
| `completed` | 完成 | `Success` | |
| `failed` | 失败 | `Danger` | 复用 M3 `error` 角色 |
| `cancelled` | 已取消 | `Neutral` | 用户主动终止的终态，非故障；报红等于把用户的决定描述成故障 |

已知取舍：`waiting` 与 `cancelled` 同为中性色。二者处于生命周期两端，
且操作菜单不同（等待 → 暂停/取消；已取消 → 重试/删除，见 `TasksScreen.kt:372-388`）。

### R6 · 抽出重复的 status 分叉表 `[由仓库规范推导]`

`statusLabel()` 现有两份私有实现，本次还要新增 status → tone 映射，
将出现三张按 status 分叉的表。`.trellis/spec/guides/code-reuse-thinking-guide.md`
明写「Am I copying code from another file? → **STOP** — extract to shared」，
故收敛为**单一来源**，同时覆盖 `statusLabel()` 与 `statusTone()`。

`NasStatusBadge` 的 `positive: Boolean` 参数由 `tone: NasStatusTone` 取代 ——
二值参数无法表达五档，这正是缺陷的成因。

*注：`SettingsScreen.kt:76,89` 的两处调用与任务状态无关 ——
「已授权/未授权」（`:76`）、「已忽略/优化中」（`:89`）是权限状态。
为不让本次改动外溢，这四处**保持现状配色语义**：已授权/已忽略 → `Success`，
未授权/优化中 → `Danger`（即今日的红/绿不变）。是否软化为 `Warning` 留待后续任务。*

### R7 · 验证面 `[已定调]`

- **编译**：`./gradlew :app:compileDebugKotlin` 必须通过（本会话可执行）。
- **单元测试**：为 status → tone 映射补测试，作为本次真实缺陷的回归防线
  （沿用现有 JUnit + `kotlinx-coroutines-test` 设置）。
- **`@Preview` 基建**：为 6 个改动页面各加明/暗两组 `@Preview`，
  外加一条状态徽章六态对照预览。理由：全仓现为 0 个 `@Preview`，
  而主题首次上线需要目视 12 个状态（6 页面 × 2 主题）。
- **目视终验**：由用户在 Studio / 设备上完成 —— 本机无 adb 与模拟器。

---

## Acceptance Criteria

**缺陷修复**

- [ ] AC1 `waiting` / `paused` / `completed` 三态渲染为三种不同颜色，不再同为绿色。
- [ ] AC2 六个状态全部落到 R5 映射表，`failed` 与 `cancelled` 不再同色。
- [ ] AC3 status → tone 映射有单元测试覆盖全部六态，测试可独立运行通过。
- [ ] AC4 `positive: Boolean` 参数在 `AppChrome.kt` 中不再存在。

**token 接入**

- [ ] AC5 `NasSpacing` / `NasShape` / `NasElevation` / `nasStatusColors()` 在 `theme/`
      之外均有实际引用（当前为零）。
- [ ] AC6 卡片圆角统一为 `NasShape.Card`（14dp），`AppChrome.kt:52` 的
      `NasCardShape = RoundedCornerShape(8.dp)` 被移除，13 处调用点全部改指新来源。
- [ ] AC7 R3 规则下阶梯内 102 处出现完成替换；离阶梯的间距类就近归并；
      组件固有尺寸保留字面量。剩余裸 dp 仅剩 R3 第 3 类，且可在 review 时逐条说明。
- [ ] AC8 三个列表页底部内边距统一走 `NasListPaddingWithFab`（88dp）。
- [ ] AC9 `NasElevation` 扩展后覆盖全部实际用到的值（0/1/2/6/8/12），
      卡片按 `Elevation.kt` 既定意图改为 flat + 1dp 描边（详见 `design.md` §4）。
      校验须用**宽于单行赋值**的模式，`levation = <digit>` 会漏掉
      `targetValue = if (selected) 8.dp else 2.dp` 这类形式：
      ```bash
      grep -rnE "(Elevation|elevation)[^=]*= *[^N][^a-zA-Z]*[0-9]+\.dp" \
        app/src/main/java --include=*.kt | grep -v "/theme/"
      ```

**多主题**

- [ ] AC10 明/暗两套下 `nasStatusColors()` 六个 tone 均可解析，无缺失分支。
- [ ] AC11 `Theme.kt` 中 `view.context as Activity` 的安全 cast 保留，
      Compose 预览下不抛 `ClassCastException`。

**单一来源**

- [ ] AC12 `statusLabel()` 全仓仅剩一份定义。
- [ ] AC13 `StatusColors.kt:31` 关于「用户可覆盖」的注释被修正为与实现一致。

**验证面**

- [ ] AC14 `./gradlew :app:compileDebugKotlin` 通过。
- [ ] AC15 6 个改动页面各有明/暗 `@Preview`，状态徽章有六态对照 `@Preview`。
- [ ] AC16 用户完成明/暗两套的逐页目视确认。

---

## Out of Scope

- **Trellis 工具链改动**（`.agents/`、`.codex/`、`.opencode/`、`.trellis/scripts/` 共约 130 个路径，
  疑似一次 `trellis update` 的产物）—— 按用户决定**分开处理**，不纳入本任务提交。
  注：`.trellis/{.gitignore,.template-hashes.json,.version,config.yaml,workflow.md}`
  亦属该次工具链改动，同样不纳入。
- **深色模式用户覆盖开关** —— 需从零新建 DataStore 持久化层，属独立交付物
  （见 R2 取舍）。
- **组件固有尺寸的 token 化**（图标 `size(18.dp)`、骨架条 `height(20.dp)`、
  描边宽 `BorderStroke(1.dp, …)`）—— R3 第 3 类明确排除。
- **`NavMotion` / `rememberNasMotionEnabled()` 相关改动** —— 本任务不动动效层。

## 执行中的范围增量（经用户逐项批准）

原计划之外、在执行中出现并经用户明确同意的两项：

- **R8 · CHANGELOG 归档重构** —— `CHANGELOG.md` 自 `3c31e2e Initial commit` 后未再更新，
  其 `[Unreleased]` 与 `[0.1.0]` 两段均为已被 `5c97dd0` 删除的 Flutter 内容。
  经用户决定：把两段 Flutter 内容移入「历史 · Flutter 时代，已废弃」区，
  新建 `[0.2.0] - 2026-10-01` 段记录本任务。
- **R9 · 版本号提升** —— `versionCode 1 → 2`、`versionName "0.1.0" → "0.2.0"`
  （`app/build.gradle.kts`）。原 `versionName` 与 CHANGELOG 中 Flutter 的 `[0.1.0]` 撞号。
  用户已确认此为有意的版本发布决定。
- **R10 · `PermissionHelper` 预览加固** —— `util/PermissionHelper.kt:43` 的
  `getSystemService(POWER_SERVICE) as PowerManager` 改为安全转换。
  真机上该服务必然存在，**行为零变化**；目的是消除设置页 `@Preview` 的崩溃风险
  （原 `implement.md` 风险项 R-4）。与 `Theme.kt` 中已有的 `as? Activity` 修复同构。
- **R11 · release 签名配置** —— 生成密钥库 `nastools-release.jks`（RSA 2048，别名
  `nastools`），凭据存 `keystore.properties`；两者均加入 `.gitignore`。
  `app/build.gradle.kts` 接入**条件式** `signingConfigs`：配置文件缺失时不配置签名，
  使没有密钥库的机器仍能编译与跑测试。用户授权自行生成密钥。
  已验证 release 包以 v2 方案签名成功（`apksigner verify` 通过，`CN=NasTools`）。

## 后续工作流（用户于执行中下达的长期规则）

以下为**面向未来**的约定，非本任务的一次性范围，已记入长期记忆：

- **每次更新追加版本号**，默认追加第三级（patch），`versionCode` 同步 +1。
  本任务已按用户决定将 `0.1.0` → `0.2.0`（属该规则生效前的显式版本决定）；
  规则自下一个任务起适用。
- **编译测试环节同时打 release 包**（`assembleDebug` + `assembleRelease` 一并执行）。
- **提交后自动 push 到远程**（`origin` = `git@github.com:han1997/NasTools.git`）。

## 验收状态说明

- **AC16（用户目视终验）未获用户回执。** 用户在本任务末指示「继续提交以及后面的步骤」，
  即在未报告目视结果的情况下要求推进提交。故 AC1/AC2/AC6/AC9 的**代码与测试层面**
  已验证成立（单测断言 + grep 校验），但**视觉呈现效果未经人工确认** ——
  debug APK 已产出供后续复核。

## Open Questions

无阻塞项。全部产品与范围决策已在规划阶段解决；
`NasElevation` 扩展方案见 `design.md`，在 1.4 评审闸门一并确认。
