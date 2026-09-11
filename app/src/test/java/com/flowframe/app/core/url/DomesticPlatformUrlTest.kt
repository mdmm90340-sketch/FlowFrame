package com.flowframe.app.core.url

import com.flowframe.app.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomesticPlatformUrlTest {
    @Test fun recognizesDomesticShareLinksWithoutLosingShareParameters() {
        val examples = mapOf(
            "https://www.xiaohongshu.com/explore/674051740000000007027a15?xsec_token=fixture%2Bvalue%3D" to Platform.XIAOHONGSHU,
            "https://www.xiaohongshu.com/discovery/item/674051740000000007027a15" to Platform.XIAOHONGSHU,
            "https://xhslink.com/a/AbCdEf" to Platform.XIAOHONGSHU,
            "https://weibo.com/123456/N4xlMvjhI" to Platform.WEIBO,
            "https://m.weibo.cn/detail/4910815147462302" to Platform.WEIBO,
            "https://weibo.com/2/detail/4910815147462302" to Platform.WEIBO,
            "https://weibo.com/tv/show/1034:4797699866951785" to Platform.WEIBO,
            "https://v.kuaishou.com/AbCdEf" to Platform.KUAISHOU,
            "https://www.kuaishou.com/short-video/3x5jvmsmmiahx3m?shareToken=fixture" to Platform.KUAISHOU,
        )
        examples.forEach { (url, platform) ->
            val result = SupportedUrlParser.extract("复制链接 $url 看看这条作品")
            assertEquals(url, result?.value)
            assertEquals(platform, result?.platform)
        }
    }

    @Test fun upgradesOnlyOfficialShortHttpLinksBeforeAnyNetworkRequest() {
        listOf("xhslink.com/a/AbC", "t.cn/AbC", "v.kuaishou.com/AbC", "v.douyin.com/AbC", "b23.tv/AbC").forEach {
            assertEquals("https://$it", SupportedUrlParser.validate("http://$it")?.value)
        }
        assertNull(SupportedUrlParser.validate("http://www.weibo.com/123/AbC"))
        assertNull(SupportedUrlParser.validate("http://xhslink.com:443/a/AbC"))
    }

    @Test fun rejectsLookalikesProfilesAndRedirectInjection() {
        listOf(
            "https://xiaohongshu.com.evil.example/explore/674051740000000007027a15",
            "https://user@weibo.com/123/AbC", "https://www.weibo.com:444/123/AbC",
            "https://www.kuaishou.com/profile/3xt4m66xxjt3qgq", "https://weibo.com/u/123",
            "https://xhslink.com/a/AbC/extra", "https://v.kuaishou.com/%2e%2e/internal",
            "https://www.xiaohongshu.com/user/profile/674051740000000007027a15",
        ).forEach { assertNull(it, SupportedUrlParser.validate(it)) }
    }
}
