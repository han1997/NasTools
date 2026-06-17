# Phase 4: 启动速度优化

## Goal

缩短应用冷启动时间，提升首次打开体验。目标：冷启动时间 < 2 秒，首屏渲染时间 < 500ms。

## What I already know

当前技术栈：
* Hilt 依赖注入
* Room 数据库
* Jetpack Compose UI
* Coroutines 异步处理

潜在启动瓶颈：
* Hilt 依赖注入初始化
* 数据库初始化（Room）
* 首屏数据加载
* Compose 首次渲染

## Requirements

### 懒加载非关键模块
* 延迟初始化非首屏必需的服务
* 数据库查询延迟到需要时
* 图片加载器延迟初始化

### 并行初始化
* 数据库和网络模块并行初始化
* 使用 App Startup 库协调初始化顺序
* 避免主线程阻塞

### 首屏数据优化
* 首屏只加载必要数据
* 使用占位符（Placeholder）
* 延迟加载详细信息

### Compose 启动优化
* 避免首屏复杂布局
* 延迟加载非可见内容
* 使用 LaunchedEffect 延迟初始化

## Acceptance Criteria

* [ ] 冷启动时间 < 2 秒（Release 模式）
* [ ] 首屏渲染时间 < 500ms
* [ ] 非关键模块懒加载
* [ ] 主线程不阻塞
* [ ] 无功能回归

## Definition of Done

* 编译 / lint 通过
* 启动时间 profiling 达标（Android Profiler）
* 功能正常工作
* 无回归

## Technical Approach

### 1. App Startup 库集成
- 使用 androidx.startup 协调初始化
- 定义初始化依赖关系
- 延迟非关键初始化

### 2. Hilt 延迟初始化
- 非首屏模块使用 `@Lazy` 注入
- 避免在 Application.onCreate 中初始化所有模块

### 3. 数据库懒加载
- Room 数据库延迟创建（首次查询时）
- 避免启动时预加载数据

### 4. 首屏简化
- HomeScreen 只加载摘要数据
- 任务列表使用占位符（骨架屏）
- 详细数据延迟加载

## Technical Notes

需要检查的文件：
* `NasToolsApplication.kt` — Application 初始化
* `di/*.kt` — Hilt 模块配置
* `presentation/home/HomeScreen.kt` — 首屏加载
* `MainActivity.kt` — Activity 启动

工具：
* Android Profiler（启动时间分析）
* Systrace / Perfetto（系统级性能）

## Out of Scope

* ProGuard/R8 优化（Release 构建配置）
* APK 体积优化
* 冷启动前的系统预加载优化
