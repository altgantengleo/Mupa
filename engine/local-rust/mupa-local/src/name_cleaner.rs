use regex::Regex;
use std::sync::OnceLock;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct DisplayTrackParts {
    pub title: String,
    pub artist: String,
    pub recovered_artist: String,
}

fn ext_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"(?i)\.(?:mp3|flac|m4a|wav|ogg|opus|aac|webm)$").unwrap())
}

fn split_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"\s+[-–—|]\s+").unwrap())
}

fn leading_track_num_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"^\d{1,3}\s*[\.\)\-]\s+").unwrap())
}

fn bracket_clutter_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| {
        Regex::new(
            r"(?i)\s*[\(\[\{][^)\]}]*(?:slowed|sped\s*up|nightcore|daycore|reverb|8d|bass\s*boost|official|audio|video|visualizer|lyrics?|lirik|remaster|hd|hq|4k|320kbps|flac|mp3|vevo|topic)[^)\]}]*[\)\]\}]",
        )
        .unwrap()
    })
}

fn modifier_segment_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| {
        Regex::new(
            r"(?i)^(?:(?:ultra|super|extreme|extra|very)\s+)?(?:slowed(?:\s*\+\s*reverb)?|sped\s*up|nightcore|daycore|reverb(?:ed)?|8d(?:\s+audio)?|instrumental|acapella|karaoke|official(?:\s+(?:music|lyric))?\s+(?:video|audio|visualizer)|lyrics?|lirik|audio\s+only|remaster(?:ed)?|hq|hd|4k|320kbps|flac|topic|vevo)$",
        )
        .unwrap()
    })
}

fn artist_tail_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"(?i)\s*(?:-\s*topic|vevo|official)$").unwrap())
}

pub fn clean_title_text(raw: &str) -> String {
    let mut s = bracket_clutter_re().replace_all(raw, "").trim().to_string();
    // Hapus segmen modifier di belakang bila ada pemisah
    let parts: Vec<&str> = split_re().split(&s).map(str::trim).filter(|p| !p.is_empty()).collect();
    if parts.len() > 1 {
        let kept: Vec<&str> = parts
            .into_iter()
            .filter(|p| !modifier_segment_re().is_match(p))
            .collect();
        if !kept.is_empty() {
            s = kept.join(" - ");
        }
    }
    s.trim().to_string()
}

pub fn clean_artist_text(raw: &str) -> String {
    let no_num = leading_track_num_re().replace(raw.trim(), "");
    let no_bracket = bracket_clutter_re().replace_all(&no_num, "");
    artist_tail_re().replace(&no_bracket, "").trim().to_string()
}

/// Pemformatan non-destruktif "Hapus Nama Artis dari Judul" (sesuai docs/AUDIT.md §7.9).
/// Nama artis file lokal selalu bisa dipulihkan dari `original_file_name`.
pub fn format_track_display(
    original_file_name: Option<&str>,
    fallback_title: &str,
    fallback_artist: &str,
    strip_artist_name: bool,
) -> DisplayTrackParts {
    if let Some(raw_file) = original_file_name.filter(|s| !s.trim().is_empty()) {
        let without_ext = ext_re().replace(raw_file.trim(), "");
        let segments: Vec<&str> = split_re()
            .split(&without_ext)
            .map(str::trim)
            .filter(|p| !p.is_empty() && !modifier_segment_re().is_match(p))
            .collect();

        if segments.len() >= 2 {
            let recovered_artist = clean_artist_text(segments[0]);
            let title_joined = segments[1..].join(" - ");
            let clean_title = clean_title_text(&title_joined);
            let final_title = if clean_title.is_empty() {
                fallback_title.to_string()
            } else {
                clean_title
            };
            let final_artist = if strip_artist_name {
                String::new()
            } else if recovered_artist.is_empty() {
                fallback_artist.to_string()
            } else {
                recovered_artist.clone()
            };
            return DisplayTrackParts {
                title: final_title,
                artist: final_artist,
                recovered_artist: if recovered_artist.is_empty() {
                    fallback_artist.to_string()
                } else {
                    recovered_artist
                },
            };
        }

        let single_title = clean_title_text(&without_ext);
        let recovered = clean_artist_text(fallback_artist);
        return DisplayTrackParts {
            title: if single_title.is_empty() {
                fallback_title.to_string()
            } else {
                single_title
            },
            artist: if strip_artist_name {
                String::new()
            } else {
                recovered.clone()
            },
            recovered_artist: recovered,
        };
    }

    let recovered = clean_artist_text(fallback_artist);
    DisplayTrackParts {
        title: fallback_title.to_string(),
        artist: if strip_artist_name {
            String::new()
        } else {
            recovered.clone()
        },
        recovered_artist: recovered,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn strips_and_restores_artist_non_destructively() {
        let raw = "01. Hindia - Evaluasi (Official Audio) - Ultra Slowed.flac";
        let hidden = format_track_display(Some(raw), "Evaluasi", "Unknown", true);
        assert_eq!(hidden.title, "Evaluasi");
        assert_eq!(hidden.artist, "");
        assert_eq!(hidden.recovered_artist, "Hindia");

        let shown = format_track_display(Some(raw), "Evaluasi", "Unknown", false);
        assert_eq!(shown.title, "Evaluasi");
        assert_eq!(shown.artist, "Hindia");
    }

    #[test]
    fn prevents_modifier_segment_from_becoming_title() {
        let raw = "Nadin Amizah - Bertaut - Slowed + Reverb.mp3";
        let parts = format_track_display(Some(raw), "Fallback", "Unknown", false);
        assert_eq!(parts.title, "Bertaut");
        assert_eq!(parts.artist, "Nadin Amizah");
    }
}
