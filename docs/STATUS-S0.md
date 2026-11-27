# Audit Status Awal Fase 2S (`STATUS-S0.md`)

> **Branch:** `fase-2s` (bercabang dari akhir Fase 2R commit `2a5f510`).  
> **Sifat S0:** Audit & baseline murni — **belum mengubah perilaku aplikasi**.

---

## 1. Konfirmasi Akar Masalah `N1`–`N9` di Kode Saat Ini

| ID | Temuan di Screenshot Perangkat | Akar Masalah Terkonfirmasi di Kode (File & Baris) | Rencana Perbaikan |
|---|---|---|---|
| **N1** | Now Playing berlatar abu-abu kecokelatan (bukan biru+pasir) & ada pita hitam di atas status bar | 1) `MupaPlayerUiState.kt:64–75`: `coverAccent` membaca `accentColorArgb` yang selalu `null` di streaming.<br>2) `MupaCinemaOverlay.kt:116–124`: `FullBleedBlurredCoverBackdrop` melakukan 2-pass `fastBoxBlurOnce(128px, r=10)` + scrim hitam `46–80%`, mencampur piksel biru & pasir menjadi abu-kecokelatan.<br>3) `app/src/main/res/values/themes.xml:3–7`: belum ada `android:windowLayoutInDisplayCutoutMode = shortEdges` (API 28+), sehingga area notch/cutout di atas status bar tergambar pita hitam oleh sistem. | **S1 + S2:** Ganti latar dengan mesin **Ultra Dynamic** OKLab k-means (3 radial gradient + grain) dan tambah `values-v28/themes.xml` dengan `windowLayoutInDisplayCutoutMode = shortEdges` + scrim status bar. |
| **N2** | Pada `Liquid+`, teks daftar masih terbaca di belakang pill Bottom Nav dan di celah antara Mini Player & Nav | 1) `MupaGlassSurface.kt:69–78`: `Liquid+` hanya memakai `blurRadius = 14.dp`, `tintAlpha = 0.72f`, `noiseFactor = 0f`.<br>2) `MupaApp.kt:197–213`: scrim bawah bergradasi mulai transparan dan celah `8dp` antara Mini Player dan Nav tidak memiliki scrim cukup pekat. | **S5:** Naikkan blur Crystal/Liquid+ ke `≥ 24.dp`, tint `≥ 0.74f`, aktifkan noise halus, dan perkuat scrim celah Mini↔Nav. |
| **N3** | Konten scroll menembus di bawah status bar tanpa scrim (chip `"yung kai"` terpotong di bawah jam pada layar Akun) | `MupaApp.kt:162–194`: `MupaNavHost` edge-to-edge tanpa lapisan scrim gradasi di bawah status bar untuk layar yang di-scroll. | **S3:** Tambah lapisan scrim status bar di atas konten scroll untuk seluruh tab. |
| **N4** | Shelf `"Ulangi lagi"` berisi kompilasi/upload sampah (`"TOP HITS SPOTIFY 2026"`, `"Piktor TV"`) | `StreamingRepositories.kt:262–264, 304–309`: `trendingJob` mencari string generik `"Top Hits Indonesia"` di InnerTube tanpa filter tipe lagu/durasi/judul, lalu langsung dimasukkan ke `shelf_repeat` (`"Ulangi lagi"`). | **S3:** Filter kualitas rekomendasi (prioritas lagu, tolak judul `"top hits"`, `"playlist"`, `"full album"`, `"mix"`, `"nonstop"`, `"1 hour/jam"`, durasi `< 60 dtk` atau `> 8 mnt`, dedup judul dinormalisasi) + unit test. |
| **N5** | Tiga kontrol sumber berbeda: chip `"Awan"` (Beranda), switch awan/ponsel (Pustaka), chip `"Stream…"` terpotong (Cari) | `HomeScreen.kt:226–251` (chip `"Awan"`), `LibraryScreen.kt:602–640` (`SourceModeSwitch`), `SearchScreen.kt:284–293` (`MupaFilterChip` di ujung `MupaChipRow` yang terpotong di 360dp). | **S3:** Ekstrak 1 komponen `MupaSourceSwitch` global di kanan-atas Beranda (Hero), Cari, dan Pustaka; hapus chip `"Awan"` & chip `"Stream…"` di Cari. |
| **N6** | Angka tidak konsisten (`Akun "0 Lagu Lokal"` vs `Pustaka "Lagu 40"`); tile `"Album 0"` tetap menampilkan kolase cover | 1) `MupaAppViewModel.kt:68–69`: `totalTracksCount` hanya membaca `localTracks.size` meski `activeSource == STREAMING`; `AccountAndSettingsScreen.kt:94–98` melabeli `"Lagu Lokal"`.<br>2) `LibraryScreen.kt:488–512`: `LibraryOverviewTile` tetap mengirim `covers.getOrNull(...)` ke tile `Playlist`/`Album` saat `count == 0`. | **S4:** Samakan angka berdasarkan sumber aktif (`activeSource`) dan tampilkan ikon + teks polos saat tile kosong (`count == 0`). |
| **N7** | Tab Akun terlalu panjang karena semua pengaturan menumpuk + paragraf atribusi padat di dasar | `AccountAndSettingsScreen.kt:144–245`: seluruh pengaturan & paragraf atribusi panjang dirender inline di `AccountScreen`. | **S4:** Ringkas Akun (profil + 3 StatTile + Artis Favorit + 1 pintasan Pengaturan + ikon gerigi kanan-atas) → layar `SettingsScreen` terpisah (Tampilan, Pemutaran, Pustaka, Data, Tentang) + layar `AttributionScreen` dari `NOTICE`. |
| **N8** | Layar Cari (state awal) hanya menampilkan kartu kosong besar dengan ruang mati | `SearchScreen.kt:373–380`: saat `recentQueries` kosong, langsung menampilkan `MupaEmptyState` besar. | **S4:** State awal = `"Terakhir dicari"` jika ada; jika kosong tampilkan pintasan pencarian dari **Artis Favorit** (tanpa data palsu). |
| **N9** | Now Playing: ikon aksi bawah tanpa label; badge `"10"` mengambang tidak rapi di atas ikon antrean | `MupaPlayerContainer.kt:865–923`: `NowPlayingActionIcon` hanya menggambar ikon tanpa label 11sp, dan `BadgedBox` membungkus seluruh `IconButton` 48dp bukan ikonnya. | **S2:** Tambah label 11sp (`Lirik`, `Antrean`, `EQ`, `Cinema`), tempelkan badge tepat di pojok kanan-atas ikon, dan tunjukkan status aktif yang jelas. |

