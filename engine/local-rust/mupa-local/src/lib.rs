pub mod db;
pub mod name_cleaner;
pub mod recommender;
pub mod tags;

use db::{LocalArtistSummary, LocalDb, LocalEngineError};
use name_cleaner::{format_track_display, DisplayTrackParts};
use recommender::{build_mix_mupa, build_repeat_again, build_unplayed, LocalTrackRecord, RecommendedTrack};
use std::sync::Mutex;

uniffi::setup_scaffolding!();

#[uniffi::export]
pub fn clean_local_track_display(
    original_file_name: Option<String>,
    fallback_title: String,
    fallback_artist: String,
    strip_artist_name: bool,
) -> DisplayTrackParts {
    format_track_display(
        original_file_name.as_deref(),
        &fallback_title,
        &fallback_artist,
        strip_artist_name,
    )
}

#[derive(uniffi::Object)]
pub struct LocalLibraryEngine {
    db: Mutex<LocalDb>,
}

#[uniffi::export]
impl LocalLibraryEngine {
    #[uniffi::constructor]
    pub fn open(db_path: String) -> Result<Self, LocalEngineError> {
        let db = LocalDb::open(&db_path)?;
        Ok(Self { db: Mutex::new(db) })
    }

    /// Scoped Storage compliant ingestion: Kotlin reads bytes from MediaStore/SAF ContentResolver
    /// and passes `content_uri` + `original_file_name` + header/file bytes to Rust.
    pub fn ingest_audio_bytes(
        &self,
        id: String,
        content_uri: String,
        original_file_name: String,
        file_size: i64,
        bytes: Vec<u8>,
        fallback_title: String,
        fallback_artist: String,
        fallback_duration_ms: i64,
        imported_at_ms: i64,
    ) -> Result<LocalTrackRecord, LocalEngineError> {
        let extracted = tags::extract_metadata_from_bytes(&bytes, file_size.max(0) as u64);
        let parsed_name = format_track_display(
            Some(&original_file_name),
            extracted.title.as_deref().unwrap_or(&fallback_title),
            extracted.artist.as_deref().unwrap_or(&fallback_artist),
            false,
        );

        let record = LocalTrackRecord {
            id,
            content_uri,
            original_file_name,
            title: extracted.title.unwrap_or(parsed_name.title),
            artist: extracted.artist.unwrap_or(parsed_name.recovered_artist),
            album: extracted.album,
            duration_ms: if extracted.duration_ms > 0 {
                extracted.duration_ms as i64
            } else {
                fallback_duration_ms
            },
            imported_at_ms,
            is_favorite: false,
            play_count: 0,
            last_played_at_ms: 0,
            embedded_lyrics: extracted.embedded_lyrics,
        };

        let guard = self.db.lock().map_err(|_| LocalEngineError::Database {
            message: "Mutex poisoned".into(),
        })?;
        guard.upsert_track(&record, &extracted.fingerprint)?;
        Ok(record)
    }

    /// Scoped Storage Android ParcelFileDescriptor ingestion (dup'd raw fd owned by Rust).
    #[cfg(unix)]
    pub fn ingest_audio_fd(
        &self,
        id: String,
        content_uri: String,
        original_file_name: String,
        file_size: i64,
        raw_fd: i32,
        fallback_title: String,
        fallback_artist: String,
        fallback_duration_ms: i64,
        imported_at_ms: i64,
    ) -> Result<LocalTrackRecord, LocalEngineError> {
        use std::os::fd::FromRawFd;
        // SAFETY: Kotlin passes a detached ParcelFileDescriptor.dup().detachFd() exclusively owned by this call.
        let file = unsafe { std::fs::File::from_raw_fd(raw_fd) };
        let extracted = tags::extract_metadata_from_file(file, file_size.max(0) as u64);
        let parsed_name = format_track_display(
            Some(&original_file_name),
            extracted.title.as_deref().unwrap_or(&fallback_title),
            extracted.artist.as_deref().unwrap_or(&fallback_artist),
            false,
        );
        let record = LocalTrackRecord {
            id,
            content_uri,
            original_file_name,
            title: extracted.title.unwrap_or(parsed_name.title),
            artist: extracted.artist.unwrap_or(parsed_name.recovered_artist),
            album: extracted.album,
            duration_ms: if extracted.duration_ms > 0 {
                extracted.duration_ms as i64
            } else {
                fallback_duration_ms
            },
            imported_at_ms,
            is_favorite: false,
            play_count: 0,
            last_played_at_ms: 0,
            embedded_lyrics: extracted.embedded_lyrics,
        };
        let guard = self.db.lock().map_err(|_| LocalEngineError::Database {
            message: "Mutex poisoned".into(),
        })?;
        guard.upsert_track(&record, &extracted.fingerprint)?;
        Ok(record)
    }

    pub fn list_tracks(&self) -> Result<Vec<LocalTrackRecord>, LocalEngineError> {
        let guard = self.db.lock().map_err(|_| LocalEngineError::Database {
            message: "Mutex poisoned".into(),
        })?;
        guard.list_tracks()
    }

    pub fn record_play(&self, track_id: String, now_ms: i64) -> Result<(), LocalEngineError> {
        let guard = self.db.lock().map_err(|_| LocalEngineError::Database {
            message: "Mutex poisoned".into(),
        })?;
        guard.record_play(&track_id, now_ms)
    }

    pub fn record_engagement(
        &self,
        track_id: String,
        completed: bool,
        now_ms: i64,
    ) -> Result<(), LocalEngineError> {
        let guard = self.db.lock().map_err(|_| LocalEngineError::Database {
            message: "Mutex poisoned".into(),
        })?;
        guard.record_engagement(&track_id, completed, now_ms)
    }

    pub fn get_mix_mupa(
        &self,
        current_track_id: Option<String>,
        seed: i32,
        now_ms: i64,
        limit: u32,
    ) -> Result<Vec<RecommendedTrack>, LocalEngineError> {
        let guard = self.db.lock().map_err(|_| LocalEngineError::Database {
            message: "Mutex poisoned".into(),
        })?;
        let tracks = guard.list_tracks()?;
        let log = guard.list_play_log()?;
        let signals = guard.list_signals()?;
        Ok(build_mix_mupa(
            &tracks,
            &log,
            &signals,
            current_track_id.as_deref(),
            seed,
            now_ms,
            limit as usize,
        ))
    }

    pub fn get_repeat_again(
        &self,
        now_ms: i64,
        limit: u32,
    ) -> Result<Vec<LocalTrackRecord>, LocalEngineError> {
        let guard = self.db.lock().map_err(|_| LocalEngineError::Database {
            message: "Mutex poisoned".into(),
        })?;
        let tracks = guard.list_tracks()?;
        Ok(build_repeat_again(&tracks, now_ms, limit as usize))
    }

    pub fn get_unplayed(&self, limit: u32) -> Result<Vec<LocalTrackRecord>, LocalEngineError> {
        let guard = self.db.lock().map_err(|_| LocalEngineError::Database {
            message: "Mutex poisoned".into(),
        })?;
        let tracks = guard.list_tracks()?;
        Ok(build_unplayed(&tracks, limit as usize))
    }

    pub fn get_local_artists(&self) -> Result<Vec<LocalArtistSummary>, LocalEngineError> {
        let guard = self.db.lock().map_err(|_| LocalEngineError::Database {
            message: "Mutex poisoned".into(),
        })?;
        guard.list_local_artists()
    }
}
