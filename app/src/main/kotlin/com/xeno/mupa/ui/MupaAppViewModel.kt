package com.xeno.mupa.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.xeno.mupa.core.designsystem.theme.DynamicCoverAccent
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPaletteCache
import com.xeno.mupa.core.domain.ArtistRepository
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.data.backup.MupaBackupCodec
import com.xeno.mupa.data.local.LocalLibraryRepository
import com.xeno.mupa.data.local.db.LyricsCacheDao
import com.xeno.mupa.playback.MupaCrossfadeConfig
import com.xeno.mupa.playback.eq.EqPreset
import com.xeno.mupa.streaming.aggregator.ProviderPrewarmer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject

@Immutable
data class MupaAppUiState(
    val isReady: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val favoriteArtists: List<Artist> = emptyList(),
    val totalTracksCount: Int = 0,
    val favoriteTracksCount: Int = 0,
    val connectedFoldersCount: Int = 0,
    val audioCacheBytes: Long = 0L,
    val settingsStatusMessage: String? = null,
    val isEditingFavoriteArtists: Boolean = false,
) {
    val appearance: MupaAppearance
        get() = MupaAppearance(
            glassStyle = preferences.glassStyle,
            isOledMode = preferences.isOledMode,
            isUltraDynamicEnabled = preferences.isAuroraEnabled,
            isPerformanceMode = preferences.isPerformanceMode,
            dynamicCoverAccent = DynamicCoverAccent.Default,
        )
}

