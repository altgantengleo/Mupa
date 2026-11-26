package com.xeno.mupa.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.xeno.mupa.data.local.db.LocalTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Helper pure-Kotlin untuk deduplikasi & fallback metadata import lokal (dapat diuji unit test JVM).
 */
object LocalImportRules {
    val SUPPORTED_EXTENSIONS: Set<String> = setOf("mp3", "flac", "m4a", "aac", "ogg", "wav", "opus")

    val SUPPORTED_MIME_TYPES: Array<String> = arrayOf(
        "audio/*",
        "audio/mpeg",
        "audio/flac",
        "audio/x-flac",
        "audio/mp4",
        "audio/aac",
        "audio/ogg",
        "audio/wav",
        "audio/x-wav",
        "audio/opus",
    )

    fun isSupportedAudioFileName(fileName: String?): Boolean {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext in SUPPORTED_EXTENSIONS
    }

    fun fallbackTitleFromFileName(fileName: String): String {
        val noExt = fileName.substringBeforeLast('.').trim()
        return noExt.ifBlank { "Audio Lokal" }
    }

    fun fingerprintKey(title: String, durationMs: Long, fileSizeBytes: Long): String {
        val normTitle = title.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        val durBucket = (durationMs / 1_000L).coerceAtLeast(0L)
        return "$normTitle|$durBucket|$fileSizeBytes"
    }

    fun isDuplicate(
        candidateUri: String,
        candidateTitle: String,
        candidateDurationMs: Long,
        candidateFileSize: Long,
        existingUris: Set<String>,
        existingFingerprints: Set<String>,
    ): Boolean {
        if (candidateUri in existingUris) return true
        if (candidateFileSize > 0L && candidateDurationMs > 0L) {
            val fp = fingerprintKey(candidateTitle, candidateDurationMs, candidateFileSize)
            if (fp in existingFingerprints) return true
        }
        return false
    }
}

/**
 * Ekstraksi metadata & artwork tertanam secara aman di Dispatchers.IO.
 * - Menangani SecurityException (izin dicabut) dan file audio korup tanpa crash.
 * - Artwork tertanam dikompresi <= 512px JPEG ke cache disk agar Coil tidak memuat bitmap penuh.
 */
class LocalAudioMetadataExtractor(
    private val appContext: Context,
) {
    private val artworkCacheDir: File by lazy {
        File(appContext.filesDir, "local_artwork").apply { mkdirs() }
    }

    suspend fun extract(
        uri: Uri,
        folderTreeUri: String? = null,
        knownFileName: String? = null,
        knownFileSize: Long = 0L,
        nowMs: Long = System.currentTimeMillis(),
    ): Result<LocalTrackEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val (queriedName, queriedSize) = queryDisplayNameAndSize(uri)
            val originalFileName = (knownFileName ?: queriedName ?: uri.lastPathSegment ?: "audio.mp3")
                .substringAfterLast('/')
            val fileSize = if (knownFileSize > 0L) knownFileSize else queriedSize

            val retriever = MediaMetadataRetriever()
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var durationMs = 0L
            var artworkPath: String? = null

            try {
                retriever.setDataSource(appContext, uri)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                val picBytes = runCatching { retriever.embeddedPicture }.getOrNull()
                if (picBytes != null && picBytes.isNotEmpty()) {
                    artworkPath = saveCompressedArtwork(uri.toString(), picBytes)
                }
            } finally {
                runCatching { retriever.release() }
            }

            val finalTitle = title?.takeIf { it.isNotBlank() }
                ?: LocalImportRules.fallbackTitleFromFileName(originalFileName)
            val finalArtist = artist?.takeIf { it.isNotBlank() }.orEmpty()

            LocalTrackEntity(
                uriString = uri.toString(),
                folderTreeUri = folderTreeUri,
                title = finalTitle,
                artist = finalArtist,
                album = album?.takeIf { it.isNotBlank() },
                durationMs = durationMs,
                artworkCachePath = artworkPath,
                originalFileName = originalFileName,
                fileSizeBytes = fileSize,
                dateAddedEpochMs = nowMs,
                isMissing = false,
            )
        }
    }

    private fun queryDisplayNameAndSize(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = 0L
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun saveCompressedArtwork(uriKey: String, bytes: ByteArray): String? = runCatching {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uriKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
        val outFile = File(artworkCacheDir, "art_$hash.jpg")
        if (outFile.exists() && outFile.length() > 128L) return outFile.absolutePath

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (maxDim / sample > 512) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        try {
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 84, stream)
            }
        } finally {
            bitmap.recycle()
        }
        outFile.absolutePath
    }.getOrNull()
}
