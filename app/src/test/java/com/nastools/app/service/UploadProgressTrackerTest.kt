package com.nastools.app.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadProgressTrackerTest {

    @Test
    fun concurrentActiveFiles_areAggregatedWithCompletedBytes() = runTest {
        val events = mutableListOf<Pair<Long, Long>>()
        val tracker = UploadProgressTracker(totalTaskBytes = 300L) { uploaded, total ->
            events += uploaded to total
        }

        tracker.updateActive("a.jpg", 50L)
        tracker.updateActive("b.jpg", 70L)
        tracker.finishActive("a.jpg", 100L)
        tracker.finishActive("b.jpg", 200L)

        assertEquals(
            listOf(50L to 300L, 120L to 300L, 170L to 300L, 300L to 300L),
            events
        )
        assertEquals(300L, tracker.completedBytes())
    }

    @Test
    fun progressNeverMovesBackward_whenActiveProgressIsClearedOrLowered() = runTest {
        val events = mutableListOf<Pair<Long, Long>>()
        val tracker = UploadProgressTracker(totalTaskBytes = 200L) { uploaded, total ->
            events += uploaded to total
        }

        tracker.updateActive("a.jpg", 120L)
        tracker.updateActive("a.jpg", 80L)
        tracker.clearActive("a.jpg")
        tracker.markComplete(40L)

        assertEquals(listOf(120L to 200L, 120L to 200L, 120L to 200L), events)
        assertEquals(40L, tracker.completedBytes())
    }

    @Test
    fun unknownTotal_reportsUploadedBytesAsTotal() = runTest {
        val events = mutableListOf<Pair<Long, Long>>()
        val tracker = UploadProgressTracker(totalTaskBytes = 0L) { uploaded, total ->
            events += uploaded to total
        }

        tracker.updateActive("stream.bin", 64L)

        assertEquals(listOf(64L to 64L), events)
    }

    @Test
    fun formatUploadWarnings_keepsDeletionSummaryAndOtherWarnings() {
        val message = formatUploadWarnings(
            listOf(
                "本地文件 a.jpg",
                "本地文件夹 Album",
                "跳过 2 个已存在的文件夹",
                "跳过 2 个已存在的文件夹"
            )
        )

        assertEquals("上传完成，2 个本地项目未能删除；跳过 2 个已存在的文件夹", message)
    }

    @Test
    fun formatUploadWarnings_returnsNullWhenEmpty() {
        assertNull(formatUploadWarnings(emptyList()))
    }
}
