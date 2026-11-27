# Spesifikasi Ultra Dynamic (UD) — Audit `music-x-V8_5.html` & Rencana Padanan Native

> **Status:** Checkpoint **S0** (menunggu konfirmasi user sebelum implementasi S1).  
> **Sumber Kebenaran:** `music-x-V8_5.html` (Engine V15 — `DYNAMIC_THEME_ENGINE_VERSION = 15`).

---

## 1. Bedah Lengkap Ultra Dynamic di `music-x-V8_5.html`

### 1.1 Ekstraksi Warna dari Cover Art (`L11235–L11702`)
1. **Input & Sampling (`extractThemeAnalysis`, `L11532–L11588`):**
   - Memuat cover ke `<canvas>` **64×64** dengan *center-crop* **trim tepi 4%** (`trim = 0.04`) agar garis/bingkai hitam-putih thumbnail tidak merusak palet.
   - Timeout 4.500 ms; deduplikasi request paralel via `dynamicThemePending` + cache LRU maks **80** lagu (`DYNAMIC_THEME_CACHE_MAX = 80`) + cache persisten lead palette.
2. **Pre-bucketing & K-Means OKLab (`pickPaletteV13`, `L11400–L11530`):**
   - Setiap piksel opaque (`alpha >= 180`) dikonversi ke **OKLab** (`rgbToOklab`, `L11341–L11349`) dengan bobot spasial Gaussian tengah (`w = 0.60 + 0.70 * exp(-r2 * 7.5)`, fokus di tengah 1.30× dan tepi 0.60×).
   - Dimasukkan ke **histogram 3D OKLab 12×12×12**; bucket dengan bobot `< 0.08%` dibuang untuk menolak noise kompresi 1–2 piksel.
   - Menjalankan **weighted k-means `K = 12`** (12 iterasi) dengan inisialisasi *density-aware* (`md[k] * w^0.72`). Klaster dengan `share <= 0.4%` dibuang.
   - **Deteksi Cover Monokrom/Gelap Netral (`L11479–L11482`):** jika total `chromaticShare (C >= 0.032) < 0.035` atau `(avgL < 0.16 && avgS < 0.11 && darkRatio > 0.78)`, mengembalikan **`buildNeutralDarkPalette()`** (`#B2B2BC` perak/grafit netral — tidak menyuntik warna ungu).
3. **Pemilihan Peran Warna (`L11486–L11529`):**
   - **Primary (dominan):** dari klaster kromatik (`C >= 0.032`), skor = `share^0.64 * (0.20 + 1.55 * (C/0.16)^1.05) * visibilityWeight(L)`.
   - **Secondary / Tertiary / Quaternary (aksen kontras):** skor = `share^0.48 * (0.14 + 1.85 * (C/0.17)^1.18) * visibility * divBoost(hueGap vs Primary)`, disaring agar `oklabDist >= 0.068` dan tidak terlalu dekat hue (`hueGap < 15°` dengan `|ΔL| < 0.14`).
   - Bila klaster unik kurang dari 3 (atau 4 pada mode `4 Dynamic`), dilengkapi secara harmonis pada hue cover (`±20°`, `±36°`).
   - **Pengangkatan Aksen (`liftAccentV13`, `L11382–L11398`):** menaikkan `L` ke `[0.48..0.74]` (primary) / `[0.45..0.80]` (lainnya), chroma `0.085..0.27`, lalu gamut-clamp aman ke sRGB via *binary search* `oklchToRgbSafe` (menjaga hue & lightness asli).
4. **Penurunan Token Permukaan & Kaca (`deriveThemePalette` & `deriveGlassPalette`, `L11601–L11702`):**
   - Menghasilkan 30+ variabel CSS (`THEME_COLOR_VARS`, `L16030–L16039`): `--theme-primary/secondary/tertiary/quaternary`, `--theme-dark-primary/secondary/tertiary` (`L = 0.155 / 0.11 / 0.09`), `--theme-surface`, `--theme-surface-elevated`, `--theme-glow-rgb` (saturasi ×1.22), `--theme-text-accent`, serta token kaca (`--glass-tint-rgb`, `--glass-rim-rgb`, `--glass-edge-a/b/c-rgb`, `--glass-caustic-rgb`).
   - **Catatan Penting — jalur non-UD (`tintFor`, `L21506–L21520`):** Saat UD **mati**, HTML memiliki jalur aurora terpisah yang merata-rata 12×12 piksel berbobot `sat^2` — rata-rata RGB pada cover dua warna berlawanan (biru + pasir) menghasilkan warna keruh/abu. **Mesin UD V15 sendiri TIDAK merata-rata warna.**

