package com.tx24.spicyplayer.translation

import android.os.Build
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tx24.spicyplayer.network.data.awaitResponse
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * What a language model is asked: the lines go as a JSON array, with the whole song for meaning,
 * and come back as a JSON object holding the song's language and one line (or null, for a line
 * already in the target language) per line sent.
 */
internal object AiPrompt {
    fun system(target: String): String {
        val language = "${TranslationLanguages.name(target)} ($target)"
        return "You translate song lyrics into $language. You are given the song's lines as a JSON array of strings. " +
            "Translate each line on its own so it stays lined up with its original, keeping its meaning, tone and imagery " +
            "natural in $language. Don't add, merge, split, explain or censor lines. A line that is already in $language, " +
            "or has nothing to translate, is null. Reply with only a JSON object, no code fence: " +
            "{\"language\": \"<ISO 639-1 code of the song's main language>\", \"lines\": [<one string or null per line, in order>]}. " +
            "\"lines\" has exactly as many items as the array you were given."
    }

    /** The user turn: the song for meaning (when [withContext]), then the lines to translate. */
    fun user(request: TranslatorRequest, withContext: Boolean = true): String = buildString {
        if (withContext && request.context.isNotBlank()) append("The whole song, for meaning:\n").append(request.context).append("\n\n")
        append("Lines to translate (").append(request.lines.size).append("):\n")
        append(JsonArray().apply { request.lines.forEach(::add) })
    }

    /**
     * The model's answer as a response: the JSON object, wherever it sits in the reply (models
     * wrap it in a code fence or a sentence regardless). A reply that isn't one is an error.
     */
    fun parse(reply: String): TranslatorResponse {
        // Reasoning models may think out loud first, braces and all.
        val text = reply.replace(THINKING, "").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        val root = if (start >= 0 && end > start) {
            runCatching { JsonParser.parseString(text.substring(start, end + 1)).asJsonObject }.getOrNull()
        } else null
        // Some answer with the lines alone, as a bare array.
        val lines = root?.getAsJsonArray("lines") ?: bareLines(text)
            ?: throw IOException("The AI didn't reply with translations" + said(text) + ".")
        return TranslatorResponse(
            lines.map { item -> TranslationEntry(item.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf(String::isNotBlank)) },
            languageCode(root?.get("language")?.takeIf { it.isJsonPrimitive }?.asString),
        )
    }

    private fun bareLines(text: String): JsonArray? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching { JsonParser.parseString(text.substring(start, end + 1)).asJsonArray }.getOrNull()
    }

    /** The start of a reply that wasn't translations, for the debug report. */
    private fun said(text: String): String =
        if (text.isBlank()) " (it replied with nothing)" else " (it said: \"${text.take(120).replace('\n', ' ')}\")"

    private val THINKING = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
}

/** How an AI service turned a request down, as something the user can act on. */
internal fun aiHttpFailure(response: okhttp3.Response, service: String, model: String): Exception {
    val detail = aiErrorMessage(runCatching { response.body.string() }.getOrDefault(""))
    return when (response.code) {
        429 -> ProviderBusy(retryAfterMs(response), detail)
        401, 403 -> TranslationFailure("$service didn't accept your API key. Check it in Translation settings.")
        404 -> TranslationFailure("$service has no model called \"$model\". Check the model in Translation settings.")
        // Groq says this when one request is over the model's per-minute token allowance on its own.
        413 -> TranslationFailure("These lyrics are too long for $model on $service" + detail?.let { ": $it" }.orEmpty())
        in 500..599 -> ProviderBusy(retryAfterMs(response), detail)
        else -> IOException("$service couldn't translate these lyrics (HTTP ${response.code})" + detail?.let { ": $it" }.orEmpty() + ".")
    }
}

/** The message in an AI service's error body (`{"error": {"message": …}}`, OpenAI's shape and Gemini's), cut short. */
internal fun aiErrorMessage(body: String): String? = runCatching {
    JsonParser.parseString(body).asJsonObject.getAsJsonObject("error").get("message").asString
}.getOrNull()?.trim()?.takeIf(String::isNotEmpty)?.let { if (it.length > 200) it.take(200) + "…" else it }

