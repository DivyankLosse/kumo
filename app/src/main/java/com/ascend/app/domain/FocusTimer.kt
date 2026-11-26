package com.ascend.app.domain

import kotlinx.serialization.Serializable

fun interface Clock { fun nowMs(): Long }

val SystemWallClock = Clock { System.currentTimeMillis() }

@Serializable
data class ActiveFocus(
    val sessionKey: String,
    val category: String,
    val taskId: Long?,
    val plannedMinutes: Int,
    val startedAtMs: Long,
    val pausedAtMs: Long?,
    val pausedTotalMs: Long,
) {
    val plannedMs: Long get() = plannedMinutes * 60_000L
    val paused: Boolean get() = pausedAtMs != null
}

/** Wall-clock based so progress is derived from absolute timestamps and survives process death. */
object FocusTimer {
    fun elapsedMs(s: ActiveFocus, now: Long): Long =
        ((s.pausedAtMs ?: now) - s.startedAtMs - s.pausedTotalMs).coerceIn(0L, s.plannedMs)

    fun remainingMs(s: ActiveFocus, now: Long): Long = s.plannedMs - elapsedMs(s, now)

    fun isComplete(s: ActiveFocus, now: Long): Boolean = elapsedMs(s, now) >= s.plannedMs

    fun pause(s: ActiveFocus, now: Long): ActiveFocus = if (s.paused) s else s.copy(pausedAtMs = now)

    fun resume(s: ActiveFocus, now: Long): ActiveFocus {
        val pausedAt = s.pausedAtMs ?: return s
        return s.copy(pausedAtMs = null, pausedTotalMs = s.pausedTotalMs + (now - pausedAt).coerceAtLeast(0L))
    }
}

/** Decides whether a session may be recorded; a key that was already finished is refused. */
object FinishGuard {
    fun canFinish(lastFinishedKey: String?, sessionKey: String): Boolean = lastFinishedKey != sessionKey
}
