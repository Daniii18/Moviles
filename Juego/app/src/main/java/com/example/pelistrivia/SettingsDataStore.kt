package com.example.pelistrivia

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Este delegado viene del KTX: androidx.datastore:datastore-preferences
private val Context.dataStore by preferencesDataStore("pelistrivia_prefs")

object SettingsKeys {
    val VOLUME = floatPreferencesKey("volume")
}

suspend fun saveVolume(context: Context, value: Float) {
    context.dataStore.edit { prefs ->
        prefs[SettingsKeys.VOLUME] = value
    }
}

suspend fun loadVolume(context: Context): Float {
    return context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.VOLUME] ?: 0.8f
    }.first()
}
