package moye.picax.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import moye.picax.model.Platform
import moye.picax.ui.*
import moye.picax.ui.components.*
import io.github.neoruaa.glasmio.ui.GlasmioListRow

@Composable
fun SourceFeaturesPage(platform: Platform, showsAccount: Boolean = true) {
    val app = LocalApp.current
    val data = appData()
    val signedIn = data.accounts.any { it.platform == platform }
    Page(platform.title) {
        if (showsAccount) item { Group("账号") {
            GlasmioListRow(platform.title, subtitle = data.accounts.firstOrNull { it.platform == platform }?.username ?: "未登录", systemName = platform.symbol,
                onClick = { app.navigator.open(Route("account", platform = platform)) })
        } }
        item { Group("前端", "用于网页登录、分享链接，以及支持网页解析的来源请求。") {
            SourceField("前端地址", "frontend.${platform.name}", platform.website)
            GlasmioListRow("打开前端", systemName = "safari", onClick = {
                val url = moye.picax.data.source.SourceSettings.baseURL(app.store.setting("frontend.${platform.name}"), platform.website)
                app.store.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            GlasmioListRow("恢复默认前端", onClick = { app.store.set("frontend.${platform.name}", platform.website) })
        } }
        when (platform) {
            Platform.picacg -> { item { PicacgProfileSection(signedIn) }; item { PicacgSettings(signedIn) } }
            Platform.jmComic -> { item { JmSettings() }; item { CheckInSection(platform, signedIn) } }
            Platform.nhentai -> item { Group("NHentai") { ClearWebLoginRow(platform) } }
            Platform.eHentai -> item { EhSettings() }
            Platform.hitomi -> item { HitomiSettings() }
            Platform.htManga -> item { HtSettings() }
            Platform.local -> Unit
        }
    }
}
