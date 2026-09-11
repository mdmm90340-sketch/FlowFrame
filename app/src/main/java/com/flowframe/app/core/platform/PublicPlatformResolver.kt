package com.flowframe.app.core.platform

import com.flowframe.app.core.model.Platform
import com.flowframe.app.core.url.SupportedUrlParser
import java.net.URI
import java.net.URLEncoder

class PublicPlatformResolver(private val client: PublicPageClient = PublicPageClient()) {
    fun cancel(processId: String) = client.cancel(processId)

    fun resolve(url: String, platform: Platform, processId: String): PublicPost {
        val supported = SupportedUrlParser.validate(url)
        require(supported?.platform == platform) { "不支持的链接" }
        client.begin(processId)
        try {
            return when (platform) {
                Platform.XIAOHONGSHU -> {
                    val page = client.get(supported!!.value, platform, processId)
                    requireContentUrl(page, platform)
                    PublicPostParser.xiaohongshu(page.body, page.url)
                }
                Platform.KUAISHOU -> {
                    val page = client.get(supported!!.value, platform, processId)
                    requireContentUrl(page, platform)
                    PublicPostParser.kuaishou(page.body, page.url)
                }
                Platform.WEIBO -> weibo(supported!!.value, processId)
                else -> throw IllegalArgumentException("不支持的链接：此平台应使用视频解析引擎")
            }
        } finally {
            client.end(processId)
        }
    }

    private fun weibo(url: String, processId: String): PublicPost {
        var sourceUrl = url
        var uri = URI(sourceUrl)
        if (uri.host == "t.cn") {
            sourceUrl = client.get(sourceUrl, Platform.WEIBO, processId).url
            uri = URI(sourceUrl)
        }
        // TV pages use an object ID, and their canonical post ID is supplied by this
        // same anonymous endpoint already used by yt-dlp's WeiboVideo extractor.
        val videoFid = when {
            uri.path.startsWith("/tv/show/") -> uri.path.trimEnd('/').substringAfterLast('/')
            uri.host == "video.weibo.com" -> uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("fid=") }?.substringAfter('=')?.replace("%3A", ":", true)
            else -> null
        }
        val id = if (videoFid != null) {
            val body = "data=" + encode("{\"Component_Play_Playinfo\":{\"oid\":\"$videoFid\"}}")
            val result = weiboJson("https://weibo.com/tv/api/component?page=" + encode("/tv/show/$videoFid"), processId, body)
            result.obj("data")?.obj("Component_Play_Playinfo")?.text("mid")
                ?: throw PublicContentException("公开页面未提供可下载媒体")
        } else {
            require(SupportedUrlParser.validate(sourceUrl)?.platform == Platform.WEIBO) { "不支持的链接" }
            uri.path.trimEnd('/').substringAfterLast('/')
        }
        val post = weiboJson("https://weibo.com/ajax/statuses/show?id=" + encode(id), processId)
        return PublicPostParser.weibo(post, sourceUrl)
    }

    private fun weiboJson(url: String, processId: String, form: String? = null): kotlinx.serialization.json.JsonObject {
        var page = client.get(url, Platform.WEIBO, processId, form)
        if (URI(page.url).host == "passport.weibo.com") {
            val fingerprint = "{\"os\":\"1\",\"browser\":\"Chrome140,0,0,0\",\"fonts\":\"undefined\",\"screenInfo\":\"1920*1080*24\",\"plugins\":\"\"}"
            val generated = client.get(
                "https://passport.weibo.com/visitor/genvisitor", Platform.WEIBO, processId,
                "cb=gen_callback&fp=" + encode(fingerprint),
                referer = page.url,
            ).body
            val start = generated.indexOf('{')
            val end = generated.lastIndexOf('}')
            if (start < 0 || end <= start) throw PublicContentException("公开页面未提供可下载媒体")
            val data = PublicPostParser.parseJson(generated.substring(start, end + 1)).obj("data")
                ?: throw PublicContentException("公开页面未提供可下载媒体")
            val tid = data.text("tid") ?: throw PublicContentException("公开页面未提供可下载媒体")
            client.get(
                "https://passport.weibo.com/visitor/visitor?a=incarnate&t=" + encode(tid) +
                    "&w=" + (if (data.text("new_tid") in setOf("true", "1")) "3" else "2") +
                    "&c=" + (if (data["confidence"] != null) data.number("confidence") else 100).coerceIn(0, 100).toString().padStart(3, '0') +
                    "&gc=&cb=cross_domain&from=weibo", Platform.WEIBO, processId, referer = page.url,
            )
            page = client.get(url, Platform.WEIBO, processId, form)
        }
        if (URI(page.url).path.contains("login", true)) throw PublicContentException("需要登录后访问此微博")
        return runCatching { PublicPostParser.parseJson(page.body) }
            .getOrElse { throw PublicContentException("公开页面未提供可下载媒体") }
    }

    private fun requireContentUrl(page: PublicPage, platform: Platform) {
        if (URI(page.url).path.contains("login", true)) throw PublicContentException("需要登录后访问此内容")
        require(SupportedUrlParser.validate(page.url)?.platform == platform) { "不支持的链接：分享地址未指向作品" }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
