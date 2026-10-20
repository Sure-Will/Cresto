package com.nevoit.cresto.feature.screenextract

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import kotlin.math.roundToInt

private const val DEFAULT_MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024
private const val DEFAULT_MAX_SOURCE_IMAGE_BYTES = 32 * 1024 * 1024
private const val DEFAULT_TARGET_SHORT_SIDE_PX = 1080
private const val DEFAULT_MAX_TARGET_LONG_SIDE_PX = 4096
private const val DEFAULT_JPEG_QUALITY = 82
private const val IMAGE_READ_BUFFER_BYTES = 16 * 1024

internal fun readCompressedImageDataUrl(openStream: () -> InputStream?): String {
    return try {
        val bytes = openStream()?.use { it.readImageBytesWithLimit() } ?: return ""
        bytes.toCompressedImageDataUrl()
    } catch (_: IllegalArgumentException) {
        ""
    } catch (_: IOException) {
        ""
    } catch (_: SecurityException) {
        ""
    }
}

internal fun InputStream.readImageBytesWithLimit(
    maxSizeBytes: Int = DEFAULT_MAX_SOURCE_IMAGE_BYTES
): ByteArray {
    require(maxSizeBytes > 0) { "图片大小限制必须大于 0" }

    return ByteArrayOutputStream(minOf(maxSizeBytes, 64 * 1024)).use { output ->
        val buffer = ByteArray(IMAGE_READ_BUFFER_BYTES)
        var totalBytes = 0

        while (true) {
            val bytesRead = read(buffer)
            if (bytesRead < 0) break
            if (totalBytes + bytesRead > maxSizeBytes) {
                throw IllegalArgumentException("图片文件过大，请选择较小的图片")
            }
            output.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
        }

        output.toByteArray()
    }
}

fun ByteArray.toPngDataUrl(maxSizeBytes: Int = DEFAULT_MAX_SCREENSHOT_BYTES): String {
    require(isNotEmpty()) { "截图为空" }
    require(size <= maxSizeBytes) { "截图过大，请重试" }

    val base64 = Base64.getEncoder().encodeToString(this)
    return "data:image/png;base64,$base64"
}

fun ByteArray.toCompressedScreenshotDataUrl(
    targetShortSidePx: Int = DEFAULT_TARGET_SHORT_SIDE_PX,
    jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    maxSizeBytes: Int = DEFAULT_MAX_SCREENSHOT_BYTES
): String {
    return compressImageDataUrl(
        targetShortSidePx = targetShortSidePx,
        jpegQuality = jpegQuality,
        maxSizeBytes = maxSizeBytes,
        emptyMessage = "截图为空",
        decodeMessage = "无法解析截图",
        oversizedMessage = "压缩后截图仍过大，请重试"
    )
}

fun ByteArray.toCompressedImageDataUrl(
    targetShortSidePx: Int = DEFAULT_TARGET_SHORT_SIDE_PX,
    jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    maxSizeBytes: Int = DEFAULT_MAX_SCREENSHOT_BYTES
): String {
    return compressImageDataUrl(
        targetShortSidePx = targetShortSidePx,
        jpegQuality = jpegQuality,
        maxSizeBytes = maxSizeBytes,
        emptyMessage = "图片为空",
        decodeMessage = "无法解析图片",
        oversizedMessage = "压缩后图片仍过大，请重试"
    )
}

fun ByteArray.toCompressedSharedImageDataUrl(
    targetShortSidePx: Int = DEFAULT_TARGET_SHORT_SIDE_PX,
    jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    maxSizeBytes: Int = DEFAULT_MAX_SCREENSHOT_BYTES
): String {
    return toCompressedImageDataUrl(
        targetShortSidePx = targetShortSidePx,
        jpegQuality = jpegQuality,
        maxSizeBytes = maxSizeBytes
    )
}

private fun ByteArray.compressImageDataUrl(
    targetShortSidePx: Int,
    jpegQuality: Int,
    maxSizeBytes: Int,
    emptyMessage: String,
    decodeMessage: String,
    oversizedMessage: String
): String = try {
    require(isNotEmpty()) { emptyMessage }
    require(targetShortSidePx > 0) { "图片目标尺寸必须大于 0" }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { decodeMessage }

    val targetSize = calculateTargetSize(
        sourceWidth = bounds.outWidth,
        sourceHeight = bounds.outHeight,
        targetShortSidePx = targetShortSidePx,
        maxLongSidePx = DEFAULT_MAX_TARGET_LONG_SIDE_PX
    )
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateDecodeSampleSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            targetWidth = targetSize.width,
            targetHeight = targetSize.height
        )
    }
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size, decodeOptions)
        ?: throw IllegalArgumentException(decodeMessage)

    val compressedBytes = bitmap.useBitmap { source ->
        val scaled = source.scaleTo(targetSize, readExifOrientation())
        scaled.useBitmap { target ->
            ByteArrayOutputStream().use { output ->
                target.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), output)
                output.toByteArray()
            }
        }
    }

    require(compressedBytes.size <= maxSizeBytes) { oversizedMessage }

    val base64 = Base64.getEncoder().encodeToString(compressedBytes)
    "data:image/jpeg;base64,$base64"
} catch (_: OutOfMemoryError) {
    throw IllegalArgumentException(oversizedMessage)
}

private fun ByteArray.readExifOrientation(): Int = try {
    inputStream().use {
        ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }
} catch (_: IOException) {
    ExifInterface.ORIENTATION_NORMAL
}

private fun Bitmap.scaleTo(targetSize: TargetImageSize, orientation: Int): Bitmap {
    val matrix = Matrix().apply {
        setScale(targetSize.width.toFloat() / width, targetSize.height.toFloat() / height)
    }
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    if (matrix.isIdentity) return this
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private data class TargetImageSize(
    val width: Int,
    val height: Int
)

private fun calculateTargetSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetShortSidePx: Int,
    maxLongSidePx: Int
): TargetImageSize {
    val shortSide = sourceWidth.coerceAtMost(sourceHeight)
    var scale = minOf(1.0, targetShortSidePx.toDouble() / shortSide.toDouble())
    val scaledLongSide = sourceWidth.coerceAtLeast(sourceHeight) * scale
    if (scaledLongSide > maxLongSidePx) {
        scale *= maxLongSidePx.toDouble() / scaledLongSide
    }

    return TargetImageSize(
        width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
        height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    )
}

private fun calculateDecodeSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    var sampleSize = 1
    while (
        sourceWidth / (sampleSize * 2) >= targetWidth &&
        sourceHeight / (sampleSize * 2) >= targetHeight
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T {
    return try {
        block(this)
    } finally {
        if (!isRecycled) recycle()
    }
}
