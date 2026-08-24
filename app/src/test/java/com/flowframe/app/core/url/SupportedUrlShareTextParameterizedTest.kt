package com.flowframe.app.core.url

import com.flowframe.app.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SupportedUrlShareTextParameterizedTest(
    private val shareText: String,
    private val expectedUrl: String,
    private val expectedPlatform: Platform,
) {
    @Test
    fun normalizesCompleteShareText() {
        val result = SupportedUrlParser.normalize(shareText)

        assertTrue(result is InputNormalizationResult.SingleSupportedLink)
        val link = (result as InputNormalizationResult.SingleSupportedLink).link
        assertEquals(expectedUrl, link.value)
        assertEquals(expectedPlatform, link.platform)
        assertEquals(link, SupportedUrlParser.extract(shareText))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "share text {index}: {1}")
        fun cases(): Collection<Array<Any>> = listOf(
            arrayOf(
                "9.74 复制打开抖音，看看【示例作者的视频作品】示例标题 # 测试  " +
                    "https://v.douyin.com/DemoVideo_01/ :7pm N@w.SL XMw:/ 04/25",
                "https://v.douyin.com/DemoVideo_01/",
                Platform.DOUYIN,
            ),
            arrayOf(
                "9.74 复制打开抖音，看看【示例作者的图文作品】# 示例图集  " +
                    "https://v.douyin.com/DemoGallery_02/ goq:/ A@t.EU 08/07 :1pm",
                "https://v.douyin.com/DemoGallery_02/",
                Platform.DOUYIN,
            ),
            arrayOf(
                "【示例知识视频-哔哩哔哩】 https://b23.tv/DemoBV01",
                "https://b23.tv/DemoBV01",
                Platform.BILIBILI,
            ),
            arrayOf(
                "【示例生活视频-哔哩哔哩】 https://b23.tv/DemoBV02",
                "https://b23.tv/DemoBV02",
                Platform.BILIBILI,
            ),
        )
    }
}
