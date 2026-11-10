package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AnnotationEntity
import com.example.data.ChapterEntity
import com.example.data.NovelEntity
import com.example.data.ReaderSettings
import com.example.data.ReadingStatsEntity
import com.example.data.TextProcessingUtils
import com.example.ui.theme.AvailableReaderFonts
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.SubScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1) "متابعة" (Continue Reading & Translator Review Queue Tab)
 */
@Composable
fun ContinueReadingTabScreen(viewModel: MainViewModel, chapters: List<ChapterEntity>, novels: List<NovelEntity>,
    annotations: List<AnnotationEntity>, settings: ReaderSettings, modifier: Modifier = Modifier) {
    val recent = remember(chapters) { chapters.filter { it.lastReadAt > 0L && it.readingStatus != "COMPLETED" }.sortedByDescending { it.lastReadAt } }
    val completed = remember(chapters) { chapters.count { it.readingStatus == "COMPLETED" } }
    val novelsById = remember(novels) { novels.associateBy { it.id } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "heading") {
            Text("متابعة القراءة", style = MaterialTheme.typography.headlineMedium)
            Text("$completed من ${chapters.size} فصلًا مقروءًا", style = MaterialTheme.typography.bodyMedium)
        }
        if (recent.isEmpty()) item(key = "empty") { Text("ستظهر هنا الفصول التي بدأت قراءتها.", modifier = Modifier.padding(vertical = 24.dp)) }
        items(recent, key = { it.id }, contentType = { "chapter" }) { chapter ->
            Card(onClick = { viewModel.openChapterForReading(chapter.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        novelsById[chapter.novelId]?.let { Text(it.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        Text(chapter.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${chapter.progressPercent.toInt()}% من الفصل", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.PlayArrow, "متابعة")
                }
            }
        }
    }
}

@Composable
fun FullSettingsScreen(
    viewModel: MainViewModel,
    settings: ReaderSettings,
    novels: List<NovelEntity>,
    chapters: List<ChapterEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    // Create Document Launcher for Exporting Full JSON Backup
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    val json = viewModel.repository.exportFullBackupJson(settings)
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                } }
                if (result.isSuccess) viewModel.updateSettings { it.copy(lastBackupTimestamp = System.currentTimeMillis()) }
                viewModel.showToastMessage(if (result.isSuccess) "تم حفظ النسخة" else "تعذر حفظ النسخة. تحقق من مساحة الهاتف.")
            }
        }
    }

    // Open Document Launcher for Restoring Full JSON Backup
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Cannot read backup")
                    viewModel.repository.importFullBackupJson(json)
                }.getOrDefault(false) }
                viewModel.showToastMessage(if (ok) "تمت استعادة النسخة" else "تعذر استعادة الملف. لم تُحذف مكتبتك.")
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 92.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (settings.language == "en") "Settings" else "الإعدادات",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = {
                        val nextLang = if (settings.language == "ar") "en" else "ar"
                        viewModel.updateSettings { it.copy(language = nextLang) }
                    },
                    modifier = Modifier.testTag("toggle_language_button")
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (settings.language == "ar") "English" else "العربية")
                }
            }
        }

        // Section 6: Backup, Export, Trash & App Lock (النسخ الاحتياطي والأمان)
        item {
            SettingsSectionCard(title = "النسخ الاحتياطي والحماية") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            createBackupLauncher.launch("chapter_reader_backup.json")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_backup_button")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ نسخة")
                    }
                    OutlinedButton(
                        onClick = {
                            restoreBackupLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_backup_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استعادة نسخة")
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.openSubScreen(SubScreen.TRASH) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فتح سلة المحذوفات (30 يومًا)")
                }

                OutlinedButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (settings.appLockPin.isBlank()) "تعيين رمز قفل للتطبيق (PIN)"
                        else "تغيير أو إلغاء رمز قفل التطبيق (مفعل)"
                    )
                }

                TextButton(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إعادة ضبط الإعدادات")
                }
            }
        }

        // Section 1: Font Settings (قائمة الخط)
        item {
            var fontMenuOpen by remember { mutableStateOf(false) }
            val currentFontLabel = remember(settings.fontFamily) {
                AvailableReaderFonts.find { it.first == settings.fontFamily }?.second ?: settings.fontFamily
            }
            SettingsSectionCard(
                title = "الخط وحجمه",
                initiallyExpanded = false
            ) {
                Text("اختر عائلة الخط من القائمة:", style = MaterialTheme.typography.labelLarge)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { fontMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(currentFontLabel, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ExpandMore, contentDescription = "فتح قائمة الخطوط")
                        }
                    }
                    DropdownMenu(
                        expanded = fontMenuOpen,
                        onDismissRequest = { fontMenuOpen = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        AvailableReaderFonts.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (settings.fontFamily == key) FontWeight.Bold else FontWeight.Normal,
                                        color = if (settings.fontFamily == key) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.updateSettings { it.copy(fontFamily = key) }
                                    fontMenuOpen = false
                                }
                            )
                        }
                    }
                }

                SettingsSliderRow(
                    label = "حجم الخط (${settings.fontSizeSp.toInt()} sp)",
                    value = settings.fontSizeSp,
                    range = 14f..36f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(fontSizeSp = v) } }
                )

                SettingsSwitchRow(
                    label = "خط عريض",
                    checked = settings.isBoldWeight,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(isBoldWeight = v) } }
                )
            }
        }

        // Section 2: Text & Lines (النص والأسطر)
        item {
            SettingsSectionCard(title = "المسافات والهوامش") {
                SettingsSliderRow(
                    label = "ارتفاع السطر (${String.format(Locale.US, "%.2f", settings.lineHeightMultiplier)})",
                    value = settings.lineHeightMultiplier,
                    range = 1.2f..2.6f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(lineHeightMultiplier = v) } }
                )
                SettingsSliderRow(
                    label = "المسافة بين الفقرات (${settings.paragraphSpacingDp.toInt()} dp)",
                    value = settings.paragraphSpacingDp,
                    range = 4f..32f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(paragraphSpacingDp = v) } }
                )
                SettingsSliderRow(
                    label = "المسافة البادئة للسطر الأول (${settings.firstLineIndentDp.toInt()})",
                    value = settings.firstLineIndentDp,
                    range = 0f..40f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(firstLineIndentDp = v) } }
                )
                SettingsSliderRow(
                    label = "الهوامش الجانبية (${settings.marginHorizontalDp.toInt()} dp)",
                    value = settings.marginHorizontalDp,
                    range = 8f..48f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(marginHorizontalDp = v) } }
                )
                SettingsSliderRow(
                    label = "الهوامش العلوية والسفلية (${settings.marginVerticalDp.toInt()} dp)",
                    value = settings.marginVerticalDp,
                    range = 8f..48f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(marginVerticalDp = v) } }
                )

                Text("محاذاة النص:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "RIGHT" to "يمين",
                        "JUSTIFY" to "ضبط",
                        "CENTER" to "وسط",
                        "LEFT" to "يسار"
                    ).forEach { (alignKey, label) ->
                        FilterChip(
                            selected = settings.textAlign == alignKey,
                            onClick = { viewModel.updateSettings { it.copy(textAlign = alignKey) } },
                            label = { Text(label) }
                        )
                    }
                }

                SettingsSwitchRow(
                    label = "فصل الفقرات بسطر فارغ واضح",
                    checked = settings.emptyLineBetweenParagraphs,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(emptyLineBetweenParagraphs = v) } }
                )
                SettingsSwitchRow(
                    label = "إخفاء التشكيل (الحركات) أثناء القراءة",
                    checked = settings.hideTashkeel,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(hideTashkeel = v) } }
                )
                SettingsSwitchRow(
                    label = "إصلاح تلقائي لمسافات وأسطر النص الملصوق",
                    checked = settings.autoFixPastedWhitespace,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoFixPastedWhitespace = v) } }
                )
            }
        }

        // Section 3: Colors & Themes (الألوان والثيمات)
        item {
            SettingsSectionCard(title = "الألوان والإضاءة") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        "SEPIA" to "سيبيا دافئ",
                        "PAPER" to "ورقي كلاسيكي",
                        "LIGHT" to "فاتح",
                        "DARK" to "داكن",
                        "AMOLED" to "أسود AMOLED",
                        "SYSTEM" to "تلقائي حسب النظام"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.updateSettings { it.copy(themeMode = mode) } },
                            label = { Text(label) }
                        )
                    }
                }

                SettingsSliderRow(
                    label = if (settings.inAppBrightness < 0f) "سطوع داخل التطبيق (تلقائي حسب النظام)"
                    else "سطوع داخل التطبيق (${(settings.inAppBrightness * 100).toInt()}%)",
                    value = if (settings.inAppBrightness < 0f) 0.5f else settings.inAppBrightness,
                    range = 0.05f..1.0f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(inAppBrightness = v) } }
                )
                SettingsSliderRow(
                    label = "فلتر دافئ لراحة العين (${(settings.warmEyeFilterIntensity * 100).toInt()}%)",
                    value = settings.warmEyeFilterIntensity,
                    range = 0f..0.5f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(warmEyeFilterIntensity = v) } }
                )
            }
        }

        // Section 4: Display & Elements Visibility (العرض والعناصر)
        item {
            SettingsSectionCard(title = "شكل المكتبة والقراءة") {
                SettingsSwitchRow(
                    label = "شريط تقدم الفصل",
                    checked = settings.showThinEdgeProgress,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(showThinEdgeProgress = v) } }
                )

                SettingsSwitchRow(
                    label = "نسبة تقدم الفصل",
                    checked = settings.showPercentAndRemaining,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(showPercentAndRemaining = v) } }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("عناصر بطاقة الفصل في المكتبة:", style = MaterialTheme.typography.labelLarge)


                SettingsSwitchRow(
                    label = "شريط ونسبة التقدم",
                    checked = settings.libShowProgress,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(libShowProgress = v) } }
                )
                SettingsSwitchRow(
                    label = "حالة القراءة (جديد / قيد القراءة / مكتمل)",
                    checked = settings.libShowStatus,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(libShowStatus = v) } }
                )
                SettingsSwitchRow(
                    label = "تاريخ آخر تعديل",
                    checked = settings.libShowModifiedDate,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(libShowModifiedDate = v) } }
                )
            }
        }

        // Section 5: Behavior & Reading Speed (السلوك وسرعة القراءة)
        item {
            SettingsSectionCard(title = "التنقل والقراءة") {


                SettingsSwitchRow(
                    label = "إبقاء الشاشة مضاءة أثناء القراءة",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(keepScreenOn = v) } }
                )
                SettingsSwitchRow(
                    label = "التنقل بأزرار الصوت",
                    checked = settings.volumeKeyPaging,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(volumeKeyPaging = v) } }
                )
                SettingsSwitchRow(
                    label = "تفعيل مناطق اللمس الجانبية للتقليب السريع",
                    checked = settings.touchZonesMode == "SIDES_FLIP",
                    onCheckedChange = { v ->
                        viewModel.updateSettings { it.copy(touchZonesMode = if (v) "SIDES_FLIP" else "STANDARD") }
                    }
                )
                SettingsSwitchRow(
                    label = "فتح الفصل التالي تلقائيًا عند النهاية",
                    checked = settings.autoOpenNextChapter,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoOpenNextChapter = v) } }
                )
                SettingsSwitchRow(
                    label = "تظليل مصطلحات القاموس داخل النص تلقائيًا",
                    checked = settings.highlightGlossaryInText,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(highlightGlossaryInText = v) } }
                )
            }
        }


    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("إعادة الضبط للافتراضي") },
            text = { Text("هل تريد استعادة جميع إعدادات الخطوط والألوان والعرض للوضع الافتراضي؟ (لن تتأثر فصولك أو رواياتك)") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetSettingsToDefault()
                        showResetConfirm = false
                    }
                ) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showPinDialog) {
        var newPin by remember { mutableStateOf(settings.appLockPin) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("قفل التطبيق برمز سري (PIN)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اترك الحقل فارغًا لإلغاء القفل، أو أدخل 4 أرقام لقفل التطبيق عند الفتح:")
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("رمز PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSettings { it.copy(appLockPin = newPin) }
                        showPinDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "طي القائمة" else "فتح القائمة",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider()
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        PreferenceSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 4) 3-Screen Onboarding Tour ("جولة تعريفية قصيرة 3 شاشات عند أول فتح")
 */
@Composable
fun OnboardingTourDialog(onFinish: () -> Unit) {
    AlertDialog(onDismissRequest = onFinish, title = { Text("قارئ الفصول") },
        text = { Text("أضف نصًا أو استورد ملفاتك من زر إضافة. يحفظ التطبيق موضع القراءة تلقائيًا. النسخ الاحتياطي متاح في الإعدادات.") },
        confirmButton = { TextButton(onClick = onFinish) { Text("فتح المكتبة") } })
}
