package com.nastools.app.presentation.browser

import com.nastools.app.data.network.RemoteEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUiStateTest {
    @Test
    fun blockingError_isShownWhenThereIsNoContent() {
        val state = BrowserUiState(pageErrorMessage = "网络连接失败")

        assertTrue(state.shouldShowBlockingError())
    }

    @Test
    fun blockingError_isNotShownWhenOldContentCanBeDisplayed() {
        val state = BrowserUiState(
            entries = listOf(RemoteEntry(path = "/photo.jpg", name = "photo.jpg", isDirectory = false)),
            pageErrorMessage = "刷新失败"
        )

        assertFalse(state.shouldShowBlockingError())
    }

    @Test
    fun blockingError_isNotShownWithoutAnError() {
        val state = BrowserUiState()

        assertFalse(state.shouldShowBlockingError())
    }
}
