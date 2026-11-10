package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- Fast Startup Existence Checks (0 RAM overhead) ---
    @Query("SELECT COUNT(id) FROM novels")
    suspend fun countNovels(): Int

    @Query("SELECT COUNT(id) FROM chapters")
    suspend fun countChapters(): Int

    // --- Novels ---
    @Query("SELECT * FROM novels WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActiveNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeletedNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels")
    suspend fun getAllNovelsSnapshot(): List<NovelEntity>

    @Query("SELECT * FROM novels WHERE id = :id LIMIT 1")
    suspend fun getNovelById(id: Long): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: NovelEntity): Long

    @Update
    suspend fun updateNovel(novel: NovelEntity)

    @Query("DELETE FROM novels WHERE id = :id")
    suspend fun permanentlyDeleteNovel(id: Long)

    // --- Chapters (Lightweight Metadata Queries for Instant Startup & Low RAM) ---
    @Query(
        """
        SELECT id, novelId, title, '' AS content, format, wordCount, readingStatus, translatorStage,
               favorite, tags, paragraphIndex, charOffset, progressPercent, createdAt, modifiedAt, lastReadAt, deletedAt
        FROM chapters
        WHERE deletedAt IS NULL
        ORDER BY createdAt ASC
        """
    )
    fun observeActiveChapters(): Flow<List<ChapterEntity>>

    @Query(
        """
        SELECT id, novelId, title, '' AS content, format, wordCount, readingStatus, translatorStage,
               favorite, tags, paragraphIndex, charOffset, progressPercent, createdAt, modifiedAt, lastReadAt, deletedAt
        FROM chapters
        WHERE deletedAt IS NOT NULL
        ORDER BY deletedAt DESC
        """
    )
    fun observeDeletedChapters(): Flow<List<ChapterEntity>>

    // Observe full chapter (including content) ONLY for the single open chapter in Reader / Editor
    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    fun observeChapterById(id: Long): Flow<ChapterEntity?>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapterById(id: Long): ChapterEntity?

    // Fast SQLite full-text search returning matching chapter IDs without loading all bodies into RAM
    @Query(
        """
        SELECT id FROM chapters
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')
        """
    )
    suspend fun searchActiveChapterIds(query: String): List<Long>

    @Query("SELECT * FROM chapters")
    suspend fun getAllChaptersSnapshot(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND deletedAt IS NULL")
    suspend fun getChaptersForNovel(novelId: Long): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET title = :title, tags = :tags, modifiedAt = :modifiedAt WHERE id = :chapterId")
    suspend fun updateChapterTitleAndTags(
        chapterId: Long,
        title: String,
        tags: String,
        modifiedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE chapters SET favorite = :favorite WHERE id = :chapterId")
    suspend fun updateChapterFavorite(chapterId: Long, favorite: Boolean)

    @Query("UPDATE chapters SET translatorStage = :stage, modifiedAt = :modifiedAt WHERE id = :chapterId")
    suspend fun updateChapterTranslatorStage(
        chapterId: Long,
        stage: String,
        modifiedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE chapters SET novelId = :novelId, modifiedAt = :modifiedAt WHERE id = :chapterId")
    suspend fun updateChapterNovelId(
        chapterId: Long,
        novelId: Long?,
        modifiedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE chapters SET deletedAt = :deletedAt WHERE id = :chapterId")
    suspend fun updateChapterDeletedAt(chapterId: Long, deletedAt: Long?)

    @Query("UPDATE chapters SET deletedAt = :deletedAt WHERE novelId = :novelId")
    suspend fun updateNovelChaptersDeletedAt(novelId: Long, deletedAt: Long?)

    @Query(
        """
        UPDATE chapters
        SET paragraphIndex = :paragraphIndex,
            charOffset = :charOffset,
            progressPercent = CASE WHEN readingStatus = 'COMPLETED' THEN 100 ELSE :progressPercent END,
            readingStatus = CASE WHEN readingStatus = 'COMPLETED' THEN 'COMPLETED' ELSE :readingStatus END,
            lastReadAt = :lastReadAt
        WHERE id = :chapterId
        """
    )
    suspend fun updateReadingPosition(
        chapterId: Long,
        paragraphIndex: Int,
        charOffset: Int,
        progressPercent: Float,
        readingStatus: String,
        lastReadAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE chapters SET readingStatus = 'COMPLETED', progressPercent = 100, lastReadAt = :now WHERE id = :id")
    suspend fun markChapterCompleted(id: Long, now: Long = System.currentTimeMillis())
    @Query("UPDATE chapters SET title = :title, content = :content, translatorStage = :stage, wordCount = :wordCount, modifiedAt = :now WHERE id = :id")
    suspend fun saveChapterText(id: Long, title: String, content: String, stage: String, wordCount: Int, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun snapshotBackup(): ReaderBackup = ReaderBackup(getAllNovelsSnapshot(), getAllChaptersSnapshot(),
        getAllAnnotationsSnapshot(), getAllGlossarySnapshot(), getAllReadingStatsSnapshot(), null)

    @Transaction
    suspend fun restoreBackup(backup: ReaderBackup) {
        backup.novels.forEach { insertNovel(it) }
        insertChapters(backup.chapters)
        backup.annotations.forEach { insertAnnotation(it) }
        backup.glossary.forEach { insertGlossaryTerm(it) }
        backup.stats.forEach { upsertReadingStats(it) }
    }

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun permanentlyDeleteChapter(id: Long)

    @Query("DELETE FROM chapters WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun purgeExpiredTrashChapters(cutoffMillis: Long)

    @Query("DELETE FROM novels WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun purgeExpiredTrashNovels(cutoffMillis: Long)

    @Query("DELETE FROM chapters WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrashChapters()

    @Query("DELETE FROM novels WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrashNovels()

    // --- Annotations (Bookmarks, Highlights, Notes) ---
    @Query("SELECT * FROM annotations ORDER BY createdAt DESC")
    fun observeAllAnnotations(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations")
    suspend fun getAllAnnotationsSnapshot(): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Update
    suspend fun updateAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Long)

    // --- Glossary ---
    @Query("SELECT * FROM glossary ORDER BY term ASC")
    fun observeAllGlossary(): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM glossary")
    suspend fun getAllGlossarySnapshot(): List<GlossaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlossaryTerm(term: GlossaryEntity): Long

    @Update
    suspend fun updateGlossaryTerm(term: GlossaryEntity)

    @Query("DELETE FROM glossary WHERE id = :id")
    suspend fun deleteGlossaryTermById(id: Long)

    // --- Reading Stats ---
    @Query("SELECT * FROM reading_stats ORDER BY dateString DESC")
    fun observeReadingStats(): Flow<List<ReadingStatsEntity>>

    @Query("SELECT * FROM reading_stats")
    suspend fun getAllReadingStatsSnapshot(): List<ReadingStatsEntity>

    @Query("SELECT * FROM reading_stats WHERE dateString = :dateString LIMIT 1")
    suspend fun getStatsForDate(dateString: String): ReadingStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadingStats(stats: ReadingStatsEntity)

    // --- Clear for Restore ---
    @Query("DELETE FROM novels")
    suspend fun clearAllNovels()

    @Query("DELETE FROM chapters")
    suspend fun clearAllChapters()

    @Query("DELETE FROM annotations")
    suspend fun clearAllAnnotations()

    @Query("DELETE FROM glossary")
    suspend fun clearAllGlossary()
}
