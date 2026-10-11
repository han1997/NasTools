package com.nastools.app.presentation.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Concept 01 调色板（prototypes/nastools-ui v1.1，用户已确认）。
// 松绿 / 雾白 / 石墨。Secondary / Tertiary / Outline 未在原型中定义，
// 保留迁移前取值（值为现状，非重新设计）；页面迁移时如需调整再走确认流程。
// ---------------------------------------------------------------------------

val Primary = Color(0xFF245C48)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFE3EEE4)
val OnPrimaryContainer = Color(0xFF213A2B)

val Secondary = Color(0xFF9B5E1A)
val OnSecondary = Color(0xFFFFF8EF)
val SecondaryContainer = Color(0xFFFFE1B7)
val OnSecondaryContainer = Color(0xFF3A2207)

val Tertiary = Color(0xFF655BA7)
val OnTertiary = Color(0xFFFAF7FF)
val TertiaryContainer = Color(0xFFE7DFFF)
val OnTertiaryContainer = Color(0xFF271D50)

val Error = Color(0xFFAC3C36)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFBE9E6)
val OnErrorContainer = Color(0xFF5C1F1B)

val Background = Color(0xFFF7F8F5)
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF242D29)
val SurfaceVariant = Color(0xFFEEF1EB)
val OnSurfaceVariant = Color(0xFF657069)

val Outline = Color(0xFF70827C)
val OutlineVariant = Color(0xFFE2E7DF)

// Dark colors
val DarkPrimary = Color(0xFFAFD0B1)
val DarkOnPrimary = Color(0xFF213A2B)
val DarkPrimaryContainer = Color(0xFF344C3B)
val DarkOnPrimaryContainer = Color(0xFFAFD0B1)

val DarkSecondary = Color(0xFFE7B77D)
val DarkOnSecondary = Color(0xFF2B1905)
val DarkSecondaryContainer = Color(0xFF583B17)
val DarkOnSecondaryContainer = Color(0xFFFFE9CB)

val DarkTertiary = Color(0xFFC9C0FF)
val DarkOnTertiary = Color(0xFF211842)
val DarkTertiaryContainer = Color(0xFF463E74)
val DarkOnTertiaryContainer = Color(0xFFE9E3FF)

val DarkError = Color(0xFFF4ACA6)
val DarkOnError = Color(0xFF49302E)
val DarkErrorContainer = Color(0xFF49302E)
val DarkOnErrorContainer = Color(0xFFF4ACA6)

val DarkBackground = Color(0xFF202925)
val DarkSurface = Color(0xFF29342E)
val DarkOnSurface = Color(0xFFEDF1E9)
val DarkSurfaceVariant = Color(0xFF313D35)
val DarkOnSurfaceVariant = Color(0xFFABB8AD)

val DarkOutline = Color(0xFF889A94)
val DarkOutlineVariant = Color(0xFF3D4A41)

// ---------------------------------------------------------------------------
// 语义状态色。
//
// 不是 M3 角色，专门给 NasStatusTone 用。加这一组的原因是旧的
// `NasStatusBadge(text, positive: Boolean)` 只有两个分支，导致
// 「等待中」和「已暂停」被渲染成和「已完成」完全一样的绿色。
//
// Concept 01 起五个 tone 全部使用专用语义色（原型 --blue/--amber/--red
// 与中性 --soft）：Progress 用独立蓝色，与成功绿明确区分；
// Danger 不再复用 M3 error 角色，避免与删除按钮语义混淆。
// ---------------------------------------------------------------------------

val StatusNeutralContainer = Color(0xFFEEF1EB)
val StatusNeutralContent = Color(0xFF657069)
val StatusNeutralAccent = Color(0xFF657069)

val StatusProgressContainer = Color(0xFFE8EEF9)
val StatusProgressContent = Color(0xFF315E95)
val StatusProgressAccent = Color(0xFF315E95)

val StatusSuccessContainer = Color(0xFFE3EEE4)
val StatusSuccessContent = Color(0xFF213A2B)
val StatusSuccessAccent = Color(0xFF245C48)

val StatusWarningContainer = Color(0xFFFBEFD9)
val StatusWarningContent = Color(0xFF875316)
val StatusWarningAccent = Color(0xFF875316)

val DarkStatusNeutralContainer = Color(0xFF313D35)
val DarkStatusNeutralContent = Color(0xFFABB8AD)
val DarkStatusNeutralAccent = Color(0xFFABB8AD)

val DarkStatusProgressContainer = Color(0xFF2C3B4B)
val DarkStatusProgressContent = Color(0xFFB0C8EB)
val DarkStatusProgressAccent = Color(0xFFB0C8EB)

val DarkStatusSuccessContainer = Color(0xFF344C3B)
val DarkStatusSuccessContent = Color(0xFFAFD0B1)
val DarkStatusSuccessAccent = Color(0xFFAFD0B1)

val DarkStatusWarningContainer = Color(0xFF483B26)
val DarkStatusWarningContent = Color(0xFFEFD094)
val DarkStatusWarningAccent = Color(0xFFEFD094)
