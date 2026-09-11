package com.flowframe.app.core.engine

import com.flowframe.app.core.platform.PublicContentException
import com.flowframe.app.core.platform.PublicPostParser
import org.junit.Assert.*
import org.junit.Test

class MediaInfoSelectionTest {
    @Test fun unwrapsOneVideoOnlyAfterTheNativeParserConfirmedItHasNoOtherMedia() {
        val raw = PublicPostParser.parseJson("""{"id":"post","entries":[{"id":"video","title":"视频","formats":[]}]}""")
        val selected = MediaInfoSelection.singleVideo(raw, nativeConfirmedSingleVideo = true)
        assertEquals("video", selected["id"].toString().trim('"'))
        assertThrows(PublicContentException::class.java) { MediaInfoSelection.singleVideo(raw, false) }
    }

    @Test fun multipleMissingOrNestedEntriesNeverBecomeOneSuccessfulVideo() {
        listOf("""{"entries":[]}""", """{"entries":[{},{}]}""", """{"entries":[null]}""",
            """{"entries":[{"entries":[{}]}]}""").forEach { value ->
            assertThrows(PublicContentException::class.java) {
                MediaInfoSelection.singleVideo(PublicPostParser.parseJson(value), true)
            }
        }
    }
}
