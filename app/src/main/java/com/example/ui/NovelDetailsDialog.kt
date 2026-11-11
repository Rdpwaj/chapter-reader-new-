package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.CoverStore
import com.example.data.NovelEntity
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NovelDetailsDialog(existingNovel: NovelEntity?, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(existingNovel?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(existingNovel?.description.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(existingNovel?.category ?: "عام") }
    var tags by rememberSaveable { mutableStateOf(existingNovel?.tags.orEmpty()) }
    var extraDetails by rememberSaveable { mutableStateOf(false) }
    var color by rememberSaveable { mutableStateOf(existingNovel?.colorHex ?: "#D97706") }
    var cover by rememberSaveable { mutableStateOf(existingNovel?.coverFile) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            try { cover = withContext(Dispatchers.IO) { CoverStore(context).importUri(uri) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = "تعذر فتح الصورة؛ اختر صورة أخرى" }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existingNovel == null) "رواية جديدة" else "تعديل الرواية") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NovelCover(cover, title.ifBlank { "غلاف الرواية" }, color, Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !busy) { Text("اختيار صورة") }
                    if (cover != null) TextButton(onClick = { cover = null }, enabled = !busy) { Text("إزالة الصورة") }
                }
                OutlinedTextField(title, { title = it }, label = { Text("اسم الرواية") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("وصف اختياري") }, maxLines = 3, enabled = !busy, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { extraDetails = !extraDetails }) { Text("تصنيف ووسوم") }
                if (extraDetails) {
                    OutlinedTextField(category, { category = it }, label = { Text("التصنيف") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tags, { tags = it }, label = { Text("وسوم مفصولة بفاصلة") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
                Text("لون الغلاف الافتراضي", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#D97706", "#0D9488", "#2563EB", "#7C3AED", "#BE123C", "#475569").forEach { hex ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(parseColorSafe(hex, MaterialTheme.colorScheme.primary))
                            .border(if (hex == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable(enabled = !busy) { color = hex })
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(enabled = !busy && title.isNotBlank(), onClick = {
            busy = true
            val novel = (existingNovel ?: NovelEntity(title = title.trim())).copy(title = title.trim(), description = description.trim(), colorHex = color,
                category = category.trim().ifBlank { "عام" }, tags = tags.trim())
            viewModel.saveNovelWithCover(novel, cover) { message ->
                busy = false
                if (message == null) onDismiss() else error = message
            }
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("رجوع") } })
}
