# Progres Pengembangan Mupa (`PROGRESS.md`)

> Diperbarui setiap selesai langkah penting agar pekerjaan dapat dilanjutkan dengan mulus bila sesi/konteks terputus.

---

## Status Saat Ini
- **Status Proyek:** **Tahap 0 sampai Tahap 5 — SELESAI (100% cakupan pengembangan lokal)**
- **Branch Git:** `stage-1a/foundation` (commit terpisah dan bersih per tahap di atas `main`)
- **Lisensi:** **GPL-3.0** (`LICENSE` & `NOTICE` aktif; kode Mupa ditulis clean-room 100% tanpa menyalin kode Mochy)
- **Verifikasi Tersisa:** Uji di perangkat Android fisik / HyperOS (`docs/TEST-DEVICE.md`) serta eksekusi pipeline `rust_local_engine` (`cargo test` + `cargo-ndk`) di GitHub Actions karena toolchain Rust tidak terpasang lokal di PC ini.

---

## Ringkasan Tahap & Persentase Pekerjaan

| Tahap | Status | Progres | Commit | Ringkasan |
|---|---|---:|---|---|
| **Tahap 0: Audit** | **Selesai** | 100% | `570aae5` | Audit Mochy & `music-x-V8_5.html`, 5 dokumen `docs/` (`AUDIT.md`, `BRAND-MAP.md`, `ROADMAP.md`, `QUESTIONS.md`, `PROGRESS.md`). |
| **Tahap 1A: Fondasi UI/UX** | **Selesai** | 100% | `8565704` | Multi-modul Gradle 8.13, CI GitHub Actions, tema Ember Editorial + OLED, ikon & splash identik SHA-256, DataStore, navigasi 4 tab, floating glass bottom nav. |
| **Tahap 1B: Player, Lirik, Antrean** | **Selesai** | 100% | `bfdf8fe` | `LICENSE` GPL-3.0 + `NOTICE`, `MupaPlayerSheetState` (1 state progres `0..1` di fase layout/draw, nol overshoot, bebas 6 sumber kedip), Mini↔Full (tanpa Semi), Lirik wipe adaptif, Antrean + pre-mount 2-frame. |
| **Tahap 1C: Layar Penuh & Onboarding** | **Selesai** | 100% | `536f9b7` | Onboarding Artis wajib (min 2, anjuran 4, maks 8, tanpa lewati, DataStore + `SavedStateHandle`), Beranda (`Mix Mupa`, `Ulangi Lagi`, `Belum Diputar`), Cari, Pustaka, Akun, Pengaturan, fake repository. |
| **Tahap 2: Streaming & Playback** | **Selesai** | 100% | `c2e93da` | `:streaming:api` (error bertipe), `:streaming:innertube` (guest + parser toleran + `NewPipeExtractor` terisolasi), `:streaming:jiosaavn` (fallback), `:streaming:aggregator` (registry + circuit breaker + cache URL thread-safe), `:playback` (`MupaPlaybackService` Media3, cache 500 MB, antrean JSON), `docs/STREAMING.md`. |
| **Tahap 3: Rust Local Mode** | **Selesai** | 100% | `2dcd190` | Workspace Rust `engine/local-rust/` (`mupa-local`: `lofty`, `rusqlite`, `uniffi`, `name_cleaner`, `recommender` Mix Mupa + MMR), `:engine:local` (Scoped Storage MediaStore/SAF tanpa raw path), CI `cargo test` + `cargo-ndk` (`arm64-v8a`, `x86_64`), gabungan artis lokal ke onboarding/rekomendasi. |
| **Tahap 4: Integrasi, Lirik, EQ, Backup** | **Selesai** | 100% | `e21d090` | EQ 5-band nyata (`FiveBandEqualizerProcessor` RBJ biquad di Media3 + `MupaEqualizerOverlay`), LRC parser + `LrcLibLyricsProvider` online, `MupaBackupCodec` JSON tanpa rahasia + restore ke DataStore. |
| **Tahap 5: Optimasi & Release R8** | **Selesai** | 100% | *(commit Tahap 5)* | `androidx-profileinstaller` + `baseline-prof.txt`, `compose_compiler_config.conf` strong-skipping, `app/proguard-rules.pro` & verifikasi `assembleRelease` R8 (APK 2.89 MB), `MupaApplication` (`isLowRamDevice` & `onTrimMemory`), `docs/TEST-DEVICE.md`. |

- **Progres Kode & Verifikasi Lokal (Tahap 0–5):** **100% selesai.**

---

