# Arsitektur Streaming & Runbook Insiden Mupa (`STREAMING.md`)

> Dokumentasi teknis lapisan streaming Mupa (Tahap 2) — sepenuhnya terisolasi dari UI di balik kontrak `:core:domain` dan `:streaming:api`.

---

## 1. Struktur Modul Streaming & Playback

```text
:core:domain (MusicSource, ArtistRepository)
       ▲
:streaming:api (StreamingAudioProvider, StreamingCatalogProvider, StreamingError)
       ▲
  ┌────┴────────────────────────────┐
  │                                 │
:streaming:innertube        :streaming:jiosaavn
(Primary, priority=10)      (Fallback, priority=20)
- Guest client profiles     - Guest search API
- Tolerant JSON parser      - DES/ECB URL decoder
- Isolated NewPipeExtractor   (AAC 96/160/320 kbps)
       ▲                            ▲
       └────────────┬───────────────┘
                    │
           :streaming:aggregator
           - ProviderRegistry (priority walk + retry backoff)
           - ProviderCircuitBreaker (per-provider)
           - ThreadSafeStreamUrlCache (ConcurrentHashMap)
                    ▲
               :playback
               - MupaPlaybackService (MediaLibraryService + ExoPlayer)
               - MupaAudioCache (SimpleCache 500 MB default)
               - QueuePersistenceStore (JSON di filesDir)
               - DefaultMediaNotificationProvider (ic_notification)
```

---

## 2. Prinsip Desain & Keamanan
1. **Tanpa login / tanpa cookie (Guest Mode — Default D1):** Tidak ada penyimpanan cookie Google/YouTube atau kredensial akun pihak ketiga.
2. **Error Bertipe (`StreamingError`):** Seluruh kegagalan dipetakan ke `Network`, `RateLimited`, `GeoBlocked`, `BotCheck`, `Unavailable`, `ParseError`, `CircuitOpen`, atau `Unknown`.
3. **Thread-Safe URL Cache:** `ThreadSafeStreamUrlCache` berbasis `ConcurrentHashMap` dengan margin keamanan 60 detik sebelum kedaluwarsa (menghilangkan data race `HashMap` yang ditemukan di audit Mochy).
4. **Circuit Breaker Per Penyedia:** Setelah 3 kegagalan berturut-turut (`Network`, `RateLimited`, `BotCheck`, atau `ParseError`), penyedia tersebut dibuka sirkuitnya selama 60 detik sehingga trafik langsung dialihkan ke penyedia cadangan tanpa membuang waktu pengguna.
5. **Isolasi Lisensi GPL-3.0:** `NewPipeExtractor` hanya ditautkan di `:streaming:innertube` (tidak bocor ke `:app`, `:core:*`, atau `:playback`), dengan atribusi di `LICENSE` dan `NOTICE`.

---

## 3. Runbook Insiden (Penanganan Saat Streaming Bermasalah)

### Insiden A: YouTube Memblokir Klien InnerTube (`StreamingError.BotCheck` / HTTP 403)
- **Gejala:** `InnerTubeProvider` gagal pada seluruh `STREAM_WALK_ORDER`; `ProviderCircuitBreaker` membuka sirkuit `innertube` selama 60 detik dan memindahkan pemutaran ke `jiosaavn`.
- **Langkah Perbaikan:**
  1. Periksa `streaming/innertube/src/main/kotlin/com/xeno/mupa/streaming/innertube/InnerTubeClientProfile.kt`.
  2. Perbarui `clientVersion` atau urutan `STREAM_WALK_ORDER` (mis. menaikkan versi `ANDROID_VR` / `IOS` / `WEB_REMIX`).
  3. Bila YouTube mengubah cipher/n-param, naikkan versi `newpipeextractor` di `gradle/libs.versions.toml`.
  4. Jalankan `./gradlew :streaming:innertube:test :streaming:aggregator:test` untuk memastikan seluruh fixture parser & fallback tetap hijau.

### Insiden B: Parser JSON InnerTube Gagal Setelah Perubahan Schema YouTube (`StreamingError.ParseError`)
- **Gejala:** Hasil pencarian kosong atau `ParseError` muncul di log.
- **Langkah Perbaikan:**
  1. Ambil sampel JSON baru dari respons endpoint (tanpa menyertakan data pribadi/cookie).
  2. Tambahkan kasus uji ke `InnerTubeParserFixtureTest.kt`.
  3. Perbarui ekstraksi field di `InnerTubeParser.kt` **tanpa pernah menambahkan `!!`**.

### Insiden C: Penyedia Fallback JioSaavn Down / Rate-Limited (`HTTP 429` / `5xx`)
- **Gejala:** `jiosaavn` mengembalikan `RateLimited` atau `Network`.
- **Dampak:** Circuit breaker otomatis melewati `jiosaavn` selama 60 detik; tidak mempengaruhi pemutaran lokal maupun InnerTube.
- **Langkah Perbaikan:** Jika endpoint atau kunci dekripsi berubah, hanya ubah `streaming/jiosaavn/` dan uji dengan `./gradlew :streaming:jiosaavn:test`.

### Insiden D: Cache Audio Penuh / File Korup
- **Gejala:** Error IO saat memuat ulang lagu yang sama.
- **Langkah Perbaikan:** `MupaAudioCache` menggunakan `LeastRecentlyUsedCacheEvictor` (default 500 MB) dan `FLAG_IGNORE_CACHE_ON_ERROR` sehingga ExoPlayer otomatis jatuh ke jaringan jika blok cache korup. Cache dapat dibersihkan dengan menghapus `cacheDir/mupa_audio_cache`.
