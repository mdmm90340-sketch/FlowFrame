package com.flowframe.app.core.platform

import com.flowframe.app.core.gallery.GalleryAssetSet
import com.flowframe.app.core.gallery.GalleryImageSource
import com.flowframe.app.core.model.Platform
import kotlinx.serialization.json.*
import java.net.URI

data class PublicPost(
    val mediaId: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val gallery: GalleryAssetSet? = null,
    val videoUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationSeconds: Int = 0,
    val canonicalUrl: String? = null,
)

class PublicContentException(message: String) : IllegalStateException(message)

/** Pure parsers: never execute a site's script and never select a recommended neighbouring post. */
object PublicPostParser {
    private val json = Json { ignoreUnknownKeys = true }
    const val MIXED_CONTENT_MESSAGE = "不支持的链接：当前暂不支持视频与正文图片混合或多视频作品"

    fun xiaohongshu(html: String, sourceUrl: String): PublicPost {
        val id = URI(sourceUrl).path.trimEnd('/').substringAfterLast('/')
        val state = embeddedObject(html, "window.__INITIAL_STATE__")
            ?: unavailable(html)
        val detail = state.obj("note")?.obj("noteDetailMap")
        val note = detail?.obj(id)?.obj("note")
            ?: detail?.values?.mapNotNull { (it as? JsonObject)?.obj("note") }
                ?.firstOrNull { it.text("noteId") == id }
            ?: unavailable(html)
        val video = note.obj("video")
        val streams = video?.obj("media")?.obj("stream")
        val hasVideo = streams?.values?.any { (it as? JsonArray)?.isNotEmpty() == true } == true ||
            video?.obj("consumer")?.text("originVideoKey")?.isNotBlank() == true
        val isImagePost = note.text("type") in setOf("normal", "image", "images")
        // A video note's imageList contains its cover. Only a declared image/mixed note
        // or an explicit separate video list constitutes mixed body content.
        if ((isImagePost && hasVideo) || note.array("videoList").size > 1 || note.text("type") == "mixed") mixed()
        val images = note.array("imageList").mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val urls = buildList {
                item.text("urlDefault")?.let(::add)
                item.array("infoList").mapNotNull { (it as? JsonObject)?.text("url") }.forEach(::add)
                item.text("urlPre")?.let(::add)
            }.mapNotNull(::mediaUrl).distinct()
            urls.takeIf { it.isNotEmpty() }?.let { GalleryImageSource(item.number("width"), item.number("height"), it) }
        }
        val title = note.text("title")?.ifBlank { null } ?: note.text("desc")?.take(100)?.ifBlank { null } ?: "未命名作品"
        val uploader = note.obj("user")?.text("nickname")
        if (hasVideo) return PublicPost(id, title, uploader, images.firstOrNull()?.mirrors?.firstOrNull())
        return gallery(sourceUrl, Platform.XIAOHONGSHU, id, title, uploader, images, note.array("imageList").size)
    }

    fun weibo(data: JsonObject, sourceUrl: String): PublicPost {
        val post = data.obj("data")?.takeIf { it["id"] != null || it["mid"] != null } ?: data
        if (post["id"] == null && post["mid"] == null && post["idstr"] == null) {
            val message = post.text("msg").orEmpty() + post.text("message").orEmpty()
            if (message.contains("登录")) throw PublicContentException("需要登录后访问此微博")
            if (message.contains("删除") || post.number("error_code") == 20101) throw PublicContentException("作品已删除")
            throw PublicContentException("公开页面未提供可下载媒体")
        }
        val id = post.text("idstr") ?: post.text("id") ?: post.text("mid")!!
        val mixed = post.obj("mix_media_info")?.array("items").orEmpty()
        val videos = mixed.count { (it as? JsonObject)?.text("type") in setOf("video", "story") }
        val bodyPictures = mixed.count { (it as? JsonObject)?.text("type") == "pic" }
        val hasVideo = videos > 0 || post.obj("page_info")?.let {
            it.obj("media_info") != null || it.text("object_type") in setOf("video", "story")
        } == true
        val pics = post.array("pics")
        val picIds = post.array("pic_ids")
        if (videos > 1 || (hasVideo && (bodyPictures > 0 || pics.isNotEmpty() || picIds.isNotEmpty()))) mixed()
        val title = post.text("text_raw") ?: post.text("text")?.replace(Regex("<[^>]+>"), "") ?: "未命名微博"
        val uploader = post.obj("user")?.text("screen_name")
        if (hasVideo) return PublicPost(
            id, title.take(160), uploader, post.obj("page_info")?.obj("page_pic")?.text("url"),
            canonicalUrl = "https://weibo.com/${post.obj("user")?.text("id") ?: "0"}/$id",
        )
        val picInfo = post.obj("pic_infos")
        val ordered = when {
            picIds.isNotEmpty() && picInfo != null -> picIds.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(picInfo::obj) }
            pics.isNotEmpty() -> pics.mapNotNull { it as? JsonObject }
            else -> mixed.mapNotNull { (it as? JsonObject)?.takeIf { it.text("type") == "pic" }?.obj("data") }
        }
        val images = ordered.mapNotNull { item ->
            val original = item.obj("largest") ?: item.obj("original") ?: item.obj("large") ?: item
            val urls = listOfNotNull(original.text("url"), item.text("url"), item.obj("large")?.text("url"))
                .mapNotNull(::mediaUrl).distinct()
            urls.takeIf { it.isNotEmpty() }?.let { GalleryImageSource(original.number("width"), original.number("height"), it) }
        }
        val expectedImageCount = when {
            picIds.isNotEmpty() -> picIds.size
            pics.isNotEmpty() -> pics.size
            else -> bodyPictures
        }
        return gallery(sourceUrl, Platform.WEIBO, id, title.take(160), uploader, images, expectedImageCount)
    }

    fun kuaishou(html: String, sourceUrl: String): PublicPost {
        val id = URI(sourceUrl).path.trimEnd('/').substringAfterLast('/')
        val state = embeddedObject(html, "window.__APOLLO_STATE__")
            ?: embeddedObject(html, "window.INIT_STATE")
            ?: embeddedObject(html, "window.__INITIAL_STATE__")
            ?: unavailable(html)
        val post = objects(state).firstOrNull {
            (it.text("id") == id || it.text("photoId") == id) &&
                (it["photoUrl"] != null || it["videoResource"] != null || it["atlas"] != null || it["atlasList"] != null)
        } ?: unavailable(html)
        val video = mediaUrl(post.text("photoUrl")) ?: objects(post["videoResource"])
            .firstNotNullOfOrNull { mediaUrl(it.text("url")) }
        val atlas = post.obj("atlas")
        val atlasItems = post.array("atlasList").ifEmpty { atlas?.array("list").orEmpty() }
        if ((video != null && atlasItems.isNotEmpty()) || post.array("videos").size > 1) mixed()
        val title = post.text("caption")?.ifBlank { null } ?: "未命名快手作品"
        val uploader = post.obj("user")?.text("name") ?: post.text("userName")
        if (video != null) return PublicPost(
            id, title, uploader, mediaUrl(post.text("coverUrl")), videoUrl = video,
            width = post.number("width"), height = post.number("height"),
            durationSeconds = (post.longNumber("duration") / 1_000L).toInt(),
        )
        val images = atlasItems.mapNotNull { element ->
            val item = element as? JsonObject
            val url = mediaUrl(item?.text("url") ?: (element as? JsonPrimitive)?.contentOrNull)
            url?.let { GalleryImageSource(item?.number("width") ?: 0, item?.number("height") ?: 0, listOf(it)) }
        }
        return gallery(sourceUrl, Platform.KUAISHOU, id, title, uploader, images, atlasItems.size)
    }

    fun parseJson(value: String): JsonObject = json.parseToJsonElement(value).jsonObject

    fun embeddedObject(html: String, marker: String): JsonObject? {
        val markerStart = html.indexOf(marker).takeIf { it >= 0 } ?: return null
        val start = html.indexOf('{', markerStart).takeIf { it >= 0 && it - markerStart < 160 } ?: return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until html.length) {
            val char = html[index]
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return runCatching { parseJson(normalizeUndefined(html.substring(start, index + 1))) }.getOrNull()
                }
            }
        }
        return null
    }

    private fun normalizeUndefined(source: String): String =
        Regex("\"(?:\\\\.|[^\"\\\\])*\"|\\b(?:undefined|NaN)\\b").replace(source) {
            if (it.value.startsWith('"')) it.value else "null"
        }

    private fun gallery(url: String, platform: Platform, id: String, title: String, uploader: String?, images: List<GalleryImageSource>, expectedImageCount: Int): PublicPost {
        if (images.size != expectedImageCount) throw PublicContentException("公开页面未提供可下载媒体：图集图片信息不完整，请重新复制作品分享链接")
        if (images.isEmpty()) throw PublicContentException("公开页面未提供可下载媒体")
        if (images.size > 100) throw PublicContentException("不支持的链接：图集超过 100 张图片")
        val gallery = GalleryAssetSet(url, id, title, uploader, images, null, emptyList(), PlatformCatalog.get(platform).homeUrl)
        return PublicPost(id, title, uploader, images.first().mirrors.first(), gallery)
    }

    private fun unavailable(html: String): Nothing {
        val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1).orEmpty()
        when {
            listOf("验证码", "安全验证", "captcha", "访问验证").any { title.contains(it, true) } -> throw PublicContentException("平台要求安全验证")
            title.contains("登录") || title.contains("log in", true) -> throw PublicContentException("需要登录后访问此内容")
            listOf("不存在", "已删除", "无法访问").any(title::contains) -> throw PublicContentException("作品已删除或不可见")
            else -> throw PublicContentException("公开页面未提供可下载媒体，请重新复制当前作品的分享链接")
        }
    }

    private fun mixed(): Nothing = throw PublicContentException(MIXED_CONTENT_MESSAGE)
    private fun objects(value: JsonElement?): Sequence<JsonObject> = sequence {
        when (value) {
            is JsonObject -> { yield(value); value.values.forEach { yieldAll(objects(it)) } }
            is JsonArray -> value.forEach { yieldAll(objects(it)) }
            else -> Unit
        }
    }

    internal fun mediaUrl(value: String?): String? {
        val normalized = value?.trim()?.let { if (it.startsWith("//")) "https:$it" else it } ?: return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("https", "http") || uri.rawUserInfo != null || uri.host == null || uri.port !in setOf(-1, 443, 80)) return null
        // Platforms sometimes advertise an HTTP CDN URL; never transmit the request in cleartext.
        return if (uri.scheme == "http") normalized.replaceFirst("http://", "https://") else normalized
    }
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList().orEmpty()
internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.number(key: String): Int = (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt()?.coerceAtLeast(0) ?: 0
internal fun JsonObject.longNumber(key: String): Long = (this[key] as? JsonPrimitive)?.doubleOrNull?.toLong()?.coerceAtLeast(0) ?: 0