private val JSON = "application/json".toMediaType()

/** Any service speaking OpenAI's chat completions API: OpenAI, OpenRouter, Groq, Mistral, a local Ollama… */
class OpenAiTranslator(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val key: String,
    private val model: String,
) : Translator {
    override val provider = TranslationProvider.OpenAi
    @Volatile private var jsonMode = true

    override suspend fun translate(request: TranslatorRequest): TranslatorResponse {
        val http = openAiRequest(request, baseUrl, key, model, jsonMode = jsonMode)
        val service = http.url.host
        val body = client.newCall(http).awaitResponse().use { response ->
            // Not every OpenAI-compatible server knows JSON mode: once turned down, ask without it.
            if (response.code == 400 && jsonMode) {
                jsonMode = false
                return translate(request)
            }
            if (!response.isSuccessful) throw aiHttpFailure(response, service, model)
            response.body.string()
        }
        val reply = runCatching {
            JsonParser.parseString(body).asJsonObject.getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message").get("content").asString
        }.getOrElse { throw IOException("$service returned an invalid response.") }
        return AiPrompt.parse(reply)
    }
}

/** The chat completions endpoint under [baseUrl] (`…/v1`), or the URL itself when it already is one. */
internal fun openAiEndpoint(baseUrl: String): okhttp3.HttpUrl? {
    val trimmed = baseUrl.trim().trimEnd('/')
    val full = if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    return full.toHttpUrlOrNull()
}

internal fun openAiRequest(request: TranslatorRequest, baseUrl: String, key: String, model: String, jsonMode: Boolean = true): Request {
    val url = openAiEndpoint(baseUrl) ?: throw TranslationFailure("Add the AI service's address in Translation settings.")
    // Android lets this app reach only https addresses.
    if (!url.isHttps) throw TranslationFailure("The AI service's address has to start with https://.")
    if (model.isBlank()) throw TranslationFailure("Add the AI model to use in Translation settings.")
    val body = JsonObject().apply {
        addProperty("model", model.trim())
        // No temperature: reasoning models (OpenAI's GPT-5 family among them) refuse any but their own.
        add("messages", JsonArray().apply {
            add(JsonObject().apply { addProperty("role", "system"); addProperty("content", AiPrompt.system(request.target)) })
            add(JsonObject().apply { addProperty("role", "user"); addProperty("content", AiPrompt.user(request)) })
        })
        if (jsonMode) add("response_format", JsonObject().apply { addProperty("type", "json_object") })
    }
    return Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).apply {
        // A local server needs no key.
        if (key.isNotBlank()) header("Authorization", "Bearer ${key.trim()}")
    }.build()
}

/** Google's Gemini API, with the user's key from Google AI Studio. */
class GeminiTranslator(private val client: OkHttpClient, private val key: String, private val model: String) : Translator {
    override val provider = TranslationProvider.Gemini

    override suspend fun translate(request: TranslatorRequest): TranslatorResponse {
        if (key.isBlank()) throw TranslationFailure("Add your Gemini API key in Translation settings.")
        val body = client.newCall(geminiRequest(request, key, model)).awaitResponse().use { response ->
            if (!response.isSuccessful) throw aiHttpFailure(response, "Gemini", model)
            response.body.string()
        }
        val reply = runCatching {
            JsonParser.parseString(body).asJsonObject.getAsJsonArray("candidates")[0].asJsonObject
                .getAsJsonObject("content").getAsJsonArray("parts")
                .joinToString("") { part -> part.asJsonObject.get("text")?.asString.orEmpty() }
        }.getOrElse { throw IOException("Gemini returned no translation (it may have blocked these lyrics).") }
        return AiPrompt.parse(reply)
    }
}

