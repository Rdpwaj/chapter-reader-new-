package com.example.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ImportDraft(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val novelId: Long? = null,
    val newNovelTitle: String = "",
    val splitMode: String = "NONE"
)

object ChapterImport {
    const val MAX_CHARACTERS = 2_000_000
    const val MARKER = "===فصل==="
    private val urlOnly = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    fun isLinkOnly(text: String) = urlOnly.matches(text.trim())

    /** Keep headings and short introductions. No translation or whitespace rewriting. */
    fun split(draft: ImportDraft): List<SplitChapterPreview> {
        if (draft.content.isBlank()) return emptyList()
        require(draft.content.length <= MAX_CHARACTERS) { "النص كبير جدًا؛ أرسله على دفعات أصغر" }
        return when (draft.splitMode) {
            "HEADINGS" -> TextProcessingUtils.autoSplitIntoChapters(draft.content, false)
            "MANUAL" -> draft.content.split(MARKER).filter { it.isNotBlank() }.mapIndexed { i, text ->
                SplitChapterPreview(TextProcessingUtils.suggestTitleFromContent(text, "الفصل ${i + 1}"), text.trim(), 0)
            }
            else -> listOf(SplitChapterPreview(draft.title.trim().ifBlank {
                TextProcessingUtils.suggestTitleFromContent(draft.content)
            }, draft.content, 0))
        }
    }
}

/** Private atomic storage, outside Bundle so long chapters cannot cause TransactionTooLarge. */
class ImportDraftStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "chapter_import_drafts.json"))
    fun read(): List<ImportDraft> {
        if (!file.baseFile.exists()) return emptyList()
        val array = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            ImportDraft(o.getString("id"), o.optString("title"), o.getString("content"),
                if (o.isNull("novelId")) null else o.getLong("novelId"), o.optString("newNovelTitle"), o.optString("splitMode", "NONE"))
        }
    }
    fun write(drafts: List<ImportDraft>) {
        val array = JSONArray()
        drafts.forEach { d -> array.put(JSONObject().put("id", d.id).put("title", d.title).put("content", d.content)
            .put("novelId", d.novelId ?: JSONObject.NULL).put("newNovelTitle", d.newNovelTitle).put("splitMode", d.splitMode)) }
        val stream = file.startWrite()
        try { stream.write(array.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
