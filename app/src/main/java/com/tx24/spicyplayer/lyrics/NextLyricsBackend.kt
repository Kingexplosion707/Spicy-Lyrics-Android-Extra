package com.tx24.spicyplayer.lyrics

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.network.data.*
import com.tx24.spicyplayer.network.data.providers.*
import com.tx24.spicyplayer.network.data.spotify.AnonymousSpotifyCatalogSearch
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.network.data.spotify.SpotifyTrackResolver
import com.tx24.spicyplayer.network.service.LyricsService
import com.tx24.spicyplayer.platform.CoreLog
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** External-playback wiring for the original player's provider boundary. */
internal class NextLyricsBackend(context: Context, clientKey: String) {
    init {
        // The sources, matching and parsers live in :core, which logs through CoreLog.
        CoreLog.sink = { level, tag, message ->
            if (level == CoreLog.Level.WARN) Log.w(tag, message) else Log.d(tag, message)
        }
    }

    private val diskCache = File(context.cacheDir, "lyrics")
    private val preferences = context.getSharedPreferences("lyrics_sources", Context.MODE_PRIVATE)
    private val client = lookupClient()
    private val gson = Gson()
    /** Header values of custom sources: kept out of [preferences], which backups copy. */
    private val customHeaders = context.getSharedPreferences("custom_source_headers", Context.MODE_PRIVATE)
    // One set of Genius pages for its lyrics, romanization and translation lookups, so a song's
    // lookups share their requests.
    private val geniusPages = GeniusSongPages(client, gson)
    /** The songs lrc.red's model synced at the user's request ([requestModelSync]), by ISRC. */
    private val lrcRedSyncs = context.getSharedPreferences("lrc_red_syncs", Context.MODE_PRIVATE)
    private val builtIn: Set<RemoteLyricsProvider> = createProviders(
        client, gson, clientKey, SharedSpotify.resolver, geniusPages,
        lrcRedSynced = ConcurrentHashMap(lrcRedSyncs.all.mapNotNull { (isrc, at) -> (at as? Long)?.let { isrc to it } }.toMap()),
    )
    private val lrcRed = builtIn.filterIsInstance<LrcRedLyricsProvider>().single()
    private val cooldowns = ProviderCooldownTracker()
    // Custom sources come and go, so these are rebuilt with them ([rebuild]).
    @Volatile private var providers: Set<RemoteLyricsProvider> = builtIn
    private val nativeNames = NativeNames(client, gson)
    @Volatile private var source = RemoteLyricsSource(providers, cooldowns, nativeNames = nativeNames::of)
    @Volatile var descriptors: List<LyricsSourceDescriptor> = emptyList()
        private set

    init {
        rebuild()
        // Left by the Musixmatch source, which is gone.
        File(context.cacheDir, "musixmatch-token.txt").delete()
    }
    /** Blends are switched on in Chinese services, just above their donors; built, never asked. */
    val blendDescriptors: List<LyricsSourceDescriptor> = LyricsBlends.ALL.map(LyricsBlendDefinition::descriptor)

    /** Readies the tokens of the providers that are asked; failures are left for the real lookup to report. */
    suspend fun warmUp() = coroutineScope {
        warmable(providers, ranking()).forEach { provider ->
            launch { runCatching { provider.warmUp() }.onFailure { if (it is CancellationException) throw it } }
        }
    }

    suspend fun resolve(
        request: LyricsLookupRequest,
        known: MutableMap<String, ProviderResult>,
        onUpdate: suspend (RemoteLyricsResolution) -> Unit = {},
    ): RemoteLyricsResolution = source.resolveLyrics(request, policy(), known = known, onUpdate = onUpdate)

    /**
     * [request]'s lyrics from provider [sourceId] alone (chosen for one song), asked now if it
     * wasn't yet, whichever source its answer is from. Only a provider the user has on somewhere:
     * one switched off everywhere was never agreed to.
     */
    suspend fun resolveFrom(request: LyricsLookupRequest, sourceId: String, known: MutableMap<String, ProviderResult>): RemoteLyricsResolution? {
        val ranking = ranking()
        if (!ranking.asked(sourceId)) return null
        val only = RemoteLyricsPolicy(
            sourceOrder = ranking.origins.map { it.id },
            pairs = ranking.origins.flatMap { origin ->
                origin.providerIds.map { SourcePair(origin.id, it).key to (it == sourceId) }
            }.toMap(),
        )
        return source.resolveLyrics(request, only, known = known)
    }

