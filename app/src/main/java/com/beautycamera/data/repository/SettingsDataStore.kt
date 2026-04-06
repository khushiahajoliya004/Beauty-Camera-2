package com.beautycamera.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "beauty_settings")

@Singleton
class SettingsDataStore @Inject constructor(private val context: Context) {

    companion object {
        val LAST_FILTER = stringPreferencesKey("last_filter")
        val SKIN_SMOOTHING = floatPreferencesKey("skin_smoothing")
        val LIP_OPACITY = floatPreferencesKey("lip_opacity")
        val BLUSH_INTENSITY = floatPreferencesKey("blush_intensity")
        val EYE_COLOR_OPACITY = floatPreferencesKey("eye_color_opacity")
        val FOUNDATION_INTENSITY = floatPreferencesKey("foundation_intensity")
    }

    val lastFilter: Flow<String> = context.dataStore.data.map { it[LAST_FILTER] ?: "natural" }
    val skinSmoothing: Flow<Float> = context.dataStore.data.map { it[SKIN_SMOOTHING] ?: 0f }
    val lipOpacity: Flow<Float> = context.dataStore.data.map { it[LIP_OPACITY] ?: 0f }
    val blushIntensity: Flow<Float> = context.dataStore.data.map { it[BLUSH_INTENSITY] ?: 0f }
    val eyeColorOpacity: Flow<Float> = context.dataStore.data.map { it[EYE_COLOR_OPACITY] ?: 0f }
    val foundationIntensity: Flow<Float> = context.dataStore.data.map { it[FOUNDATION_INTENSITY] ?: 0f }

    suspend fun saveLastFilter(filterId: String) {
        context.dataStore.edit { it[LAST_FILTER] = filterId }
    }

    suspend fun saveSkinSmoothing(value: Float) {
        context.dataStore.edit { it[SKIN_SMOOTHING] = value }
    }

    suspend fun saveLipOpacity(value: Float) {
        context.dataStore.edit { it[LIP_OPACITY] = value }
    }

    suspend fun saveBlushIntensity(value: Float) {
        context.dataStore.edit { it[BLUSH_INTENSITY] = value }
    }

    suspend fun saveEyeColorOpacity(value: Float) {
        context.dataStore.edit { it[EYE_COLOR_OPACITY] = value }
    }

    suspend fun saveFoundationIntensity(value: Float) {
        context.dataStore.edit { it[FOUNDATION_INTENSITY] = value }
    }
}
