# 导航与会话状态规范

本文记录 Concept 01 迁移中确立的导航结构、文件标签会话状态与未保存编辑保护契约。
来源：任务 `10-01-ui-polish`。相关：[原型优先契约](./prototype-first.md)、[状态反馈](./state-feedback.md)。

---

## 1. 四入口导航结构

- 主入口：`home`（连接）/ `files`（文件）/ `tasks`（传输）/ `settings`（设置），
  定义在 `NavGraph.kt` 的 `bottomNavItems`。
- **底部导航仅出现在四个主页面**：各主页面通过 `NasScaffold(bottomBar = ...)` 传入
  `NasBottomBar`；子页面（连接编辑、预设列表/编辑、任务详情、媒体预览）不传
  `bottomBar`（默认空），即自动隐藏。
- **标签切换必须走 `navigateTab()`**：

```kotlin
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

  直接 `navigate(route)` 会产生重复堆栈并丢失各标签状态（目录、列表位置）。
- 子页面返回用 `popBackStack()`，天然回到实际来源页并保留其状态；
  禁止把来源页硬编码为固定路由（如「预设返回固定 home」）。

### 错误与正确

| 错误 | 正确 |
|---|---|
| 子页面也渲染底部导航 | 子路由不传 `bottomBar` |
| 标签切换用裸 `navigate()` | 统一走 `navigateTab()` |
| 预设列表返回硬编码 `navigate("home")` | `popBackStack()` 回实际来源 |

---

## 2. FileTabSession：文件标签的会话内状态

`presentation/browser/FileTabSession.kt` 是 Hilt `@Singleton`，持有：

- `selectedConfigId: StateFlow<String?>` —— 本次会话明确选择的连接；null = 未选择
- `lastPath: StateFlow<String?>` —— 当前连接的浏览目录

### 行为契约（用户已确认）

| 场景 | 行为 | 实现位置 |
|---|---|---|
| 未选择连接（即使只有一台） | 文件标签显示连接选择页，不默选 | `FilesScreen` 分支 |
| 从首页卡片/选择页选择 | `select(configId)`；切换到不同连接时清空 `lastPath` | `FileTabSession.select` |
| 标签切回 | 恢复连接与目录（VM 存活 + `lastPath`） | `BrowserViewModel.load` |
| 当前配置被删除 | `FilesViewModel` 观察配置列表，`none { it.id == selected }` → `clearSelection()` 回选择页 | `FilesViewModel.init` |
| 网络错误 | **不经过 session**：保留连接与路径，走既有错误契约（阻塞错误/刷新保留旧列表） | `BrowserViewModel` |
| 进程重启 | 全部清空（不持久化），不构成「默认 NAS」偏好 | 内存态 |

### 关键约束

- **配置列表未加载完成时不得误判为已删除**：`FilesUiState.configs` 用
  `List<NasConfigEntity>?` 区分「加载中（null）」与「空列表」，只有非 null 且
  不含选中项才清理。
- **网络失败不得触发 `clearSelection()`**：配置不存在与暂时断网是两类状态；
  鉴权失败等可恢复错误也不能静默切换 NAS。
- `BrowserViewModel` 由 `selectedConfigId` collect 驱动加载（id 变化才 reload），
  目录加载成功后 `updatePath(path)` 持久化到会话。

---

## 3. 未保存编辑保护

适用于连接编辑（`ConfigEditScreen`）与预设编辑（`PresetEditScreen`）。

### ViewModel 契约

- `UiState` 增加 `isDirty: Boolean`。
- **所有表单字段更新统一走 `update {}` 助手**，助手内先置 `isDirty = true`——
  任何改动即标记（含改回原样的简化比对，不做深比较）。
- `testConnection()` 不走 `update {}`（直接 `_uiState.update`），**测试连接不算改动**。
- 加载完成（新建初始态 / 编辑载入）复位 `isDirty = false`。
- 保存成功：`isDirty = false` 后再回调 `onSaved()`，正常返回不再提醒。

### Screen 契约

```kotlin
val requestBack = {
    if (uiState.isDirty) showUnsavedDialog = true else onBack()
    Unit
}
BackHandler(enabled = uiState.isDirty && !showUnsavedDialog) { showUnsavedDialog = true }
```

- 顶部返回按钮与 `BackHandler`（系统返回）都走 `requestBack`。
- 确认对话框复用 `NasConfirmDialog`：`confirmText = "离开"`、
  `dismissText = "继续编辑"`（文案「未保存的修改将会丢失」为用户确认的固定措辞）。
- 对话框不持有业务状态；确认后 `showUnsavedDialog = false` 再 `onBack()`。

### 错误与正确

| 错误 | 正确 |
|---|---|
| `testConnection` 也置 dirty | 测试连接只读校验，不算改动 |
| 保存失败仍复位 dirty | 仅 `onSuccess` 复位，失败留在页面可继续编辑 |
| 只拦截顶部返回 | `BackHandler` 与顶部返回行为必须一致 |

---

## 4. 窗口背景与 Compose 主题背景必须一致

页面切换转场期间两个 destination 会短暂半透明叠加，露出的是 **XML 主题的
`android:windowBackground`**，不是 Compose 的背景色。二者不一致时，任何转场
（包括交叉淡化）都会闪出窗口底色——这是「切换页面闪屏」的根因。

- `res/values/colors.xml` 的 `nas_window_background`（浅 `#F7F8F5`）与
  `values-night`（深 `#202925`）必须与 `NasToolsTheme` 的 `background` 同步修改。
- `themes.xml`、`values-night/themes.xml`、`values-v27/v29` 及 `values-night-v27`
  均挂 `windowBackground`；深色主题需同时关闭 `windowLightStatusBar/LightNavigationBar`。
- 改主题背景色时同步检查这组资源；新增页面不得自备根背景色。

---

## 变更记录

- Concept 01 迁移（`10-01-ui-polish`）：建立四入口导航、FileTabSession 会话契约、
  未保存保护模式；`NasConfirmDialog` 增加 `dismissText` 参数（默认「取消」向后兼容）。
- 闪屏修复（`10-11-home-files-ui-fixes`）：确立窗口背景对齐契约（第 4 节）；
  首页列表模式下文件夹改为单列列表行（用户确认的偏离原型项，原型为两列卡片）。
