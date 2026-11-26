# Panduan Uji Manual Perangkat Nyata — Mupa (`TEST-DEVICE.md`)

> Dokumen ini mencakup seluruh aspek yang **TIDAK dapat diverifikasi di PC/CI** (pemutaran audio nyata, kelancaran animasi 60/120 Hz, `RenderEffect` GPU, pemuatan JNI Rust `libmupa_local.so`, scoped storage Android, dan perilaku background HyperOS/Xiaomi).

---

## 1. Status Jujur: Yang Sudah vs Belum Terverifikasi

### Sudah Terverifikasi di Lokal / CI
- Kompilasi seluruh 9 modul Gradle (`assembleDebug` & `assembleRelease` dengan R8 minify + shrinkResources).
- Android Lint (0 error pada seluruh modul Android).
- 30+ unit test JVM (kontras WCAG, tema OLED/Performa, navigasi 4 tab, Onboarding min 2 / maks 8 / `SavedStateHandle`, non-destruktif "Hapus Nama Artis", parser fixture InnerTube & JioSaavn tanpa jaringan, circuit breaker & fallback aggregator, JSON queue persistence, Mix Mupa MMR & exploration slots, DSP EQ 5-band RBJ biquad, parser LRC, dan Backup/Restore JSON tanpa rahasia).
- Paritas SHA-256 byte-for-byte seluruh 6 file ikon `assets/android-res/` → `app/src/main/res/`.
- Audit grep: 0 sisa kata `"mochy"` dan 0 sisa kata `"music x"` di seluruh kode, resource, package, dan CI.

### BELUM Terverifikasi di PC Lokal (Wajib Uji di GitHub Actions / HP Nyata)
1. **Kompilasi & unit test Rust (`cargo test` & `cargo-ndk` untuk `arm64-v8a` / `x86_64`):** `cargo` tidak terpasang di PC lokal ini; workflow `.github/workflows/android-ci.yml` telah disiapkan untuk menjalankan `cargo test` dan membangun `libmupa_local.so` di GitHub Actions.
2. **Eksekusi workflow CI di server GitHub:** Remote GitHub disiapkan oleh user sendiri.
3. **Pemutaran audio nyata (streaming InnerTube/JioSaavn & file lokal `content://`)** serta latensi buffer dan kualitas suara EQ 5-band.
4. **Kelancaran animasi 60/120 Hz & uji nol-kedip visual** saat buka-tutup Mini ↔ Full Player, Lirik wipe adaptif, dan `RenderEffect` pada GPU perangkat fisik.
5. **Tampilan ikon adaptive (bulat, squircle, kotak, themed monochrome)** dan lingkaran splash screen pada launcher nyata.
6. **Pemindaian Scoped Storage MediaStore/SAF nyata** dan pemuatan JNI `libmupa_local.so` di perangkat ARM64.
7. **Ketahanan playback background pada HyperOS / MIUI** (ketika layar mati > 15 menit).

---

## 2. Pengaturan Wajib di HP Xiaomi / HyperOS (Agar Playback Background Tidak Dimatikan)

HyperOS/MIUI agresif menghentikan foreground service media jika penghemat baterai dibiarkan default. Lakukan langkah ini setelah memasang APK debug/release:

1. **Penghemat Baterai (Wajib):**
   - Buka **Setelan → Aplikasi → Kelola aplikasi → Mupa → Penghemat baterai**.
   - Pilih **Tidak ada pembatasan** *(No restrictions)*.
2. **Mulai Otomatis di Latar Belakang:**
   - Di halaman info aplikasi **Mupa**, aktifkan **Mulai otomatis di latar belakang** *(Background autostart)*.
3. **Kunci Aplikasi di Recents (Disarankan saat uji panjang):**
   - Buka tombol *Recent Apps*, tekan tahan kartu **Mupa**, ketuk ikon **Gembok (Lock)** agar HyperOS tidak membersihkan proses saat RAM rendah.
4. **Izin Notifikasi & Audio (Android 13–15):**
   - Izinkan **Notifikasi** agar kontrol pemutar (`ic_notification`) tampil di shade & lockscreen.
   - Izinkan **Musik dan audio** (`READ_MEDIA_AUDIO`) untuk memindai file lokal via Scoped Storage.

---

## 3. Skenario Uji Manual di Perangkat Nyata

### A. Ikon Launcher, Splash & Branding
1. Pasang APK di HP Xiaomi/HyperOS.
2. Periksa ikon Mupa di Home Screen & App Drawer pada bentuk mask launcher (squircle/bulat/kotak): pastikan glyph `"m"` (badan krem `#F6F4EF`, kaki oranye `#FF5A3C` di atas `#16161A`) tidak terpotong tepi.
3. Aktifkan *Themed Icons* (Android 13+) di pengaturan wallpaper: verifikasi badan glyph solid dan kaki kanan lebih transparan (~55%).
4. Buka aplikasi dari cold start: verifikasi splash screen berlatar `#16161A` dengan glyph terpusat dan tidak terpotong lingkaran splash.

