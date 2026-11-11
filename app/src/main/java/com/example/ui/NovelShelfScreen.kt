package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelShelfScreen(
    viewModel: MainViewModel, novels: List<NovelEntity>, chapters: List<ChapterEntity>,
    onEdit: (NovelEntity) -> Unit, onCreate: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var showDelete by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    val drafts by viewModel.imports.drafts.collectAsStateWithLifecycle()
    val counts = remember(chapters) { chapters.groupingBy { it.novelId }.eachCount() }
    val unfiledCount = remember(counts, novels) {
        val ids = novels.map { it.id }.toSet()
        counts.filterKeys { it == null || it !in ids }.values.sum()
    }
    val visible = remember(novels, query) {
        novels.filter { it.title.contains(query, true) || it.tags.contains(query, true) }
            .sortedWith { a, b -> TextProcessingUtils.naturalCompare(a.title, b.title) }
    }
    LaunchedEffect(novels) { selected = selected.intersect(novels.map { it.id }.toSet()) }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    Column(Modifier.fillMaxSize().testTag("novel_shelf")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected.isEmpty()) {
                Column(Modifier.weight(1f)) {
                    Text("المكتبة", style = MaterialTheme.typography.headlineMedium)
                    Text("رواياتك في مكان واحد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onCreate) { Icon(Icons.Default.CreateNewFolder, "رواية جديدة") }
                Box {
                    IconButton(onClick = { tools = true }) { Icon(Icons.Default.MoreVert, "أدوات المكتبة") }
                    DropdownMenu(tools, { tools = false }) {
                        DropdownMenuItem(text = { Text("تحديد الروايات") }, enabled = visible.isNotEmpty(), onClick = { selected = visible.take(1).map { it.id }.toSet(); tools = false })
                        listOf("المحذوفات" to SubScreen.TRASH, "الملاحظات والعلامات" to SubScreen.ANNOTATIONS,
                            "المصطلحات" to SubScreen.GLOSSARY, "بحث واستبدال" to SubScreen.FIND_REPLACE).forEach { (label, screen) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { tools = false; viewModel.openSubScreen(screen) })
                        }
                    }
                }
            } else {
                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, "إلغاء التحديد") }
                Text("${selected.size} محدد", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = { selected = visible.map { it.id }.toSet() }) { Icon(Icons.Default.SelectAll, "تحديد كل الروايات") }
                IconButton(onClick = { showDelete = true }, modifier = Modifier.testTag("delete_selected_novels")) { Icon(Icons.Default.DeleteOutline, "حذف الروايات المحددة", tint = MaterialTheme.colorScheme.error) }
            }
        }
        OutlinedTextField(query, { query = it }, placeholder = { Text("ابحث عن رواية") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (drafts.isNotEmpty()) TextButton(onClick = { viewModel.imports.open() }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(6.dp)); Text("متابعة المسودة (${drafts.size})")
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (visible.isEmpty() && query.isBlank() && novels.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
                    Text("ابدأ بروايتك الأولى", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp)); Text("أنشئ مجلدًا للرواية، واختر غلافًا، ثم أضف فصولها.")
                    TextButton(onClick = onCreate) { Text("إنشاء رواية") }
                } }
            }
            if (visible.isEmpty() && query.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }) { Text("لا توجد رواية بهذا الاسم", Modifier.padding(12.dp)) }
            items(visible, key = { it.id }, contentType = { "novel" }) { novel ->
                val chosen = novel.id in selected
                Card(modifier = Modifier.testTag("novel_${novel.id}").combinedClickable(
                    onClick = { if (selected.isNotEmpty()) toggle(novel.id) else viewModel.setSelectedNovelId(novel.id) },
                    onLongClick = { toggle(novel.id) }),
                    border = if (chosen) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                    Box {
                        NovelCover(novel.coverFile, novel.title, novel.colorHex, Modifier.fillMaxWidth().aspectRatio(0.82f))
                        if (selected.isNotEmpty()) Checkbox(chosen, { toggle(novel.id) }, Modifier.align(Alignment.TopStart))
                        else IconButton(onClick = { onEdit(novel) }, modifier = Modifier.align(Alignment.TopEnd)
                            .padding(4.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .9f), RoundedCornerShape(24.dp))) {
                            Icon(Icons.Default.Edit, "تعديل ${novel.title}")
                        }
                    }
                    Column(Modifier.padding(12.dp)) {
                        Text(novel.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text("${counts[novel.id] ?: 0} فصل", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (query.isBlank() && unfiledCount > 0) item(key = "unfiled", span = { GridItemSpan(maxLineSpan) }) {
                OutlinedCard(onClick = { viewModel.setSelectedNovelId(-1L) }, modifier = Modifier.fillMaxWidth().testTag("unfiled_folder")) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(12.dp))
                        Column { Text("فصول بلا رواية"); Text("$unfiledCount فصل", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("حذف ${selected.size} رواية؟") },
        text = { Text("ستنتقل الروايات المحددة وفصولها إلى المحذوفات. يمكنك استعادتها خلال 30 يومًا.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteNovelsToTrash(selected.toList()); selected = emptySet(); showDelete = false }) { Text("نقل إلى المحذوفات") } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("رجوع") } })
}

@Composable
fun NovelCover(file: String?, title: String, colorHex: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { CoverStore(context).bitmap(file) }.getOrNull() }
    }
    val color = parseColorSafe(colorHex, MaterialTheme.colorScheme.primary)
    Box(modifier.background(color.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) Image(loaded.asImageBitmap(), contentDescription = "غلاف $title", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Default.MenuBook, null, Modifier.size(42.dp), tint = color)
            Spacer(Modifier.height(12.dp))
            Text(title.take(65), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 3)
        }
    }
}
