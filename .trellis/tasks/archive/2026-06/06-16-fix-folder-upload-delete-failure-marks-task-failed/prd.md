# 修复：文件夹「上传后删除」删除失败导致任务被标记为失败

## Goal

用户上传文件夹并开启「上传后删除」时，文件其实已全部上传成功，但任务被标记为 `failed`，提示「上传完成，但无法删除本地文件夹」。本任务让本地删除失败不再让上传任务失败，并按 SAF 实际能力处理：只清空文件夹内容、不尝试删除选中的根文件夹。

## Root Cause（两层）

1. **删除失败被当致命错误**：`UploadExecutor.kt:390` 的 `deleteLocalSource` 删除失败时 `throw IOException`，异常经 `TaskManager.kt:50-52` 捕获后把任务写为 `failed`——即使上传 100% 成功。
2. **SAF 树根无法删除**：经 `OpenDocumentTree` 授权的树根目录，`ExternalStorageProvider` 不设 `FLAG_SUPPORTS_DELETE`，`DocumentFile.delete()` 返回 false、回退的 `contentResolver.delete()` 也不支持 → 抛异常。夹内文件/子目录可删，唯独选中的根文件夹删不掉（错误文案是「文件夹」即印证此点）。

## Decisions（已与用户确认）

- **Q1 任务状态**：删除失败 → 任务标记 `completed`，并附一条非致命警告；复用现有 `errorMessage` 字段承载警告文案（不新增状态枚举，改动最小）。
- **Q2 根目录语义**：「上传后删除」对文件夹 = 清空内容（递归删除文件与子目录），**从不尝试删除选中的根文件夹，也不为根目录残留报警告**；空壳保留。

## Requirements

- 上传成功后，本地删除失败不再让任务进入 `failed`；改为 `completed`（必要时带 `errorMessage` 警告）。
- 文件夹「上传后删除」：递归删除夹内所有文件与子目录；选中的根文件夹不删除、不报警告。
- 仅当**夹内文件/子目录**确实删除失败时，才累计为警告（根目录残留不计入）。
- 单文件「上传后删除」删除失败同样按「completed + 警告」处理（与文件夹路径行为一致）。
- 真正的上传失败（网络、配置、Wi-Fi 限制、远端冲突 fail 等）仍标记 `failed`，绝不被误判为成功。

## Acceptance Criteria

- [ ] 文件夹 + 上传后删除：所有文件上传成功、内容删除成功、根目录保留 → 任务状态 `completed` 且**无** errorMessage（落在「已完成」页，徽标「完成」）。
- [ ] 若某个夹内文件/子目录删除失败 → 任务 `completed` 且 `errorMessage` 含简洁警告（如「上传完成，N 个本地项目未能删除」），在「已完成」页可见。
- [ ] 单文件 + 上传后删除：删除失败 → `completed` + 警告，而非 `failed`。
- [ ] 上传过程本身失败 → 任务仍 `failed`，可重试。
- [ ] 不再出现「上传成功却显示失败」的情况。

## Definition of Done

- 编译 / lint 通过。
- 关键路径行为符合验收标准（至少手动验证文件夹 + 删除场景）。
- 不引入对真正上传失败的误吞。

## Technical Approach

- `UploadExecutor`：
  - 用一个 per-execution 的 `MutableList<String> warnings`（或等价结构）贯穿 `uploadFolder/uploadDirectory/uploadFile/deleteLocalSource`。
  - `deleteLocalSource` 改为非抛出：删除失败时把描述加入 `warnings`，不再 `throw`。
  - `uploadDirectory` 增加 `isRoot` 区分：根调用只删内容、跳过自身删除；子目录维持「内容全部删除后才删自身」逻辑。
  - `execute` 返回聚合后的警告文案（无警告则 null）。
- `TaskManager.executeTask`：拿到 execute 的警告结果；标记完成时，有警告则 `updateStatusWithError(id, "completed", warning, retryCount)`，无则 `updateStatus(id, "completed")`。
- UI：`TasksScreen` 现有逻辑已能在「已完成」页显示 `errorMessage`（`:213-216`，当前为红色）。可选优化：`status=="completed"` 时用警告色（琥珀）而非错误红。

## Decision (ADR-lite)

- **Context**：SAF 树根不可删 + 删除失败被当致命错误，导致成功上传显示为失败。
- **Decision**：删除失败降级为完成态警告；文件夹删除语义改为「只清内容、不碰根」。
- **Consequences**：常见场景下任务干净完成、无警告噪音；选中的空文件夹外壳会保留（用户已接受）；真正上传失败仍正确失败。

## Out of Scope

- 真正删除选中的根文件夹外壳（受 SAF 限制，需 MANAGE_EXTERNAL_STORAGE 或其他更大权限/手段，本任务不做）。
- 新增独立任务状态枚举（如 completed_with_warnings）。
- 警告色 UI 优化为可选项，非必须。

## Technical Notes

- 相关文件：`service/UploadExecutor.kt`、`service/TaskManager.kt`、`presentation/tasks/TasksScreen.kt`、`presentation/tasks/TasksViewModel.kt`（按 status 分组，completed 入「已完成」页）、`data/database/dao/TaskDao.kt`（`updateStatusWithError` 可写 status+errorMessage+retryCount）。
- `execute` 仅在 `TaskManager.kt:67` 调用；`deleteLocalSource` 仅 `UploadExecutor` 内部使用——改签名安全。
