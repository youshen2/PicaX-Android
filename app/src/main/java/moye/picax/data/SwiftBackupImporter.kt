package moye.picax.data

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Base64
import moye.picax.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.zip.ZipFile

/** Reads the Swift application's version 1 JSON and version 2 SQLite backup envelopes. */
class SwiftBackupImporter(private val store: AppStore) {
    fun read(zip: ZipFile): AppData {
        val manifest = zip.getEntry("backup.json") ?: error("备份缺少 backup.json")
        val metadata = zip.getInputStream(manifest).use { JSONObject(it.reader().readText()) }
        require(metadata.getInt("formatVersion") in 1..2) { "暂不支持这个备份版本" }
        val values = mutableMapOf<String, JSONObject>()
        metadata.optJSONObject("defaults")?.let { defaults -> defaults.keys().forEach { values[it] = defaults.getJSONObject(it) } }
        zip.getEntry("data.sqlite3")?.let { entry ->
            val file = File.createTempFile("swift-backup", ".sqlite3", store.context.cacheDir)
            try {
                zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT key, value FROM defaults", null).use { cursor ->
                        while(cursor.moveToNext()) values[cursor.getString(0)] = JSONObject(String(cursor.getBlob(1)))
                    }
                }
            } finally { file.delete() }
        }
        fun array(key: String): JSONArray {
            val item = values[key] ?: return JSONArray()
            if(item.optString("type") == "stringArray") return item.optJSONArray("stringArrayValue") ?: JSONArray()
            val encoded = item.optString("dataValue")
            return if(encoded.isEmpty()) JSONArray() else JSONArray(String(Base64.decode(encoded, Base64.DEFAULT)))
        }
        fun objects(key: String) = array(key).let { rows -> (0 until rows.length()).map { rows.getJSONObject(it) } }
        val favorites = values.keys.filter { it.startsWith("picax.localFavorites.") }.flatMap { key -> objects(key).map { Favorite(comic(it), addedAt = date(it.opt("favoriteDate"))) } }
        val history = objects("picax.readingHistory.records").map { row ->
            val progress = row.optJSONObject("progress")
            History(comic(row.getJSONObject("item")), Chapter((progress?.optInt("chapterIndex") ?: 0).toString(), "第 ${(progress?.optInt("chapterIndex") ?: 0) + 1} 话", (progress?.optInt("chapterIndex") ?: 0) + 1), progress?.optInt("pageIndex") ?: 0, progress?.optInt("totalPages") ?: 0, date(row.opt("viewedAt")))
        }
        val later = objects("picax.readLater.records").map { comic(it.getJSONObject("item")) }
        val readingTime = objects("picax.readingDuration.records").flatMap { row ->
            val days = row.optJSONObject("dailySeconds") ?: JSONObject()
            days.keys().asSequence().map { day -> ReadingTime(comic(row.getJSONObject("item")), day, days.optDouble(day).toLong()) }.toList()
        }
        val searches = objects("picax.searchHistory.records").map(::search) + objects("picax.savedSearches").map { row -> search(row.getJSONObject("search")).copy(savedName = row.optString("name"), pinned = row.optBoolean("isPinned")) }
        val rules = listOf("common", "jmComic").flatMap { scope ->
            val key = "picax.blockingKeywords.$scope"
            val disabled = array("$key.disabled").let { list -> (0 until list.length()).map { list.getString(it) }.toSet() }
            array(key).let { list -> (0 until list.length()).map { index ->
                val text = list.getString(index)
                BlockRule(text, text !in disabled, if(scope == "jmComic") Platform.jmComic else null)
            } }
        }
        val downloads = objects("picax.download.records").flatMap { row ->
            val item = comic(row.getJSONObject("item"))
            val directory = row.getString("directoryName")
            row.getJSONArray("chapters").let { chapters -> (0 until chapters.length()).map { index ->
                val chapterRow = chapters.getJSONObject(index)
                val chapterJson = chapterRow.getJSONObject("chapter")
                val chapter = Chapter(chapterJson.getString("id"), chapterJson.getString("title"), chapterRow.optInt("index") + 1, chapterRow.getInt("pageCount"))
                val prefix = "downloads/$directory/" + "%03d-".format(chapter.order) + safeName(chapter.title) + "/"
                val entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif") }.toList().sortedWith { a, b -> DocumentTools.naturalCompare(a.name, b.name) }
                var record = DownloadRecord(ComicDetail(item), chapter, emptyList())
                store.downloadDir(record).mkdirs()
                entries.forEachIndexed { page, entry -> zip.getInputStream(entry).use { input -> store.pageFile(record, page).outputStream().use { input.copyTo(it) } } }
                record = record.copy(urls = entries.indices.map { Uri.fromFile(store.pageFile(record, it)).toString() }, completed = entries.size,
                    status = if(entries.size == chapter.pages) "已完成" else "失败", error = if(entries.size == chapter.pages) "" else "备份中有缺失的页面")
                val cover = store.pageFile(record, 0).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() } ?: item.cover
                record.copy(detail = ComicDetail(item.copy(cover = cover), chapters = listOf(chapter)))
            } }
        }
        val knownComics = (favorites.map { it.comic } + history.map { it.comic } + later + downloads.map { it.detail.comic }).associateBy { "${it.platform.name}-${it.id}" }
        val bookmarks = objects("picax.pageBookmarks").mapNotNull { row ->
            val comic = knownComics[row.optString("comicID")] ?: return@mapNotNull null
            Bookmark("${row.optString("comicID")}:${row.optString("chapterID")}:${row.optInt("pageIndex")}", comic,
                Chapter(row.optString("chapterID"), row.optString("chapterTitle")), row.optInt("pageIndex"), row.optString("note"), row.optString("thumbnailURLString"), date(row.opt("createdAt")))
        }
        val accounts = objects("picax.platformAccounts").map { row ->
            val platform = Platform.valueOf(row.getString("platform"))
            val credential = row.optJSONObject("credential") ?: JSONObject()
            fun field(key: String) = credential.optString(key).takeUnless { it == "null" }.orEmpty()
            val cookies = credential.optJSONArray("cookies") ?: JSONArray()
            val cookie = (0 until cookies.length()).map { cookies.getJSONObject(it) }.joinToString("; ") { "${it.getString("name")}=${it.getString("value")}" }
            store.vault.save(platform, Credential(field("token"), cookie, field("password"), field("userAgent"), field("baseURL")))
            Account(platform, row.optString("username", platform.title), date(row.opt("loggedInAt")))
        }
        val settings = SwiftPreferenceMapping.read(values)
        return AppData(preferences = settings, accounts = accounts, favorites = favorites, later = later, history = history, bookmarks = bookmarks, searches = searches, rules = rules, readingTime = readingTime, downloads = downloads)
    }
    private fun comic(row: JSONObject) = Comic(row.getString("id"), Platform.valueOf(row.getString("platform")), row.getString("title"), row.optString("subtitle"), row.optString("coverURLString"),
        row.optJSONArray("tags")?.let { list -> (0 until list.length()).map { list.getString(it) } }.orEmpty(), row.optInt("pageCount"), row.optInt("likesCount"), row.optString("language").takeUnless { it == "null" }.orEmpty())
    private fun search(row: JSONObject): SearchRecord {
        val target = row.optJSONObject("target") ?: JSONObject()
        val single = target.optJSONObject("platform")?.optString("_0")
        val multiple = target.optJSONObject("aggregate")?.optJSONArray("_0")
        val platforms = if(single != null) listOf(Platform.valueOf(single)) else multiple?.let { list -> (0 until list.length()).map { Platform.valueOf(list.getString(it)) } } ?: Platform.online
        return SearchRecord(row.getString("keyword"), platforms, updatedAt = date(row.opt("searchedAt")))
    }
    private fun date(value: Any?): Long = when(value) {
        is Number -> ((value.toDouble() + 978307200) * 1000).toLong()
        is String -> runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
        else -> System.currentTimeMillis()
    }
    private fun safeName(value: String) = value.replace(Regex("[/\\\\?%*|\"<>:\\n\\r\\p{Cntrl}]"), "-").trim().take(80).ifEmpty { "untitled" }
}
