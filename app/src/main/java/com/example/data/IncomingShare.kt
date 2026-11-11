package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html

object IncomingShare {
    /** Caller must run on IO; never fetch arbitrary network links or read private file:// paths. */
    @Suppress("DEPRECATION")
    fun read(context: Context, intent: Intent): String? {
        if (intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_VIEW, Intent.ACTION_PROCESS_TEXT)) return null
        if (intent.type?.startsWith("text/") != true) return null
        val plain = intent.getCharSequenceExtra(if (intent.action == Intent.ACTION_PROCESS_TEXT)
            Intent.EXTRA_PROCESS_TEXT else Intent.EXTRA_TEXT)?.toString()
        if (!plain.isNullOrBlank()) return checked(plain)
        val html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
        if (!html.isNullOrBlank()) return checked(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString())
        val uri = (if (intent.action == Intent.ACTION_VIEW) intent.data else
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri) ?: return null
        require(uri.scheme == "content") { "هذا النوع من الملفات غير مدعوم" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val block = ByteArray(8192)
            while (true) {
                val size = input.read(block)
                if (size < 0) break
                require(buffer.size() + size <= ChapterImport.MAX_CHARACTERS * 4) { "الملف كبير جدًا؛ قسّمه إلى ملفات أصغر" }
                buffer.write(block, 0, size)
            }
            buffer.toByteArray()
        } ?: error("تعذر فتح الملف المشارك")
        val text = TextProcessingUtils.decodeBytesAutoCharset(bytes)
        return checked(if (intent.type == "text/html") Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString() else text)
    }

    private fun checked(text: String): String {
        require(text.length <= ChapterImport.MAX_CHARACTERS) { "النص كبير جدًا؛ أرسله على دفعات أصغر" }
        require(text.isNotBlank()) { "النص المشارك فارغ" }
        return text
    }
}
