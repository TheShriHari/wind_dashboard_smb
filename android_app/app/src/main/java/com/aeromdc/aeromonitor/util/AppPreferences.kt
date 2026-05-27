package com.aeromdc.aeromonitor.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create a DataStore instance
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aeromdc_prefs")

object PreferenceKeys {
    val BACKEND_URL = stringPreferencesKey("backend_url")
    val ROOKTEC_USERNAME = stringPreferencesKey("rooktec_username")
    val ROOKTEC_PASSWORD = stringPreferencesKey("rooktec_password")
    val ROOKTEC_BASE_URL = stringPreferencesKey("rooktec_base_url")
    val SCRAPE_INTERVAL_MIN = stringPreferencesKey("scrape_interval_min")
}

/**
 * Typed accessors for app preferences stored in DataStore.
 */
class AppPreferences(private val context: Context) {

    // Default: 10.0.2.2 = Android emulator's alias for the host machine (localhost)
    val backendUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.BACKEND_URL] ?: "http://10.0.2.2/api"
    }

    val rooktecUsername: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.ROOKTEC_USERNAME] ?: "smb"
    }

    val rooktecPassword: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.ROOKTEC_PASSWORD] ?: "wind@smb"
    }

    val rooktecBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.ROOKTEC_BASE_URL] ?: "https://www.rooktec.in/wmapp"
    }

    val scrapeIntervalMin: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[PreferenceKeys.SCRAPE_INTERVAL_MIN] ?: "15").toIntOrNull() ?: 15
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { it[PreferenceKeys.BACKEND_URL] = url }
    }

    suspend fun setRooktecUsername(username: String) {
        context.dataStore.edit { it[PreferenceKeys.ROOKTEC_USERNAME] = username }
    }

    suspend fun setRooktecPassword(password: String) {
        context.dataStore.edit { it[PreferenceKeys.ROOKTEC_PASSWORD] = password }
    }

    suspend fun setRooktecBaseUrl(url: String) {
        context.dataStore.edit { it[PreferenceKeys.ROOKTEC_BASE_URL] = url }
    }

    suspend fun setScrapeIntervalMin(minutes: Int) {
        context.dataStore.edit { it[PreferenceKeys.SCRAPE_INTERVAL_MIN] = minutes.toString() }
    }
}
