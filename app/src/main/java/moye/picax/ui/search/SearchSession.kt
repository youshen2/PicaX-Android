package moye.picax.ui.search

import androidx.compose.runtime.*
import moye.picax.data.AppStore
import moye.picax.model.*

class SearchSession(store: AppStore, route: moye.picax.ui.Route? = null) {
    val query = mutableStateOf(route?.parameter.orEmpty())
    val platforms = mutableStateOf(if(!route?.parameter.isNullOrEmpty()) listOf(route!!.platform) else
        if(store.setting("search.target", "single") == "aggregate") store.setting("search.aggregate", Platform.online.joinToString(",") { it.name }).split(',').mapNotNull { name -> Platform.online.find { it.name == name } }
        else listOf(Platform.valueOf(store.setting("search.platform", "picacg"))))
    val sort = mutableStateOf("date")
    val language = mutableStateOf("")
    val results = mutableStateOf(emptyList<Comic>())
    val searched = mutableStateOf(false)
    val page = mutableIntStateOf(1)
    val hasMore = mutableStateOf(false)
    val activeQuery = mutableStateOf("")
    val activePlatforms = mutableStateOf(platforms.value)
}
