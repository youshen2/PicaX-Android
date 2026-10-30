package moye.picax.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moye.picax.model.Category
import moye.picax.model.Platform
import org.json.JSONObject
import java.io.File

class TagCatalog(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "tag-translations.json"))
    @Volatile private var translations = if(file.baseFile.exists()) runCatching { JSONObject(file.openRead().bufferedReader().use { it.readText() }) }.getOrElse { bundled() } else bundled()
    private val nhentai = context.assets.open("NhentaiTagSuggestions.json").bufferedReader().use { JSONObject(it.readText()) }
    private val nhGroups = listOf("language" to "语言", "tag" to "标签", "character" to "角色", "parody" to "原作")
    private val suggestions = nhGroups.flatMap { (key, title) ->
        val tags = nhentai.optJSONArray(key)
        (0 until (tags?.length() ?: 0)).map { index -> val tag = tags!!.getString(index)
            Category(tag, if(key == "language") "language:$tag" else if(' ' in tag) "\"$tag\"" else tag, group = title)
        }
    }
    private fun bundled() = context.assets.open("EhTagTranslations.json").bufferedReader().use { JSONObject(it.readText()) }
    fun groupTitle(namespace: String) = translations.optJSONObject("rows")?.optString(namespace, namespace) ?: namespace
    fun translate(query: String): String {
        val database = translations
        val namespace = query.substringBefore(':', "")
        val tag = query.substringAfter(':', query).trim('"')
        if(namespace.isNotEmpty()) database.optJSONObject(namespace)?.optString(tag)?.takeIf { it.isNotEmpty() }?.let { return it }
        for(key in listOf("language", "female", "male", "mixed", "other", "character", "parody", "artist", "group", "reclass")) {
            database.optJSONObject(key)?.optString(tag)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return tag
    }
    fun searchKeyword(keyword: String): String {
        val database = translations
        return Regex("\"[^\"]+\"|\\S+").replace(keyword) { match ->
            val raw = match.value.trim('"')
            if(raw.none { it.code in 0x3400..0x9fff }) match.value else {
                val namespace = raw.substringBefore(':', "")
                val title = raw.substringAfter(':', raw)
                val groups = if(namespace.isNotBlank()) listOf(namespace) else listOf("language", "female", "male", "mixed", "other", "character", "parody", "artist", "group", "reclass")
                groups.firstNotNullOfOrNull { group -> database.optJSONObject(group)?.let { values ->
                    values.keys().asSequence().firstOrNull { values.optString(it) == title }?.let { tag -> "$group:\"$tag\"" }
                } } ?: match.value
            }
        }
    }
    fun suggestions(query: String, limit: Int = 8): List<Category> {
        val fragment = query.substringAfterLast(' ').substringAfterLast('&').substringAfterLast('/').trim('"').lowercase()
        if(fragment.isBlank()) return emptyList()
        return suggestions.asSequence().map { it.copy(title = translate(it.query)) }.filter { it.query.contains(fragment, true) || it.title.contains(fragment, true) }.take(limit).toList()
    }
    fun nhentaiCategories() = nhGroups.flatMap { (_, title) -> suggestions.filter { it.group == title }.take(50).map { it.copy(title = translate(it.query)) } }
    fun ehentaiCategories(): List<Category> {
        val database = translations
        val rows = database.getJSONObject("rows")
        return rows.keys().asSequence().filter { it != "temp" }.flatMap { namespace ->
            val values = database.optJSONObject(namespace) ?: return@flatMap emptySequence()
            values.keys().asSequence().take(20).map { tag -> Category(values.getString(tag), "$namespace:\"$tag\"", group = rows.optString(namespace, namespace)) }
        }.toList()
    }
    suspend fun update(http: moye.picax.data.source.SourceHttp): String = withContext(Dispatchers.IO) {
        val root = "https://raw.githubusercontent.com/EhTagTranslation/Database/master"
        val limit = Semaphore(4)
        val namespaces = listOf("rows", "artist", "character", "cosplayer", "female", "group", "language", "location", "male", "mixed", "other", "parody", "reclass")
        val database = JSONObject()
        coroutineScope { namespaces.map { namespace -> async { limit.withPermit {
            namespace to parseTable(http.text("$root/database/$namespace.md", Platform.local))
        } } }.awaitAll().forEach { (key, value) -> database.put(key, value) } }
        require(database.getJSONObject("female").length() >= 100 && database.getJSONObject("male").length() >= 100) { "下载的标签数据库不完整" }
        val version = http.text("$root/version", Platform.local).trim()
        val output = file.startWrite()
        try { output.write(database.toString().toByteArray()); file.finishWrite(output) }
        catch(e: Exception) { file.failWrite(output); throw e }
        translations = database
        version
    }
    suspend fun restore() = withContext(Dispatchers.IO) { file.delete(); translations = bundled() }
    internal fun parseTable(text: String): JSONObject = JSONObject().apply {
        text.lineSequence().map { it.trim() }.filter { it.startsWith('|') }.forEach { line ->
            val cells = line.trim('|').split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }
            if(cells.size >= 2 && cells[0].isNotEmpty() && cells[0] != "原始标签" && cells[0].any { it != '-' && it != ':' }) put(cells[0].lowercase(), cells[1])
        }
    }
}