### 1.2 Elemen Visual, Gerakan, Kecepatan & Pemicu di HTML
| Aspek | Perilaku di `music-x-V8_5.html` | Lokasi Baris |
|---|---|---|
| **Lapisan Gradien (Player / Cinema / Mini)** | 3 radial gradient berlapis (kiri-atas `primary` @ 12% 0%, kanan-bawah `secondary`/`quaternary` @ 92% 100%, tengah-atas `tertiary` @ 50% 36%) + linear chromatic blend 118° pada tier Ultra+/Max/X. Di atas `surface` gelap + `vignette` & `grain` 160×160px (`High+`). | `L2070–L2077`, `L2314–L2361`, `L2444–L2465` |
| **Lapisan Beranda / Halaman (`.mx-aurora`)** | 3 radial gradient di puncak halaman (tinggi `540px`): blob 1 kiri-atas (`12% 0%`, alpha `.56`), blob 2 kanan-atas (`98% 4%`, alpha `.44`), blob 3 tengah (`46% 40%`, alpha `.20`). Mengikuti palet UD lagu aktif (`dynRgb`). | `L5457–L5459`, `L21480–L21540` |
| **Elemen yang Bergerak & Kecepatan** | 1) `themeAmbientDrift`: animasi `scale(1 -> 1.018)` + `opacity(.82 -> .94)`, periode **11 dtk** *ease-in-out alternate* pada layer `::after` (aktif tier `High+`).<br>2) `auroraA` / `auroraB` (Overclock Cinema): 2 blob radial melayang perlahan, periode **16 dtk** & **21 dtk**.<br>3) `mxDrift` (Beranda): `translate3d(-4%, 2%, 0) scale(1.06)`, periode **20 dtk**. | `L2096–L2102`, `L2559–L2570`, `L5349` |
| **Pemicu Perubahan & Crossfade** | Dipicu saat `track-change` (`applyDynamicThemeToCurrentTrack`, `L16171`), ganti cover (`applyCoverBlob`), atau ubah pengaturan UD. Perubahan warna memakai crossfade opacity **340–420 ms** (`.mx-aurora-ghost` / `themeCrossfadeOut`, tanpa loop rAF JS per frame demi anti-kedip). | `L2106–L2121`, `L16063–L16083`, `L16137–L16162`, `L21530–L21536` |
| **Pengaturan & Tier Grafis** | Toggle master **Ultra Dynamic** (`dynamicThemeOn`), intensitas (`Lembut 0.7× / Seimbang 1× / Pekat 1.4×`), sub-toggle Cinema/Mini/Daftar, `4 Dynamic` (4 warna), pratinjau 4 swatch warna. Tier `Low` = opacity `.58` statis tanpa grain/drift; `120S+` = matikan drift & crossfade overlay; **Mode OLED** = matikan total ke `#000000`; `prefers-reduced-motion` = statis. | `L2080–L2084`, `L2268–L2279`, `L2448`, `L4768–L4904`, `L8781–L8849` |
| **Layar Tempat UD Tampil** | **Beranda** (`.mx-aurora` + kartu Hero `.mx-now` + penanda lagu aktif), **Mini Player** (tint sudut & border/progress), **Full / Cinema Player** (latar 3–4 radial gradient + glow cover + aksen tombol/seekbar), **Pustaka / Cari / Akun** (aurora puncak halaman saat terbuka). | `L2154–L2194`, `L2314–L2390`, `L5457–L5501`, `L21541–L21560` |

---

## 2. Diagnosis Jujur Penyebab Latar Abu-Abu Kecokelatan di Now Playing Mupa Saat Ini (`N1`)

