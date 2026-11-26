use crate::recommender::{EngagementSignal, LocalTrackRecord, PlayLogEntry};
use rusqlite::{params, Connection};
use std::collections::HashMap;
use thiserror::Error;

#[derive(Debug, Error, uniffi::Error)]
pub enum LocalEngineError {
    #[error("SQLite error: {message}")]
    Database { message: String },
    #[error("I/O error: {message}")]
    Io { message: String },
}

impl From<rusqlite::Error> for LocalEngineError {
    fn from(value: rusqlite::Error) -> Self {
        Self::Database {
            message: value.to_string(),
        }
    }
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct LocalArtistSummary {
    pub id: String,
    pub name: String,
    pub track_count: i64,
    pub total_plays: i64,
}

pub struct LocalDb {
    conn: Connection,
}

impl LocalDb {
    pub fn open(path: &str) -> Result<Self, LocalEngineError> {
        let conn = if path == ":memory:" {
            Connection::open_in_memory()?
        } else {
            Connection::open(path)?
        };
        conn.execute_batch(
            "
            PRAGMA journal_mode = WAL;
            PRAGMA synchronous = NORMAL;

            CREATE TABLE IF NOT EXISTS tracks (
                id TEXT PRIMARY KEY,
                content_uri TEXT NOT NULL UNIQUE,
                original_file_name TEXT NOT NULL,
                fingerprint TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                imported_at_ms INTEGER NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                play_count INTEGER NOT NULL DEFAULT 0,
                last_played_at_ms INTEGER NOT NULL DEFAULT 0,
                embedded_lyrics TEXT
            );

            CREATE TABLE IF NOT EXISTS play_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                track_id TEXT NOT NULL,
                played_at_ms INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS engagement_signals (
                track_id TEXT PRIMARY KEY,
                completions INTEGER NOT NULL DEFAULT 0,
                skips INTEGER NOT NULL DEFAULT 0,
                last_skip_ms INTEGER NOT NULL DEFAULT 0
            );
            ",
        )?;
        Ok(Self { conn })
    }

    pub fn upsert_track(
        &self,
        record: &LocalTrackRecord,
        fingerprint: &str,
    ) -> Result<(), LocalEngineError> {
        self.conn.execute(
            "
            INSERT INTO tracks (
                id, content_uri, original_file_name, fingerprint, title, artist, album,
                duration_ms, imported_at_ms, is_favorite, play_count, last_played_at_ms, embedded_lyrics
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)
            ON CONFLICT(content_uri) DO UPDATE SET
                original_file_name = excluded.original_file_name,
                fingerprint = excluded.fingerprint,
                title = excluded.title,
                artist = excluded.artist,
                album = excluded.album,
                duration_ms = excluded.duration_ms,
                embedded_lyrics = COALESCE(excluded.embedded_lyrics, tracks.embedded_lyrics)
            ",
            params![
                record.id,
                record.content_uri,
                record.original_file_name,
                fingerprint,
                record.title,
                record.artist,
                record.album,
                record.duration_ms,
                record.imported_at_ms,
                if record.is_favorite { 1 } else { 0 },
                record.play_count,
                record.last_played_at_ms,
                record.embedded_lyrics,
            ],
        )?;
        Ok(())
    }

