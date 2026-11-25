package com.tx24.spicyplayer.update

// The desktop version of :app's UpdateViewModel.kt (same states): it looks for the desktop app's
// installer on GitHub Releases, downloads it and runs it.

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tx24.spicyplayer.BuildConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tx24.spicyplayer.network.data.counted
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: AppRelease) : UpdateStatus
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateStatus
    /** Handed to Android's installer, which asks the user to confirm. */
    data class Installing(val release: AppRelease) : UpdateStatus
    /** Downloaded while the app was out of view: Android held its prompt back until the user taps "Install". */
    data class ReadyToInstall(val release: AppRelease) : UpdateStatus
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateStatus
}

/** The changelog page's list of releases, fetched once a run. */
sealed interface ChangelogState {
    data object Idle : ChangelogState
    data object Loading : ChangelogState
    /** Newest first. */
    data class Loaded(val releases: List<AppRelease>) : ChangelogState
    data object Failed : ChangelogState
}

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.Idle,
    /** The update pop-up is up. A check from settings answers in place instead. */
    val prompt: Boolean = false,
)

/**
 * Looks for a newer desktop build on GitHub Releases (the installer published with tags
 * `desktop-app-v…`), downloads it, checks it and runs it; the installer replaces this app.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().counted().build()
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private val mutableChangelog = MutableStateFlow<ChangelogState>(ChangelogState.Idle)
    val changelog: StateFlow<ChangelogState> = mutableChangelog.asStateFlow()
    private var downloaded: File? = null

    val enabled: Boolean get() = System.getProperty("os.name").startsWith("Windows")

    fun checkOnLaunch(includePrereleases: Boolean) {
        val now = System.currentTimeMillis()
        if (!enabled || now - prefs.getLong(KEY_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL_MS) return
        check(includePrereleases, manual = false)
    }

    fun checkQuietly(includePrereleases: Boolean) {
        if (mutableState.value.status == UpdateStatus.Idle) check(includePrereleases, manual = false, prompt = false)
    }

    fun check(includePrereleases: Boolean, manual: Boolean = true, prompt: Boolean = true) {
        if (!enabled || job?.isActive == true) return
        mutableState.value = mutableState.value.copy(status = UpdateStatus.Checking)
        job = viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetch(RELEASES_URL).use { desktopReleases(it.body.string()) } } }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val releases = result.getOrElse { error ->
                Log.w(TAG, "Update check failed", error)
                mutableState.value = mutableState.value.copy(status = if (manual) UpdateStatus.Failed("Couldn't reach GitHub. Try again later.") else UpdateStatus.Idle)
                return@launch
            }
            // Desktop builds are all published as prereleases (GitHub's "latest" is the phone app's).
            val newest = Releases.newest(releases, DESKTOP_VERSION, includePrereleases = true)
            mutableState.value = when {
                newest == null -> UpdateUiState(UpdateStatus.UpToDate)
                !prompt || !manual && prefs.getString(KEY_SKIPPED, null) == newest.tag -> UpdateUiState(UpdateStatus.Available(newest))
                else -> UpdateUiState(UpdateStatus.Available(newest), prompt = true)
            }
        }
    }

    fun loadChangelog() {
        val now = mutableChangelog.value
        if (now is ChangelogState.Loading || now is ChangelogState.Loaded) return
        mutableChangelog.value = ChangelogState.Loading
        viewModelScope.launch {
            mutableChangelog.value = runCatching {
                withContext(Dispatchers.IO) { fetch(CHANGELOG_URL).use { Releases.parse(it.body.string()) } }
            }.fold(
                onSuccess = { releases -> ChangelogState.Loaded(releases.sortedWith { a, b -> Releases.compareVersions(b.version, a.version) }) },
                onFailure = { error -> Log.w(TAG, "Changelog failed", error); ChangelogState.Failed },
            )
        }
    }

    fun show() {
        val status = mutableState.value.status
        if (status is UpdateStatus.Available || status is UpdateStatus.ReadyToInstall) mutableState.value = mutableState.value.copy(prompt = true)
    }

    fun later() { mutableState.value = mutableState.value.copy(prompt = false) }

    fun skip() {
        (mutableState.value.status as? UpdateStatus.Available)?.let { prefs.edit().putString(KEY_SKIPPED, it.release.tag).apply() }
        later()
    }

    fun confirmInstall(context: Context) {
        val status = mutableState.value.status as? UpdateStatus.ReadyToInstall ?: return
        val file = downloaded ?: return
        run(file, status.release)
    }

    fun install() {
        val release = when (val status = mutableState.value.status) {
            is UpdateStatus.Available -> status.release
            is UpdateStatus.Failed -> status.release
            else -> null
        } ?: return
        if (job?.isActive == true) return
        mutableState.value = UpdateUiState(UpdateStatus.Downloading(release, 0f), prompt = true)
        job = viewModelScope.launch {
            val failure = runCatching {
                val file = withContext(Dispatchers.IO) { download(release) }
                downloaded = file
                run(file, release)
            }.exceptionOrNull() ?: return@launch
            Log.w(TAG, "Update failed", failure)
            mutableState.value = UpdateUiState(UpdateStatus.Failed(failure.message ?: "The download didn't finish.", release), prompt = true)
        }
    }

    /** Starts the installer and quits, so it can replace the app's files. */
    private fun run(file: File, release: AppRelease) {
        mutableState.value = UpdateUiState(UpdateStatus.Installing(release), prompt = true)
        val command = if (file.name.endsWith(".msi")) listOf("msiexec", "/i", file.path, "/passive") else listOf(file.path)
        ProcessBuilder(command).start()
        com.tx24.spicyplayer.desktop.platform.DesktopPlatform.quit()
    }

    private fun download(release: AppRelease): File {
        val dir = File(getApplication<Application>().cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, release.apkUrl.substringAfterLast('/'))
        val digest = MessageDigest.getInstance("SHA-256")
        fetch(release.apkUrl).use { response ->
            val total = response.body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = 0f
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        val progress = if (total > 0) done.toFloat() / total else 0f
                        if (progress - shown >= 0.01f) {
                            shown = progress
                            mutableState.value = mutableState.value.copy(status = UpdateStatus.Downloading(release, progress))
                        }
                    }
                }
            }
        }
        release.sha256Url?.let { url ->
            val expected = fetch(url).use { it.body.string() }.trim().substringBefore(' ').lowercase()
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(expected == actual) { "The download was damaged. Try again." }
        }
        return file
    }

    private fun fetch(url: String) = http.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()).execute().also {
        check(it.isSuccessful) { "HTTP ${it.code}" }
    }

    /** The desktop installers among GitHub's releases, as [AppRelease]s (the installer in place of the APK). */
    private fun desktopReleases(json: String): List<AppRelease> {
        val array = com.google.gson.JsonParser.parseString(json) as? com.google.gson.JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val release = element as? com.google.gson.JsonObject ?: return@mapNotNull null
            val tag = release.get("tag_name")?.asString ?: return@mapNotNull null
            if (!tag.startsWith(DESKTOP_TAG)) return@mapNotNull null
            val assets = release.getAsJsonArray("assets")?.mapNotNull { it as? com.google.gson.JsonObject }.orEmpty()
            val installer = assets.firstOrNull { a -> a.get("name")?.asString?.let { it.startsWith("SpicyLyricsPlus") && (it.endsWith(".msi") || it.endsWith(".exe")) } == true }
                ?: return@mapNotNull null
            val name = installer.get("name").asString
            AppRelease(
                tag = tag, version = tag.removePrefix(DESKTOP_TAG),
                name = release.get("name")?.takeIf { !it.isJsonNull }?.asString ?: tag,
                notes = ReleaseNotes.parse(release.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty()),
                date = release.get("published_at")?.takeIf { !it.isJsonNull }?.asString?.take(10),
                prerelease = release.get("prerelease")?.asBoolean == true,
                apkUrl = installer.get("browser_download_url").asString,
                apkSizeBytes = installer.get("size")?.asLong ?: 0L,
                sha256Url = assets.firstOrNull { it.get("name")?.asString == "$name.sha256" }?.get("browser_download_url")?.asString,
                pageUrl = release.get("html_url")?.asString.orEmpty(),
            )
        }
    }

    private companion object {
        const val TAG = "SpicyUpdates"
        const val KEY_LAST_CHECK = "lastCheck"
        const val KEY_SKIPPED = "skipped"
        const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val DESKTOP_TAG = "desktop-v"
        val DESKTOP_VERSION: String = com.tx24.spicyplayer.desktop.platform.DesktopPlatform.version
        const val RELEASES_URL = "https://api.github.com/repos/Kingexplosion707/Spicy-Lyrics-Android-Extra/releases?per_page=30"
        const val CHANGELOG_URL = RELEASES_URL
    }
}
