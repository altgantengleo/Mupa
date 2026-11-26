# Peta Brand & Design System Mupa (`BRAND-MAP.md`)

> Dokumen ini memverifikasi aset brand Mupa, mengusulkan palet warna aksen baru berbasis logo (dibandingkan dengan HTML Music X lama), serta memetakan elemen referensi Mochy dan Music X ke padanan asli Mupa.

---

## 1. Verifikasi Aset Logo & Ikon Mupa

### 1.1 Warna Terverifikasi dari File Sumber SVG & XML
Pembacaan langsung atas file sumber di `assets/logo/` dan `assets/android-res/` mengonfirmasi 3 warna inti identik 100%:

| Nama Token | Kode Hex Terverifikasi | Sumber File | Peran Brand |
|---|---|---|---|
| **Mupa Coral (Oranye)** | `#FF5A3C` | `mupa-glyph-accent-ondark.svg:3`, `mupa-glyph-accent.svg:3`, `mupa_icon_colors.xml:5` | Kaki kanan glyph `"m"` — aksen utama khas Mupa |
| **Mupa Charcoal (Gelap)** | `#16161A` | `mupa-glyph-accent.svg:2`, `mupa_icon_colors.xml:3` | Latar ikon launcher & splash, badan glyph versi terang |
| **Mupa Cream (Krem)** | `#F6F4EF` | `mupa-glyph-accent-ondark.svg:2`, `mupa_icon_colors.xml:4` | Badan glyph versi gelap (`on-dark`), teks/tinta utama di latar gelap |

### 1.2 Audit Teknis Aset `assets/logo/` & `assets/android-res/`

| Aset | Status | Hasil Verifikasi Teknis |
|---|---|---|
| `assets/logo/mupa-glyph-accent-ondark.svg` | **Siap pakai** | `viewBox="0 0 96 96"`, 2 path bersih tanpa gradient (`#F6F4EF` + `#FF5A3C`). Bounding box glyph: X `20..76` (lebar 56), Y `26..70` (tinggi 44), tepat terpusat di `(48, 48)`. |
| `assets/logo/mupa-glyph-accent.svg` | **Siap pakai** | Versi latar terang (`#16161A` + `#FF5A3C`), geometri identik. |
| `assets/logo/mupa-glyph.svg` | **Siap pakai** | 1 path (`fill="currentColor"`), ideal untuk ikon kecil/tint satu warna. |
| `assets/android-res/drawable/ic_launcher_foreground.xml` | **Siap salin & uji build** | Canvas `108×108dp`, grup `pivot(48,48) scale(0.8) translate(6,6)` → pusat glyph tepat di `(54, 54)`. Ukuran akhir `44.8 × 35.2dp`, radius sudut terjauh dari pusat **~28,5dp** — aman jauh di dalam *safe zone* adaptive icon **66dp (r=33dp)** maupun lingkaran splash Android 12+ **72dp (r=36dp)**. Tidak akan terpotong di mask bulat, squircle, maupun kotak. |
| `assets/android-res/drawable/ic_launcher_monochrome.xml` | **Siap salin & uji build** | Menggunakan 2 path terpisah (`#FF000000` alpha 100% untuk badan, `fillAlpha="0.55"` untuk kaki kanan) sesuai catatan `README.md` untuk Android 13+ Themed Icon. |
| `assets/android-res/drawable/ic_notification.xml` | **Siap salin & uji build** | Canvas `24×24dp`, `scale(0.357) translate(-36,-36)` → pusat `(12,12)`, ukuran ~`20,0 × 15,7dp`, warna murni `#FFFFFFFF` (valid untuk tint sistem notifikasi). |
| `assets/android-res/mipmap-anydpi-v26/ic_launcher*.xml` | **Siap salin & uji build** | Mendeklarasikan `<background>`, `<foreground>`, dan `<monochrome>` lengkap untuk `ic_launcher` dan `ic_launcher_round`. *(Catatan: jika `minSdk < 26`, perlu fallback bitmap mipmap `mdpi..xxxhdpi` tanpa mengubah desain.)* |
| `mupa-wordmark_from-sheet_transparent.png` & `mupa-lockup_from-sheet.png` | **Perlu perhatian (crop sementara)** | Berdasarkan inspeksi visual, `mupa-wordmark_from-sheet_transparent.png` berwarna gelap (`#16161A`) bertepi sedikit bergerigi (crop resolusi rendah) dan tidak terbaca di tema gelap tanpa tint; `mupa-lockup_from-sheet.png` memiliki latar krem solid (tidak transparan). **Rekomendasi Tahap 1:** gunakan VectorDrawable glyph `"m"` + tipografi Compose bersih (`mupa`) untuk header/onboarding/tentang, atau minta user menyediakan SVG wordmark vektor bersih bila ingin memakai huruf kustom persis lembar asli. |

