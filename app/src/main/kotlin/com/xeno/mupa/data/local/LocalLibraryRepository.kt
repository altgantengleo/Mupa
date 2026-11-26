package com.xeno.mupa.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.data.local.db.LocalFolderDao
import com.xeno.mupa.data.local.db.LocalFolderEntity
import com.xeno.mupa.data.local.db.LocalTrackDao
import com.xeno.mupa.data.local.db.LocalTrackEntity
import com.xeno.mupa.data.local.db.PlaylistTrackCrossRef
import com.xeno.mupa.data.local.db.UserPlaylistDao
import com.xeno.mupa.data.local.db.UserPlaylistEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class LocalImportSummary(
    val importedCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
    val failedCount: Int = 0,
    val removedMissingCount: Int = 0,
)

data class LocalImportProgress(
    val isRunning: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val lastSummary: LocalImportSummary? = null,
    val message: String? = null,
)

/**
 * Repository khusus Mode Lokal (Ponsel) berbasis SAF + Room (perbaikan B12).
 * - ZERO auto-scan MediaStore liar; lagu lokal hanya masuk melalui aksi Import Lagu / Import Folder.
 * - Menggunakan takePersistableUriPermission agar izin URI bertahan setelah restart aplikasi.
 * - Menghapus dari pustaka TIDAK pernah menghapus file asli di perangkat pengguna.
 */
