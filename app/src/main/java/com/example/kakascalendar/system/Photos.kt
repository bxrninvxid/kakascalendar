package com.example.kakascalendar.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/** Avatar photo helpers: decode, fix rotation, center-crop to a square, compress. */
object Photos {

    /** Reads the whole picked file once (some providers only allow one read). */
    private fun readBytes(ctx: Context, uri: Uri): ByteArray? =
        runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

    private fun exifRotation(bytes: ByteArray): Float = runCatching {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)

    /**
     * Decodes any picked image (JPEG, PNG, WebP, HEIC…) upright, with its shorter
     * ([shortSide]) or longer side downsampled to roughly [target] px.
     */
    private fun decode(ctx: Context, uri: Uri, target: Int, shortSide: Boolean): Bitmap? {
        val bytes = readBytes(ctx, uri) ?: return null
        // inJustDecodeBounds only fills in the size; decodeByteArray returns null by design here.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val side = if (shortSide) min(bounds.outWidth, bounds.outHeight) else max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (side / (sample * 2) >= target) sample *= 2
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            if (raw != null) {
                val rotation = exifRotation(bytes)
                if (rotation == 0f) return raw
                val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
                if (upright != raw) raw.recycle()
                return upright
            }
        }
        // Fallback (Android 9+): ImageDecoder handles HEIF/AVIF and applies rotation itself.
        if (Build.VERSION.SDK_INT >= 28) {
            return runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val side = if (shortSide) min(w, h) else max(w, h)
                    if (side > target * 2) {
                        val f = target.toFloat() / side
                        decoder.setTargetSize((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1))
                    }
                }
            }.getOrNull()
        }
        return null
    }

    /** Square, upright bitmap of at most [size] px, or null if the image can't be read. */
    fun loadSquare(ctx: Context, uri: Uri, size: Int = 512): Bitmap? {
        val raw = decode(ctx, uri, size, shortSide = true) ?: return null
        val side = min(raw.width, raw.height)
        val scale = size.toFloat() / side
        val matrix = Matrix().apply { if (scale < 1f) postScale(scale, scale) }
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side, matrix, true)
        if (square != raw) raw.recycle()
        return square
    }

    /** Upright bitmap whose longest side is at most [maxSide] px (for stories). */
    fun loadScaled(ctx: Context, uri: Uri, maxSide: Int = 1280): Bitmap? {
        val raw = decode(ctx, uri, maxSide, shortSide = false) ?: return null
        val scale = (maxSide.toFloat() / max(raw.width, raw.height)).coerceAtMost(1f)
        if (scale >= 1f) return raw
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postScale(scale, scale) }, true)
        if (out != raw) raw.recycle()
        return out
    }

    /** JPEG under ~550 KB for a story, base64-encoded. */
    fun storyBase64(bmp: Bitmap): String {
        var quality = 82
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 12
        } while (bytes.size > 550 * 1024 && quality > 30)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Saves the avatar to app storage under a fresh name (so image caches refresh) and returns its path. */
    fun saveAvatar(ctx: Context, bmp: Bitmap): String {
        ctx.filesDir.listFiles { f -> f.name.startsWith("avatar_") }?.forEach { it.delete() }
        val file = File(ctx.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }

    fun deleteAvatar(ctx: Context) {
        ctx.filesDir.listFiles { f -> f.name.startsWith("avatar_") }?.forEach { it.delete() }
    }

    /** Small JPEG for the Squad server, base64-encoded. */
    fun toUploadBase64(bmp: Bitmap, size: Int = 256): String {
        val small = if (bmp.width > size) Bitmap.createScaledBitmap(bmp, size, size, true) else bmp
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (small != bmp) small.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
