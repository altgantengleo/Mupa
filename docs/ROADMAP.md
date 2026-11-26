# Peta Jalan Pengembangan Mupa (`ROADMAP.md`)

> Rencana bertahap dengan gerbang persetujuan user di akhir setiap tahap, isolasi modul yang tegas, dan strategi build berbasis GitHub Actions.

---

## 1. Arsitektur Target Mupa (Clean Modular Architecture)

Berbeda dari Mochy (yang membocorkan singleton `YouTube` ke 63 file UI), Mupa sejak awal memisahkan kontrak domain dari implementasi sumber:

```text
Mupa/
├── .github/workflows/
│   └── android-ci.yml            # Build debug + lint + unit test (+ Rust di Tahap 3)
├── app/                          # Entry point, MainActivity, DI wiring, NavHost, Splash, Icons
├── core/
│   ├── model/                    # Pure Kotlin: Track, Artist, Album, Playlist, LyricsLine, AudioSourceType
│   ├── domain/                   # Kontrak interface: MusicSource, StreamProvider, ArtistRepository,
│   │                             #   LibraryRepository, LyricsRepository, PreferencesRepository
│   ├── designsystem/             # Tema Mupa, palet, tipografi, shape, komponen glass/aurora, motion specs
│   └── ui/                       # Komponen reusable, MiniPlayer, FullPlayerSheet, LyricsWipe, Queue, EQ
├── feature/
│   ├── onboarding/               # Onboarding Artis Favorit (wajib >=2, anjuran 4, maks 8)
│   ├── home/                     # Beranda (Untukmu, Mix Mupa, Ulangi Lagi, Belum Diputar, dll.)
│   ├── search/                   # Cari + filter + histori
│   ├── library/                  # Pustaka (Lagu, Playlist, Lokal vs Online)
│   ├── account/                  # Akun & Pengaturan minimal (OLED, Glass, Performa, Artis Favorit, Hapus Nama Artis)
│   └── player/                   # ViewModel & UI Now Playing, Mini Player, Lirik, Antrean, EQ
├── data/
│   ├── fake/                     # Tahap 1: FakeMusicSource & FakeArtistRepository (dipakai sebelum backend siap)
│   └── local-prefs/              # DataStore Preferences (onboarding, tema OLED, glass, performa, artis terpilih)
├── playback/                     # Tahap 2: Media3 ExoPlayer, MupaPlaybackService, MediaSession, Notification, Cache, Queue
├── source/                       # Tahap 2: Tiap penyedia terisolasi di modulnya sendiri!
│   ├── innertube/                # Klien InnerTube buatan sendiri (tanpa bocor ke UI)
│   ├── fallback-*/               # Penyedia alternatif terpisah (mis. JioSaavn / penyedia lain bila disetujui)
│   └── aggregator/               # Router multi-penyedia, retry, circuit breaker, fallback
└── engine/
    └── local-rust/               # Tahap 3: Crate Rust (import, scan folder, tag/art, SQLite library,
                                  #   rekomendasi lokal, Hapus Nama Artis) + UniFFI/JNI bindings
```

*(Catatan: pada Tahap 1, struktur bisa dimulai ringkas dengan `:app`, `:core:model`, `:core:domain`, `:core:designsystem`, `:data:fake` — cukup modular untuk pemisahan ketat tanpa overhead Gradle berlebihan di CI, lalu diperluas saat Tahap 2 & 3 masuk.)*

---

## 2. Rencana Tahap Demi Tahap

### Tahap 0 — Audit & Desain Awal *(SELESAI — Menunggu Persetujuan)*
- **Lingkup:** Audit read-only Mochy (lisensi, arsitektur, streaming, animasi, onboarding, design system), ekstraksi fitur `music-x-V8_5.html`, verifikasi logo/ikon Mupa, usulan palet.
- **Output:** `docs/AUDIT.md`, `docs/BRAND-MAP.md`, `docs/ROADMAP.md`, `docs/QUESTIONS.md`, `docs/PROGRESS.md`.
- **Gerbang:** User meninjau dokumen dan menjawab `docs/QUESTIONS.md`.

