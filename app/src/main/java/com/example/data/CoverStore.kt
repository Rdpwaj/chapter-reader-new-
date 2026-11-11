package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** Small private copies: revoking gallery access never removes a novel's cover. I/O only. */
class CoverStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "covers").apply { mkdirs() }

    fun file(name: String?): File? = name?.takeIf { NAME.matches(it) }?.let { File(directory, it) }

    fun importUri(uri: Uri): String {
        require(uri.scheme == "content") { "اختر صورة من صور الهاتف" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "تعذّر فتح الصورة" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("تعذّر فتح الصورة")
        try {
            val scale = minOf(1f, 640f / maxOf(bitmap.width, bitmap.height))
            val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
            try {
                val name = "${UUID.randomUUID()}.jpg"
                file(name)!!.outputStream().use { check(small.compress(Bitmap.CompressFormat.JPEG, 82, it)) }
                return name
            } finally { if (small !== bitmap) small.recycle() }
        } finally { bitmap.recycle() }
    }

    fun bitmap(name: String?): Bitmap? {
        val cover = file(name) ?: return null
        cache.get(cover.absolutePath)?.let { return it }
        return BitmapFactory.decodeFile(cover.absolutePath)?.also { cache.put(cover.absolutePath, it) }
    }

    fun export(name: String?): String? = file(name)?.takeIf { it.isFile && it.length() <= MAX_BYTES }
        ?.readBytes()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    fun restore(encoded: String): String {
        require(encoded.length <= MAX_BYTES * 2)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size <= MAX_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..640 && bounds.outHeight in 1..640)
        val name = "${UUID.randomUUID()}.jpg"
        file(name)!!.writeBytes(bytes)
        return name
    }

    companion object {
        private const val MAX_BYTES = 1024 * 1024
        private val NAME = Regex("[a-fA-F0-9-]{36}\\.jpg")
        private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
    }
}