    /** Whether lrc.red's model may be asked to sync a song: while its AI syncs are on as a source. */
    fun canRequestModelSync(): Boolean = ranking().let { ranking ->
        ranking.origins.firstOrNull { it.id == LyricsOrigins.LRC_RED_AI }?.let { ranking.live(it, RemoteLyricsSource.LRC_RED_ID) } == true
    }

    /** Has lrc.red's model word-sync [request]'s song, remembering it so later lookups get the sync. */
    suspend fun requestModelSync(request: LyricsLookupRequest): ModelSyncResult {
        if (!canRequestModelSync()) return ModelSyncResult.Failed("lrc.red AI syncs are switched off in Sources.")
        val result = lrcRed.requestSync(request)
        if (result is ModelSyncResult.Synced) {
            lrcRed.synced[result.isrc]?.let { lrcRedSyncs.edit().putLong(result.isrc, it).apply() }
            // The stored picks predate the sync.
            cacheFile(request).delete()
            request.spotifyTrackId?.let { cacheFile(request, it).delete() }
        }
        return result
    }

    /**
     * A stored pick. [settled] is false when a source ranked above it had no real answer (an error,
     * or no Spotify match for a request without a length): the pick shows at once, but those
     * sources are asked again. [answers] are what may be reused without asking.
     */
    class CachedPick(val resolution: RemoteLyricsResolution, val settled: Boolean) {
        val answers: Map<String, ProviderResult> get() = buildMap {
            resolution.attempts.filter { it.outcome == ProviderAttemptOutcome.MISS }.forEach { put(it.sourceId, ProviderResult.Miss) }
            // A blend is rebuilt from its donors, never taken as an answer.
            (resolution as? RemoteLyricsResolution.Found)?.selection
                ?.takeIf { LyricsBlends.byId(it.source.id) == null }
                ?.let { put(it.source.id, ProviderResult.Hit(it.payload)) }
        }
    }

    /**
     * The last final pick for [request]: kept [CACHE_DAYS] days,
     * "no lyrics" included, errors never. Only reused while the sources, their order, switches and
     * sync types are the ones it was picked under, since others could pick differently.
     * [manualSpotifyId], a Spotify ID the user set for the song, has picks of its own.
     */
    fun cachedResolution(request: LyricsLookupRequest, manualSpotifyId: String? = null): CachedPick? {
        val stored = runCatching { gson.fromJson(cacheFile(request, manualSpotifyId).readText(), StoredPick::class.java) }.getOrNull()
            ?: return null
        if (stored.version != CACHE_VERSION || stored.expiresAt < System.currentTimeMillis() || stored.order != enabledOrder()) return null
        val attempts = stored.attempts.orEmpty().mapNotNull(StoredAttempt::restore)
        val payload = stored.payload ?: return CachedPick(RemoteLyricsResolution.NotFound(attempts), stored.settled)
        val source = (descriptors + blendDescriptors).firstOrNull { it.id == stored.sourceId } ?: return null
        val quality = payload.measuredQuality()
        return CachedPick(
            RemoteLyricsResolution.Found(
                RemoteLyricsSelection(source, payload, quality),
                attempts.map { if (it.sourceId == source.id) it.copy(message = "cached") else it },
            ),
            stored.settled,
        )
    }

    fun store(request: LyricsLookupRequest, resolution: RemoteLyricsResolution, manualSpotifyId: String? = null) {
        val found = resolution as? RemoteLyricsResolution.Found
        if (found == null && resolution !is RemoteLyricsResolution.NotFound) return
        val stored = StoredPick(
            version = CACHE_VERSION,
            expiresAt = System.currentTimeMillis() + CACHE_DAYS * 86_400_000L,
            order = enabledOrder(),
            sourceId = found?.selection?.source?.id,
            payload = found?.selection?.payload,
            attempts = resolution.attempts.map(StoredAttempt::of),
            settled = isSettled(request, resolution),
        )
        runCatching { diskCache.mkdirs(); cacheFile(request, manualSpotifyId).writeText(gson.toJson(stored)) }
    }

