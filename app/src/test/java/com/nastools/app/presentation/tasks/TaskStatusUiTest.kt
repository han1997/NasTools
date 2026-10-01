package com.nastools.app.presentation.tasks

import com.nastools.app.presentation.theme.NasStatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * status → tone 映射的回归防线（prd.md R5 / AC3）。
 *
 * 这一组断言针对的正是此前的真实缺陷：`waiting` / `paused` / `completed`
 * 三者被渲染成完全相同的绿色。任何未来的改动若让三者重新同色，这里会失败。
 */
class TaskStatusUiTest {

    @Test
    fun statusTone_mapsEachStatePerR5Table() {
        assertEquals(NasStatusTone.Neutral, "waiting".statusTone())
        assertEquals(NasStatusTone.Progress, "running".statusTone())
        assertEquals(NasStatusTone.Warning, "paused".statusTone())
        assertEquals(NasStatusTone.Success, "completed".statusTone())
        assertEquals(NasStatusTone.Danger, "failed".statusTone())
        assertEquals(NasStatusTone.Neutral, "cancelled".statusTone())
    }

    @Test
    fun statusTone_unknownStateFallsBackToNeutral() {
        assertEquals(NasStatusTone.Neutral, "whatever".statusTone())
    }

    @Test
    fun statusTone_waitingPausedCompletedAreThreeDistinctTones() {
        val waiting = "waiting".statusTone()
        val paused = "paused".statusTone()
        val completed = "completed".statusTone()

        // AC1: 三态不再同为绿色。
        assertNotEquals(waiting, paused)
        assertNotEquals(waiting, completed)
        assertNotEquals(paused, completed)
    }

    @Test
    fun statusTone_failedAndCancelledAreNotSameTone() {
        // AC2: 失败与取消不再同色（失败是故障，取消是用户决定）。
        assertNotEquals("failed".statusTone(), "cancelled".statusTone())
    }

    @Test
    fun statusTone_allSixStatesAreNotCollapsedToASingleTone() {
        val tones = listOf("waiting", "running", "paused", "completed", "failed", "cancelled")
            .map { it.statusTone() }
            .toSet()

        // AC1/AC2 的整体防线：至少覆盖 Neutral/Progress/Warning/Success/Danger 五档。
        assertEquals(5, tones.size)
    }

    @Test
    fun statusLabel_mapsEachStateAndPassesThroughUnknown() {
        assertEquals("等待", "waiting".statusLabel())
        assertEquals("运行中", "running".statusLabel())
        assertEquals("已暂停", "paused".statusLabel())
        assertEquals("完成", "completed".statusLabel())
        assertEquals("失败", "failed".statusLabel())
        assertEquals("已取消", "cancelled".statusLabel())
        assertEquals("mystery", "mystery".statusLabel())
    }
}
