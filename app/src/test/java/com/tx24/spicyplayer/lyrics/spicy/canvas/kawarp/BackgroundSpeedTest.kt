package com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp

import com.google.gson.JsonParser
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackgroundSpeedTest {
    private val analysis = AudioAnalysis(
        tempo = 120f,
        loudness = -10f,
        sections = listOf(
            AudioAnalysis.Section(start = 0f, duration = 10f, loudness = 0f, tempo = 120f),
            AudioAnalysis.Section(start = 10f, duration = 10f, loudness = -40f, tempo = 60f),
        ),
        beats = listOf(
            AudioAnalysis.Beat(start = 1f, duration = 0.5f, confidence = 0.9f),
            AudioAnalysis.Beat(start = 1.5f, duration = 0.5f, confidence = 0.2f),
        ),
    )

    @Test fun loudnessFactorRunsFromHalfToOnePointTwo() {
        assertEquals(0.5f, BackgroundSpeed.loudnessFactor(-60f), 1e-6f)
        assertEquals(0.5f, BackgroundSpeed.loudnessFactor(-40f), 1e-6f)
        assertEquals(1.2f, BackgroundSpeed.loudnessFactor(0f), 1e-6f)
    }

    @Test fun sectionTempoAndLoudnessSetTheSpeed() {
        // 120 BPM at 0 dB: 1 × 1.2.
        assertEquals(1.2f, BackgroundSpeed.at(5f, analysis), 1e-5f)
        // 60 BPM at -40 dB: 0.5 × 0.5.
        assertEquals(0.25f, BackgroundSpeed.at(15f, analysis), 1e-5f)
    }

    @Test fun outsideEverySectionTheTrackStandsIn() {
        // 120 BPM at -10 dB: 1 × (0.5 + 0.75 × 0.7).
        assertEquals(1.025f, BackgroundSpeed.at(25f, analysis), 1e-5f)
    }

    @Test fun aConfidentBeatPulsesAndDecays() {
        // On the beat: +1.5 × 0.9, capped at 3.
        assertEquals(1.2f + 1.35f, BackgroundSpeed.at(1f, analysis), 1e-5f)
        val halfway = BackgroundSpeed.at(1.25f, analysis)
        assertEquals(1.2f + 1.35f * kotlin.math.exp(-2.5f), halfway, 1e-5f)
        // Too unsure a beat adds nothing.
        assertEquals(1.2f, BackgroundSpeed.at(1.6f, analysis), 1e-5f)
    }

    @Test fun compactCopyReadsBack() {
        val copy = AudioAnalysis.fromJson(JsonParser.parseString(analysis.toJson().toString()).asJsonObject)
        assertNotNull(copy)
        assertEquals(BackgroundSpeed.at(1.25f, analysis), BackgroundSpeed.at(1.25f, copy!!), 1e-6f)
        assertEquals(2, copy.sections.size)
    }

    @Test fun readsSpotifysDocument() {
        val json = """{"track":{"tempo":90.5,"loudness":-7.2,"duration":180},
            "sections":[{"start":0,"duration":30,"confidence":1,"loudness":-8,"tempo":90,"key":1,"mode":1,"time_signature":4}],
            "beats":[{"start":0.4,"duration":0.66,"confidence":0.8}],"segments":[],"bars":[],"tatums":[]}"""
        val parsed = AudioAnalysis.fromSpotify(JsonParser.parseString(json).asJsonObject)
        assertNotNull(parsed)
        assertEquals(90.5f, parsed!!.tempo, 1e-6f)
        assertEquals(1, parsed.beats.size)
    }
}
