package com.nastools.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nastools.app.presentation.theme.NasElevation
import com.nastools.app.presentation.theme.NasShape
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasStatusTone
import com.nastools.app.presentation.theme.nasStatusColors

val NasSmallShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NasScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NasTopAppBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier.background(Color.Transparent)
    ) {        TopAppBar(
            title = {
                Column {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        // Concept 01：顶栏无分隔线，页面内容自带层次
    }
}

/** 底部导航项：route 由导航层定义，组件不感知具体路由 */
data class NasBottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * 底部导航栏（Concept 01）：仅出现在连接/文件/传输/设置四个主页面，
 * 子页面不传 bottomBar 即自动隐藏。
 */
@Composable
fun NasBottomBar(
    items: List<NasBottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = NasSpacing.sm, vertical = NasSpacing.xs)
            ) {
                items.forEach { item ->
                    val selected = item.route == currentRoute
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(NasShape.Field)
                            .clickable(onClick = { onSelect(item.route) })
                            .padding(vertical = NasSpacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(NasShape.Field)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(horizontal = NasSpacing.xl, vertical = NasSpacing.xs)
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (selected) FontWeight.SemiBold else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NasEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NasSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Concept 01：空态为裸图标（40dp，accent 色），无容器气泡
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(NasSpacing.lg))
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(NasSpacing.sm))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (action != null) {
                Spacer(Modifier.height(NasSpacing.lg))
                action()
            }
        }
    }
}

@Composable
fun NasErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Error
) {
    NasEmptyState(
        icon = icon,
        title = title,
        message = message,
        modifier = modifier,
        action = {
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    )
}

@Composable
fun NasConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.Warning,
    confirmIsDestructive: Boolean = true,
    dismissText: String = "取消"
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        icon = icon?.let { imageVector ->
            { Icon(imageVector, contentDescription = null) }
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (confirmIsDestructive) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

@Composable
fun nasCardColors(): CardColors {
    return CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun nasCardElevation(): CardElevation {
    // Concept 01：卡片无阴影，层次由 1dp 描边（见 nasCardBorder）承担
    return CardDefaults.cardElevation(
        defaultElevation = NasElevation.flat,
        pressedElevation = NasElevation.flat,
        focusedElevation = NasElevation.flat,
        hoveredElevation = NasElevation.flat
    )
}

/** 卡片描边：Concept 01 的层次手段（1dp outlineVariant），页面迁移时逐页接入 */
@Composable
fun nasCardBorder(): BorderStroke {
    return BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun NasIconContainer(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = NasSmallShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Icon(
            icon,
            contentDescription,
            modifier = Modifier.padding(9.dp).size(22.dp)
        )
    }
}

@Composable
fun NasStatusBadge(
    text: String,
    tone: NasStatusTone,
    modifier: Modifier = Modifier
) {
    val colors = nasStatusColors(tone)
    val motionEnabled = rememberNasMotionEnabled()

    Surface(
        modifier = modifier.nasAnimateContentSize(motionEnabled),
        shape = NasShape.Badge,
        color = colors.container,
        contentColor = colors.content
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = NasSpacing.sm, vertical = NasSpacing.xs),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun NasMiniFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val motionEnabled = rememberNasMotionEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = nasMotionSpec(motionEnabled, NasMotion.Quick),
        label = "miniFabPressScale"
    )

    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
        interactionSource = interactionSource
    ) {
        Icon(icon, contentDescription)
    }
}