## Bukti Verifikasi Terakhir (Tahap 5)
- **Unit Test Gradle (Offline):** **30 / 30 lulus (`0 failures`, `0 errors`)** across 11 suite (`:app`, `:streaming:innertube`, `:streaming:jiosaavn`, `:streaming:aggregator`, `:playback`, `:engine:local`).
- **Android Lint (`lintDebug` + `lintVitalRelease`):** **0 error** di seluruh modul Android (`:app`, `:core:designsystem`, `:playback`, `:engine:local`).
- **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk` (**23.3 MB** / `24,429,848 bytes`).
- **Release APK (R8 Minify + ShrinkResources + ART Baseline Profile):** `app/build/outputs/apk/release/app-release-unsigned.apk` (**2.89 MB** / `3,025,918 bytes`).
- **Audit Brand (`mochy`, `music x`, `musicx` di luar `docs/`):** **0 temuan**.
- **Audit Animasi Spring Overshoot (`dampingRatio < 1f`):** **0 temuan**.
- **Paritas SHA-256 Ikon (`assets/android-res/` → `app/src/main/res/`):** **6 / 6 identik (100%)**.

---

## Belum Terverifikasi di PC Lokal (Lihat `docs/TEST-DEVICE.md`)
- [ ] Eksekusi `cargo test` dan cross-compile `cargo-ndk` (`libmupa_local.so` ARM64/x86_64) di GitHub Actions (`rust_local_engine` job) — Rust tidak terpasang di PC lokal.
- [ ] Pemutaran streaming nyata (InnerTube/NewPipeExtractor + JioSaavn fallback), cache 500 MB, dan perpindahan jaringan di perangkat fisik.
- [ ] Kelancaran animasi Mini ↔ Full pada 60/120 Hz, bebas kedip/flash hitam, dan performa Mode Kaca vs Mode OLED/Performa di HP fisik.
- [ ] Uji JNI Rust lokal + Scoped Storage MediaStore/SAF + EQ 5-band audio nyata + background playback HyperOS/MIUI (`docs/TEST-DEVICE.md`).

---

## Keputusan Final & Default
- Seluruh keputusan user (`Q1`–`Q9`) dan keputusan default non-blokir (`D1`–`D7`) tercatat lengkap di [`docs/QUESTIONS.md`](QUESTIONS.md).

---

## Log Riwayat
- **2026-11-26:** Tahap 0 selesai (`570aae5`) & disetujui user.
- **2026-11-26:** Tahap 1A selesai (`8565704`).
- **2026-11-26:** Tahap 1B selesai (`bfdf8fe`) + lisensi GPL-3.0 (`LICENSE`, `NOTICE`).
- **2026-11-26:** Tahap 1C selesai (`536f9b7`).
- **2026-11-26:** Tahap 2 selesai (`c2e93da`).
- **2026-11-26:** Tahap 3 selesai (`2dcd190`).
- **2026-11-26:** Tahap 4 selesai (`e21d090`).
- **2026-11-26:** Tahap 5 selesai (`437105a`) — 30/30 unit test lulus, lint 0 error, `assembleRelease` R8 lulus (2.89 MB), `docs/TEST-DEVICE.md` siap.
- **2026-11-26:** CI Run #1 (`altgantengleo/Mupa`) 100% hijau termasuk `cargo test` & `cargo-ndk` Rust (`arm64-v8a` & `x86_64`).
- **2026-11-26:** Perbaikan uji perangkat nyata v1.0.0: InnerTube `VISIONOS` + `visitor_id` otomatis (terverifikasi HTTP 206 audio), `MupaPlayerViewModel` terhubung langsung ke Media3 `MediaController` / `MupaPlaybackService`, artwork HD Coil 3 di Onboarding & seluruh kartu/player, opening editorial dark-sheet 2 langkah, `versionCode=1` / `versionName="1.0.0"`, ABI split `arm64-v8a`, dan publikasi otomatis ke GitHub Releases. 31/31 unit test lulus, lint 0 error, brand grep 0 temuan.
- **2026-11-26 (Fase 2R — R0):** Branch `fase-2r` dibuat. Audit riil `docs/STATUS-R0.md` selesai (akar masalah B1–B12 terkonfirmasi per baris kode), 4 screenshot baseline JVM direkam & diverifikasi visual di `docs/screens/baseline/`, 35/35 unit test lulus, `lintDebug` 0 error, `assembleDebug` lulus.
- **2026-11-26 (Fase 2R — R1):** Fondasi Mupa Calm + Pipeline Lokal SAF/Room selesai: Plus Jakarta Sans (OFL 400–700), palet solid default (`GlassStyle.SOLID` + Haze Apache-2.0 untuk opsi Kaca API 31+), aurora terkendali <38%, `PlayerChromeInsets` + scrim 24dp, `MupaComponents.kt`, `TrackDisplayRules` + `ShelfComposer`, `MupaDatabase` (Room) + `LocalLibraryRepository` (SAF `OpenDocument`/`OpenDocumentTree` + ekstraksi artwork terkompresi ≤512px) terpisah ketat dari Streaming. Build, lint 0 error, dan seluruh unit test lulus (40/40).
- **2026-11-26 (Fase 2R — R2):** Redesign penuh 4 layar (`HomeScreen`, `SearchScreen`, `LibraryScreen`, `AccountScreen`) selesai dalam gaya Mupa Calm: Beranda 1 hero Mix Mupa berkolase + maks 3 shelf tanpa duplikasi, Cari sticky + debounce 300ms (Streaming) / instan (Lokal) + 1 baris SegmentedTabs+sumber, Pustaka tile ala Music X lama + switch Awan/Ponsel + SAF Import Lagu/Folder/Pindai Ulang + `FilterSheet`, Akun inline tanpa tombol pengaturan ganda. 5 screenshot JVM dirender & diperiksa langsung (`docs/screens/r2_*.png`). Build, lint 0 error, dan unit test lulus.
- **2026-11-26 (Fase 2R — R3):** Player + Cinema + Antrean selesai (perbaikan B6 & B11): drag 1:1 pada wadah stasioner (0 feedback loop) + tap Mini Player membuka Full Now Playing + `rememberSaveable` tahan rotasi, Full Now Playing Mupa Calm (`Lokal`/`Streaming` badge, cover 28dp, seekbar tipis + segmen buffer Streaming + tabular, inline Lirik crossfade, 72dp Play/Pause koral, aksi Lirik/Antrean+badge/EQ/Cinema), Mode Cinema layar penuh (backdrop one-shot blurred cache / OLED `#000000`, kontrol auto-fade 3 dtk, Back bertahap `Cinema -> Overlay -> Full -> Mini`), dan `MupaQueueOverlay` (lagu aktif tersemat, durasi total tabular, Bersihkan, long-press drag reorder clean-room, geser hapus). Terverifikasi dengan `R3PlayerAndCinemaUiTest` + 4 screenshot JVM diperiksa langsung (`docs/screens/r3_*.png`), build + lint 0 error + semua test lulus.