### Tahap 1 — Fondasi Proyek & UI/UX Lengkap (Data Dummy)
- **Branch/Commit:** `stage-1/ui-foundation` (commit kecil terstruktur: setup proyek+CI → aset ikon+splash → design system+tema → onboarding artis → 4 tab+pengaturan → player mini↔full+lirik+antrean+EQ → verifikasi).
- **Lingkup:**
  - Proyek Android baru Jetpack Compose + Material3 (`applicationId` sesuai keputusan, `minSdk` sesuai keputusan, Kotlin + Hilt + DataStore + Compose Navigation + `core-splashscreen`).
  - Workflow GitHub Actions (`android-ci.yml`) yang menjalankan `./gradlew assembleDebug lintDebug testDebugUnitTest` + upload APK debug artifact.
  - Pemasangan aset ikon dari `assets/android-res/` ke `app/src/main/res/` (+ fallback legacy jika `minSdk < 26`), splash screen `#16161A` + `ic_launcher_foreground`, dan validasi `ic_notification`.
  - Implementasi semua layar: **Onboarding Artis Favorit** (wajib ≥2, anjuran 4, maks 8, tanpa skip, tahan rotasi & process death via `SavedStateHandle`, back = keluar app, tersimpan di DataStore, bisa diubah ulang dari Akun), **Beranda** (membaca artis onboarding untuk rak "Untukmu"), **Cari**, **Pustaka**, **Akun**, **Pengaturan**, **Mini Player**, **Now Playing (Full)**, **Lirik (wipe adaptif)**, **Antrean**, dan **EQ**.
  - **Penghapusan mode Semi:** transisi langsung **Mini ↔ Full** progress-driven (tanpa overshoot/bounce, drag-down & back handler konsisten, target 60/120 Hz stabil tanpa kedip).
  - Glass `Crystal`/`Liquid+` berbasis `RenderEffect` hanya API 31+ dengan fallback solid/translusen di bawah API 31, toggle performa, Aurora, Mode OLED (1 toggle), edge-to-edge, font besar, `@Preview` komponen utama.
  - Kontrak interface `MusicSource`, `StreamProvider`, `ArtistRepository`, `LyricsRepository` + `Fake*Repository`.
- **Verifikasi Wajib:** `./gradlew assembleDebug` + `lintDebug` bersih + CI hijau, grep nol `"Mochy"` & nol `"Music X"` di kode/res/package, checklist tiap layar & state (loading/kosong/error), verifikasi alur onboarding & rotasi, verifikasi ikon adaptive/monochrome/splash/notifikasi, audit animasi & recomposition.
- **Gerbang:** Laporan + bukti verifikasi → tunggu persetujuan user.

### Tahap 2 — Lapisan Streaming & Service Pemutar (Media3)
- **Lingkup:**
  - Implementasi klien InnerTube Mupa di `:source:innertube` (endpoint, client config terisolasi, parsing respons defensif tanpa `!!`, resolusi stream + signature/n-param sesuai keputusan lisensi Q7).
  - Penyedia audio alternatif terisolasi di modul terpisah + router fallback, retry/backoff (termasuk HTTP 429/5xx), circuit breaker, dan URL cache thread-safe (`ConcurrentHashMap`).
  - `MupaPlaybackService` (`MediaLibraryService` + ExoPlayer + `MediaSession` + notifikasi memakai `ic_notification` + `SimpleCache` LRU + AudioFocus + Queue/Radio continuation).
  - Mengisi `ArtistRepository` dan katalog Beranda/Cari dari penyedia nyata di balik interface Tahap 1 (tanpa mengubah kontrak UI).
- **Verifikasi Wajib:** Unit test parser & fallback router, build & lint CI hijau, uji playback background/notifikasi/rotasi/putus jaringan.
- **Gerbang:** Review & persetujuan user.

### Tahap 3 — Engine Lokal Rust (Local Mode)
- **Lingkup:**
  - Crate Rust di `engine/local-rust/`: import file & scan folder (SAF/URI bridge), ekstraksi metadata/tag (MP3 ID3v2/v1, FLAC Vorbis + PICTURE, Ogg/Opus, MP4/M4A `covr`/tag — memakai crate permisif seperti `lofty` / `symphonia`), penyimpanan SQLite lokal, deduplikasi fingerprint cepat, algoritma rekomendasi lokal (`Mix Mupa`, `Ulangi Lagi`, `Belum Diputar`), dan parser **"Hapus Nama Artis"** yang menyimpan `originalFileName` permanen agar pemulihan selalu akurat.
  - Binding Android via **UniFFI** (atau JNI tipis) + cross-compile ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) di GitHub Actions (`cargo-ndk`).
  - `ArtistRepository` lokal tergabung agar onboarding & rekomendasi juga bisa memakai artis dari file lokal.
