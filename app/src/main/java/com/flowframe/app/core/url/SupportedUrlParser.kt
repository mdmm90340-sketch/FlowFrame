package com.flowframe.app.core.url

import com.flowframe.app.core.model.Platform
import java.net.URI
import java.util.Locale

data class SupportedUrl(
    val value: String,
    val platform: Platform,
)

object SupportedUrlParser {
    const val MAX_INPUT_BYTES = 16 * 1_024

    private const val MAX_URL_LENGTH = 2_048
    private val schemeRegex = Regex("https?://", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = charArrayOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}',
    )

    /**
     * Normalizes share text into zero, one, or multiple distinct supported links.
     *
     * Links retain their first-seen order. Inputs larger than [MAX_INPUT_BYTES] in UTF-8
     * are rejected before URL extraction.
     */
    fun normalize(text: String): InputNormalizationResult {
        if (exceedsInputLimit(text) || text.isBlank()) {
            return InputNormalizationResult.NoSupportedLink
        }

        val linksByValue = linkedMapOf<String, SupportedUrl>()
        var searchStart = 0
        while (searchStart < text.length) {
            val match = schemeRegex.find(text, searchStart) ?: break
            val candidateEnd = findCandidateEnd(text, match.range.first)
            val rawCandidate = text.substring(match.range.first, candidateEnd)
                .trimEnd(*trailingPunctuation)
            validate(rawCandidate)?.let { link ->
                if (link.value !in linksByValue) {
                    linksByValue[link.value] = link
                }
            }
            searchStart = maxOf(candidateEnd, match.range.last + 1)
        }

        val links = linksByValue.values.toList()
        return when (links.size) {
            0 -> InputNormalizationResult.NoSupportedLink
            1 -> InputNormalizationResult.SingleSupportedLink(links.single())
            else -> InputNormalizationResult.MultipleSupportedLinks(links)
        }
    }

    /**
     * Compatibility API for existing callers. Ambiguous input deliberately returns null.
     */
    fun extract(text: String): SupportedUrl? =
        (normalize(text) as? InputNormalizationResult.SingleSupportedLink)?.link

    fun validate(candidate: String): SupportedUrl? {
        if (
            candidate.length !in 1..MAX_URL_LENGTH ||
            candidate.any { it == '\r' || it == '\n' || it.isISOControl() }
        ) {
            return null
        }

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "https" || uri.isOpaque) return null
        if (uri.rawUserInfo != null) return null
        if (uri.port !in setOf(-1, 443)) return null

        val host = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT) ?: return null
        val authority = uri.rawAuthority?.lowercase(Locale.ROOT) ?: return null
        if (authority !in allowedAuthorities(host)) return null
        val rawPath = uri.rawPath ?: return null
        val platform = when {
            host == "v.douyin.com" && DOUYIN_SHORT_PATH.matches(rawPath) -> Platform.DOUYIN
            host in DOUYIN_WEB_HOSTS && DOUYIN_WEB_PATH.matches(rawPath) -> Platform.DOUYIN
            host in IES_DOUYIN_HOSTS && IES_DOUYIN_PATH.matches(rawPath) -> Platform.DOUYIN
            host in BILIBILI_SHORT_HOSTS && BILIBILI_SHORT_PATH.matches(rawPath) -> Platform.BILIBILI
            host in BILIBILI_WEB_HOSTS && BILIBILI_VIDEO_PATH.matches(rawPath) -> Platform.BILIBILI
            else -> return null
        }

        val value = canonicalValue(uri, host) ?: return null
        return SupportedUrl(value, platform)
    }

    private fun exceedsInputLimit(text: String): Boolean {
        if (text.length > MAX_INPUT_BYTES) return true
        return text.toByteArray(Charsets.UTF_8).size > MAX_INPUT_BYTES
    }

    private fun findCandidateEnd(text: String, start: Int): Int {
        var end = start
        while (end < text.length && !text[end].endsCandidate()) {
            end += 1
        }
        return end
    }

    private fun Char.endsCandidate(): Boolean =
        code !in 0x21..0x7e || this in ASCII_BOUNDARIES

    private fun allowedAuthorities(host: String): Set<String> = setOf(
        host,
        "$host.",
        "$host:443",
        "$host.:443",
    )

    private fun canonicalValue(uri: URI, host: String): String? {
        val asciiUri = runCatching { URI(uri.toASCIIString()) }.getOrNull() ?: return null
        return buildString {
            append("https://")
            append(host)
            append(asciiUri.rawPath)
            asciiUri.rawQuery?.let { query ->
                append('?')
                append(query)
            }
        }
    }

    private val ASCII_BOUNDARIES = setOf(
        '<', '>', '"', '\'', '(', ')', '[', ']', '{', '}', ',', ';', '!', '`', '|',
    )

    private val DOUYIN_WEB_HOSTS = setOf(
        "douyin.com",
        "www.douyin.com",
        "m.douyin.com",
    )
    private val IES_DOUYIN_HOSTS = setOf(
        "iesdouyin.com",
        "www.iesdouyin.com",
    )
    private val BILIBILI_SHORT_HOSTS = setOf(
        "b23.tv",
        "bili2233.cn",
        "www.bili2233.cn",
    )
    private val BILIBILI_WEB_HOSTS = setOf(
        "bilibili.com",
        "www.bilibili.com",
        "m.bilibili.com",
    )

    private val DOUYIN_SHORT_PATH = Regex("^/[A-Za-z0-9_-]{1,128}/?$")
    private val DOUYIN_WEB_PATH = Regex("^/(?:video|note)/[0-9]{1,32}/?$")
    private val IES_DOUYIN_PATH = Regex("^/share/(?:video|note)/[0-9]{1,32}/?$")
    private val BILIBILI_SHORT_PATH = Regex("^/[A-Za-z0-9_-]{1,128}/?$")
    private val BILIBILI_VIDEO_PATH = Regex("^/video/BV[A-Za-z0-9]{8,20}/?$")
}
