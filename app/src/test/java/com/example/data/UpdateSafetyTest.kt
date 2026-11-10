package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class UpdateSafetyTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: AppDao
    private lateinit var repository: ReaderRepository
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.appDao()
        repository = ReaderRepository(context, dao)
    }
    @After fun close() { db.close() }

    @Test fun readingPositionKeepsContentAndCompletion() = runBlocking {
        val id = dao.insertChapter(ChapterEntity(title = "فصل", content = "نص محفوظ", readingStatus = "COMPLETED", favorite = true))
        repository.saveReadingPosition(id, 3, 29, 100)
        val actual = dao.getChapterById(id)!!
        assertEquals("نص محفوظ", actual.content)
        assertTrue(actual.favorite)
        assertEquals(3, actual.paragraphIndex)
        assertEquals(29, actual.charOffset)
        assertEquals("COMPLETED", actual.readingStatus)
        assertEquals(100f, actual.progressPercent)
        assertTrue(dao.getAllReadingStatsSnapshot().isEmpty())
    }
    @Test fun openingShortChapterDoesNotCompleteItOrTrackTime() = runBlocking {
        val id = dao.insertChapter(ChapterEntity(title = "فصل قصير", content = "نص"))
        repository.saveReadingPosition(id, 0, 0, 1)
        assertEquals("READING", dao.getChapterById(id)!!.readingStatus)
        repository.markChapterCompleted(id)
        repository.markChapterCompleted(id)
        assertEquals(1, dao.observeActiveChapters().first().count { it.readingStatus == "COMPLETED" })
        assertTrue(dao.getAllReadingStatsSnapshot().isEmpty())
    }
    @Test fun editingEmptyTextPreservesMetadata() = runBlocking {
        val id = dao.insertChapter(ChapterEntity(title = "فصل", content = "old", paragraphIndex = 4, favorite = true))
        repository.saveChapterText(ChapterEntity(id = id, title = "جديد", content = ""))
        val actual = dao.getChapterById(id)!!
        assertEquals("", actual.content)
        assertEquals(4, actual.paragraphIndex)
        assertTrue(actual.favorite)
    }
    @Test fun legacyBackupRestoresNotesAndReadingPosition() = runBlocking {
        val json = """{"version":1,"novels":[{"id":9,"title":"رواية"}],"chapters":[{"id":7,"novelId":9,"title":"فصل","content":"نص محفوظ","paragraphIndex":31,"charOffset":12}],"annotations":[{"chapterId":7,"type":"NOTE","noteText":"ملاحظتي"}],"glossary":[{"term":"اسم","translation":"ترجمة"}]}"""
        assertTrue(repository.importFullBackupJson(json))
        assertEquals(31, dao.getChapterById(7)!!.paragraphIndex)
        assertEquals("ملاحظتي", dao.getAllAnnotationsSnapshot().single().noteText)
        assertEquals("ترجمة", dao.getAllGlossarySnapshot().single().translation)
    }
    @Test fun backupRoundTripRetainsTrashNotesAndIds() = runBlocking {
        val n = dao.insertNovel(NovelEntity(title = "رواية", deletedAt = 1234))
        val id = dao.insertChapter(ChapterEntity(novelId = n, title = "فصل", content = "نص", deletedAt = 1234, favorite = true, charOffset = 72))
        dao.insertAnnotation(AnnotationEntity(chapterId = id, noteText = "ملاحظة", createdAt = 123))
        dao.insertGlossaryTerm(GlossaryEntity(term = "اسم", translation = "Name", createdAt = 456))
        val backup = repository.exportFullBackupJson(ReaderSettings(fontSizeSp = 24f))
        assertTrue(repository.importFullBackupJson(backup))
        assertTrue(repository.importFullBackupJson(backup))
        assertEquals(1, dao.getAllAnnotationsSnapshot().size)
        assertEquals(1, dao.getAllGlossarySnapshot().size)
        assertEquals(1234L, dao.getChapterById(id)!!.deletedAt)
        assertEquals(72, dao.getChapterById(id)!!.charOffset)
        assertEquals(123L, dao.getAllAnnotationsSnapshot().single().createdAt)
    }
    @Test fun malformedBackupCannotPartiallyOverwriteLibrary() = runBlocking {
        dao.insertNovel(NovelEntity(id = 1, title = "الأصل"))
        dao.insertChapter(ChapterEntity(id = 1, title = "الأصل", content = "المحتوى الأصلي"))
        val broken = """{"version":1,"novels":[{"id":1,"title":"خطأ"}],"chapters":[{"id":1,"title":"خطأ","content":"تغيير"},{"id":2,"title":"ناقص"}]}"""
        assertFalse(repository.importFullBackupJson(broken))
        assertEquals("الأصل", dao.getNovelById(1)!!.title)
        assertEquals("المحتوى الأصلي", dao.getChapterById(1)!!.content)
    }
    @Test fun lightweightListDoesNotLoadChapterBodies() = runBlocking {
        val id = dao.insertChapter(ChapterEntity(title = "طويل", content = "نص كبير ".repeat(10000)))
        assertEquals("", dao.observeActiveChapters().first().single().content)
        assertTrue(dao.getChapterById(id)!!.content.isNotEmpty())
    }
}
