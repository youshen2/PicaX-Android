package moye.picax.data

import android.webkit.CookieManager
import moye.picax.model.Platform
import moye.picax.data.source.SourceSettings
import java.net.URI

object WebLoginState {
    fun urls(store: AppStore, platform: Platform): List<String> = buildList {
        add(platform.website)
        add(SourceSettings.baseURL(store.setting("frontend.${platform.name}"), platform.website))
        add(SourceSettings.baseURL(store.setting("source.${platform.name}.url"), platform.website))
        if (platform == Platform.eHentai) addAll(listOf("https://e-hentai.org", "https://exhentai.org", "https://forums.e-hentai.org"))
    }.distinct()

    fun clear(store: AppStore, platform: Platform) {
        val manager = CookieManager.getInstance()
        urls(store, platform).forEach { url ->
            val host = URI(url).host ?: return@forEach
            manager.getCookie(url).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter(String::isNotEmpty).forEach { name ->
                manager.setCookie(url, "$name=; Path=/; Max-Age=0")
                manager.setCookie(url, "$name=; Domain=.$host; Path=/; Max-Age=0")
            }
        }
        manager.flush()
        val credential = store.vault.get(platform)
        store.vault.save(platform, credential.copy(cookie = "", token = if (platform == Platform.nhentai) "" else credential.token))
    }
}
