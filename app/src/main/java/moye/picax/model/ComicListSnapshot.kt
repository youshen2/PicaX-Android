package moye.picax.model

data class HiddenComic(val comic: Comic, val reason: String)
data class ComicListSnapshot(val visible: List<Comic> = emptyList(), val hidden: List<HiddenComic> = emptyList(), val crossRead: Set<String> = emptySet())

fun evaluateComics(comics: List<Comic>, data: AppData, filters: Boolean = true): ComicListSnapshot {
    val exact = data.history.associateBy { it.comic.key }
    val read = data.history.filter { it.pageCount > 0 }
    val later = data.later.associateBy { it.key }
    val threshold = data.preferences["list.matchThreshold"]?.toIntOrNull() ?: 95
    val match = data.preferences["list.matchTitles"] != "false"
    val hiddenThreshold = data.preferences["list.readThreshold"]?.toIntOrNull() ?: 100
    val cross = mutableSetOf<String>(); val visible = mutableListOf<Comic>(); val hidden = mutableListOf<HiddenComic>()
    for(comic in comics) {
        val same = exact[comic.key]
        val matching = if(match && same?.pageCount.orZero() == 0) read.firstOrNull { it.comic.platform != comic.platform && TitleMatcher.matches(comic, it.comic, threshold) } else null
        if(matching != null) cross += comic.key
        val rule = data.rules.firstOrNull { it.enabled && (it.platform == null || it.platform == comic.platform) &&
            (comic.title + " " + comic.subtitle + " " + comic.tags.joinToString(" ")).contains(it.text, true) }
        val progress = same ?: matching
        val readHidden = data.preferences["list.hideRead"] == "true" && progress != null && progress.pageCount > 0 &&
            ((progress.chapter.order - 1) + (progress.page + 1).toDouble() / progress.pageCount) * 100 / progress.chapterCount.coerceAtLeast(1) >= hiddenThreshold
        val laterHidden = data.preferences["list.hideLater"] == "true" && (comic.key in later || match && data.later.any { TitleMatcher.matches(comic, it, threshold) })
        val reason = when {
            rule != null -> "关键词：${rule.text}"
            filters && readHidden -> "阅读进度达到 $hiddenThreshold%"
            filters && laterHidden -> "已加入稍后再读"
            else -> null
        }
        if(reason == null) visible += comic else hidden += HiddenComic(comic, reason)
    }
    return ComicListSnapshot(visible, hidden, cross)
}
private fun Int?.orZero() = this ?: 0

fun versionGroups(comics: List<Comic>, overrides: List<VersionOverride>): List<List<Comic>> {
    val custom = overrides.associateBy { it.id }
    val unique = comics.distinctBy { it.key }
    val parents = IntArray(unique.size) { it }
    fun root(index: Int): Int { var current = index; while(parents[current] != current) { parents[current] = parents[parents[current]]; current = parents[current] }; return current }
    val keys = mutableMapOf<String, Int>()
    unique.forEachIndexed { index, comic ->
        val rule = custom[comic.key]
        val aliases = when {
            rule?.separates == true -> listOf("item:${comic.key}")
            !rule?.group.isNullOrBlank() -> listOf("manual:${rule!!.group}")
            else -> TitleMatcher.candidates(comic.title).map { "title:$it" }.ifEmpty { listOf("item:${comic.key}") }
        }
        aliases.forEach { alias -> keys.putIfAbsent(alias, index)?.let { previous -> parents[root(index)] = root(previous) } }
    }
    return unique.indices.groupBy(::root).values.map { group -> group.map(unique::get) }
}
