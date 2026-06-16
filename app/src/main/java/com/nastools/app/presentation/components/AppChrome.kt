package com.nastools.app.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val NasCardShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NasScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        surface.copy(alpha = 0.98f),
                        background,
                        surfaceVariant.copy(alpha = 0.58f),
                        background
                    )
                )
            )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
            topBar = topBar,
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
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        surface.copy(alpha = 0.96f),
                        surface.copy(alpha = 0.86f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.70f)
                    )
                )
            )
    ) {
        TopAppBar(
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
        HorizontalDivider(color = primary.copy(alpha = 0.18f))
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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
                shadowElevation = 6.dp
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(18.dp)
                        .size(34.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (action != null) {
                Spacer(Modifier.height(18.dp))
                action()
            }
        }
    }
}

@Composable
fun nasCardColors(): CardColors {
    return CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun nasCardElevation(): CardElevation {
    return CardDefaults.cardElevation(
        defaultElevation = 4.dp,
        pressedElevation = 8.dp,
        focusedElevation = 6.dp,
        hoveredElevation = 6.dp
    )
}

@Composable
fun nasCardBorder(): BorderStroke {
    return BorderStroke(
        1.dp,
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            )
        )
    )
}

@Composable
fun NasIconContainer(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val motionEnabled = rememberNasMotionEnabled()
    val shadowElevation by animateDpAsState(
        targetValue = if (selected) 8.dp else 2.dp,
        animationSpec = nasMotionSpec(motionEnabled, NasMotion.Standard),
        label = "iconContainerElevation"
    )

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
            }
        ),
        shadowElevation = shadowElevation
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
    positive: Boolean,
    modifier: Modifier = Modifier
) {
    val container = if (positive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val content = if (positive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val motionEnabled = rememberNasMotionEnabled()

    Surface(
        modifier = modifier.nasAnimateContentSize(motionEnabled),
        shape = CircleShape,
        color = container.copy(alpha = 0.76f),
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.16f)),
        shadowElevation = 4.dp
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp),
        interactionSource = interactionSource
    ) {
        Icon(icon, contentDescription)
    }
}
