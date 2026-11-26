package com.xeno.mupa.data.lyrics

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Pembaca lirik tertanam (embedded) clean-room dari file audio lokal via SAF content:// InputStream:
 * - Mendukung tag ID3v2.3 / ID3v2.4 frame USLT (unsynchronized lyrics / embedded LRC) pada MP3.
 * - Mendukung blok Vorbis Comment LYRICS / UNSYNCEDLYRICS pada FLAC.
 * - Pembacaan dibatasi maks 2 MB header/tag pada Dispatchers.IO agar aman memori & tanpa path mentah.
 */
object EmbeddedAudioLyricsReader {

    private const val MAX_HEADER_SCAN_BYTES = 2 * 1024 * 1024

    suspend fun readEmbeddedLyrics(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16_384)
                var total = 0
                while (total < MAX_HEADER_SCAN_BYTES) {
                    val toRead = minOf(buf.size, MAX_HEADER_SCAN_BYTES - total)
                    val read = stream.read(buf, 0, toRead)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    total += read
                }
                val bytes = out.toByteArray()
                parseId3v2Uslt(bytes) ?: parseFlacVorbisLyrics(bytes)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Ekstrak frame USLT dari header ID3v2.3 / ID3v2.4. */
    fun parseId3v2Uslt(data: ByteArray): String? {
        if (data.size < 10) return null
        if (data[0] != 'I'.code.toByte() || data[1] != 'D'.code.toByte() || data[2] != '3'.code.toByte()) {
            return null
        }
        val majorVersion = data[3].toInt() and 0xFF
        if (majorVersion !in 3..4) return null
        val flags = data[5].toInt() and 0xFF
        val tagSize = readSyncSafeInt(data, 6)
        val tagEnd = minOf(data.size, 10 + tagSize)
        var pos = 10

        // Lewati extended header bila ada
        if ((flags and 0x40) != 0 && pos + 4 <= tagEnd) {
            val extSize = if (majorVersion == 4) readSyncSafeInt(data, pos) else readBigEndianInt(data, pos)
            pos += extSize.coerceAtLeast(4)
        }

        while (pos + 10 <= tagEnd) {
            val frameId = String(data, pos, 4, StandardCharsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' }) break
            val frameSize = if (majorVersion == 4) {
                readSyncSafeInt(data, pos + 4)
            } else {
                readBigEndianInt(data, pos + 4)
            }
            if (frameSize <= 0 || pos + 10 + frameSize > tagEnd) break

            val contentStart = pos + 10
            if (frameId == "USLT" && frameSize > 4) {
                val decoded = decodeUsltPayload(data, contentStart, frameSize)
                if (!decoded.isNullOrBlank()) return decoded
            }
            pos = contentStart + frameSize
        }
        return null
    }

    /** Ekstrak tag LYRICS / UNSYNCEDLYRICS dari FLAC METADATA_BLOCK_VORBIS_COMMENT (tipe 4). */
    fun parseFlacVorbisLyrics(data: ByteArray): String? {
        if (data.size < 8) return null
        if (data[0] != 'f'.code.toByte() || data[1] != 'L'.code.toByte() ||
            data[2] != 'a'.code.toByte() || data[3] != 'C'.code.toByte()
        ) {
            return null
        }
        var pos = 4
        while (pos + 4 <= data.size) {
            val header = data[pos].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val length = ((data[pos + 1].toInt() and 0xFF) shl 16) or
                ((data[pos + 2].toInt() and 0xFF) shl 8) or
                (data[pos + 3].toInt() and 0xFF)
            pos += 4
            if (length < 0 || pos + length > data.size) break

            if (blockType == 4) {
                val found = parseVorbisCommentBlock(data, pos, length)
                if (!found.isNullOrBlank()) return found
            }
            pos += length
            if (isLast) break
        }
        return null
    }

    private fun parseVorbisCommentBlock(data: ByteArray, offset: Int, length: Int): String? {
        val end = offset + length
        var pos = offset
        if (pos + 4 > end) return null
        val vendorLen = readLittleEndianInt(data, pos)
        pos += 4 + vendorLen
        if (vendorLen < 0 || pos + 4 > end) return null
        val commentCount = readLittleEndianInt(data, pos)
        pos += 4
        if (commentCount <= 0 || commentCount > 2_000) return null

        var fallbackPlain: String? = null
        repeat(commentCount) {
            if (pos + 4 > end) return fallbackPlain
            val len = readLittleEndianInt(data, pos)
            pos += 4
            if (len < 0 || pos + len > end) return fallbackPlain
            val comment = String(data, pos, len, StandardCharsets.UTF_8)
            pos += len
            val eq = comment.indexOf('=')
            if (eq > 0) {
                val key = comment.substring(0, eq).uppercase()
                val value = comment.substring(eq + 1).trim()
                if (value.isNotBlank()) {
                    when (key) {
                        "LYRICS", "SYNCEDLYRICS" -> return value
                        "UNSYNCEDLYRICS" -> fallbackPlain = value
                    }
                }
            }
        }
        return fallbackPlain
    }

    private fun decodeUsltPayload(data: ByteArray, offset: Int, size: Int): String? {
        // USLT: [1 byte encoding][3 bytes lang][desc + null terminator][lyrics text]
        val encoding = data[offset].toInt() and 0xFF
        val payloadStart = offset + 4
        val payloadEnd = offset + size
        if (payloadStart >= payloadEnd) return null

        val isDoubleByte = encoding == 1 || encoding == 2
        val charset: Charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.UTF_8
        }

        var textStart = payloadStart
        if (isDoubleByte) {
            while (textStart + 1 < payloadEnd) {
                if (data[textStart].toInt() == 0 && data[textStart + 1].toInt() == 0) {
                    textStart += 2
                    break
                }
                textStart += 2
            }
        } else {
            while (textStart < payloadEnd) {
                if (data[textStart].toInt() == 0) {
                    textStart += 1
                    break
                }
                textStart += 1
            }
        }

        if (textStart >= payloadEnd) return null
        return String(data, textStart, payloadEnd - textStart, charset)
            .trim('\u0000', '\uFEFF', ' ', '\n', '\r')
    }

    private fun readSyncSafeInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0x7F) shl 21) or
            ((data[offset + 1].toInt() and 0x7F) shl 14) or
            ((data[offset + 2].toInt() and 0x7F) shl 7) or
            (data[offset + 3].toInt() and 0x7F)

    private fun readBigEndianInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun readLittleEndianInt(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
}
