package com.flowframe.app.core.engine

import com.flowframe.app.core.platform.PublicContentException
import com.flowframe.app.core.platform.PublicPostParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Some extractors wrap even a single video in a playlist-shaped result. */
internal object MediaInfoSelection {
    fun singleVideo(raw: JsonObject, nativeConfirmedSingleVideo: Boolean): JsonObject {
        val entries = raw["entries"] as? JsonArray ?: return raw
        val entry = entries.singleOrNull() as? JsonObject
        if (!nativeConfirmedSingleVideo || entry == null || entry["entries"] != null) {
            throw PublicContentException(PublicPostParser.MIXED_CONTENT_MESSAGE)
        }
        return entry
    }
}
