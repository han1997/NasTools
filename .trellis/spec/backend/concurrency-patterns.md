# 并发模式规范

本文档记录项目中并发场景的实现模式和线程安全要求。

---

## 并发上传模式

### 问题

文件夹上传时，多个文件串行上传效率低，无法充分利用网络带宽。需要并发上传多个文件，但必须保证线程安全。

### 解决方案

使用协程 + Semaphore 限流 + 进度聚合器保护共享状态。全局进度必须由两部分组成：

1. 已完成文件/跳过文件的字节数
2. 当前正在上传的活跃文件进度之和

不要让每个并发文件直接 `set()` 全局完成字节数；这会让后完成的文件覆盖其他文件已经完成的进度。

### 实现模式

```kotlin
suspend fun uploadDirectory(...) {
    // 1. 收集待上传文件
    val filesToUpload = mutableListOf<Triple<DocumentFile, String, Long>>()
    directory.listFiles().forEach { child ->
        when {
            child.isFile -> filesToUpload.add(Triple(child, remotePath, size))
            child.isDirectory -> uploadDirectory(...) // 递归处理子文件夹
        }
    }
    
    // 2. 并发上传（限流 + 线程安全）
    val semaphore = Semaphore(3) // 限制并发数为 3
    val progressTracker = UploadProgressTracker(totalTaskBytes = totalBytes) { uploaded, total ->
        onProgress(uploaded, total)
    }
    val progressMutex = Mutex() // 进度回调互斥锁
    val fileWarnings = Collections.synchronizedList(mutableListOf<String>())
    
    coroutineScope {
        filesToUpload.map { (file, path, size) ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val moved = uploadFile(
                            file = file,
                            remotePath = path,
                            fileSize = size,
                            onProgress = { current, total ->
                                progressTracker.updateActive(path, current)
                            }
                        )
                        progressTracker.finishActive(path, size)
                        if (!moved) canDeleteDirectory = false
                    }
                }
            }
        }.awaitAll()
    }
    
    // 3. 合并警告（需要同步）
    synchronized(warnings) {
        warnings.addAll(fileWarnings)
    }
}
```

### 关键要点

1. **进度计数器必须区分 completed 与 active**
   - completed：已经上传完成、跳过或过滤掉的文件字节数
   - active：正在上传的每个文件当前进度
   - 汇总进度 = `completed + active.values.sum()`
   - 使用 `max(lastReported, aggregate)` 保证 UI 进度不倒退

2. **进度回调必须用 `Mutex` 串行化**
   - `onProgress` 触发 UI 更新，不能并发调用
   - 使用 `Mutex.withLock` 确保回调串行执行

3. **警告列表需要线程安全**
   - 使用 `Collections.synchronizedList()` 创建线程安全列表
   - 合并到主列表时仍需 `synchronized` 块保护

4. **Semaphore 限制并发数**
   - 避免同时打开过多文件句柄
   - 限制为 3-5 个并发上传
   - 使用 `semaphore.withPermit` 自动管理许可

5. **错误分类在并发场景仍然适用**
   - 上传、读取、认证、配置、远端冲突等主操作失败必须传播（`throw e`），让任务进入 `failed`
   - 只有上传成功后的清理失败（例如本地删除失败）记录为 warning，并允许任务 `completed`

### 错误示例

#### 错误：不保护共享状态

```kotlin
// ❌ 错误：竞态条件
var completedBytes = 0L
coroutineScope {
    files.map { file ->
        async {
            uploadFile(...)
            completedBytes += file.size // 竞态！多个协程同时写
            onProgress(completedBytes, total) // 并发 UI 更新！
        }
    }.awaitAll()
}
```

**问题**：
- `completedBytes` 不是原子操作，多个协程并发写入会丢失更新
- `onProgress` 并发调用导致 UI 更新混乱

#### 正确：completed + active 聚合 + 互斥锁

```kotlin
// ✅ 正确：线程安全
val progressMutex = Mutex()
var completedBytes = 0L
val activeProgress = mutableMapOf<String, Long>()
coroutineScope {
    files.map { file ->
        async {
            uploadFile(
                onFileProgress = { fileProgress ->
                    progressMutex.withLock {
                        activeProgress[file.path] = fileProgress
                        onProgress(completedBytes + activeProgress.values.sum(), total)
                    }
                }
            )
            progressMutex.withLock {
                activeProgress.remove(file.path)
                completedBytes += file.size
                onProgress(completedBytes + activeProgress.values.sum(), total)
            }
        }
    }.awaitAll()
}
```

### Scenario: Concurrent Upload Progress Contract

#### 1. Scope / Trigger
- Trigger: Any change to folder upload concurrency, progress reporting, or task progress persistence.

#### 2. Signatures
- `UploadProgressTracker.updateActive(key: String, uploadedBytes: Long)`
- `UploadProgressTracker.finishActive(key: String, completedFileBytes: Long)`
- `UploadProgressTracker.markComplete(bytes: Long)`

#### 3. Contracts
- `key` must be stable per remote file path.
- `uploadedBytes` is file-local progress, not global task progress.
- `finishActive` moves one file from active progress into completed bytes.
- `markComplete` is for skipped/filtered items that should count toward task progress.

#### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Two files upload concurrently | Aggregate reports sum of both active progresses |
| One file completes while another is active | Completed bytes + remaining active bytes are reported |
| Active progress decreases or clears after failure | Reported task progress must not move backward |
| File read/upload fails | Throw; task must become `failed` |
| Post-upload local delete fails | Warning; task may become `completed` with `errorMessage` |

#### 5. Good/Base/Bad Cases
- Good: `a=50`, `b=70`, then `a complete 100` reports `50 -> 120 -> 170`.
- Base: skipped folder calls `markComplete(size)` so UI does not appear stuck.
- Bad: each file calls `globalProgress.set(fileBase + progress)`, losing other files' bytes.

#### 6. Tests Required
- Unit test progress aggregation with two active files.
- Unit test progress never moves backward.
- Unit test unknown total reports uploaded bytes as total.
- Regression test short local reads fail instead of being coerced to success.

#### 7. Wrong vs Correct

Wrong:
```kotlin
updateCompletedBytes(completedBeforeFile + fileProgress)
```

Correct:
```kotlin
progressTracker.updateActive(remotePath, fileProgress)
progressTracker.finishActive(remotePath, completedFileBytes)
```

### 性能考虑

- **并发数选择**：3-5 个文件，平衡吞吐量和资源占用
- **Semaphore 开销**：可忽略，远小于网络 I/O
- **Mutex 开销**：进度回调本身很快（毫秒级），串行化影响小

### 适用场景

- 文件夹上传（多个独立文件）
- 批量下载
- 任何需要并发处理多个独立 I/O 操作的场景

### 不适用场景

- 单个大文件上传（保持串行，避免复杂性）
- 有严格顺序依赖的操作
- 共享资源冲突严重的场景（如数据库写入）

---

## 扩展：动态并发数

未来可以根据网络状况动态调整并发数：

```kotlin
val concurrency = when (networkSpeed) {
    NetworkSpeed.FAST -> 5
    NetworkSpeed.NORMAL -> 3
    NetworkSpeed.SLOW -> 2
}
val semaphore = Semaphore(concurrency)
```

---

## 相关文档

- [错误处理规范](error-handling.md) - 并发场景下的错误分类
- [质量标准](quality-guidelines.md) - 代码质量要求
