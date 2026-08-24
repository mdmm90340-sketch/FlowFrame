package com.flowframe.app.core.error

import java.util.Locale

enum class FailureOperation {
    PARSE,
    DOWNLOAD,
}

enum class FailureKind {
    LOGIN_REQUIRED,
    PRIVATE_OR_REMOVED,
    DNS,
    TLS,
    TIMEOUT,
    NETWORK,
    HTTP_FORBIDDEN,
    RATE_LIMITED,
    EXTRACTOR_OUTDATED,
    FORMAT_UNAVAILABLE,
    FFMPEG,
    STORAGE_FULL,
    STORAGE_PERMISSION,
    UNSUPPORTED_LINK,
    UNKNOWN,
}

data class FailureClassification(
    val kind: FailureKind,
    val userMessage: String,
    val diagnostic: String,
    val retryable: Boolean,
)

/**
 * Maps parser/download failures to stable UI messages while keeping log diagnostics useful and safe.
 * This object deliberately has no Android dependencies so its matching and redaction can be JVM-tested.
 */
object FailureClassifier {
    fun classify(
        error: Throwable,
        operation: FailureOperation,
    ): FailureClassification = classify(error.describeCauses(), operation)

    fun classify(
        raw: String?,
        operation: FailureOperation,
    ): FailureClassification {
        val source = raw.orEmpty()
        val normalized = source.lowercase(Locale.ROOT)
        val kind = when {
            normalized.hasAny(STORAGE_FULL_MARKERS) -> FailureKind.STORAGE_FULL
            normalized.hasAny(STORAGE_PERMISSION_MARKERS) -> FailureKind.STORAGE_PERMISSION
            RATE_LIMITED_STATUS.containsMatchIn(source) ||
                normalized.hasAny(RATE_LIMIT_MARKERS) -> FailureKind.RATE_LIMITED
            FORBIDDEN_STATUS.containsMatchIn(source) -> FailureKind.HTTP_FORBIDDEN
            normalized.hasAny(PRIVATE_MARKERS) -> FailureKind.PRIVATE_OR_REMOVED
            normalized.hasAny(EXTRACTOR_OUTDATED_MARKERS) -> FailureKind.EXTRACTOR_OUTDATED
            normalized.hasAny(LOGIN_MARKERS) -> FailureKind.LOGIN_REQUIRED
            normalized.hasAny(DNS_MARKERS) -> FailureKind.DNS
            normalized.hasAny(TLS_MARKERS) -> FailureKind.TLS
            normalized.hasAny(TIMEOUT_MARKERS) -> FailureKind.TIMEOUT
            normalized.hasAny(NETWORK_MARKERS) -> FailureKind.NETWORK
            normalized.hasAny(FORMAT_MARKERS) -> FailureKind.FORMAT_UNAVAILABLE
            normalized.hasAny(FFMPEG_MARKERS) -> FailureKind.FFMPEG
            normalized.hasAny(UNSUPPORTED_MARKERS) -> FailureKind.UNSUPPORTED_LINK
            else -> FailureKind.UNKNOWN
        }
        return FailureClassification(
            kind = kind,
            userMessage = userMessage(kind, operation),
            diagnostic = sanitizeDiagnostic(source),
            retryable = kind in RETRYABLE_KINDS,
        )
    }

    fun sanitizeDiagnostic(raw: String?): String {
        var sanitized = raw.orEmpty()
        sanitized = URL_PATTERN.replace(sanitized) { match -> redactUrlQuery(match.value) }
        sanitized = FILE_URI_PATTERN.replace(sanitized, "<local-path>")
        sanitized = SENSITIVE_HEADER_PATTERN.replace(sanitized) { match ->
            "${match.groupValues[1]}: <redacted>"
        }
        sanitized = BEARER_PATTERN.replace(sanitized, "Bearer <redacted>")
        sanitized = SECRET_ASSIGNMENT_PATTERN.replace(sanitized) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
        sanitized = WINDOWS_PATH_PATTERN.replace(sanitized, "<local-path>")
        sanitized = UNIX_PATH_PATTERN.replace(sanitized, "<local-path>")
        sanitized = CONTROL_OR_WHITESPACE_PATTERN.replace(sanitized, " ").trim()
        if (sanitized.isBlank()) sanitized = "无错误详情"
        return if (sanitized.length <= MAX_DIAGNOSTIC_LENGTH) {
            sanitized
        } else {
            sanitized.take(MAX_DIAGNOSTIC_LENGTH - 1).trimEnd() + "…"
        }
    }

