package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/** v1/v2 backup parser. Validate all records before the first transactional write. */
data class ReaderBackup(val novels: List<NovelEntity>, val chapters: List<ChapterEntity>,
    val annotations: List<AnnotationEntity>, val glossary: List<GlossaryEntity>,
    val stats: List<ReadingStatsEntity>, val settings: ReaderSettings?) {
    companion object {
        fun parse(json: String): ReaderBackup {
            val root = JSONObject(json)
            require(root.optInt("version", 1) in 1..2)
            val now = System.currentTimeMillis()
            val novels = root.getJSONArray("novels").objects().map { o ->
                NovelEntity(id = o.getLong("id"), title = o.getString("title"), description = o.optString("description"),
                    colorHex = o.optString("colorHex", "#D97706"), category = o.optString("category", "عام"), tags = o.optString("tags"),
                    sortBy = o.optString("sortBy", "NATURAL_NAME"), sortAscending = o.optBoolean("sortAscending", true),
                    settingsOverrideJson = o.optString("settingsOverrideJson").takeIf { it.isNotBlank() },
                    createdAt = o.optLong("createdAt", now), deletedAt = o.optionalLong("deletedAt"))
            }
            val chapters = root.getJSONArray("chapters").objects().map { o ->
                require(o.get("content") is String)
                ChapterEntity(id = o.getLong("id"), novelId = o.optionalLong("novelId"), title = o.getString("title"),
                    content = o.getString("content"), format = o.optString("format", "TXT"), wordCount = o.optInt("wordCount"),
                    readingStatus = o.optString("readingStatus", "NEW"), translatorStage = o.optString("translatorStage", "DRAFT"),
                    favorite = o.optBoolean("favorite"), tags = o.optString("tags"),
                    paragraphIndex = o.optInt("paragraphIndex").coerceAtLeast(0), charOffset = o.optInt("charOffset").coerceAtLeast(0),
                    progressPercent = o.optDouble("progressPercent", 0.0).toFloat().coerceIn(0f, 100f),
                    createdAt = o.optLong("createdAt", now), modifiedAt = o.optLong("modifiedAt", now),
                    lastReadAt = o.optLong("lastReadAt"), deletedAt = o.optionalLong("deletedAt"))
            }
            require(novels.all { it.id > 0 } && novels.map { it.id }.distinct().size == novels.size)
            require(chapters.all { it.id > 0 } && chapters.map { it.id }.distinct().size == chapters.size)
            val annotations = root.optionalArray("annotations").objects().map { o ->
                AnnotationEntity(id = o.optLong("id"), chapterId = o.getLong("chapterId"), novelId = o.optionalLong("novelId"),
                    type = o.optString("type", "HIGHLIGHT"), paragraphIndex = o.optInt("paragraphIndex"),
                    startOffset = o.optInt("startOffset"), endOffset = o.optInt("endOffset"), selectedText = o.optString("selectedText"),
                    noteText = o.optString("noteText"), colorHex = o.optString("colorHex", "#FDE047"), createdAt = o.optLong("createdAt", now))
            }
            val glossary = root.optionalArray("glossary").objects().map { o ->
                GlossaryEntity(id = o.optLong("id"), novelId = o.optionalLong("novelId"), term = o.getString("term"),
                    translation = o.getString("translation"), category = o.optString("category", "شخصية"),
                    note = o.optString("note"), highlightColorHex = o.optString("highlightColorHex", "#FDE047"), createdAt = o.optLong("createdAt", now))
            }
            val stats = root.optionalArray("readingStats").objects().map { o ->
                ReadingStatsEntity(o.getString("dateString"), o.optInt("minutesRead"), o.optInt("wordsRead"), o.optInt("chaptersCompleted"), o.optLong("updatedAt", now))
            }
            return ReaderBackup(novels, chapters, annotations, glossary, stats,
                root.optJSONObject("settings")?.let { ReaderSettings.fromJsonString(it.toString()) })
        }
        private fun JSONObject.optionalLong(key: String): Long? = if (isNull(key)) null else optLong(key, -1).takeIf { it > 0 }
        private fun JSONObject.optionalArray(key: String): JSONArray = if (has(key)) getJSONArray(key) else JSONArray()
        private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    }
}
