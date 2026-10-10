# 空态 / 错误态 / 确认对话框统一

父任务：`10-01-experience-feature-optimization`　·　依赖：无

## Goal

统一各页面的空态、加载态、错误态和危险操作确认，消除“操作失败但界面没有反馈”的路径。
本子任务解决体验正确性问题；视觉风格层面的打磨仍归 `10-01-ui-polish`，并继续遵守“先修功能，再美化”的排期。

## Background

上轮已完成并归档同名文件跳过可见化：同名文件默认策略仍是 `resume_or_overwrite`，本子任务不改变冲突策略或上传决策逻辑。
本轮代码核查（2026-10-02）确认：

- `presentation/components/AppChrome.kt` 已有 `NasEmptyState` 等共享外观组件，但没有共享错误态或确认对话框；删除确认由各页面分别实现 `AlertDialog`。
- `HomeScreen.kt:92-103` 只有加载骨架和配置空态；`HomeViewModel.kt:24-33` 直接组合数据库流，没有错误字段或 `catch`，流异常不会显示给用户。
- `BrowserScreen.kt:280-299` 已区分加载、空目录和列表；`BrowserViewModel.kt:230-257` 的目录加载错误会进入通用 `errorMessage`，虽能经 Snackbar 展示，但首屏或空列表失败没有持续可见、带重试的页面错误态。
- `PresetListScreen.kt:77-82,125-135` 已有 Snackbar 和 `NasEmptyState`；`PresetEditScreen.kt:99-101,207-209`、`ConfigEditScreen.kt:120-122,200-205` 仍以线性进度条和裸红色文本展示错误，加载对象不存在时没有独立错误页。
- `TasksScreen.kt:128-167,229-253` 已覆盖三个列表空态；`TasksViewModel.kt:43-47,76-79` 的暂停、恢复、取消、重试、删除和批量删除没有把失败写回 UI 状态，且菜单中的取消操作没有二次确认。
- `TaskDetailScreen.kt:51-80` 已有 Loading / Error / Success 三态，`TaskDetailScreen.kt:85-108` 已有删除确认；文件列表“无文件信息”（`TaskDetailScreen.kt:288-294`）是有意的内容空态，不应改成页面错误。
- 危险操作确认分散在 `ConfigEditScreen.kt:65-85`、`PresetListScreen.kt:84-105`、`BrowserScreen.kt:182-204`、`TasksScreen.kt:170-216`、`TaskDetailScreen.kt:85-108`。

## Requirements

### R1：页面状态契约

- 页面首次加载、刷新或加载指定对象失败时，使用持续可见的共享错误态，并提供“重试”动作。
- Home、远端浏览、配置编辑、预设编辑、任务详情都必须覆盖加载成功、加载中、加载失败；已有内容空态继续保留，并明确区分“没有内容”和“加载失败”。
- 远端浏览刷新失败且已有旧列表时，不隐藏可用内容；通过 Snackbar 告知失败，错误态只用于没有可展示内容的页面加载失败。
- 任务中心的活跃、已完成、失败列表继续使用明确空态；任务详情的文件列表空态保持“无文件信息”的内容语义。

### R2：操作失败反馈

- 保存、连接测试、创建文件夹、删除远端项目、创建上传任务、运行/删除预设、任务暂停/恢复/取消/重试/删除/批量删除等操作失败时，必须通过 Snackbar 可见，不得静默吞掉异常。
- 页面级错误与操作级错误使用不同状态通道，避免一次性 Snackbar 被误当成页面加载成功或失败。
- 成功后会立即离开页面的保存/删除操作无需额外成功提示；留在当前页面的操作可保留现有成功提示行为。

### R3：危险操作确认

- 抽取共享 `NasConfirmDialog`，统一取消/确认按钮顺序、危险色、图标和文案接口。
- NAS 配置删除、上传预设删除、远端文件/文件夹删除、任务删除、批量删除和任务取消全部二次确认。
- 确认文案必须说明影响范围；任务取消说明会终止任务但不删除 NAS 文件，远端删除说明不可撤销。

### R4：共享组件与兼容性

- 抽取共享 `NasErrorState`，复用现有 `NasEmptyState`、`NasSpacing`、`NasShape`、`NasElevation`、Material 颜色和既有 Snackbar/动效约定，不新造平行设计体系。
- 共享组件保持无业务依赖，页面只负责提供标题、说明、图标和动作；不改变现有导航、数据模型或同名文件处理策略。
- 明暗主题均可读；改动页面补充或保留浅色/深色预览，不为预览修改生产函数签名。

### R5：可验证性

- 为新增的状态转换/错误恢复逻辑补充可执行的 JVM 单测；无法脱离 Android 框架测试的页面路径提供明确的 Compose 预览或手工复现步骤。
- 编译、现有 JVM 单测和静态检查通过；验证时同时构建 debug 与 release 包。

## Acceptance Criteria

- [ ] AC1：状态覆盖表完成：Home、Browser、ConfigEdit、PresetEdit、Tasks、TaskDetail 均有加载中/成功或空态/失败重试路径，且内容空态不误报为错误。
- [ ] AC2：每个页面加载失败都能持续看到错误原因并点击重试；Browser 刷新已有内容失败时保留旧内容并显示 Snackbar。
- [ ] AC3：列出的操作失败均能看到 Snackbar，代码中没有因直接启动协程而静默丢失异常的路径。
- [ ] AC4：所有列出的危险操作均弹出共享确认对话框；任务取消包含确认，确认与取消都只执行一次动作。
- [ ] AC5：共享 `NasErrorState` / `NasConfirmDialog` 位于 `presentation/components/AppChrome.kt`（或同层共享组件文件），调用方不重复实现同一套视觉结构。
- [ ] AC6：明暗预览、JVM 单测、debug/release 构建均通过；CHANGELOG 追加本轮版本段，README 的实现清单与实际能力保持一致。

## Out of Scope

- 同名文件冲突策略、远端文件大小判断和上传执行决策；该问题已在 `10-01-upload-skip-visibility` 完成。
- 列表排序/筛选/搜索、字符串资源化、凭据加密、媒体预览和导航结构重设计。
- `10-01-ui-polish` 的纯视觉重设计；本任务只做状态反馈正确性和共享交互基础。
- Trellis 工具链及 `.agents/` 等已存在的工作区改动。

## Decisions

- 任务取消属于危险操作，纳入二次确认（用户确认于 2026-10-02）。
- 页面加载失败使用带重试的持续错误态；操作失败使用 Snackbar（用户确认于 2026-10-02）。
- 所有共享组件和实现细节按上述推荐方案执行，无需继续逐项确认（用户授权于 2026-10-02）。

## Open Questions

无。规划可进入技术设计和执行清单阶段。