**Temuan penting:** Latar abu-abu kecokelatan di Now Playing Mupa saat ini **BUKAN** disebabkan oleh algoritma UD V15 di HTML (HTML V15 sudah memakai OKLab k-means). Penyebab sebenarnya di kode native Mupa sekarang ada dua:
1. **Belum ada ekstraktor palet bitmap sama sekali:** `MupaPlayerUiState.coverAccent` (`MupaPlayerUiState.kt:64–75`) hanya membaca `currentTrack?.accentColorArgb` yang selalu `null` untuk semua lagu streaming → jatuh ke default koral/amber.
2. **Blur 2-pass pada bitmap cover kecil (`MupaCinemaOverlay.kt:116–124`):** `FullBleedBlurredCoverBackdrop` memuat cover `128×128` lalu menjalankan `fastBoxBlurOnce(radius = 10)` **2 kali** di atas latar `Color(0xFF1D1820)` dan scrim hitam `46%–80%`. Pada cover `"blue"` (yung kai) yang setengah atasnya biru langit/laut dan setengah bawahnya pasir krem kekuningan, *box blur* besar + scrim gelap **mencampur rata-rata biru + krem menjadi abu-abu kecokelatan keruh**!

---

## 3. Usulan Padanan Native Mupa (Menunggu Konfirmasi User di Gerbang S0)

Kami mengusulkan menggabungkan karakter visual **Ultra Dynamic HTML V15** dengan penyederhanaan & pengamanan performa native sesuai spesifikasi Fase 2S Bagian A:

| Aspek | Usulan Implementasi Native Mupa (`:core:designsystem` + `:app`) |
|---|---|
| **Ekstraksi Palet (Background `Dispatchers.Default`, Cache LRU 64 per `trackId`)** | Downscale **64×64** (center-crop trim tepi 4% seperti HTML V15) → konversi **OKLab** → **k-means `k = 5`** (atau histogram cepat + `k = 5` untuk stabilitas) → buang klaster hampir abu/hitam/putih (`chroma < 0.04`, `L < 0.12`, atau `L > 0.95`) kecuali seluruh cover memang monokrom → pilih **3 warna** berdasarkan **`populasi × chroma`** dengan **jarak hue minimal ≥ 25°**. |
| **Token Warna Hasil** | - `bgDeep`: `L 0.20–0.28`, `chroma ≤ 0.07` (menjamin kontras teks utama `#F6F4EF` **≥ 4.5:1**, faktanya > 10:1)<br>- `glow1`, `glow2`, `glow3`: `L 0.42–0.60`, `chroma 0.10–0.16` (biru + pasir tetap tampil sebagai **biru cerah + pasir hangat**, tidak pernah bercampur jadi abu!)<br>- `onAccent`: `L ≥ 0.87` (untuk teks sorotan / wipe lirik)<br>- **Fallback Cover Monokrom/Abu:** koral `#FF5A3C` + amber `#FFB067` khas merek Mupa (Opsi A Ember Editorial). |
| **Renderer (1 Canvas `drawWithCache`, 0 Blur Layar Penuh)** | Satu `Canvas` memakai `drawWithCache` menggambar **3 radial gradient besar** (`glow1` kiri-atas, `glow2` kanan-bawah, `glow3` tengah-atas — persis komposisi HTML V15) di atas `bgDeep`, ditambah **grain statis 3–4%** (tile kecil anti-banding), **TANPA** `RenderEffect`/blur bitmap layar penuh. |
| **Gerakan & Crossfade** | - **Drift:** satu `rememberInfiniteTransition` berperiode sangat pelan (**48 detik**, rentang 40–60 dtk), amplitudo kecil, dibaca **hanya di fase draw** (0 rekomposisi). Dijeda otomatis saat app tidak terlihat / layar mati (`Lifecycle.State.STARTED`).<br>- **Crossfade warna ganti lagu:** **600 ms** *tween* tanpa overshoot, dibaca di fase draw. |
| **Layar Penerapan** | **Hero Beranda** (ikut halaman carousel aktif), **Now Playing penuh** (edge-to-edge sampai tepi atas di belakang status bar), **Cinema**, dan **tint tipis di Mini Player**. |
| **Hemat Daya & Pengaturan** | - Menggantikan switch `"Aurora Latar"` → menjadi **1 switch `"Ultra Dynamic"` (default ON)**; `MupaAuroraBackground` lama dihapus.<br>- **Mode OLED:** hitam murni `#000000`, tanpa drift.<br>- **Mode Performa:** 1 gradient statis tanpa drift & tanpa grain.<br>- **Animator Duration Scale = 0 (sistem):** statis tanpa drift. |

