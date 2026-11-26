use lofty::file::{AudioFile, TaggedFileExt};
use lofty::probe::Probe;
use lofty::tag::{Accessor, ItemKey};
use std::io::{Cursor, Read, Seek, SeekFrom};

#[derive(Debug, Clone)]
pub struct ExtractedAudioMetadata {
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub duration_ms: u64,
    pub embedded_lyrics: Option<String>,
    pub fingerprint: String,
}

/// Fingerprint 3-irisan FNV-1a (64 KB awal + 64 KB tengah + 64 KB akhir, sesuai AUDIT.md §7.3).
pub fn compute_fingerprint<R: Read + Seek>(reader: &mut R, file_size: u64) -> String {
    const SLICE: usize = 65_536;
    let mut hash: u64 = 14_695_981_039_346_656_037;
    let offsets: Vec<u64> = if file_size <= (SLICE as u64) * 3 {
        vec![0]
    } else {
        vec![
            0,
            file_size / 2 - (SLICE as u64 / 2),
            file_size.saturating_sub(SLICE as u64),
        ]
    };

    let mut buf = vec![0u8; SLICE];
    for off in offsets {
        if reader.seek(SeekFrom::Start(off)).is_ok() {
            if let Ok(n) = reader.read(&mut buf) {
                for &b in &buf[..n] {
                    hash ^= u64::from(b);
                    hash = hash.wrapping_mul(1_099_511_628_211);
                }
            }
        }
    }
    let _ = reader.seek(SeekFrom::Start(0));
    format!("v2:{file_size}:{hash:016x}")
}

/// Membaca metadata tag audio (MP3/FLAC/Ogg/Opus/M4A) dari byte buffer (aman Scoped Storage).
pub fn extract_metadata_from_bytes(bytes: &[u8], file_size: u64) -> ExtractedAudioMetadata {
    let mut cursor = Cursor::new(bytes);
    let fingerprint = compute_fingerprint(&mut cursor, file_size);
    extract_from_seekable(cursor, fingerprint)
}

#[cfg(unix)]
pub fn extract_metadata_from_file(mut file: std::fs::File, file_size: u64) -> ExtractedAudioMetadata {
    let fingerprint = compute_fingerprint(&mut file, file_size);
    extract_from_seekable(file, fingerprint)
}

fn extract_from_seekable<R: Read + Seek>(
    reader: R,
    fingerprint: String,
) -> ExtractedAudioMetadata {
    let tagged = Probe::new(reader)
        .guess_file_type()
        .ok()
        .and_then(|p| p.read().ok());

    let Some(tagged_file) = tagged else {
        return ExtractedAudioMetadata {
            title: None,
            artist: None,
            album: None,
            duration_ms: 0,
            embedded_lyrics: None,
            fingerprint,
        };
    };

    let duration_ms = tagged_file.properties().duration().as_millis() as u64;
    let tag = tagged_file.primary_tag().or_else(|| tagged_file.first_tag());

    let title = tag.and_then(|t| t.title().map(|s| s.into_owned()));
    let artist = tag.and_then(|t| t.artist().map(|s| s.into_owned()));
    let album = tag.and_then(|t| t.album().map(|s| s.into_owned()));
    let embedded_lyrics = tag.and_then(|t| {
        t.get_string(&ItemKey::Lyrics)
            .map(ToString::to_string)
    });

    ExtractedAudioMetadata {
        title,
        artist,
        album,
        duration_ms,
        embedded_lyrics,
        fingerprint,
    }
}