internal fun geminiRequest(request: TranslatorRequest, key: String, model: String): Request {
    val name = model.trim().removePrefix("models/").ifBlank { GeminiModels.DEFAULT }
    val url = "https://generativelanguage.googleapis.com/v1beta/models/$name:generateContent".toHttpUrlOrNull()
        ?: throw TranslationFailure("\"$model\" isn't a Gemini model name. Check it in Translation settings.")
    val body = JsonObject().apply {
        add("systemInstruction", JsonObject().apply {
            add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", AiPrompt.system(request.target)) }) })
        })
        add("contents", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "user")
                add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", AiPrompt.user(request)) }) })
            })
        })
        add("generationConfig", JsonObject().apply {
            addProperty("temperature", 0.2)
            addProperty("responseMimeType", "application/json")
        })
    }
    // The key goes in a header, never the URL, so it stays out of logs.
    return Request.Builder().url(url).header("x-goog-api-key", key.trim()).post(body.toString().toRequestBody(JSON)).build()
}

object GeminiModels {
    /** A fast, cheap model that translates well; the model is the user's to change as Google renames them. */
    const val DEFAULT = "gemini-3.5-flash-lite"
}

/**
 * Gemini Nano through ML Kit's Prompt API, on phones whose AICore has it: nothing leaves the
 * phone. It reads under 4,000 tokens, so it gets the lines without the whole song, a verse or
 * two at a time ([TranslationProvider.textLimit]).
 */
class GeminiNanoTranslator : Translator {
    override val provider = TranslationProvider.GeminiNano

    override suspend fun translate(request: TranslatorRequest): TranslatorResponse {
        if (OnDeviceModel.status() != OnDeviceModel.Status.Ready) {
            throw TranslationFailure("Gemini Nano isn't ready on this phone. Check it in Translation settings.")
        }
        val reply = try {
            OnDeviceModel.generate(AiPrompt.system(request.target), AiPrompt.user(request, withContext = false))
        } catch (error: com.google.mlkit.genai.common.GenAiException) {
            throw TranslationFailure("Gemini Nano couldn't translate these lyrics (${error.errorCode}).")
        }
        return AiPrompt.parse(reply)
    }
}

/** The phone's Gemini Nano: whether it has it, fetching it, and asking it. Only reached on Android 8+. */
object OnDeviceModel {
    enum class Status(val label: String) {
        Ready("Ready"), Downloadable("Not downloaded yet"), Downloading("Downloading…"), Unavailable("Not available on this phone"),
    }

    private val model by lazy { com.google.mlkit.genai.prompt.Generation.getClient() }

    suspend fun status(): Status {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return Status.Unavailable
        return try {
            when (model.checkStatus()) {
                com.google.mlkit.genai.common.FeatureStatus.AVAILABLE -> Status.Ready
                com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE -> Status.Downloadable
                com.google.mlkit.genai.common.FeatureStatus.DOWNLOADING -> Status.Downloading
                else -> Status.Unavailable
            }
        } catch (error: Exception) {
            // No AICore, a locked-down or unlocked-bootloader phone, or AICore still setting up.
            Status.Unavailable
        }
    }

    /** Downloads the model, reporting bytes so far; true once it's there. */
    suspend fun download(progress: (Long) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        var done = false
        try {
            model.download().collect { status ->
                when (status) {
                    is com.google.mlkit.genai.common.DownloadStatus.DownloadProgress -> progress(status.totalBytesDownloaded)
                    is com.google.mlkit.genai.common.DownloadStatus.DownloadCompleted -> done = true
                    is com.google.mlkit.genai.common.DownloadStatus.DownloadFailed -> done = false
                    else -> Unit
                }
            }
        } catch (error: Exception) {
            return false
        }
        return done
    }

    suspend fun generate(system: String, prompt: String): String {
        // Not every phone's Nano takes a system instruction, so it leads the prompt instead.
        val request = com.google.mlkit.genai.prompt.generateContentRequest(com.google.mlkit.genai.prompt.TextPart("$system\n\n$prompt")) {
            temperature = 0.2f
            maxOutputTokens = 2_000
        }
        return model.generateContent(request).candidates.firstOrNull()?.text
            ?: throw IOException("Gemini Nano returned no translation.")
    }
}
