package com.flowframe.app.core.url

import com.flowframe.app.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedUrlParserTest {
    @Test
    fun extractsDouyinUrlFromShareText() {
        val result = SupportedUrlParser.extract(
            "复制打开抖音，看看这个视频 https://v.douyin.com/abc123/ 真的很好看！",
        )
        assertEquals(Platform.DOUYIN, result?.platform)
        assertEquals("https://v.douyin.com/abc123/", result?.value)
    }

    @Test
    fun recognizesBilibiliShortLinkAndTrimsPunctuation() {
        val result = SupportedUrlParser.extract("链接：https://b23.tv/AbCdEf。")
        assertEquals(Platform.BILIBILI, result?.platform)
        assertEquals("https://b23.tv/AbCdEf", result?.value)
    }

    @Test
    fun rejectsLookalikeAndCredentials() {
        assertNull(SupportedUrlParser.extract("https://bilibili.com.evil.example/video"))
        assertNull(SupportedUrlParser.extract("https://user:pass@bilibili.com/video/BV1"))
    }

    @Test
    fun rejectsOptionLikeOrUnsupportedInput() {
        assertNull(SupportedUrlParser.extract("--exec calc.exe"))
        assertNull(SupportedUrlParser.extract("https://example.com/video"))
        assertNull(SupportedUrlParser.extract("http://bilibili.com/video/BV1"))
        assertNull(SupportedUrlParser.extract("https://bilibili.com:444/video/BV1"))
    }
}
