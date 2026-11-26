package com.xeno.mupa.engine.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScopedAudioEntry(
    val id: String,
    val contentUri: Uri,
    val originalFileName: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val dateAddedEpochMs: Long,
)

/**
 * Scanner Scoped Storage Android (MediaStore & SAF).
 * Tidak pernah mengandalkan path mentah filesystem (/sdcard/...);
 * Kotlin mengakses MediaStore/ContentResolver dan menyerahkan ParcelFileDescriptor/metadata
 * ke lapisan engine Rust (libmupa_local.so) sementara pemutaran tetap memakai Media3 content:// URI.
 */
class ScopedStorageAudioScanner(
    private val context: Context,
) {
    suspend fun scanMediaStoreAudio(): List<ScopedAudioEntry> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ScopedAudioEntry>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, mediaId)
                    val displayName = cursor.getString(nameCol).orEmpty()
                    val title = cursor.getString(titleCol).orEmpty().ifBlank { displayName }
                    val rawArtist = cursor.getString(artistCol).orEmpty()
                    val artist = if (rawArtist.equals("<unknown>", ignoreCase = true)) "" else rawArtist
                    val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() }
                    val durationMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val dateAddedMs = cursor.getLong(addedCol).coerceAtLeast(0L) * 1_000L

                    out.add(
                        ScopedAudioEntry(
                            id = "local_$mediaId",
                            contentUri = uri,
                            originalFileName = displayName.ifBlank { "$title.mp3" },
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            fileSizeBytes = sizeBytes,
                            dateAddedEpochMs = dateAddedMs,
                        ),
                    )
                }
            }
        }
        out
    }

    /**
     * Membuka descriptor read-only untuk diserahkan ke Rust `ingest_audio_fd` (via dup().detachFd()),
     * atau membaca header/bytes aman bila memakai `ingest_audio_bytes`.
     */
    fun openReadOnlyFd(contentUri: Uri): ParcelFileDescriptor? =
        runCatching { context.contentResolver.openFileDescriptor(contentUri, "r") }.getOrNull()

    fun querySafDisplayNameAndSize(uri: Uri): Pair<String, Long>? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val name = cursor.getString(0).orEmpty()
                val size = cursor.getLong(1).coerceAtLeast(0L)
                name to size
            }
        }.getOrNull()
    }
}
