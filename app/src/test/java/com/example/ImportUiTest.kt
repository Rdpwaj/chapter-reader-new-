package com.example

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.ImportDraftStore
import com.example.data.IncomingShare
import com.example.ui.ChapterImportHost
import com.example.ui.theme.ChapterReaderTheme
import com.example.viewmodel.MainViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "ar-rPS-w360dp-h760dp-xxhdpi")
class ImportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharingOpensEditorAndSecondShareKeepsFirstDraft() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ImportDraftStore(context).write(emptyList())
        val vm = MainViewModel(context)
        compose.setContent { ChapterReaderTheme { ChapterImportHost(vm) } }
        val first = "الفصل 12\nنص الترجمة الأول كاملًا"
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, first)
        compose.runOnIdle { vm.onReceiveSharedText(IncomingShare.read(context, intent)!!) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("paste_content_input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("paste_content_input").assertTextContains(first)
        compose.onNodeWithTag("paste_content_input").performTextReplacement(first + "\nتعديل محفوظ")
        compose.runOnIdle { vm.onReceiveSharedText("الفصل 13\nالمشاركة الثانية") }
        compose.waitUntil(5_000) { vm.imports.drafts.value.size == 2 }
        compose.onNodeWithTag("paste_content_input").assertTextContains("تعديل محفوظ", substring = true)
        compose.onNodeWithContentDescription("حفظ المسودة والرجوع").performClick()
        compose.onNodeWithTag("paste_content_input").assertDoesNotExist()
        compose.runOnIdle { vm.imports.open() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("paste_content_input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("paste_content_input").assertTextContains("تعديل محفوظ", substring = true)
        assertEquals("الفصل 13\nالمشاركة الثانية", vm.imports.drafts.value[1].content)
    }
}
