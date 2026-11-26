# Progres Pengembangan Mupa (`PROGRESS.md`)

> Diperbarui setiap selesai langkah penting agar pekerjaan dapat dilanjutkan dengan mulus bila sesi/konteks terputus.

---

## Status Saat Ini
- **Tahap Aktif:** **Tahap 1A (Fondasi Proyek, Tema/Brand, Ikon/Splash, Navigasi & Bottom Nav)** — **SELESAI**
- **Branch Git:** `stage-1a/foundation` (bercabang dari `main` yang menyimpan commit Tahap 0 `570aae5`)
- **Gerbang Berikutnya:** Menunggu persetujuan user untuk lanjut ke **Tahap 1B (Layar Penuh, Onboarding Artis, Fake Repositories, Player Mini↔Full, Lirik, Antrean, EQ)** serta pilihan final **Q7** sebelum Tahap 2.

---

## Ringkasan Tahap & Persentase Pekerjaan

| Tahap | Status | Progres | Ringkasan |
|---|---|---:|---|
| **Tahap 0: Audit** | **Selesai & Disetujui** | 100% | Audit Mochy & `music-x-V8_5.html`, verifikasi aset, 5 dokumen `docs/`. |
| **Tahap 1A: Fondasi UI/UX** | **Selesai (menunggu review)** | 100% *(~35% dari Tahap 1)* | Multi-modul Gradle (`:app`, `:core:model`, `:core:domain`, `:core:designsystem`), CI GitHub Actions, git lokal + `.gitignore`, tema Ember Editorial + OLED + warna dinamis cover, ikon adaptive/monochrome/notifikasi + splash, DataStore preferensi, navigasi 4 tab + floating glass bottom bar. |
| **Tahap 1B: Layar & Player UI** | **Belum mulai** | 0% *(~65% dari Tahap 1 tersisa)* | Fake repositories, Onboarding Artis Favorit wajib (min 2, anjuran 4, maks 8), layar penuh Beranda/Cari/Pustaka/Akun/Pengaturan, Mini Player ↔ Full Now Playing (tanpa Semi, tanpa overshoot), Lirik wipe adaptif, Antrean, EQ 5-band. |
| **Tahap 2: Streaming** | Belum mulai | 0% | Klien InnerTube + penyedia fallback terisolasi, Media3 service/session/notifikasi/cache/queue. |
| **Tahap 3: Rust Local Mode** | Belum mulai | 0% | Crate Rust (import, scan folder, metadata/tag, DB lokal, rekomendasi lokal, Hapus Nama Artis) + UniFFI/JNI + unit test. |
| **Tahap 4: Integrasi** | Belum mulai | 0% | Gabung streaming + lokal, lirik nyata, EQ nyata, backup/restore, uji stabilitas. |
| **Tahap 5: Optimasi** | Belum mulai | 0% | Optimasi HP spek rendah, baseline profile, uji beban. |

- **Sisa Tahap 1 (UI/UX):** ~65% (Tahap 1B).
- **Sisa Proyek Keseluruhan (Tahap 1B–5):** ~82%.

---

## Keputusan Final dari Gerbang Tahap 0
- **Q1 (Nama Mix):** `Mix Mupa`
- **Q2 (Palet):** **Opsi A — Ember Editorial** (`#FF5A3C` / `#FF7357` + `#FFB067` + `#F6F4EF` di atas `#0E0E11` / `#16161A`). Warna dinamis cover art tetap aktif untuk Aurora & Now Playing, dan ikut nonaktif di Mode OLED / Mode Performa. Teks di atas koral wajib gelap `#16161A`.
- **Q3 (Wordmark):** Glyph SVG + teks Compose bersih (`mupa`); wordmark SVG bersih menyusul tanpa memblokir Tahap 1.
- **Q4 (`applicationId`):** `com.xeno.mupa` (dengan `.debug` untuk build debug).
- **Q5 (`minSdk`):** `26` (Android 8.0 Oreo; adaptive icon native tanpa fallback legacy).
- **Q6 (Struktur & Git):** Proyek Gradle di root `d:\Music-X-Native/`; `Mochy/` dan `music-x-V8_5.html` masuk `.gitignore`; `git init` lokal; tanpa token/rahasia di repo/log/commit.
- **Q8 (Batas Artis Onboarding):** Min 2, anjuran 4, maksimal 8.
- **Q9 (Lingkup Pengaturan & Akun):** Pengaturan minimal (`Crystal`/`Liquid+`, Mode OLED, Aurora, Mode Performa, Hapus Nama Artis, Ubah Artis Favorit) & Tab Akun ringkas.

