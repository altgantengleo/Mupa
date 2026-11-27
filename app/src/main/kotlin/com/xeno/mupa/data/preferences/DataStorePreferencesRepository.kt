package com.xeno.mupa.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStorePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val glassStyleName = prefs[KEY_GLASS_STYLE] ?: GlassStyle.SOLID.name
            val glassStyle = GlassStyle.entries.firstOrNull { it.name == glassStyleName }
                ?: GlassStyle.SOLID
            val sourceName = prefs[KEY_ACTIVE_SOURCE] ?: AudioSourceType.STREAMING.name
            val activeSource = AudioSourceType.entries.firstOrNull { it.name == sourceName }
                ?: AudioSourceType.STREAMING

            UserPreferences(
                onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
                favoriteArtistIds = prefs[KEY_FAVORITE_ARTIST_IDS] ?: emptySet(),
                glassStyle = glassStyle,
                isOledMode = prefs[KEY_OLED_MODE] ?: false,
                isAuroraEnabled = prefs[KEY_ULTRA_DYNAMIC_ENABLED] ?: prefs[KEY_AURORA_ENABLED] ?: true,
                isPerformanceMode = prefs[KEY_PERFORMANCE_MODE] ?: false,
                stripArtistName = prefs[KEY_STRIP_ARTIST_NAME] ?: true,
                activeSource = activeSource,
                crossfadeSeconds = (prefs[KEY_CROSSFADE_SECONDS] ?: 5).coerceIn(0, 12),
            )
        }

    override suspend fun setOnboardingCompleted(artistIds: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
            prefs[KEY_FAVORITE_ARTIST_IDS] = artistIds
        }
    }

    override suspend fun updateFavoriteArtists(artistIds: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_FAVORITE_ARTIST_IDS] = artistIds
        }
    }

    override suspend fun setGlassStyle(style: GlassStyle) {
        dataStore.edit { prefs ->
            prefs[KEY_GLASS_STYLE] = style.name
        }
    }

    override suspend fun setOledMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_OLED_MODE] = enabled
        }
    }

    override suspend fun setAuroraEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ULTRA_DYNAMIC_ENABLED] = enabled
            prefs[KEY_AURORA_ENABLED] = enabled
        }
    }

    override suspend fun setPerformanceMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_PERFORMANCE_MODE] = enabled
        }
    }

    override suspend fun setStripArtistName(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_STRIP_ARTIST_NAME] = enabled
        }
    }

    override suspend fun setActiveSource(source: AudioSourceType) {
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_SOURCE] = source.name
        }
    }

    override suspend fun setCrossfadeSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_CROSSFADE_SECONDS] = seconds.coerceIn(0, 12)
        }
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_FAVORITE_ARTIST_IDS = stringSetPreferencesKey("favorite_artist_ids")
        val KEY_GLASS_STYLE = stringPreferencesKey("glass_style")
        val KEY_OLED_MODE = booleanPreferencesKey("oled_mode")
        val KEY_AURORA_ENABLED = booleanPreferencesKey("aurora_enabled")
        val KEY_ULTRA_DYNAMIC_ENABLED = booleanPreferencesKey("ultra_dynamic_enabled")
        val KEY_PERFORMANCE_MODE = booleanPreferencesKey("performance_mode")
        val KEY_STRIP_ARTIST_NAME = booleanPreferencesKey("strip_artist_name")
        val KEY_ACTIVE_SOURCE = stringPreferencesKey("active_source")
        val KEY_CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
    }
}
