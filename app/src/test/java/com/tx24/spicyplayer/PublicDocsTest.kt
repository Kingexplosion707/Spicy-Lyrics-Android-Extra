package com.tx24.spicyplayer

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The tracked docs (`CLAUDE.md`, `docs/`) describe the app, not where its code was ported from.
 * Credits belong in THIRD_PARTY_NOTICES.md and the README; provenance notes stay in the
 * git-ignored local docs. Only files git tracks are checked, so local notes can say anything.
 */
class PublicDocsTest {
    private val provenance = Regex(
        """mild-lyrics|iPixelGalaxy|Pixel's|pixel/|refs/|\bSL\b|SL's|BlendParityTest|make_blend_fixtures|upstream-notes""",
    )

    @Test
    fun `tracked docs carry no provenance notes`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
        assumeTrue("project root not found", root != null)
        val tracked = runCatching {
            val process = ProcessBuilder("git", "ls-files", "--", "CLAUDE.md", "docs")
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readText()
            if (process.waitFor() == 0) out.lines().filter { it.endsWith(".md") } else null
        }.getOrNull()
        assumeTrue("git not available", tracked != null)

        val hits = tracked!!.flatMap { path ->
            File(root, path).readLines().mapIndexedNotNull { i, line ->
                provenance.find(line)?.let { "$path:${i + 1}: ${it.value}" }
            }
        }
        assertTrue("Provenance in tracked docs (move it to the local notes):\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
