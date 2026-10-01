package com.nastools.app.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * 高度 token。
 *
 * 卡片走 [flat] + 1dp 描边而不是阴影：深色模式下阴影几乎不可见，
 * 描边才是跨主题都成立的层次手段，也让明暗两套结构完全一致。
 */
object NasElevation {
    /** 卡片/徽章：靠 1dp 描边建立层次 */
    val flat = 0.dp

    /** 贴地元素 */
    val raised = 1.dp

    /**
     * 图标容器未选中态（值为现状，非重新设计）。
     *
     * 唯一一个仅为消除字面量而存在的档位 —— 保值为 2dp、零视觉变化，
     * 不套用「卡片走 flat + 描边」的规则（图标容器是选中态指示器，与卡片不同类）。
     */
    val resting = 2.dp

    /** 浮层、空态图标容器 */
    val overlay = 6.dp

    /** FAB 常态；图标容器选中态 */
    val floating = 8.dp

    /** FAB 按下 */
    val lifted = 12.dp
}
