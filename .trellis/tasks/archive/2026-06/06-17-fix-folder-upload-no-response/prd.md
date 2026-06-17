# 修复文件夹上传"没反应"问题

## Goal

修复文件夹上传时出现的"没反应"问题，特别是包含子文件夹的场景。当前问题：用户上传包含子文件夹的文件夹时，任务没有进度反馈，看起来卡住或失败，但实际上可能是冲突被跳过或网络操作阻塞。

## What I already know

从代码分析（`UploadExecutor.kt`）发现的问题：

1. **根文件夹冲突被静默跳过**（第 96-99 行）
   - 当 `folderConflictMode = "skip"` 且远程已存在同名文件夹时，`prepareFolderTarget` 返回 `null`
   - 代码直接 `return`，任务标记为完成，但用户没有看到任何提示或警告

2. **子文件夹冲突被静默跳过**（第 140-149 行）
   - 子文件夹冲突时返回 `null`，只标记进度跳过（调用 `markProgress` 跳过该文件夹大小）
   - 用户看不到哪些文件夹被跳过了

3. **网络操作可能阻塞**
   - `adapter.mkdir()` 调用（第 128、278、284 行）在网络慢时会阻塞
   - 没有超时机制，用户看不到正在处理什么，感觉"没反应"

4. **进度反馈不够细致**
   - 只有字节级别的进度，看不到当前正在处理哪个文件/文件夹
   - 大量小文件时，进度条长时间不动

## Assumptions (temporary)

* 用户期望看到被跳过的文件夹/文件的提示
* 用户期望看到当前正在处理的文件名
* 冲突策略应该有更明确的反馈机制

## Open Questions

### 1. 根文件夹冲突处理策略 ✅
**决定：根文件夹存在时自动合并内容（上传不冲突的文件）**
* 当 `folderConflictMode = "skip"` 且根文件夹已存在时，不再跳过整个任务
* 改为进入文件夹，递归上传内容（子文件/子文件夹按各自的冲突策略处理）
* 这样用户可以实现增量上传，只上传新增或修改的文件
* 如果所有内容都被跳过，任务标记为 `completed` + 警告信息

### 2. 进度反馈粒度 ✅
**决定：在任务标题中动态显示当前处理的文件名**
* 修改 `TaskEntity.title` 动态更新为"上传文件夹 xxx > 当前：folder/file.jpg"
* 用户在任务列表能实时看到当前上传进度
* 不需要数据库迁移或 UI 改动，实现简单
* 每个文件开始上传前更新标题

### 3. 超时机制 ✅
**决定：为网络操作添加超时（30 秒）**
* 为 `mkdir`、`stat`、`upload` 等网络操作设置 30 秒超时
* 超时后抛出 `IOException("网络操作超时")`，任务标记为失败，可重试
* 使用 Kotlin 协程的 `withTimeout` 或 OkHttp 的超时配置
* 保留现有的协程取消机制（用户手动取消任务）

## Requirements

### 根文件夹合并模式
* 当根文件夹在远程已存在时，不再跳过整个任务
* 进入文件夹，递归上传内容（子文件/子文件夹按各自的冲突策略处理）
* 实现增量上传，只上传新增或修改的文件

### 进度反馈改进
* 在任务标题中动态显示当前处理的文件名
* 格式：`上传文件夹 xxx > 当前：folder/file.jpg`
* 每个文件开始上传前更新标题
* 用户在任务列表能实时看到当前进度

### 网络超时机制
* 为 `mkdir`、`stat`、`upload` 等网络操作设置 30 秒超时
* 超时后抛出 `IOException("网络操作超时")`
* 任务标记为失败，可重试
* 保留现有的协程取消机制（用户手动取消）

### "全部跳过"场景处理
* 如果所有文件/文件夹都因冲突被跳过，任务标记为 `completed` + 警告
* 警告信息：`所有文件已存在，跳过上传（已上传 0 字节）`
* 与现有错误处理模式一致（非致命警告）

### 跳过反馈
* 子文件夹被跳过时，记录到警告列表
* 单个文件被跳过时，不记录警告（避免信息过载）
* 最终汇总显示跳过的文件夹数量

## Acceptance Criteria

