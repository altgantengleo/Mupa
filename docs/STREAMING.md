# Arsitektur Streaming, Antrean Cerdas & Runbook Insiden Mupa (`STREAMING.md`)

> Dokumentasi teknis lapisan streaming & multi-provider Mupa (Tahap 2 + Fase 2T) — sepenuhnya terisolasi dari UI di balik kontrak `:core:domain` dan `:streaming:api`.

---

## 1. Struktur Modul Streaming & Multi-Provider (Paket 1 Aktif)

Mupa memisahkan kapabilitas menjadi **`CatalogProvider`**, **`StreamProvider`**, **`StreamingRadioProvider`**, dan **`StreamingLyricsProvider`**. Total **3 provider aktif** (tanpa login, tanpa API key rahasia, tanpa DRM, kompatibel GPL-3.0):

```text
:core:domain (MusicSource, ArtistRepository)
       ▲
:streaming:api (CatalogProvider, StreamProvider, StreamingRadioProvider,
                StreamingLyricsProvider, StreamingError, MupaStreamingDiagnostics)
       ▲
  ┌────┴────────────────────────────────────────────────────────┐
  │                         │                                   │
DeezerCatalogProvider   ItunesCatalogProvider          InnerTubeProvider
(Catalog priority=10)   (Catalog priority=20)          (Stream priority=10,
- api.deezer.com        - itunes.apple.com (ID)         Radio, Catalog priority=30)
- Foto artis HD         - Cover 600x600 & durasi ms    - POST /youtubei/v1/next
- Tanpa login/key/DRM   - Tanpa login/key/DRM          - RDAMVM + Automix + Continuation
                                                       - NewPipeExtractor (GPL-3.0)
       ▲                         ▲                              ▲
       └─────────────────────────┴──────────────────────────────┘
                                 │
                        :streaming:aggregator
                        - MultiProviderCatalogAggregator (paralel progresif + dedupe)
                        - CrossProviderTrackMatcher (judul + artis + durasi +-3 dtk, min 0.72)
                        - SwrCache (stale-while-revalidate lokal)
                        - ProviderPrewarmer (DNS/TLS/HTTP2 saat start)
                        - ProviderRegistry (hedging 800ms + circuit breaker + URL cache)
                                 ▲
                            :playback
                            - MupaPlaybackService (MediaLibraryService + ExoPlayer)
                            - MupaAudioCache (SimpleCache 500 MB)
                            - QueuePersistenceStore (JSON: QueueSource + continuation + shuffle seed)
```

---

## 2. Perilaku Antrean Cerdas, Shuffle & Repeat

- **Sumber Antrean (`QueueSource`):** `Radio(seed)`, `Artist(id)`, `PlaylistOrAlbum(id)`, `Local(label)`, `MixMupa`.
- **Ketuk hasil pencarian (Streaming):** Memutar `[lagu yang diketuk]` lalu memuat Radio via `POST /youtubei/v1/next` (`playlistId = RDAMVM<videoId>`, fallback automix/artis), disaring oleh `SmartQueueFilter` (nol versi live/slowed/acoustic/remix/karaoke, durasi 60 dtk–8 mnt, unik per `(judul ternormalisasi + artis)`, maks 2 lagu berurutan artis sama secara lunak, hindari 20 lagu terakhir). Muat lanjutan otomatis saat sisa antrean `<= 3`.
- **Shuffle deterministik berbasis seed (`SmartShuffleEngine`):** Hanya mengacak item sesudah `currentIndex`; lagu aktif dan riwayat sebelumnya tidak bergeser sehingga tombol **Sebelumnya** selalu tepat. Mematikan shuffle mengembalikan urutan asli untuk sisa antrean. Item lanjutan radio yang baru datang saat shuffle ON disisipkan acak sambil menghindari 2 lagu berurutan dari artis sama bila memungkinkan.
- **Perilaku Repeat pada antrean tak berujung (Radio / Mix Mupa):**
  - **`RepeatMode.OFF`:** Putar berurutan; ketika sisa `<= 3` lagu, radio memuat halaman lanjutan otomatis.
  - **`RepeatMode.ALL`:** Mengulang seluruh item yang **sudah termuat** di antrean saat ini (Media3 `REPEAT_MODE_ALL`).
  - **`RepeatMode.ONE`:** Mengulang lagu aktif terus-menerus (`REPEAT_MODE_ONE`) tanpa memajukan antrean.

