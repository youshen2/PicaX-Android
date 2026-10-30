package moye.picax.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.data.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*
import moye.picax.ui.settings.NumberSetting

@Composable
fun DownloadsPage() {
    val app = LocalApp.current
    val data = appData()
    val scope = rememberCoroutineScope()
    val documents = remember { DocumentTools(app.store) }
    var exporting by remember { mutableStateOf(emptyList<DownloadRecord>()) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var deleting by remember { mutableStateOf(emptySet<String>()) }
    var inspecting by remember { mutableStateOf(emptyList<DownloadRecord>()) }
    var selectedComic by remember { mutableStateOf<Comic?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var importSheet by remember { mutableStateOf(false) }
    var filtersSheet by remember { mutableStateOf(false) }
    var queueSheet by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("全部") }
    var platform by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("newest") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            importSheet = false; busy = true
            try { documents.importComic(uri); status = "已导入本地书库" }
            catch(e: CancellationException) { throw e } catch(e: Exception) { status = e.message }
            finally { busy = false }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val records = exporting
        if(uri != null && records.isNotEmpty()) scope.launch {
            try { documents.exportComics(records, uri); status = "导出完成" }
            catch(e: CancellationException) { throw e } catch(e: Exception) { status = e.message }
        }
    }
    val books = data.downloads.groupBy { it.detail.comic.key }.values.toList()
    val filtered = books.filter { chapters -> (filter == "全部" || chapters.any { it.status == filter }) && (platform == "all" || chapters.first().detail.comic.platform.name == platform) }
    val records = when(sort) { "title" -> filtered.sortedBy { it.first().detail.comic.title }; "oldest" -> filtered.sortedBy { it.maxOf { row -> row.createdAt } }; else -> filtered.sortedByDescending { it.maxOf { row -> row.createdAt } } }
    val queue = data.downloads.filter { it.status != "已完成" }
    fun statusFor(keys: Set<String>, status: String) {
        app.store.update { it.copy(downloads = it.downloads.map { row -> if(row.detail.comic.key in keys && row.status != "已完成") row.copy(status = status) else row }) }
        if(status == "等待中") DownloadService.resume(app.store.context)
    }
    Page("下载", actions = {
        IconAction(if(selecting) "checkmark" else "checkmark.circle", if(selecting) "完成选择" else "选择") { selecting = !selecting; if(!selecting) selected = emptySet() }
        IconAction("line.3.horizontal.decrease.circle", "筛选与排序") { filtersSheet = true }
        ActionMenu(listOf(
            MenuAction("导入本地漫画", "plus", onClick = { importSheet = true }),
            MenuAction("下载队列（${queue.size}）", "arrow.down.circle", onClick = { queueSheet = true }),
            MenuAction("全选", "checkmark.circle", enabled = selecting, onClick = { selected = records.map { it.first().detail.comic.key }.toSet() }),
            MenuAction("暂停所选", "pause", enabled = selected.isNotEmpty(), onClick = { statusFor(selected, "已暂停") }),
            MenuAction("继续所选", "play", enabled = selected.isNotEmpty(), onClick = { statusFor(selected, "等待中") }),
            MenuAction("导出所选", "square.and.arrow.up", enabled = selected.isNotEmpty(), onClick = { exporting = data.downloads.filter { it.detail.comic.key in selected }; exporter.launch("PicaX-books.zip") }),
            MenuAction("删除所选", "trash", enabled = selected.isNotEmpty(), destructive = true, onClick = { deleting = selected }),
        )) { open -> IconAction("ellipsis", "下载工具", open) }
    }) {
        if(busy) item { Busy() }
        if(records.isEmpty() && !busy) item { EmptyPage("暂无下载", "从漫画详情页下载，或导入 ZIP、CBZ 和 PDF。", "arrow.down.circle") }
        items(records, key = { it.first().detail.comic.key }) { chapters ->
            val record = chapters.first()
            val key = record.detail.comic.key
            val actions = buildList {
                if(record.status == "已完成") {
                    add(MenuAction("阅读", "book", onClick = { app.navigator.reader(record.detail.comic, record.chapter, offline = true) }))
                    add(MenuAction("导出 ZIP", "square.and.arrow.up", onClick = { exporting = chapters; exporter.launch("${record.detail.comic.title}.zip") }))
                    add(MenuAction("检查完整性", "checkmark.shield", onClick = { inspecting = chapters }))
                } else if(record.status in listOf("下载中", "等待中")) {
                    add(MenuAction("暂停", "pause", onClick = { statusFor(setOf(key), "已暂停") }))
                } else add(MenuAction("继续下载", "play", onClick = { statusFor(setOf(key), "等待中") }))
                add(MenuAction("删除", "trash", destructive = true, onClick = { deleting = setOf(key) }))
            }
            ActionMenu(actions, morphFromAnchor = false) { open ->
                GlasmioGroupedList { ComicRow(record.detail.comic,
                    { if(selecting) selected = if(key in selected) selected - key else selected + key else selectedComic = record.detail.comic }, open,
                    "${if(key in selected) "✓ " else ""}${chapters.size} 章 · ${chapters.sumOf { it.completed }}/${chapters.sumOf { it.urls.size }} 页 · ${if(chapters.all { it.status == "已完成" }) "已完成" else record.status}") }
            }
        }
    }
    AppSheet(importSheet, "导入本地漫画", { importSheet = false }) {
        item { Group("选择文件", "ZIP、CBZ 内的图片按文件名自然排序；PDF 会转换为阅读页面。") {
            GlasmioListRow("选择 ZIP、CBZ 或 PDF", systemName = "folder", onClick = { importer.launch(arrayOf("application/zip", "application/x-cbz", "application/vnd.comicbook+zip", "application/pdf", "application/octet-stream")) })
        } }
        item { Group("PDF") { NumberSetting("导入宽度", "import.width", 1440, 720..2880) } }
    }
    AppSheet(filtersSheet, "筛选与排序", { filtersSheet = false }) {
        item { Group("筛选") {
            ChoiceMenuRow("来源", platform, listOf("all" to "全部来源") + Platform.entries.map { it.name to it.title }, { platform = it })
            ChoiceMenuRow("状态", filter, listOf("全部", "下载中", "等待中", "已暂停", "已完成", "失败").map { it to it }, { filter = it })
        } }
        item { Group("排序") { ChoiceMenuRow("顺序", sort, listOf("newest" to "最近下载", "oldest" to "最早下载", "title" to "标题"), { sort = it }) } }
    }
    AppSheet(queueSheet, "下载队列", { queueSheet = false }) {
        if(queue.isEmpty()) item { EmptyPage("下载队列为空", "新的下载任务会显示在这里。", "arrow.down.circle") }
        items(queue, key = { it.key }) { record -> Group {
            GlasmioListRow(record.detail.comic.title, subtitle = "${record.chapter.title} · ${record.status}", detail = "${record.completed}/${record.urls.size}")
        } }
    }
    if(deleting.isNotEmpty()) GlasmioAlertDialog({ deleting = emptySet() }, "删除下载？", "将删除所选漫画的本地文件。", buttons = listOf(
        GlasmioAlertButtonSpec("取消", { deleting = emptySet() }, GlasmioAlertButtonRole.Cancel),
        GlasmioAlertButtonSpec("删除", { app.store.deleteDownloads(deleting); selected = selected - deleting; deleting = emptySet() }, GlasmioAlertButtonRole.Destructive)))
    DownloadIntegritySheet(inspecting, { inspecting = emptyList() })
    DownloadedComicInfoSheet(selectedComic, { selectedComic = null })
    if(status != null) GlasmioAlertDialog({ status = null }, "下载", status, buttons = listOf(GlasmioAlertButtonSpec("好", { status = null })))
}

