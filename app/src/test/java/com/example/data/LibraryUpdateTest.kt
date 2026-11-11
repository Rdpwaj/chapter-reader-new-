package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryUpdateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var dao: AppDao
    private lateinit var repo: ReaderRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.appDao()
        repo = ReaderRepository(context, dao)
    }
    @After fun close() { db.close() }

    @Test fun folderBatchDeleteAndUndoPreserveOldTrashAndOtherNovels() = runBlocking {
        val a = dao.insertNovel(NovelEntity(title = "الأسمى الأبدي"))
        val b = dao.insertNovel(NovelEntity(title = "رواية ثانية"))
        val c = dao.insertNovel(NovelEntity(title = "رواية باقية"))
        val chapter = dao.insertChapter(ChapterEntity(novelId = a, title = "الفصل 1", content = "محتوى كامل", charOffset = 19, favorite = true))
        val priorTrash = dao.insertChapter(ChapterEntity(novelId = a, title = "حذف سابق", content = "نص سابق", deletedAt = 12L))
        val untouched = dao.insertChapter(ChapterEntity(novelId = c, title = "باقي", content = "محتوى آخر"))
        dao.insertAnnotation(AnnotationEntity(chapterId = chapter, noteText = "ملاحظة"))
        val removed = repo.moveNovelsToTrash(listOf(a, b, a))
        assertEquals(2, removed.size)
        assertNotNull(dao.getChapterById(chapter)!!.deletedAt)
        assertEquals(12L, dao.getChapterById(priorTrash)!!.deletedAt)
        assertNull(dao.getChapterById(untouched)!!.deletedAt)
        repo.restoreNovelsFromTrash(removed)
        assertNull(dao.getNovelById(a)!!.deletedAt)
        assertNull(dao.getChapterById(chapter)!!.deletedAt)
        assertEquals("محتوى كامل", dao.getChapterById(chapter)!!.content)
        assertEquals(19, dao.getChapterById(chapter)!!.charOffset)
        assertTrue(dao.getChapterById(chapter)!!.favorite)
        assertEquals(12L, dao.getChapterById(priorTrash)!!.deletedAt)
        assertEquals("ملاحظة", dao.getAllAnnotationsSnapshot().single().noteText)
    }

    @Test fun importPreservesShortIntroAndCreatesNovelAtomically() = runBlocking {
        val draft = ImportDraft(content = "مقدمة قصيرة\n\nالفصل 12: الوصول\nالنص الأول\n\nالفصل 13: العودة\nالنص الثاني", splitMode = "HEADINGS", newNovelTitle = "الأسمى الأبدي")
        val (novel, ids) = repo.saveImport(draft)
        assertEquals(3, ids.size)
        assertEquals("الأسمى الأبدي", dao.getNovelById(novel!!)!!.title)
        val bodies = ids.map { dao.getChapterById(it)!!.content }
        assertEquals("مقدمة قصيرة", bodies.first())
        assertTrue(bodies[1].contains("النص الأول"))
        assertTrue(bodies[2].contains("النص الثاني"))
        assertTrue(dao.getAllReadingStatsSnapshot().isEmpty())
    }

    @Test fun invalidImportDoesNotCreateAnEmptyNovel() = runBlocking {
        try { repo.saveImport(ImportDraft(content = " ", newNovelTitle = "لا تُنشأ")); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, dao.countNovels())
        assertEquals(0, dao.countChapters())
    }

    @Test fun interruptedImportRetryDoesNotDuplicateChaptersOrNovel() = runBlocking {
        val draft = ImportDraft(content = "الفصل 1\nالنص", newNovelTitle = "رواية جديدة")
        val first = repo.saveImport(draft)
        assertEquals(first, repo.saveImport(draft))
        assertEquals(1, dao.countNovels())
        assertEquals(1, dao.countChapters())
        assertTrue(repo.wasDraftImported(draft.id))
    }

    @Test fun shareTextAndSelectedTextArriveVerbatimAndLinksAreRecognized() {
        val content = "الفصل 10\n\n  نص الترجمة كاملًا.\n"
        assertEquals(content, IncomingShare.read(context, Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, content)))
        assertEquals(content, IncomingShare.read(context, Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, content)))
        assertTrue(ChapterImport.isLinkOnly("https://chatgpt.com/share/123"))
        assertFalse(ChapterImport.isLinkOnly("رواية\nhttps://example.org"))
        assertNull(IncomingShare.read(context, Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_TEXT, content)))
    }

    @Test fun manualSplitDoesNotLoseEitherSideOfTheBoundary() {
        val parts = ChapterImport.split(ImportDraft(content = "بداية\nنهاية الجزء الأول\n${ChapterImport.MARKER}\nبداية الجزء الثاني\nآخر كلمة", splitMode = "MANUAL"))
        assertEquals(2, parts.size)
        assertTrue(parts[0].content.endsWith("نهاية الجزء الأول"))
        assertTrue(parts[1].content.startsWith("بداية الجزء الثاني"))
        assertTrue(parts[1].content.endsWith("آخر كلمة"))
    }

    @Test fun translatedMarkdownHeadingsAndEasternDigitsSplitCorrectly() {
        val parts = ChapterImport.split(ImportDraft(content = "تمهيد\n\n**الفصل ۱۲: البداية**\nالنص\n\n## chapter 13\nنص آخر", splitMode = "HEADINGS"))
        assertEquals(3, parts.size)
        assertEquals("تمهيد", parts[0].content)
        assertEquals("الفصل ۱۲: البداية", parts[1].title)
        assertTrue(parts[1].content.startsWith("**الفصل"))
        assertEquals("chapter 13", parts[2].title)
    }

    @Test fun draftsRoundTripLongTextAndDestination() {
        val store = ImportDraftStore(context)
        val drafts = listOf(ImportDraft(content = "نص محفوظ ".repeat(10000), novelId = 7, splitMode = "MANUAL"), ImportDraft(content = "المشاركة التالية"))
        store.write(drafts)
        assertEquals(drafts, store.read())
        store.write(emptyList())
    }

    @Test fun coversSurviveBackupAndMalformedCoversDoNotOverwriteData() = runBlocking {
        val bytes = ByteArrayOutputStream().also { Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        val cover = CoverStore(context).restore(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        val id = dao.insertNovel(NovelEntity(title = "رواية بغلاف", coverFile = cover))
        val backup = repo.exportFullBackupJson(ReaderSettings())
        CoverStore(context).file(cover)!!.delete()
        assertTrue(repo.importFullBackupJson(backup))
        assertNotNull(CoverStore(context).bitmap(dao.getNovelById(id)!!.coverFile))
        val malformed = org.json.JSONObject(backup)
        malformed.getJSONArray("novels").getJSONObject(0).put("title", "لا يتغير").put("coverData", "broken")
        assertFalse(repo.importFullBackupJson(malformed.toString()))
        assertEquals("رواية بغلاف", dao.getNovelById(id)!!.title)
        assertNull(CoverStore(context).file("../../secret.jpg"))
    }

    @Test fun versionOneDatabaseMigratesWithoutLosingReadingData() = runBlocking {
        val name = "migration-reader-test.db"
        context.deleteDatabase(name)
        val original = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val id = original.appDao().insertNovel(NovelEntity(title = "قبل التحديث"))
        val chapter = original.appDao().insertChapter(ChapterEntity(novelId = id, title = "الفصل", content = "المحتوى القديم", charOffset = 52, paragraphIndex = 8))
        val sql = original.openHelper.writableDatabase
        // Reconstruct the actual v1 novels schema; all other tables stay unchanged.
        sql.execSQL("ALTER TABLE novels RENAME TO novels_v2")
        sql.execSQL("CREATE TABLE novels (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, colorHex TEXT NOT NULL, category TEXT NOT NULL, tags TEXT NOT NULL, sortBy TEXT NOT NULL, sortAscending INTEGER NOT NULL, settingsOverrideJson TEXT, createdAt INTEGER NOT NULL, deletedAt INTEGER)")
        sql.execSQL("INSERT INTO novels SELECT id,title,description,colorHex,category,tags,sortBy,sortAscending,settingsOverrideJson,createdAt,deletedAt FROM novels_v2")
        sql.execSQL("DROP TABLE novels_v2")
        sql.execSQL("DROP TABLE chapter_imports")
        sql.execSQL("PRAGMA user_version=1")
        original.close()
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            assertEquals("قبل التحديث", updated.appDao().getNovelById(id)!!.title)
            assertNull(updated.appDao().getNovelById(id)!!.coverFile)
            assertEquals("المحتوى القديم", updated.appDao().getChapterById(chapter)!!.content)
            assertEquals(52, updated.appDao().getChapterById(chapter)!!.charOffset)
            assertEquals(8, updated.appDao().getChapterById(chapter)!!.paragraphIndex)
        } finally { updated.close(); context.deleteDatabase(name) }
    }
}