---

## 2. Status Fungsional Lirik, Cinema & Antrean (Verifikasi S0)

| Modul | Status Kode Saat Ini | Temuan & Pekerjaan Lanjutan (S5) |
|---|---|---|
| **Lirik** | **Berjalan** (terverifikasi 17 unit test `R4LyricsParserAndRepositoryTest` + `MupaUserFlowsUiTest.flow5`): `LrcParser` (12+ kasus), `EmbeddedAudioLyricsReader` (ID3v2 USLT & FLAC Vorbis), `LocalLyricsProvider` (`.lrc` + tag), `LrcLibLyricsProvider` (`/api/get` + `/api/search` + `?q=` fallback, timeout 6 dtk), `LyricsRepository` (Room cache 30 hari / 500 entri / 24 jam negative cache), pre-fetch otomatis saat play, `onRetryLyrics` aktif. | Perlu integrasi warna **`onAccent` (OKLCH `L >= 0.87`) dari mesin UD S1** dan verifikasi penuh di atas latar UD (tanpa garis potong multi-line). |
| **Cinema** | **Berjalan** (terverifikasi `R3PlayerAndCinemaUiTest` + `MupaUserFlowsUiTest.flow4`): fullscreen, kontrol auto-fade 3,5 dtk, drag-down & Back bertahap (`Cinema → Full → Mini`). | Saat ini masih memakai `FullBleedBlurredCoverBackdrop` (box blur 128px); di **S5** diganti ke renderer **Ultra Dynamic** (3 radial gradient + drift pelan + grain, OLED `#000000`). |
| **Antrean** | **Berjalan** (`MupaQueueOverlay.kt`): lagu aktif tersemat, total durasi tabular, `"Bersihkan"`, long-press drag reorder clean-room, geser hapus `SwipeToDismissBox`. | Saat ini berlatar solid `MupaPalette.Background`; di **S5** diberi tint permukaan **UD** halus (`bgDeep` / `surface`) dan diverifikasi visualnya. |

---

## 3. Baseline Screenshot S0
Baseline sebelum perubahan Fase 2S (hasil akhir Fase 2R) telah tersedia dan diperiksa di `docs/screens/`:
- Beranda (`r2_home_360dp.png`, `r5_home_411dp_oled.png`, `r5_home_360dp_font13.png`)
- Cari (`r5_search_idle_360dp.png`, `r2_search_results_360dp.png`, `r5_search_longlist_b1_360dp.png`)
- Pustaka (`r5_library_streaming_360dp.png`, `r2_library_local_filled_360dp.png`, `r2_library_local_empty_360dp.png`)
- Akun (`r2_account_360dp.png`, `r5_account_360dp_font13.png`)
- Player, Lirik, Cinema, Antrean (`r3_now_playing_360dp.png`, `r3_now_playing_lyrics_360dp.png`, `r3_cinema_360dp.png`, `r3_queue_360dp.png`, `r5_now_playing_411dp_oled.png`, `r5_cinema_oled_360dp.png`)
