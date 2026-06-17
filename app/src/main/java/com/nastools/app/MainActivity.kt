package com.nastools.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.nastools.app.presentation.navigation.NasToolsNavHost
import com.nastools.app.presentation.theme.NasToolsTheme
import com.nastools.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 立即设置内容以加快首屏渲染
        setContent {
            NasToolsTheme {
                NasToolsNavHost()
            }
        }

        // 延迟请求通知权限，避免阻塞首屏
        window.decorView.post {
            if (!PermissionHelper.hasNotificationPermission(this)) {
                PermissionHelper.requestNotificationPermission(this)
            }
        }
    }
}
