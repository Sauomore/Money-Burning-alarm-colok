package com.dsh.burnalarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "burn_alarm")

class AlarmRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val keyAlarms = stringPreferencesKey("alarms_json")
    private val keySettings = stringPreferencesKey("settings_json")
    private val keyLastAlarmId = stringPreferencesKey("last_alarm_id")

    // ---------------- 闹钟 ----------------

    val alarms: Flow<List<Alarm>> = context.dataStore.data.map { prefs ->
        decodeAlarms(prefs[keyAlarms])
    }

    suspend fun currentAlarms(): List<Alarm> = alarms.first()

    suspend fun saveAlarms(list: List<Alarm>) {
        context.dataStore.edit { prefs ->
            prefs[keyAlarms] = json.encodeToString(
                ListSerializer(Alarm.serializer()),
                list.sortedWith(compareBy({ it.hour }, { it.minute }))
            )
        }
    }

    suspend fun upsert(alarm: Alarm) {
        val list = currentAlarms().toMutableList()
        val idx = list.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) list[idx] = alarm else list.add(alarm)
        saveAlarms(list)
    }

    suspend fun delete(id: Long) {
        saveAlarms(currentAlarms().filterNot { it.id == id })
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        saveAlarms(currentAlarms().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    suspend fun getAlarm(id: Long): Alarm? = currentAlarms().firstOrNull { it.id == id }

    suspend fun nextId(): Long {
        val current = context.dataStore.data.first()[keyLastAlarmId]?.toLongOrNull() ?: 0L
        val next = current + 1
        context.dataStore.edit { it[keyLastAlarmId] = next.toString() }
        return next
    }

    private fun decodeAlarms(raw: String?): List<Alarm> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Alarm.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    // ---------------- 配置 ----------------

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        decodeSettings(prefs[keySettings])
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun saveSettings(value: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[keySettings] = json.encodeToString(AppSettings.serializer(), value)
        }
    }

    private fun decodeSettings(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        return runCatching {
            json.decodeFromString(AppSettings.serializer(), raw)
        }.getOrDefault(AppSettings())
    }

    companion object {
        @Volatile
        private var instance: AlarmRepository? = null

        fun get(context: Context): AlarmRepository =
            instance ?: synchronized(this) {
                instance ?: AlarmRepository(context.applicationContext).also { instance = it }
            }
    }
}
