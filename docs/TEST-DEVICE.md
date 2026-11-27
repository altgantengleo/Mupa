# Panduan Uji Manual Perangkat Nyata — Mupa (`TEST-DEVICE.md`)

> Dokumen ini mencakup seluruh aspek yang **TIDAK dapat diverifikasi di PC/CI** (pemutaran audio nyata, kelancaran animasi 60/120 Hz, blur Haze/RenderEffect GPU, SAF file manager nyata, pencabutan izin file lokal, dan perilaku background HyperOS/Xiaomi). Setiap butir di bawah berstatus **"belum terverifikasi di perangkat fisik"** hingga diuji langsung oleh user.

---

## 1. Status Jujur: Sudah Terverifikasi di PC vs Belum Terverifikasi di Perangkat

### Sudah Terverifikasi di PC Lokal (Branch `fase-2r`)
- Kompilasi seluruh modul Gradle (`assembleDebug` & `assembleRelease` dengan R8 minify + shrinkResources).
- Android Lint (`lintDebug`: 0 error).
- Seluruh unit test & Compose UI test Robolectric (`MupaUserFlowsUiTest`, `R1FoundationAndLocalRulesTest`, `R3PlayerAndCinemaUiTest`, `R4LyricsParserAndRepositoryTest`, `BaselineScreenshotsTest`, `R5ScreenshotMatrixTest`, dan seluruh suite Tahap 1–5).
- Inspeksi visual langsung screenshot JVM (`docs/screens/`): baseline R0 (4 layar) vs hasil R2/R3/R5 meliputi `360dp`, `411dp`, `fontScale 1.0` & `1.3`, OLED OFF & ON, semua status Cari (awal/hasil/memuat/error), Pustaka (Streaming, Lokal kosong, Lokal terisi), Beranda panjang + Mini+Nav (bukti B1), Now Playing + Lirik, Cinema, Antrean, dan Akun.
- Audit brand (`mochy`, `music x`, `musicx` di luar `docs/`): **0 temuan**.
- Audit overshoot (`dampingRatio < 1f`): **0 temuan**.
- Paritas SHA-256 ikon (`assets/android-res/` → `app/src/main/res/`): **6 / 6 identik**.

### BELUM Terverifikasi di PC Lokal (Wajib Uji di HP Nyata)
1. **Impor SAF file & folder nyata** (`OpenMultipleDocuments` & `OpenDocumentTree`), `takePersistableUriPermission`, pembacaan tag embedded & artwork lokal pada berbagai codec (MP3/FLAC/M4A/OPUS), serta **pencabutan izin / penghapusan file di File Manager**.
2. **Pemutaran audio nyata** (streaming InnerTube/JioSaavn, segmen buffer, cache 500 MB, perpindahan Wi-Fi ↔ seluler, dan audio file lokal `content://`) serta EQ 5-band nyata di earphone.
3. **Buka-tutup cepat Player, half-drag release, rotasi layar saat player terbuka, dan kelancaran 60/120 Hz** (nol jank, nol kedip/flash hitam).
4. **Transisi lirik cepat & sinkronisasi real-time** saat lagu berputar, tap-to-seek, scroll manual + jeda 3 dtk, offset ±0,5 dtk, dan wipe OKLCH pada sampul gelap/terang nyata.
5. **Efek blur Haze (`Crystal` & `Liquid+`) di API 31+ GPU nyata** vs `Solid` default, Mode OLED `#000000`, dan Mode Performa.
6. **Ikon adaptive & Themed Icon** pada launcher nyata + splash screen cold start.
7. **Ketahanan playback background di HyperOS / MIUI** (layar mati > 15 menit, `onTaskRemoved`, notifikasi lockscreen, cabut Bluetooth).
8. **Eksekusi `cargo test` & `cargo-ndk` Rust (`arm64-v8a`, `x86_64`)** di GitHub Actions (`cargo` tidak terpasang di PC lokal).

---

## 2. Pengaturan Wajib di HP Xiaomi / HyperOS

1. **Penghemat Baterai:** Setelan → Aplikasi → Kelola aplikasi → **Mupa** → Penghemat baterai → **Tidak ada pembatasan**.
2. **Mulai Otomatis di Latar Belakang:** Aktifkan pada halaman info aplikasi Mupa.
3. **Kunci di Recents** saat uji pemutaran latar belakang panjang.
4. **Izin Notifikasi (Android 13–15):** Izinkan agar kontrol pemutar Media3 tampil di shade & lockscreen.

---

## 3. Skenario Uji Manual Fase 2R di Perangkat Nyata