@Composable
private fun DownloadIntegritySheet(records: List<DownloadRecord>, onDismiss: () -> Unit) {
    val app = LocalApp.current
    var missing by remember { mutableStateOf<List<Pair<DownloadRecord, Int>>?>(null) }
    LaunchedEffect(records) {
        missing = null
        if(records.isNotEmpty()) missing = withContext(Dispatchers.IO) {
            records.flatMap { record -> record.urls.indices.filter { index ->
                app.store.pageFile(record, index).let { !it.exists() || it.length() == 0L }
            }.map { record to it } }
        }
    }
    AppSheet(records.isNotEmpty(), "下载完整性", onDismiss, footer = {
        if(!missing.isNullOrEmpty() && records.firstOrNull()?.detail?.comic?.platform != Platform.local) GlasmioButton({
            val keys = missing.orEmpty().map { it.first.key }.toSet()
            app.store.update { state -> state.copy(downloads = state.downloads.map { if(it.key in keys) it.copy(status = "等待中") else it }) }
            DownloadService.resume(app.store.context); onDismiss()
        }, Modifier.fillMaxWidth()) { Text("修复缺失页面") }
    }) {
        if(missing == null) item { Busy() }
        else if(missing!!.isEmpty()) item { Group { EmptyRow("文件完整", "全部 ${records.sumOf { it.urls.size }} 页均已下载。", "checkmark.circle") } }
        else items(missing!!) { (record, index) -> Group { GlasmioListRow("${record.chapter.title} · 第 ${index + 1} 页", subtitle = "文件缺失或为空", systemName = "exclamationmark.triangle") } }
    }
}
