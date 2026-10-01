package com.nastools.app.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3 形状阶梯。
 *
 * 注意 [Shapes.extraLarge] 会被 AlertDialog 默认读取，
 * 所以改这一处即同时修正全部对话框的圆角。
 */
val NasShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** 语义化形状别名，避免各页面直接引用 M3 的角色名 */
object NasShape {
    val Card = RoundedCornerShape(14.dp)
    val Field = RoundedCornerShape(10.dp)
    val Badge = CircleShape
}