    private fun userMessage(kind: FailureKind, operation: FailureOperation): String = when (kind) {
        FailureKind.LOGIN_REQUIRED -> "该内容需要登录，当前仅支持公开内容"
        FailureKind.PRIVATE_OR_REMOVED -> "内容为私密、已删除或不可用"
        FailureKind.DNS -> "域名解析失败，请检查网络"
        FailureKind.TLS -> "安全连接失败，请检查系统时间或网络"
        FailureKind.TIMEOUT -> "连接超时，请稍后重试"
        FailureKind.NETWORK -> "网络连接失败，请检查网络"
        FailureKind.HTTP_FORBIDDEN -> when (operation) {
            FailureOperation.PARSE -> "平台拒绝了本次请求，请稍后重试"
            FailureOperation.DOWNLOAD -> "下载地址已失效，请返回重新解析"
        }
        FailureKind.RATE_LIMITED -> "请求过于频繁，请稍后再试"
        FailureKind.EXTRACTOR_OUTDATED -> "平台接口已更新，请升级应用后重试"
        FailureKind.FORMAT_UNAVAILABLE -> "没有找到可用的下载格式"
        FailureKind.FFMPEG -> "音视频处理失败，请重试"
        FailureKind.STORAGE_FULL -> "存储空间不足，请清理后重试"
        FailureKind.STORAGE_PERMISSION -> "无法写入存储，请检查权限"
        FailureKind.UNSUPPORTED_LINK -> "暂时无法识别这个链接"
        FailureKind.UNKNOWN -> when (operation) {
            FailureOperation.PARSE -> "解析失败，请稍后重试"
            FailureOperation.DOWNLOAD -> "下载失败，请稍后重试"
        }
    }

    private fun Throwable.describeCauses(): String {
        val seen = mutableSetOf<Throwable>()
        val descriptions = mutableListOf<String>()
        var current: Throwable? = this
        while (current != null && descriptions.size < MAX_CAUSE_DEPTH && seen.add(current)) {
            val type = current.javaClass.simpleName.ifBlank { current.javaClass.name }
            val message = current.message?.trim().orEmpty()
            descriptions += if (message.isBlank()) type else "$type: $message"
            current = current.cause
        }
        return descriptions.joinToString(" <- ")
    }

