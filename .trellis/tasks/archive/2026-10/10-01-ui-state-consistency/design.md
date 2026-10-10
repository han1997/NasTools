# 技术设计：统一页面状态反馈与危险操作确认

## 1. 设计目标与边界

本子任务只补齐状态反馈和危险操作确认，不改变上传冲突策略、数据表结构、导航结构或页面的视觉品牌方向。实现采用现有 Compose + StateFlow + Material 3 体系，优先在现有文件上做最小增量修改。

共享 UI 组件放在 `presentation/components/AppChrome.kt`，继续复用 `NasEmptyState`、`NasScaffold`、`NasSpacing`、`NasShape`、`NasElevation` 和 `NasToolsTheme`。页面状态由各自 ViewModel 提供，组件不读取 Repository，也不持有业务状态。

## 2. 共享组件契约

### 2.1 `NasErrorState`

建议签名：

```kotlin
@Composable
fun NasErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Error
)
```

内部复用 `NasEmptyState`，通过 `Button` 提供“重试”动作。这样错误态与已有空态在图标容器、间距、主题和动效上保持一致，调用方只提供业务文案和重试回调。

### 2.2 `NasConfirmDialog`

建议签名：

```kotlin
@Composable
fun NasConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.Warning,
    confirmIsDestructive: Boolean = true
)
```

内部统一使用 Material 3 `AlertDialog`。确认按钮使用危险色（非破坏性场景可关闭 `confirmIsDestructive`），取消按钮始终先于确认按钮呈现。组件不负责关闭状态，调用方在 `onConfirm` / `onDismiss` 中清除待处理对象，避免确认回调重复执行。

## 3. 状态数据流

### 3.1 页面级加载错误

页面级错误是持续状态，不与一次性 Snackbar 共用字段：

- Home：`HomeUiState` 增加 `loadErrorMessage`，ViewModel 将配置和任务流的异常转换为状态；`reload()` 取消旧收集并重新建立组合流。
- Tasks：任务中心的三个状态流通过可重建的列表流组合，增加 `isLoading` / `loadErrorMessage`；列表加载失败显示共享错误态，重试重新建立订阅。- Browser：`BrowserUiState` 增加 `pageErrorMessage`。首次进入目录且没有旧条目时显示 `NasErrorState`；刷新已有目录失败时保留旧条目，将错误放入操作级 Snackbar 通道。
- ConfigEdit / PresetEdit：增加 `loadErrorMessage` 和 `retryLoad()`；找不到对象或读取失败时显示错误态，不再把加载错误混在表单底部文本中。
- TaskDetail：以重试触发器重新创建 `observeById` 流；`Error` 状态使用 `NasErrorState`，点击重试重新订阅。

加载成功或用户修改表单时清除对应页面错误；重试动作不得清空已有可继续使用的数据，除非该页面本身没有可展示内容。

### 3.2 操作级反馈

各 ViewModel 保留或增加 `message` / `errorMessage` 一次性状态，并提供 `clearTransientMessage()`。页面用 `SnackbarHostState` 监听该字段，展示后立即清除：

- Browser 继续使用已有 Snackbar 通道，但区分 `pageErrorMessage` 与 `errorMessage`。
- ConfigEdit / PresetEdit 将测试、保存、选择本地文件、删除和校验失败统一接入 Snackbar；保存成功后导航的路径不额外提示。
- TasksViewModel 增加 transient 状态，所有 `TaskManager` 和 Repository 调用包在 `runCatching` 中；失败写入 error，成功可写入简短 message。批量删除只有 Repository 成功后才退出选择模式。
- TaskDetailViewModel 为重试和删除增加操作级反馈；删除成功才调用 `onDeleted`，失败留在当前页并显示 Snackbar。
- 现有成功提示（如浏览器创建文件夹、预设运行）保持兼容。

### 3.3 危险操作确认

各页面仅保存“待确认对象”，不直接执行：

| 页面 | 确认对象 | 确认后动作 |
|---|---|---|
| ConfigEdit | `showDeleteDialog` | 删除 NAS 配置及关联预设 |
| PresetList | `presetPendingDelete` | 删除预设 |
| Browser | `entryPendingDelete` | 删除远端文件/文件夹 |
| Tasks | `taskPendingDelete` / `taskPendingCancel` / `showBatchDeleteDialog` | 删除任务、取消任务、批量删除 |
| TaskDetail | `showDeleteDialog` | 删除任务记录 |

任务取消文案明确“会终止任务，但不会删除 NAS 文件”；远端删除文案明确“无法撤销”。所有确认弹窗改用 `NasConfirmDialog`，不改变现有业务回调。

## 4. 页面改动边界

- `HomeScreen.kt`：在配置内容区域接入 `NasErrorState`，保留活动任务横幅、加载骨架和空配置动作。
- `HomeViewModel.kt`：加入可重试的组合流收集和加载错误状态。
- `BrowserScreen.kt` / `BrowserViewModel.kt`：区分阻塞加载错误和 Snackbar 错误，接入错误态重试，替换远端删除确认。
- `ConfigEditScreen.kt` / `ConfigEditViewModel.kt`：接入 Snackbar、加载错误态、重试和共享确认对话框。
- `PresetEditScreen.kt` / `PresetEditViewModel.kt`：接入 Snackbar、加载错误态和重试。
- `PresetListScreen.kt`：保留现有 Snackbar，替换删除确认。
- `TasksScreen.kt` / `TasksViewModel.kt`：接入 Snackbar，替换删除/批量删除确认，增加取消确认。
- `TaskDetailScreen.kt` / `TaskDetailViewModel.kt`：错误态增加重试，替换删除确认，接入操作失败 Snackbar。
- `AppChrome.kt`：增加两个共享组件；不修改已有组件签名，避免影响已完成页面。

## 5. 兼容性与风险控制

- 不改变 Room Entity、DAO、Repository 的公开契约；只改变 ViewModel 状态和异常收集方式。
- 不把异常消息直接展示为原始堆栈；沿用现有 `error.message ?: <fallback>`，必要时提供用户可理解的中文兜底文案。
- Snackbar 为一次性反馈，页面状态错误为持久反馈，二者字段分离，避免重组时重复弹出或错误态被清除。
- Home 和 TaskDetail 的流重试通过重新建立订阅实现，不在同一个已经完成的 `catch` 流上重复调用无效 retry。
- 如实现中发现某个 Repository Flow 在重试后仍无法恢复，只回滚该页面的重试实现，不回滚共享组件或其它页面改动；每个页面保持独立提交边界以便定位。

## 6. 验证设计

1. 纯 JVM：覆盖页面错误显示判定（空列表 + 错误显示阻塞态、已有列表 + 刷新错误保留内容）、任务操作失败进入 transient error、确认回调只执行一次等可抽出的纯逻辑。
2. Compose 预览：为 `NasErrorState` / `NasConfirmDialog` 和受影响的无状态页面内容提供浅色、深色预览。
3. 手工复现矩阵：断网/错误地址、删除不存在对象、任务管理器操作异常、空配置/空预设/空目录、远端刷新失败且已有列表，逐项验证文案、重试和 Snackbar。
4. 构建验证：现有 JVM 单测、debug APK、release APK；若存在签名配置，用 `apksigner` 检查 release 签名。
