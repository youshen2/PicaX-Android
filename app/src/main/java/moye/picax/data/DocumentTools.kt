package moye.picax.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import moye.picax.model.*
import java.io.*
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class DocumentTools(private val store: AppStore) {
    private val resolver = store.context.contentResolver
    suspend fun importComic(uri: Uri): Comic = withContext(Dispatchers.IO) {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "本地漫画"
        val comic = Comic(UUID.randomUUID().toString(), Platform.local, name.substringBeforeLast('.'), "本地导入")
        var record = DownloadRecord(ComicDetail(comic), Chapter("local", "全部页面"), emptyList())
        val directory = store.downloadDir(record).apply { mkdirs() }
        try {
            var count = 0
            if(name.endsWith(".pdf", ignoreCase = true) || resolver.getType(uri) == "application/pdf") {
                resolver.openFileDescriptor(uri, "r")!!.use { descriptor -> PdfRenderer(descriptor).use { pdf ->
                    for(i in 0 until pdf.pageCount) pdf.openPage(i).use { page ->
                        val width = store.number("import.width", 1440).coerceIn(720, 2880)
                        val height = (width.toDouble() * page.height / page.width).toInt()
                        require(width.toLong() * height <= 32_000_000L) { "PDF 页面尺寸过大" }
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        store.pageFile(record, count++).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                } }
            } else {
                val temp = File.createTempFile("import", ".zip", store.context.cacheDir)
                try {
                    resolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                    ZipFile(temp).use { zip ->
                        val entries = zip.entries().asSequence().filter { !it.isDirectory && !it.name.startsWith("__MACOSX") && it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif") }.toList().sortedWith { a, b -> naturalCompare(a.name, b.name) }
                        var total = 0L
                        entries.forEach { entry ->
                            zip.getInputStream(entry).use { input -> store.pageFile(record, count++).outputStream().use { output ->
                                val buffer = ByteArray(65536)
                                while(true) { val bytes = input.read(buffer); if(bytes < 0) break; total += bytes; require(total <= 2L * 1024 * 1024 * 1024) { "图片解压总大小超过 2 GB" }; output.write(buffer, 0, bytes) }
                            } }
                        }
                    }
                } finally { temp.delete() }
            }
            require(count > 0) { "文件中没有可读取的图片" }
            val complete = comic.copy(cover = Uri.fromFile(store.pageFile(record, 0)).toString(), pages = count)
            record = record.copy(detail = ComicDetail(complete, chapters = listOf(record.chapter.copy(pages = count))), urls = (0 until count).map { Uri.fromFile(store.pageFile(record, it)).toString() }, completed = count, status = "已完成")
            store.download(record)
            complete
        } catch(e: Exception) { directory.deleteRecursively(); throw e }
    }

    suspend fun exportComic(record: DownloadRecord, uri: Uri) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri)!!.use { output -> ZipOutputStream(output).use { zip ->
            record.urls.indices.forEach { index ->
                val file = store.pageFile(record, index)
                if(file.exists()) {
                    val type = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeFile(file.path, this) }.outMimeType
                    val ext = when(type) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
                    zip.putNextEntry(ZipEntry("%05d.$ext".format(index + 1))); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        } }
    }

    suspend fun exportComics(records: List<DownloadRecord>, uri: Uri) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri)!!.use { output -> ZipOutputStream(output).use { zip ->
            records.filter { it.status == "已完成" }.forEach { record ->
                val book = (store.setting("downloads.nameTemplate", "{title}").replace("{title}", record.detail.comic.title).replace("{id}", record.detail.comic.id).replace("{author}", record.detail.comic.subtitle).replace("{platform}", record.detail.comic.platform.title) + "-" + AppStore.safeName(record.detail.comic.key).take(8)).replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val chapter = (record.chapter.title + "-" + AppStore.safeName(record.chapter.id).take(8)).replace(Regex("[\\\\/:*?\"<>|]"), "_")
                record.urls.indices.forEach { index ->
                    val file = store.pageFile(record, index)
                    if(file.exists()) {
                        val type = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeFile(file.path, this) }.outMimeType
                        val ext = when(type) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; "image/avif" -> "avif"; else -> "jpg" }
                        zip.putNextEntry(ZipEntry("$book/${record.chapter.order}-$chapter/${"%05d".format(index + 1)}.$ext")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
        } }
    }

    suspend fun exportBackup(uri: Uri, includeDownloads: Boolean, includeAccounts: Boolean) = withContext(Dispatchers.IO) {
        val snapshot = store.data.value.let { it.copy(accounts = if(includeAccounts) it.accounts else emptyList(), downloads = if(includeDownloads) it.downloads else emptyList()) }
        resolver.openOutputStream(uri)!!.use { output -> ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("android-library.json")); zip.write(store.json.encodeToString(snapshot).toByteArray()); zip.closeEntry()
            if(includeAccounts) {
                zip.putNextEntry(ZipEntry("android-credentials.json"))
                zip.write(store.json.encodeToString(snapshot.accounts.associate { it.platform.name to store.vault.get(it.platform) }).toByteArray()); zip.closeEntry()
            }
            snapshot.downloads.forEach { record -> record.urls.indices.forEach { index ->
                val file = store.pageFile(record, index)
                if(file.exists()) { zip.putNextEntry(ZipEntry("downloads/${AppStore.safeName(record.key)}/$index.img")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
            } }
        } }
    }

    suspend fun importBackup(uri: Uri, merge: Boolean): String = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("restore", ".picax", store.context.cacheDir)
        val wrapped = File(temp.path + ".zip")
        try {
            resolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
            val zipped = temp.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
            if(!zipped) {
                val metadata = org.json.JSONObject(temp.readText())
                ZipOutputStream(wrapped.outputStream()).use { archive ->
                    archive.putNextEntry(ZipEntry("backup.json")); archive.write(metadata.toString().toByteArray()); archive.closeEntry()
                    val files = metadata.optJSONArray("downloadFiles")
                    for(index in 0 until (files?.length() ?: 0)) {
                        val entry = files!!.getJSONObject(index)
                        archive.putNextEntry(ZipEntry("downloads/" + entry.getString("relativePath")))
                        archive.write(android.util.Base64.decode(entry.getString("data"), android.util.Base64.DEFAULT)); archive.closeEntry()
                    }
                }
            }
            ZipFile(if(zipped) temp else wrapped).use { zip ->
                val manifest = zip.getEntry("android-library.json")
                val backup = if(manifest != null) zip.getInputStream(manifest).use { store.json.decodeFromString<AppData>(it.reader().readText()) }
                    else if(zip.getEntry("appdata") != null) PicaComicBackupImporter(store).read(zip)
                    else SwiftBackupImporter(store).read(zip)
                if(manifest != null) backup.downloads.forEach { record ->
                    store.downloadDir(record).mkdirs()
                    record.urls.indices.forEach { index ->
                        zip.getEntry("downloads/${AppStore.safeName(record.key)}/$index.img")?.let { entry ->
                            zip.getInputStream(entry).use { input -> store.pageFile(record, index).outputStream().use { input.copyTo(it) } }
                        }
                    }
                }
                zip.getEntry("android-credentials.json")?.let { entry ->
                    val credentials = zip.getInputStream(entry).use { store.json.decodeFromString<Map<String, Credential>>(it.reader().readText()) }
                    credentials.forEach { (platform, credential) -> store.vault.save(Platform.valueOf(platform), credential) }
                }
                val restored = backup.copy(preferences = backup.preferences + store.data.value.preferences.filterKeys { it in setOf("adult", "onboarding") }, downloads = backup.downloads.map { record ->
                    if(record.detail.comic.platform == Platform.local) record.copy(detail = record.detail.copy(comic = record.detail.comic.copy(cover = Uri.fromFile(store.pageFile(record, 0)).toString())), urls = record.urls.indices.map { Uri.fromFile(store.pageFile(record, it)).toString() }) else record
                })
                store.update { current -> if(!merge) restored else current.copy(
                    preferences = current.preferences + restored.preferences,
                    accounts = (restored.accounts + current.accounts).distinctBy { it.platform },
                    favorites = (restored.favorites + current.favorites).distinctBy { it.comic.key },
                    folders = (current.folders + restored.folders).distinct(),
                    later = (restored.later + current.later).distinctBy { it.key },
                    history = (restored.history + current.history).sortedByDescending { it.updatedAt }.distinctBy { it.comic.key },
                    bookmarks = (restored.bookmarks + current.bookmarks).distinctBy { it.id },
                    searches = (restored.searches + current.searches).distinctBy { it.query to it.savedName },
                    rules = (restored.rules + current.rules).distinct(),
                    readingTime = (restored.readingTime + current.readingTime).distinctBy { it.comic.key to it.day },
                    downloads = (restored.downloads + current.downloads).distinctBy { it.key },
                    versionOverrides = (restored.versionOverrides + current.versionOverrides).distinctBy { it.id },
                    followUpdates = (restored.followUpdates + current.followUpdates).distinctBy { it.comic.key },
                    laterAddedAt = current.laterAddedAt + restored.laterAddedAt
                ) }
                "已恢复 ${restored.favorites.size} 个收藏、${restored.history.size} 条历史和 ${restored.downloads.size} 个下载章节。"
            }
        } finally { temp.delete(); wrapped.delete() }
    }

    companion object {
        fun naturalCompare(a: String, b: String): Int {
            val pattern = Regex("\\d+|\\D+")
            val left = pattern.findAll(a).map { it.value }.toList()
            val right = pattern.findAll(b).map { it.value }.toList()
            for(i in 0 until minOf(left.size, right.size)) {
                val l = left[i].toLongOrNull(); val r = right[i].toLongOrNull()
                val cmp = if(l != null && r != null) l.compareTo(r) else left[i].compareTo(right[i], ignoreCase = true)
                if(cmp != 0) return cmp
            }
            return left.size.compareTo(right.size)
        }
    }
}
