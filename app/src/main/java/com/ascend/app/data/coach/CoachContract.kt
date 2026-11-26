package com.ascend.app.data.coach

import kotlinx.serialization.Serializable

enum class CoachTask(val path: String) {
    WEEKLY_REVIEW("weekly_review"),
    ROADMAP_PLAN("roadmap_plan"),
    REFLECTION_PROMPTS("reflection_prompts"),
}

const val COACH_SCHEMA_VERSION = 1
const val COACH_PROMPT_VERSION = "kumo-coach@1"

@Serializable
data class Fact(val id: String, val kind: String, val value: Double, val unit: String, val period: String)

@Serializable
data class CoachConstraints(val sleepStart: String, val sleepEnd: String)

@Serializable
data class CoachRequest(
    val promptVersion: String,
    val locale: String,
    val facts: List<Fact>,
    val constraints: CoachConstraints,
)

@Serializable
data class CoachItem(val type: String, val text: String, val cites: List<String> = emptyList())

@Serializable
data class CoachResponse(
    val schemaVersion: Int,
    val model: String,
    val promptVersion: String,
    val items: List<CoachItem>,
    val latencyMs: Long = 0,
)

sealed interface CoachResult {
    data class Reply(val response: CoachResponse) : CoachResult
    data object NotConfigured : CoachResult
    data class Failed(val reason: String) : CoachResult
}

/** Source of the per-install token; real issuance (Play Integrity) comes later, so it may be null. */
fun interface InstallTokenStore {
    suspend fun token(): String?
}

object NoInstallToken : InstallTokenStore {
    override suspend fun token(): String? = null
}
