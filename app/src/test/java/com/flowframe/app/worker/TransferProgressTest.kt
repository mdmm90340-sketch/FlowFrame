package com.flowframe.app.worker

import org.junit.Assert.*
import org.junit.Test

class TransferProgressTest {
    @Test fun structuredEngineOutputReportsTransferredBytesInsteadOfFinalFileSize() {
        val value = TransferProgress.parse("FLOWFRAME_PROGRESS:1024|4096|null|512.75|6")!!
        assertEquals(1024L, value.downloadedBytes)
        assertEquals(4096L, value.totalBytes)
        assertEquals(512L, value.bytesPerSecond)
        assertEquals(6L, value.etaSeconds)
        assertEquals(.25f, value.fraction!!, .001f)
    }
    @Test fun unknownLengthRemainsUnknown() {
        val value = TransferProgress.parse("FLOWFRAME_PROGRESS:1024|null|null|null|null")!!
        assertNull(value.totalBytes)
        assertNull(value.fraction)
        assertNull(value.bytesPerSecond)
    }
    @Test fun estimatedLengthIsUsedWhenServerDoesNotSendLength() {
        assertEquals(8192L, TransferProgress.parse("FLOWFRAME_PROGRESS:1024|null|8192|100|2")!!.totalBytes)
    }
    @Test fun unrelatedOrMalformedOutputDoesNotInventMetrics() {
        assertNull(TransferProgress.parse("[Merger] Merging formats"))
        assertNull(TransferProgress.parse("FLOWFRAME_PROGRESS:NaN|4|4|1|0"))
        assertNull(TransferProgress.parse("FLOWFRAME_PROGRESS:100|200"))
    }
}
