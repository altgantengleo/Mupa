use std::collections::{HashMap, HashSet};

#[derive(Debug, Clone, uniffi::Record)]
pub struct LocalTrackRecord {
    pub id: String,
    pub content_uri: String,
    pub original_file_name: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub duration_ms: i64,
    pub imported_at_ms: i64,
    pub is_favorite: bool,
    pub play_count: i64,
    pub last_played_at_ms: i64,
    pub embedded_lyrics: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct EngagementSignal {
    pub track_id: String,
    pub completions: i64,
    pub skips: i64,
    pub last_skip_ms: i64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct PlayLogEntry {
    pub track_id: String,
    pub played_at_ms: i64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RecommendedTrack {
    pub track: LocalTrackRecord,
    pub reason: String,
    pub score: f64,
}

const DAY_MS: f64 = 86_400_000.0;
const HALF_LIFE_DAYS: f64 = 21.0;

fn fnv1a_seeded(key: &str) -> f64 {
    let mut h: u32 = 2_166_136_261;
    for b in key.as_bytes() {
        h ^= u32::from(*b);
        h = h.wrapping_mul(16_777_619);
    }
    f64::from(h % 1000) / 1000.0
}

fn hour_of_day(epoch_ms: i64) -> f64 {
    let secs = (epoch_ms / 1_000).rem_euclid(86_400);
    (secs as f64) / 3600.0
}

/// Algoritma "Mix Mupa" (formula lengkap dari docs/AUDIT.md §7.5):
/// - Afinitas artis, decay paruh-waktu 21 hari, kernel Gaussian jam (sigma 2.2j),
///   transisi Markov (< 20 mnt), penalti baru diputar, bonus rediscovery >= 14 hari,
///   sinyal tuntas vs skip, slot eksplorasi {2, 6, 11, 17}, dan MMR diversitas artis.
pub fn build_mix_mupa(
    tracks: &[LocalTrackRecord],
    play_log: &[PlayLogEntry],
    signals: &HashMap<String, EngagementSignal>,
    current_track_id: Option<&str>,
    seed: i32,
    now_ms: i64,
    limit: usize,
) -> Vec<RecommendedTrack> {
    if tracks.is_empty() || limit == 0 {
        return Vec::new();
    }

    let track_by_id: HashMap<&str, &LocalTrackRecord> =
        tracks.iter().map(|t| (t.id.as_str(), t)).collect();

    let mut artist_aff: HashMap<String, f64> = HashMap::new();
    let mut max_play: i64 = 1;
    for t in tracks {
        max_play = max_play.max(t.play_count);
        let key = t.artist.to_lowercase();
        let entry = artist_aff.entry(key).or_insert(0.0);
        *entry += (t.play_count as f64) + if t.is_favorite { 2.0 } else { 0.0 };
    }
    let max_aff = artist_aff.values().copied().fold(1.0_f64, f64::max);

    let now_hour = hour_of_day(now_ms);
    let mut trend_track: HashMap<&str, f64> = HashMap::new();
    let mut trend_artist: HashMap<String, f64> = HashMap::new();
    let mut time_track: HashMap<&str, f64> = HashMap::new();
    let mut trans_from_anchor: HashMap<&str, f64> = HashMap::new();

    let anchor_id: Option<&str> = current_track_id.or_else(|| {
        play_log.last().and_then(|e| {
            if now_ms - e.played_at_ms < 6 * 3_600_000 {
                Some(e.track_id.as_str())
            } else {
                None
            }
        })
    });

    for (idx, entry) in play_log.iter().enumerate() {
        let age_days = ((now_ms - entry.played_at_ms).max(0) as f64) / DAY_MS;
        let w = 0.5_f64.powf(age_days / HALF_LIFE_DAYS);
        *trend_track.entry(entry.track_id.as_str()).or_insert(0.0) += w;
        if let Some(t) = track_by_id.get(entry.track_id.as_str()) {
            *trend_artist.entry(t.artist.to_lowercase()).or_insert(0.0) += w;
        }
        let h_diff = (hour_of_day(entry.played_at_ms) - now_hour).abs();
        let h_dist = h_diff.min(24.0 - h_diff);
        let tw = w * (-(h_dist * h_dist) / (2.0 * 2.2 * 2.2)).exp();
        *time_track.entry(entry.track_id.as_str()).or_insert(0.0) += tw;

        if idx > 0 {
            let prev = &play_log[idx - 1];
            if Some(prev.track_id.as_str()) == anchor_id
                && (entry.played_at_ms - prev.played_at_ms).abs() < 20 * 60_000
            {
                *trans_from_anchor.entry(entry.track_id.as_str()).or_insert(0.0) += 1.0;
            }
        }
    }

    let max_trend_t = trend_track.values().copied().fold(1.0_f64, f64::max);
    let max_trend_a = trend_artist.values().copied().fold(1.0_f64, f64::max);
    let max_time_t = time_track.values().copied().fold(1.0_f64, f64::max);
    let max_follow = trans_from_anchor.values().copied().fold(1.0_f64, f64::max);
    let anchor_artist = anchor_id
        .and_then(|id| track_by_id.get(id))
        .map(|t| t.artist.to_lowercase());

    let seed_tag = format!("{}|{}", now_ms / 86_400_000, seed);
    let mut scored: Vec<RecommendedTrack> = Vec::with_capacity(tracks.len());

    for t in tracks {
        let a_key = t.artist.to_lowercase();
        let mut total = 0.0;
        let mut best_reason: (&str, f64) = ("Pilihan Mupa", 0.07);

        let mut offer = |val: f64, label: &'static str, weight: f64| {
            let weighted = val * weight;
            if weighted > best_reason.1 {
                best_reason = (label, weighted);
            }
        };

        if t.is_favorite {
            total += 0.30;
            offer(0.30, "Favorit", 1.0);
        }

        let af = artist_aff.get(&a_key).copied().unwrap_or(0.0) / max_aff;
        let artist_score = 0.30 * af;
        total += artist_score;
        if af > 0.5 {
            offer(artist_score, "Artis favoritmu", 1.0);
        }

        total += 0.12 * ((1.0 + t.play_count.max(0) as f64).ln() / (1.0 + max_play as f64).ln());

        let tr = 0.24 * (trend_track.get(t.id.as_str()).copied().unwrap_or(0.0) / max_trend_t)
            + 0.16 * (trend_artist.get(&a_key).copied().unwrap_or(0.0) / max_trend_a);
        total += tr;
        offer(tr, "Lagi sering kamu putar", 0.8);

        if play_log.len() >= 12 {
            let time_s = 0.30 * (time_track.get(t.id.as_str()).copied().unwrap_or(0.0) / max_time_t);
            total += time_s;
            offer(time_s, "Pas untuk jam ini", 1.4);
        }

        if let Some(&f_cnt) = trans_from_anchor.get(t.id.as_str()) {
            let follow_s = 0.32 * (f_cnt / max_follow);
            total += follow_s;
            offer(follow_s, "Biasanya setelah ini", 1.9);
        }

        if anchor_artist.as_deref() == Some(a_key.as_str()) && current_track_id != Some(t.id.as_str()) {
            total += 0.16;
            offer(0.16, "Mirip yang diputar", 1.0);
        }

        if t.last_played_at_ms > 0 {
            let days = ((now_ms - t.last_played_at_ms).max(0) as f64) / DAY_MS;
            if days < 1.0 {
                total -= 0.60;
            } else if days < 3.0 {
                total -= 0.30;
            } else if days < 7.0 {
                total -= 0.10;
            } else if t.play_count >= 2 && days >= 14.0 {
                let redisc = 0.28 + ((days - 14.0) / 100.0).min(0.20);
                total += redisc;
                offer(redisc, "Lama tak diputar", 1.0);
            }
        }

        if t.play_count == 0 {
            let import_days = ((now_ms - t.imported_at_ms).max(0) as f64) / DAY_MS;
            let new_s = 0.12 + if import_days < 30.0 { 0.10 } else { 0.0 };
            total += new_s;
            offer(new_s, "Baru buatmu", 1.0);
        }

        if let Some(sig) = signals.get(&t.id) {
            if sig.completions + sig.skips >= 2 {
                let rate = (sig.completions as f64 + 1.0) / ((sig.completions + sig.skips) as f64 + 2.0);
                let eng = (rate - 0.5) * 0.5;
                total += eng;
                if rate > 0.75 && sig.completions >= 2 {
                    offer(eng.max(0.12), "Selalu kamu tuntaskan", 1.1);
                }
            }
            if sig.last_skip_ms > 0 {
                let skip_days = ((now_ms - sig.last_skip_ms).max(0) as f64) / DAY_MS;
                if skip_days < 3.0 {
                    total -= 0.35;
                } else if skip_days < 14.0 {
                    total -= 0.12;
                }
            }
            if sig.skips >= 3 && sig.completions == 0 {
                total -= 0.35;
            }
        }

        if current_track_id == Some(t.id.as_str()) {
            total -= 1.0;
        }

        total += 0.06 * fnv1a_seeded(&format!("{}{}", t.id, seed_tag));

        scored.push(RecommendedTrack {
            track: t.clone(),
            reason: best_reason.0.to_string(),
            score: total,
        });
    }

    scored.sort_by(|a, b| b.score.total_cmp(&a.score));

    // Pool eksplorasi (play_count <= 1) untuk slot {2, 6, 11, 17}
    let mut explore_pool: Vec<RecommendedTrack> = scored
        .iter()
        .filter(|r| r.track.play_count <= 1 && current_track_id != Some(r.track.id.as_str()))
        .cloned()
        .collect();
    explore_pool.sort_by(|a, b| {
        let sa = fnv1a_seeded(&format!("e{}{}", a.track.id, seed_tag));
        let sb = fnv1a_seeded(&format!("e{}{}", b.track.id, seed_tag));
        sb.total_cmp(&sa)
    });

    let explore_slots: HashSet<usize> = [2, 6, 11, 17].into_iter().collect();
    let max_out = limit.min(30).min(scored.len());
    let mut result: Vec<RecommendedTrack> = Vec::with_capacity(max_out);
    let mut used_ids: HashSet<String> = HashSet::new();
    let mut artist_counts: HashMap<String, usize> = HashMap::new();

    for slot in 0..max_out {
        let artist_cap = if slot < 12 { 2 } else { 3 };
        let prev_artist = result.last().map(|r| r.track.artist.to_lowercase());

        if explore_slots.contains(&slot) {
            if let Some(pos) = explore_pool.iter().position(|c| {
                !used_ids.contains(&c.track.id)
                    && artist_counts.get(&c.track.artist.to_lowercase()).copied().unwrap_or(0) < artist_cap
            }) {
                let mut picked = explore_pool.remove(pos);
                if picked.reason == "Pilihan Mupa" {
                    picked.reason = if picked.track.play_count == 0 {
                        "Coba ini".to_string()
                    } else {
                        "Coba lagi".to_string()
                    };
                }
                used_ids.insert(picked.track.id.clone());
                *artist_counts.entry(picked.track.artist.to_lowercase()).or_insert(0) += 1;
                result.push(picked);
                continue;
            }
        }

        // MMR diversity pass (lookahead 40 item, dengan fallback bila semua tersisa melampaui cap)
        let mut best_idx: Option<usize> = None;
        let mut best_mmr = f64::NEG_INFINITY;
        let mut fallback_idx: Option<usize> = None;
        let mut fallback_mmr = f64::NEG_INFINITY;
        let mut inspected = 0;

        for (i, cand) in scored.iter().enumerate() {
            if used_ids.contains(&cand.track.id) {
                continue;
            }
            inspected += 1;
            let a_key = cand.track.artist.to_lowercase();
            let count = artist_counts.get(&a_key).copied().unwrap_or(0);
            let same_prev_penalty = if prev_artist.as_deref() == Some(a_key.as_str()) { 0.20 } else { 0.0 };
            let mmr = cand.score - 0.18 * (count as f64) - same_prev_penalty;
            if count < artist_cap {
                if mmr > best_mmr {
                    best_mmr = mmr;
                    best_idx = Some(i);
                }
            } else if mmr > fallback_mmr {
                fallback_mmr = mmr;
                fallback_idx = Some(i);
            }
            if inspected >= 40 {
                break;
            }
        }

        let Some(idx) = best_idx.or(fallback_idx) else { break };
        let picked = scored[idx].clone();
        used_ids.insert(picked.track.id.clone());
        *artist_counts.entry(picked.track.artist.to_lowercase()).or_insert(0) += 1;
        result.push(picked);
    }

    result
}

/// "Ulangi lagi": (play_count >= 2 || favorite) && last_played >= 10 hari lalu.
pub fn build_repeat_again(
    tracks: &[LocalTrackRecord],
    now_ms: i64,
    limit: usize,
) -> Vec<LocalTrackRecord> {
    let ten_days_ms = 10 * 86_400_000_i64;
    let mut filtered: Vec<LocalTrackRecord> = tracks
        .iter()
        .filter(|t| {
            (t.play_count >= 2 || t.is_favorite)
                && t.last_played_at_ms > 0
                && (now_ms - t.last_played_at_ms) >= ten_days_ms
        })
        .cloned()
        .collect();
    filtered.sort_by(|a, b| b.play_count.cmp(&a.play_count));
    filtered.truncate(limit.min(30));
    filtered
}

/// "Belum diputar": play_count == 0, terbaru lebih dulu.
pub fn build_unplayed(tracks: &[LocalTrackRecord], limit: usize) -> Vec<LocalTrackRecord> {
    let mut filtered: Vec<LocalTrackRecord> = tracks
        .iter()
        .filter(|t| t.play_count == 0)
        .cloned()
        .collect();
    filtered.sort_by(|a, b| b.imported_at_ms.cmp(&a.imported_at_ms));
    filtered.truncate(limit.min(30));
    filtered
}

#[cfg(test)]
mod tests {
    use super::*;

    fn mk_track(id: &str, artist: &str, plays: i64, fav: bool, last_played_days_ago: i64, now: i64) -> LocalTrackRecord {
        LocalTrackRecord {
            id: id.into(),
            content_uri: format!("content://media/audio/{id}"),
            original_file_name: format!("{artist} - Song {id}.flac"),
            title: format!("Song {id}"),
            artist: artist.into(),
            album: None,
            duration_ms: 210_000,
            imported_at_ms: now - 5 * 86_400_000,
            is_favorite: fav,
            play_count: plays,
            last_played_at_ms: if last_played_days_ago > 0 { now - last_played_days_ago * 86_400_000 } else { 0 },
            embedded_lyrics: None,
        }
    }

    #[test]
    fn mix_mupa_enforces_artist_diversity_and_exploration_slot() {
        let now = 1_750_000_000_000_i64;
        let tracks = vec![
            mk_track("1", "Hindia", 20, true, 16, now),
            mk_track("2", "Hindia", 19, true, 15, now),
            mk_track("3", "Hindia", 18, true, 14, now),
            mk_track("4", "Nadin", 12, true, 20, now),
            mk_track("5", "Tulus", 0, false, 0, now),
            mk_track("6", "Reality Club", 10, false, 18, now),
        ];
        let mix = build_mix_mupa(&tracks, &[], &HashMap::new(), None, 0, now, 6);
        assert_eq!(mix.len(), 6);
        let hindia_in_top_5 = mix.iter().take(5).filter(|r| r.track.artist == "Hindia").count();
        assert!(hindia_in_top_5 <= 2);
        // Slot 2 (index 2) is an exploration slot -> should pick unplayed track "5"
        assert_eq!(mix[2].track.id, "5");
    }

    #[test]
    fn repeat_again_and_unplayed_follow_audit_rules() {
        let now = 1_750_000_000_000_i64;
        let tracks = vec![
            mk_track("old_fav", "Hindia", 5, true, 12, now),
            mk_track("recent_fav", "Nadin", 10, true, 2, now),
            mk_track("never", "Tulus", 0, false, 0, now),
        ];
        let repeat = build_repeat_again(&tracks, now, 10);
        assert_eq!(repeat.len(), 1);
        assert_eq!(repeat[0].id, "old_fav");

        let unplayed = build_unplayed(&tracks, 10);
        assert_eq!(unplayed.len(), 1);
        assert_eq!(unplayed[0].id, "never");
    }
}
