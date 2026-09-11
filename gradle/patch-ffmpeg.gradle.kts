import groovy.json.JsonSlurper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.CRC32
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.apache.commons:commons-compress:1.26.1") }
}

// Preserve the pinned FFmpeg runtime, replacing only the five rebuilt WebP libraries.
// Prebuilt, source-reproducible inputs make a normal app build independent of a local NDK.
val upstreamFfmpeg = configurations.create("upstreamFfmpeg16k") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies.add(upstreamFfmpeg.name, "io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1@aar")

val webpDirectory = rootProject.file("native/webp-1.6.0")
val patchedFfmpeg = layout.buildDirectory.file("generated/native/ffmpeg-0.18.1-flowframe16k.aar")

fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun replaceZipEntries(bytes: ByteArray, replacements: Map<String, ByteArray>): ByteArray {
    val result = ByteArrayOutputStream()
    val remaining = replacements.keys.toMutableSet()
    val seen = mutableSetOf<String>()
    ZipArchiveOutputStream(result).use { output ->
        output.setLevel(9)
        ZipFile(SeekableInMemoryByteChannel(bytes)).use { input ->
            val entries = input.entries
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                check(seen.add(name)) { "Duplicate archive entry: $name" }
                val original = input.getInputStream(entry).use { it.readBytes() }
                val replacement = replacements[name]
                if (replacement != null) remaining.remove(name)
                val payload = replacement ?: original
                // Clone central-directory metadata: native payloads contain Unix symlinks.
                output.putArchiveEntry(ZipArchiveEntry(entry).apply {
                    setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0))
                    method = ZipEntry.DEFLATED
                    size = payload.size.toLong()
                    compressedSize = -1L
                    crc = CRC32().apply { update(payload) }.value
                })
                output.write(payload)
                output.closeArchiveEntry()
            }
        }
    }
    check(remaining.isEmpty()) { "Expected native entries absent: $remaining" }
    return result.toByteArray()
}

tasks.register("prepareFfmpeg16k") {
    inputs.files(upstreamFfmpeg)
    inputs.dir(webpDirectory)
    inputs.file(rootProject.file("app/src/main/assets/open_source/WEBP-NATIVE-MANIFEST.json"))
    outputs.file(patchedFfmpeg)
    doLast {
        val manifest = JsonSlurper().parse(webpDirectory.resolve("manifest.json")) as Map<*, *>
        check(webpDirectory.resolve("manifest.json").readBytes().contentEquals(
            rootProject.file("app/src/main/assets/open_source/WEBP-NATIVE-MANIFEST.json").readBytes(),
        )) { "Runtime and build native manifests must match" }
        val upstream = manifest["upstreamAar"] as Map<*, *>
        val original = upstreamFfmpeg.singleFile.readBytes()
        check(original.sha256() == upstream["sha256"]) { "Pinned FFmpeg AAR checksum mismatch" }
        val libraries = manifest["libraries"] as List<*>
        val names = setOf("libsharpyuv.so", "libwebp.so", "libwebpdecoder.so", "libwebpdemux.so", "libwebpmux.so")
        val abis = setOf("arm64-v8a", "x86_64")
        val payloads = linkedMapOf<String, MutableMap<String, ByteArray>>()
        for (item in libraries) {
            val library = item as Map<*, *>
            val abi = library["abi"] as String
            val name = library["name"] as String
            check(abi in abis && name in names) { "Unexpected native input" }
            val bytes = webpDirectory.resolve("$abi/$name").readBytes()
            check(bytes.sha256() == library["sha256"]) { "Native library checksum mismatch: $abi/$name" }
            val entries = payloads.getOrPut("jni/$abi/libffmpeg.zip.so") { linkedMapOf() }
            check(entries.put("usr/lib/$name", bytes) == null) { "Duplicate native input: $abi/$name" }
        }
        check(payloads.size == abis.size && payloads.values.all { it.size == names.size }) {
            "Each supported ABI must contain all five rebuilt WebP libraries"
        }
        val replacements = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(original)).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                payloads[entry.name]?.let { replacements[entry.name] = replaceZipEntries(input.readBytes(), it) }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        check(replacements.keys == payloads.keys) { "Pinned FFmpeg native payloads are missing" }
        val target = patchedFfmpeg.get().asFile
        target.parentFile.mkdirs()
        target.writeBytes(replaceZipEntries(original, replacements))
    }
}
