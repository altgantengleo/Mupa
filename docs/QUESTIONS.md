# Keputusan & Default Proyek Mupa (`QUESTIONS.md`)

> Seluruh pertanyaan gerbang Tahap 0 sudah diputuskan. Keputusan non-blokir yang diambil sebagai default dicatat di bagian akhir agar mudah ditinjau atau diubah kapan saja.

---

## Keputusan Final dari User

| ID | Topik | Keputusan Final |
|---|---|---|
| **Q1** | Nama pengganti `"Mix X"` | **`Mix Mupa`** |
| **Q2** | Palet Aksen & Dinamis | **Opsi A — Ember Editorial** (`#FF5A3C`/`#FF7357` + `#FFB067` + `#F6F4EF` di atas `#0E0E11`/`#16161A`). Warna dinamis cover art aktif untuk **Aurora & Now Playing**, otomatis nonaktif pada **Mode OLED / Mode Performa**. Teks di atas koral wajib gelap `#16161A`. |
| **Q3** | Wordmark / Lockup | Glyph SVG resmi + teks tipografi Compose bersih (`mupa`). Wordmark SVG bersih menyusul tanpa memblokir pengembangan. |
| **Q4** | `applicationId` | `com.xeno.mupa` (dengan suffix `.debug` untuk debug). |
| **Q5** | `minSdk` | **`26` (Android 8.0 Oreo)**. |
| **Q6** | Struktur Proyek & Git | Proyek Gradle di root `d:\Music-X-Native/`; `Mochy/` dan `music-x-V8_5.html` di `.gitignore`; `git init` lokal; remote GitHub disiapkan user; tanpa token/rahasia di file, log, atau commit. |
| **Q7** | Model Lisensi | **Open-Source GPL-3.0** (`LICENSE` GPL-3.0 + `NOTICE` atribusi dependensi). Tahap 2 boleh memakai `NewPipeExtractor` terisolasi di balik interface `StreamProvider`. Kode Mupa tetap ditulis sendiri (tidak disalin dari Mochy). |
| **Q8** | Batas Artis Onboarding | Min **2**, anjuran **4**, maksimal **8**, tanpa tombol lewati. |
| **Q9** | Pengaturan & Akun | Pengaturan diringkas (`Crystal`/`Liquid+`, Mode OLED, Aurora, Mode Performa, Hapus Nama Artis, Ubah Artis Favorit) & Tab Akun ringkas. |

---

## Keputusan Default Non-Blokir yang Diterapkan Otomatis

| ID | Topik | Default yang Diterapkan | Alasan & Dampak |
|---|---|---|---|
| **D1** | Autentikasi Streaming | **Tanpa login / tanpa cookie (guest mode)** | Meminimalkan kompleksitas & risiko keamanan kredensial; semua katalog, pencarian, dan streaming berjalan tanpa akun. |
| **D2** | Penyedia Streaming & Urutan Prioritas | **1. InnerTube (utama, + NewPipeExtractor terisolasi)** → **2. JioSaavn (fallback AAC 320/160 kbps)** | Berdasarkan `AUDIT.md`: InnerTube punya katalog terluas; JioSaavn memakai API langsung yang relatif stabil untuk fallback tanpa login. Monochrome sengaja tidak dimasukkan karena mirror pihak ketiga tidak stabil; mudah ditambahkan nanti lewat `StreamProvider`. Semua berjalan di balik `ProviderRegistry` + circuit breaker. |
| **D3** | Ukuran Cache Audio Default | **500 MB** (`500L * 1024 * 1024` byte) | Seimbang untuk pemutaran ulang cepat tanpa membebani penyimpanan HP spek rendah (bisa diatur di Pengaturan). |
| **D4** | Penyedia Lirik Online (Tahap 4) | **Tag tersemat lokal (Rust) → LRCLib (`https://lrclib.net/api/`)** | LRCLib open-source, tanpa auth, stabil, dan mendukung LRC tersinkron + teks polos. |
| **D5** | `compileSdk` & `targetSdk` | **`35` (Android 15)** | Stabil di SDK lokal & GitHub Actions `ubuntu-latest`, mendukung penuh API modern (edge-to-edge, foreground service types, `RenderEffect`). |
| **D6** | Binding Rust (Tahap 3) | **UniFFI (proc-macro / Kotlin JNA)** + fallback Kotlin Scoped Storage safe bila `libmupa_local.so` belum tersedia di host JVM unit test | Menjaga unit test JVM & CI tetap berjalan mulus sekaligus menyediakan engine Rust native penuh untuk `arm64-v8a` (dan `x86_64`). |
| **D7** | Rilis / Signing Lokal | Release build di Tahap 5 diverifikasi dengan `assembleRelease` (R8 minify + shrinkResources aktif; keystore release sesungguhnya disediakan user di GitHub Secrets bila nanti ingin publikasi). | Tidak pernah menyimpan keystore/password release di repositori. |
| **D8** | Tipografi Fase 2R | **Plus Jakarta Sans** static TTF (`400/500/600/700`, lisensi SIL OFL-1.1) dibundel di `:core:designsystem` + tercatat di `NOTICE`. | Konsisten lintas perangkat Android/HyperOS tanpa bergantung font sistem dan mendukung angka tabular (`tnum`). |
| **D9** | Permukaan Melayang & Blur (B1) | Default **`GlassStyle.SOLID`** (buram rapi + hairline); opsi **Crystal / Liquid+** memakai **`dev.chrisbanes.haze:haze-android:1.5.2`** (Apache-2.0, aktif hanya di API 31+ saat OLED & Mode Performa mati). | Mengatasi bug B1 (manual `RenderEffect` tidak mem-blur latar belakang sibling). Solid dipilih sebagai default aman sampai user mengonfirmasi kelancaran Haze di perangkat fisik. |
| **D10** | Reorder Antrean (R3) | Implementasi **clean-room** berbasis `detectDragGesturesAfterLongPress` + `SwipeToDismissBox` di `MupaQueueOverlay.kt`. | Nol dependensi pihak ketiga tambahan, nol spring overshoot, footprint APK tetap ringan. |
| **D11** | Sumber Lirik Eksternal (R4) | **Hanya LRCLIB** (`https://lrclib.net/api/get` & `/api/search`, open-source MIT, API publik resmi, timeout 6 dtk, toleransi durasi ±2 dtk) sebagai fallback setelah lirik lokal (`.lrc` sidecar & tag embedded `USLT`/Vorbis). | Musixmatch, Genius, dan NetEase tidak digunakan karena ToS melarang scraping/redistribusi lirik berhak cipta tanpa lisensi. Arsitektur `LyricsProvider` tetap modular bila nanti ingin menambah penyedia legal lain. |
| **D12** | Harness Screenshot JVM (R0/R2/R3/R5) | **Robolectric `4.14.1` (`@GraphicsMode(NATIVE)`) + `View.drawToBitmap()`** pada `360dp`, `411dp`, `fontScale 1.0/1.3`, dan OLED OFF/ON. | Berjalan cepat & deterministik di JVM lokal/CI tanpa emulator; bukti visual tersimpan dan diperiksa langsung di `docs/screens/`. |
