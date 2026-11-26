# Pertanyaan Keputusan Gerbang Tahap 0 (`QUESTIONS.md`)

> Silakan tinjau dan jawab poin-poin di bawah ini sebelum kita masuk ke **Tahap 1 (UI/UX)**. Setiap pertanyaan sudah disertai rekomendasi agar keputusan bisa cepat.

---

## Kelompok 1: Identitas Brand, Palet & Aset

### Q1. Nama pengganti fitur `"Mix X"`
Karena app direbrand total dari *Music X* menjadi **Mupa**, nama fitur rekomendasi lokal `"Mix X"` masih membawa huruf `"X"` dari nama lama.
- **Opsi:**
  1. **`Mix Mupa`** *(Rekomendasi — singkat, selaras brand baru, langsung dipahami)*
  2. **`Racikan Mupa`** *(lebih lokal/editorial, selaras tombol "Racik ulang")*
  3. **`Mix Harian`** / **`Mix Untukmu`** *(netral)*
  4. Nama lain pilihanmu?

### Q2. Pilihan Palet Aksen Baru Berbasis Logo Mupa (`#FF5A3C`, `#16161A`, `#F6F4EF`)
Lihat perbandingan detail & kontras WCAG di `docs/BRAND-MAP.md` (§2.3). Mana yang kamu pilih sebagai identitas dasar Mupa?
- **Opsi:**
  1. **Opsi A — Ember Editorial** *(Rekomendasi: Koral `#FF5A3C`/`#FF7357` + Amber hangat `#FFB067` + Krem `#F6F4EF` di atas `#0E0E11`/`#16161A`)*
  2. **Opsi B — Minimal Studio** *(UI dominan monokrom krem `#F6F4EF` & arang `#16161A`; koral `#FF5A3C` hanya muncul di aksi utama seperti Play, Lanjut, Like, indikator aktif)*
  3. **Opsi C — Contrast Dusk** *(Koral `#FF6B4E` dipasangkan dengan Biru Kabut `#7AA2F7` untuk kontras hangat-dingin)*
- **Sub-pertanyaan Q2b:** Apakah warna dinamis dari cover lagu yang sedang diputar tetap aktif untuk **Aurora & Now Playing** (dengan palet pilihanmu di atas sebagai warna utama saat belum ada lagu / pada chrome utama)? *(Rekomendasi: **Ya**, agar cover art tetap hidup dan wipe lirik tetap adaptif).*

### Q3. Wordmark & Lockup (`assets/logo/`)
File `mupa-wordmark_from-sheet_transparent.png` dan `mupa-lockup_from-sheet.png` adalah crop resolusi rendah dari lembar asli (tidak punya versi on-dark transparan yang tajam).
- **Opsi:**
  1. **Tahap 1 pakai glyph vektor SVG `"m"` + teks tipografi Compose bersih (`mupa`)** *(Rekomendasi — tajam di semua DPI & tema gelap; bisa diganti kapan saja jika nanti ada SVG wordmark bersih)*
  2. Kamu akan menyediakan file SVG wordmark/lockup bersih terlebih dulu sebelum Tahap 1?

---

## Kelompok 2: Konfigurasi Proyek Android & Alur Git/CI

### Q4. Konfirmasi `applicationId`
- Apakah kita kunci `applicationId = "com.xeno.mupa"` (dan `namespace = "com.xeno.mupa"`)? *(Rekomendasi: **Ya**, dengan suffix `.debug` untuk build debug agar bisa dipasang berdampingan).*

### Q5. Pilihan `minSdk`
- **Opsi 1: `minSdk = 26` (Android 8.0 Oreo)** *(Rekomendasi)*
  - **Plus:** Adaptive icon (`mipmap-anydpi-v26`) berjalan native tanpa perlu fallback bitmap PNG legacy; dukungan `NotificationChannel`, font variabel, dan API audio lebih bersih; mencakup mayoritas besar perangkat Android aktif; perawatan kode lebih sederhana.
  - **Minus:** Tidak bisa dipasang di Android 7.0–7.1 (API 24–25).
- **Opsi 2: `minSdk = 24` (Android 7.0 Nougat)** *(seperti Mochy)*
  - **Plus:** Mendukung perangkat Android 7 lama.
  - **Minus:** Wajib membuat fallback bitmap ikon legacy (`mipmap-mdpi..xxxhdpi`) tanpa mengubah desain, serta percabangan ekstra untuk API < 26.
