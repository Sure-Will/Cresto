package com.nevoit.cresto.feature.screenextract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ScreenshotDataUrlTest {

    @Test
    fun readCompressedImageDataUrl_rejectsOversizedImageWithoutThrowingAndClosesStream() {
        var closed = false
        val input = object : ByteArrayInputStream(ByteArray(32 * 1024 * 1024 + 1)) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        assertEquals("", readCompressedImageDataUrl { input })
        assertTrue(closed)
    }

    @Test
    fun readCompressedImageDataUrl_handlesUnavailableProvider() {
        assertEquals("", readCompressedImageDataUrl { throw IOException("provider unavailable") })
    }

    @Test
    fun readCompressedImageDataUrl_handlesRevokedPermission() {
        assertEquals("", readCompressedImageDataUrl { throw SecurityException("permission revoked") })
    }

    @Test
    fun readCompressedImageDataUrl_handlesMissingOrEmptyImage() {
        assertEquals("", readCompressedImageDataUrl { null })
        assertEquals("", readCompressedImageDataUrl { ByteArrayInputStream(byteArrayOf()) })
    }

    @Test
    fun readImageBytesWithLimit_preservesImageAtExactBoundary() {
        val bytes = byteArrayOf(1, 2, 3)
        val result = ByteArrayInputStream(bytes).use { it.readImageBytesWithLimit(3) }

        org.junit.Assert.assertArrayEquals(bytes, result)
    }

    @Test
    fun toPngDataUrl_encodesBytesAsPngDataUrl() {
        val result = byteArrayOf(1, 2, 3).toPngDataUrl()

        assertEquals("data:image/png;base64,AQID", result)
    }

    @Test
    fun toPngDataUrl_rejectsEmptyBytes() {
        assertThrows(IllegalArgumentException::class.java) {
            byteArrayOf().toPngDataUrl()
        }
    }

    @Test
    fun toPngDataUrl_rejectsOversizedBytes() {
        assertThrows(IllegalArgumentException::class.java) {
            byteArrayOf(1, 2).toPngDataUrl(maxSizeBytes = 1)
        }
    }
}
