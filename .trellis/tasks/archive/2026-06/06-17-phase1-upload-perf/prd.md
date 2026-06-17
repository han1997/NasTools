# Phase 1: 上传性能优化 - 并发上传

## Goal

实现多文件并发上传，提升文件夹上传速度 2-3 倍。当前上传是串行的（一个文件传完再传下一个），改为使用协程并发上传 3-5 个文件，充分利用网络带宽和 I/O 资源。

## What I already know

当前上传流程（`UploadExecutor.kt`）：
- `uploadDirectory` 递归遍历文件夹，对每个文件调用 `uploadFile`
- `uploadFile` 串行执行：打开文件 → 分块上传 → 更新进度 → 删除本地文件（可选）
- 使用 `Dispatchers.IO` 协程调度器
- 进度回调是同步的（`onProgress`）

架构：
- `UploadExecutor` 负责执行上传逻辑
- `TaskManager` 管理任务状态和进度
- Foreground Service 确保后台上传不被杀

## Requirements

### 并发上传实现
* 使用协程 `async` 创建并发任务
* 使用 `Semaphore(3)` 限制并发数为 3（可配置为 3-5）
* 每个文件作为独立协程上传
* 保持单个文件的串行上传逻辑不变

### 进度聚合
* 多个文件并发上传时，进度需要正确聚合
* 使用原子操作（`AtomicLong` 或 `Mutex`）保护共享状态
* 进度回调频率控制（避免过于频繁触发 UI 更新）

### 错误处理
* 单个文件上传失败不应阻塞其他文件
* 收集所有文件的警告信息
* 最终根据成功/失败文件数判断任务状态

### 资源管理
* 并发上传不应导致内存暴增
* 文件句柄正确关闭（每个文件上传完立即关闭）
* 协程取消时正确清理资源

### 兼容性
* 单文件上传任务保持原有逻辑（不受影响）
* 文件夹上传才启用并发

## Acceptance Criteria

* [ ] 多文件上传速度提升 2-3 倍（基准测试验证）
* [ ] 并发数限制为 3-5 个文件
* [ ] 进度聚合正确，UI 显示准确
* [ ] 单个文件失败不阻塞其他文件
* [ ] 内存占用无明显增加
* [ ] 协程取消时资源正确清理
* [ ] 单文件上传不受影响

## Definition of Done

* 编译 / lint 通过
* 上传速度基准测试通过（对比优化前后）
* 内存 profiling 验证无泄漏
* 错误处理覆盖边界情况
* 无功能回归

## Technical Approach

### 1. 修改 `uploadDirectory` 方法
- 收集所有待上传文件到列表
- 使用 `coroutineScope` 创建并发作用域
- 使用 `Semaphore(3)` 限制并发数
- 为每个文件启动 `async` 协程
- 使用 `awaitAll()` 等待所有文件完成

### 2. 进度聚合机制
- 使用 `AtomicLong` 存储已完成字节数
- 每个文件上传时更新原子计数器
- 统一的进度回调入口（避免竞态）

### 3. 错误收集
- 每个文件的警告独立收集
- 最终合并所有警告列表
- 根据成功率判断任务状态

### 4. 代码结构
```kotlin
suspend fun uploadDirectory(...) {
    val filesToUpload = mutableListOf<Pair<DocumentFile, String>>()
    // 遍历收集文件
    directory.listFiles().forEach { child ->
        when {
            child.isFile -> filesToUpload.add(child to remotePath)
            child.isDirectory -> uploadDirectory(...) // 递归
        }
    }
    
    // 并发上传文件
    val semaphore = Semaphore(3)
    coroutineScope {
        filesToUpload.map { (file, path) ->
            async {
                semaphore.withPermit {
                    uploadFile(file, path, ...)
                }
            }
        }.awaitAll()
    }
}
```

## Technical Notes

需要修改的文件：
* `app/src/main/java/com/nastools/app/service/UploadExecutor.kt` — 主要修改
  - `uploadDirectory` 方法 — 并发逻辑
  - `uploadFile` 方法 — 保持不变，但需要线程安全的进度更新

需要添加的依赖：
* `kotlinx-coroutines-core`（已有）
* 可能需要 `java.util.concurrent.Semaphore`（JDK 自带）

性能测试方案：
* 测试场景：100 个小文件（每个 1MB）上传
* 对比指标：总耗时、内存峰值
* 预期结果：耗时减少 50-70%，内存增加 < 20%
