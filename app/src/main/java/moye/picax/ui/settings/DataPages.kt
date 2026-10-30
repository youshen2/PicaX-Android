package moye.picax.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.data.DocumentTools
import moye.picax.data.WebDavBackup
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*
import java.time.LocalDate

@Composable fun BlockingPage() {
    val app = LocalApp.current
    val data = appData()
    var add by remember { mutableStateOf(false) }
    Page("关键词屏蔽", actions = { IconAction("plus", "添加屏蔽词") { add = true } }) {
        if(data.rules.isEmpty()) item { EmptyPage("暂无屏蔽词", "按标题、作者和标签过滤漫画列表。", "eye.slash") }
        item { Group("屏蔽规则") { data.rules.forEach { rule ->
            ActionMenu(listOf(
                MenuAction(if(rule.platform == null) "仅用于 JMComic" else "用于全部来源", "network", onClick = {
                    app.store.update { it.copy(rules = it.rules.map { old -> if(old == rule) old.copy(platform = if(rule.platform == null) Platform.jmComic else null) else old }) }
                }),
                MenuAction("删除", "trash", destructive = true, onClick = { app.store.update { it.copy(rules = it.rules - rule) } }),
            ), morphFromAnchor = false) { open ->
                GlasmioListRow(rule.text, subtitle = rule.platform?.title ?: "全部来源", onClick = open, trailing = {
                    GlasmioSwitch(rule.enabled, { enabled -> app.store.update { it.copy(rules = it.rules.map { old -> if(old == rule) old.copy(enabled = enabled) else old }) } })
                })
            }
        } } }
    }
    if(add) TextPrompt("添加屏蔽词", "标题、作者或标签", dismiss = { add = false }) { value -> app.store.update { it.copy(rules = (it.rules + BlockRule(value)).distinctBy { r -> r.text to r.platform }) } }

}

@Composable fun StoragePage() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableLongStateOf(0) }
    var downloadBytes by remember { mutableLongStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(revision) { withContext(Dispatchers.IO) {
        cacheBytes = app.store.context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        downloadBytes = java.io.File(app.store.context.filesDir, "downloads").walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } }
    Page("存储管理") {
        item { Group("存储空间") {
            GlasmioListRow("图片缓存", detail = bytesLabel(cacheBytes), systemName = "photo")
            GlasmioListRow("下载漫画", detail = bytesLabel(downloadBytes), systemName = "arrow.down.circle")
        } }
        item { Group("管理") {
            ConfirmAction("清理图片缓存", "清理本机图片缓存？") { scope.launch { withContext(Dispatchers.IO) { java.io.File(app.store.context.cacheDir, "pages").deleteRecursively() }; revision++ } }
            NumberSetting("图片缓存上限（MB）", "cache.megabytes", 512, 64..8192)
            GlasmioListRow("管理下载", onClick = { app.navigator.open(Route("downloads")) })
        } }
    }
}

