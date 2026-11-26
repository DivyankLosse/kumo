package com.ascend.app.data.coach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class CoachMessage(val role: String, val content: String)

@Serializable
data class CoachRequest(val messages: List<CoachMessage>, val context: String)

@Serializable
data class CoachResponse(val reply: String)

sealed interface CoachResult {
    data class Reply(val text: String) : CoachResult
    data object NotConfigured : CoachResult
    data class Failed(val reason: String) : CoachResult
}

/**
 * Talks only to the user's own backend proxy (POST {url}/v1/coach). The APK holds no model keys,
 * and nothing is ever synthesised locally: no proxy or an error means no reply.
 */
class CoachClient(private val json: Json = Json { ignoreUnknownKeys = true }) {

    suspend fun ask(proxyUrl: String, request: CoachRequest): CoachResult = withContext(Dispatchers.IO) {
        if (proxyUrl.isBlank()) return@withContext CoachResult.NotConfigured
        val endpoint = proxyUrl.trimEnd('/') + "/v1/coach"
        if (!endpoint.startsWith("https://") && !endpoint.startsWith("http://10.0.2.2") && !endpoint.startsWith("http://localhost")) {
            return@withContext CoachResult.Failed("Proxy URL must use https://")
        }
        runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                conn.outputStream.use { it.write(json.encodeToString(CoachRequest.serializer(), request).toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) return@runCatching CoachResult.Failed("Coach proxy returned HTTP $code")
                val body = conn.inputStream.use { it.readBytes().decodeToString() }
                val reply = json.decodeFromString(CoachResponse.serializer(), body).reply
                if (reply.isBlank()) CoachResult.Failed("Coach proxy sent an empty reply") else CoachResult.Reply(reply)
            } finally {
                conn.disconnect()
            }
        }.getOrElse { CoachResult.Failed(it.message ?: "Network error") }
    }
}
