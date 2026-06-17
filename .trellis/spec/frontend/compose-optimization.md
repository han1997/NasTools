# Compose 性能优化规范

本文档记录 Jetpack Compose UI 的性能优化模式和常见错误。

---

## remember 依赖键管理

### 问题

`remember` 的依赖键决定何时重新计算缓存值。依赖键不正确会导致：
- **过度缓存**：依赖键太少，值过时但未重新计算
- **缓存失效**：依赖键太多，频繁重新计算失去缓存意义

### 解决方案

只包含**直接影响计算结果**的值作为依赖键。

### 正确示例

```kotlin
@Composable
fun TaskCard(task: TaskEntity) {
    // ✅ 正确：只包含影响计算结果的值
    val progress = remember(task.progressBytes, task.totalBytes) {
        if (task.totalBytes > 0) {
            task.progressBytes.toFloat() / task.totalBytes
        } else 0f
    }
    
    // ✅ 正确：MB 转换只依赖字节数
    val progressMB = remember(task.progressBytes) {
        task.progressBytes / (1024 * 1024)
    }
    
    val totalMB = remember(task.totalBytes) {
        task.totalBytes / (1024 * 1024)
    }
}
```

### 错误示例

#### 错误 1：包含不影响计算的值

```kotlin
// ❌ 错误：task.status 不影响进度计算
val progress = remember(task.progressBytes, task.totalBytes, task.status) {
    if (task.totalBytes > 0) {
        task.progressBytes.toFloat() / task.totalBytes
    } else 0f
}
```

**问题**：`task.status` 变化时会触发重新计算，但 `status` 不影响进度值。

**修复**：移除 `task.status`。

#### 错误 2：缓存动画状态

```kotlin
val animatedProgress by animateFloatAsState(progress)

// ❌ 错误：animatedProgress 持续变化，缓存无意义
val progressPercent = remember(animatedProgress) {
    (animatedProgress * 100).toInt()
}
```

**问题**：`animatedProgress` 在动画期间持续变化（每帧都变），导致 `remember` 每帧都重新计算，失去缓存意义。

**修复**：直接计算，不缓存。

```kotlin
// ✅ 正确：直接计算，无需缓存
val progressPercent = (animatedProgress * 100).toInt()
```

### 判断依据

问自己：**这个值变化时，计算结果会变吗？**

- 是 → 加入依赖键
- 否 → 不加入依赖键

---

## LazyColumn/LazyGrid 性能优化

### 问题

列表滚动卡顿，列表项频繁重组。

### 解决方案

1. **使用稳定 `key` 参数**
2. **添加 `animateItemPlacement()` 平滑动画**
3. **缓存昂贵计算**

### 实现模式

```kotlin
LazyColumn {
    items(
        items = tasks,
        key = { it.id } // 稳定标识符
    ) { task ->
        TaskCard(
            task = task,
            onTaskClick = onTaskClick,
            modifier = Modifier.animateItemPlacement()
        )
    }
}
```

### 关键要点

1. **`key` 参数必须使用稳定标识符**
   - 使用数据库 ID、唯一标识符
   - 避免使用索引（`items.indexOf(item)`）
   - 避免使用 `hashCode()`（不稳定）

2. **`animateItemPlacement()` 提供平滑动画**
   - 列表项增删时的位置变化动画
   - 需要配合 `key` 参数使用

3. **缓存昂贵计算**
   - 浮点除法、MB 转换等
   - 使用 `remember` 避免每次重组都计算

### 错误示例

#### 错误：不使用 key 参数

```kotlin
// ❌ 错误：没有 key，列表变化时全量重组
LazyColumn {
    items(tasks) { task ->
        TaskCard(task = task)
    }
}
```

**问题**：列表项增删时，Compose 无法识别哪个项是哪个，导致全量重组。

**修复**：添加 `key = { it.id }`。

#### 错误：使用不稳定的 key

```kotlin
// ❌ 错误：hashCode() 可能变化
LazyColumn {
    items(tasks, key = { it.hashCode() }) { task ->
        TaskCard(task = task)
    }
}
```

**问题**：`hashCode()` 不保证稳定，可能导致动画异常。

**修复**：使用 `key = { it.id }`（数据库 ID）。

---

## 动画性能优化

### 问题

动画卡顿，滚动时性能下降。

### 解决方案

使用硬件加速的 `graphicsLayer` 代替布局修饰符。

### 实现模式

```kotlin
@Composable
fun TaskCard(task: TaskEntity) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (task.status == "completed") 0.7f else 1f
    )
    
    Card(
        modifier = Modifier.graphicsLayer {
            // ✅ 硬件加速：在 GPU 上执行
            alpha = animatedAlpha
            scaleX = animatedScale
            scaleY = animatedScale
        }
    ) {
        // UI 内容
    }
}
```

### 关键要点

1. **`graphicsLayer` 使用 GPU 加速**
   - `alpha`, `scaleX`, `scaleY`, `rotationZ` 等变换在 GPU 上执行
   - 不触发布局重测量（layout pass）
   - 性能远优于 `Modifier.alpha()` 等布局修饰符

2. **`animateFloatAsState` 提供平滑动画**
   - 自动插值计算
   - 支持自定义动画曲线

### 错误示例

#### 错误：使用布局修饰符实现动画

```kotlin
// ❌ 错误：Modifier.alpha() 触发布局重测量
Card(
    modifier = Modifier.alpha(animatedAlpha)
) {
    // UI 内容
}
```

**问题**：`Modifier.alpha()` 在布局阶段执行，触发重测量，性能差。

**修复**：使用 `graphicsLayer { alpha = animatedAlpha }`。

---

## 性能监控

### Compose Layout Inspector

使用 Android Studio 的 Compose Layout Inspector 分析重组：

1. **Recomposition Count**：查看每个 Composable 的重组次数
2. **Skipped Recomposition**：查看跳过的重组（说明优化生效）

### 性能基准测试

```kotlin
// 滚动性能测试
@Test
fun testScrollPerformance() {
    // 1. 准备 100 个任务
    val tasks = (1..100).map { createTask(it) }
    
    // 2. 滚动列表
    composeTestRule.onNodeWithTag("TaskList").performScrollToIndex(50)
    
    // 3. 验证帧率 > 55 FPS
    // 使用 Android Profiler 或 FrameMetrics 测量
}
```

---

## 适用场景

- 所有使用 LazyColumn/LazyRow/LazyGrid 的列表
- 所有包含动画的 Composable
- 所有包含昂贵计算的 Composable（如进度计算、格式化）

---

## 相关文档

- [质量标准](../backend/quality-guidelines.md) - 代码质量要求
- [Compose 官方文档](https://developer.android.com/jetpack/compose/performance) - 官方性能优化指南