### 根文件夹合并
* [ ] 根文件夹存在时不再跳过整个任务
* [ ] 能够递归上传文件夹内容
* [ ] 增量上传正确工作（只上传新文件）

### 进度反馈
* [ ] 任务标题动态显示当前文件名
* [ ] 标题格式正确：`上传文件夹 xxx > 当前：folder/file.jpg`
* [ ] 任务列表实时更新标题

### 网络超时
* [ ] 网络操作超时设置为 30 秒
* [ ] 超时后任务标记为 `failed`
* [ ] 超时错误信息清晰："网络操作超时"
* [ ] 用户可以重试超时的任务

### "全部跳过"场景
* [ ] 所有文件被跳过时，任务状态为 `completed`
* [ ] `errorMessage` 包含："所有文件已存在，跳过上传（已上传 0 字节）"
* [ ] 任务出现在"已完成" Tab，而非"失败" Tab

### 跳过反馈
* [ ] 子文件夹被跳过时记录到警告列表
* [ ] 警告信息包含被跳过的文件夹路径
* [ ] 最终任务 `errorMessage` 汇总跳过的文件夹数量

## Out of Scope

* 单文件上传的标题动态更新（只针对文件夹上传）
* 超时时间用户可配置（固定 30 秒）
* 上传速度和剩余时间估算
* 其他模块（下载、浏览）的超时机制
* 断点续传优化（利用现有机制即可）
* 文件夹嵌套深度限制

## Definition of Done

* 编译 / lint 通过
* 修复后的逻辑符合错误处理 spec（.trellis/spec/backend/error-handling.md）
* 冲突被跳过时有明确的用户反馈
* 网络操作有超时机制
* 文件夹上传支持增量上传（合并模式）

## Technical Approach

### 1. 修改根文件夹合并逻辑
- 修改 `executeFolderUpload` 方法（第 96-99 行）
- 当 `prepareFolderTarget` 返回 `null` 时，不再直接 return
- 改为使用原 `rootPath` 继续递归上传内容
- 判断最终上传字节数，如果为 0 则设置警告信息

### 2. 实现标题动态更新
- 在 `uploadFile` 方法中，上传前调用 `TaskRepository.updateTitle`
- 标题格式：`原标题 > 当前：相对路径/文件名`
- 需要计算文件的相对路径（相对于根文件夹）
- 任务完成后恢复原始标题

### 3. 添加网络超时
- 为 `StorageAdapter` 的方法调用添加 `withTimeout(30_000)` 包装
- 主要影响：`mkdir`、`stat`、`upload` 方法
- 在 `prepareFolderTarget`、`prepareFileTarget`、`uploadDirectory`、`uploadFile` 中添加
- 超时异常向上抛出，由 `TaskManager` 捕获并标记任务失败

### 4. 跳过反馈优化
- 在 `uploadDirectory` 中，子文件夹被跳过时记录警告
- 警告格式：`跳过已存在的文件夹：folder/subfolder`
- 统计跳过的文件夹数量，在最终警告中汇总
- 所有文件被跳过时，返回特殊标志，由 `executeFolderUpload` 设置警告

## Technical Notes

需要修改的文件：
* `app/src/main/java/com/nastools/app/service/UploadExecutor.kt` — 主要修改
  - `executeFolderUpload` (第 85-114 行) — 合并模式
  - `uploadDirectory` (第 116-203 行) — 跳过反馈
  - `uploadFile` (第 205-267 行) — 标题更新
  - `prepareFolderTarget` (第 269-296 行) — 超时包装
  - `prepareFileTarget` (第 298-329 行) — 超时包装

* `app/src/main/java/com/nastools/app/data/repository/TaskRepository.kt` — 新增方法
  - `updateTitle(taskId: String, title: String)` — 动态更新标题

* `app/src/main/java/com/nastools/app/data/database/dao/TaskDao.kt` — 新增查询
  - `@Query("UPDATE tasks SET title = :title, updatedAt = :updatedAt WHERE id = :id")`

相关 spec：
* `.trellis/spec/backend/error-handling.md` — 错误处理模式（致命 vs 非致命）
* `.trellis/spec/backend/database-guidelines.md` — 数据库操作规范
