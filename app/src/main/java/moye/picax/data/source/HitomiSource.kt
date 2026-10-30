package moye.picax.data.source

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moye.picax.model.*
import org.json.JSONObject
import org.jsoup.Jsoup
import java.nio.ByteBuffer
import java.security.MessageDigest

class HitomiSource(private val http: SourceHttp) : SourceClient {
    override val platform = Platform.hitomi
    private val domain get() = http.store.setting("hitomi.domain", "gold-usergeneratedcontent.net")
    private fun url(path: String) = "https://ltn.$domain/" + path.split('/').joinToString("/") { it.encoded().replace("+", "%20") }
    private suspend fun bytes(path: String, range: String): ByteArray = http.response(url(path), http.headers(platform) + ("Range" to range)).use { it.body!!.bytes() }
    private fun ids(data: ByteArray) = ByteBuffer.wrap(data).let { buffer -> buildList { while (buffer.remaining() >= 4) add(buffer.int) } }
    private suspend fun indexIDs(query: String, limit: Int): List<Int> {
        val value = query.lowercase().replace('_', ' ').trim()
        if (value.isBlank()) return ids(bytes("index-all.nozomi", "bytes=0-${limit * 4 - 1}"))
        if (':' in value) {
            val namespace = value.substringBefore(':')
            val term = value.substringAfter(':')
            val path = when(namespace) {
                "language" -> "n/index-$term.nozomi"
                "female", "male" -> "n/tag/$value-all.nozomi"
                else -> "n/$namespace/$term-all.nozomi"
            }
            return ids(bytes(path, "bytes=0-${maxOf(limit, 2000) * 4 - 1}"))
        }
        val version = http.text(url("galleriesindex/version"), platform).trim()
        val key = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).copyOf(4)
        var address = 0L
        do {
            val node = ByteBuffer.wrap(bytes("galleriesindex/galleries.$version.index", "bytes=$address-${address + 463}"))
            val keys = List(node.int) { ByteArray(node.int).also(node::get) }
            val ranges = List(node.int) { node.long to node.int }
            val children = List(17) { node.long }
            var index = 0
            while (index < keys.size && compare(key, keys[index]) > 0) index++
            if (index < keys.size && compare(key, keys[index]) == 0) {
                val (offset, length) = ranges[index]
                val data = bytes("galleriesindex/galleries.$version.data", "bytes=$offset-${offset + length - 1}")
                return ids(data).drop(1)
            }
            address = children[index]
        } while (address != 0L)
        return emptyList()
    }
    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val c = (a[i].toInt() and 255).compareTo(b[i].toInt() and 255)
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }
    private suspend fun brief(id: Int): Comic {
        val doc = Jsoup.parse(http.text(url("galleryblock/$id.html"), platform))
        val source = doc.selectFirst("source[data-srcset]")?.attr("data-srcset").orEmpty().substringBefore(" 2x").trim()
        val cover = if (source.isEmpty()) "" else "https://atn.$domain/" + source.substringAfter("//").substringAfter('/').replace("avifbigtn", "webpbigtn").replace(".avif", ".webp")
        return Comic(id.toString(), platform, doc.selectFirst("h1.lillie a")?.text() ?: id.toString(), doc.selectFirst(".artist-list a")?.text().orEmpty(), cover,
            doc.select(".relatedtags a").map { it.text() })
    }
    private suspend fun items(ids: List<Int>): List<Comic> = coroutineScope {
        val semaphore = Semaphore(4)
        ids.map { id -> async { semaphore.withPermit { brief(id) } } }.awaitAll()
    }
    override suspend fun search(query: String, page: Int, sort: String): List<Comic> {
        val lists = query.split(' ').filter { it.isNotBlank() }.ifEmpty { listOf("") }.map { indexIDs(it, page * 24) }
        val matching = lists.drop(1).fold(lists.first().toSet()) { result, list -> result.intersect(list.toSet()) }
        return items(lists.first().filter { it in matching }.drop((page - 1) * 24).take(24))
    }
    override suspend fun explore(kind: String, page: Int): List<Comic> {
        val path = if (kind.startsWith("popular")) "popular/${when(kind) { "popular-today" -> "today"; "popular-week" -> "week"; "popular-month" -> "month"; else -> "year" }}-all.nozomi" else "index-all.nozomi"
        val list = ids(bytes(path, "bytes=0-${page * 24 * 4 - 1}"))
        return items((if(kind == "random") list.shuffled() else list).drop((page - 1) * 24).take(24))
    }
    private suspend fun gallery(comic: Comic): JSONObject {
        val script = http.text(url("galleries/${comic.id}.js"), platform)
        return JSONObject(script.substring(script.indexOf('{'), script.lastIndexOf('}') + 1))
    }
    override suspend fun detail(comic: Comic): ComicDetail {
        val json = gallery(comic)
        val tags = listOf("artists" to "artist", "groups" to "group", "parodys" to "parody", "characters" to "character", "tags" to "tag")
            .flatMap { (array, key) -> json.optJSONArray(array).objects().map { "$key:${it.optString(key)}" } }
        return ComicDetail(comic.copy(title = json.optString("title", comic.title), tags = tags, language = json.optString("language"), pages = json.optJSONArray("files")?.length() ?: 0), updatedText = json.optString("date"), tagGroups = groupedTags(tags))
    }
    override suspend fun pages(comic: Comic, chapter: Chapter): List<String> {
        val json = gallery(comic)
        val gg = http.text(url("gg.js"), platform)
        val numbers = Regex("case (\\d+)").findAll(gg).map { it.groupValues[1].toInt() }.toSet()
        val b = Regex("b: '(\\d+)").find(gg)?.groupValues?.get(1) ?: error("Hitomi 图片路由解析失败")
        val initial = Regex("var o = (\\d+)").find(gg)?.groupValues?.get(1)?.toInt() ?: 1
        return json.optJSONArray("files").objects().map { file ->
            val hash = file.getString("hash")
            val routing = (hash.takeLast(1) + hash.takeLast(3).take(2)).toInt(16)
            val bit = if (routing in numbers) initial.inv() and 1 else initial
            val suffix = hash.takeLast(3).toInt(16)
            val ext = if (file.optInt("haswebp") == 1) "webp" else file.getString("name").substringAfterLast('.')
            "https://w${if(bit == 0) 1 else 2}.$domain/$b/$suffix/$hash.$ext"
        }
    }
    override suspend fun categories() = listOf(Category("中文", "language:chinese"), Category("日本語", "language:japanese"), Category("English", "language:english")) +
        listOf("doujinshi", "manga", "artistcg", "gamecg", "imageset", "cosplay").map { Category(it) }
}
