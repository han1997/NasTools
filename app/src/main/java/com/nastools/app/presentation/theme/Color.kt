package com.nastools.app.presentation.theme

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF286F62)
val OnPrimary = Color(0xFFF4FFFB)
val PrimaryContainer = Color(0xFFD2EDE6)
val OnPrimaryContainer = Color(0xFF08332C)

val Secondary = Color(0xFF9B5E1A)
val OnSecondary = Color(0xFFFFF8EF)
val SecondaryContainer = Color(0xFFFFE1B7)
val OnSecondaryContainer = Color(0xFF3A2207)

val Tertiary = Color(0xFF655BA7)
val OnTertiary = Color(0xFFFAF7FF)
val TertiaryContainer = Color(0xFFE7DFFF)
val OnTertiaryContainer = Color(0xFF271D50)

val Error = Color(0xFFB3261E)
val OnError = Color(0xFFFFF8F6)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)

val Background = Color(0xFFF3FAF7)
val Surface = Color(0xFFFAFEFC)
val OnSurface = Color(0xFF17211E)
val SurfaceVariant = Color(0xFFE2EFEB)
val OnSurfaceVariant = Color(0xFF53635E)

val Outline = Color(0xFF70827C)
val OutlineVariant = Color(0xFFC7D8D2)

// Dark colors
val DarkPrimary = Color(0xFF9AD9CA)
val DarkOnPrimary = Color(0xFF0D241F)
val DarkPrimaryContainer = Color(0xFF244B43)
val DarkOnPrimaryContainer = Color(0xFFE3F7F2)

val DarkSecondary = Color(0xFFE7B77D)
val DarkOnSecondary = Color(0xFF2B1905)
val DarkSecondaryContainer = Color(0xFF583B17)
val DarkOnSecondaryContainer = Color(0xFFFFE9CB)

val DarkTertiary = Color(0xFFC9C0FF)
val DarkOnTertiary = Color(0xFF211842)
val DarkTertiaryContainer = Color(0xFF463E74)
val DarkOnTertiaryContainer = Color(0xFFE9E3FF)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD4)

val DarkBackground = Color(0xFF121817)
val DarkSurface = Color(0xFF1B2320)
val DarkOnSurface = Color(0xFFE7F0EC)
val DarkSurfaceVariant = Color(0xFF2D3935)
val DarkOnSurfaceVariant = Color(0xFFBECBC6)

val DarkOutline = Color(0xFF889A94)
val DarkOutlineVariant = Color(0xFF3E4D48)

// ---------------------------------------------------------------------------
// 语义状态色。
//
// 不是 M3 角色，专门给 NasStatusTone 用。加这一组的原因是旧的
// `NasStatusBadge(text, positive: Boolean)` 只有两个分支，导致
// 「等待中」和「已暂停」被渲染成和「已完成」完全一样的绿色。
//
// Progress（运行中）和 Danger（失败）直接复用 M3 的 primary/error 角色，
// 不在这里重复定义。
// ---------------------------------------------------------------------------

val StatusNeutralContainer = Color(0xFFE7EBEA)
val StatusNeutralContent = Color(0xFF44534F)
val StatusNeutralAccent = Color(0xFF70827C)

val StatusSuccessContainer = Color(0xFFD7EFD9)
val StatusSuccessContent = Color(0xFF10421A)
val StatusSuccessAccent = Color(0xFF2E7D32)

val StatusWarningContainer = Color(0xFFFFEFD0)
val StatusWarningContent = Color(0xFF4A3208)
val StatusWarningAccent = Color(0xFFB07400)

val DarkStatusNeutralContainer = Color(0xFF2D3935)
val DarkStatusNeutralContent = Color(0xFFBECBC6)
val DarkStatusNeutralAccent = Color(0xFF889A94)

val DarkStatusSuccessContainer = Color(0xFF1E3A24)
val DarkStatusSuccessContent = Color(0xFFB7E4BC)
val DarkStatusSuccessAccent = Color(0xFF7FD08A)

val DarkStatusWarningContainer = Color(0xFF4A3A17)
val DarkStatusWarningContent = Color(0xFFF2DCAE)
val DarkStatusWarningAccent = Color(0xFFE7B77D)
