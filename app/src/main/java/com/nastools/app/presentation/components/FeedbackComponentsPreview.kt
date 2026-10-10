package com.nastools.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasToolsTheme

@Preview(name = "反馈组件 · 浅色", showBackground = true)
@Composable
private fun FeedbackComponentsLightPreview() {
    NasToolsTheme(darkTheme = false) {
        FeedbackComponentsPreviewContent()
    }
}

@Preview(name = "反馈组件 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun FeedbackComponentsDarkPreview() {
    NasToolsTheme(darkTheme = true) {
        FeedbackComponentsPreviewContent()
    }
}

@Composable
private fun FeedbackComponentsPreviewContent() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(NasSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NasSpacing.lg)
        ) {
            NasErrorState(
                icon = Icons.Default.Error,
                title = "加载失败",
                message = "无法连接到 NAS，请检查网络后重试。",
                onRetry = {}
            )
        }
    }
}

@Preview(name = "确认对话框 · 浅色", showBackground = true)
@Composable
private fun ConfirmDialogLightPreview() {
    NasToolsTheme(darkTheme = false) {
        NasConfirmDialog(
            title = "取消任务",
            message = "任务会停止，但不会删除 NAS 上的文件。",
            confirmText = "取消任务",
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "确认对话框 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun ConfirmDialogDarkPreview() {
    NasToolsTheme(darkTheme = true) {
        NasConfirmDialog(
            title = "删除任务",
            message = "此操作无法撤销。",
            confirmText = "删除",
            onConfirm = {},
            onDismiss = {}
        )
    }
}
