# 综合性能优化

## Goal

全面优化应用性能，提升用户体验，让应用更流畅好用。涵盖四个核心维度：
1. **上传性能**：提升文件/文件夹上传速度
2. **UI 流畅度**：优化界面响应和滚动性能
3. **内存优化**：降低内存占用，避免 OOM
4. **启动速度**：缩短冷启动时间

## What I already know

当前技术栈：
* Jetpack Compose（现代声明式 UI）
* Hilt（依赖注入）
* Room（数据库）
* Coroutines + Flow（异步处理）
* WebDAV + SMB（网络存储协议）
* Foreground Service（后台上传）

现有架构：
* MVVM 架构（ViewModel + Repository + DAO）
* 单线程上传（串行处理文件）
* Compose 重组机制（状态变化触发重绘）

## Assumptions (temporary)

* 用户主要性能痛点：上传慢、UI 卡顿、内存占用高、启动慢
* 需要保持现有功能完整性
* 优化不应引入新的稳定性问题

## Open Questions

### 2. Phase 1: 上传性能优化方向 ✅
**决定：并发上传多个文件**
- 使用协程 `async/await` 并发上传 3-5 个文件
- 限制并发数避免资源耗尽（`Semaphore`）
- 单个大文件仍然串行，多个小文件并发
- 保持单个文件的上传逻辑不变（避免复杂度）
- 预期效果：多文件上传速度提升 2-3 倍

Phase 1 将作为独立子任务优先实现。

### 2. UI 流畅度优化重点
任务列表、动画性能、重组优化，哪个最影响体验？

### 3. 内存优化场景
大文件处理、图片缓存、内存泄漏，主要问题在哪？

### 4. 启动速度瓶颈
数据库查询、初始化流程、懒加载，主要耗时在哪？

### 1. 优化优先级和实施策略 ✅
**决定：分阶段实施，每个阶段独立优化并验证**
- Phase 1: 上传性能优化（并发上传、流式处理）
- Phase 2: UI 流畅度优化（列表滚动、重组优化）
- Phase 3: 内存优化（大文件处理、缓存管理）
- Phase 4: 启动速度优化（懒加载、并行初始化）

每个 Phase 作为独立子任务，完成后验证效果再进入下一阶段。风险可控，效果可量化。

## Requirements (evolving)

### 上传性能
* 并发上传多个文件（利用多线程/协程）
* 压缩传输（可选，针对文本/日志文件）
* 断点续传优化（现有机制改进）

### UI 流畅度
* 任务列表滚动优化（LazyColumn 性能）
* 减少不必要的重组（Compose 优化）
* 动画性能改进

### 内存优化
* 大文件处理（流式读取，避免一次性加载）
* 图片/缓存管理（Coil 优化）
* 内存泄漏检测和修复

### 启动速度
* 冷启动优化（懒加载、并行初始化）
* 数据库查询优化（索引、分页）
* Hilt 依赖注入优化

## Acceptance Criteria (evolving)

### 上传性能
* [ ] 多文件上传速度提升 50% 以上
* [ ] 大文件上传内存占用降低
* [ ] 断点续传成功率 > 95%

### UI 流畅度
* [ ] 任务列表滚动帧率 > 55 FPS
* [ ] Compose 重组次数减少 30%
* [ ] 动画流畅无卡顿

### 内存优化
* [ ] 峰值内存占用降低 20%
* [ ] 无内存泄漏（LeakCanary 验证）
* [ ] 大文件处理不触发 OOM

### 启动速度
* [ ] 冷启动时间 < 2 秒
* [ ] 首屏渲染时间 < 500ms
* [ ] 初始化流程并行化

## Definition of Done

* 编译 / lint 通过
* 性能基准测试通过（对比优化前后数据）
* 内存 profiling 验证无泄漏
* 启动时间 profiling 达标
* 无功能回归（现有功能正常工作）

## Out of Scope

待确认后补充

## Technical Notes

需要检查的文件：
* `service/UploadExecutor.kt` — 上传逻辑
* `presentation/tasks/TasksScreen.kt` — 任务列表 UI
* `MainActivity.kt` — 启动流程
* `build.gradle` — 依赖配置

性能分析工具：
* Android Profiler（CPU、内存、网络）
* LeakCanary（内存泄漏）
* Compose Layout Inspector（重组分析）
* Systrace / Perfetto（系统级性能）
