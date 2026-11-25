package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The desktop's SpicyBridge: the same lines and song model as the Android one, for motion lyrics'
 * page (com.tx24.spicyplayer.desktop.motion.MotionPage). ivLyrics' own lookup, vinyl mode and video
 * background are Android views and aren't here.
 */
public final class SpicyBridge {
    private SpicyBridge() {}

    /** Motion lyrics run where the page's engine is available. */
    public static boolean supported() {
        return com.tx24.spicyplayer.desktop.motion.MotionEngine.INSTANCE.getAvailable();
    }

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
        public final List<Syllable> syllables;
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

    public interface VinylControls {
        void close();
        void playPause();
        void seek(long positionMs);
        void pause();
    }

    public interface FuriganaCallback {
        void onFurigana(String[] rubyLines);
    }

    /** ivLyrics' furigana service is part of its Android app: no readings here. */
    public static void furigana(Object context, String title, String artist, long durationMs, List<Line> lines, FuriganaCallback callback) {}

    private static List<LyricsLine.Syllable> syllables(List<Syllable> from) {
        List<LyricsLine.Syllable> out = new ArrayList<>(from.size());
        for (Syllable syllable : from) out.add(new LyricsLine.Syllable(syllable.text, syllable.startMs, syllable.endMs));
        return out;
    }

    /** Spicy's lines as ivLyrics' result: background vocals ride on the lead line before them. */
    public static LyricsResult result(List<Line> lines, boolean wordSynced) {
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

    public static boolean motionCanPlay(List<Line> lines) {
        return MotionLyricsSong.canPlay(result(lines, true));
    }

    /** The song's URI and JSON as the page takes them ([themeId] empty for ivLyrics' own direction). */
    public static String[] motionSong(String spotifyId, String key, String title, String artist, String accent,
                                      List<Line> lines, String themeId, String themeJson) {
        LyricsResult result = result(lines, true);
        boolean themed = themeId != null && !themeId.isEmpty() && themeJson != null && !themeJson.isEmpty();
        String uri = themed
                ? MotionLyricsSong.uri(null, (spotifyId == null ? "" : spotifyId) + "|" + key + "|" + themeId)
                : MotionLyricsSong.uri(spotifyId, key);
        String colour = accent == null || accent.isEmpty() ? "" : accent;
        return new String[] {uri, MotionLyricsSong.json(uri, title, artist, colour, "", result), themed ? themeJson : "null"};
    }
}
