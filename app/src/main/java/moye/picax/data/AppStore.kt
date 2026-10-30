package moye.picax.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moye.picax.model.*
import moye.picax.data.source.SourceSettings
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AppStore(val context: Context) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = AtomicFile(File(context.filesDir, "library.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(read())
    val data: StateFlow<AppData> = mutable.asStateFlow()
    val preferences = data.map { it.preferences }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, mutable.value.preferences)
    private fun presentation(value: AppData) = LibraryPresentation(value.history.associateBy { it.comic.key }, value.favorites.associateBy { it.comic.key }, value.preferences)
    val libraryPresentation = data.map { Triple(it.history, it.favorites, it.preferences) }
        .distinctUntilChanged()
        .map { (history, favorites, preferences) -> LibraryPresentation(history.associateBy { it.comic.key }, favorites.associateBy { it.comic.key }, preferences) }
        .stateIn(scope, SharingStarted.Eagerly, presentation(mutable.value))
    val vault = CredentialVault(context, json)

    init {
        scope.launch {
            @OptIn(FlowPreview::class)
            data.drop(1).debounce(150).collect { value ->
                val stream = file.startWrite()
                try { stream.write(json.encodeToString(value).toByteArray()); file.finishWrite(stream) }
                catch (error: Exception) { file.failWrite(stream); throw error }
            }
        }
    }

    private fun read(): AppData = if (file.baseFile.exists())
        runCatching { json.decodeFromString<AppData>(file.openRead().use { it.reader().readText() }).let { it.copy(preferences = SourceSettings.migrate(it.preferences)) } }.getOrElse { AppData() }
        else AppData()

    fun update(block: (AppData) -> AppData) { mutable.update(block) }
    fun setting(key: String, fallback: String = "") = data.value.preferences[key] ?: fallback
    fun bool(key: String, fallback: Boolean = false) = setting(key, fallback.toString()).toBoolean()
    fun number(key: String, fallback: Int) = setting(key).toIntOrNull() ?: fallback
    fun set(key: String, value: Any) = update { it.copy(preferences = it.preferences + (key to value.toString())) }
    fun favorite(comic: Comic, folder: String = "默认收藏夹") = update { state ->
        state.copy(favorites = if (state.favorites.any { it.comic.key == comic.key }) state.favorites.filterNot { it.comic.key == comic.key }
            else listOf(Favorite(comic, folder)) + state.favorites)
    }
    fun later(comic: Comic) = update { state ->
        val removing = state.later.any { it.key == comic.key }
        state.copy(later = if(removing) state.later.filterNot { it.key == comic.key } else listOf(comic) + state.later,
            laterAddedAt = if(removing) state.laterAddedAt - comic.key else state.laterAddedAt + (comic.key to System.currentTimeMillis()))
    }
    fun record(comic: Comic, chapter: Chapter, page: Int = 0, count: Int = 0, chapterCount: Int = 1) = update { it.copy(history = listOf(History(comic, chapter, page, count, chapterCount = chapterCount)) + it.history.filterNot { h -> h.comic.key == comic.key }) }
    fun deleteDownloads(comicKeys: Set<String>) {
        val records = data.value.downloads.filter { it.detail.comic.key in comicKeys }
        update { it.copy(downloads = it.downloads.filterNot { record -> record.detail.comic.key in comicKeys }) }
        scope.launch { records.forEach { downloadDir(it).deleteRecursively() } }
    }
    fun addTime(comic: Comic, seconds: Long) = update { state ->
        val day = LocalDate.now().toString()
        val existing = state.readingTime.firstOrNull { it.comic.key == comic.key && it.day == day }
        state.copy(readingTime = state.readingTime.filterNot { it.comic.key == comic.key && it.day == day } + ReadingTime(comic, day, (existing?.seconds ?: 0) + seconds))
    }
    fun saveAccount(platform: Platform, username: String, credential: Credential) {
        vault.save(platform, credential)
        update { it.copy(accounts = it.accounts.filterNot { a -> a.platform == platform } + Account(platform, username)) }
    }
    fun logout(platform: Platform) {
        vault.remove(platform)
        update { it.copy(accounts = it.accounts.filterNot { a -> a.platform == platform }) }
    }
    fun blocked(comic: Comic) = data.value.rules.any { it.enabled && (it.platform == null || it.platform == comic.platform) &&
        (comic.title + " " + comic.subtitle + " " + comic.tags.joinToString(" ")).contains(it.text, ignoreCase = true) }
    fun download(record: DownloadRecord) = update { it.copy(downloads = it.downloads.filterNot { d -> d.key == record.key } + record) }
    fun downloadDir(record: DownloadRecord) = File(context.filesDir, "downloads/" + safeName(record.key))
    fun pageFile(record: DownloadRecord, page: Int) = File(downloadDir(record), "%05d.img".format(page))
    companion object { fun safeName(value: String) = value.toByteArray().let { java.security.MessageDigest.getInstance("SHA-256").digest(it) }.joinToString("") { "%02x".format(it) } }
}

class CredentialVault(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
    private val cached = java.util.concurrent.ConcurrentHashMap<String, Credential>()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("picax.accounts", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("picax.accounts", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(platform: Platform, value: Credential) = save(platform.name, value)
    fun save(name: String, value: Credential) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(json.encodeToString(value).toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        cached[name] = value
    }
    fun get(platform: Platform): Credential = get(platform.name)
    fun get(name: String): Credential {
        cached[name]?.let { return it }
        val encoded = prefs.getString(name, null) ?: return Credential()
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        return json.decodeFromString<Credential>(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))).also { cached[name] = it }
    }
    fun remove(platform: Platform) { cached.remove(platform.name); prefs.edit().remove(platform.name).apply() }
}
