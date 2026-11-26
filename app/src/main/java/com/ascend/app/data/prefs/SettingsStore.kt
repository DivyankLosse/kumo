package com.ascend.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ascend.app.work.QuickActionLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val haptics: Boolean = true,
    val remindersEnabled: Boolean = false,
    val enabledReminders: Set<String> = QuickActionLogic.DEFAULT_ENABLED_REMINDERS,
    val quietStartMin: Int = QuickActionLogic.DEFAULT_QUIET_START,
    val quietEndMin: Int = QuickActionLogic.DEFAULT_QUIET_END,
    val coachProxyUrl: String = "",
    val glassMl: Int = 250,
    val rewardsEnabled: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val haptics = booleanPreferencesKey("haptics")
        val reminders = booleanPreferencesKey("reminders_enabled")
        val enabled = stringPreferencesKey("enabled_reminders")
        val quietStart = intPreferencesKey("quiet_start_min")
        val quietEnd = intPreferencesKey("quiet_end_min")
        val coachUrl = stringPreferencesKey("coach_proxy_url")
        val glass = intPreferencesKey("glass_ml")
        val rewards = booleanPreferencesKey("rewards_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            haptics = p[Keys.haptics] ?: true,
            remindersEnabled = p[Keys.reminders] ?: false,
            enabledReminders = p[Keys.enabled]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: QuickActionLogic.DEFAULT_ENABLED_REMINDERS,
            quietStartMin = p[Keys.quietStart] ?: QuickActionLogic.DEFAULT_QUIET_START,
            quietEndMin = p[Keys.quietEnd] ?: QuickActionLogic.DEFAULT_QUIET_END,
            coachProxyUrl = p[Keys.coachUrl] ?: "",
            glassMl = p[Keys.glass] ?: 250,
            rewardsEnabled = p[Keys.rewards] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setHaptics(on: Boolean) = context.dataStore.edit { it[Keys.haptics] = on }
    suspend fun setRemindersEnabled(on: Boolean) = context.dataStore.edit { it[Keys.reminders] = on }
    suspend fun setReminderEnabled(key: String, enabled: Boolean) = context.dataStore.edit { p ->
        val set = p[Keys.enabled]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet()
            ?: QuickActionLogic.DEFAULT_ENABLED_REMINDERS.toMutableSet()
        if (enabled) set += key else set -= key
        p[Keys.enabled] = set.joinToString(",")
    }
    suspend fun setQuietHours(startMin: Int, endMin: Int) = context.dataStore.edit {
        it[Keys.quietStart] = startMin
        it[Keys.quietEnd] = endMin
    }
    suspend fun setCoachUrl(url: String) = context.dataStore.edit { it[Keys.coachUrl] = url.trim() }
    suspend fun setGlassMl(ml: Int) = context.dataStore.edit { it[Keys.glass] = ml }
    suspend fun setRewardsEnabled(on: Boolean) = context.dataStore.edit { it[Keys.rewards] = on }
}
