package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ChapterImportHost(viewModel: MainViewModel) {
    val manager = viewModel.imports
    val visible by manager.visible.collectAsStateWithLifecycle()
    val drafts by manager.drafts.collectAsStateWithLifecycle()
    val saving by manager.saving.collectAsStateWithLifecycle()
    val error by manager.error.collectAsStateWithLifecycle()
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    if (!visible) return
    val draft = drafts.firstOrNull()
    if (draft == null) {
        AlertDialog(onDismissRequest = manager::dismiss, title = { Text("إضافة فصل") },
            text = { Text(error ?: "لا توجد مسودة") },
            confirmButton = { TextButton(onClick = manager::dismiss) { Text("إغلاق") } })
        return
    }
    key(draft.id) {
        ImportEditor(draft, novels, drafts.size, saving, error, manager::update,
            manager::dismiss, { manager.discard(draft.id) }, manager::save)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportEditor(
    draft: ImportDraft, novels: List<NovelEntity>, queued: Int, saving: Boolean, error: String?,
    onChange: (ImportDraft) -> Unit, onClose: () -> Unit, onDiscard: () -> Unit, onSave: (ImportDraft) -> Unit
) {
    var textField by remember { mutableStateOf(TextFieldValue(draft.content)) }
    var novelMenu by remember { mutableStateOf(false) }
    var newNovel by remember { mutableStateOf(draft.newNovelTitle.isNotBlank()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val linkOnly = remember(draft.content) { ChapterImport.isLinkOnly(draft.content) }
    val splitKey = remember(draft.content, draft.title, draft.splitMode) { Triple(draft.content, draft.title, draft.splitMode) }
    val preview by produceState<Pair<Triple<String, String, String>, List<SplitChapterPreview>>?>(null, splitKey) {
        value = null
        delay(200)
        value = splitKey to withContext(Dispatchers.Default) { ChapterImport.split(draft) }
    }
    val ready = preview?.first == splitKey
    val parts = preview?.takeIf { ready }?.second.orEmpty()
    val validNovel = draft.newNovelTitle.isNotBlank() || draft.novelId == null || novels.any { it.id == draft.novelId }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = !saving, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.safeDrawingPadding().imePadding(),
                topBar = {
                    TopAppBar(title = { Text("إضافة فصل") }, navigationIcon = {
                        IconButton(onClick = onClose, enabled = !saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "حفظ المسودة والرجوع") }
                    }, actions = {
                        IconButton(onClick = { confirmDiscard = true }, enabled = !saving) { Icon(Icons.Default.DeleteOutline, "حذف المسودة") }
                    })
                },
                bottomBar = {
                    Surface(shadowElevation = 4.dp) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onSave(draft) }, enabled = !saving && ready && parts.isNotEmpty() && !linkOnly && validNovel &&
                                (!newNovel || draft.newNovelTitle.isNotBlank()), modifier = Modifier.fillMaxWidth().testTag("confirm_save_pasted_button")) {
                                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Text(if (draft.splitMode == "NONE") "حفظ الفصل" else "حفظ ${parts.size} فصول")
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(if (queued > 1) "المسودة 1 من $queued — المشاركات الأخرى محفوظة في الانتظار" else "عدّل النص واختر روايته قبل الحفظ. الرجوع يحتفظ بالمسودة.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (linkOnly) item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Text("وصل رابط محادثة وليس نص الفصل. من شاشة الترجمة حدّد النص ثم اختر مشاركة ← قارئ الفصول، أو شارك ملف TXT. هذا التطبيق لا يسجّل الدخول إلى حساب ChatGPT أو Gemini.", Modifier.padding(12.dp))
                        }
                    }
                    item {
                        Box {
                            OutlinedButton(onClick = { novelMenu = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp))
                                Text(if (newNovel) "رواية جديدة" else novels.find { it.id == draft.novelId }?.title ?: "فصول بلا رواية")
                                Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, "اختيار الرواية")
                            }
                            DropdownMenu(novelMenu, { novelMenu = false }) {
                                DropdownMenuItem(text = { Text("فصول بلا رواية") }, onClick = { newNovel = false; onChange(draft.copy(novelId = null, newNovelTitle = "")); novelMenu = false })
                                DropdownMenuItem(text = { Text("إنشاء رواية جديدة") }, onClick = { newNovel = true; onChange(draft.copy(novelId = null)); novelMenu = false })
                                novels.forEach { novel -> DropdownMenuItem(text = { Text(novel.title) }, onClick = {
                                    newNovel = false; onChange(draft.copy(novelId = novel.id, newNovelTitle = "")); novelMenu = false
                                }) }
                            }
                        }
                        if (newNovel) OutlinedTextField(draft.newNovelTitle, { onChange(draft.copy(newNovelTitle = it)) },
                            label = { Text("اسم الرواية الجديدة") }, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (!validNovel) Text("الرواية السابقة حُذفت؛ اختر رواية أخرى", color = MaterialTheme.colorScheme.error)
                    }
                    item {
                        OutlinedTextField(draft.title, { onChange(draft.copy(title = it)) }, label = { Text("اسم الفصل") },
                            singleLine = true, enabled = !saving && draft.splitMode == "NONE", modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Text("تقسيم النص", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("NONE" to "فصل واحد", "HEADINGS" to "بالعناوين", "MANUAL" to "يدوي").forEach { (mode, label) ->
                                FilterChip(selected = draft.splitMode == mode, onClick = { onChange(draft.copy(splitMode = mode)) },
                                    enabled = !saving, label = { Text(label) })
                            }
                        }
                        if (draft.splitMode == "HEADINGS") Text("يفصل عند عناوين مثل «الفصل 12» و«Chapter 12»، ويحفظ المقدمة أيضًا.", style = MaterialTheme.typography.bodySmall)
                        if (draft.splitMode == "MANUAL") Text("ضع المؤشر عند بداية الفصل التالي، ثم اضغط «فصل هنا».", style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        OutlinedTextField(value = textField, onValueChange = {
                            if (it.text.length <= ChapterImport.MAX_CHARACTERS) { textField = it; onChange(draft.copy(content = it.text)) }
                        }, enabled = !saving, label = { Text("نص الفصل") }, minLines = 8, maxLines = 16,
                            modifier = Modifier.fillMaxWidth().testTag("paste_content_input"))
                        if (draft.splitMode == "MANUAL") OutlinedButton(onClick = {
                            val cursor = textField.selection.start.coerceIn(0, textField.text.length)
                            val marker = "\n\n${ChapterImport.MARKER}\n\n"
                            if (textField.text.length + marker.length <= ChapterImport.MAX_CHARACTERS) {
                                val changed = textField.text.substring(0, cursor) + marker + textField.text.substring(cursor)
                                textField = TextFieldValue(changed, TextRange(cursor + marker.length))
                                onChange(draft.copy(content = changed))
                            }
                        }, enabled = !saving) { Icon(Icons.Default.ContentCut, null); Spacer(Modifier.width(8.dp)); Text("فصل هنا") }
                    }
                    if (draft.splitMode != "NONE") {
                        item { Text(if (!ready) "جارٍ تجهيز المعاينة…" else "معاينة: ${parts.size} فصول", style = MaterialTheme.typography.titleMedium) }
                        itemsIndexed(parts) { index, part ->
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                Text("${index + 1}. ${part.title}", style = MaterialTheme.typography.titleSmall)
                                Text(part.content.take(120), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                    }
                }
            }
        }
        if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("حذف المسودة؟") },
            text = { Text("سيُحذف النص الذي لم تحفظه في المكتبة.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("رجوع") } })
    }
}
