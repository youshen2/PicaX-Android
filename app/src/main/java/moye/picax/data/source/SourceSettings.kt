package moye.picax.data.source

import moye.picax.data.AppStore
import moye.picax.data.AppProject
import moye.picax.model.Platform
import java.net.URI

object SourceSettings {
    val jmAPIs = listOf("https://www.cdnhjk.net", "https://www.cdngwc.cc", "https://www.cdngwc.net", "https://www.cdngwc.club", "https://www.cdnutc.me")
    val jmImages = listOf("https://cdn-msp3.jmapiproxy1.cc", "https://cdn-msp.jmapiproxy3.cc", "https://cdn-msp2.jmapiproxy2.cc", "https://cdn-msp3.jmapiproxy3.cc")
    const val hitomiDomain = "gold-usergeneratedcontent.net"

    fun baseURL(value: String, fallback: String): String {
        val trimmed = value.trim().trimEnd('/')
        if (trimmed.isEmpty()) return fallback
        return if ("://" in trimmed) trimmed else "https://$trimmed"
    }
    fun domain(value: String): String = runCatching { URI(baseURL(value, "https://$hitomiDomain")).host }.getOrNull() ?: hitomiDomain
    fun jmCandidates(store: AppStore): List<String> = store.setting("jm.apis").lines().filter { it.isNotBlank() }
        .map { baseURL(it, "") }.distinct().ifEmpty { jmAPIs }
    fun jmRequestBases(store: AppStore): List<String> {
        val configured = jmCandidates(store)
        val candidates = (configured + jmAPIs).distinct()
        return if (store.bool("jm.autoSelect", true)) candidates
        else (listOf(configured.getOrElse(store.number("jm.apiIndex", 0)) { jmAPIs.getOrElse(store.number("jm.apiIndex", 0)) { jmAPIs.first() } }) + candidates).distinct()
    }
    fun jmImageBase(store: AppStore): String {
        val index = store.number("jm.imageIndex", 1)
        return if (index in jmImages.indices) jmImages[index] else baseURL(store.setting("jm.images"), jmImages[1])
    }
    fun jmFavoriteSort(value: String) = if (value == "updated" || value == "mp") "mp" else "mr"

    fun migrate(preferences: Map<String, String>): Map<String, String> = preferences.toMutableMap().apply {
        Platform.online.filter { it != Platform.picacg }.forEach { platform ->
            remove("source.${platform.name}.url")?.let { putIfAbsent("frontend.${platform.name}", it) }
        }
        if (!containsKey("jm.imageIndex")) get("jm.images")?.let { custom ->
            put("jm.imageIndex", jmImages.indexOf(baseURL(custom, jmImages[1])).takeIf { it >= 0 }?.toString() ?: "4")
        }
        when (get("jm.favoriteSort")) { "mr" -> put("jm.favoriteSort", "latest"); "mp" -> put("jm.favoriteSort", "updated") }
        if (get("updates.repository") == "youshen2/PicaX") put("updates.repository", AppProject.repository)
    }
}
