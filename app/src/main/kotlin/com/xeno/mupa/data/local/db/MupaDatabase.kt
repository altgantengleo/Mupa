package com.xeno.mupa.data.local.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_tracks")
data class LocalTrackEntity(
    @PrimaryKey val uriString: String,
    val folderTreeUri: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkCachePath: String? = null,
    val originalFileName: String,
    val fileSizeBytes: Long = 0L,
    val dateAddedEpochMs: Long,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedEpochMs: Long = 0L,
    val isMissing: Boolean = false,
)

@Entity(tableName = "local_folders")
data class LocalFolderEntity(
    @PrimaryKey val treeUriString: String,
    val displayName: String,
    val addedAtEpochMs: Long,
    val lastScannedEpochMs: Long,
    val trackCount: Int = 0,
)

@Entity(tableName = "user_playlists")
data class UserPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceType: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val position: Int,
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val cacheKey: String,
    val rawLrc: String?,
    val isSynced: Boolean,
    val providerId: String,
    val isNegative: Boolean,
    val cachedAtEpochMs: Long,
)

@Dao
interface LocalTrackDao {
    @Query("SELECT * FROM local_tracks WHERE isMissing = 0 ORDER BY dateAddedEpochMs DESC")
    fun observeAvailableTracks(): Flow<List<LocalTrackEntity>>

    @Query("SELECT * FROM local_tracks WHERE isMissing = 0 ORDER BY dateAddedEpochMs DESC")
    suspend fun getAllAvailableTracks(): List<LocalTrackEntity>

    @Query("SELECT * FROM local_tracks")
    suspend fun getAllTracksIncludingMissing(): List<LocalTrackEntity>

    @Query("SELECT * FROM local_tracks WHERE uriString = :uriString LIMIT 1")
    suspend fun getByUri(uriString: String): LocalTrackEntity?

    @Query(
        """
        SELECT * FROM local_tracks
        WHERE isMissing = 0 AND (
            title LIKE '%' || :query || '%' OR
            artist LIKE '%' || :query || '%' OR
            album LIKE '%' || :query || '%' OR
            originalFileName LIKE '%' || :query || '%'
        )
        ORDER BY title ASC
        """,
    )
    suspend fun searchTracks(query: String): List<LocalTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<LocalTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: LocalTrackEntity)

    @Query("UPDATE local_tracks SET isFavorite = NOT isFavorite WHERE uriString = :uriString")
    suspend fun toggleFavorite(uriString: String)

    @Query("UPDATE local_tracks SET playCount = playCount + 1, lastPlayedEpochMs = :nowMs WHERE uriString = :uriString")
    suspend fun recordPlay(uriString: String, nowMs: Long)

    @Query("UPDATE local_tracks SET isMissing = :missing WHERE uriString = :uriString")
    suspend fun markMissing(uriString: String, missing: Boolean)

    @Query("DELETE FROM local_tracks WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)

    @Query("DELETE FROM local_tracks WHERE isMissing = 1")
    suspend fun purgeMissing()
}

@Dao
interface LocalFolderDao {
    @Query("SELECT * FROM local_folders ORDER BY addedAtEpochMs DESC")
    fun observeFolders(): Flow<List<LocalFolderEntity>>

    @Query("SELECT * FROM local_folders")
    suspend fun getAllFolders(): List<LocalFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: LocalFolderEntity)

    @Query("DELETE FROM local_folders WHERE treeUriString = :treeUriString")
    suspend fun deleteByUri(treeUriString: String)
}

@Dao
interface UserPlaylistDao {
    @Query("SELECT * FROM user_playlists WHERE sourceType = :sourceType ORDER BY createdAtEpochMs DESC")
    fun observePlaylists(sourceType: String): Flow<List<UserPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: UserPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(ref: PlaylistTrackCrossRef)

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTrackIdsForPlaylist(playlistId: String): List<String>

    @Query("DELETE FROM user_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)
}

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE cachedAtEpochMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM lyrics_cache")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM lyrics_cache
        WHERE cacheKey IN (
            SELECT cacheKey FROM lyrics_cache ORDER BY cachedAtEpochMs ASC LIMIT :deleteCount
        )
        """,
    )
    suspend fun deleteOldest(deleteCount: Int)

    @Query("DELETE FROM lyrics_cache")
    suspend fun deleteAll()
}

@Database(
    entities = [
        LocalTrackEntity::class,
        LocalFolderEntity::class,
        UserPlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        LyricsCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MupaDatabase : RoomDatabase() {
    abstract fun localTrackDao(): LocalTrackDao
    abstract fun localFolderDao(): LocalFolderDao
    abstract fun userPlaylistDao(): UserPlaylistDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
}