---

## 2. Perbandingan Palet HTML Lama vs Usulan Palet Baru Mupa

### 2.1 Palet Referensi Lama (`music-x-V8_5.html`)
- Latar gelap bernuansa ungu tua: `--bg: #121014`, `--bg-elevated: #1A171D`, `--bg-card: #201D24`, `--border: #2A262F`.
- Aksen utama ungu pastel: `--purple: #A78BFA` (`#6D5B99` dim), teks `#F0EEF2`.
- **Masalah bila dipakai ke Mupa:** Nuansa ungu dingin bertabrakan langsung dengan identitas logo Mupa yang hangat dan berkarakter (**koral oranye `#FF5A3C` + krem `#F6F4EF` + arang netral `#16161A`**).

### 2.2 Verifikasi Kontras WCAG 2.1 Warna Kunci Mupa
*(Dihitung matematis langsung dari luminansi relatif sRGB)*

| Warna Foreground | Hex | Kontras vs `#000000` (OLED) | Kontras vs `#0E0E11` (Canvas) | Kontras vs `#16161A` (Surface) | Kontras vs `#1E1E24` (Card) | Status WCAG |
|---|---|---:|---:|---:|---:|---|
| **Mupa Cream** (Teks Utama) | `#F6F4EF` | 19,11:1 | 17,53:1 | 16,42:1 | 15,09:1 | AAA |
| **Warm Gray** (Teks Sekunder) | `#B4B0A8` | 9,72:1 | 8,92:1 | 8,35:1 | 7,67:1 | AAA |
| **Muted Gray** (Label/Hint) | `#85828A` | 5,56:1 | 5,10:1 | 4,78:1 | 4,39:1 | AA (di surface gelap) |
| **Logo Coral** (Brand Accent) | `#FF5A3C` | 6,78:1 | 6,22:1 | 5,83:1 | 5,35:1 | AA+ |
| **Bright Coral** (Primary Dark UI) | `#FF7357` | 7,84:1 | 7,20:1 | 6,74:1 | 6,19:1 | AA+ / AAA |
| **Soft Coral** (Teks Aksen / Link) | `#FF9A84` | 10,21:1 | 9,37:1 | 8,77:1 | 8,06:1 | AAA |
| **Warm Amber** (Sekunder Opsi A) | `#FFB067` | 11,66:1 | 10,70:1 | 10,02:1 | 9,21:1 | AAA |
| **Mist Blue** (Sekunder Opsi C) | `#7AA2F7` | 8,34:1 | 7,65:1 | 7,17:1 | 6,58:1 | AAA |

> **Catatan Kritis Kontras Tombol (`onPrimary`):** Teks putih `#FFFFFF` di atas `#FF5A3C` hanya menghasilkan **3,10:1** (gagal WCAG AA untuk teks normal). Sebaliknya, tinta gelap logo **`#16161A` di atas `#FF5A3C`** menghasilkan **5,83:1** (dan **6,74:1** di atas `#FF7357`) — lolos WCAG AA dengan nyaman sekaligus terlihat sangat premium.