---

## 3. Prinsip Kecepatan, Timeout & Keamanan
1. **Tanpa login / tanpa API key / tanpa DRM** di repositori maupun runtime.
2. **Timeout Ketat (`OkHttpClient`):** `connectTimeout = 3 dtk`, `readTimeout = 6 dtk`, `callTimeout = 7 dtk`, `ConnectionPool(8, 5 menit)` dengan HTTP/2.
3. **Pencocokan Lintas Provider:** ID katalog `dz_*` / `it_*` dicocokkan ke stream InnerTube dengan skor judul + artis + toleransi durasi **±3 detik** (ambang minimal `0.72`). Jika skor rendah, Mupa **menolak memutar lagu yang salah** dan mengembalikan `StreamingError.Unavailable`.
4. **Pra-panas & Pra-resolusi Terbatas:** `ProviderPrewarmer` hanya menghangatkan koneksi ke 3 host saat start; `MupaPlayerViewModel.preResolveNextTrack()` hanya mempra-resolusi **1 lagu berikutnya** di antrean (tidak pernah massal).
5. **Instrumentasi Debug Lokal:** `MupaStreamingDiagnostics` hanya aktif pada build `DEBUG` (`adb logcat -s MupaPerf:D`), tidak pernah dikirim ke server mana pun.

---

## 4. Runbook Insiden Per Provider

### Insiden A: InnerTube Bot-Check / HTTP 403 / Perubahan Schema `/next` atau `/player`
- **Gejala:** Stream gagal (`StreamingError.BotCheck` / `ParseError`) atau Radio tidak memuat item lanjutan.
- **Langkah Perbaikan:**
  1. Periksa `InnerTubeClientProfile.kt` (`VISIONOS`, `ANDROID_VR`, `IOS`, `WEB_REMIX`) dan naikkan `clientVersion` bila perlu.
  2. Untuk perubahan schema `/next`, ambil sampel JSON baru (tanpa cookie/data pribadi), tambahkan ke `T1SmartQueueAndRadioTest.kt`, lalu perbarui `InnerTubeParser.parseNextResponse(...)` tanpa `!!`.
  3. Untuk perubahan cipher `n`-param, perbarui versi `newpipeextractor` di `gradle/libs.versions.toml`.
  4. Jalankan `./gradlew :streaming:innertube:test :app:testDebugUnitTest --tests "*T1*"`.

### Insiden B: Deezer Public API Rate-Limited (HTTP 429) atau Down
- **Gejala:** `DeezerCatalogProvider` mengembalikan `StreamingError.RateLimited` atau timeout 6 dtk.
- **Dampak:** `ProviderCircuitBreaker` membuka sirkuit `deezer` selama 60 detik; `MultiProviderCatalogAggregator` otomatis tetap menampilkan hasil dari **iTunes** dan **InnerTube** tanpa mengganggu UI atau pemutaran audio.
- **Langkah Perbaikan:** Verifikasi respons `https://api.deezer.com/search?q=Tulus&limit=1`; periksa parser di `DeezerCatalogProvider.kt` dan jalankan `T3MultiProviderTest`.

### Insiden C: iTunes Search API Rate-Limited (HTTP 429) atau Timeout
- **Gejala:** `ItunesCatalogProvider` melambat atau terkena batas ~20 req/menit per IP.
- **Dampak:** Sirkuit `itunes` dibuka 60 dtk; hasil katalog dan onboarding tetap berjalan melalui **Deezer** + **InnerTube** + **SwrCache**.
- **Langkah Perbaikan:** Pastikan `SwrCache` aktif agar query identik tidak dikirim berulang; uji `ItunesCatalogProvider.parseTracksResponse` via `T3MultiProviderTest`.

### Insiden D: Cache Audio Penuh / Korup
- **Langkah Perbaikan:** `MupaAudioCache` (500 MB LRU) memakai `FLAG_IGNORE_CACHE_ON_ERROR`. Pengguna dapat membersihkannya langsung lewat **Akun → Pengaturan → Pemutaran → Cache audio → Hapus**.
