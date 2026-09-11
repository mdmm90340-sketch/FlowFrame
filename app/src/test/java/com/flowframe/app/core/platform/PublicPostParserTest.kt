package com.flowframe.app.core.platform

import com.flowframe.app.core.model.Platform
import org.junit.Assert.*
import org.junit.Test

class PublicPostParserTest {
    private val noteId = "674051740000000007027a15"
    private val noteUrl = "https://www.xiaohongshu.com/explore/$noteId?xsec_token=fixture"

    @Test fun preservesImageOrderAndAlternativesWithoutDuplicatingImages() {
        val html = xhs("""{"type":"normal","title":"图文","imageList":[
            {"width":1200,"height":800,"urlDefault":"https://cdn.example/a.jpg","urlPre":"https://cdn.example/a-preview.jpg"},
            {"width":800,"height":1200,"urlDefault":"https://cdn.example/b.png"}]}""")
        val post = PublicPostParser.xiaohongshu(html, noteUrl)
        val gallery = post.gallery!!
        assertEquals(noteId, post.mediaId)
        assertEquals(2, gallery.images.size)
        assertEquals(listOf("https://cdn.example/a.jpg", "https://cdn.example/b.png"), gallery.images.map { it.mirrors.first() })
        assertEquals(2, gallery.images.first().mirrors.size)
        assertEquals("https://www.xiaohongshu.com/", gallery.referer)
        assertNull(gallery.audio)
    }

    @Test fun videoCoverIsNotASecondMediaItem() {
        val html = xhs("""{"type":"video","video":{"media":{"stream":{"h264":[{"masterUrl":"https://cdn.example/video.mp4"}]}}},"imageList":[{"urlDefault":"https://cdn.example/cover.jpg"}]}""")
        assertNull(PublicPostParser.xiaohongshu(html, noteUrl).gallery)
    }

    @Test fun rejectsDeclaredMixedXiaohongshuPost() {
        expectMixed { PublicPostParser.xiaohongshu(xhs("""{"type":"mixed","imageList":[],"videoList":[{},{}]}"""), noteUrl) }
    }

    @Test fun acceptsUndefinedWithoutChangingLiteralTextOrExecutingScripts() {
        val state = PublicPostParser.embeddedObject("""window.__INITIAL_STATE__={"text":"undefined and }", "missing":undefined}; alert('no');""", "window.__INITIAL_STATE__")!!
        assertEquals("undefined and }", state.text("text"))
        assertEquals("null", state["missing"].toString())
        assertNull(PublicPostParser.embeddedObject("window.__INITIAL_STATE__=executeCode()", "window.__INITIAL_STATE__"))
    }

    @Test fun weiboGalleryUsesPicIdsOrderAndOriginalMimeUnmodified() {
        val post = PublicPostParser.weibo(PublicPostParser.parseJson("""{
            "idstr":"123456","text_raw":"图文", "pic_ids":["b","a"],"pic_infos":{
                "a":{"largest":{"url":"https://wx1.sinaimg.cn/large/a.jpg","width":1200,"height":800}},
                "b":{"largest":{"url":"https://wx1.sinaimg.cn/large/b.png","width":800,"height":1200}}
            }}"""), "https://weibo.com/123/AbC")
        assertEquals(listOf("https://wx1.sinaimg.cn/large/b.png", "https://wx1.sinaimg.cn/large/a.jpg"), post.gallery!!.images.map { it.mirrors.first() })
        assertEquals("https://weibo.com/", post.gallery.referer)
    }

    @Test fun missingMiddleImageRejectsTheWholeGalleryRatherThanChangingSelectionIndices() {
        val xhsHtml = xhs("""{"type":"normal","imageList":[
            {"urlDefault":"https://cdn.example/first.jpg"},{"width":800},
            {"urlDefault":"https://cdn.example/third.jpg"}]}""")
        val weiboData = PublicPostParser.parseJson("""{"id":"123","pic_ids":["first","missing","third"],"pic_infos":{
            "first":{"largest":{"url":"https://cdn.example/first.jpg"}},
            "third":{"largest":{"url":"https://cdn.example/third.jpg"}}
        }}""")
        val kuaishouHtml = """window.INIT_STATE={"photo":{"id":"target","atlasList":[
            "https://cdn.example/first.jpg",{},"https://cdn.example/third.jpg"]}};"""
        val parsers = listOf<() -> PublicPost>(
            { PublicPostParser.xiaohongshu(xhsHtml, noteUrl) },
            { PublicPostParser.weibo(weiboData, "https://weibo.com/123/AbC") },
            { PublicPostParser.kuaishou(kuaishouHtml, "https://www.kuaishou.com/short-video/target") },
        )
        parsers.forEach { parse ->
            val error = assertThrows(PublicContentException::class.java) { parse() }
            assertTrue(error.message.orEmpty().contains("图集图片信息不完整"))
        }
    }

    @Test fun refusesToSilentlyDiscardWeiboPicturesOrExtraVideos() {
        expectMixed { PublicPostParser.weibo(PublicPostParser.parseJson("""{"id":"123","pic_ids":["a"],"page_info":{"media_info":{}}}"""), "https://weibo.com/123/AbC") }
        expectMixed { PublicPostParser.weibo(PublicPostParser.parseJson("""{"id":"123","mix_media_info":{"items":[{"type":"video"},{"type":"video"}]}}"""), "https://weibo.com/123/AbC") }
    }

    @Test fun kuaishouSelectsOnlyTheRequestedWork() {
        val html = """window.__APOLLO_STATE__={"defaultClient":{"recommended":{"id":"other","photoUrl":"https://cdn.example/wrong.mp4"},"work":{"id":"target","caption":"目标作品","photoUrl":"https://cdn.example/right.mp4"}}};"""
        val result = PublicPostParser.kuaishou(html, "https://www.kuaishou.com/short-video/target")
        assertEquals("https://cdn.example/right.mp4", result.videoUrl)
        assertEquals("目标作品", result.title)
        assertThrows(PublicContentException::class.java) { PublicPostParser.kuaishou(html, "https://www.kuaishou.com/short-video/missing") }
    }

    @Test fun challengeAndMissingPagesNeverInventMedia() {
        val error = assertThrows(PublicContentException::class.java) {
            PublicPostParser.kuaishou("<title>安全验证</title>", "https://www.kuaishou.com/short-video/target")
        }
        assertEquals("平台要求安全验证", error.message)
        assertThrows(PublicContentException::class.java) { PublicPostParser.xiaohongshu("{}", noteUrl) }
    }

    @Test fun redirectBoundaryRejectsAnotherPlatformAndUnsafeSchemes() {
        val client = PublicPageClient()
        listOf("https://www.weibo.com.evil.example/x", "https://user@weibo.com/x", "http://weibo.com/x", "https://127.0.0.1/x").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { client.safeUri(url, Platform.WEIBO) }
        }
        assertEquals("passport.weibo.com", client.safeUri("https://passport.weibo.com/visitor/visitor", Platform.WEIBO).host)
    }

    private fun xhs(note: String): String = "window.__INITIAL_STATE__={\"note\":{\"noteDetailMap\":{\"$noteId\":{\"note\":$note}}}};"
    private fun expectMixed(action: () -> Unit) {
        assertEquals(PublicPostParser.MIXED_CONTENT_MESSAGE, assertThrows(PublicContentException::class.java, action).message)
    }
}