---

## Yang Sudah Selesai di Tahap 1A
1. **Git & CI:**
   - Inisialisasi repo lokal (`main` commit Tahap 0 `570aae5`), `.gitignore` mengecualikan `Mochy/`, `music-x-*.html`, `local.properties`, keystore/rahasia.
   - Branch `stage-1a/foundation` + workflow `.github/workflows/android-ci.yml` (`testDebugUnitTest`, `lintDebug`, `assembleDebug`, upload artifact APK & lint report).
   - Gradle Wrapper resmi `8.13` (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`).
2. **Struktur Multi-Modul:**
   - `:core:model` (pure Kotlin: `Track`, `Artist`, `Album`, `Playlist`, `LyricsLine`, `AudioSourceType`, `GlassStyle`, `UserPreferences`).
   - `:core:domain` (pure Kotlin interfaces: `MusicSource`, `StreamProvider`, `ArtistRepository`, `PreferencesRepository`).
   - `:core:designsystem` (`MupaColors` Ember Editorial + OLED, `MupaTypography`, `MupaShapes`, `MupaMotion` nol overshoot, `MupaTheme`, `MupaLogoGlyph` & `MupaBrandMark`, `MupaAuroraBackground`, `MupaGlassSurface` Crystal/Liquid+ dengan `RenderEffect` API 31+ & fallback <31, `@Preview`).
   - `:app` (`com.xeno.mupa`, `minSdk 26`, `compileSdk/targetSdk 35`, Hilt, DataStore `DataStorePreferencesRepository`, `MainActivity` + `core-splashscreen` + edge-to-edge).
3. **Ikon & Splash:**
   - Seluruh 6 file dari `assets/android-res/` disalin identik (SHA-256 cocok 100%) ke `app/src/main/res/` tanpa mengubah desain.
   - Terhubung di `AndroidManifest.xml` (`@mipmap/ic_launcher`, `@mipmap/ic_launcher_round`) dan `Theme.Mupa.Splash` (`#16161A` + `ic_launcher_foreground`).
   - `ic_notification.xml` terverifikasi valid dan dijaga oleh `res/raw/keep.xml` hingga pemutar dibuat di Tahap 2.
4. **Navigasi & Floating Glass Bottom Nav:**
   - 4 tab utama: `Beranda`, `Cari`, `Pustaka`, `Akun` (`MupaTopLevelDestination`, `MupaNavHost` bertransisi Emphasized Decelerate tanpa overshoot, state-preserving per tab).
   - `MupaFloatingBottomBar` bergaya `Crystal`/`Liquid+`/OLED dengan indikator pil geser halus tanpa bounce, target sentuh ≥ 48dp, `contentDescription` lengkap, dan `@Preview`.
   - Placeholder interaktif 4 tab (termasuk kontrol langsung Mode OLED, Gaya Kaca, Aurora, Mode Performa, & pratinjau warna sampul dinamis).

---

## Tertunda
- [ ] **Q7 (untuk Tahap 2):** User mencantumkan kedua opsi (`GPL-3.0` dan `Clean-room`) saat balasan Tahap 0; perlu memilih **salah satu** sebelum masuk Tahap 2 (tidak memblokir Tahap 1B).
- [ ] **Tahap 1B:** Onboarding Artis Favorit wajib, Fake repositories, layar penuh 4 tab + Pengaturan, Mini Player ↔ Full Now Playing (tanpa Semi, tanpa overshoot), Lirik wipe adaptif, Antrean, EQ 5-band.
- [ ] **Wordmark SVG bersih:** Opsional menyusul dari user bila ingin mengganti teks tipografi Compose `mupa`.

---

## Log Riwayat
- **2026-11-26:** Tahap 0 selesai & disetujui user.
- **2026-11-26:** Tahap 1A selesai di branch `stage-1a/foundation` (build, lint, unit test, dan brand grep lulus). Berhenti menunggu review user.