### A. Impor SAF Lokal, File Dihapus & Pemisahan Sumber (B7 & B12)
1. Buka **Pustaka**, alihkan switch header ke **Lokal (ikon ponsel)**: pastikan layar kosong bersih (TIDAK menarik audio WhatsApp/sistem otomatis) dan menampilkan tombol **Impor Lagu** & **Impor Folder**.
2. Ketuk **Impor Lagu** → pilih beberapa file MP3/FLAC: pastikan judul, artis, album, durasi, dan sampul terkompresi muncul rapi tanpa `"0:00"` dan tanpa baris kedua `"Lokal"` jika tag artis/album ada.
3. Ketuk **Impor Folder** → pilih 1 folder musik: pastikan seluruh lagu dalam folder terimpor dan tetap terbaca setelah aplikasi ditutup paksa & dibuka kembali (`takePersistableUriPermission`).
4. Letakkan file `.lrc` bernama sama di folder musik tersebut → putar lagunya → buka Lirik → pastikan label sumber menunjukkan **`File .lrc`**.
5. Hapus atau pindahkan salah satu file lagu lokal lewat File Manager Android → coba putar lagu tersebut di Mupa: pastikan aplikasi **tidak crash**, menampilkan pesan yang jelas, dan skip aman ke lagu berikutnya. Ketuk **Pindai Ulang** di Pustaka Lokal → pastikan file yang hilang tidak lagi tampil.
6. Alihkan switch ke **Streaming (ikon awan)**: pastikan daftar Streaming & Lokal tidak pernah tercampur.

### B. Player Mini ↔ Full, Half-Drag, Rotasi, Cinema & Back Bertahap (B6 & B11)
1. Putar lagu → pastikan **Mini Player** solid rapi (tombol Play/Pause koral 40dp, tidak ada teks daftar tembus di belakangnya).
2. **Tap** Mini Player → pastikan Full Now Playing terbuka penuh tanpa kedip hitam dan tanpa pantulan.
3. Uji **drag-up / drag-down 1:1** cepat 10× dan **half-drag release** (tarik 35% lalu lepas → kembali ke posisi asal; tarik 55% lalu lepas → lanjut ke tujuan).
4. **Rotasi layar** saat Full Now Playing terbuka → pastikan sheet tetap terbuka dan tidak macet di posisi setengah.
5. Ketuk ikon **Cinema** → pastikan masuk layar penuh dengan backdrop blur one-shot (atau `#000000` di Mode OLED), tunggu **3 detik** tanpa sentuh saat lagu diputar → kontrol bawah memudar otomatis; ketuk layar → kontrol muncul lagi.
6. Uji **Back bertahap**: dari Cinema tekan Back → kembali ke Full Now Playing; buka Antrean/EQ/Lirik lalu tekan Back → tutup overlay; tekan Back lagi → ciutkan Full ke Mini Player.
7. Uji **Antrean**: tekan tahan (`long-press`) pegangan drag untuk ubah urutan lagu, geser baris ke kiri untuk hapus, ketuk **Bersihkan** untuk menghapus lagu berikutnya.

### C. Lirik Cepat, Wipe OKLCH, Offset & Cache Offline (R4)
1. Putar lagu streaming populer (mis. *blue — yung kai*) → ketuk ikon **Lirik** di Full Now Playing:
   - Pastikan loading menampilkan skeleton (bukan layar kosong/beku), playback tidak tersendat.
   - Amati transisi antar baris cepat: auto-scroll halus memusatkan baris aktif di ~40% layar, tidak ada lonjakan tinggi baris, dan sapuan warna OKLCH terbaca terang bahkan pada cover gelap.
2. Scroll manual ke baris lain → pastikan auto-scroll berhenti sementara **~3 detik**, lalu ketuk salah satu baris → lagu langsung seek ke timestamp tersebut.
3. Uji tombol **Offset ±0,5 dtk** dan ketuk teks offset untuk reset ke `0,0 dtk`.
4. Matikan internet dan buka ulang lagu yang sama → pastikan lirik langsung tampil dari **cache Room** (TTL 30 hari).

### D. Permukaan Melayang (Solid / Crystal / Liquid+), OLED, Performa & 120 Hz (B1, B8, B10)
1. Scroll panjang daftar di Beranda & Cari pada default **Solid**: pastikan **0 teks tembus** di belakang Mini Player & Bottom Nav.
2. Di tab **Akun → Permukaan Melayang**, coba **Crystal** lalu **Liquid+** (Android 12+ / API 31+): periksa kualitas blur Haze & kestabilan frame rate 120 Hz; jika ada artefak GPU, kembalikan ke **Solid**.
3. Aktifkan **Mode OLED**: pastikan latar `#000000` murni di Beranda, Now Playing, dan Cinema; Aurora & blur mati total.
4. Aktifkan **Mode Performa**: pastikan blur, tint cover dinamis, dan backdrop blur Cinema nonaktif.
5. Perbesar ukuran font sistem Android ke **Besar (1.3×)**: periksa Beranda, Cari, Pustaka, Akun, dan Now Playing agar tetap rapi.