### B. Onboarding Artis Favorit (Wajib)
1. Buka Mupa pertama kali (fresh install): verifikasi layar **Pilih Artis Favoritmu** muncul sebelum Beranda dan **tidak ada tombol Lewati**.
2. Tekan tombol **Back** sistem: pastikan aplikasi keluar (tidak melewati onboarding ke Beranda).
3. Pilih 1 artis: verifikasi tombol **Lanjut** tetap nonaktif dan counter menunjukkan `1 / 8`.
4. Pilih artis ke-2: verifikasi tombol **Lanjut** aktif (warna koral `#FF5A3C`, teks gelap `#16161A` terbaca tajam).
5. Uji cari nama artis, putar layar (rotasi landscape ↔ portrait): pastikan pilihan sementara **tidak hilang**.
6. Pilih hingga 8 artis lalu coba ketuk artis ke-9: pastikan terkunci di `8 / 8`.
7. Tekan **Lanjut** → masuk ke Beranda. Verifikasi rak **Untukmu** menampilkan artis yang baru dipilih.
8. Tutup paksa dan buka ulang aplikasi: pastikan Onboarding **tidak muncul lagi**.
9. Buka **Akun → Ubah Artis Favorit**: ubah pilihan, tekan Simpan, pastikan Beranda terbarui tanpa menghapus data.

### C. Animasi Player (Mini ↔ Full), Lirik & Antrean (60 / 120 Hz)
1. Putar lagu dari Beranda: verifikasi Mini Player muncul mulus di atas Floating Glass Bottom Nav.
2. Ketuk Mini Player dan uji **drag-up / drag-down 1:1** berulang 10×:
   - Pastikan **nol pantulan / overshoot** (berhenti halus).
   - Pastikan **nol kedip / flash hitam** di awal, tengah, maupun akhir transisi.
   - Pastikan Bottom Nav ikut turun dan memudar secara sinkron.
3. Di Full Now Playing, buka **Lirik**: verifikasi sapuan warna adaptif terang terbaca di atas latar gelap, ketuk baris lirik untuk seek, dan uji kontrol **Offset ±0,5 dtk**.
4. Buka **Antrean**: geser naik/turun urutan lagu, hapus lagu, ketuk lagu lain.
5. Tekan tombol **Back** saat Lirik/Antrean terbuka: pastikan menutup overlay dulu; tekan **Back** lagi → menciutkan Full → Mini Player.

### D. Streaming, MediaSession, Notifikasi & Cache
1. Buka **Cari**, ketik judul lagu/artis online (dengan internet aktif): pastikan hasil InnerTube muncul tanpa login.
2. Putar lagu streaming, matikan layar selama 5–10 menit (setelah konfigurasi HyperOS di §2): verifikasi audio terus berjalan lancar tanpa putus.
3. Periksa notifikasi media di status bar & lockscreen: pastikan ikon kecil adalah glyph putih `ic_notification`, tombol Play/Pause/Next/Prev berfungsi, dan cabut earphone Bluetooth otomatis menjeda audio (`handleAudioBecomingNoisy`).
4. Matikan internet dan coba putar lagu yang baru selesai diputar: verifikasi pemutaran ulang memanfaatkan cache audio 500 MB.

### E. Mode Lokal (Scoped Storage + Rust JNI) & "Hapus Nama Artis"
1. Pastikan file MP3/FLAC lokal dengan format nama `"Artis - Judul (Official Audio).flac"` ada di folder `Music/` perangkat.
2. Buka **Pustaka → chip Lokal**: pastikan file terdeteksi lewat MediaStore/Scoped Storage.
3. Buka **Pengaturan → Hapus Nama Artis dari Judul**: matikan lalu nyalakan kembali — pastikan nama artis pulih utuh dari `originalFileName` (non-destruktif).
4. Uji **Akun → Pengaturan → Equalizer 5-Band** (di Now Playing tombol **EQ**): bandingkan preset `Flat`, `Bass`, `Vocal`, `Treble`, dan `Warm` pada earphone untuk memastikan perubahan frekuensi terdengar nyata tanpa distorsi/clipping.

### F. Mode OLED, Gaya Kaca & Mode Performa (HP Spek Rendah)
1. Di **Pengaturan**, ganti **Gaya Kaca** antara `Crystal` dan `Liquid+`: amati perbedaan kejernihan & rim kaca pada Floating Bottom Nav & kartu.
2. Aktifkan **Mode OLED**: pastikan latar menjadi `#000000` murni, Aurora & blur nonaktif total.
3. Matikan OLED, aktifkan **Mode Performa**: pastikan blur & warna dinamis sampul nonaktif untuk menghemat GPU/baterai.
4. Uji di Pengaturan font sistem Android diperbesar (**Large / Largest**): pastikan tidak ada teks terpotong/tumpang tindih parah di Bottom Nav, Onboarding, maupun Player.
