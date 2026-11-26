package com.ascend.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val haptics: Boolean = true,
    val remindersEnabled: Boolean = false,
    val disabledReminders: Set<String> = emptySet(),
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
        val disabled = stringPreferencesKey("disabled_reminders")
        val coachUrl = stringPreferencesKey("coach_proxy_url")
        val glass = intPreferencesKey("glass_ml")
        val rewards = booleanPreferencesKey("rewards_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            haptics = p[Keys.haptics] ?: true,
            remindersEnabled = p[Keys.reminders] ?: false,
            disabledReminders = p[Keys.disabled]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            coachProxyUrl = p[Keys.coachUrl] ?: "",
            glassMl = p[Keys.glass] ?: 250,
            rewardsEnabled = p[Keys.rewards] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setHaptics(on: Boolean) = context.dataStore.edit { it[Keys.haptics] = on }
    suspend fun setRemindersEnabled(on: Boolean) = context.dataStore.edit { it[Keys.reminders] = on }
    suspend fun setReminderDisabled(key: String, disabled: Boolean) = context.dataStore.edit { p ->
        val set = p[Keys.disabled]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        if (disabled) set += key else set -= key
        p[Keys.disabled] = set.joinToString(",")
    }
    suspend fun setCoachUrl(url: String) = context.dataStore.edit { it[Keys.coachUrl] = url.trim() }
    suspend fun setGlassMl(ml: Int) = context.dataStore.edit { it[Keys.glass] = ml }
    suspend fun setRewardsEnabled(on: Boolean) = context.dataStore.edit { it[Keys.rewards] = on }
}
