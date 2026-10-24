package com.example.kakascalendar.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/** Avatar photo helpers: decode, fix rotation, center-crop to a square, compress. */
object Photos {

    /** Square, rotation-corrected bitmap of at most [size] px, or null if the image can't be read. */
    fun loadSquare(ctx: Context, uri: Uri, size: Int = 512): Bitmap? {
        val resolver = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        val side = min(raw.width, raw.height)
        val matrix = Matrix().apply {
            val scale = size.toFloat() / side
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side, matrix, true)
        if (square != raw) raw.recycle()
        return square
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
