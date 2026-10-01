package com.nastools.app.presentation.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nastools.app.presentation.components.NasStatusBadge
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasToolsTheme

/**
 * 六态对照预览（prd.md R7 / AC1 / AC2）。
 *
 * 一眼确认六个状态两两可区分：`waiting` / `paused` / `completed` 不再同为绿色，
 * `failed` 与 `cancelled` 亦不同色。`darkTheme` 必须显式传参 —— 预览里
 * `isSystemInDarkTheme()` 跟随 IDE 设置而非预览声明。
 */
private val previewStatuses = listOf(
    "waiting", "running", "paused", "completed", "failed", "cancelled"
)

@Preview(name = "状态徽章六态 · 浅色", showBackground = true)
@Composable
private fun StatusBadgeTonesLightPreview() {
    NasToolsTheme(darkTheme = false) {
        StatusBadgeToneColumn()
    }
}

@Preview(name = "状态徽章六态 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun StatusBadgeTonesDarkPreview() {
    NasToolsTheme(darkTheme = true) {
        StatusBadgeToneColumn()
    }
}

@Composable
private fun StatusBadgeToneColumn() {
    Surface {
        Column(
            modifier = Modifier.padding(NasSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NasSpacing.sm)
        ) {
            previewStatuses.forEach { status ->
                NasStatusBadge(text = status.statusLabel(), tone = status.statusTone())
            }
        }
    }
}
