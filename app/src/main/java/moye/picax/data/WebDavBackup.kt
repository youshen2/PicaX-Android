package moye.picax.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class WebDavBackup(private val repository: ComicRepository) {
    private val store get() = repository.store
    private fun request(): Request.Builder {
        val url = store.setting("webdav.url").trimEnd('/')
        require(url.startsWith("https://") || url.startsWith("http://")) { "请填写完整的 WebDAV 目录地址" }
        val username = store.setting("webdav.username")
        return Request.Builder().url("$url/PicaX.picax").apply {
            if(username.isNotEmpty()) header("Authorization", Credentials.basic(username, store.vault.get("webdav").password))
        }
    }
    suspend fun upload(includeDownloads: Boolean, includeAccounts: Boolean) = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("webdav", ".picax", store.context.cacheDir)
        try {
            DocumentTools(store).exportBackup(Uri.fromFile(temporary), includeDownloads, includeAccounts)
            repository.http.execute(request().put(temporary.asRequestBody("application/octet-stream".toMediaType())).build()).close()
        } finally { temporary.delete() }
    }
    suspend fun restore(merge: Boolean): String = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("webdav", ".picax", store.context.cacheDir)
        try {
            repository.http.execute(request().get().build()).use { response -> response.body!!.byteStream().use { input -> temporary.outputStream().use { input.copyTo(it) } } }
            DocumentTools(store).importBackup(Uri.fromFile(temporary), merge)
        } finally { temporary.delete() }
    }
}