### 2.3 Tiga Usulan Palet Aksen Mupa (Untuk Dipilih User di Gerbang Tahap 0)

| Token / Elemen | **Opsi A: Ember Editorial (Rekomendasi)** | **Opsi B: Minimal Studio (Quiet Luxury)** | **Opsi C: Contrast Dusk (Vibrant Dual)** |
|---|---|---|---|
| **Karakter Visual** | Hangat, modern, 100% selaras dengan jiwa logo Mupa (koral + amber hangat di atas arang). | Sangat tenang & fokus pada cover art; UI dominan krem & arang, koral hanya jadi percikan pada aksi paling penting. | Lebih ekspresif dengan kontras hangat-dingin (koral + biru kabut), meneruskan energi aurora dinamis Music X lama tanpa ungu. |
| `background` (Canvas) | `#0E0E11` | `#0C0C0F` | `#0E0E12` |
| `surface` / `surfaceContainerLow` | `#16161A` (persis logo) | `#16161A` (persis logo) | `#16161A` (persis logo) |
| `surfaceContainer` (Card/Sheet) | `#1E1E24` | `#1C1C21` | `#1E1F26` |
| `surfaceContainerHigh` (Chip/Input) | `#26262E` | `#25252B` | `#272832` |
| `outlineVariant` (Border halus) | `#2E2E36` | `#2A2A31` | `#2F313D` |
| `primary` (Tombol utama / Aktif) | `#FF5A3C` (CTA/badge) · `#FF7357` (ikon/teks aktif) | `#F6F4EF` (umum) · `#FF5A3C` (Play, Lanjut, Like, indikator aktif) | `#FF6B4E` |
| `onPrimary` | `#16161A` (kontras 5,83–6,74:1) | `#16161A` (16,42:1 di krem / 5,83:1 di koral) | `#16161A` (6,45:1) |
| `primaryContainer` / `onPrimaryContainer` | `#3D1F1B` / `#FFDAD2` | `#2A292E` / `#F6F4EF` | `#3B201D` / `#FFDAD2` |
| `secondary` (Aurora blob 2 / tag) | `#FFB067` (Warm Amber) | `#B4B0A8` (Warm Stone) | `#7AA2F7` (Mist Blue) |
| `tertiary` (Aksen lembut / Lirik fallback) | `#FF9A84` (Soft Coral) | `#FF8C73` (Muted Coral) | `#E87A90` (Warm Rose) |
| `onBackground` / `onSurface` | `#F6F4EF` (Logo Cream) | `#F6F4EF` (Logo Cream) | `#F6F4EF` (Logo Cream) |
| `onSurfaceVariant` | `#B4B0A8` | `#A8A49E` | `#B0B2BE` |
| **Default Aurora (tanpa cover)** | Blob 1 `#FF5A3C` (32%) + Blob 2 `#FFB067` (22%) | Blob 1 `#FF5A3C` (20%) + Blob 2 `#F6F4EF` (10%) | Blob 1 `#FF5A3C` (28%) + Blob 2 `#7AA2F7` (22%) |
| **Mode OLED (semua opsi)** | Bg `#000000`, Card `#0A0A0E`, Border `#1E1E24`, Blur & Aurora OFF, Primary tetap menjaga aksen terpilih (atau krem netral pada chip) | Bg `#000000`, Card `#09090C`, Border `#1C1C22`, Blur & Aurora OFF | Bg `#000000`, Card `#0A0A0E`, Border `#1E1E26`, Blur & Aurora OFF |

---

## 3. Pemetaan Elemen Mochy → Padanan Mupa (Clean-Room)

> **Prinsip:** Tidak ada satupun kelas, nama, aset, atau kode sumber Mochy yang disalin. Yang diambil **hanya pemahaman pola** pada 2 domain: (1) mekanisme animasi player & (2) arsitektur lapisan streaming.

