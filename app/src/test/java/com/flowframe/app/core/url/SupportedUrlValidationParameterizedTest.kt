package com.flowframe.app.core.url

import com.flowframe.app.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SupportedUrlValidationParameterizedTest(
    private val candidate: String,
    private val expectedUrl: String,
    private val expectedPlatform: Platform,
) {
    @Test
    fun acceptsSupportedHostAndPath() {
        val result = SupportedUrlParser.validate(candidate)

        assertNotNull(result)
        assertEquals(expectedUrl, result?.value)
        assertEquals(expectedPlatform, result?.platform)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "valid {index}: {0}")
        fun cases(): Collection<Array<Any>> = listOf(
            arrayOf(
                "HTTPS://V.DOUYIN.COM:443/DemoVideo_01/#share",
                "https://v.douyin.com/DemoVideo_01/",
                Platform.DOUYIN,
            ),
            arrayOf(
                "https://www.douyin.com/video/7529534988769123620?previous_page=app_code_link",
                "https://www.douyin.com/video/7529534988769123620?previous_page=app_code_link",
                Platform.DOUYIN,
            ),
            arrayOf(
                "https://m.douyin.com/note/7529534988769123621/",
                "https://m.douyin.com/note/7529534988769123621/",
                Platform.DOUYIN,
            ),
            arrayOf(
                "https://www.iesdouyin.com/share/video/7529534988769123622/",
                "https://www.iesdouyin.com/share/video/7529534988769123622/",
                Platform.DOUYIN,
            ),
            arrayOf(
                "https://b23.tv/DemoBV02",
                "https://b23.tv/DemoBV02",
                Platform.BILIBILI,
            ),
            arrayOf(
                "https://www.bilibili.com/video/BV17fTF6EEAc?p=2",
                "https://www.bilibili.com/video/BV17fTF6EEAc?p=2",
                Platform.BILIBILI,
            ),
        )
    }
}

@RunWith(Parameterized::class)
class UnsupportedUrlValidationParameterizedTest(
    private val candidate: String,
) {
    @Test
    fun rejectsUnsupportedOrUnsafeUrl() {
        assertNull(SupportedUrlParser.validate(candidate))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "invalid {index}: {0}")
        fun cases(): Collection<Array<String>> = listOf(
            arrayOf("http://www.bilibili.com/video/BV17fTF6EEAc"),
            arrayOf("https://bilibili.com.evil.example/video/BV17fTF6EEAc"),
            arrayOf("https://user:pass@www.bilibili.com/video/BV17fTF6EEAc"),
            arrayOf("https://www.bilibili.com:444/video/BV17fTF6EEAc"),
            arrayOf("https://www.bilibili.com:/video/BV17fTF6EEAc"),
            arrayOf("https://foo.bilibili.com/video/BV17fTF6EEAc"),
            arrayOf("https://www.bilibili.com/bangumi/BV17fTF6EEAc"),
            arrayOf("https://www.bilibili.com/video/bv17fTF6EEAc"),
            arrayOf("https://www.bilibili.com/video/BV17fTF6EEAc/extra"),
            arrayOf("https://www.bilibili.com/video/BV1"),
            arrayOf("https://v.douyin.com/DemoVideo_01/extra"),
            arrayOf("https://v.douyin.com/Demo%2FVideo_01/"),
            arrayOf("https://www.douyin.com/video/not-a-number"),
            arrayOf("https://example.com/video/BV17fTF6EEAc"),
        )
    }
}
