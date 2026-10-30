package moye.picax.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.picax.model.Platform
import org.json.JSONObject

data class AppRelease(val version: String, val notes: String, val url: String, val apk: String?)
object AppUpdates {
    suspend fun check(repository: ComicRepository): AppRelease = withContext(Dispatchers.IO) {
        val project = repository.store.setting("updates.repository", AppProject.repository).trim()
        require(project.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "请填写 owner/repository 格式的 GitHub 项目" }
        val json = JSONObject(repository.http.text("https://api.github.com/repos/$project/releases/latest", Platform.local))
        val assets = json.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk", true) }?.getString("browser_download_url")
        AppRelease(json.getString("tag_name"), json.optString("body"), json.getString("html_url"), apk)
    }
}
