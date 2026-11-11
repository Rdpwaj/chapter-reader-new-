package com.example.viewmodel

import android.content.Context
import com.example.data.ChapterImport
import com.example.data.ImportDraft
import com.example.data.ImportDraftStore
import com.example.data.ReaderRepository
import com.example.data.TextProcessingUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Drafts survive rotation and restart; a second share queues behind an unfinished draft. */
@OptIn(FlowPreview::class)
class ChapterImportManager(
    context: Context,
    private val scope: CoroutineScope,
    private val repository: ReaderRepository,
    private val onSaved: (Long?, Int) -> Unit
) {
    private val store = ImportDraftStore(context)
    private val lock = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val _drafts = MutableStateFlow<List<ImportDraft>>(emptyList())
    val drafts = _drafts.asStateFlow()
    val visible = MutableStateFlow(false)
    val saving = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init {
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.read() }
                _drafts.value = saved.filterNot { repository.wasDraftImported(it.id) }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = "تعذر فتح المسودة المحفوظة" }
            ready.complete(Unit)
            _drafts.debounce(350).collect { persist() }
        }
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        lock.withLock {
            try { store.write(_drafts.value) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = "تعذر حفظ المسودة؛ تحقق من مساحة الهاتف" }
        }
    }

    fun flush() { scope.launch { ready.await(); persist() } }

    fun open(text: String = "", novelId: Long? = null, fromShare: Boolean = false) {
        scope.launch {
            ready.await()
            if (text.length > ChapterImport.MAX_CHARACTERS) {
                error.value = "النص كبير جدًا؛ أرسله على دفعات أصغر"
                visible.value = true
                return@launch
            }
            if (fromShare || _drafts.value.isEmpty()) {
                if (_drafts.value.size >= 5) {
                    error.value = "لديك خمس مسودات؛ احفظ واحدة ثم أعد المشاركة"
                    visible.value = true
                    return@launch
                }
                val draft = ImportDraft(content = text, novelId = novelId,
                    title = if (text.isBlank() || ChapterImport.isLinkOnly(text)) "" else TextProcessingUtils.suggestTitleFromContent(text))
                _drafts.value += draft
            }
            visible.value = true
            persist()
        }
    }

    fun update(draft: ImportDraft) {
        if (saving.value || draft.content.length > ChapterImport.MAX_CHARACTERS) return
        _drafts.value = _drafts.value.map { if (it.id == draft.id) draft else it }
        error.value = null
    }

    fun dismiss() { if (!saving.value) { visible.value = false; flush() } }

    fun discard(id: String) {
        if (saving.value) return
        _drafts.value = _drafts.value.filterNot { it.id == id }
        visible.value = _drafts.value.isNotEmpty()
        error.value = null
        flush()
    }

    fun save(draft: ImportDraft) {
        if (saving.value) return
        saving.value = true
        error.value = null
        scope.launch {
            try {
                val (novel, ids) = repository.saveImport(draft)
                _drafts.value = _drafts.value.filterNot { it.id == draft.id }
                visible.value = _drafts.value.isNotEmpty()
                persist()
                onSaved(novel, ids.size)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "تعذر حفظ الفصل؛ المسودة ما زالت موجودة" }
            finally { saving.value = false }
        }
    }
}