### E. Ikon Launcher, Themed Icon, Notifikasi & HyperOS Background
1. Periksa ikon Mupa di launcher (bulat/squircle/kotak) dan aktifkan *Themed Icons* Android 13+.
2. Cek cold start splash screen (`#16161A` + glyph terpusat).
3. Putar lagu streaming → matikan layar 15 menit (setelah setelan §2) → pastikan audio tidak mati; cabut earphone Bluetooth → pastikan otomatis jeda; jeda lagu lalu geser aplikasi dari Recents → pastikan notifikasi & service berhenti bersih (`onTaskRemoved`).
4. Cek LeakCanary di build **Debug**: lakukan 5× siklus buka-tutup layar, player, Cinema, dan rotasi → pastikan tidak ada notifikasi leak memori.

---

## 4. Skenario Uji Manual Fase 2S & 2T (Status: BELUM TERVERIFIKASI DI PERANGKAT FISIK)

### F. Ultra Dynamic, Cover Morph 520/440 ms, Hero & Pengaturan (Fase 2S)
1. **Cover biru/pasir (`blue - yung kai`):** Pastikan latar Now Playing & Cinema memunculkan rona biru + pasir (bukan abu kecoklatan) dan tidak ada pita hitam di atas status bar.
2. **Animasi cover Mini ↔ Full:** Uji tap buka (520 ms), tap tutup (440 ms), half-drag release, serta ganti lagu saat Now Playing terbuka pada layar 60/120 Hz — pastikan tidak ada overshoot/pantulan dan tidak ada kedip hitam.
3. **Hero Beranda (Lagu Terakhir Diputar):** Putar satu lagu lalu kembali ke Beranda → pastikan Hero menampilkan lagu terakhir yang diputar (bukan Mix Mupa) dengan parallax `0,5×` saat scroll.
4. **Akun & Pengaturan terpisah:** Buka **Akun → ikon gerigi / baris Pengaturan** → uji ganti Solid/Crystal/Liquid+, Ultra Dynamic, OLED, Performa, Equalizer, Hapus cache audio, Hapus cache gambar & lirik, Cadangkan/Pulihkan JSON, dan layar **Atribusi & Lisensi**.

### G. Antrean Cerdas, Bug `"blue"`, Shuffle & Multi-Provider (Fase 2T)
1. **Uji Bug `"blue"`:** Buka tab **Cari**, ketik `"blue"`, ketuk hasil utama `"blue - yung kai"`. Buka sheet **Antrean** dan lihat header Now Playing:
   - Pastikan label sumber menampilkan **`Radio dari 'blue'`**.
   - Pastikan daftar *Berikutnya* berisi lagu-lagu radio serupa yang bersih (**BUKAN** versi Slowed, Acoustic, Live, Karaoke, Sped Up dari `"blue"`).
2. **Continuation otomatis & persistensi:** Lewati lagu hingga sisa antrean `<= 3` → pastikan lagu radio baru masuk otomatis. Tutup paksa aplikasi lalu buka lagi → pastikan antrean dan label sumber pulih.
3. **Shuffle deterministik & tombol Acak:**
   - Nyalakan Shuffle di Now Playing → lagu aktif tidak bergeser; tekan **Berikutnya**, lalu tekan **Sebelumnya** → harus kembali ke lagu yang tepat sebelumnya.
   - Matikan Shuffle → sisa antrean kembali ke urutan asli.
   - Buka **Pustaka → Lagu** → ketuk ikon **Acak** di header daftar → pastikan langsung memutar secara acak.
4. **Onboarding Artis & Pencarian Multi-Provider (Deezer + iTunes + InnerTube):**
   - Buka **Akun → Artis Favorit → Ubah** → amati daftar artis berfoto HD muncul cepat secara progresif, uji cari `"Tulus"`, `"Tommy J. Pisa"`, `"yung kai"`, `"Bruno Mars"`, `"Justin Bieber"`.
   - Ketuk hasil pencarian yang berasal dari katalog Deezer/iTunes → pastikan lagu yang diputar sesuai (judul, artis, durasi ±3 dtk) tanpa salah putar.

### H. Cara Membaca Instrumentasi Kecepatan Lokal (Build Debug Saja)
Data latensi **TIDAK pernah dikirim ke internet**. Untuk mengukur kecepatan nyata (*time-to-first-result* & *time-to-audio*) di HP fisik via ADB:
```bash
adb logcat -s MupaPerf:D
```
Contoh keluaran yang diharapkan saat menguji Cari, Onboarding, dan Putar lagu:
- `MupaPerf[first_result] provider=deezer label="search:blue" ms=280 cache=false`
- `MupaPerf[first_result] provider=swr_cache label="search:blue" ms=0 cache=true`
- `MupaPerf[time_to_audio] provider=innertube label="blue" ms=920 cache=false`

> **Catatan Kejujuran:** Seluruh metrik kecepatan nyata, kestabilan audio di jaringan seluler Indonesia, serta status geoblock/rate-limit provider di perangkat nyata bertanda **belum terverifikasi** sampai diukur langsung pada HP dengan perintah di atas.