- **Verifikasi Wajib:** `cargo test` (unit test parser metadata, Hapus Nama Artis, rekomendasi lokal, DB), build APK + `.so` di CI, uji import & scan.
- **Gerbang:** Review & persetujuan user.

### Tahap 4 — Integrasi Penuh (Streaming + Lokal, Lirik, EQ, Backup/Restore)
- **Lingkup:**
  - Penggabungan mulus mode Streaming + Lokal (sumber tunggal di Pustaka/Cari/Antrean, fallback offline).
  - Modul Lirik (tag tersemat dari Rust + penyedia online seperti LRCLib + wipe warna adaptif sinkron real-time + offset per lagu).
  - Equalizer 5-band nyata via `AudioProcessor` kustom pada Media3 ExoPlayer (tanpa `LoudnessEnhancer` sistem yang bermasalah) + preset + volume/tempo.
  - Backup & Restore JSON ringan (profil, artis favorit, playlist, favorit, statistik, pengaturan, EQ).
  - Uji stabilitas (switching cepat, audio focus, Bluetooth disconnect, process death, rotasi).

### Tahap 5 — Optimasi Performa Low-End, Baseline Profile & Uji Beban
- **Lingkup:**
  - Compose Compiler metrics/reports audit (pastikan seluruh composable panas `skippable` dan parameter `@Immutable`/`@Stable`).
  - Baseline Profile (`androidx.profileinstaller`) + R8 release build tuning + analisis ukuran APK per ABI.
  - Uji beban (pustaka 2.000+ lagu lokal, scroll 120Hz, buka-tutup player berulang, memory trim di HP RAM rendah).

---

## 3. Matriks Risiko Utama & Mitigasi

| Risiko | Tingkat | Dampak | Mitigasi Konkret |
|---|---|---|---|
| **Kontaminasi Lisensi GPL-3.0** (Mochy, `NewPipeExtractor`, `innertubex`, Jellyfin FFmpeg) | **Tinggi** | Mupa terpaksa menjadi GPL-3.0 atau melanggar lisensi jika menyalin kode / menautkan library GPL tanpa sadar | Tahap 1 100% clean-room + dependensi Apache/MIT saja. User memutuskan model lisensi di `QUESTIONS.md` (Q7) sebelum Tahap 2 dimulai. |
| **Perubahan Anti-Bot YouTube** (PoToken, `n`-param, pembatasan klien InnerTube) | **Tinggi** | Stream YouTube gagal diputar sewaktu-waktu | Isolasi ketat per penyedia di `:source:*`, router fallback multi-penyedia + multi-client config yang mudah diperbarui tanpa menyentuh UI/playback core. |
| **Jank / Flash Hitam saat Transisi Player 60/120 Hz** | **Tinggi** | Pengalaman patah-patah atau kedipan hitam saat buka-tutup panel | Tanpa `dampingRatio < 1f`, tanpa `CompositingStrategy.Offscreen` layar penuh pada konten belakang, backdrop sinkron sejak frame 0, 2-frame pre-mount untuk Queue/Lyrics, baca `progress` hanya di fase layout/draw. |
| **Blur Berat di HP Spek Rendah / < API 31** | **Sedang** | Frame drop parah & boros baterai | `RenderEffect` hanya aktif di API 31+, matikan blur konten tertutup saat player terbuka, fallback solid/translusen ringan di < API 31, sediakan 1 toggle Performa + Mode OLED. |
| **User Tanpa PC (Ketergantungan Penuh pada GitHub Actions)** | **Sedang** | Sulit debug build jika CI lambat atau rusak | Siapkan workflow CI sejak Tahap 1 dengan Gradle caching, build + lint + unit test otomatis tiap push/PR, serta artifact APK debug yang langsung bisa diunduh & dipasang di HP. |
| **Build Cross-Compile Rust + Android NDK di CI (Tahap 3)** | **Sedang** | Waktu build CI naik dan konfigurasi ABI rawan error | Gunakan `cargo-ndk` + cache target Rust (`Swatinem/rust-cache`), jalankan `cargo test` terpisah sebelum build Android, batasi ABI debug CI ke `arm64-v8a` (dan universal di release) agar cepat. |
| **Ketiadaan Repository Git di Workspace Saat Ini** | **Rendah–Sedang** | Aturan 1 branch/commit per tahap & pemicu GitHub Actions belum aktif | Dikonfirmasi di `QUESTIONS.md` (Q6) sebelum Tahap 1 agar inisialisasi git + `.gitignore` (mengecualikan `Mochy/` dan `music-x-V8_5.html`) rapi sejak commit pertama. |
