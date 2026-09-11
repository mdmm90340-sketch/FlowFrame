package com.flowframe.app.core.error

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureClassifierTest {
    @Test
    fun doesNotHideKnownFailureBehindExtractorExceptionType() {
        assertKind(FailureKind.LOGIN_REQUIRED, "ExtractorError: Login required")
        assertKind(FailureKind.FORMAT_UNAVAILABLE, "ExtractorError: No video formats found")
        assertKind(FailureKind.NETWORK, "ExtractorError: Connection reset")
    }

    @Test
    fun redactsDomesticPlatformShareSecrets() {
        val result = FailureClassifier.sanitizeDiagnostic("xsec_token=fixture-secret shareToken=another-secret")
        assertFalse(result.contains("fixture-secret"))
        assertFalse(result.contains("another-secret"))
    }
    @Test
    fun distinguishesLoginFromPrivateContent() {
        assertKind(FailureKind.LOGIN_REQUIRED, "Login required. Use --cookies to authenticate")
        assertKind(FailureKind.PRIVATE_OR_REMOVED, "ERROR: This video is private")
    }

    @Test
    fun treatsKnownDouyinParserFailureAsOutdatedExtractorInsteadOfLogin() {
        val failure = FailureClassifier.classify(
            "ERROR: Failed to parse JSON; Fresh cookies (not necessarily logged in) are needed",
            FailureOperation.PARSE,
        )

        assertEquals(FailureKind.EXTRACTOR_OUTDATED, failure.kind)
        assertEquals("平台接口已更新，请升级应用后重试", failure.userMessage)
    }

    @Test
    fun distinguishesDnsTlsTimeoutAndOtherNetworkFailures() {
        assertEquals(
            FailureKind.DNS,
            FailureClassifier.classify(UnknownHostException("api.example"), FailureOperation.PARSE).kind,
        )
        assertEquals(
            FailureKind.TLS,
            FailureClassifier.classify(SSLHandshakeException("certificate verify failed"), FailureOperation.PARSE).kind,
        )
        assertEquals(
            FailureKind.TIMEOUT,
            FailureClassifier.classify(SocketTimeoutException("Read timed out"), FailureOperation.PARSE).kind,
        )
        assertEquals(
            FailureKind.NETWORK,
            FailureClassifier.classify(ConnectException("Connection refused"), FailureOperation.PARSE).kind,
        )
    }

    @Test
    fun distinguishesHttpBlockingStatuses() {
        assertKind(FailureKind.HTTP_FORBIDDEN, "HTTP Error 403: Forbidden")
        assertKind(FailureKind.RATE_LIMITED, "server returned HTTP response code: 412")
        assertKind(FailureKind.RATE_LIMITED, "HTTP Error 429: Too Many Requests")
    }

    @Test
    fun distinguishesFormatFfmpegStorageAndPermissionFailures() {
        assertKind(FailureKind.FORMAT_UNAVAILABLE, "Requested format is not available")
        assertKind(FailureKind.FFMPEG, "FFmpegPostProcessorError: postprocessing failed")
        assertKind(FailureKind.STORAGE_FULL, "java.io.IOException: ENOSPC (No space left on device)")
        assertKind(FailureKind.STORAGE_PERMISSION, "java.io.FileNotFoundException: Permission denied")
    }

    @Test
    fun lostDirectoryGrantsAndUnwritableTreesGiveAnActionableRecoveryMessage() {
        listOf(
            "保存目录授权已失效，请在保存设置中重新选择目录",
            "保存目录无法写入，请在保存设置中重新选择目录",
            "无法在所选目录中创建媒体文件",
            "无法写入所选保存目录",
        ).forEach { message ->
            val failure = FailureClassifier.classify(IllegalArgumentException(message), FailureOperation.DOWNLOAD)
            assertEquals(FailureKind.STORAGE_PERMISSION, failure.kind)
            assertTrue(failure.userMessage.contains("重新选择目录"))
            assertTrue(failure.userMessage.contains("恢复默认目录"))
            assertFalse(failure.retryable)
        }
    }

    @Test
    fun unknownFailuresNeverExposeRawTextToTheUi() {
        val raw = "internal failure containing secret-value"
        val parse = FailureClassifier.classify(raw, FailureOperation.PARSE)
        val download = FailureClassifier.classify(raw, FailureOperation.DOWNLOAD)

        assertEquals(FailureKind.UNKNOWN, parse.kind)
        assertEquals("解析失败，请稍后重试", parse.userMessage)
        assertEquals("下载失败，请稍后重试", download.userMessage)
        assertFalse(parse.userMessage.contains("secret-value"))
    }

    @Test
    fun diagnosticsRedactSecretsQueriesAndLocalPaths() {
        val raw = """
            Request https://example.com/video/42?token=url-secret&a_bogus=signature-secret
            Mirror https://url-user:url-password@example.com/video/42
            Cookie: sessionid=cookie-secret; ttwid=another-secret
            Authorization: Bearer bearer-secret-value
            JSON {"msToken":"json-secret"}
            Windows C:\Users\Alice\FlowFrame\debug.log: failed
            Normalized D:/workspace/project/debug.log: failed
            Android /data/user/0/com.flowframe.app/files/debug.log: failed
        """.trimIndent()

        val diagnostic = FailureClassifier.classify(raw, FailureOperation.PARSE).diagnostic

        assertTrue(diagnostic.contains("https://example.com/video/42?<redacted>"))
        assertTrue(diagnostic.contains("Cookie: <redacted>"))
        assertTrue(diagnostic.contains("Authorization: <redacted>"))
        assertTrue(diagnostic.contains("<local-path>"))
        assertFalse(diagnostic.contains("url-secret"))
        assertFalse(diagnostic.contains("signature-secret"))
        assertFalse(diagnostic.contains("cookie-secret"))
        assertFalse(diagnostic.contains("bearer-secret-value"))
        assertFalse(diagnostic.contains("url-password"))
        assertFalse(diagnostic.contains("json-secret"))
        assertFalse(diagnostic.contains("Alice"))
        assertFalse(diagnostic.contains("workspace/project"))
        assertFalse(diagnostic.contains("com.flowframe.app/files"))
    }

    @Test
    fun diagnosticsIncludeSanitizedCauseChainAndAreTruncated() {
        val cause = IllegalStateException(
            "request failed token=cause-secret at /storage/emulated/0/Movies/output.mp4 ${"x".repeat(800)}",
        )
        val error = RuntimeException("wrapper", cause)

        val failure = FailureClassifier.classify(error, FailureOperation.DOWNLOAD)

        assertTrue(failure.diagnostic.length <= 512)
        assertTrue(failure.diagnostic.contains("IllegalStateException"))
        assertFalse(failure.diagnostic.contains("cause-secret"))
        assertFalse(failure.diagnostic.contains("/storage/emulated"))
    }

    @Test
    fun onlyTransportFailuresAreAutomaticallyRetryable() {
        val retryable = listOf(
            "DNS lookup failed",
            "TLS handshake failed",
            "Read timed out",
            "Connection reset",
        )
        retryable.forEach { raw ->
            assertTrue(FailureClassifier.classify(raw, FailureOperation.DOWNLOAD).retryable)
        }
        assertFalse(
            FailureClassifier.classify("HTTP Error 429", FailureOperation.DOWNLOAD).retryable,
        )
        assertFalse(
            FailureClassifier.classify("Requested format is not available", FailureOperation.DOWNLOAD).retryable,
        )
    }

    private fun assertKind(expected: FailureKind, raw: String) {
        assertEquals(
            expected,
            FailureClassifier.classify(raw, FailureOperation.PARSE).kind,
        )
    }
}