- Mana yang kamu pilih: **`minSdk = 26`** atau **`minSdk = 24`**?

### Q6. Struktur Folder Proyek & Inisialisasi Git untuk GitHub Actions
Saat audit ditemukan bahwa `d:\Music-X-Native` **belum merupakan repositori Git** (tidak ada `.git`).
- **Pertanyaan:**
  1. Di mana root proyek Gradle Mupa sebaiknya diletakkan?
     - **Opsi A (Rekomendasi):** Langsung di root `d:\Music-X-Native/` (`settings.gradle.kts`, `app/`, `.github/workflows/`, `docs/`), dengan `Mochy/` dan `music-x-V8_5.html` dimasukkan ke `.gitignore` agar referensi lama tidak ikut ter-push ke GitHub maupun terbawa ke build.
     - **Opsi B:** Di subfolder terpisah, mis. `d:\Music-X-Native/mupa/`.
  2. Apakah mulai Tahap 1 kita inisialisasi `git init` lokal dengan pola **1 branch/commit per tahap** (sesuai Aturan #5) sehingga tinggal kamu push ke GitHub untuk memicu GitHub Actions?

---

## Kelompok 3: Lisensi & Strategi Ketergantungan (Berpengaruh Mulai Tahap 2)

### Q7. Model Lisensi Mupa (Terkait GPL-3.0 pada Ekosistem Streaming)
Hasil audit mengonfirmasi bahwa Mochy, `NewPipeExtractor`, `innertubex`, dan `media3-ffmpeg-decoder` semuanya berlisensi **GPL-3.0 (strong copyleft)**. Di Tahap 1 kita 100% clean-room dengan dependensi Apache-2.0/MIT saja. Namun untuk **Tahap 2 (Streaming)**:
- **Opsi 1: Mupa dirilis Open-Source GPL-3.0** *(Jika ingin fleksibilitas boleh memakai library seperti `NewPipeExtractor` atau FFmpeg decoder bila suatu saat dibutuhkan sebagai fallback).*
- **Opsi 2: Mupa Bebas GPL (Lisensi Permisif / MIT / Apache-2.0 / Privat)** *(Rekomendasi jika kamu ingin kendali penuh atas lisensi app: Tahap 2 kita tulis 100% klien InnerTube + cipher/n-param solver sendiri secara clean-room, dan tanpa menautkan `NewPipeExtractor`, `innertubex`, maupun Jellyfin FFmpeg).*
- Mana arah lisensi yang kamu inginkan untuk Mupa?

---

## Kelompok 4: Lingkup Minimal Tahap 1 (Onboarding & Pengaturan)

### Q8. Batas Maksimal Pilihan Artis di Onboarding
Syarat wajib: minimal **2 artis**, anjuran **4 artis**, tidak ada tombol lewati.
- Apakah batas maksimal **8 artis** cocok? *(Rekomendasi: **8** — cukup kaya sebagai seed rekomendasi tanpa membebani layar/query).*

### Q9. Penyederhanaan Pengaturan dari HTML V8.5 (Aturan #7)
HTML V8.5 memiliki banyak pengaturan berlapis (4 gaya kaca, 7 tier grafik, 5 tier FPS, 3 gaya lirik, streak/level, AI bahasa, menu beta). Untuk menjaga Mupa tetap rapi dan minimal di Tahap 1:
- **Usulan set pengaturan Tahap 1:**
  - Tampilan: **Gaya Kaca** (`Crystal` / `Liquid+`), **Mode OLED** (1 toggle), **Aurora Latar** (1 toggle), **Mode Performa / Kurangi Blur** (1 toggle).
  - Pustaka: **Hapus Nama Artis dari Judul** (1 toggle, default ON).
  - Akun: **Ubah Artis Favorit** (membuka ulang pemilih artis tanpa hapus data) + **Tentang Mupa**.
- **Sub-pertanyaan Q9b:** Untuk Tab **Akun**, apakah kamu ingin tampilan ringkas (Profil lokal, Ringkasan statistik dasar seperti jumlah lagu/favorit/waktu dengar, Artis Favorit, dan akses cepat Pengaturan), atau perlu membawa grafik mingguan/streak/level seperti HTML V8.5? *(Rekomendasi: **Ringkas & bersih** di Tahap 1 agar tidak penuh opsi berlebihan).*
