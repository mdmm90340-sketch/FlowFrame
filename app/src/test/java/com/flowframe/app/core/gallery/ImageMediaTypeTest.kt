package com.flowframe.app.core.gallery

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class ImageMediaTypeTest {
    @Test fun identifiesJpegPngAndWebpByTheirBytes() {
        assertEquals(ImageMediaType.JPEG, ImageMediaType.fromHeader(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte())))
        assertEquals(ImageMediaType.PNG, ImageMediaType.fromHeader(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)))
        assertEquals(ImageMediaType.WEBP, ImageMediaType.fromHeader("RIFF0000WEBP".toByteArray()))
        assertNull(ImageMediaType.fromHeader("<html>blocked".toByteArray()))
        assertNull(ImageMediaType.fromHeader("GIF89a".toByteArray()))
        assertNull(ImageMediaType.fromHeader(byteArrayOf()))
        assertEquals("image/jpeg", ImageMediaType.JPEG.mimeType)
        assertEquals("jpg", ImageMediaType.JPEG.extension)
    }

    @Test fun rejectsPrivateMediaAddressesIncludingDnsResults() {
        listOf("127.0.0.1", "10.0.0.1", "192.168.1.1", "169.254.1.1", "100.64.0.1", "fc00::1").forEach { ip ->
            assertThrows(IllegalArgumentException::class.java) {
                RemoteMediaUrls.validate("https://cdn.example/file.jpg") { arrayOf(InetAddress.getByName(ip)) }
            }
        }
        assertEquals("cdn.example", RemoteMediaUrls.validate("https://cdn.example/file.jpg") { arrayOf(InetAddress.getByName("1.1.1.1")) }.host)
    }
}
