# Phase 3: 内存优化

## Goal

降低应用内存占用，避免 OOM（Out of Memory），提升稳定性。重点解决大文件处理、图片缓存、内存泄漏等问题。目标：峰值内存占用降低 20%，无内存泄漏。

## What I already know

当前技术栈：
* Coil（图片加载库）
* Room（数据库）
* Coroutines（异步处理）
* DocumentFile（文件访问）

潜在内存问题：
* 大文件上传可能一次性读入内存
* 图片缓存配置可能不合理
* 协程或 ViewModel 可能存在内存泄漏
* 数据库查询结果集可能过大

## Requirements

### 大文件流式处理
* 上传时使用 InputStream 流式读取
* 避免一次性读入整个文件到内存
* 分块读取（chunk size 已配置，验证是否生效）

### 图片缓存优化
* Coil 内存缓存限制（maxSizePercent）
* 磁盘缓存配置合理
* 大图缩放加载（采样率）

### 内存泄漏检测
* 使用 LeakCanary 检测泄漏
* ViewModel 正确管理生命周期
* 协程正确取消（viewModelScope）
* Context 引用不泄漏

### 数据库查询优化
* 使用分页加载（Paging 3）
* 避免加载全部任务到内存
* Flow 延迟加载

## Acceptance Criteria

* [ ] 峰值内存占用降低 20%（基准对比）
* [ ] 大文件上传不触发 OOM（测试 500MB 文件）
* [ ] LeakCanary 验证无内存泄漏
* [ ] 图片缓存配置合理（内存缓存 < 15% RAM）
* [ ] 数据库查询使用分页

## Definition of Done

* 编译 / lint 通过
* LeakCanary 验证无泄漏
* 内存 profiling 达标（Android Profiler）
* 大文件测试通过
* 无功能回归

## Technical Approach

### 1. 验证大文件流式处理
- 检查 UploadExecutor.uploadFromUri 是否正确使用 InputStream
- 验证 chunk size 配置生效
- 测试 500MB 文件上传内存占用

### 2. Coil 图片缓存配置
- 配置内存缓存限制（maxSizePercent = 0.15）
- 配置磁盘缓存大小
- 大图采样加载（crossfade + size）

### 3. 内存泄漏检测
- 集成 LeakCanary（debug 模式）
- 检查 ViewModel 生命周期
- 检查协程作用域（viewModelScope）
- 检查 Context 引用

### 4. 数据库分页
- TaskRepository 使用 Paging 3
- Flow<List<TaskEntity>> → Flow<PagingData<TaskEntity>>
- LazyColumn 使用 LazyPagingItems

## Technical Notes

需要检查的文件：
* `service/UploadExecutor.kt` — 大文件处理
* `di/AppModule.kt` — Coil 配置
* `presentation/tasks/TasksViewModel.kt` — 内存泄漏检测
* `data/repository/TaskRepository.kt` — 分页实现

工具：
* LeakCanary（内存泄漏检测）
* Android Profiler（内存占用分析）

## Out of Scope

* 下载功能的内存优化
* 其他图片库（只优化 Coil）
* 数据库迁移策略