### Pertanyaan Konfirmasi S0 untuk User
1. Apakah spesifikasi **Ultra Dynamic** di atas (komposisi 3 radial gradient + grain dari HTML V15, memakai ekstraksi **OKLab k-means `k=5`** dengan token `bgDeep`/`glow1`/`glow2`/`onAccent`, fallback monokrom ke koral/amber Mupa, drift 48 dtk, crossfade 600 ms, tanpa blur bitmap yang bikin warna keruh) sudah cocok dengan Ultra Dynamic yang kamu maksud?
2. Untuk cover **monokrom / hitam-putih**: di HTML V15 ia jatuh ke **abu-abu/perak netral**, sedangkan di spesifikasi Fase 2S A diminta jatuh ke **koral/amber merek Mupa** — kami set default mengikuti Fase 2S (**koral/amber Mupa**). Apakah sudah sesuai?


---

## 4. Perbandingan HTML V15 vs Native Mupa (Disetujui S0 → S1)

| Aspek | HTML V15 (`music-x-V8_5.html`) | Padanan Native Mupa (`UltraDynamicEngine` + `UltraDynamicBackground`) | Status / Alasan Penyimpangan |
|---|---|---|---|
| **Sampling Cover** | Canvas 64×64, *center-crop* trim tepi 4%, bobot Gaussian tengah `0.60 + 0.70·exp(-7.5r²)`. | Identik: downscale 64×64, trim tepi 4%, bobot Gaussian tengah `0.60 + 0.70·exp(-7.5r²)`. | **Identik** |
| **Clustering OKLab** | Histogram 3D 12×12×12 + weighted k-means (`K=12`, 12 iterasi), buang klaster `C < 0.032`. | Histogram 3D 12×12×12 + weighted k-means (`K=5`, 12 iterasi) di `Dispatchers.Default`, buang klaster `C < 0.04` / `L < 0.12` / `L > 0.95`, jarak hue min **≥ 25°**. | **Disetujui:** `K=5` + filter `C/L` + `hueGap ≥ 25°` tajam memisahkan 2–3 warna kontras (biru+pasir) & hemat CPU mobile. |
| **Fallback Monokrom vs Tanpa Cover** | Monokrom → `buildNeutralDarkPalette()` (abu/perak netral). | **Monokrom/B&W → abu/perak netral (`MonochromeNeutral`, identik HTML V15).** **Tanpa cover sama sekali** (placeholder/loading/lokal tanpa artwork) → **koral/amber Mupa (`BrandNoCover`)**. | **Identik aturan user #2** |
| **Jumlah & Posisi Blob** | 3 radial gradient (kiri-atas `11% 0%`, kanan-bawah `93% 100%`, tengah-atas `50% 34%`) + vignette + grain 160px. | 1 `Canvas` (`drawWithCache`) dengan 3 radial gradient identik (`11% 4%`, `92% 92%`, `50% 34%`) + vignette tepi + grain statis 3,6% (tile 64×64). **Nol blur bitmap/RenderEffect layar penuh.** | **Identik rasa visual**, tanpa blur mahal di GPU mobile. |
| **Kecepatan Drift** | `themeAmbientDrift` 11 dtk, `auroraA`/`auroraB` 16–21 dtk, `mxDrift` 20 dtk (*ease-in-out alternate*). | 1 `rememberInfiniteTransition` **18 detik** (*EaseInOutSine Reverse*) dibaca hanya di fase draw, jeda otomatis saat lifecycle `< STARTED`, OLED, Performa, atau `animator_duration_scale = 0`. | **Mengikuti HTML V15 (16–21 dtk)**; 1 transisi agar hemat baterai. |
| **Crossfade Ganti Lagu** | Crossfade opacity 340–420 ms tanpa loop JS berat. | Crossfade warna **600 ms** *tween* dibaca di fase draw (nol rekomposisi, nol overshoot). | **Halus & stabil di 60/120 Hz** |
| **Pengaturan** | Toggle `Ultra Dynamic` + sub-toggle + 7 tier grafis. | **1 switch `Ultra Dynamic` (default ON)** menggantikan `Aurora Latar`, plus penghormatan otomatis ke **Mode OLED**, **Mode Performa**, dan **Reduced Motion**. | **Disederhanakan** sesuai aturan Mupa (tanpa 7 tier grafis rumit). |
| **Cache** | LRU 80 lagu. | Thread-safe `LruCache` **64 lagu** per `trackId` di `Dispatchers.Default`. | **Hemat RAM mobile** |
