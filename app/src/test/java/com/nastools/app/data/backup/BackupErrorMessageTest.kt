package com.nastools.app.data.backup

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 错误归一化(AC8):底层 HTTP 码与网络异常必须翻译为用户可理解的文案,
 * 不把 `PUT failed: 403` 这类原始消息直接展示。
 */
class BackupErrorMessageTest {

    @Test
    fun httpCodesMapToReadableMessages() {
        assertEquals("服务器要求登录,请检查用户名与密码", IOException("PROPFIND failed: 401").readableMessage())
        assertEquals("权限不足,服务器拒绝访问", IOException("PUT failed: 403").readableMessage())
        assertEquals("远端目录不存在,请检查连接默认目录", IOException("GET failed: 404").readableMessage())
        assertEquals("远端返回 HTTP 418", IOException("PUT failed: 418").readableMessage())
        assertEquals("服务器内部错误(HTTP 500)", IOException("PUT failed: 500").readableMessage())
    }

    @Test
    fun networkExceptionsMapByType() {
        assertEquals(
            "无法解析服务器地址,请检查网络与地址",
            UnknownHostException("nas.example").readableMessage()
        )
        assertEquals(
            "连接服务器超时,请检查网络后重试",
            SocketTimeoutException("timeout").readableMessage()
        )
        assertEquals(
            "无法连接到服务器,请检查网络与地址",
            ConnectException("refused").readableMessage()
        )
    }

    @Test
    fun unknownMessageAndBlankFallBackSafely() {
        // 非 HTTP 码的自定义消息原样保留(例如「未找到备份文件:...」)
        assertEquals("未找到备份文件:/a/b.json,请先在该位置执行备份", IOException("未找到备份文件:/a/b.json,请先在该位置执行备份").readableMessage())
        // 空消息有兜底,不会把 null/空白抛给用户
        assertEquals("网络或服务器错误", IOException().readableMessage())
        assertEquals("网络或服务器错误", IOException("   ").readableMessage())
    }
}