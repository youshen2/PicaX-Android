package moye.picax

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moye.picax.data.*
import moye.picax.model.Platform
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class BackupInteropTest {
    @Test fun picaComicHistoryFavoritesDownloadsAndExport() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PicaXApplication
        val directory = File(app.cacheDir, "interop-test").apply { mkdirs() }
        val history = File(directory, "history.db")
        val favorite = File(directory, "local_favorite.db")
        val downloads = File(directory, "download.db")
        try {
            SQLiteDatabase.openOrCreateDatabase(history, null).use { db ->
                db.execSQL("CREATE TABLE history(target TEXT,title TEXT,subtitle TEXT,cover TEXT,time INTEGER,type INTEGER,ep INTEGER,page INTEGER,max_page INTEGER)")
                db.execSQL("INSERT INTO history VALUES('jm42','样本','作者','',12345,2,2,3,20)")
            }
            SQLiteDatabase.openOrCreateDatabase(favorite, null).use { db ->
                db.execSQL("CREATE TABLE favorites(target TEXT,name TEXT,author TEXT,type INTEGER,tags TEXT,cover_path TEXT,time TEXT)")
                db.execSQL("INSERT INTO favorites VALUES('jm42','样本','作者',2,'[]','','2026-10-30')")
            }
            SQLiteDatabase.openOrCreateDatabase(downloads, null).use { db ->
                db.execSQL("CREATE TABLE download(id TEXT,title TEXT,subtitle TEXT,time INTEGER,directory TEXT,json TEXT)")
                db.execSQL("INSERT INTO download VALUES('jm42','样本','作者',12345,'sample','{}')")
            }
            val archive = File(directory, "sample.backup")
            ZipOutputStream(archive.outputStream()).use { zip ->
                fun add(name: String, content: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry() }
                add("appdata", "{\"searchHistory\":[\"测试\"],\"blockingKeywords\":[\"block\"]}".toByteArray())
                add("history.db", history.readBytes()); add("local_favorite.db", favorite.readBytes()); add("download/download.db", downloads.readBytes())
                zip.putNextEntry(ZipEntry("download/sample/1/1.png"))
                val bitmap = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip); bitmap.recycle(); zip.closeEntry()
            }
            val result = ZipFile(archive).use { PicaComicBackupImporter(app.store).read(it) }
            assertEquals(Platform.jmComic, result.history.single().comic.platform)
            assertEquals("42", result.history.single().comic.id)
            assertEquals(2, result.history.single().chapter.order)
            assertEquals(2, result.history.single().page)
            assertEquals("样本", result.favorites.single().comic.title)
            assertEquals("测试", result.searches.single().query)
            assertEquals(1, result.downloads.single().completed)
            val output = File(directory, "export.zip")
            DocumentTools(app.store).exportComics(result.downloads, Uri.fromFile(output))
            ZipFile(output).use { zip -> assertTrue(zip.entries().asSequence().single().name.endsWith(".png")) }
            result.downloads.forEach { app.store.downloadDir(it).deleteRecursively() }
        } finally { directory.deleteRecursively() }
    }
}
