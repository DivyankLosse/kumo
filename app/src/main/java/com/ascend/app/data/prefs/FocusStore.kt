package com.ascend.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ascend.app.domain.ActiveFocus
import com.ascend.app.domain.FinishGuard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(name = "focus")

class FocusStore(private val context: Context) {
    private object Keys {
        val active = stringPreferencesKey("active_focus")
        val lastFinished = stringPreferencesKey("last_finished_key")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val active: Flow<ActiveFocus?> = context.focusDataStore.data.map { decode(it[Keys.active]) }

    suspend fun current(): ActiveFocus? = active.first()

    suspend fun save(state: ActiveFocus) {
        context.focusDataStore.edit { it[Keys.active] = json.encodeToString(ActiveFocus.serializer(), state) }
    }

    suspend fun clear() {
        context.focusDataStore.edit { it.remove(Keys.active) }
    }

    /** Atomically records [sessionKey] as finished and clears the active session; false if it was already finished. */
    suspend fun claimFinish(sessionKey: String): Boolean {
        var claimed = false
        context.focusDataStore.edit {
            claimed = FinishGuard.canFinish(it[Keys.lastFinished], sessionKey)
            if (claimed) it[Keys.lastFinished] = sessionKey
            it.remove(Keys.active)
        }
        return claimed
    }

    private fun decode(raw: String?): ActiveFocus? =
        raw?.let { runCatching { json.decodeFromString(ActiveFocus.serializer(), it) }.getOrNull() }
}
