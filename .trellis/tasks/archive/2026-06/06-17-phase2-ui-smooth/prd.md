# Phase 2: UI 流畅度优化

## Goal

优化界面响应和滚动性能，提升用户体验。重点解决任务列表滚动卡顿、Compose 重组次数过多、动画不流畅等问题。目标：任务列表滚动帧率 > 55 FPS，重组次数减少 30%。

## What I already know

当前技术栈：
* Jetpack Compose（声明式 UI）
* LazyColumn（任务列表）
* Material 3 组件
* ViewModel + Flow（状态管理）

潜在性能问题：
* 任务列表可能未启用 `key` 参数（导致全量重组）
* 状态更新可能触发不必要的重组
* 列表项可能包含复杂计算（在 Composable 中执行）
* 动画可能未使用硬件加速

## Requirements

### 任务列表滚动优化
* LazyColumn 使用 `key` 参数（基于任务 ID）
* 列表项使用 `Modifier.animateItemPlacement()` 平滑动画
* 避免在 Composable 中执行复杂计算（移到 ViewModel）

### 减少不必要的重组
* 使用 `remember` 缓存稳定值
* 使用 `derivedStateOf` 处理派生状态
* Lambda 参数稳定化（避免每次重组创建新 lambda）
* 拆分大 Composable 为小组件（细粒度重组）

### 动画性能优化
* 使用 `animateFloatAsState` 替代手动动画
* 动画使用硬件加速（`graphicsLayer`）
* 避免在动画中修改布局（使用 transform 代替）

### 性能监控
* 使用 Compose Layout Inspector 分析重组
* 添加重组计数（开发模式）
* 性能基准测试（滚动帧率）

## Acceptance Criteria

* [ ] 任务列表滚动帧率 > 55 FPS（100 个任务时）
* [ ] Compose 重组次数减少 30%（基准对比）
* [ ] 列表滚动动画流畅无卡顿
* [ ] 无内存泄漏（LeakCanary 验证）
* [ ] 功能无回归

## Definition of Done

* 编译 / lint 通过
* Compose Layout Inspector 验证重组优化
* 性能基准测试通过（对比优化前后）
* 无功能回归

## Technical Approach

### 1. TasksScreen 优化
- LazyColumn 添加 `key = { it.id }`
- 列表项添加 `Modifier.animateItemPlacement()`
- 拆分 TaskCard 为独立组件（细粒度重组）

### 2. 状态管理优化
- 使用 `remember` 缓存计算结果
- 使用 `derivedStateOf` 处理过滤/排序逻辑
- Lambda 参数提取到顶层或使用 `rememberUpdatedState`

### 3. 动画优化
- 使用 `animateFloatAsState` 处理透明度/缩放
- 使用 `graphicsLayer` 代替 `alpha`/`scale` 修饰符
- 避免动画中触发布局重测量

### 4. 代码示例
```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize()
) {
    items(
        items = tasks,
        key = { it.id } // 关键：基于 ID 的稳定 key
    ) { task ->
        TaskCard(
            task = task,
            onTaskClick = onTaskClick,
            modifier = Modifier.animateItemPlacement() // 平滑动画
        )
    }
}

@Composable
fun TaskCard(task: TaskEntity, onTaskClick: (String) -> Unit) {
    // 缓存计算结果
    val progress = remember(task.uploadedBytes, task.totalBytes) {
        if (task.totalBytes > 0) task.uploadedBytes.toFloat() / task.totalBytes else 0f
    }
    
    // 使用 graphicsLayer 优化动画
    Card(
        modifier = Modifier.graphicsLayer {
            alpha = animateFloatAsState(if (task.status == "completed") 0.7f else 1f).value
        }
    ) {
        // UI 内容
    }
}
```

## Technical Notes

需要检查的文件：
* `presentation/tasks/TasksScreen.kt` — 任务列表
* `presentation/tasks/TasksViewModel.kt` — 状态管理
* `presentation/components/*.kt` — 可复用组件

性能分析工具：
* Compose Layout Inspector（重组分析）
* Android Profiler（CPU、内存）
* Frame Timing（帧率测量）

## Out of Scope

* 其他页面的 UI 优化（Home、Settings 等）
* 图片加载优化（Coil 配置，属于 Phase 3）
* 主题动画优化（非核心功能）
