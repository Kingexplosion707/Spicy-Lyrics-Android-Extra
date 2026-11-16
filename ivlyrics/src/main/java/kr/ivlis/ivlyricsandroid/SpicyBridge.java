package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spicy Lyrics Plus's door into ivLyrics. Everything else in this module is ivLyrics' own code,
 * package-private; Spicy calls only what is here. Lyrics cross in Spicy's terms ({@link Line}).
 * Nothing here works below Android 8 (ivLyrics' minimum): check {@link #supported()} first.
 */
public final class SpicyBridge {
    private SpicyBridge() {}

    private static final String SPOTIFY = "com.spotify.music";
    private static volatile boolean initialised;
    private static FuriganaRepository furigana;

    /** ivLyrics needs Android 8 or newer. */
    public static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    /** Sets up what ivLyrics' own app sets up at start. Safe to call again. */
    public static synchronized void init(Context context) {
        if (initialised || !supported()) return;
        initialised = true;
        WebViewSupport.install(new WebViewSupport.Backend() {
            @Override
            public boolean addDocumentStartJavaScript(WebView view, String script, Set<String> origins) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false;
                WebViewCompat.addDocumentStartJavaScript(view, script, origins);
                return true;
            }
        });
    }

    // ---- lyrics, in Spicy's terms ----

    public static final class Syllable {
        public final String text;
        public final long startMs;
        public final long endMs;

        public Syllable(String text, long startMs, long endMs) {
            this.text = text == null ? "" : text;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    public static final class Line {
        public final String text;
        public final long startMs;
        public final long endMs;
        /** Word or syllable timing; empty for a line timed only as a whole. Texts carry their own spaces. */
        public final List<Syllable> syllables;
        /** Background vocals sung with the lead line before them. */
        public final boolean background;
        public final String singer;
        public final String translation;

        public Line(String text, long startMs, long endMs, List<Syllable> syllables, boolean background, String singer, String translation) {
            this.text = text == null ? "" : text;
            this.startMs = startMs;
            this.endMs = endMs;
            this.syllables = syllables == null ? Collections.emptyList() : syllables;
            this.background = background;
            this.singer = singer == null ? "" : singer;
            this.translation = translation == null ? "" : translation;
        }
    }

    /** A lookup's answer: its lines, how they are timed, and who served them. */
    public static final class Lookup {
        public final List<Line> lines;
        public final boolean wordSynced;
        public final boolean lineSynced;
        public final String provider;

        Lookup(List<Line> lines, boolean wordSynced, boolean lineSynced, String provider) {
            this.lines = lines;
            this.wordSynced = wordSynced;
            this.lineSynced = lineSynced;
            this.provider = provider;
        }
    }

    private static List<LyricsLine.Syllable> syllables(List<Syllable> from) {
        List<LyricsLine.Syllable> out = new ArrayList<>(from.size());
        for (Syllable syllable : from) out.add(new LyricsLine.Syllable(syllable.text, syllable.startMs, syllable.endMs));
        return out;
    }

    /** Spicy's lines as ivLyrics' result: background vocals ride on the lead line before them. */
    static LyricsResult result(List<Line> lines, boolean wordSynced) {
        List<LyricsLine> out = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            Line lead = lines.get(index++);
            if (lead.background) continue;
            List<Line> backing = new ArrayList<>();
            while (index < lines.size() && lines.get(index).background) backing.add(lines.get(index++));
            List<LyricsLine.Syllable> leadSyllables = syllables(lead.syllables);
            List<LyricsLine.VocalPart> parts = new ArrayList<>();
            if (!backing.isEmpty() && !leadSyllables.isEmpty()) {
                parts.add(new LyricsLine.VocalPart("lead", "lead", lead.singer, "vocal", lead.text, leadSyllables));
                int number = 0;
                for (Line line : backing) {
                    parts.add(new LyricsLine.VocalPart("bg" + number++, "background", line.singer, "vocal", line.text, syllables(line.syllables)));
                }
            }
            out.add(new LyricsLine(lead.startMs, lead.endMs, lead.text, leadSyllables, lead.singer, "vocal", parts,
                    "", lead.translation, ""));
        }
        return new LyricsResult(out, "Spicy Lyrics", "", wordSynced);
    }

    private static List<Line> lines(LyricsResult result) {
        List<Line> out = new ArrayList<>();
        for (LyricsLine line : result.lines) {
            boolean hasParts = false;
            for (LyricsLine.VocalPart part : line.vocalParts) {
                if (part.syllables.isEmpty()) continue;
                hasParts = true;
                out.add(new Line(part.text, part.startTimeMs, endOf(part.syllables, line.endTimeMs), spicy(part.syllables),
                        !"lead".equals(part.role), part.speaker, "lead".equals(part.role) ? line.translationText : ""));
            }
            if (!hasParts) {
                out.add(new Line(line.text, line.startTimeMs, line.endTimeMs, spicy(line.syllables), false, line.speaker, line.translationText));
            }
        }
        return out;
    }

    private static long endOf(List<LyricsLine.Syllable> syllables, long fallback) {
        return syllables.isEmpty() ? fallback : syllables.get(syllables.size() - 1).endTimeMs;
    }

    private static List<Syllable> spicy(List<LyricsLine.Syllable> from) {
        List<Syllable> out = new ArrayList<>(from.size());
        for (LyricsLine.Syllable syllable : from) out.add(new Syllable(syllable.text, syllable.startTimeMs, syllable.endTimeMs));
        return out;
    }

    private static TrackSnapshot track(String title, String artist, String album, String spotifyId, long durationMs,
                                       long positionMs, boolean playing, Bitmap artwork) {
        String mediaId = spotifyId == null || spotifyId.isEmpty() ? "" : "spotify:track:" + spotifyId;
        return new TrackSnapshot(title, artist, album, SPOTIFY, mediaId, "", durationMs, positionMs,
                SystemClock.elapsedRealtime(), playing ? 1f : 0f, playing, artwork, "");
    }

    // ---- lyrics source: ivLyrics' whole lookup (its sources and community syncs) ----

    /** Looks the song up the way ivLyrics does; null when it has nothing. Blocks: call off the main thread. */
    public static Lookup lookUp(Context context, String title, String artist, String album, long durationMs,
                                String spotifyId, long timeoutMs) {
        if (!supported()) return null;
        init(context);
        TrackSnapshot track = track(title, artist, album, spotifyId, durationMs, 0L, false, null);
        if (!track.hasUsableMetadata()) return null;
        LyricsRepository repository = new LyricsRepository(context);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<LyricsResult> found = new AtomicReference<>();
        repository.loadLyrics(track, new LyricsRepository.Callback() {
            @Override public void onLyricsLoaded(String trackKey, LyricsResult result) {
                if (result != null && !result.loading && !result.lines.isEmpty()) {
                    found.compareAndSet(null, result);
                    done.countDown();
                } else if (result != null && !result.loading) {
                    done.countDown();
                }
            }
            @Override public void onLyricsError(String trackKey, String message) { done.countDown(); }
            @Override public void onLyricsProviderLoading(String trackKey, String providerName) {}
            @Override public void onLyricsLog(String trackKey, String message) {}
            @Override public void onLyricsArtworkLoaded(String trackKey, Bitmap artwork, String artworkKey) {}
            @Override public void onLyricsMetadataResolved(String trackKey, String isrc, String spotifyTrackId) {}
        });
        try {
            done.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LyricsResult result = found.get();
        if (result == null) return null;
        boolean timed = false;
        for (LyricsLine line : result.lines) if (line.startTimeMs > 0L) { timed = true; break; }
        return new Lookup(lines(result), result.karaoke, timed, result.providerLabel == null ? "" : result.providerLabel);
    }

    // ---- motion lyrics: ivLyrics' moving-type page ----

    /** Whether these lyrics have the word timing motion lyrics needs. */
    public static boolean motionCanPlay(List<Line> lines) {
        return MotionLyricsSong.canPlay(result(lines, true));
    }

    /** ivLyrics' motion lyrics stage, showing at once, without its close button. */
    public static View createMotion(Context context) {
        init(context);
        SharedPreferences cache = context.getSharedPreferences("ivlyrics_motion", Context.MODE_PRIVATE);
        MotionLyricsModeView view = new MotionLyricsModeView(context, new MotionLyricsModeView.Listener() {
            @Override public void onClose() {}
            @Override public void onDirectionRequested(String prompt, MotionLyricsModeView.DirectionCallback callback) {
                callback.onAnswer(null, "AI direction is off");
            }
            @Override public String onCacheRead(String key) { return cache.getString(key, null); }
            @Override public void onCacheWrite(String key, String value) { cache.edit().putString(key, value).apply(); }
            @Override public void onBackgroundFrame(float rotate, float scale, float x, float y) {}
            @Override public void onLog(String message) {}
        });
        view.setUiText("Close", "This song has no word timing", "", "");
        view.setAiAvailable(false);
        // Its own touch layer and close button belong to ivLyrics' full-screen mode; the host has its own.
        // Children: the page, a touch layer, the notice, the close button.
        if (view.getChildCount() >= 4) {
            view.removeViewAt(3);
            view.removeViewAt(1);
        }
        view.show(false);
        return view;
    }

    public static void motionSong(View view, String spotifyId, String key, String title, String artist, List<Line> lines) {
        if (!(view instanceof MotionLyricsModeView)) return;
        LyricsResult result = result(lines, true);
        String uri = MotionLyricsSong.uri(spotifyId, key);
        ((MotionLyricsModeView) view).setSong(uri, MotionLyricsSong.json(uri, title, artist, "#ffffff", "", result), true);
    }

    public static void motionPlayback(View view, long positionMs, boolean playing) {
        if (view instanceof MotionLyricsModeView) ((MotionLyricsModeView) view).setPlayback(positionMs, playing, 1f);
    }

    // ---- vinyl mode ----

    public interface VinylControls {
        void close();
        void playPause();
        void seek(long positionMs);
        void pause();
    }

    public static View createVinyl(Context context, VinylControls controls) {
        init(context);
        VinylPlayerModeView view = new VinylPlayerModeView(context);
        view.setListener(new VinylPlayerModeView.Listener() {
            @Override public void onClose() { controls.close(); }
            @Override public void onTogglePlayback() { controls.playPause(); }
            @Override public void onSeek(long positionMs) { controls.seek(positionMs); }
            @Override public void onStopPlayback() { controls.pause(); }
            @Override public void onShowTmi() {}
        });
        view.setUiText("Vinyl", "Close", "Tap the record to play or pause", "Move the tonearm to seek", "");
        AiLyricsSettings.Snapshot settings = new AiLyricsSettings(context).snapshot();
        view.setCustomization(settings.vinyl, settings.typography);
        view.show(true);
        return view;
    }

    public static void vinylTrack(View view, String title, String artist, String album, String spotifyId,
                                  long durationMs, long positionMs, boolean playing, Bitmap artwork, boolean changed) {
        if (!(view instanceof VinylPlayerModeView)) return;
        ((VinylPlayerModeView) view).setTrack(track(title, artist, album, spotifyId, durationMs, positionMs, playing, artwork), artwork, changed);
    }

    public static void vinylPlayback(View view, long positionMs, long durationMs, boolean playing) {
        if (view instanceof VinylPlayerModeView) ((VinylPlayerModeView) view).setPlayback(positionMs, durationMs, playing);
    }

    /** The line being sung under the record (null for none), with its translation under it. */
    public static void vinylLine(View view, Line line, long positionMs, boolean playing) {
        if (!(view instanceof VinylPlayerModeView)) return;
        List<MainLyricPreviewView.PreviewLine> rows = new ArrayList<>();
        long start = 0L;
        long end = 0L;
        if (line != null) {
            rows.add(new MainLyricPreviewView.PreviewLine(line.text, true, syllables(line.syllables)));
            if (!line.translation.isEmpty()) rows.add(new MainLyricPreviewView.PreviewLine(line.translation, false));
            start = line.startMs;
            end = line.endMs;
        }
        ((VinylPlayerModeView) view).lyricView().setPreview(rows, positionMs, start, end, playing);
    }

    /** A frame for the line's karaoke fill; true while it wants more. */
    public static boolean vinylFrame(View view, long positionMs, long frameTimeNanos) {
        if (!(view instanceof VinylPlayerModeView)) return false;
        MainLyricPreviewView lyrics = ((VinylPlayerModeView) view).lyricView();
        lyrics.setPlaybackFrame(positionMs, frameTimeNanos);
        return lyrics.needsHostFrames();
    }

    // ---- YouTube music video background ----

    public interface VideoListener {
        /** A video was found for the song (true), or none (false). */
        void onVideo(boolean found);
    }

    private static final class VideoHolder extends android.widget.FrameLayout {
        final YouTubeBackgroundView video;
        final YouTubeBackgroundRepository repository;
        String requestKey = "";
        long firstLyricMs;

        VideoHolder(Context context) {
            super(context);
            video = new YouTubeBackgroundView(context);
            repository = new YouTubeBackgroundRepository(context);
            addView(video, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            video.setBackgroundSettings(new AiLyricsSettings.BackgroundSettings(
                    AiLyricsSettings.BACKGROUND_MODE_VIDEO, 70, 0, false, false, "#000000", 120));
            video.setVideoBackgroundEnabled(true);
        }
    }

    public static View createVideo(Context context) {
        init(context);
        return new VideoHolder(context);
    }

    public static void videoSong(View view, String title, String artist, String album, String spotifyId, long durationMs,
                                 List<Line> lines, VideoListener listener) {
        if (!(view instanceof VideoHolder)) return;
        VideoHolder holder = (VideoHolder) view;
        TrackSnapshot track = track(title, artist, album, spotifyId, durationMs, 0L, true, null);
        String key = track.stableKey();
        if (key.equals(holder.requestKey)) return;
        holder.requestKey = key;
        holder.video.clearVideo();
        holder.firstLyricMs = lines.isEmpty() ? 0L : lines.get(0).startMs;
        holder.repository.load(key, track, result(lines, false), new YouTubeBackgroundRepository.Callback() {
            @Override public void onYouTubeBackgroundLoaded(String requestKey, YouTubeBackgroundRepository.VideoInfo info, boolean fromCache) {
                holder.post(() -> {
                    if (!requestKey.equals(holder.requestKey)) return;
                    holder.video.loadVideo(info);
                    listener.onVideo(true);
                });
            }
            @Override public void onYouTubeBackgroundError(String requestKey, String message) {
                holder.post(() -> { if (requestKey.equals(holder.requestKey)) listener.onVideo(false); });
            }
            @Override public void onYouTubeBackgroundLog(String requestKey, String message) {}
        });
    }

    public static void videoPlayback(View view, long positionMs, boolean playing) {
        if (!(view instanceof VideoHolder)) return;
        VideoHolder holder = (VideoHolder) view;
        holder.video.setPlaybackState(positionMs, playing, holder.firstLyricMs, 0);
    }

    // ---- furigana ----

    public interface FuriganaCallback {
        /** One entry per line given: the line as ruby markup (<ruby>漢字<rt>かんじ</rt></ruby>), or empty. */
        void onFurigana(String[] rubyLines);
    }

    /** ivLyrics' readings for the kanji in [lines]; answers on the main thread, not at all when there's no kanji. */
    public static void furigana(Context context, String title, String artist, long durationMs, List<Line> lines,
                                FuriganaCallback callback) {
        if (!supported()) return;
        init(context);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (furigana == null) furigana = new FuriganaRepository(context.getApplicationContext());
            List<Line> plain = new ArrayList<>(lines.size());
            for (Line line : lines) plain.add(new Line(line.text, line.startMs, line.endMs, line.syllables, false, "", ""));
            LyricsResult base = new LyricsResult(result(plain, false).lines, "Spicy Lyrics", "", false);
            furigana.loadFurigana(track(title, artist, "", "", durationMs, 0L, false, null), base, false,
                    new FuriganaRepository.Callback() {
                        @Override public void onFuriganaLoaded(String trackKey, LyricsResult result) {
                            String[] out = new String[lines.size()];
                            for (int index = 0; index < out.length; index++) {
                                out[index] = index < result.lines.size() ? result.lines.get(index).furiganaText : "";
                                if (out[index] == null) out[index] = "";
                            }
                            callback.onFurigana(out);
                        }
                        @Override public void onFuriganaError(String trackKey, String message) {}
                        @Override public void onFuriganaLog(String trackKey, String message) {}
                    });
        });
    }

    /** Lets go of an ivLyrics view's web pages and players. */
    public static void release(View view) {
        if (view instanceof MotionLyricsModeView) ((MotionLyricsModeView) view).release();
        else if (view instanceof VinylPlayerModeView) ((VinylPlayerModeView) view).release();
        else if (view instanceof VideoHolder) {
            ((VideoHolder) view).video.destroy();
            ((VideoHolder) view).repository.shutdown();
        }
    }
}
