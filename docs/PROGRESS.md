# Progres Pengembangan Mupa (`PROGRESS.md`)

> Diperbarui setiap selesai langkah penting agar pekerjaan dapat dilanjutkan dengan mulus bila sesi/konteks terputus.

---

## Status Saat Ini
- **Tahap Aktif:** **Tahap 0 (Audit)** — **SELESAI**
- **Gerbang Berikutnya:** Menunggu review & keputusan user terhadap `docs/QUESTIONS.md` sebelum memulai **Tahap 1 (UI/UX)**.
- **Status Kode App:** Belum ada kode proyek Android/Rust yang dibuat atau diubah (Tahap 0 murni read-only + dokumentasi).

---

## Ringkasan Tahap

| Tahap | Status | Ringkasan |
|---|---|---|
| **Tahap 0: Audit** | **Selesai (menunggu persetujuan)** | Audit lengkap Mochy (lisensi, arsitektur, streaming, animasi, onboarding, design system), ekstraksi fitur `music-x-V8_5.html`, verifikasi aset logo/ikon Mupa, penyusunan 5 dokumen di `docs/`. |
| **Tahap 1: UI/UX** | Belum mulai (tertahan gerbang Tahap 0) | Setup proyek Mupa + CI GitHub Actions, ikon/splash, design system, onboarding artis favorit, 4 tab, player mini↔full tanpa Semi & tanpa overshoot, lirik, antrean, EQ, fake repositories. |
| **Tahap 2: Streaming** | Belum mulai | Klien InnerTube clean-room + penyedia alternatif terisolasi, Media3 service/session/notifikasi/cache/queue. |
| **Tahap 3: Rust Local Mode** | Belum mulai | Crate Rust (import, scan folder, metadata/tag, DB lokal, rekomendasi lokal, Hapus Nama Artis) + UniFFI/JNI + unit test. |
| **Tahap 4: Integrasi** | Belum mulai | Gabung streaming + lokal, lirik nyata, EQ nyata, backup/restore, uji stabilitas. |
| **Tahap 5: Optimasi** | Belum mulai | Optimasi HP spek rendah, baseline profile, uji beban. |

---

## Yang Sudah Selesai (Tahap 0)
1. **Inspeksi workspace & aset:**
   - Memverifikasi struktur root `d:\Music-X-Native` (`Mochy/Mochy-Music-main/`, `music-x-V8_5.html`, `assets/logo/`, `assets/android-res/`, `README.md`, `docs/`).
   - Memastikan belum ada `.git` di root workspace.
   - Memverifikasi hex warna langsung dari SVG & XML (`#FF5A3C`, `#16161A`, `#F6F4EF`), menghitung *safe zone* `ic_launcher_foreground.xml` (radius maks ~28,5dp < 33dp safe zone adaptive icon & < 36dp splash circle), memeriksa `ic_launcher_monochrome.xml` & `ic_notification.xml`, serta meninjau visual `mupa-sheet-original.png`, `mupa-lockup_from-sheet.png`, dan `mupa-wordmark_from-sheet_transparent.png`.
2. **Audit lisensi & arsitektur Mochy:**
   - Mengonfirmasi Mochy berlisensi **GPL-3.0**, serta memetakan lisensi dependensi (`NewPipeExtractor`, `innertubex`, `media3-ffmpeg-decoder`, `jamesdsp_eel2` = GPL; `libusb` = LGPL; AndroidX/Media3/Compose/Room/Hilt/Ktor/Coil = Apache-2.0/MIT).
   - Memetakan 22 modul Gradle, DI Hilt, Room v47, navigasi ~60 route, DataStore `PreferencesCache`, playback Media3/ExoPlayer/MediaSession/notifikasi, onboarding `SetupWizardScreen` (mengonfirmasi tidak ada artist picker aktif, hanya jejak cold-start di `HomeViewModel`/`HomeRecommender`), dan workflow GitHub Actions Mochy.
3. **Audit lapisan streaming Mochy:**
   - Menginventarisasi seluruh penyedia audio (InnerTube, Monochrome, JioSaavn, Apple Music, modul dinamis Web/Code/Local), 14 penyedia lirik, 4 canvas/video, 3 metadata, 5 translasi, scrobble, Shazam, dan importer.
   - Membedah 18 preset `YouTubeClient`, resolusi stream (InnerTubeX + `VISIONOS` + `CipherDeobfuscator` + yt-dlp EJS n-param + WebView BotGuard PoToken; memastikan SABR tidak dijalankan), fallback 2 lapis, retry/cache/queue, daftar titik rapuh, serta mengukur keterikatan UI (63/658 file UI & 23/51 ViewModel mengimpor `innertube`).
