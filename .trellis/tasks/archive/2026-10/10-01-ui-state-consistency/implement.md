# 执行计划：统一页面状态反馈与危险操作确认

## 启动门槛

- [ ] 用户审阅并批准 `prd.md`、`design.md`、`implement.md`。
- [ ] 执行 `python ./.trellis/scripts/task.py start .trellis/tasks/10-01-ui-state-consistency`，确认任务状态变为 `in_progress`。
- [ ] 实施前运行 `trellis-before-dev`，刷新 frontend/backend 规范；只修改本任务相关代码、测试和文档，不触碰当前工作区已有的 Trellis 工具链改动。

## 实施步骤

### 1. 建立共享反馈组件

- [x] 在 `presentation/components/AppChrome.kt` 增加 `NasErrorState`，复用 `NasEmptyState` 和现有设计 token，支持默认错误图标、文案、重试按钮和明暗主题。
- [x] 增加 `NasConfirmDialog`，统一危险色确认按钮、取消按钮、图标、标题、说明和回调语义。
- [x] 为两个共享组件补充浅色/深色无状态 `@Preview`；不修改既有生产函数签名。
- [x] 编译一次，确认共享组件不会破坏现有页面。

**回滚点**：若 Material 3 参数或预览兼容性失败，只回退共享组件提交，不改页面逻辑。

### 2. 补齐 ViewModel 页面错误状态与重试

- [x] `HomeViewModel.kt`：加入加载错误字段和可重新建立组合流的 `reload()`；保证异常不会让页面永久停留在骨架屏。
- [x] `BrowserViewModel.kt`：将目录加载错误与 Snackbar 操作错误分开；首屏/空列表失败生成阻塞错误态，已有列表刷新失败保留旧内容并走 Snackbar。
- [x] `ConfigEditViewModel.kt`、`PresetEditViewModel.kt`：拆分加载错误和表单/操作错误，增加 `retryLoad()`、transient 清理方法。
- [x] `TaskDetailViewModel.kt`：让详情流可重新订阅，增加页面错误重试和删除/重试操作失败反馈。
- [x] `TasksViewModel.kt`：增加 `isLoading` / `loadErrorMessage`，三个状态流异常时显示页面错误态；增加 transient message/error 状态，所有任务管理与批量删除协程使用 `runCatching`；失败必须回写 UI，批量删除仅成功后退出选择模式。
- [x] 保留 `PresetListViewModel` 现有 Snackbar 机制，必要时只调整字段命名以符合统一契约。

**回滚点**：每个 ViewModel 单独编译/测试；流重试异常时先回退对应 ViewModel，不回退共享组件。

### 3. 接入页面级错误态与 Snackbar

- [x] `HomeScreen.kt`：在无可展示内容时接入 `NasErrorState` 和重试，保留加载骨架与配置空态。
- [x] `BrowserScreen.kt`：接入阻塞错误态和重试；已有条目刷新失败只弹 Snackbar；保留目录空态。
- [x] `ConfigEditScreen.kt`、`PresetEditScreen.kt`：加载失败显示错误态和重试，测试/保存/文件选择/校验失败改走 Snackbar；保留表单加载进度。
- [x] `TasksScreen.kt`：增加 `SnackbarHost` 和 transient 收集，确保暂停/恢复/取消/重试/删除/批量删除失败可见。
- [x] `TaskDetailScreen.kt`：错误态增加重试按钮，任务操作失败显示 Snackbar；文件列表“无文件信息”保持内容空态。
- [x] 每个改动页面补齐或保留浅色/深色预览，预览使用显式 `NasToolsTheme(darkTheme = ...)`。

**回滚点**：页面接入按页面独立验证；若 Snackbar 与页面状态重复触发，优先修正字段清理时机，不绕过共享契约。

### 4. 替换并补齐危险操作确认

- [x] `ConfigEditScreen.kt`：用 `NasConfirmDialog` 替换连接删除弹窗。
- [x] `PresetListScreen.kt`：用共享确认弹窗替换预设删除弹窗。
- [x] `BrowserScreen.kt`：用共享确认弹窗替换远端文件/文件夹删除弹窗。
- [x] `TasksScreen.kt`：用共享确认弹窗替换单个删除和批量删除弹窗；为活跃/暂停任务的“取消”增加待确认状态与弹窗。
- [x] `TaskDetailScreen.kt`：用共享确认弹窗替换任务删除弹窗。
- [x] 检查所有确认回调：取消只关闭弹窗，确认只执行一次业务动作；文案明确不可撤销、影响范围和“取消任务不删除 NAS 文件”。

**回滚点**：如果共享参数无法覆盖某个文案，扩展组件参数；不要重新复制一套页面内 `AlertDialog`。

### 5. 测试、文档与版本

- [x] 为可抽出的页面状态判定、刷新保留旧列表、transient 错误回写等纯逻辑补充 JVM 单测；运行现有测试并修复回归。
- [x] 运行 `python ./.trellis/scripts/task.py validate .trellis/tasks/10-01-ui-state-consistency`。
- [x] 更新 `README.md` 的已实现/待实现清单，仅反映本轮实际完成的状态反馈能力。
- [x] 按项目约定将 `versionName` 从 `0.2.1` 追加到 `0.2.2`，`versionCode` 从 `3` 增加到 `4`。
- [x] 在 `CHANGELOG.md` 追加 `0.2.2` 条目，记录页面错误重试、操作失败 Snackbar、统一危险确认和任务取消确认。

### 6. 全量验证与交付前检查

- [ ] 运行 `./gradlew.bat testDebugUnitTest`。
- [ ] 同时构建 `./gradlew.bat assembleDebug assembleRelease`。
- [ ] 若 release 产物已签名，使用 `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk` 验签；不可使用 `jarsigner` 替代。
- [ ] 按手工矩阵验证：空配置、空目录、空预设、错误连接、目录加载失败、刷新已有目录失败、表单加载失败、任务操作失败、每类删除和任务取消确认、明暗主题。
- [ ] 运行 `trellis-check`，检查规格符合性、异常路径、预览、文档和工作区边界。
- [ ] 确认未修改 `.agents/`、`.codex/`、`.trellis/scripts/` 等本轮明确排除的现有脏文件。

## 关键验证命令

```bash
python ./.trellis/scripts/task.py validate .trellis/tasks/10-01-ui-state-consistency
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug assembleRelease
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

## 风险与回滚策略

- **StateFlow 重试风险**：避免在 `catch` 完成后的同一 Flow 上重复 collect；使用可重新建立的触发器或显式 reload job。若失败，回滚单个 ViewModel。
- **Snackbar 重复风险**：展示后立即调用清理函数，且页面错误字段与 transient 字段分离；发现重复弹出时优先增加事件消费边界。
- **确认回调重复风险**：弹窗组件不持有业务状态，调用方确认后立即清除 pending 对象；用手工点击验证一次确认只触发一次。
- **工作区脏文件风险**：提交前按 `git diff --name-only` 分离本任务文件；不纳入既有 Trellis 工具链改动。
- **视觉回归风险**：共享组件只复用既有 token；明暗预览均通过后再交付，视觉细节调整留给 `10-01-ui-polish`。
