package com.flowframe.app.core.platform

import com.flowframe.app.core.model.Platform
import java.io.ByteArrayOutputStream
import java.net.HttpCookie
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.cancellation.CancellationException

data class PublicPage(val url: String, val body: String)

/** Bounded, anonymous, same-platform HTTP. Cookie state is discarded at operation end. */
class PublicPageClient {
    private data class Session(
        val connections: MutableSet<HttpsURLConnection> = ConcurrentHashMap.newKeySet(),
        val cookies: MutableMap<String, MutableMap<String, String>> = ConcurrentHashMap(),
        @Volatile var canceled: Boolean = false,
    )
    private val sessions = ConcurrentHashMap<String, Session>()

    fun begin(id: String) { check(sessions.putIfAbsent(id, Session()) == null) { "解析操作仍在运行" } }
    fun end(id: String) { sessions.remove(id)?.connections?.forEach { it.disconnect() } }
    fun cancel(id: String) {
        sessions[id]?.let { session ->
            session.canceled = true
            session.connections.forEach { it.disconnect() }
        }
    }

    fun get(url: String, platform: Platform, processId: String, form: String? = null, referer: String? = null): PublicPage {
        val session = sessions[processId] ?: error("公开页面解析会话未初始化")
        var current = safeUri(url, platform)
        var requestBody = form
        repeat(6) { redirect ->
            checkActive(session)
            val connection = current.toURL().openConnection() as HttpsURLConnection
            session.connections.add(connection)
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            connection.setRequestProperty("Referer", referer ?: PlatformCatalog.get(platform).homeUrl)
            session.cookies[current.host]?.takeIf { it.isNotEmpty() }?.let { cookies ->
                connection.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            try {
                checkActive(session)
                if (requestBody != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    connection.outputStream.use { it.write(requestBody!!.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                checkActive(session)
                connection.headerFields.filterKeys { it.equals("Set-Cookie", true) }.values.flatten().forEach { raw ->
                    runCatching { HttpCookie.parse(raw) }.getOrDefault(emptyList()).forEach { cookie ->
                        // Only server-issued anonymous cookies, restricted to observed official hosts.
                        val domain = cookie.domain?.trimStart('.') ?: current.host
                        PlatformCatalog.pageHosts(platform).filter { it == domain || it.endsWith(".$domain") }.forEach { host ->
                            session.cookies.getOrPut(host) { ConcurrentHashMap() }[cookie.name] = cookie.value
                        }
                    }
                }
                if (status in setOf(301, 302, 303, 307, 308)) {
                    if (redirect == 5) throw PublicContentException("公开页面未提供可下载媒体：重定向过多")
                    val target = connection.getHeaderField("Location") ?: throw PublicContentException("公开页面未提供可下载媒体")
                    current = safeUri(current.resolve(target).toString(), platform)
                    if (status in setOf(301, 302, 303)) requestBody = null
                    return@repeat
                }
                if (status == 404 || status == 410) throw PublicContentException("作品已删除或不可见")
                if (status !in 200..299) throw PublicContentException("HTTP Error $status")
                require(connection.contentLengthLong <= MAX_PAGE_BYTES) { "公开页面超过读取大小限制" }
                val bytes = ByteArrayOutputStream()
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(16_384)
                    while (true) {
                        checkActive(session)
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(bytes.size() + count <= MAX_PAGE_BYTES) { "公开页面超过读取大小限制" }
                        bytes.write(buffer, 0, count)
                    }
                }
                return PublicPage(current.toString(), bytes.toString(Charsets.UTF_8.name()))
            } catch (error: Exception) {
                checkActive(session)
                throw error
            } finally {
                session.connections.remove(connection)
                connection.disconnect()
            }
        }
        throw PublicContentException("公开页面未提供可下载媒体")
    }

    private fun checkActive(session: Session) {
        if (session.canceled || Thread.currentThread().isInterrupted) throw CancellationException("解析已取消")
    }

    internal fun safeUri(url: String, platform: Platform): URI {
        val uri = runCatching { URI(url) }.getOrElse { throw IllegalArgumentException("不支持的链接") }
        require(uri.scheme == "https" && uri.rawUserInfo == null && uri.port in setOf(-1, 443)) { "不支持的链接：页面跳转不是安全地址" }
        require(uri.host?.lowercase() in PlatformCatalog.pageHosts(platform)) { "不支持的链接：页面跳转离开原平台" }
        require(uri.toString().length <= 8_192) { "不支持的链接：页面地址过长" }
        return uri
    }

    companion object {
        private const val MAX_PAGE_BYTES = 8 * 1024 * 1024
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    }
}
