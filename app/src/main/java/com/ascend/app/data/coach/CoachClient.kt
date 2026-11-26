package com.ascend.app.data.coach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks only to the user's own backend proxy (POST {url}/v1/coach/{task}). The APK holds no model keys,
 * and nothing is ever synthesised locally: no proxy or an error means no reply.
 */
class CoachClient(
    private val tokens: InstallTokenStore = NoInstallToken,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {

    suspend fun ask(proxyUrl: String, task: CoachTask, request: CoachRequest): CoachResult = withContext(Dispatchers.IO) {
        if (proxyUrl.isBlank()) return@withContext CoachResult.NotConfigured
        val endpoint = endpointFor(proxyUrl, task)
            ?: return@withContext CoachResult.Failed("Proxy URL must use https://")
        runCatching {
            val installToken = tokens.token()
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Schema-Version", COACH_SCHEMA_VERSION.toString())
                if (!installToken.isNullOrBlank()) setRequestProperty("X-Install-Token", installToken)
            }
            try {
                conn.outputStream.use { it.write(json.encodeToString(CoachRequest.serializer(), request).toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) return@runCatching CoachResult.Failed("Coach proxy returned HTTP $code")
                val body = conn.inputStream.use { it.readBytes().decodeToString() }
                val response = json.decodeFromString(CoachResponse.serializer(), body)
                when {
                    response.schemaVersion != COACH_SCHEMA_VERSION -> CoachResult.Failed("Unsupported coach schema ${response.schemaVersion}")
                    response.items.isEmpty() -> CoachResult.Failed("Coach proxy sent no items")
                    else -> CoachResult.Reply(response)
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { CoachResult.Failed(it.message ?: "Network error") }
    }

    companion object {
        /** Null unless the URL is https (or http to the dev hosts 10.0.2.2 / localhost). */
        fun endpointFor(proxyUrl: String, task: CoachTask): String? {
            val endpoint = proxyUrl.trim().trimEnd('/') + "/v1/coach/" + task.path
            val allowed = endpoint.startsWith("https://") || endpoint.startsWith("http://10.0.2.2") || endpoint.startsWith("http://localhost")
            return endpoint.takeIf { allowed }
        }
    }
}
