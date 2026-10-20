package com.nevoit.cresto.feature.screenextract

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Random
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompressedImageDataUrlInstrumentedTest {

    @Test
    fun cameraImage_preservesAllExifOrientations() {
        val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                source.setPixel(x, y, when {
                    x < 40 && y < 20 -> Color.RED
                    y < 20 -> Color.GREEN
                    x < 40 -> Color.BLUE
                    else -> Color.YELLOW
                })
            }
        }
        val file = File.createTempFile(
            "cresto-orientation-", ".jpg",
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        )
        try {
            val expectations = listOf(
                ExifInterface.ORIENTATION_NORMAL to listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL to listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
                ExifInterface.ORIENTATION_ROTATE_180 to listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
                ExifInterface.ORIENTATION_FLIP_VERTICAL to listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
                ExifInterface.ORIENTATION_TRANSPOSE to listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
                ExifInterface.ORIENTATION_ROTATE_90 to listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
                ExifInterface.ORIENTATION_TRANSVERSE to listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
                ExifInterface.ORIENTATION_ROTATE_270 to listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE)
            )
            for ((orientation, expectedColors) in expectations) {
                file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                ExifInterface(file.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                val dataUrl = readCompressedImageDataUrl { file.inputStream() }
                val bytes = Base64.getDecoder().decode(dataUrl.substringAfter(','))
                val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                try {
                    val swapsAxes = orientation >= ExifInterface.ORIENTATION_TRANSPOSE
                    assertEquals(if (swapsAxes) 40 else 80, result.width)
                    assertEquals(if (swapsAxes) 80 else 40, result.height)
                    val actualColors = listOf(
                        result.getPixel(result.width / 4, result.height / 4),
                        result.getPixel(result.width * 3 / 4, result.height / 4),
                        result.getPixel(result.width / 4, result.height * 3 / 4),
                        result.getPixel(result.width * 3 / 4, result.height * 3 / 4)
                    )
                    actualColors.zip(expectedColors).forEach { (actual, expected) ->
                        assertTrue("EXIF orientation $orientation", abs(Color.red(actual) - Color.red(expected)) < 32)
                        assertTrue("EXIF orientation $orientation", abs(Color.green(actual) - Color.green(expected)) < 32)
                        assertTrue("EXIF orientation $orientation", abs(Color.blue(actual) - Color.blue(expected)) < 32)
                    }
                } finally {
                    result.recycle()
                }
            }
        } finally {
            source.recycle()
            file.delete()
        }
    }

    @Test
    fun oversizedSourceStream_isRejectedBeforeUnboundedRead() {
        val error = runCatching {
            ByteArrayInputStream(ByteArray(1_025)).use {
                it.readImageBytesWithLimit(maxSizeBytes = 1_024)
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("图片文件过大，请选择较小的图片", error?.message)
    }

    @Test
    fun largeImage_isCompressedBeforePayloadLimitIsApplied() {
        val sourceWidth = 2200
        val sourceHeight = 1200
        val source = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        val random = Random(0)
        val pixels = IntArray(sourceWidth * sourceHeight) {
            0xFF000000.toInt() or random.nextInt(0x1000000)
        }
        source.setPixels(pixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight)

        val sourceBytes = ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        source.recycle()

        assertTrue(sourceBytes.size > 5 * 1024 * 1024)

        val dataUrl = readCompressedImageDataUrl { ByteArrayInputStream(sourceBytes) }
        val compressedBytes = Base64.getDecoder().decode(dataUrl.substringAfter(','))
        val compressed = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

        assertTrue(dataUrl.startsWith("data:image/jpeg;base64,"))
        assertTrue(compressedBytes.size <= 5 * 1024 * 1024)
        assertEquals(1980, compressed.width)
        assertEquals(1080, compressed.height)

        compressed.recycle()
    }
}
