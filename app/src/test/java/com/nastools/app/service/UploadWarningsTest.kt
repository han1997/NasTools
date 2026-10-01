package com.nastools.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadWarningsTest {

    // 1. 空 SkipLog → 无任何新增警告（保 AC4：正常上传不多出警告）
    @Test
    fun emptySkipLog_producesNoWarnings() {
        val skips = SkipLog()

        assertTrue(skips.isEmpty())
        assertEquals(emptyList<String>(), skips.toWarnings())
    }

    // 2. 少量同名跳过 → 一句汇总，含数量与文件名
    @Test
    fun fewSameNameSkips_areSummarizedWithNames() {
        val skips = SkipLog()
        skips.sameName.add("a.jpg")
        skips.sameName.add("b.jpg")
        skips.sameName.add("c.jpg")

        val warnings = skips.toWarnings()

        assertEquals(1, warnings.size)
        val message = warnings.single()
        assertTrue(message.contains("3 个同名文件"))
        assertTrue(message.contains("a.jpg"))
        assertTrue(message.contains("b.jpg"))
        assertTrue(message.contains("c.jpg"))
    }

    // 3. 超过 10 个 → 只列前 10 个，尾部以 " …" 收束，字符串有上界
    @Test
    fun manySameNameSkips_areTruncatedToTenNames() {
        val skips = SkipLog()
        (1..12).forEach { skips.sameName.add("file%02d.txt".format(it)) }

        val message = skips.toWarnings().single()

        assertTrue(message.contains("12 个同名文件"))
        assertTrue(message.contains("file01.txt"))
        assertTrue(message.contains("file10.txt"))
        assertFalse(message.contains("file11.txt"))
        assertFalse(message.contains("file12.txt"))
        assertTrue(message.endsWith(" …"))
    }

    // 4. 过滤规则跳过 → 独立文案，与同名文案不互相合并
    @Test
    fun filteredSkips_areReportedSeparatelyFromSameNameSkips() {
        val skips = SkipLog()
        skips.filtered.add("temp.log")
        skips.sameName.add("keep.jpg")

        val warnings = skips.toWarnings()

        assertEquals(2, warnings.size)
        val filteredMessage = warnings.first { it.contains("未匹配过滤规则") }
        val sameNameMessage = warnings.first { it.contains("同名文件") }
        assertEquals("1 个文件未匹配过滤规则，已跳过", filteredMessage)
        assertFalse(filteredMessage.contains("keep.jpg"))
        assertFalse(sameNameMessage.contains("temp.log"))
    }

    // 5. 空警告列表 → null
    @Test
    fun formatUploadWarnings_returnsNullWhenEmpty() {
        assertNull(formatUploadWarnings(emptyList()))
    }

    // 6. 跳过文案原样输出，不被「本地」类汇总吞并
    @Test
    fun formatUploadWarnings_keepsSkipMessageVerbatim() {
        val message = formatUploadWarnings(listOf("跳过 3 个同名文件：a、b、c"))

        assertEquals("跳过 3 个同名文件：a、b、c", message)
    }

    // 7. 「本地…」删除失败与跳过警告并存 → 两者都出现（前者折叠为计数句）
    @Test
    fun formatUploadWarnings_keepsDeletionSummaryAndSkipWarnings() {
        val message = formatUploadWarnings(
            listOf(
                "本地文件 a.jpg",
                "本地文件夹 Album",
                "跳过 35 个同名文件：a、b"
            )
        )

        assertEquals(
            "上传完成，2 个本地项目未能删除；跳过 35 个同名文件：a、b",
            message
        )
    }
}
