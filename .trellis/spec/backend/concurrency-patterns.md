# 并发模式规范

本文档记录项目中并发场景的实现模式和线程安全要求。

---

## 并发上传模式

### 问题

文件夹上传时，多个文件串行上传效率低，无法充分利用网络带宽。需要并发上传多个文件，但必须保证线程安全。

### 解决方案

使用协程 + Semaphore 限流 + 原子操作保护共享状态。

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
    val completedBytesAtomic = AtomicLong(0L) // 原子计数器
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
                                // 原子更新进度
                                completedBytesAtomic.set(current)
                                // 串行化进度回调（避免并发 UI 更新）
                                progressMutex.withLock {
                                    onProgress(completedBytesAtomic.get(), totalBytes)
                                }
                            }
                        )
                        if (!moved) canDeleteDirectory = false
                    } catch (e: Exception) {
                        when {
                            // 致命错误：传播失败整个任务
                            e.message?.contains("网络") == true ||
                            e.message?.contains("配置") == true ||
                            e.message?.contains("超时") == true -> throw e
                            // 非致命错误：记录警告继续
                            else -> {
                                fileWarnings.add("文件上传失败: ${file.name}")
                                canDeleteDirectory = false
                            }
                        }
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

1. **进度计数器必须用 `AtomicLong`**
   - 多个协程并发更新 `completedBytes` 会导致竞态条件
   - 使用 `AtomicLong.set()` / `get()` 保证原子性

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
   - 致命错误（网络、配置、超时）必须传播（`throw e`）
   - 非致命错误（单个文件失败）记录警告（`fileWarnings.add`）

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

#### 正确：原子操作 + 互斥锁

```kotlin
// ✅ 正确：线程安全
val completedBytesAtomic = AtomicLong(0L)
val progressMutex = Mutex()
coroutineScope {
    files.map { file ->
        async {
            uploadFile(...)
            completedBytesAtomic.addAndGet(file.size) // 原子递增
            progressMutex.withLock { // 串行化回调
                onProgress(completedBytesAtomic.get(), total)
            }
        }
    }.awaitAll()
}
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