    private fun redactUrlQuery(url: String): String {
        val schemeEnd = url.indexOf("://").takeIf { it >= 0 }?.plus(3)
        val withoutCredentials = if (schemeEnd != null) {
            val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), startIndex = schemeEnd)
                .takeIf { it >= 0 } ?: url.length
            val userInfoEnd = url.lastIndexOf('@', startIndex = authorityEnd - 1)
            if (userInfoEnd >= schemeEnd) {
                url.take(schemeEnd) + "<redacted>@" + url.drop(userInfoEnd + 1)
            } else {
                url
            }
        } else {
            url
        }
        val queryIndex = withoutCredentials.indexOf('?').takeIf { it >= 0 }
        val fragmentIndex = withoutCredentials.indexOf('#').takeIf { it >= 0 }
        val cutIndex = listOfNotNull(queryIndex, fragmentIndex).minOrNull() ?: return withoutCredentials
        return withoutCredentials.take(cutIndex) + "?<redacted>"
    }

    private fun String.hasAny(markers: Array<String>): Boolean = markers.any(::contains)

    private const val MAX_CAUSE_DEPTH = 8
    private const val MAX_DIAGNOSTIC_LENGTH = 512

    private val RETRYABLE_KINDS = setOf(
        FailureKind.DNS,
        FailureKind.TLS,
        FailureKind.TIMEOUT,
        FailureKind.NETWORK,
    )

    private val FORBIDDEN_STATUS = Regex(
        """(?i)\b(?:http(?:\s+error)?|status(?:\s+code)?|response(?:\s+code)?|server\s+returned|error)[^\r\n]{0,24}\b403\b|\b403\s+forbidden\b""",
    )
    private val RATE_LIMITED_STATUS = Regex(
        """(?i)\b(?:http(?:\s+error)?|status(?:\s+code)?|response(?:\s+code)?|server\s+returned|error)[^\r\n]{0,24}\b(?:412|429)\b|\b429\s+too\s+many\s+requests\b|\b412\s+precondition\s+failed\b""",
    )
    private val URL_PATTERN = Regex("""(?i)\bhttps?://[^\s\"'<>]+""")
    private val FILE_URI_PATTERN = Regex("""(?i)\bfile:/+[^\s\"'<>]+""")
    private val SENSITIVE_HEADER_PATTERN = Regex(
        """(?im)\b(cookie|set-cookie|authorization|proxy-authorization)\s*:\s*[^\r\n]+""",
    )
    private val BEARER_PATTERN = Regex("""(?i)\bbearer\s+[a-z0-9._~+/=-]{8,}""")
    private val SECRET_ASSIGNMENT_PATTERN = Regex(
        """(?i)[\"']?\b(cookie|authorization|token|access[_-]?token|refresh[_-]?token|ms[_-]?token|session(?:id)?|sid|ttwid|signature|sig|a_bogus|x-bogus|csrf(?:token)?|api[_-]?key)\b[\"']?\s*[:=]\s*(?!<redacted>)(?:\"[^\"\r\n]*\"|'[^'\r\n]*'|[^\s,;]+)""",
    )
    private val WINDOWS_PATH_PATTERN = Regex(
        """(?i)(?<![a-z0-9])(?:[a-z]:[\\/]|\\\\)[^\r\n<>|?*\":]+""",
    )
    private val UNIX_PATH_PATTERN = Regex(
        """(?i)(?<![a-z0-9])/(?:data|storage|sdcard|home|users|tmp|var/tmp|private|mnt|opt|workspace)(?:/[^\s:;,\])}]+)+""",
    )
    private val CONTROL_OR_WHITESPACE_PATTERN = Regex("""[\p{Cc}\s]+""")

    private val STORAGE_FULL_MARKERS = arrayOf(
        "enospc",
        "no space left on device",
        "not enough space",
        "insufficient storage",
        "disk full",
        "storage full",
        "quota exceeded",
    )
    private val STORAGE_PERMISSION_MARKERS = arrayOf(
        "eacces",
        "eperm",
        "permission denied",
        "operation not permitted",
        "read-only file system",
        "readonlyfilesystemexception",
        "securityexception",
    )
    private val RATE_LIMIT_MARKERS = arrayOf(
        "too many requests",
        "rate limit",
        "rate-limit",
        "precondition failed",
        "请求过于频繁",
        "访问频繁",
    )
    private val PRIVATE_MARKERS = arrayOf(
        "video is private",
        "private video",
        "content is private",
        "this content is private",
        "video has been removed",
        "content has been removed",
        "deleted by the uploader",
        "video is unavailable",
        "content is no longer available",
        "内容不可见",
        "私密视频",
        "作品已删除",
    )
    private val EXTRACTOR_OUTDATED_MARKERS = arrayOf(
        "failed to parse json",
        "unable to extract",
        "extractorerror",
        "report this issue",
        "confirm you are on the latest version",
        "platform page may have changed",
        "extractor may be outdated",
        "fresh cookies (not necessarily logged in)",
        "网页结构已更新",
        "解析器已过期",
    )
    private val LOGIN_MARKERS = arrayOf(
        "login required",
        "log in to",
        "login to",
        "sign in to",
        "authentication required",
        "requires authentication",
        "members-only",
        "members only",
        "use --cookies",
        "cookies-from-browser",
        "provide account credentials",
        "valid cookies are required",
        "age-restricted",
        "age restricted",
        "需要登录",
        "登录后",
    )
    private val DNS_MARKERS = arrayOf(
        "unknownhostexception",
        "name or service not known",
        "no address associated with hostname",
        "temporary failure in name resolution",
        "nodename nor servname provided",
        "getaddrinfo failed",
        "dns lookup failed",
        "dns resolution failed",
    )
    private val TLS_MARKERS = arrayOf(
        "sslhandshakeexception",
        "sslpeerunverifiedexception",
        "sslerror",
        "ssl error",
        "tls error",
        "tls handshake",
        "certificate verify failed",
        "certpathvalidatorexception",
        "trust anchor for certification path not found",
        "hostname verification failed",
    )
    private val TIMEOUT_MARKERS = arrayOf(
        "sockettimeoutexception",
        "timed out",
        "timeout",
        "time-out",
        "deadline exceeded",
    )
    private val NETWORK_MARKERS = arrayOf(
        "connectexception",
        "connection reset",
        "connection refused",
        "connection aborted",
        "failed to connect",
        "unable to connect",
        "network is unreachable",
        "network unreachable",
        "no route to host",
        "broken pipe",
        "remote end closed connection",
        "network error",
        "network connection failed",
    )
    private val FORMAT_MARKERS = arrayOf(
        "requested format is not available",
        "requested format not available",
        "no video formats found",
        "no audio formats found",
        "no matching formats",
        "format selection failed",
        "no downloadable formats",
        "没有可用格式",
    )
    private val FFMPEG_MARKERS = arrayOf(
        "ffmpeg is not installed",
        "ffmpeg not found",
        "ffprobe not found",
        "ffmpegpostprocessorerror",
        "postprocessing failed",
        "post-processing failed",
        "error while opening encoder",
        "invalid data found when processing input",
        "merger failed",
    )
    private val UNSUPPORTED_MARKERS = arrayOf(
        "unsupported url",
        "no suitable extractor",
        "url is not supported",
        "不支持的链接",
    )
}
