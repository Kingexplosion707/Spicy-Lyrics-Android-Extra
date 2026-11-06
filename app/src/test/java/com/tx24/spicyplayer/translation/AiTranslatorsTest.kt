package com.tx24.spicyplayer.translation

import com.google.gson.JsonParser
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslatorsTest {
    private val request = TranslatorRequest(listOf("君の名前", "I love you"), "en", null, "君の名前\nI love you")

    private fun body(http: okhttp3.Request) = JsonParser.parseString(Buffer().also { http.body!!.writeTo(it) }.readUtf8()).asJsonObject

    @Test fun `reads the JSON object wherever the model put it, nulls kept in place`() {
        val reply = "Sure! ```json\n{\"language\": \"ja\", \"lines\": [\"Your name\", null]}\n```"
        val response = AiPrompt.parse(reply)
        assertEquals("ja", response.detectedLanguage)
        assertEquals(listOf("Your name", null), response.lines.map { it?.text })
    }

    @Test fun `a reply without the object is an error, not an empty translation`() {
        assertTrue(runCatching { AiPrompt.parse("I can't help with that.") }.isFailure)
        assertTrue(runCatching { AiPrompt.parse("{\"language\":\"ja\"}") }.isFailure)
    }

    @Test fun `a reasoning model's thinking and a bare array of lines are read too`() {
        val thought = AiPrompt.parse("<think>The user wants {json}. Fine.</think>\n{\"language\":\"ja\",\"lines\":[\"Hi\"]}")
        assertEquals(listOf("Hi"), thought.lines.map { it?.text })
        val bare = AiPrompt.parse("[\"Your name\", null]")
        assertEquals(listOf("Your name", null), bare.lines.map { it?.text })
        assertNull(bare.detectedLanguage)
        // What it said instead goes into the error, for the debug report.
        val error = runCatching { AiPrompt.parse("I can't help with that.") }.exceptionOrNull()?.message.orEmpty()
        assertTrue(error, error.contains("I can't help with that."))
    }

    @Test fun `an AI service's error message is read from its body`() {
        assertEquals("Rate limit reached for model x on tokens per day", aiErrorMessage("{\"error\":{\"message\":\"Rate limit reached for model x on tokens per day\",\"type\":\"tokens\"}}"))
        assertNull(aiErrorMessage("<html>"))
    }

    @Test fun `the user turn carries the song for meaning and the lines as a JSON array`() {
        val user = AiPrompt.user(request)
        assertTrue(user.contains("The whole song"))
        assertTrue(user.endsWith("[\"君の名前\",\"I love you\"]"))
        assertFalse(AiPrompt.user(request, withContext = false).contains("The whole song"))
        assertTrue(AiPrompt.system("ja").contains("Japanese (ja)"))
    }

    @Test fun `OpenAI-compatible requests go to chat completions under the address, key as a bearer`() {
        val http = openAiRequest(request, "https://openrouter.ai/api/v1/", "sk-test", "some/model")
        assertEquals("https://openrouter.ai/api/v1/chat/completions", http.url.toString())
        assertEquals("Bearer sk-test", http.header("Authorization"))
        val json = body(http)
        assertEquals("some/model", json.get("model").asString)
        assertFalse(json.has("temperature"))
        assertEquals("json_object", json.getAsJsonObject("response_format").get("type").asString)
        assertFalse(body(openAiRequest(request, "https://api.groq.com/openai/v1", "k", "m", jsonMode = false)).has("response_format"))
        assertEquals(listOf("system", "user"), json.getAsJsonArray("messages").map { it.asJsonObject.get("role").asString })
        // A full endpoint is taken as it is; a server that needs no key gets none.
        val local = openAiRequest(request, "https://llm.home.example/v1/chat/completions", "", "llama")
        assertEquals("https://llm.home.example/v1/chat/completions", local.url.toString())
        assertNull(local.header("Authorization"))
        // Android won't let the app reach plain http.
        assertTrue(runCatching { openAiRequest(request, "http://192.168.1.5:11434/v1", "", "m") }.exceptionOrNull() is TranslationFailure)
        assertTrue(runCatching { openAiRequest(request, "not a url", "", "m") }.exceptionOrNull() is TranslationFailure)
        assertTrue(runCatching { openAiRequest(request, "https://api.openai.com/v1", "k", " ") }.exceptionOrNull() is TranslationFailure)
    }

    @Test fun `Gemini requests name the model in the path and keep the key out of the URL`() {
        val http = geminiRequest(request, "AIza-test", "models/gemini-x")
        assertEquals("/v1beta/models/gemini-x:generateContent", http.url.encodedPath)
        assertEquals("AIza-test", http.header("x-goog-api-key"))
        assertFalse(http.url.toString().contains("AIza"))
        assertEquals("application/json", body(http).getAsJsonObject("generationConfig").get("responseMimeType").asString)
        assertTrue(geminiRequest(request, "k", "").url.encodedPath.contains(GeminiModels.DEFAULT))
    }

    @Test fun `Gemini Nano gets smaller batches than the cloud models`() {
        val document = TranslationDocument.from("song", "source", (1..40).map { i ->
            TimedLine(i * 10_000L, i * 10_000L + 1_000, listOf(TimedWord("This is a longish lyric line number $i", 0, 1, false)))
        })
        val nano = TranslationBatching.batches(document, TranslationProvider.GeminiNano)
        val cloud = TranslationBatching.batches(document, TranslationProvider.Gemini)
        assertTrue(nano.size > cloud.size)
        assertTrue(nano.all { batch -> batch.sumOf { it.text.length } <= TranslationProvider.GeminiNano.textLimit })
    }

    private class Miscounting : Translator {
        override val provider = TranslationProvider.Gemini
        val sizes = mutableListOf<Int>()
        override suspend fun translate(request: TranslatorRequest): TranslatorResponse {
            sizes += request.lines.size
            // Whole-song requests come back a line short; one verse at a time comes back right.
            val lines = if (request.lines.size > 2) request.lines.drop(1) else request.lines
            return TranslatorResponse(lines.map { TranslationEntry("t:$it") }, "es")
        }
    }

    @Test fun `a miscounted AI reply is asked again verse by verse`() = runBlocking {
        val document = TranslationDocument.from("song", "source", listOf(
            TimedLine(0, 1_000, listOf(TimedWord("uno", 0, 1_000, false))),
            TimedLine(1_000, 2_000, listOf(TimedWord("dos", 1_000, 2_000, false))),
            TimedLine(9_000, 10_000, listOf(TimedWord("tres", 9_000, 10_000, false))),
            TimedLine(10_000, 11_000, listOf(TimedWord("cuatro", 10_000, 11_000, false))),
        ))
        val cache = object : TranslationCache {
            override fun read(key: TranslationKey, lineCount: Int): TranslationResult? = null
            override fun write(result: TranslationResult) {}
        }
        val translator = Miscounting()
        val result = TranslationEngine({ null }, cache, pause = {})
            .translate(document, TranslationPreferences(targetLanguage = "en", provider = TranslationProvider.Gemini, humanTranslations = false), null, translator)
        result as TranslationOutcome.Translated
        assertEquals(listOf(4, 2, 2), translator.sizes)
        assertEquals(listOf("t:uno", "t:dos", "t:tres", "t:cuatro"), result.result.texts)
        assertEquals("Gemini 4", result.result.lineSources())
    }
}
