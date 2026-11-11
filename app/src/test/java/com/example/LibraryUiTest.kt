package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChapterEntity
import com.example.data.NovelEntity
import com.example.data.ReaderSettings
import com.example.ui.LibraryScreen
import com.example.ui.theme.ChapterReaderTheme
import com.example.viewmodel.MainViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "ar-rPS-w360dp-h760dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun libraryOpensWithFoldersThenShowsOnlyTheirChapters() {
        val vm = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        val novels = listOf(NovelEntity(id = 10, title = "الأسمى الأبدي"), NovelEntity(id = 20, title = "رحلة الحكيم"))
        val chapters = listOf(ChapterEntity(id = 1, novelId = 10, title = "الفصل 1346", content = ""),
            ChapterEntity(id = 2, novelId = 20, title = "الفصل الآخر", content = ""))
        compose.setContent {
            val folder by vm.selectedNovelId.collectAsStateWithLifecycle()
            val selected by vm.selectedChapterIds.collectAsStateWithLifecycle()
            ChapterReaderTheme {
              Surface {
                LibraryScreen(vm, novels, chapters, ReaderSettings(), folder, "", emptySet(), "ALL", null, selected)
              }
            }
        }
        compose.onNodeWithTag("novel_shelf").assertExists()
        compose.onNodeWithText("الفصل 1346").assertDoesNotExist()
        savePreview("library")
        compose.onNodeWithTag("novel_10").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("chapter_item_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("الفصل 1346").assertExists()
        compose.onNodeWithText("الفصل الآخر").assertDoesNotExist()
        savePreview("chapters")
        compose.onNodeWithContentDescription("الروايات").performClick()
        compose.onNodeWithTag("novel_shelf").assertExists()
    }

    @Test fun longPressAllowsSelectingMultipleNovelsAndConfirmingTrash() {
        val vm = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            ChapterReaderTheme {
              Surface {
                com.example.ui.NovelShelfScreen(vm, listOf(NovelEntity(id = 1, title = "رواية أولى"),
                    NovelEntity(id = 2, title = "رواية ثانية")), emptyList(), {}, {})
              }
            }
        }
        compose.onNodeWithTag("novel_1").performTouchInput { longClick() }
        compose.onNodeWithTag("novel_2").performClick()
        compose.onNodeWithText("2 محدد").assertExists()
        compose.onNodeWithTag("delete_selected_novels").performClick()
        compose.onNodeWithText("حذف 2 رواية؟").assertExists()
        compose.onNodeWithText("رجوع").performClick()
        compose.onNodeWithText("2 محدد").assertExists()
    }

    private fun savePreview(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/ui-previews").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
