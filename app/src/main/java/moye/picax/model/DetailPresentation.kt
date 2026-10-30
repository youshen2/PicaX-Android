package moye.picax.model

val detailSectionTitles = linkedMapOf("comments" to "评论", "actions" to "操作", "chapters" to "章节", "description" to "简介", "uploader" to "上传者", "information" to "信息", "tags" to "标签", "related" to "相关推荐")
val homeSectionTitles = linkedMapOf("history" to "历史记录", "later" to "稍后再读", "updates" to "追更", "time" to "阅读时长", "downloads" to "下载", "sources" to "漫画源", "tools" to "工具")
fun normalizedOrder(raw: String?, defaults: Collection<String>): List<String> = (raw.orEmpty().split(',').filter { it in defaults } + defaults).distinct()
fun groupedTags(tags: List<String>): List<TagGroup> = tags.groupBy { it.substringBefore(':', "tag") }.map { (namespace, values) ->
    TagGroup(when(namespace) { "artist", "author" -> "作者"; "group" -> "社团"; "parody", "works" -> "原作"; "character", "actors" -> "角色"; "language" -> "语言"; "category", "reclass" -> "分类"; else -> "标签" }, values)
}
