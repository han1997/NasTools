package com.nastools.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 任务状态的语义色调。
 *
 * 取代旧的 `positive: Boolean` —— 那两个分支把「等待中」和「已暂停」
 * 都渲染成了和「已完成」一样的绿色，状态实际上不可区分。
 */
enum class NasStatusTone { Neutral, Progress, Success, Warning, Danger }

@Immutable
data class NasStatusColors(
    val container: Color,
    val content: Color,
    val accent: Color
)

/**
 * 当前是否处于深色主题。
 *
 * 当前主题由 `NasToolsTheme` 的 `darkTheme` 参数决定（跟随系统）。
 * 此处不能用 `isSystemInDarkTheme()` —— 它读的是系统设置，
 * 与 theme 实际采用的值可能不一致（例如预览中显式传参）。
 */
val LocalNasDarkTheme = staticCompositionLocalOf { false }

@Composable
fun nasStatusColors(tone: NasStatusTone): NasStatusColors {
    val dark = LocalNasDarkTheme.current
    val scheme = MaterialTheme.colorScheme

    return when (tone) {
        NasStatusTone.Neutral -> if (dark) {
            NasStatusColors(DarkStatusNeutralContainer, DarkStatusNeutralContent, DarkStatusNeutralAccent)
        } else {
            NasStatusColors(StatusNeutralContainer, StatusNeutralContent, StatusNeutralAccent)
        }

        NasStatusTone.Progress -> NasStatusColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            accent = scheme.primary
        )

        NasStatusTone.Success -> if (dark) {
            NasStatusColors(DarkStatusSuccessContainer, DarkStatusSuccessContent, DarkStatusSuccessAccent)
        } else {
            NasStatusColors(StatusSuccessContainer, StatusSuccessContent, StatusSuccessAccent)
        }

        NasStatusTone.Warning -> if (dark) {
            NasStatusColors(DarkStatusWarningContainer, DarkStatusWarningContent, DarkStatusWarningAccent)
        } else {
            NasStatusColors(StatusWarningContainer, StatusWarningContent, StatusWarningAccent)
        }

        NasStatusTone.Danger -> NasStatusColors(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            accent = scheme.error
        )
    }
}