@HiltViewModel
class MupaAppViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesRepository: PreferencesRepository,
    private val artistRepository: ArtistRepository,
    private val musicSource: MusicSource,
    private val localLibraryRepository: LocalLibraryRepository,
    private val lyricsCacheDao: LyricsCacheDao,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MupaAppUiState())
    val uiState: StateFlow<MupaAppUiState> = _uiState.asStateFlow()

    init {
        // Pra-panas DNS + TLS + HTTP/2 pool semua provider di latar belakang saat aplikasi dibuka
        viewModelScope.launch(Dispatchers.IO) {
            ProviderPrewarmer.prewarm(okHttpClient)
        }
        viewModelScope.launch {
            combine(
                preferencesRepository.userPreferences,
                localLibraryRepository.localTracksFlow,
                localLibraryRepository.localFoldersFlow,
            ) { prefs, localTracks, folders ->
                Triple(prefs, localTracks, folders)
            }.collect { (prefs, localTracks, folders) ->
                MupaCrossfadeConfig.setDurationSeconds(prefs.crossfadeSeconds)
                val artists = artistRepository.getArtistsByIds(prefs.favoriteArtistIds).getOrDefault(emptyList())
                // Konsistensi angka N6: total & favorit mengikuti activeSource yang sama dengan Pustaka
                val activeTracks = if (prefs.activeSource == AudioSourceType.LOCAL) {
                    localTracks
                } else {
                    musicSource.getLibraryTracks(AudioSourceType.STREAMING).getOrDefault(emptyList())
                }
                val cacheBytes = withContext(Dispatchers.IO) { computeAudioCacheBytes() }
                _uiState.update {
                    it.copy(
                        isReady = true,
                        preferences = prefs,
                        favoriteArtists = artists,
                        totalTracksCount = activeTracks.size,
                        favoriteTracksCount = activeTracks.count { t -> t.isFavorite },
                        connectedFoldersCount = folders.size,
                        audioCacheBytes = cacheBytes,
                    )
                }
            }
        }
    }

    fun setGlassStyle(style: GlassStyle) {
        viewModelScope.launch { preferencesRepository.setGlassStyle(style) }
    }

    fun setOledMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOledMode(enabled) }
    }

    fun setAuroraEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAuroraEnabled(enabled) }
    }

    fun setPerformanceMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPerformanceMode(enabled) }
    }

    fun setStripArtistName(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setStripArtistName(enabled) }
    }

    fun setActiveSource(source: AudioSourceType) {
        viewModelScope.launch { preferencesRepository.setActiveSource(source) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(MupaCrossfadeConfig.MIN_SECONDS, MupaCrossfadeConfig.MAX_SECONDS)
        MupaCrossfadeConfig.setDurationSeconds(clamped)
        viewModelScope.launch { preferencesRepository.setCrossfadeSeconds(clamped) }
    }

    fun importLocalFiles(uris: List<Uri>) {
        viewModelScope.launch { localLibraryRepository.importAudioFiles(uris) }
    }

    fun importLocalFolder(treeUri: Uri) {
        viewModelScope.launch { localLibraryRepository.importAudioFolder(treeUri) }
    }

    fun rescanLocalFolders() {
        viewModelScope.launch {
            val summary = localLibraryRepository.rescanFolders()
            _uiState.update {
                it.copy(
                    settingsStatusMessage = "Pemindaian selesai: +${summary.importedCount} lagu baru.",
                )
            }
        }
    }

    fun refreshCacheStats() {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { computeAudioCacheBytes() }
            _uiState.update { it.copy(audioCacheBytes = bytes) }
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dir = File(appContext.cacheDir, "mupa_audio_cache")
                if (dir.exists()) {
                    dir.listFiles()?.forEach { child ->
                        runCatching { child.deleteRecursively() }
                    }
                }
            }
            val remaining = withContext(Dispatchers.IO) { computeAudioCacheBytes() }
            _uiState.update {
                it.copy(
                    audioCacheBytes = remaining,
                    settingsStatusMessage = "Cache audio berhasil dibersihkan.",
                )
            }
        }
    }

    fun clearImageAndLyricsCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val loader = SingletonImageLoader.get(appContext)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
                UltraDynamicPaletteCache.clear()
                runCatching { lyricsCacheDao.deleteAll() }
            }
            _uiState.update {
                it.copy(settingsStatusMessage = "Cache gambar, palet UD, dan lirik dibersihkan.")
            }
        }
    }

    fun exportBackupToUri(
        uri: Uri,
        eqPreset: EqPreset,
        eqGainsDb: List<Float>,
    ) {
        viewModelScope.launch {
            val prefs = _uiState.value.preferences
            val payload = MupaBackupCodec.exportJson(
                preferences = prefs,
                eqPreset = eqPreset,
                eqGainsDb = eqGainsDb,
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("Tidak dapat membuka berkas tujuan.")
                }.isSuccess
            }
            _uiState.update {
                it.copy(
                    settingsStatusMessage = if (ok) {
                        "Cadangan JSON berhasil disimpan."
                    } else {
                        "Gagal menyimpan berkas cadangan."
                    },
                )
            }
        }
    }

    fun importBackupFromUri(
        uri: Uri,
        onRestoreEq: (EqPreset, List<Float>) -> Unit,
    ) {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    }
                }.getOrNull()
            }
            if (raw.isNullOrBlank()) {
                _uiState.update { it.copy(settingsStatusMessage = "Berkas cadangan kosong atau tidak terbaca.") }
                return@launch
            }
            MupaBackupCodec.importJson(raw).fold(
                onSuccess = { restored ->
                    applyRestoredBackup(restored)
                    onRestoreEq(restored.eqPreset, restored.eqGainsDb)
                    _uiState.update { it.copy(settingsStatusMessage = "Cadangan berhasil dipulihkan.") }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(settingsStatusMessage = err.message ?: "Berkas cadangan tidak valid.")
                    }
                },
            )
        }
    }

    fun openEditFavoriteArtists() {
        _uiState.update { it.copy(isEditingFavoriteArtists = true) }
    }

    fun closeEditFavoriteArtists() {
        _uiState.update { it.copy(isEditingFavoriteArtists = false) }
    }

    fun applyRestoredBackup(
        restored: MupaBackupCodec.RestoredBackup,
    ) {
        viewModelScope.launch {
            val p = restored.preferences
            preferencesRepository.setGlassStyle(p.glassStyle)
            preferencesRepository.setOledMode(p.isOledMode)
            preferencesRepository.setAuroraEnabled(p.isAuroraEnabled)
            preferencesRepository.setPerformanceMode(p.isPerformanceMode)
            preferencesRepository.setStripArtistName(p.stripArtistName)
            if (p.favoriteArtistIds.size >= 2) {
                preferencesRepository.setOnboardingCompleted(p.favoriteArtistIds)
            }
        }
    }

    private fun computeAudioCacheBytes(): Long {
        val dir = File(appContext.cacheDir, "mupa_audio_cache")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
