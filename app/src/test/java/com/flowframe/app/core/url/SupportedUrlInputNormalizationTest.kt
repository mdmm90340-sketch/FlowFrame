package com.flowframe.app.core.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedUrlInputNormalizationTest {
    @Test
    fun reportsNoLinkForBlankUnsupportedOrOversizedInput() {
        assertSame(
            InputNormalizationResult.NoSupportedLink,
            SupportedUrlParser.normalize("  \n  "),
        )
        assertSame(
            InputNormalizationResult.NoSupportedLink,
            SupportedUrlParser.normalize("https://example.com/video/BV17fTF6EEAc"),
        )
        assertSame(
            InputNormalizationResult.NoSupportedLink,
            SupportedUrlParser.normalize("a".repeat(SupportedUrlParser.MAX_INPUT_BYTES + 1)),
        )
    }

    @Test
    fun ignoresInvalidLinksWhenOneSupportedLinkRemains() {
        val result = SupportedUrlParser.normalize(
            "http://www.bilibili.com/video/BV17fTF6EEAc https://example.com/unsupported " +
                "https://v.douyin.com/DemoVideo_01/。",
        )

        assertEquals(
            "https://v.douyin.com/DemoVideo_01/",
            (result as InputNormalizationResult.SingleSupportedLink).link.value,
        )
    }

    @Test
    fun deduplicatesCanonicalLinksAndRetainsFirstSeenOrder() {
        val input = """
            HTTPS://B23.TV:443/DemoBV02#first
            https://v.douyin.com/DemoVideo_01/
            https://b23.tv/DemoBV02
            https://v.douyin.com/DemoGallery_02/
            https://v.douyin.com/DemoVideo_01/
        """.trimIndent()

        val result = SupportedUrlParser.normalize(input)

        assertTrue(result is InputNormalizationResult.MultipleSupportedLinks)
        assertEquals(
            listOf(
                "https://b23.tv/DemoBV02",
                "https://v.douyin.com/DemoVideo_01/",
                "https://v.douyin.com/DemoGallery_02/",
            ),
            (result as InputNormalizationResult.MultipleSupportedLinks).links.map { it.value },
        )
    }

    @Test
    fun repeatedMarkdownLinkIsStillUnique() {
        val input =
            "[https://v.douyin.com/DemoVideo_01/](https://v.douyin.com/DemoVideo_01/)"

        val result = SupportedUrlParser.normalize(input)

        assertEquals(
            "https://v.douyin.com/DemoVideo_01/",
            (result as InputNormalizationResult.SingleSupportedLink).link.value,
        )
    }

    @Test
    fun smartQuotesChinesePunctuationAndNewlinesEndTheUrl() {
        val input = "第一行\n‘https://b23.tv/DemoBV01’。\n最后一行"

        assertEquals(
            "https://b23.tv/DemoBV01",
            (SupportedUrlParser.normalize(input) as InputNormalizationResult.SingleSupportedLink)
                .link.value,
        )
    }

    @Test
    fun compatibilityExtractDoesNotGuessWhenInputIsAmbiguous() {
        val input =
            "https://v.douyin.com/DemoVideo_01/ https://b23.tv/DemoBV02"

        assertTrue(
            SupportedUrlParser.normalize(input) is InputNormalizationResult.MultipleSupportedLinks,
        )
        assertNull(SupportedUrlParser.extract(input))
    }

    @Test
    fun acceptsExactlySixteenKibibytesOfUtf8Input() {
        val link = "https://v.douyin.com/DemoVideo_01/"
        val remainingBytes = SupportedUrlParser.MAX_INPUT_BYTES - link.toByteArray().size - 1
        val input = buildString {
            append("中".repeat(remainingBytes / 3))
            append("a".repeat(remainingBytes % 3))
            append('\n')
            append(link)
        }
        assertEquals(SupportedUrlParser.MAX_INPUT_BYTES, input.toByteArray().size)

        assertTrue(
            SupportedUrlParser.normalize(input) is InputNormalizationResult.SingleSupportedLink,
        )
        assertSame(
            InputNormalizationResult.NoSupportedLink,
            SupportedUrlParser.normalize("a$input"),
        )
    }
}
