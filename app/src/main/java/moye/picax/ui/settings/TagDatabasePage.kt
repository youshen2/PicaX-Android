package moye.picax.ui.settings

import androidx.compose.runtime.*
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable fun TagDatabasePage() {
    val app = LocalApp.current; val scope = rememberCoroutineScope()
    val preferences by app.store.preferences.collectAsState()
    var busy by remember { mutableStateOf(false) }; var status by remember { mutableStateOf<String?>(null) }
    Page("标签翻译库") {
        item { Group("EhTagTranslation") {
            GlasmioListRow("当前版本", detail = preferences["tags.version"] ?: "内置数据库")
            GlasmioListRow("检查并更新", enabled = !busy, onClick = { scope.launch {
                busy = true
                try { val version = app.repository.tags.update(app.repository.http); app.store.set("tags.version", version); app.repository.invalidateCategories(); status = "标签翻译库已更新" }
                catch(e: CancellationException) { throw e } catch(e: Exception) { status = e.message }
                finally { busy = false }
            } })
            GlasmioListRow("恢复内置数据库", enabled = !busy, onClick = { scope.launch { app.repository.tags.restore(); app.repository.invalidateCategories(); app.store.set("tags.version", "内置数据库"); status = "已恢复内置数据库" } })
        } }
        if(busy) item { Busy() }
    }
    if(status != null) GlasmioAlertDialog({ status = null }, "标签翻译库", status, buttons = listOf(GlasmioAlertButtonSpec("好", { status = null })))
}