| Area | Di Mochy (Referensi GPL-3.0) | Padanan di Mupa (Implementasi Baru Clean-Room) |
|---|---|---|
| **Identitas & Package** | `Mochy Music`, `com.mochyy.mochymusic` | **MUPA**, `com.xeno.mupa` *(menunggu konfirmasi user)* |
| **Ikon & Splash** | 50 `<activity-alias>` + `ic_splash_mark` + `LaunchIntroOverlay` | 1 set adaptive icon Mupa (`assets/android-res/`), `core-splashscreen` (`ic_launcher_foreground` di atas `#16161A`), tanpa bloat 50 alias ikon |
| **Onboarding** | `SetupWizardScreen` (13 step: maskot, impor, profil, benchmark blur, crossmix) — **tanpa pilih artis** | **`ArtistOnboardingScreen` (wajib, 1 layar fokus seperti Spotify):** cari & pilih minimal 2 artis (anjuran 4, usul maks 8), tanpa lewati, simpan DataStore, ubah ulang dari Akun |
| **Navigasi 4 Tab** | `Home`, `Search`, `Library`, `Settings` (floating pill + `NavPill` berspring pantul) | **`Beranda`, `Cari`, `Pustaka`, `Akun`** (floating glass pill 4 tab gaya Music X, transisi indikator halus **tanpa overshoot**) |
| **Transisi Mini ↔ Full Player** | `BottomSheetState` (`Animatable<Dp>`, `progress 0f..1f`), `MorphingCover` (`Modifier.layout`), `PlayerSheetAnimationSpec` (`dampingRatio = 0.96f` — ada sedikit bounce) | **`MupaPlayerSheetState` (tulis ulang dari nol):** progress-driven `0f..1f`, drag 1:1 sinkron (tanpa antre coroutine), fling dengan critically damped `dampingRatio = 1f` / emphasized decelerate (`0%` overshoot), cover morph via `Modifier.layout` + `graphicsLayer`, tanpa `Offscreen` FBO layar penuh |
| **Transisi Queue & Lyrics** | `PlayerPageTransition` (`OpenSpring 0.82f` memantul) + `PlayerBottomCardStack` + 2-frame pre-mount | **Sheet/pane transisi mulus** berbasis emphasized decelerate (tanpa bounce) + mengadopsi **pola 2-frame pre-mount** agar buka Lirik/Antrean 0 jank |
| **Glass / Blur** | `GlassKit` + AGSL + EGL/GLES 3.0 Dual-Kawase fallback | **Sederhana & stabil:** `RenderEffect` hanya di Android 12+ (API 31+), fallback permukaan solid/translusen elegan di < API 31, menghormati toggle performa & Mode OLED |
| **Arsitektur Streaming** | `YouTube` singleton bocor ke 63 file UI + `:modules:api` yang bersih | **Clean Architecture penuh:** `:core:model` & `:core:domain` (`MusicSource`, `StreamProvider`, `ArtistRepository`, `LyricsRepository`), tiap penyedia (InnerTube, penyedia fallback lain) terisolasi di modul Gradle sendiri (`:source:innertube`, `:source:...`) di Tahap 2 |
| **Local Mode** | `DeviceMusicScanner` berbasis Kotlin/MediaStore | **Engine Rust native** (`:engine:local-rust`, Tahap 3) via UniFFI/JNI untuk import, scan folder, parsing tag/artwork, DB lokal, rekomendasi lokal, & aturan "Hapus Nama Artis" |
| **Audio DSP / EQ** | 10 AudioProcessor + C++ JamesDSP/USB DAC/3D Stage | EQ 5-band bersih (`60/250/1k/4k/12k Hz`, ±12 dB, 5 preset + custom) + volume/tempo dasar di Tahap 4 (minimal & ringan) |

---

## 4. Pemetaan Fitur Music X V8.5 HTML → Mupa