@Singleton
class LocalLibraryRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val trackDao: LocalTrackDao,
    private val folderDao: LocalFolderDao,
    private val playlistDao: UserPlaylistDao,
) {
    private val extractor = LocalAudioMetadataExtractor(appContext)

    private val _importProgress = MutableStateFlow(LocalImportProgress())
    val importProgress: StateFlow<LocalImportProgress> = _importProgress.asStateFlow()

    val localTracksFlow: Flow<List<Track>> = trackDao.observeAvailableTracks()
        .map { entities -> entities.map { it.toDomainTrack() } }

    val localFoldersFlow: Flow<List<LocalFolderEntity>> = folderDao.observeFolders()

    fun observePlaylists(source: AudioSourceType): Flow<List<UserPlaylistEntity>> =
        playlistDao.observePlaylists(source.name)

    suspend fun getAllLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        trackDao.getAllAvailableTracks().map { it.toDomainTrack() }
    }

    suspend fun searchLocalTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val entities = if (trimmed.isEmpty()) {
            trackDao.getAllAvailableTracks()
        } else {
            trackDao.searchTracks(trimmed)
        }
        entities.map { it.toDomainTrack() }
    }

    /**
     * Import multi-file audio via OpenDocument + takePersistableUriPermission.
     */
    suspend fun importAudioFiles(uris: List<Uri>): LocalImportSummary = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext LocalImportSummary()
        _importProgress.value = LocalImportProgress(isRunning = true, processed = 0, total = uris.size)

        val existing = trackDao.getAllTracksIncludingMissing()
        val existingUris = existing.map { it.uriString }.toMutableSet()
        val existingFps = existing.map {
            LocalImportRules.fingerprintKey(it.title, it.durationMs, it.fileSizeBytes)
        }.toMutableSet()

        var imported = 0
        var skipped = 0
        var failed = 0
        val toInsert = mutableListOf<LocalTrackEntity>()

        uris.forEachIndexed { idx, uri ->
            takePersistableReadPermissionSafe(uri)
            val uriStr = uri.toString()
            if (uriStr in existingUris) {
                skipped++
            } else {
                extractor.extract(uri = uri).fold(
                    onSuccess = { entity ->
                        if (LocalImportRules.isDuplicate(
                                candidateUri = entity.uriString,
                                candidateTitle = entity.title,
                                candidateDurationMs = entity.durationMs,
                                candidateFileSize = entity.fileSizeBytes,
                                existingUris = existingUris,
                                existingFingerprints = existingFps,
                            )
                        ) {
                            skipped++
                        } else {
                            toInsert.add(entity)
                            existingUris.add(entity.uriString)
                            existingFps.add(
                                LocalImportRules.fingerprintKey(entity.title, entity.durationMs, entity.fileSizeBytes),
                            )
                            imported++
                        }
                    },
                    onFailure = { failed++ },
                )
            }
            _importProgress.value = LocalImportProgress(
                isRunning = true,
                processed = idx + 1,
                total = uris.size,
            )
        }

        if (toInsert.isNotEmpty()) {
            trackDao.upsertAll(toInsert)
        }

        val summary = LocalImportSummary(
            importedCount = imported,
            skippedDuplicateCount = skipped,
            failedCount = failed,
        )
        _importProgress.value = LocalImportProgress(
            isRunning = false,
            processed = uris.size,
            total = uris.size,
            lastSummary = summary,
            message = buildSummaryMessage(summary),
        )
        summary
    }

    /**
     * Import seluruh file audio di dalam folder terpilih (OpenDocumentTree) + simpan izin persisten.
     */
    suspend fun importAudioFolder(treeUri: Uri): LocalImportSummary = withContext(Dispatchers.IO) {
        takePersistableReadPermissionSafe(treeUri)
        val rootDoc = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: return@withContext LocalImportSummary(failedCount = 1)

        val folderName = rootDoc.name ?: "Folder Musik"
        val audioDocs = mutableListOf<DocumentFile>()
        collectAudioDocuments(rootDoc, audioDocs, maxDepth = 4)

        _importProgress.value = LocalImportProgress(isRunning = true, processed = 0, total = audioDocs.size)

        val existing = trackDao.getAllTracksIncludingMissing()
        val existingUris = existing.map { it.uriString }.toMutableSet()
        val existingFps = existing.map {
            LocalImportRules.fingerprintKey(it.title, it.durationMs, it.fileSizeBytes)
        }.toMutableSet()

        var imported = 0
        var skipped = 0
        var failed = 0
        val toInsert = mutableListOf<LocalTrackEntity>()

        audioDocs.forEachIndexed { idx, doc ->
            val docUri = doc.uri
            val uriStr = docUri.toString()
            if (uriStr in existingUris) {
                skipped++
            } else {
                extractor.extract(
                    uri = docUri,
                    folderTreeUri = treeUri.toString(),
                    knownFileName = doc.name,
                    knownFileSize = doc.length(),
                ).fold(
                    onSuccess = { entity ->
                        if (LocalImportRules.isDuplicate(
                                candidateUri = entity.uriString,
                                candidateTitle = entity.title,
                                candidateDurationMs = entity.durationMs,
                                candidateFileSize = entity.fileSizeBytes,
                                existingUris = existingUris,
                                existingFingerprints = existingFps,
                            )
                        ) {
                            skipped++
                        } else {
                            toInsert.add(entity)
                            existingUris.add(entity.uriString)
                            existingFps.add(
                                LocalImportRules.fingerprintKey(entity.title, entity.durationMs, entity.fileSizeBytes),
                            )
                            imported++
                        }
                    },
                    onFailure = { failed++ },
                )
            }
            _importProgress.value = LocalImportProgress(
                isRunning = true,
                processed = idx + 1,
                total = audioDocs.size,
            )
        }

        if (toInsert.isNotEmpty()) {
            trackDao.upsertAll(toInsert)
        }

        val now = System.currentTimeMillis()
        folderDao.upsert(
            LocalFolderEntity(
                treeUriString = treeUri.toString(),
                displayName = folderName,
                addedAtEpochMs = now,
                lastScannedEpochMs = now,
                trackCount = audioDocs.size,
            ),
        )

        val summary = LocalImportSummary(
            importedCount = imported,
            skippedDuplicateCount = skipped,
            failedCount = failed,
        )
        _importProgress.value = LocalImportProgress(
            isRunning = false,
            processed = audioDocs.size,
            total = audioDocs.size,
            lastSummary = summary,
            message = buildSummaryMessage(summary),
        )
        summary
    }

    /**
     * Pindai ulang semua folder terhubung untuk mendeteksi file baru & membersihkan URI yang hilang/dicabut.
     */
    suspend fun rescanFolders(): LocalImportSummary = withContext(Dispatchers.IO) {
        val folders = folderDao.getAllFolders()
        var totalImported = 0
        var totalSkipped = 0
        var totalFailed = 0

        for (folder in folders) {
            val res = importAudioFolder(Uri.parse(folder.treeUriString))
            totalImported += res.importedCount
            totalSkipped += res.skippedDuplicateCount
            totalFailed += res.failedCount
        }

        // Validasi aksesibilitas semua track tersimpan (deteksi file terhapus / izin dicabut)
        var removedMissing = 0
        val allTracks = trackDao.getAllAvailableTracks()
        for (entity in allTracks) {
            if (!isUriReadable(Uri.parse(entity.uriString))) {
                trackDao.markMissing(entity.uriString, missing = true)
                removedMissing++
            }
        }
        if (removedMissing > 0) {
            trackDao.purgeMissing()
        }

        val summary = LocalImportSummary(
            importedCount = totalImported,
            skippedDuplicateCount = totalSkipped,
            failedCount = totalFailed,
            removedMissingCount = removedMissing,
        )
        _importProgress.value = LocalImportProgress(
            isRunning = false,
            lastSummary = summary,
            message = buildSummaryMessage(summary),
        )
        summary
    }

    /**
     * Menghapus lagu dari pustaka Room & melepas persistable URI bila aman.
     * TIDAK pernah menghapus file asli di penyimpanan pengguna.
     */
    suspend fun removeTrackFromLibrary(uriString: String) = withContext(Dispatchers.IO) {
        val entity = trackDao.getByUri(uriString)
        trackDao.deleteByUri(uriString)
        if (entity != null && entity.folderTreeUri == null) {
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    suspend fun toggleFavorite(uriString: String) = withContext(Dispatchers.IO) {
        trackDao.toggleFavorite(uriString)
    }

    suspend fun recordPlay(uriString: String) = withContext(Dispatchers.IO) {
        trackDao.recordPlay(uriString, System.currentTimeMillis())
    }

    suspend fun createPlaylist(name: String, source: AudioSourceType): UserPlaylistEntity =
        withContext(Dispatchers.IO) {
            val entity = UserPlaylistEntity(
                id = "pl_${UUID.randomUUID().toString().take(8)}",
                name = name.trim().ifEmpty { "Playlist Baru" },
                sourceType = source.name,
                createdAtEpochMs = System.currentTimeMillis(),
            )
            playlistDao.upsertPlaylist(entity)
            entity
        }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        val current = playlistDao.getTrackIdsForPlaylist(playlistId)
        if (trackId !in current) {
            playlistDao.addTrackToPlaylist(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = current.size,
                ),
            )
        }
    }

    /**
     * Mencari file .lrc pendamping di folder yang sama (untuk R4 Lirik lokal offline).
     */
    suspend fun readSidecarLrc(trackUriString: String): String? = withContext(Dispatchers.IO) {
        val entity = trackDao.getByUri(trackUriString) ?: return@withContext null
        val treeUriStr = entity.folderTreeUri ?: return@withContext null
        val baseName = entity.originalFileName.substringBeforeLast('.').trim()
        if (baseName.isEmpty()) return@withContext null

        runCatching {
            val root = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUriStr)) ?: return@withContext null
            val targetName = "$baseName.lrc"
            val lrcDoc = findFileByName(root, targetName, maxDepth = 4) ?: return@withContext null
            appContext.contentResolver.openInputStream(lrcDoc.uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
    }

    fun isUriReadable(uri: Uri): Boolean = runCatching {
        appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    fun clearImportMessage() {
        _importProgress.value = _importProgress.value.copy(message = null)
    }

    private fun takePersistableReadPermissionSafe(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun collectAudioDocuments(
        dir: DocumentFile,
        out: MutableList<DocumentFile>,
        maxDepth: Int,
        depth: Int = 0,
    ) {
        if (depth > maxDepth) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (child.isDirectory) {
                collectAudioDocuments(child, out, maxDepth, depth + 1)
            } else if (child.isFile && LocalImportRules.isSupportedAudioFileName(child.name)) {
                out.add(child)
            }
        }
    }

    private fun findFileByName(
        dir: DocumentFile,
        targetName: String,
        maxDepth: Int,
        depth: Int = 0,
    ): DocumentFile? {
        if (depth > maxDepth) return null
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (child.isFile && child.name.equals(targetName, ignoreCase = true)) {
                return child
            }
            if (child.isDirectory) {
                findFileByName(child, targetName, maxDepth, depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun buildSummaryMessage(summary: LocalImportSummary): String {
        val parts = mutableListOf<String>()
        if (summary.importedCount > 0) parts.add("${summary.importedCount} lagu ditambahkan")
        if (summary.skippedDuplicateCount > 0) parts.add("${summary.skippedDuplicateCount} duplikat dilewati")
        if (summary.removedMissingCount > 0) parts.add("${summary.removedMissingCount} file hilang dibersihkan")
        if (summary.failedCount > 0) parts.add("${summary.failedCount} gagal dibaca")
        return if (parts.isEmpty()) "Tidak ada perubahan pada pustaka lokal." else parts.joinToString(" · ")
    }
}

fun LocalTrackEntity.toDomainTrack(): Track = Track(
    id = uriString,
    title = title,
    artistName = artist,
    albumTitle = album,
    durationMs = durationMs,
    artworkUrl = artworkCachePath,
    sourceType = AudioSourceType.LOCAL,
    originalFileName = originalFileName,
    isFavorite = isFavorite,
    playCount = playCount,
    dateAddedEpochMs = dateAddedEpochMs,
    lastPlayedEpochMs = lastPlayedEpochMs,
)
