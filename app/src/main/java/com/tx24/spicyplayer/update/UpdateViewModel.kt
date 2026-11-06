package com.tx24.spicyplayer.update

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
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
 * Looks for a newer build on GitHub Releases, downloads its APK, checks it, and hands it to
 * Android's installer (which always asks the user first). Off in debug builds: they are a
 * separate app that a release APK would not update.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().counted().build()
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private val mutableChangelog = MutableStateFlow<ChangelogState>(ChangelogState.Idle)
    val changelog: StateFlow<ChangelogState> = mutableChangelog.asStateFlow()

    val enabled: Boolean get() = !BuildConfig.DEBUG

    init {
        viewModelScope.launch {
            UpdateInstallReceiver.results.collect { failure ->
                val release = installingRelease()
                mutableState.value = mutableState.value.copy(
                    status = if (failure == null) UpdateStatus.Available(release ?: return@collect)
                    else UpdateStatus.Failed(failure, release),
                )
            }
        }
        viewModelScope.launch {
            UpdateInstallReceiver.pending.collect { confirm ->
                val status = mutableState.value.status
                if (confirm != null && status is UpdateStatus.Installing) {
                    mutableState.value = mutableState.value.copy(status = UpdateStatus.ReadyToInstall(status.release))
                }
            }
        }
    }

    private fun installingRelease(): AppRelease? = when (val status = mutableState.value.status) {
        is UpdateStatus.Installing -> status.release
        is UpdateStatus.ReadyToInstall -> status.release
        else -> null
    }

    /** On opening the app: at most every few hours, and quiet unless there is something new. */
    fun checkOnLaunch(includePrereleases: Boolean) {
        val now = System.currentTimeMillis()
        if (!enabled || now - prefs.getLong(KEY_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL_MS) return
        check(includePrereleases, manual = false)
    }

    /** For the settings footer's status: once per run, failing silently and never popping up. */
    fun checkQuietly(includePrereleases: Boolean) {
        if (mutableState.value.status == UpdateStatus.Idle) check(includePrereleases, manual = false, prompt = false)
    }

    fun check(includePrereleases: Boolean, manual: Boolean = true, prompt: Boolean = true) {
        if (!enabled || job?.isActive == true) return
        mutableState.value = mutableState.value.copy(status = UpdateStatus.Checking)
        job = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { fetch(RELEASES_URL).use { Releases.parse(it.body.string()) } }
            }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val releases = result.getOrElse { error ->
                Log.w(TAG, "Update check failed", error)
                mutableState.value = mutableState.value.copy(status = if (manual) {
                    UpdateStatus.Failed("Couldn't reach GitHub. Try again later.")
                } else UpdateStatus.Idle)
                return@launch
            }
            val newest = Releases.newest(releases, BuildConfig.VERSION_NAME, includePrereleases)
            mutableState.value = when {
                newest == null -> UpdateUiState(UpdateStatus.UpToDate)
                // A skipped version only stays quiet on its own: asking from settings still shows it.
                !prompt || !manual && prefs.getString(KEY_SKIPPED, null) == newest.tag -> UpdateUiState(UpdateStatus.Available(newest))
                else -> UpdateUiState(UpdateStatus.Available(newest), prompt = true)
            }
        }
    }

    /**
     * Every release for the changelog, once a run (GitHub allows few unsigned requests an hour).
     * Debug builds too: reading notes installs nothing.
     */
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

    /** Brings the pop-up back for an update already found. */
    fun show() {
        val status = mutableState.value.status
        if (status is UpdateStatus.Available || status is UpdateStatus.ReadyToInstall) mutableState.value = mutableState.value.copy(prompt = true)
    }

    fun later() {
        mutableState.value = mutableState.value.copy(prompt = false)
    }

    fun skip() {
        (mutableState.value.status as? UpdateStatus.Available)?.let { prefs.edit().putString(KEY_SKIPPED, it.release.tag).apply() }
        later()
    }

    /** Opens the installer's prompt that Android held back while the app was out of view. */
    fun confirmInstall(context: Context) {
        val status = mutableState.value.status as? UpdateStatus.ReadyToInstall ?: return
        val confirm = UpdateInstallReceiver.takePending(context)
        if (confirm == null) {
            // The installer's prompt is no longer held: start the update over.
            mutableState.value = UpdateUiState(UpdateStatus.Available(status.release), prompt = true)
            return
        }
        mutableState.value = UpdateUiState(UpdateStatus.Installing(status.release), prompt = true)
        runCatching { context.startActivity(confirm) }.onFailure { error ->
            Log.w(TAG, "Couldn't open the installer", error)
            mutableState.value = UpdateUiState(UpdateStatus.Failed("Android's installer didn't open. Try again.", status.release), prompt = true)
        }
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
                val apk = withContext(Dispatchers.IO) { download(release) }
                mutableState.value = UpdateUiState(UpdateStatus.Installing(release), prompt = true)
                withContext(Dispatchers.IO) { commit(apk) }
            }.exceptionOrNull() ?: return@launch
            Log.w(TAG, "Update failed", failure)
            mutableState.value = UpdateUiState(
                UpdateStatus.Failed(failure.message ?: "The download didn't finish.", release),
                prompt = true,
            )
        }
    }

    private fun download(release: AppRelease): File {
        val dir = File(getApplication<Application>().cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val apk = File(dir, "${release.tag}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        fetch(release.apkUrl).use { response ->
            val total = response.body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
            response.body.byteStream().use { input ->
                apk.outputStream().use { output ->
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
        // Only ever this app: anything else under that link is not an update for it.
        val app = getApplication<Application>()
        @Suppress("DEPRECATION")
        val packageName = app.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName
        check(packageName == app.packageName) { "That download isn't an update for this app." }
        return apk
    }

    private fun commit(apk: File) {
        val app = getApplication<Application>()
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(app.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            // The installer fills in the result, so the intent must stay mutable.
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val intent = PendingIntent.getBroadcast(
                app, sessionId, Intent(app, UpdateInstallReceiver::class.java), flags or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(intent.intentSender)
        }
    }

    private fun fetch(url: String) = http.newCall(
        Request.Builder()
            .url(url)
            .header("User-Agent", "Spicy Lyrics Mobile ${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.github+json")
            .build(),
    ).execute().also { response ->
        if (!response.isSuccessful) {
            response.close()
            error("HTTP ${response.code}")
        }
    }

    private companion object {
        const val TAG = "SpicyUpdate"
        const val RELEASES_URL = "https://api.github.com/repos/spicylyrics/mobile/releases?per_page=20"
        const val CHANGELOG_URL = "https://api.github.com/repos/spicylyrics/mobile/releases?per_page=100"
        const val KEY_LAST_CHECK = "lastCheckMs"
        const val KEY_SKIPPED = "skippedTag"
        const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
