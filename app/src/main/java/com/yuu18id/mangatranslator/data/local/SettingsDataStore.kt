package com.yuu18id.mangatranslator.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val preferencesFlow: Flow<Preferences> = dataStore.data

    suspend fun saveConfigString(keyName: String, value: String) {
        val key = stringPreferencesKey(keyName)
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    fun getConfigString(keyName: String, defaultValue: String): Flow<String> {
        val key = stringPreferencesKey(keyName)
        return dataStore.data.map { preferences ->
            preferences[key] ?: defaultValue
        }
    }

    suspend fun saveConfigInt(keyName: String, value: Int) {
        val key = intPreferencesKey(keyName)
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    fun getConfigInt(keyName: String, defaultValue: Int): Flow<Int> {
        val key = intPreferencesKey(keyName)
        return dataStore.data.map { preferences ->
            preferences[key] ?: defaultValue
        }
    }

    suspend fun saveApiKey(service: String, apiKey: String) {
        val key = stringPreferencesKey("api_key_$service")
        dataStore.edit { preferences ->
            preferences[key] = apiKey
        }
    }

    fun getApiKey(service: String): Flow<String> {
        val key = stringPreferencesKey("api_key_$service")
        return dataStore.data.map { preferences ->
            preferences[key] ?: ""
        }
    }
}