    pub fn list_tracks(&self) -> Result<Vec<LocalTrackRecord>, LocalEngineError> {
        let mut stmt = self.conn.prepare(
            "SELECT id, content_uri, original_file_name, title, artist, album, duration_ms,
                    imported_at_ms, is_favorite, play_count, last_played_at_ms, embedded_lyrics
             FROM tracks
             ORDER BY imported_at_ms DESC",
        )?;
        let rows = stmt.query_map([], |row| {
            Ok(LocalTrackRecord {
                id: row.get(0)?,
                content_uri: row.get(1)?,
                original_file_name: row.get(2)?,
                title: row.get(3)?,
                artist: row.get(4)?,
                album: row.get(5)?,
                duration_ms: row.get(6)?,
                imported_at_ms: row.get(7)?,
                is_favorite: row.get::<_, i64>(8)? != 0,
                play_count: row.get(9)?,
                last_played_at_ms: row.get(10)?,
                embedded_lyrics: row.get(11)?,
            })
        })?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r?);
        }
        Ok(out)
    }

    pub fn record_play(&self, track_id: &str, now_ms: i64) -> Result<(), LocalEngineError> {
        self.conn.execute(
            "UPDATE tracks SET play_count = play_count + 1, last_played_at_ms = ?2 WHERE id = ?1",
            params![track_id, now_ms],
        )?;
        self.conn.execute(
            "INSERT INTO play_log (track_id, played_at_ms) VALUES (?1, ?2)",
            params![track_id, now_ms],
        )?;
        self.conn.execute(
            "DELETE FROM play_log WHERE id NOT IN (SELECT id FROM play_log ORDER BY id DESC LIMIT 400)",
            [],
        )?;
        Ok(())
    }

    pub fn record_engagement(
        &self,
        track_id: &str,
        completed: bool,
        now_ms: i64,
    ) -> Result<(), LocalEngineError> {
        if completed {
            self.conn.execute(
                "INSERT INTO engagement_signals (track_id, completions, skips, last_skip_ms)
                 VALUES (?1, 1, 0, 0)
                 ON CONFLICT(track_id) DO UPDATE SET completions = completions + 1",
                params![track_id],
            )?;
        } else {
            self.conn.execute(
                "INSERT INTO engagement_signals (track_id, completions, skips, last_skip_ms)
                 VALUES (?1, 0, 1, ?2)
                 ON CONFLICT(track_id) DO UPDATE SET skips = skips + 1, last_skip_ms = ?2",
                params![track_id, now_ms],
            )?;
        }
        Ok(())
    }

    pub fn list_play_log(&self) -> Result<Vec<PlayLogEntry>, LocalEngineError> {
        let mut stmt = self
            .conn
            .prepare("SELECT track_id, played_at_ms FROM play_log ORDER BY played_at_ms ASC")?;
        let rows = stmt.query_map([], |row| {
            Ok(PlayLogEntry {
                track_id: row.get(0)?,
                played_at_ms: row.get(1)?,
            })
        })?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r?);
        }
        Ok(out)
    }

    pub fn list_signals(&self) -> Result<HashMap<String, EngagementSignal>, LocalEngineError> {
        let mut stmt = self
            .conn
            .prepare("SELECT track_id, completions, skips, last_skip_ms FROM engagement_signals")?;
        let rows = stmt.query_map([], |row| {
            Ok(EngagementSignal {
                track_id: row.get(0)?,
                completions: row.get(1)?,
                skips: row.get(2)?,
                last_skip_ms: row.get(3)?,
            })
        })?;
        let mut map = HashMap::new();
        for r in rows {
            let sig = r?;
            map.insert(sig.track_id.clone(), sig);
        }
        Ok(map)
    }

    pub fn list_local_artists(&self) -> Result<Vec<LocalArtistSummary>, LocalEngineError> {
        let mut stmt = self.conn.prepare(
            "SELECT artist, COUNT(*) AS cnt, SUM(play_count) AS plays
             FROM tracks
             WHERE TRIM(artist) != ''
             GROUP BY LOWER(artist)
             ORDER BY plays DESC, cnt DESC",
        )?;
        let rows = stmt.query_map([], |row| {
            let name: String = row.get(0)?;
            Ok(LocalArtistSummary {
                id: format!("local-art-{}", name.to_lowercase().replace(' ', "-")),
                name,
                track_count: row.get(1)?,
                total_plays: row.get(2)?,
            })
        })?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r?);
        }
        Ok(out)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stores_tracks_records_plays_and_aggregates_local_artists() {
        let db = LocalDb::open(":memory:").unwrap();
        let rec = LocalTrackRecord {
            id: "loc-1".into(),
            content_uri: "content://media/external/audio/media/101".into(),
            original_file_name: "Hindia - Evaluasi.flac".into(),
            title: "Evaluasi".into(),
            artist: "Hindia".into(),
            album: Some("Menari Dengan Bayangan".into()),
            duration_ms: 214_000,
            imported_at_ms: 1_700_000_000_000,
            is_favorite: true,
            play_count: 0,
            last_played_at_ms: 0,
            embedded_lyrics: None,
        };
        db.upsert_track(&rec, "v2:1234:abcd").unwrap();
        db.record_play("loc-1", 1_700_000_100_000).unwrap();

        let tracks = db.list_tracks().unwrap();
        assert_eq!(tracks.len(), 1);
        assert_eq!(tracks[0].play_count, 1);

        let artists = db.list_local_artists().unwrap();
        assert_eq!(artists.len(), 1);
        assert_eq!(artists[0].name, "Hindia");
        assert_eq!(artists[0].total_plays, 1);
    }
}