4. **Audit mekanisme animasi & design system Mochy:**
   - Mendokumentasikan mekanisme `BottomSheetState` (`Animatable<Dp>`, `progress 0f..1f`), `detectSheetDrag`, `MorphingCover` (`Modifier.layout`), `PlayerPageTransition` (2-frame pre-mount), daftar 27+ spring `dampingRatio < 1f` yang harus dihindari Mupa, mekanisme 120Hz, dan 6 penyebab flash hitam/kedip.
5. **Ekstraksi fitur & perilaku `music-x-V8_5.html`:**
   - Mendokumentasikan `:root` & Ultra Dynamic OKLab V15, Mode OLED (+ pixel shift), gaya glass (`Crystal` vs `Liquid+`), Aurora background, 4 tab (`Beranda`, `Cari`, `Pustaka`, `Akun`), formula eksak rekomendasi lokal (`Mix X 2.0`, `Ulangi lagi`, `Belum diputar`), inventaris fitur Semi Player yang dipindah ke Full, Lirik + wipe warna adaptif OKLCH `L>=0.87`, EQ 5-band, serta mekanisme non-destruktif `"Hapus Nama Artis"` (`originalFileName`).
6. **Penyusunan dokumen Tahap 0:**
   - [docs/AUDIT.md](AUDIT.md)
   - [docs/BRAND-MAP.md](BRAND-MAP.md)
   - [docs/ROADMAP.md](ROADMAP.md)
   - [docs/QUESTIONS.md](QUESTIONS.md)
   - [docs/PROGRESS.md](PROGRESS.md)

---

## Keputusan yang Sudah Tetap (Dari Instruksi Awal)
- **Brand:** 100% **Mupa** (nol sisa "Mochy" di mana pun; nol "Music X" di UI/resource/package).
- **Lisensi & Clean-Room:** Tidak menyalin kode mentah dari Mochy (GPL-3.0); seluruh kode Mupa ditulis ulang dari nol berdasarkan pemahaman pola.
- **Animasi:** Tanpa spring overshoot/bounce (`dampingRatio < 1f` dilarang); pakai easing halus (emphasized/decelerate) atau critically damped; stabil 60/120 Hz tanpa flash hitam.
- **Mode Player:** Mode **Semi** dihapus, diganti transisi langsung **Mini ↔ Full**.
- **Glass & OLED:** `RenderEffect` hanya di API 31+, fallback solid/translusen di bawahnya, menghormati toggle performa; Mode OLED sebagai 1 toggle.
- **Onboarding:** Wajib pilih minimal 2 artis (anjuran 4), tanpa lewati, tersimpan di DataStore, muncul sekali, bisa diubah dari Akun/Pengaturan, tahan rotasi & process death, tombol Back keluar app.
- **Build & Target:** Build via GitHub Actions; UI terpisah total di balik interface repository; local mode memakai Rust di Tahap 3.

---

## Tertunda (Menunggu Jawaban User di `docs/QUESTIONS.md`)
- [ ] **Q1:** Nama pengganti `"Mix X"` (usul: `Mix Mupa`)
- [ ] **Q2:** Pilihan palet aksen (Opsi A Ember Editorial / Opsi B Minimal Studio / Opsi C Contrast Dusk) + konfirmasi dynamic cover tint
- [ ] **Q3:** Wordmark pakai glyph SVG + tipografi Compose bersih atau suplai SVG wordmark
- [ ] **Q4:** Konfirmasi `applicationId = com.xeno.mupa`
- [ ] **Q5:** Pilihan `minSdk` (`26` rekomendasi vs `24`)
- [ ] **Q6:** Lokasi root Gradle Mupa & inisialisasi `git init` lokal
- [ ] **Q7:** Model lisensi Mupa untuk Tahap 2 (Bebas GPL clean-room murni vs Open-Source GPL-3.0)
- [ ] **Q8:** Batas maksimal pilihan artis onboarding (usul: `8`)
- [ ] **Q9:** Lingkup minimal Pengaturan & tampilan Tab Akun di Tahap 1

---

## Log Riwayat
- **2026-11-26:** Tahap 0 dimulai dan diselesaikan (read-only audit + 5 dokumen `docs/`). Berhenti di gerbang Tahap 0 menunggu review & keputusan user.