| Fitur di `music-x-V8_5.html` | Status di Mupa | Catatan Desain & Tahap |
|---|---|---|
| **Bottom Nav 4 Tab** (`Beranda`, `Cari`, `Pustaka`, `Akun`) | **Dipertahankan** | Floating glass bar (Crystal/Liquid+) + fallback solid di OLED/<API 31 (Tahap 1) |
| **Mode Semi Player** | **DIHAPUS** | Diganti langsung transisi **Mini ↔ Full** yang mulus seperti pola Mochy; 5 aksi sekunder (Favorit, EQ, Lirik, Antrean, Timer) dipindah rapi ke Full Now Playing (Tahap 1) |
| **Gaya Glass (`Crystal` & `Liquid+`)** | **Dipertahankan (disederhanakan)** | Fokus pada 2 gaya utama (`Crystal` & `Liquid+`) + toggle Performa/Matikan Blur; hindari 7 tier grafik + 5 tier FPS yang berlebihan (Tahap 1) |
| **Aurora Background** | **Dipertahankan** | 2 gradien radial lembut di bagian atas (warna dari palet logo / cover art aktif), otomatis mati saat Mode OLED atau toggle OFF (Tahap 1) |
| **Mode OLED** | **Dipertahankan** | 1 toggle tunggal: latar `#000000`, card `#0A0A0E`, border tipis, matikan blur & aurora (Tahap 1) |
| **Rekomendasi Lokal (`Mix X`, `Ulangi lagi`, `Belum diputar`)** | **Dipertahankan & direbrand** | Nama **`Mix X` diusulkan ganti nama** (mis. `Mix Mupa` / `Racikan Mupa`, lihat `QUESTIONS.md`). UI dummy di Tahap 1, mesin skor lokal di Rust pada Tahap 3, ditambah rak `Untukmu` berbasis artis onboarding |
| **Lirik + Wipe Warna Adaptif** | **Dipertahankan** | UI + simulasi wipe adaptif di Tahap 1; sinkronisasi LRC/word-level, tag tersemat, dan penyedia online di Tahap 4 (kontras OKLCH/luminansi terjaga untuk cover gelap) |
| **Equalizer 5-Band + Preset** | **Dipertahankan** | Komponen/layar UI EQ di Tahap 1/4 (5 band `60/250/1k/4k/12k Hz`, ±12 dB, 5 preset), dihubungkan ke `AudioProcessor`/DSP Media3 di Tahap 4 |
| **Pengaturan "Hapus Nama Artis"** | **Dipertahankan** | Toggle + cakupan ringkas di Pengaturan; penyimpanan permanen `originalFileName` dan parser clutter diimplementasikan penuh di Rust pada Tahap 3 |
| **Opsi Berlebihan HTML** (7 Graphic tiers, 5 FPS modes, Developer Easter Egg `Leo11x`, AI Bahasa, Hybrid Blur Beta, Kartu Kutipan Video) | **Dibuang / Ditunda** | Sesuai Aturan #7 (*fitur/setting seminimal mungkin*). Fokus ke pengalaman inti yang bersih, cepat, dan mudah dipahami |

---

## 5. Konvensi Copywriting & Bahasa UI Mupa

- **Bahasa Utama:** Bahasa Indonesia natural, singkat, modern, dan konsisten (tanpa campuran istilah kaku).
- **Nama Brand:** **Mupa** (penulisan di teks berjalan: `Mupa`; di wordmark/ikon: `mupa` atau `MUPA` sesuai konteks visual).
- **Nol jejak brand lama:** Tidak boleh ada kata `"Mochy"` di seluruh proyek, dan tidak boleh ada kata `"Music X"` / `"MusicX"` di UI, resource, manifest, maupun nama package (hanya boleh disebut di folder `docs/` sebagai dokumen referensi historis).
- **Istilah Standar UI:** `Beranda`, `Cari`, `Pustaka`, `Akun`, `Sedang Diputar`, `Lirik`, `Antrean`, `Pengaturan`, `Artis Favorit`, `Ulangi Lagi`, `Belum Diputar`, `Untukmu`, `Mode OLED`, `Hapus Nama Artis`.
