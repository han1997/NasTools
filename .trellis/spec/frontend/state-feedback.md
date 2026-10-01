# 页面状态与反馈规范

本文记录 Compose 页面在加载失败、操作失败和危险操作场景下的实际反馈契约。适用于 `presentation/**` 页面与对应 ViewModel。

## 状态通道

页面必须把两类状态分开：

- **页面状态**：加载中、成功/内容为空、加载失败。加载失败是持续状态，直到成功重试或页面离开。
- **操作反馈**：保存、删除、测试、任务控制等一次性动作的成功/失败。使用 `SnackbarHostState` 展示，展示后立即消费并清除。

不要把页面加载错误复用为 Snackbar 字段，也不要让操作失败只写日志或只依赖协程异常处理器。

## 共享组件契约

共享组件位于 `presentation/components/AppChrome.kt`：

```kotlin
@Composable
fun NasErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Error
)

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

`NasErrorState` 复用 `NasEmptyState` 的布局和设计 token，通过“重试”按钮提供恢复动作。`NasConfirmDialog` 不持有业务状态；调用方负责清除 pending 对象，保证确认回调只执行一次。

## 页面错误契约

- Home、Tasks、Browser、ConfigEdit、PresetEdit、TaskDetail 的首次加载/指定对象加载失败，必须显示持续可见的 `NasErrorState`。
- `NasErrorState` 的重试动作必须重新建立失败的 Flow 收集或重新发起读取，不能在已经完成的 `catch` Flow 上重复 collect。
- Browser 刷新目录时，如果已有旧条目，刷新失败不得清空旧条目；保留旧内容并通过 Snackbar 提示。只有没有可展示条目时才显示阻塞错误态。
- “没有内容”继续使用 `NasEmptyState`，不能用错误态替代；例如任务详情文件列表无文件信息是内容空态。

### Browser 阻塞错误判定

```kotlin
internal fun BrowserUiState.shouldShowBlockingError(): Boolean {
    return !pageErrorMessage.isNullOrBlank() && entries.isEmpty()
}
```

## 操作错误契约

以下操作失败必须进入 Snackbar 通道，并提供用户可理解的兜底文案：

| 页面/模块 | 操作 |
|---|---|
| 连接配置 | 测试、保存、删除、校验 |
| 文件浏览 | 新建文件夹、远端删除、创建上传任务 |
| 上传预设 | 选择本地文件、保存、运行、删除 |
| 任务中心 | 暂停、恢复、取消、重试、删除、批量删除 |
| 任务详情 | 重试、删除 |

一次性反馈的推荐消费模式：

```kotlin
val transientMessage = uiState.errorMessage ?: uiState.message
LaunchedEffect(transientMessage) {
    if (!transientMessage.isNullOrBlank()) {
        snackbarHostState.showSnackbar(transientMessage)
        viewModel.clearTransientMessage()
    }
}
```

`CancellationException` 必须继续抛出，不能被转换成普通操作失败；普通异常才转成 `errorMessage`。

## 危险操作契约

配置删除、预设删除、远端文件/文件夹删除、任务删除、批量删除和任务取消必须使用 `NasConfirmDialog`。确认文案要写明影响范围：

- 任务取消会停止任务，但不会删除 NAS 上的文件。
- 任务删除只删除任务记录，不删除 NAS 文件。
- 远端删除不可撤销。
- 配置删除会连带删除相关上传预设。

## 验证矩阵

| 状态/操作 | 预期呈现 | 恢复动作 |
|---|---|---|
| 首次加载失败且无内容 | 阻塞错误态 + 原因 | 重试重新建立加载 |
| 刷新失败但已有 Browser 条目 | 保留旧列表 + Snackbar | 再次刷新 |
| 列表成功但无数据 | `NasEmptyState` | 页面提供的创建/上传动作 |
| 普通操作抛异常 | Snackbar 错误 | 用户重新发起操作 |
| 删除/取消入口 | 共享确认对话框 | 确认执行一次，取消不执行 |

## 测试要求

- JVM 测试覆盖 Browser “无旧内容显示错误态 / 有旧内容保留列表 / 无错误不显示错误态”三条判定。
- 页面改动提供浅色/深色预览；错误态和确认对话框的共享组件各有预览。
- 手工验证网络失败、空数据、刷新保留旧列表、操作异常、确认取消/确认路径。

## 常见错误

### 错误：用 Snackbar 承担页面加载失败

```kotlin
// ❌ Snackbar 消失后页面只剩空白或误导性的空态
uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
```

正确做法是页面无内容时显示 `NasErrorState`，并把重试作为明确动作。

### 错误：刷新失败先清空旧列表

```kotlin
// ❌ 网络抖动时用户失去仍然可用的旧内容
_uiState.update { it.copy(entries = emptyList(), isLoading = true) }
```

正确做法是同一路径刷新时保留旧条目；失败写入操作错误，成功后再替换列表。