    fun forget(request: LyricsLookupRequest, manualSpotifyId: String? = null) {
        cacheFile(request, manualSpotifyId).delete()
        // Genius's recent answers are kept in memory too, for its lyrics, romanization and translation alike.
        geniusPages.clear()
    }

    /** Drops every stored pick and Genius lookup; the next lookups ask the sources again. */
    fun clearCache() {
        diskCache.deleteRecursively()
        romanCache.deleteRecursively()
        humanTranslationCache.clear()
        geniusPages.clear()
    }

    /** Each source with a live provider, in order, with those providers and the sync types it has off. */
    private fun enabledOrder(): List<String> = ranking().let { ranking ->
        val revisions = providers.mapNotNull { (it as? CustomLyricsProvider)?.source }.associate { it.id to it.revision }
        ranking.origins.mapNotNull { origin ->
            val live = origin.providerIds.filter { ranking.live(origin, it) }.ifEmpty { return@mapNotNull null }
                .map { id -> revisions[id]?.let { "$id@$it" } ?: id }
            val off = ranking.policy.syncTypesOff[origin.id].orEmpty().map { it.name }.sorted()
            "${origin.id}:${live.joinToString("+")}" + if (off.isEmpty()) "" else ":-${off.joinToString("+")}"
        }
    }

    private fun cacheFile(request: LyricsLookupRequest, manualSpotifyId: String? = null): File =
        File(diskCache, cacheName(request.title, request.artist, manualSpotifyId))

    private data class StoredPick(
        val version: Int,
        val expiresAt: Long,
        val order: List<String>,
        val sourceId: String?,
        val payload: RemoteLyricsPayload?,
        val attempts: List<StoredAttempt>?,
        val settled: Boolean,
    )

    /** An attempt by names, so a renamed or dropped enum value only loses that one line. */
    private data class StoredAttempt(
        val sourceId: String,
        val outcome: String,
        val quality: String,
        val category: String?,
        val message: String?,
    ) {
        fun restore(): ProviderAttempt? = runCatching {
            ProviderAttempt(
                sourceId,
                ProviderAttemptOutcome.valueOf(outcome),
                RemoteLyricsQuality.valueOf(quality),
                failureCategory = category?.let(ProviderFailureCategory::valueOf),
                message = message,
            )
        }.getOrNull()

        companion object {
            fun of(attempt: ProviderAttempt) = StoredAttempt(
                attempt.sourceId, attempt.outcome.name, attempt.quality.name, attempt.failureCategory?.name, attempt.message,
            )
        }
    }

