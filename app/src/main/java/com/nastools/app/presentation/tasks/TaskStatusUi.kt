package com.nastools.app.presentation.tasks

import com.nastools.app.presentation.theme.NasStatusTone

/**
 * 任务状态的呈现层单一来源。
 *
 * 这里刻意不 import 任何 `androidx.compose.*`：返回类型 [NasStatusTone] 是纯 Kotlin
 * 枚举，因此本文件可以在 JVM 单元测试里直接加载（R7 的回归防线）。
 * [statusLabel] 取代了原先在 TaskDetailScreen/TasksScreen 各自复制的一份实现。
 */

/** 状态中文标签。全仓唯一来源。 */
fun String.statusLabel(): String = when (this) {
    "waiting" -> "等待"
    "running" -> "运行中"
    "paused" -> "已暂停"
    "completed" -> "完成"
    "failed" -> "失败"
    "cancelled" -> "已取消"
    else -> this
}

/**
 * 状态 → 语义色档位。映射表见 prd.md R5。
 *
 * `waiting` 与 `cancelled` 同为 [NasStatusTone.Neutral]：二者处于生命周期两端，
 * 且操作菜单不同（等待 → 暂停/取消；已取消 → 重试/删除）。
 */
fun String.statusTone(): NasStatusTone = when (this) {
    "running" -> NasStatusTone.Progress
    "paused" -> NasStatusTone.Warning
    "completed" -> NasStatusTone.Success
    "failed" -> NasStatusTone.Danger
    "waiting", "cancelled" -> NasStatusTone.Neutral
    else -> NasStatusTone.Neutral
}