@Composable fun BackupPage() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val documents = remember { DocumentTools(app.store) }
    var includeDownloads by remember { mutableStateOf(false) }
    var includeAccounts by remember { mutableStateOf(false) }
    var merge by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var passwordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri != null) scope.launch {
            busy = true
            try { documents.exportBackup(uri, includeDownloads, includeAccounts); message = "备份已导出" }
            catch(e: Exception) { message = e.message } finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            busy = true
            try { message = documents.importBackup(uri, merge) }
            catch(e: Exception) { message = e.message } finally { busy = false }
        }
    }
    Page("备份与恢复") {
        item { Group("导出内容", if(includeAccounts) "备份文件包含账号凭证，请妥善保管。" else "保存设置、收藏、历史、搜索、书签和阅读时长。") {
            GlasmioListRow("包含已下载漫画", trailing = { GlasmioSwitch(includeDownloads, { includeDownloads = it }) })
            GlasmioListRow("包含平台账号", trailing = { GlasmioSwitch(includeAccounts, { includeAccounts = it }) })
            GlasmioListRow("导出 .picax 备份", systemName = "square.and.arrow.up", enabled = !busy, onClick = { export.launch("PicaX-${LocalDate.now()}.picax") })
        } }
        item { Group("恢复") {
            GlasmioListRow("合并现有数据", trailing = { GlasmioSwitch(merge, { merge = it }) })
            GlasmioListRow("选择备份文件", systemName = "square.and.arrow.down", enabled = !busy, onClick = { import.launch(arrayOf("*/*")) })
        } }
        item { Group("WebDAV", "备份保存为配置目录中的 PicaX.picax 文件。") {
            TextSetting("目录地址", "webdav.url")
            TextSetting("用户名", "webdav.username")
            SwitchSetting("每日自动备份", "webdav.auto", false)
            GlasmioListRow("密码", onClick = { passwordDialog = true }, detail = "加密保存在本机")
            GlasmioListRow("上传备份", enabled = !busy, onClick = { scope.launch {
                busy = true
                try { WebDavBackup(app.repository).upload(includeDownloads, includeAccounts); message = "备份已上传" }
                catch(e: Exception) { message = e.message } finally { busy = false }
            } })
            GlasmioListRow("从 WebDAV 恢复", enabled = !busy, onClick = { scope.launch {
                busy = true
                try { message = WebDavBackup(app.repository).restore(merge) }
                catch(e: Exception) { message = e.message } finally { busy = false }
            } })
        } }
        if(busy) item { Busy() }
    }
    if(message != null) GlasmioAlertDialog({ message = null }, "备份与恢复", message, buttons = listOf(GlasmioAlertButtonSpec("好", { message = null })))
    if(passwordDialog) GlasmioAlertDialog({ passwordDialog = false }, "WebDAV 密码", buttons = listOf(GlasmioAlertButtonSpec("取消", { passwordDialog = false }), GlasmioAlertButtonSpec("保存", {
        app.store.vault.save("webdav", Credential(password = password)); passwordDialog = false
    }))) { GlasmioTextField(password, { password = it }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()) }
}

@Composable fun StatisticsPage(comic: Comic? = null) {
    val app = LocalApp.current
    val data = appData()
    val reading = data.readingTime.filter { record -> comic == null || record.comic.key == comic.key }
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()).toString() }
    val totals = days.map { day -> reading.filter { it.day == day }.sumOf { it.seconds } }
    val max = totals.maxOrNull()?.coerceAtLeast(60L) ?: 60L
    val accent = GlasmioTheme.colors.accent
    Page(comic?.title ?: "阅读时长") {
        item { Group("概览") {
            GlasmioListRow("今日阅读", detail = durationLabel(totals.last()))
            GlasmioListRow("累计阅读", detail = durationLabel(reading.sumOf { it.seconds }))
        } }
        item { Group("最近七天") {
            Canvas(Modifier.fillMaxWidth().height(160.dp).padding(20.dp)) {
                val step = size.width / 7
                totals.forEachIndexed { index, total ->
                    val height = size.height * total / max
                    drawRoundRect(accent, Offset(index * step + step * .2f, size.height - height), Size(step * .6f, height.coerceAtLeast(2f)), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { days.forEach { Text(it.takeLast(5), fontSize = 11.sp) } }
        } }
        item { Group("阅读排行") {
            reading.groupBy { it.comic.key }.values.sortedByDescending { it.sumOf { row -> row.seconds } }.forEach { records ->
                val comic = records.first().comic
                GlasmioListRow(comic.title, detail = durationLabel(records.sumOf { it.seconds }), onClick = { app.navigator.detail(comic) })
            }
            if(reading.isEmpty()) EmptyRow("暂无阅读时长", "阅读漫画后会在这里显示统计。", "timer")
        } }
    }
}
private fun bytesLabel(bytes: Long) = if(bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes.toDouble() / 1024 / 1024)
private fun durationLabel(seconds: Long) = if(seconds < 3600) "${seconds / 60} 分钟" else "${seconds / 3600} 小时 ${seconds % 3600 / 60} 分钟"
