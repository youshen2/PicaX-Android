package moye.picax.data

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import moye.picax.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** PicaComic appdata, SQLite libraries and download trees, matching the Swift importer. */
class PicaComicBackupImporter(private val store: AppStore) {
    fun read(zip: ZipFile): AppData {
        val appdata = zip.getEntry("appdata") ?: error("备份缺少 appdata")
        val app = zip.getInputStream(appdata).use { JSONObject(it.reader().readText()) }
        fun rows(name: String, query: String): List<JSONObject> {
            val entry = zip.getEntry(name) ?: return emptyList()
            val file = File.createTempFile("pica-import", ".db", store.context.cacheDir)
            return try {
                zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery(query, null).use { cursor -> buildList {
                        while(cursor.moveToNext()) add(JSONObject().apply { cursor.columnNames.forEachIndexed { i, key -> if(!cursor.isNull(i)) put(key, cursor.getString(i)) } })
                    } }
                }
            } finally { file.delete() }
        }
        fun platform(type: Int) = listOf(Platform.picacg, Platform.eHentai, Platform.jmComic, Platform.hitomi, Platform.htManga, Platform.nhentai, Platform.nhentai).getOrNull(type)
        fun normalize(id: String, platform: Platform): String = when(platform) {
            Platform.jmComic -> id.removePrefix("jm")
            Platform.hitomi, Platform.htManga, Platform.nhentai -> Regex("\\d+").find(id)?.value ?: id
            else -> id
        }
        fun comic(row: JSONObject, title: String, subtitle: String, cover: String): Comic? {
            val platform = platform(row.optInt("type", -1)) ?: return null
            val id = normalize(row.optString("target"), platform)
            if(id.isBlank() || row.optString(title).isBlank()) return null
            val tags = runCatching { strings(JSONArray(row.optString("tags", "[]"))) }.getOrDefault(emptyList())
            return Comic(id, platform, row.optString(title), row.optString(subtitle), row.optString(cover), tags)
        }
        val history = rows("history.db", "SELECT * FROM history ORDER BY time DESC").mapNotNull { row ->
            comic(row, "title", "subtitle", "cover")?.let { book ->
                val chapter = row.optInt("ep", 1).coerceAtLeast(1)
                History(book, Chapter((chapter - 1).toString(), "第 $chapter 话", chapter), (row.optInt("page", 1) - 1).coerceAtLeast(0), row.optInt("max_page"), row.optLong("time", System.currentTimeMillis()))
            }
        }
        val favoriteDb = if(zip.getEntry("local_favorite.db") != null) "local_favorite.db" else "local_favorite_temp.db"
        val folders = rows(favoriteDb, "SELECT name FROM sqlite_master WHERE type='table'").map { it.getString("name") }.filter { it !in setOf("folder_sync", "folder_order", "sqlite_sequence") }
        val favorites = folders.flatMap { folder -> rows(favoriteDb, "SELECT * FROM \"${folder.replace("\"", "\"\"")}\"").mapNotNull { row ->
            comic(row, "name", "author", "cover_path")?.let { Favorite(it, folder) }
        } }.distinctBy { it.comic.key }
        val cookies = rows("cookies.db", "SELECT * FROM cookies")
        val accounts = Platform.online.mapNotNull { platform ->
            val matching = cookies.filter { row -> when(platform) {
                Platform.picacg -> row.optString("domain").contains("picacomic")
                Platform.jmComic -> row.optString("domain").contains("18comic") || row.optString("domain").contains("jmcomic")
                Platform.eHentai -> row.optString("domain").contains("e-hentai") || row.optString("domain").contains("exhentai")
                else -> row.optString("domain").contains(java.net.URI(platform.website).host.orEmpty())
            } }
            if(matching.isEmpty()) null else {
                store.vault.save(platform, Credential(cookie = matching.joinToString("; ") { "${it.optString("name")}=${it.optString("value")}" }))
                Account(platform, platform.title)
            }
        }
        val downloads = rows("download/download.db", "SELECT * FROM download ORDER BY time DESC").flatMap { row ->
            val raw = row.optString("id"); val platform = when {
                raw.startsWith("jm") -> Platform.jmComic; raw.startsWith("hitomi") -> Platform.hitomi
                raw.startsWith("nhentai") -> Platform.nhentai; raw.startsWith("Ht") -> Platform.htManga
                raw.all { it.isDigit() } -> Platform.eHentai; else -> Platform.picacg
            }
            val metadata = runCatching { JSONObject(row.optString("json", "{}")) }.getOrDefault(JSONObject())
            val book = Comic(normalize(raw, platform), platform, row.optString("title"), row.optString("subtitle"))
            val titles = strings(metadata.optJSONArray("chapters") ?: metadata.optJSONObject("comic")?.optJSONArray("epNames"))
            val prefix = "download/${row.optString("directory")}/"
            val images = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) && !it.name.substringAfter(prefix).startsWith("cover", true) && it.name.substringAfterLast('.').lowercase() in imageTypes }.toList()
            images.groupBy { entry -> entry.name.substringAfter(prefix).substringBefore('/').toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0 }.toSortedMap().map { (index, pages) ->
                val chapter = Chapter("${platform.name}-${book.id}-${index + 1}", titles.getOrNull(index) ?: "第 ${index + 1} 章", index + 1, pages.size)
                var record = DownloadRecord(ComicDetail(book, chapters = listOf(chapter)), chapter, emptyList(), completed = pages.size, status = "已完成")
                store.downloadDir(record).mkdirs()
                pages.sortedWith { a, b -> DocumentTools.naturalCompare(a.name, b.name) }.forEachIndexed { page, entry ->
                    zip.getInputStream(entry).use { input -> store.pageFile(record, page).outputStream().use { input.copyTo(it) } }
                }
                val cover = Uri.fromFile(store.pageFile(record, 0)).toString()
                record.copy(detail = record.detail.copy(comic = book.copy(cover = cover)), urls = pages.indices.map { Uri.fromFile(store.pageFile(record, it)).toString() })
            }
        }
        val settings = app.optJSONArray("settings") ?: JSONArray()
        val mapped = buildMap {
            fun value(index: Int, key: String) { if(!settings.isNull(index) && index < settings.length()) put(key, settings.opt(index).toString()) }
            value(0, "reader.tap"); value(28, "reader.preload"); value(35, "cache.megabytes"); value(79, "downloads.concurrency"); value(102, "downloads.comments"); value(72, "list.favorite"); value(73, "list.progress"); value(93, "list.hideRead")
            if(settings.length() > 32) put("appearance", when(settings.optString(32)) { "1" -> "light"; "2" -> "dark"; else -> "system" })
        }
        val searches = strings(app.optJSONArray("searchHistory") ?: app.optJSONArray("search")).distinct().map { SearchRecord(it, Platform.online) }
        val rules = strings(app.optJSONArray("blockingKeywords")).map { BlockRule(it) } + strings(app.optJSONArray("jmBlockingKeywords") ?: app.optJSONArray("jmBlockingKeyword")).map { BlockRule(it, platform = Platform.jmComic) }
        return AppData(preferences = mapped, accounts = accounts, favorites = favorites, folders = (listOf("默认收藏夹") + folders).distinct(), history = history, searches = searches, rules = rules, downloads = downloads)
    }
    private fun strings(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0)).map { array!!.getString(it) }
    private val imageTypes = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
}
