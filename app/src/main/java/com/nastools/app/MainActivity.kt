package com.nastools.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nastools.app.presentation.navigation.NasToolsNavHost
import com.nastools.app.presentation.theme.NasToolsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NasToolsTheme {
                NasToolsNavHost()
            }
        }
    }
}
