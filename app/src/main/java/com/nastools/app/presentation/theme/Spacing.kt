package com.nastools.app.presentation.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * 全局间距 token，取代散落在各页面的裸 dp 字面量。
 */
object NasSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 所有列表页面统一的左右内边距 */
    val screenH = 16.dp
    val listTop = 12.dp

    /** 无 FAB 的列表底部内边距 */
    val listBottom = 24.dp

    /** 有 FAB 的列表底部内边距：SmallFAB(40) + FAB 边距(16) + 呼吸空间(32) */
    val listBottomWithFab = 88.dp

    val cardPadding = 16.dp
    val sectionGap = 12.dp

    /** Material 3 最小触摸目标 */
    val minTouchTarget = 48.dp
}

/** 无 FAB 的列表页通用内边距 */
val NasListPadding = PaddingValues(
    start = NasSpacing.screenH,
    top = NasSpacing.listTop,
    end = NasSpacing.screenH,
    bottom = NasSpacing.listBottom
)

/** 有 FAB 的列表页通用内边距 */
val NasListPaddingWithFab = PaddingValues(
    start = NasSpacing.screenH,
    top = NasSpacing.listTop,
    end = NasSpacing.screenH,
    bottom = NasSpacing.listBottomWithFab
)