    companion object {
        /** The [providers] worth warming up: those [ranking] asks. One switched off everywhere never fetches. */
        internal fun warmable(providers: Collection<RemoteLyricsProvider>, ranking: SourceRanking): List<RemoteLyricsProvider> =
            providers.filter { ranking.asked(it.descriptor.id) }

        /**
         * A stored pick's file name. Title + artist only: a queue entry warmed ahead has no album,
         * length or Spotify ID, and must hit the same entry the real load asks for once the song
         * starts. A [manualSpotifyId] gets an entry of its own, so its pick never stands in for the
         * song's usual one once the ID is cleared.
         */
        internal fun cacheName(title: String, artist: String, manualSpotifyId: String? = null): String =
            cacheKey(title, artist) + manualSpotifyId?.let { "-" + sha256(it).take(16) }.orEmpty() + ".json"

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun cacheKey(title: String, artist: String): String {
            val identity = listOf(title, artist).joinToString("\u001f") { it.trim().lowercase() }
            return sha256(identity)
        }

        /** Whether every source ranked above the pick (every source, for "no lyrics") really answered. */
        internal fun isSettled(request: LyricsLookupRequest, resolution: RemoteLyricsResolution): Boolean {
            val pick = (resolution as? RemoteLyricsResolution.Found)?.selection?.source?.id
            val above = resolution.attempts.takeWhile { it.sourceId != pick }
            return above.none {
                it.outcome in UNANSWERED ||
                    // Matched on title and artist alone (a queue entry, or a player that sends the
                    // length later): with the length, the match may well be found.
                    (it.outcome == ProviderAttemptOutcome.NEEDS_MATCH && request.durationSeconds <= 0)
            }
        }

        /**
         * Sources on by default before [SOURCE_DEFAULTS_VERSION] 2 that now ask first: switched off
         * once for installs that had them on, so they're only asked after their disclosure.
         */
        internal val NOW_OPT_IN = setOf("kugou", "netease", "qq_music", "kuwo", "genius")
        internal const val MUSIXMATCH_ID = "musixmatch"

        /**
         * Brings choices saved by an older version up to date, once. The order and every other
         * choice stay; [NOW_OPT_IN] sources and the Genius romanization switch, which asks Genius,
         * go off. What was on and went off, Musixmatch included, is kept in [SWITCHED_OFF] for the
         * user to be told. Runs on every read, so a restored older backup is brought up too.
         */
        internal fun migrateSourceDefaults(preferences: SharedPreferences) {
            val savedDefaults = preferences.getInt("defaults", 0)
            if (savedDefaults >= SOURCE_DEFAULTS_VERSION) return
            val edit = preferences.edit().putInt("defaults", SOURCE_DEFAULTS_VERSION)
            // Fresh installs start on the new defaults: nothing to switch off or tell.
            if (savedDefaults > 0) {
                val disabled = preferences.getStringSet("disabled", emptySet()).orEmpty()
                val switchedOff = (NOW_OPT_IN + MUSIXMATCH_ID).filterTo(mutableSetOf()) { it !in disabled }
                // On by default before.
                if (preferences.getBoolean("humanRomanizations", true)) switchedOff += SourceDisclosures.GENIUS_ROMANIZATION_ID
                edit.putStringSet("disabled", disabled + NOW_OPT_IN)
                    .remove("humanRomanizations")
                    // The Musixmatch word-sync switch, gone with Musixmatch.
                    .remove("ignoreMusixmatchWordSync")
                    .putStringSet(SWITCHED_OFF, switchedOff)
            }
            edit.apply()
        }

        /**
         * The sources' connections. The read timeout restarts with every byte, so [LOOKUP_CALL_TIMEOUT_S]
         * caps a whole call: a server trickling its answer can't hold the lookup open.
         */
        internal fun lookupClient(): OkHttpClient = OkHttpClient.Builder()
            .counted()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(LOOKUP_CALL_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        /** Every source the app asks, built on [client]. Also used by the live source check test. */
        fun createProviders(
            client: OkHttpClient,
            gson: Gson,
            clientKey: String,
            spotifyResolver: SpotifyTrackResolver = SpotifyTrackResolver(AnonymousSpotifyCatalogSearch(client, gson)),
            geniusPages: GeniusSongPages = GeniusSongPages(client, gson),
            lrcRedSynced: MutableMap<String, Long> = ConcurrentHashMap(),
        ): Set<RemoteLyricsProvider> {
            val lrclib = Retrofit.Builder()
                .baseUrl(LyricsService.BASE_URL)
                // LRCLIB asks clients to identify themselves; its Cloudflare front answers OkHttp's
                // default user agent with HTTP 520.
                .client(client.newBuilder().addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", APP_USER_AGENT).build())
                }.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(LyricsService::class.java)
            return setOf(
                SpicyLyricsProvider(client, gson, spotifyResolver, clientKey),
                AmllLyricsProvider(client, gson),
                UnisonLyricsProvider(client, gson),
                SpicyUnisonLyricsProvider(client, gson, spotifyResolver),
                RmmRevivalLyricsProvider(client, gson),
                LrcRedLyricsProvider(client, gson, lrcRedSynced),
                KugouLyricsProvider(client, gson),
                QqMusicLyricsProvider(client, gson),
                KuwoLyricsProvider(client),
                NetEaseLyricsProvider(client, gson),
                LyricsSource(lrclib),
                LrcMuxLyricsProvider(client, gson),
                GeniusLyricsProvider(geniusPages),
                YouTubeTranscriptLyricsProvider(client, gson),
            )
        }

        private val ORIGIN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/?#]*")

        /** The most one request to a source may take, body included. */
        internal const val LOOKUP_CALL_TIMEOUT_S = 20L
        /** Bump when payload conversion changes, so stale conversions are refetched. */
        private const val CACHE_VERSION = 37
        /** Outcomes that are no answer at all: a later lookup may get one. */
        private val UNANSWERED = setOf(
            ProviderAttemptOutcome.UNAVAILABLE,
            ProviderAttemptOutcome.COOLING_DOWN,
            ProviderAttemptOutcome.QUEUED,
            ProviderAttemptOutcome.PENDING,
        )
        /**
         * Bump when the default source order or on/off set changes, to reset saved choices once.
         * 2: Spicy Lyrics and the lyrics APIs open to any app (LRCLIB, AMLL TTML DB, Unison, LRCMux,
         * lrc.red, BiniLyrics) are on; every other source asks first ([SourceDisclosures]).
         */
        internal const val SOURCE_DEFAULTS_VERSION = 2
        private const val NETEASE_ID = "netease"
        private val APP_USER_AGENT = "Spicy Lyrics Mobile ${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
        private const val CACHE_DAYS = 3
        /** Custom sources start after every built-in one. */
        private const val CUSTOM_PRIORITY = 1_000
        internal const val SWITCHED_OFF = "switchedOff"
        private const val DISCLOSED = "disclosed"

        // Sources and their switches ([RemoteLyricsPolicy]). The per-provider "order", "disabled"
        // and "blends" they replace are read once more, to move them over ([LyricsSourceSettings.migrate]).
        internal const val SOURCE_ORDER = "sourceOrder"
        internal const val SOURCES_OFF = "sourcesOff"
        internal const val PAIRS_ON = "pairsOn"
        internal const val PAIRS_OFF = "pairsOff"
        internal const val SYNC_TYPES_OFF = "syncTypesOff"
        private val LEGACY_KEYS = listOf("order", "disabled", "blends")

        /**
         * The stored source preferences for [providers], moving the old per-provider ones over
         * the first time (also when a restored older backup brought them back). A fresh install
         * has the default sources and switches.
         */
        internal fun readPolicy(preferences: SharedPreferences, providers: Collection<LyricsSourceDescriptor>): RemoteLyricsPolicy {
            val order = preferences.getString(SOURCE_ORDER, null)
            if (order == null) {
                if (LEGACY_KEYS.none(preferences::contains)) return LyricsSourceSettings.normalized(RemoteLyricsPolicy(), providers)
                val migrated = LyricsSourceSettings.migrate(
                    preferences.getString("order", null)?.split(',')?.filter(String::isNotBlank),
                    preferences.getStringSet("disabled", emptySet()).orEmpty(),
                    preferences.getStringSet("blends", emptySet()).orEmpty(),
                    providers,
                )
                writePolicy(preferences, migrated)
                return migrated
            }
            val pairs = preferences.getStringSet(PAIRS_OFF, emptySet()).orEmpty().associateWith { false } +
                preferences.getStringSet(PAIRS_ON, emptySet()).orEmpty().associateWith { true }
            val syncOff = preferences.getStringSet(SYNC_TYPES_OFF, emptySet()).orEmpty().mapNotNull { entry ->
                val pair = SourcePair.parse(entry) ?: return@mapNotNull null
                runCatching { pair.originId to RemoteLyricsQuality.valueOf(pair.providerId) }.getOrNull()
            }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
            return LyricsSourceSettings.normalized(
                RemoteLyricsPolicy(
                    sourceOrder = order.split(',').filter(String::isNotBlank),
                    disabledSourceIds = preferences.getStringSet(SOURCES_OFF, emptySet()).orEmpty(),
                    pairs = pairs,
                    syncTypesOff = syncOff,
                ),
                providers,
            )
        }

        internal fun writePolicy(preferences: SharedPreferences, policy: RemoteLyricsPolicy) {
            preferences.edit()
                .putString(SOURCE_ORDER, policy.sourceOrder.joinToString(","))
                .putStringSet(SOURCES_OFF, policy.disabledSourceIds)
                .putStringSet(PAIRS_ON, policy.pairs.filterValues { it }.keys)
                .putStringSet(PAIRS_OFF, policy.pairs.filterValues { !it }.keys)
                .putStringSet(SYNC_TYPES_OFF, policy.syncTypesOff.flatMapTo(mutableSetOf()) { (origin, types) -> types.map { "$origin/${it.name}" } })
                .apply()
        }
    }

    /** Run on every read, so a restored old backup can't switch the old sources back on either. */
    private fun migrateDefaults() = migrateSourceDefaults(preferences)

    /** The sources in order, with every switch spelled out. */
    fun policy(): RemoteLyricsPolicy {
        migrateDefaults()
        return readPolicy(preferences, descriptors)
    }

    fun ranking(): SourceRanking = SourceRanking(descriptors, policy())

    /** Stores [policy], as [LyricsSourceSettings] changed it. */
    fun savePolicy(policy: RemoteLyricsPolicy) = writePolicy(preferences, LyricsSourceSettings.normalized(policy, descriptors))

    /** Whether what [id] sends ([SourceDisclosures]) has been shown and agreed to. */
    fun isDisclosed(id: String): Boolean = id in preferences.getStringSet(DISCLOSED, emptySet()).orEmpty()

    fun markDisclosed(id: String) {
        preferences.edit().putStringSet(DISCLOSED, preferences.getStringSet(DISCLOSED, emptySet()).orEmpty() + id).apply()
    }

    /** What the update to [SOURCE_DEFAULTS_VERSION] switched off ([migrateSourceDefaults]), until the user's been told. */
    val switchedOffSources: Set<String>
        get() {
            migrateDefaults()
            return preferences.getStringSet(SWITCHED_OFF, emptySet()).orEmpty()
        }

    fun dismissSwitchedOff() {
        preferences.edit().remove(SWITCHED_OFF).apply()
    }

    /** Brackets at a synced line's start or end read as its background vocals ([BackgroundVocals]). */
    var splitBackgroundVocals: Boolean
        get() = preferences.getBoolean("splitBackgroundVocals", true)
        set(value) = preferences.edit().putBoolean("splitBackgroundVocals", value).apply()

    /** Human-written romanizations from Genius over the on-device ones (off until asked for: it asks Genius). */
    var humanRomanizations: Boolean
        get() = preferences.getBoolean("humanRomanizations", false)
        set(value) = preferences.edit().putBoolean("humanRomanizations", value).apply()

    private val geniusRomanization = GeniusRomanizationSource(geniusPages)
    // Named anew when Genius matching changes, so answers matched the old way are asked again.
    private val romanCache = File(context.cacheDir, "genius-roman-3")
    private val geniusTranslation = GeniusTranslationSource(geniusPages)
    private val humanTranslationCache = com.tx24.spicyplayer.translation.DiskGeniusTranslationCache(File(context.cacheDir, "genius-translations"))

    suspend fun humanTranslation(title: String, artist: String, target: String): com.tx24.spicyplayer.translation.GeniusTranslationPair? =
        humanTranslationCache.find(title, artist, target) { geniusTranslation.find(title, artist, target) }

    /**
     * A human-written romanization of the song as lyric lines: Genius's, else NetEase's while
     * NetEase is on as a source; null when neither has one. Genius's answer is kept on disk a
     * week when found and a day when not, so a replayed song asks once; NetEase's comes with its
     * lyrics lookup. A lookup that failed throws and isn't kept, so the next play asks again.
     */
    suspend fun humanRomanization(title: String, artist: String, durationSeconds: Int = 0, lines: List<String> = emptyList()): List<String>? {
        val file = File(romanCache, cacheKey(title, artist) + ".json")
        val stored = runCatching { gson.fromJson(file.readText(), StoredRoman::class.java) }.getOrNull()
            ?.takeIf { it.expiresAt > System.currentTimeMillis() }
        val lines = if (stored != null) stored.lines else geniusRomanization.find(title, artist, lines).also { found ->
            val keep = TimeUnit.DAYS.toMillis(if (found != null) 7 else 1)
            runCatching {
                romanCache.mkdirs()
                file.writeText(gson.toJson(StoredRoman(System.currentTimeMillis() + keep, found)))
            }
        }
        if (lines != null || !ranking().asked(NETEASE_ID)) return lines
        return builtIn.filterIsInstance<NetEaseLyricsProvider>().firstOrNull()
            ?.romanization(LyricsLookupRequest(artist, title, "", durationSeconds))
    }

    fun forgetRomanization(title: String, artist: String) {
        File(romanCache, cacheKey(title, artist) + ".json").delete()
        humanTranslationCache.forget(title, artist)
        geniusPages.clear()
    }

    private data class StoredRoman(val expiresAt: Long, val lines: List<String>?)

    /** The user's own sources ([CustomLyricsSource]), in the order they were added. */
    fun customSources(): List<CustomLyricsSource> = runCatching {
        JsonParser.parseString(preferences.getString("custom", null) ?: return emptyList()).asJsonArray.mapNotNull { element ->
            runCatching {
                val o = element.asJsonObject
                val id = o.get("id").asString
                val url = o.get("url").asString
                // Values only go to the host they were typed for: a restored backup that points
                // a source elsewhere leaves them behind. Ones saved before this get bound now.
                val bound = customHeaders.getString(originKey(id), null)
                    ?: origin(url).also { customHeaders.edit().putString(originKey(id), it).apply() }
                val values = if (bound != origin(url)) null
                    else runCatching { JsonParser.parseString(customHeaders.getString(id, "{}")).asJsonObject }.getOrNull()
                CustomLyricsSource(
                    id = id,
                    name = o.get("name").asString,
                    url = url,
                    path = o.get("path")?.asString.orEmpty(),
                    headers = o.getAsJsonArray("headers")?.map { it.asString }.orEmpty()
                        .filter(CustomLyricsSource::isHeaderName)
                        .map { name -> name to (values?.get(name)?.asString.orEmpty()) },
                    keyRevision = customHeaders.getString(revisionKey(id), null).orEmpty(),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    /** Adds [custom], or replaces the one with its ID. */
    fun saveCustomSource(custom: CustomLyricsSource) {
        writeCustomSources(customSources().let { list ->
            if (list.any { it.id == custom.id }) list.map { if (it.id == custom.id) custom else it } else list + custom
        })
        customHeaders.edit()
            .putString(custom.id, JsonObject().apply { custom.headers.forEach { (name, value) -> addProperty(name, value) } }.toString())
            .putString(originKey(custom.id), origin(custom.url))
            .putString(revisionKey(custom.id), java.util.UUID.randomUUID().toString().take(8))
            .apply()
        rebuild()
    }

    fun removeCustomSource(id: String) {
        writeCustomSources(customSources().filter { it.id != id })
        customHeaders.edit().remove(id).remove(originKey(id)).remove(revisionKey(id)).apply()
        rebuild()
    }

    /** Asks [custom] alone for [request], for its editor's test; nothing is cached. */
    suspend fun testCustomSource(custom: CustomLyricsSource, request: LyricsLookupRequest): ProviderResult =
        CustomLyricsProvider(custom, client, SharedSpotify.resolver, 0, APP_USER_AGENT).fetch(request)

    private fun originKey(id: String) = "$id@origin"
    private fun revisionKey(id: String) = "$id@revision"
    /** "https://host:port" of a URL template, placeholders and all. */
    private fun origin(url: String) = ORIGIN.find(url)?.value?.lowercase().orEmpty()

    // Names and addresses only: header values are in [customHeaders].
    private fun writeCustomSources(list: List<CustomLyricsSource>) {
        val array = JsonArray()
        list.forEach { custom ->
            array.add(JsonObject().apply {
                addProperty("id", custom.id)
                addProperty("name", custom.name)
                addProperty("url", custom.url)
                addProperty("path", custom.path)
                add("headers", JsonArray().apply { custom.headers.forEach { add(it.first) } })
            })
        }
        preferences.edit().putString("custom", array.toString()).apply()
    }

    /** Re-reads the custom sources, after a settings restore. */
    fun reloadCustomSources() = rebuild()

    private fun rebuild() {
        val custom = customSources().mapIndexed { index, it -> CustomLyricsProvider(it, client, SharedSpotify.resolver, CUSTOM_PRIORITY + index, APP_USER_AGENT) }
        val all = builtIn + custom
        providers = all
        source = RemoteLyricsSource(all, cooldowns, nativeNames = nativeNames::of)
        descriptors = all.map(RemoteLyricsProvider::descriptor)
            .sortedWith(compareBy<LyricsSourceDescriptor> { it.defaultPriority }.thenBy { it.id })
    }

}
