# Audit Teknis Tahap 0 — Mochy & Music X V8.5

> **Status:** Read-only audit selesai (belum ada kode proyek Mupa yang dibuat/diubah).  
> **Sumber yang diaudit:**
> - `Mochy/Mochy-Music-main/` (referensi pola arsitektur streaming & mekanisme animasi)
> - `music-x-V8_5.html` (23.232 baris, ~1,77 MB; referensi fitur & perilaku versi web lama)
> - `assets/logo/` & `assets/android-res/` (diverifikasi detail di `docs/BRAND-MAP.md`)

---

## 1. Audit Lisensi (Mochy & Seluruh Dependensinya)

### 1.1 Lisensi Repositori Mochy
- **`Mochy/Mochy-Music-main/LICENSE`:** **GNU General Public License v3.0 (GPL-3.0)** — strong copyleft.
- **`Mochy/Mochy-Music-main/README.md` & `AGENTS.md`:** Menegaskan bahwa Mochy adalah turunan dari ViviMusic/Metrolist berlisensi GPL-3.0.
- **Konsekuensi untuk Mupa (Aturan Kerja #4):**
  - **DILARANG menyalin kode Mochy mentah-mentah** (baik UI, animasi, playback, maupun provider).
  - Seluruh implementasi Mupa harus ditulis ulang secara **clean-room** (dari nol) berdasarkan pemahaman pola arsitektur dan mekanisme yang didokumentasikan pada audit ini.
  - Nol aset brand, logo, nama, ikon, string, atau package Mochy dibawa ke Mupa.

### 1.2 Klasifikasi Lisensi Dependensi Mochy (`gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/cpp/third_party/NOTICE.md`)

| Kategori Lisensi | Dependensi di Mochy | Lokasi di Mochy | Implikasi bagi Mupa |
|---|---|---|---|
| **Strong Copyleft (GPL-3.0 / GPL-2.0+)** | `com.github.TeamNewPipe:NewPipeExtractor:v0.25.2` | `:providers:innertube` | **Menular (copyleft).** Jika ditautkan di Tahap 2, Mupa wajib dirilis di bawah GPL-3.0 (kode sumber terbuka penuh). |
| **Strong Copyleft (GPL-3.0)** | `com.github.MetrolistGroup.innertubex:innertubex:v0.11.6` | `:app` | **Menular (copyleft).** Sama: menautkan library ini mewajibkan Mupa berlisensi GPL-3.0. |
| **Strong Copyleft (GPL-3.0)** | `org.jellyfin.media3:media3-ffmpeg-decoder:1.6.1+2` | `:app` | **Menular (copyleft).** Dibutuhkan hanya jika ingin software decoder FFmpeg (mis. ALAC). Jika Mupa tidak GPL, gunakan decoder bawaan Android (`MediaCodec`) atau build decoder permisif / Rust symphonia di local mode. |
| **Strong Copyleft (GPL-2.0+ / GPL-3.0)** | `third_party/jamesdsp_eel2` (`libmochyfx.so`) | `app/src/main/cpp/` | **Tidak dibutuhkan Mupa** (Mupa memakai EQ 5-band ringan). |
| **Weak Copyleft (LGPL-2.1)** | `third_party/libusb` (`libmochypass.so`, static link) | `app/src/main/cpp/` | **Tidak dibutuhkan Mupa** (driver USB DAC eksklusif di luar lingkup). |
| **GPL-2.0 + Classpath Exception** | `com.android.tools:desugar_jdk_libs_nio:2.1.5` | `:app`, `:providers:*` | **Aman** untuk proyek non-GPL maupun GPL berkat Classpath Exception. |
| **Permisif (Apache-2.0)** | AndroidX (`compose`, `material3`, `lifecycle`, `activity`, `appcompat`, `room`, `datastore`, `work`, `webkit`, `palette`, `core-splashscreen`, `media3-exoplayer`, `media3-session`, `media3-hls`, `media3-dash`, `media3-okhttp`), Dagger/Hilt, Guava, Kotlin/Coroutines/Serialization, Ktor 3.6, OkHttp, Coil 3, Timber, `compose-reorderable`, `compose-shimmer`, `kuromoji-ipadic`, `tinypinyin` | `:app`, `:providers:*`, `:modules:*` | **Aman dipakai** di Mupa tanpa kewajiban copyleft (cukup sertakan atribusi Apache-2.0). |
| **Permisif (MIT / BSD-3 / EPL-1.0)** | `material-kolor` (MIT), `smooth-corner-rect-android-compose` (MIT), `protobuf-javalite`/`kotlin-lite` (BSD-3), `junit` (EPL-1.0, test-only), `progenitor` (MIT), `brotli` (MIT) | `:app` | **Aman dipakai** sesuai kebutuhan (dengan atribusi MIT/BSD). |
| **Proprietary** | `play-services-cast-framework` (hanya flavor `gms`) | `:app` (`gmsImplementation`) | Tidak diperlukan pada tahap awal Mupa. |

> **Ringkasan Lisensi:** Di **Tahap 1 (UI/UX)**, Mupa hanya menggunakan dependensi permisif (Apache-2.0/MIT) dan 100% kode tulis-sendiri, sehingga **bebas kewajiban GPL**. Keputusan besar ada di **Tahap 2 (Streaming)**: apakah Mupa ingin tetap bebas GPL (menulis klien InnerTube + cipher/n-param solver sendiri secara clean-room) atau bersedia berlisensi GPL-3.0 jika memakai `NewPipeExtractor`/`innertubex` (dicatat di `docs/QUESTIONS.md`).

---

## 2. Peta Arsitektur Mochy

### 2.1 Struktur Modul Gradle (`settings.gradle.kts:20-43`)
Mochy terdiri dari **22 subproject Gradle** (AGP `9.0.0`, Kotlin `2.3.10`, JVM 21, `minSdk = 24`, `compileSdk = 37`, `targetSdk = 37`):
- **`:app`**: aplikasi utama + 3 library native CMake (`libmochypass.so`, `libmochystage.so`, `libmochyfx.so`). Memiliki 5 flavor ABI (`universal`, `arm64`, `armeabi`, `x86`, `x86_64`) × 3 varian (`foss`, `fdroid`, `gms`) serta 4 build type (`debug`, `release`, `profileable`, `fast`).
- **16 modul `:providers:*`**:
  - Streaming/Katalog: `innertube` (106 file `.kt`, Android lib)
  - Lirik: `betterlyrics`, `youlyplus`, `paxsenixlyrics`, `simpmusic`, `lrclib`, `kugou`, `directlyrics` (berisi 5 layanan: Unison, LrcRed, Deezer, NetEase, QQMusic)
  - Canvas/Video: `canvas`, `applecanvas`, `artistvideo`
  - Metadata/Editorial: `musixmatch` (fakta/makna lagu), `genius`, `wikipedia`
  - Scrobble & Pengenalan: `lastfm`, `shazamkit`
- **5 modul `:modules:*` (Pure Kotlin JVM, tanpa dependensi Android/Media3):**
  - `api` (kontrak `MochyModule`/`SourceModule` API v3), `bridge` (jembatan isolasi classloader + JSON serialization), serta 3 sumber audio bawaan: `monochrome`, `jiosaavn`, `applemusic`.

### 2.2 Layer, DI, Navigasi & State Management
- **Layering:** Mochy **tidak memiliki layer Domain/Repository formal**.
  - **Yang bersih:** `:modules:api` + 3 modul sumber bawaan + `:providers:*` (kecuali `:providers:artistvideo` yang mencampur `@Composable` & `ExoPlayer` di dalam modul provider).
  - **Yang bocor/terikat erat:** `DatabaseDao.kt` (2.235 baris) mengimpor DTO InnerTube (`SongItem`, `AlbumPage`, `ArtistPage`) dan helper UI; `MusicService.kt` (6.181 baris) adalah monolit yang mengurus playback, stream resolution, DSP, crossmix, lirik, scrobble, Discord, Cast, widget, dan persistensi antrean; `MainActivity.kt` (1.767 baris) mencampur binding service, pemanggilan `YouTube.queue(...)`, ekstraksi warna cover, system bar, dan root UI.
- **Dependency Injection (Hilt `2.60.1`):**
  - `@HiltAndroidApp App` + `@AndroidEntryPoint MainActivity`.
  - `di/AppModule.kt` menyediakan `@ApplicationScope`, `InternalDatabase` (Room v47, 25 entitas, 3 view, WAL mode, `busy_timeout=60000`, `synchronous=NORMAL`), `MusicDatabase`, `@PlayerCache SimpleCache` (LRU 1024 MB default), `@DownloadCache SimpleCache` (`NoOpCacheEvictor`).
  - `di/NetworkModule.kt` menyediakan `NetworkConnectivityObserver`.
  - 7 `@EntryPoint` (`SingletonComponent`) menjembatani singleton Hilt ke Composable sheet/menu dan worker.
- **Navigasi (`ui/screens/Screens.kt`, `NavigationBuilder.kt`):**
  - 4 tab utama: `Home ("home")`, `Search ("search_input")`, `Library ("library")`, `Settings ("settings"` → `ProfileScreen`).
  - 1 `NavHost` Compose berisi ~60 route dibungkus `SharedTransitionLayout` + `ModuleTakeover`.
- **State Management:**
  - 54 `@HiltViewModel` mengekspos `StateFlow`.
  - **DataStore (`utils/DataStore.kt`):** dibungkus `SecretPreferences` + `PreferencesCache` in-memory `mutableStateOf` yang dikoleksi sekali sejak `App.onCreate`, lalu dibaca per-key via `rememberPreference()` berbasis `derivedStateOf` agar perubahan 1 key tidak me-recompose semua pembaca.
  - **CompositionLocals (`MainActivity.kt:918-937`):** ~20 local provider (`LocalDatabase`, `LocalPlayerConnection`, `LocalPlayerBottomSheetState`, `LocalGlassState`, `LocalArtworkColor`, `LocalBlurQuality`, `LocalPlayerAwareWindowInsets`, dll.).

### 2.3 Background Playback, Media3/ExoPlayer, MediaSession & Notifikasi
- **`MusicService` (`playback/MusicService.kt`)** mewarisi `MediaLibraryService` (Media3 `1.7.1`):
  - **ExoPlayer:** `DefaultLoadControl` (min 15s, max 25s, playback 1,5s, rebuffer 3s, target 6 MB, `prioritizeTimeOverSizeThresholds = true`), `WAKE_MODE_LOCAL` (bukan `WAKE_MODE_NETWORK`), `setHandleAudioBecomingNoisy(true)`, `experimentalSetDynamicSchedulingEnabled(true)`, `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC)`, manajemen **AudioFocus manual** (termasuk opsi mix-with-other-apps + `CallWatcher`).
  - **Renderers:** `MediaCodecAudioRenderer` kustom (`AudioDecoderPolicy` memfilter decoder vendor OEM bermasalah untuk FLAC/ALAC) + fallback `FfmpegFallbackRenderer` (`org.jellyfin.media3:media3-ffmpeg-decoder`, GPL-3.0).
  - **AudioSink & Processor Chain:** 10 audio processor berurutan (Parametric EQ → Mixer/Liveprog → 3D Stage → Spatial → Reverb → Loudness → 8D → SilenceDetector → Haptic → CrossmixTransition) + `SilenceSkippingAudioProcessor` + `SonicAudioProcessor`. Audio Offload otomatis dimatikan saat DSP/EQ/crossmix aktif.
  - **Normalisasi:** Sengaja **tidak memakai** Android `LoudnessEnhancer` (bug balance audio Android 11); memakai attenuation + limiter in-app.
  - **MediaSession & Notifikasi:** Player dibungkus `OriginCardPlayer(CarLyricsPlayer(TransportFadingPlayer(player)))`, dipasangkan dengan `MediaLibrarySession` + `MediaLibrarySessionCallback` (custom commands Like/Repeat/Shuffle/Radio, browse tree, voice search) + `CacheBitmapLoader(CoilBitmapLoader)`. Saat `onCreate()` langsung memanggil `startForeground(...)` dengan channel `IMPORTANCE_LOW` lalu memasang `DefaultMediaNotificationProvider` (`setSmallIcon`).
  - **Lifecycle & Persistensi:** `MainActivity` mengikat via `BIND_AUTO_CREATE` di `onStart`/`onStop` melalui `PlayerConnection` (polling progress ~15 Hz hanya saat `isUiVisible` dan sedang bermain). Antrean disimpan via `ObjectOutputStream` setiap 30 detik hanya saat bermain dan di `onDestroy`. `onTaskRemoved` mempertahankan playback di background secara default.
  - **CI GitHub Actions Mochy (`.github/workflows/`):** `build.yml` & `build_pr.yml` berjalan di `ubuntu-latest` + JDK 21 Temurin + `setup-gradle`, membuat ephemeral `debug.keystore`, lalu menjalankan `assemble...Debug` & `lint...Debug` dan mengunggah APK sebagai artifact.

---

## 3. Audit Lapisan Streaming & Penyedia Mochy

### 3.1 Inventaris Lengkap Semua Penyedia & Sumber

| Kategori | Penyedia / Modul | Endpoint / Sumber Utama | Auth | Peran |
|---|---|---|---|---|
| **Audio Streaming (Core)** | `providers/innertube` + `innertubex` + `NewPipeExtractor` | `music.youtube.com/youtubei/v1/`, `www.youtube.com/youtubei/v1/`, `sw.js_data` | Opsional Cookie (`SAPISIDHASH`), `X-Goog-Visitor-Id`, `dataSyncId`, PoToken | Katalog utama, Beranda, Cari, Artis, Album, Playlist, Radio/Queue, Stream audio/video, Captions |
| **Audio Streaming (Modul Bawaan)** | `modules/monochrome` | `https://tracks.monochrome.st` (`/search/tracks`, `/track/{id}`) + relay/custom server | Tidak ada | FLAC Lossless / Hi-Res progresif (validasi header `fLaC` via byte-range 0–41) |
| **Audio Streaming (Modul Bawaan)** | `modules/jiosaavn` | `https://www.jiosaavn.com/api.php` (`search.getResults`, `api_version=4`) | Cookie statis `explicit_content=1` | AAC 320/160/96 kbps (dekripsi `encrypted_media_url` DES/ECB kunci hardcoded `"38346591"`) |
| **Audio Streaming (Modul Bawaan)** | `modules/applemusic` | `amp-api.music.apple.com`, `play.itunes.apple.com` (`webPlayback`, `acquireWebPlaybackLicense`) | Langganan user via WebView (`Bearer JWT` + `Media-User-Token`) | AAC 256 / HE-AAC 64 via HLS + Widevine CDM (`AppleHls` rewrite ke `SAMPLE-AES-CTR`, tanpa lossless/download) |
| **Audio/Skin Dinamis** | `modules/web`, `modules/code`, `modules/local` | `{base}/manifest.json`, `mochy-module-feed.mochyy.workers.dev` | Manifest / Discord OAuth + HMAC native | Sumber audio HTTP/JSON, lirik, metadata, skin ZIP, atau APK tertandatangani via `:modules:bridge` |
| **Lirik (14 Penyedia)** | `betterlyrics` (`lyrics-api.boidu.dev`), `youlyplus` (`lyricsplus.prjktla.my.id`, `lyricsplus.binimum.org`), `paxsenixlyrics` (`lyrics.paxsenix.org`), `simpmusic` (`api-lyrics.simpmusic.org`, fallback Render), `lrclib` (`lrclib.net`), `kugou`, `directlyrics` (**Unison** `unison.betterlyrics.org`, **LrcRed** `lrc.red`, **Deezer** GraphQL, **NetEase** `music.163.com`, **QQMusic** `u.y.qq.com`), + YouTube Subtitle / Music / Captions + `EmbeddedLyrics` | Lihat kolom kiri | Tidak ada (kecuali Deezer anon JWT & sesi YT) | `LyricsHelper` memanggil penyedia secara paralel (timeout 35s), memberi skor kecocokan aksara, timing kata/suku kata, dan fallback ke YouTube Captions |
| **Canvas / Video** | `providers/canvas` (Apple Artist BG, Monochrome Tidal MP4, `artwork.boidu.dev`), `providers/applecanvas`, `providers/artistvideo` (`artwork-archivetune.koiiverse.cloud`), `app/.../canvas/SpotifyCanvasProvider` | Apple AMP API, `*.monochrome.tf`, `open.spotify.com` + `spclient.wg.spotify.com` | Scraped Apple JWT / cookie `sp_dc` Spotify | Latar/cover bergerak (HLS/MP4) |
| **Metadata / Fakta** | `providers/musixmatch`, `providers/genius`, `providers/wikipedia` | Musixmatch API + WebView WAF/DDG DoH, Genius API, Wikidata/Wikipedia | Auto `user_token` (Musixmatch) | Deskripsi lagu, kredit penulis/produser, biografi artis |
| **Translasi Lirik** | `GoogleTranslateEngine`, `DeepLService`, `MistralService`, `OpenRouterService` | `translate.googleapis.com`, DeepL, Mistral, OpenRouter | API key user (kecuali Google Translate) | Terjemahan baris lirik |
| **Scrobble, Recognition & Importer** | `providers/lastfm` (Last.fm & Libre.fm), `providers/shazamkit` (`amp.shazam.com`), `importer/*` (Spotify, Apple Music, Tidal, YTM, Deezer, SoundCloud, CSV, `DeviceMusicScanner`) | API/WebView masing-masing layanan | Akun user / API key | Scrobble, deteksi audio 10 detik, impor playlist/pustaka |

### 3.2 Klien InnerTube (`providers/innertube`)
- **Base URL:** `https://music.youtube.com/youtubei/v1/` (default) dan `https://www.youtube.com/youtubei/v1/` untuk `/player` & `/visitor_id` klien tertentu.
- **18 Preset `YouTubeClient` (`YouTubeClient.kt:66-303`):** `WEB`, `WEB_REMIX` (`67`, login+sigTs+poToken), `WEB_CREATOR` (`62`), `TVHTML5` (`7`), `TVHTML5_SAMSUNG`, `TVHTML5_DOWNGRADED`, `WEB_EMBEDDED_PLAYER` (`56`), `TVHTML5_SIMPLY_EMBEDDED_PLAYER` (`85`), `IOS` (`5`), `IPADOS`, `MOBILE` (`ANDROID`/`3`), `ANDROID_NO_SDK`, 4 varian `ANDROID_VR` (`28`: `NO_AUTH`, `1.61.48`, `1.65.10`, `1.43.32`), `ANDROID_CREATOR` (`14`), dan **`VISIONOS` (`101`, `apiFormatVersion=2`, `ownVisitorId=true`, `isNativeApp=true`)**.
- **Autentikasi & Header:** `X-YouTube-Client-Name`, `X-YouTube-Client-Version`, `X-Goog-Visitor-Id` (dari `sw.js_data` atau `/visitor_id` khusus `VISIONOS`), `cookie`, `Authorization: SAPISIDHASH <ts>_<sha1(ts sapisid origin)>`, `Context.User.onBehalfOfUser` (`dataSyncId`), `serviceIntegrityDimensions.poToken`, `signatureTimestamp`, locale `hl`/`gl`.
- **Parsing Respons:** 23 parser di `providers/innertube/pages/*` (`HomePage`, `SearchPage`, `ArtistPage`, `AlbumPage`, `PlaylistPage`, `NextPage`, `RelatedPage`, `ChartsPage`, dll.) memetakan JSON InnerTube ke hierarki `YTItem` (`SongItem`, `AlbumItem`, `PlaylistItem`, `ArtistItem`).

### 3.3 Resolusi URL Stream, Signature/n-param, PoToken & SABR (`YTPlayerUtils.kt`, `InnerTubeXStreams.kt`)
1. **Path Primer — `InnerTubeX`** (`StreamBackend.INNERTUBEX`, default, timeout 12s): menggunakan library `com.metrolist.innertubex.InnerTubeExtractor` dengan JS cipher bawaan dan `TokenProvider` yang meneruskan ke `PoTokenGenerator`. Secara eksplisit mematikan HLS, SABR, dan bounded-range (`allowHls = false, allowSabr = false, allowBoundedRange = false`).
2. **Path Sekunder — Classic Client Walk (`YTPlayerUtils.kt:456-910`):**
   - Klien utama: **`VISIONOS`** (tanpa PoToken, URL langsung tanpa cipher melewati batas ~786 KB; cooldown 30 menit bila terkena bot-check dua kali).
   - Metadata/Premium: **`WEB_REMIX`** (dengan PoToken + `signatureTimestamp`) untuk mengambil `audioConfig` (loudness) & cek akun Premium (256 kbps `itag` 141/774/dst.).
   - Age-restricted: `WEB_CREATOR`.
   - Urutan fallback: `VISIONOS` → `ACCOUNT_CLIENTS` (`TVHTML5_SAMSUNG`, `WEB_EMBEDDED_PLAYER`, `TVHTML5_DOWNGRADED`) → `STREAM_FALLBACK_CLIENTS` (`WEB_REMIX`, `WEB`, `WEB_CREATOR`, `IPADOS`, 4× `ANDROID_VR`, `TVHTML5*`, `ANDROID_CREATOR`, `MOBILE`).
3. **Dekripsi `signatureCipher`:** URL langsung → `CipherDeobfuscator` (download `base.js` via `iframe_api`, cache 6 jam, injeksi ke `CipherWebView` tersembunyi) → `NewPipeExtractor.getStreamUrl` → `StreamInfo.getInfo`.
4. **Transformasi `n`-param:** Primer `EjsNTransformSolver` (memuat bundler AST yt-dlp `meriyah.js` + `astring.js` + `yt.solver.core.js` di WebView tersembunyi) → fallback `CipherDeobfuscator.transformNParamInUrl`.
5. **PoToken (`utils/potoken/`):** `PoTokenWebView` memuat `assets/po_token.html`, memanggil `POST /api/jnn/v1/Create` & `/GenerateIT` via OkHttp dengan kunci BotGuard hardcoded, mendekripsi tantangan `(byte + 97)`, men-cache token sesi sampai `exp - 10m`, dan mencetak token per-video (`&pot=`).
6. **Validasi & Stand-in:** Range GET 1 byte di akhir stream / offset 1 MiB (`validateStatus`), opsi tukar ke versi audio studio (`studioVersionOf`), fallback upload alternatif (`SongVersions.otherUploads`), serta rotasi `visitorData` tamu (`BotDetectionMitigator`).
7. **Status SABR:** Meskipun ada package `utils/sabr/`, **Mochy TIDAK menjalankan pemutaran SABR**. Package tersebut hanya berisi `EjsNTransformSolver.kt` dan `SabrException.kt`.

### 3.4 Fallback Lintas Penyedia, Retry, Rate-Limit, Cache & Queue
- **Resolusi 2 Lapis (`MusicService.kt`):**
  - **Lapis 1 (`DynamicResolvingMediaSource`):** Cek file lokal / `downloadCache` → `SourceModules.route()` menjalankan semua modul aktif sebelum YouTube (`monochrome`, `applemusic`, `jiosaavn`, web/code) **secara paralel** (budget 12s), memilih pemenang sesuai prioritas + cek dukungan decoder + validasi URL `ModuleUrlGuard`, lalu membatalkan sisanya. Jika tak ada pemenang → delegasi ke Lapis 2 (YouTube).
  - **Lapis 2 (`ResolvingDataSource`):** `LocalBypass` → `downloadCache` (RO) → `playerCache` (RW, LRU 1 GB) → `songUrlCache` (in-memory, chunk 512 KB) → `YTPlayerUtils.playerResponseForPlayback(...)` → `OkHttpDataSource`.
- **Retry & Fallback Error:** Kegagalan modul non-YouTube langsung mem-blacklist sumber tersebut untuk lagu itu selama 10 menit (`failedSources`) dan memuat ulang dari sumber berikutnya hingga YouTube. Per lagu ada `MAX_RETRY_PER_SONG = 3` (jeda 1s) dan circuit breaker `MAX_CONSECUTIVE_ERR = 5`.
- **Rate-Limit:** `ModuleUrlGuard` membatasi 60 req/10s per modul; `Shazam` punya backoff 429 (2s/4s/8s); **`InnerTube` tidak punya retry/backoff HTTP 429/5xx** (hanya retry 3× pada `IOException`).
- **Queue (`playback/queues/`):** `EmptyQueue`, `ListQueue`, `YouTubeQueue` (radio `RDAMVM<id>` + fallback `related`/`artist`), `YouTubePlaylistQueue`, `YouTubeAlbumRadio`, `LocalAlbumRadio`, plus *infinite runway* menjaga minimal 5 lagu di depan.

### 3.5 Bagian Rapuh (Fragile Parts) yang Teridentifikasi
1. **Ketergantungan Anti-Bot YouTube:** Kunci BotGuard hardcoded di `PoTokenWebView.kt:311-312`, WebView cold start hingga 45 detik, dan ketergantungan klien utama pada `VISIONOS` tanpa PoToken yang sewaktu-waktu bisa dibatasi YouTube seperti `IOS`/`ANDROID_VR`.
2. **Scraping JS/HTML & Solver Statis:** Regex ekstraksi `base.js` (`FunctionNameExtractor`), aset statis `yt.solver.core.js` yang rusak jika AST YouTube berubah, 4× duplikasi scraper JWT Apple Music (salah satunya dengan fallback JWT kadaluarsa), kunci DES/ECB hardcoded `"38346591"` pada JioSaavn, WebView monkey-patch & GraphQL hash hardcoded pada Spotify Canvas, serta banyak non-null assertion `!!` pada parser InnerTube `YouTube.kt`.
3. **Host Pihak Ketiga Tidak Stabil:** `tracks.monochrome.st`, `*.monochrome.tf`, `lyrics-api.boidu.dev`, `lyricsplus.*` (4 mirror lama sudah mati), `api-lyrics.simpmusic.org`, `lyrics.paxsenix.org`, `lrc.red`, `unison.betterlyrics.org` bisa down atau berubah kapan saja.
4. **Bug Konkurensi & Jaringan di Mochy (harus dihindari di Mupa):**
   - **Data race pada `songUrlCache`** (`MusicService.kt:860`): `HashMap` biasa diakses lintas thread ExoPlayer IO dan Main tanpa sinkronisasi konsisten.
   - **`forceRefreshForVideo()` adalah no-op** (`YTPlayerUtils.kt:1320-1322`): dipanggil saat retry namun tidak menghapus cache apa pun.
   - **Proxy/Offline bypass:** Sebagian besar modul provider (`lrclib`, `kugou`, `betterlyrics`, `directlyrics`, bahkan `ModuleRegistry`) mengabaikan `AppProxy` dan status offline.
   - **Global mutable singleton `YouTube.*`:** `cookie`, `visitorData`, `locale` dimutasi bebas dari berbagai thread/layar.
   - **`runBlocking` di thread loader** (`MusicService.kt:4187,4319`, `DownloadUtil.kt:168`).

### 3.6 Evaluasi Pemisahan Lapisan Streaming dari UI di Mochy
**Kesimpulan: SANGAT TERIKAT (Heavily Coupled) — BUKAN contoh pemisahan UI yang boleh ditiru untuk Mupa.**
- **63 dari 658 file** di `app/.../ui/` langsung mengimpor `com.music.innertube.*`; **50 file UI** mengikat langsung DTO InnerTube (`YTItem`, `SongItem`, `ArtistItem`); **36 file UI** memanggil langsung singleton `YouTube.*` (mis. `ArtistScreen.kt` 51 ref, `OnlineSearchResult.kt` 44 ref, `HomeScreen.kt` 38 ref).
- **23 dari 51 file** di `viewmodels/` mengimpor `com.music.innertube.*` dan 18 memanggil langsung `YouTube.*`.
- Bahkan ada **coupling terbalik**: `providers/artistvideo` mengandung `@Composable` + `ExoPlayer`; `MusicService.kt:4457` memanggil `ui.component.TopToaster.show(...)`.
- **Pelajaran untuk Mupa:** Ambil pola positif dari `:modules:api` (kontrak pure-Kotlin tanpa Android/UI) dan perluas ke **seluruh lapisan sumber musik** (`MusicSource`, `StreamProvider`, `ArtistRepository`, `LyricsRepository`) dengan model domain Mupa murni (`Track`, `Artist`, `Album`, `Playlist`), sehingga UI 100% buta terhadap InnerTube maupun Rust local engine.

---

## 4. Audit Mekanisme Animasi Player Mochy

### 4.1 Mekanisme Inti Transisi Mini Player ↔ Full Player
Mochy **TIDAK** menggunakan `AnchoredDraggable` maupun Compose `SharedElement` untuk transisi mini ↔ full player. Mekanismenya adalah **progress-driven unified sheet** berbasis:
1. **State (`ui/component/BottomSheet.kt:842-1160`):** `BottomSheetState` memegang satu `Animatable<Dp>` dengan 3 anchor: `dismissedBound (0.dp)`, `collapsedBound (bottomInset + navBarHeight + MiniPlayerHeight 64.dp)`, dan `expandedBound (maxHeight)`.
2. **Progress tunggal:** `progress: Float` dihitung via `derivedStateOf` (`0f` = collapsed, `1f` = expanded, `< 0f` = dismissing).
3. **Detektor Gestur Kustom (`detectSheetDrag`, `BottomSheet.kt:795-829`):**
   - Menggunakan dynamic touch slop (`2×` saat terbuka agar slider/list internal prioritas), *child yield* di `PointerEventPass.Final`, dan akumulasi `dragSum` (mengatasi delta kecil per event di layar 120Hz+).
   - **Follow 1:1:** `hold()` menghentikan `Animatable`, `dragHeld()` menggeser langsung `collapsedBound..expandedBound`, `release(velocity)` melepaskan ke **critically damped spring dinamis** (`dampingRatio = 1f`, `omega = clamp(toward/dist, 8.94..42)`).
   - **Resolusi konflik gestur:** Swipe horizontal pada mini player didelegasikan ke `SongCarousel` (`abs(dx) > abs(dy)`); swipe horizontal pada cover penuh dicegat di `PointerEventPass.Initial` (sebelum sheet `Main` pass); swipe-down menutup dibatasi pada zona tertentu (`canCollapseFrom`); `collapseOnNestedOverscroll` dimatikan saat Queue/Lyrics terbuka.
4. **Pemetaan `progress (p)` ke Elemen Visual (semua dibaca di fase Layout/Draw via lambda, bukan Composition):**
   - **Wadah Sheet (`BottomSheet.kt:379-394`):** `graphicsLayer` mengatur `translationY = (expandedBound - value).toPx()`, clip `RoundRect` mutable (tanpa alokasi per frame) yang berinterpolasi `left/right` dari bingkai pill mini → `0.dp`, tinggi `pillHeight → expandedBound`, sudut `20.dp → 0.dp`.
   - **Konten App di Belakang (`MainActivity.kt:1182-1204`):** `scale = 1 - 0.06*p`, `alpha = clamp((1 - p) / 0.25, 0..1)` (mulai memudar setelah `p > 0.75`).
   - **Bottom Nav Bar (`MainActivity.kt:1070-1169`):** `translationY = navBarTotalHeight * p`, `alpha = clamp(1 - p / 0.6, 0..1)`.
   - **Backdrop vs Glass Mini (`MorphingCover.kt:1830-2011`):** Histeresis mount: `SheetPillGlass` tetap terpasang sampai `p > 0.21f` sementara `SheetBackdrop` sudah penuh (`alpha = 1f`) di `p = 0.18f` (overlap 3% untuk mencegah celah transparan).
   - **Cover Art Morph (`MorphingCover.kt:265-389`):** Menggunakan **`Modifier.layout` manual** (`morphLayout(ART)`), menginterpolasi ukuran, posisi pusat, dan `cornerRadius` (melalui `AnimatedCornerShape` reusable) dari `48.dp` mini → cover penuh → `60.dp` header saat Queue/Lyrics dibuka.
   - **Teks & Tombol Mini vs Kontrol Penuh:** Elemen mini memudar `0..0.5f` sambil bergeser; kontrol penuh muncul `0.25..0.85f` dan tidak menerima klik/penempatan saat `p <= 0.25f`.
   - **BackHandler:** Dua `BackHandler` terpisah (`PageBackHandler` dipasang setelah `SheetBackHandler`) sehingga tombol Back menutup Queue/Lyrics lebih dulu, lalu menciutkan Full → Mini.

### 4.2 Transisi Sekunder: Queue & Lyrics (`PlayerPageTransition.kt`, `PlayerBottomCardStack.kt`)
- State machine `PlayerPage { NONE, LYRICS, QUEUE }` dengan `Animatable<Float>` (`header`, `lyrics`, `queue`).
- **Trik anti-jank paling penting di Mochy (2-Frame Pre-Mount Warm-up, `PlayerPageTransition.kt:106-118`):** Sebelum animasi buka dijalankan, state `preparing = target` memasang `LazyColumn` berat selama **2 `withFrameNanos`** dalam kondisi ter-clip kecil (frame 1 compose+measure, frame 2 draw), baru memulai animasi di frame 3 yang bersih.
- `MorphingSongInfo` memindahkan judul/artis dari kontrol penuh ke header kecil menggunakan GPU `scaleX/Y` (tanpa relayout teks per frame).

### 4.3 Audit `spring(dampingRatio < 1f)` (Overshoot/Bounce) di Mochy — **WAJIB DIHINDARI DI MUPA**
Mochy memiliki **27+ spring dengan `dampingRatio < 1f`** yang menimbulkan efek memantul/overshoot (termasuk yang secara halus atau kuat membuat pusing):
- **Sheet & Navigasi:** `PlayerSheetAnimationSpec` (`dampingRatio = 0.96f, stiffness = 80f`), `PlayerSheetHorizontalAnimationSpec` (`0.92f`), `wideBarWithPlayer` (`0.86f`), `NavHighlightLead/Trail/PillRise/Settle` (`0.82f / 0.55f / 0.70f`), `ContainerOpenDamping` (`0.88f`).
- **Queue/Lyrics & Deck:** `OpenSpring` (`0.82f, 230f` — sengaja unclamped agar list memantul!), `deck.flip` (`0.75f`), `HandleBendSpring` (`0.82f`), `MiniLyricsTicker` (`0.86f`).
- **Tombol & Kontrol (overshoot ekstrem):** `pressBounce` (`0.38f`), `TransportIcons` play squash (`0.34f`), `LikeBurst` (`0.30f / 0.35f`), `FlagshipTransport` shuffle/repeat spin (`0.40f`), `SeekStretchBack` (`0.45f`), `FilterChip` (`0.50f`), `ExpandMenuOverlay` (`0.54f`), `PressScaleActionButton` (`0.54f`), chevron rotate (`0.55f`).

> **Aturan Mupa (Aturan #6):** Nol `dampingRatio < 1f` di seluruh kode Mupa. Gunakan kurva **Emphasized Decelerate** (`CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)`), **Standard Decelerate** (`CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)`), atau `spring(dampingRatio = Spring.DampingRatioNoBouncy)` (`1.0f`) khusus saat membawa momentum *fling* jari agar kecepatan akhir cocok natural tanpa memantul sedikit pun.

### 4.4 Analisis 60/120 Hz & Penyebab Flash Hitam / Kedip di Mochy
**Pola bagus yang layak diadopsi Mupa (tulis ulang bersih):**
- Baca `progress` hanya lewat lambda di `graphicsLayer` / `layout {}` / `drawBehind`.
- Gunakan `WindowInsets.*IgnoringVisibility` (`stableSystemBars`) agar sembunyi/muncul system bar tidak memicu relayout massal.
- Isolasi state toggle boolean (`OwnScope`) dan gunakan reusable mutable `Shape` selama animasi.
- Pilih display mode refresh rate tertinggi + pertimbangkan frame-rate voting di Android 15+ (`HighRefreshVote`) untuk animasi kontinyu (lirik).
- 2-frame pre-mount warm-up sebelum membuka sheet berat (Queue/Lyrics).

**6 Sumber Kedip / Jank di Mochy yang HARUS dicegah di Mupa:**
1. **Backdrop telat siap (`MorphingCover.kt:1955-1975`):** Beberapa gaya backdrop baru dikomposisi saat `p > 0f` atau bitmap gradiennya masih `null` pada frame 1 → saat pill glass dilepas di `p = 0.21f`, warna latar belakang polos (`#000`) berkedip. **Solusi Mupa:** Pastikan warna dasar backdrop selalu sinkron (solid/gradient siap sejak frame 0) dan panel penuh memakai warna permukaan konstan sebagai lapisan paling bawah sebelum aurora/blur siap.
2. **Toggle `CompositingStrategy.Offscreen` layar penuh (`MainActivity.kt:1199`):** Memasang & melepas FBO layar penuh di `p = 0.001f` / `0.999f` memicu hitch/flash 1 frame di GPU Mali/HP low-end. **Solusi Mupa:** Hindari `Offscreen` layar penuh pada konten belakang saat drag; gunakan scrim overlay tipis di atas konten belakang.
3. **Mount kondisional di tengah drag (`p > 0.5f`, `p > 0.98f`):** Memasang elemen baru saat jari masih bergerak menciptakan frame spike. **Solusi Mupa:** Komposisi komponen utama player tetap stabil atau gunakan pre-mount sebelum animasi.
4. **Pembacaan `state.isExpanded` langsung di body `BottomSheet` (`BottomSheet.kt:369`)** dan **`remember(bounds)` membuat ulang `BottomSheetState`** saat insets berubah: menyebabkan recomposition & animasi terputus. **Solusi Mupa:** Simpan bounds di `State` mutable di dalam instance state holder stabil (jangan recreate state saat insets berubah).
5. **Peluncuran coroutine `launch { snapTo() }` per event sentuh (`BottomSheet.kt:968`):** Berisiko tertinggal 1 frame di layar 120Hz. **Solusi Mupa:** Gunakan `CoroutineStart.UNDISPATCHED` atau update offset sinkron saat finger-drag.
6. **Fallback blur asinkron tanpa warna dasar (`GlFallbackSurfaces.kt:228`):** Menggambar kosong sebelum bitmap pertama selesai. **Solusi Mupa:** Di bawah API 31 (dan sebelum efek siap), selalu gambar permukaan translusen/solid Mupa secara sinkron — tidak ada GLES fallback rumit.

---

## 5. Audit Onboarding & Personalisasi Mochy

- **Temuan Eksplisit:** **Onboarding Mochy saat ini (`ui/screens/setup/SetupWizardScreen.kt`) TIDAK memiliki pemilihan artis favorit maupun taste picker.**
- **13 Langkah Onboarding Mochy yang Sebenarnya Ada:** `Welcome` (maskot) → `ImportAsk` → `ImportMode` (Zip/Streaming/CSV) → `Providers` (Spotify/Apple/YTM/Tidal) → `ProviderSetup` (WebView login) → `WhatToSync` → `SyncOptions` (khusus YTM) → `Profile` (nama & avatar lokal) → `Hello` (3 detik + **benchmark GPU blur `BlurCapability`** tersembunyi) → `BlurWarning` (hanya jika verdict `Laggy`) → `LastOne` (Crossmix & AI Artist Filter) → `Done` (menulis `SetupCompletedKey = true` + file marker `noBackupFilesDir/setup_wizard_completed`).
- **Jejak Kode Personalisasi Cold-Start:** Di `HomeViewModel.kt:862-892` (`loadStarterSongs()`) dan `HomeRecommender.kt:137-149`, masih ada kode lama yang berkomentar *"artists picked during onboarding (saved to the library) stand in until listening history takes over"*: jika `eventCount == 0`, ia membaca hingga 5–14 artis dari Room DB untuk mengisi `starterSongs` dan profil rekomendasi. Jika user melewati impor, daftar kosong dan Beranda fallback ke feed tamu generik.
- **Relevansi untuk Mupa:** Mupa akan membangun **Onboarding Artis Favorit wajib** dari nol (min 2 artis, anjuran 4, usul maks 8, tanpa tombol lewati, tersimpan di DataStore + bisa diubah dari Akun) di balik interface `ArtistRepository`, lalu menyalurkan artis terpilih sebagai *seed* rak Beranda ("Untukmu") baik untuk dummy Tahap 1, streaming Tahap 2, maupun library lokal Tahap 3.

---

## 6. Ekstraksi Design System Mochy

- **Tema (`ui/theme/Theme.kt`):** Terkunci mode gelap (`ForceDarkTheme = true`). Dasar netral hitam/abu (`#000000` bg, `#131313` @ 78% glass chrome, `#1A1A1A` surfaceVariant, `#E0E0E0` primary). Tidak memakai Material You; warna aksen dinamis diekstrak dari cover art via `Palette` + `MaterialKolor` (`ArtworkPalette`, `CoverThemeColor`, `PlayerColorExtractor`, `AmbientBackdrop`).
- **Tipografi (`ui/theme/Type.kt`):** Font bawaan **Inter** (bobot 100–800), letter-spacing rapat (`-0.05.em` global, `-0.06.em` heading), skala M3 standar (Display 57/45/36sp, Headline 32/28/24sp, Title 22/16/14sp, Body 16/14/12sp, Label 14/12/11sp).
- **Shape & Dimensi (`constants/Dimensions.kt`, `SmoothCornerShape.kt`):** Squircle `SmoothCornerShape(radius, exponent=4f)`, stadium pill (`50%`) untuk Bottom Nav Bar (`64.dp`, margin horizontal `24.dp`), `MiniPlayerHeight = 64.dp`, `PillCornerRadius = 20.dp`, `ListItemHeight = 64.dp`, `ListThumbnailSize = 48.dp`, `PlayerHorizontalPadding = 32.dp`.
- **Pipeline Glass/Blur (`GlassKit.kt`, `FrostedGlass.kt`):** Multi-tier kompleks (API 35+ full RenderEffect, API 33–34 RenderEffect clamp `<=54f` + downscale 1/2–1/4 + AGSL progressive blur, API 31–32 RenderEffect + slice, API 29–30 EGL/GLES 3.0 Dual-Kawase fallback, API 24–28 CPU box blur). Untuk Mupa (Aturan #6 & Tahap 1e), cukup **RenderEffect pada API 31+** dan **fallback solid/translusen bersih di bawah API 31** + hormati toggle performa.

---

## 7. Ekstraksi Fitur, Perilaku & Gaya dari `music-x-V8_5.html`

### 7.1 Palet Warna, Mode OLED & Ultra Dynamic V15
- **Palet Gelap Default (`:root`, `L22–L35`):** Gelap saja (tanpa light theme). Background `#121014`, elevated `#1A171D`, card `#201D24`, border `#2A262F`, teks `#F0EEF2` / `#A8A4AE` / `#8A8690`, **aksen ungu `#A78BFA`** (`--purple-dim: #6D5B99`, `--purple-glow: rgba(167,139,250,0.15)`).
- **Ultra Dynamic Engine V15 (`L11235–L11702`):** Ekstraksi warna cover 64×64 ke ruang warna **OKLab** + histogram 12×12×12 berbobot Gaussian tengah + weighted k-means (`K=12`), dengan gamut clamping OKLCH dan fallback netral untuk cover monokrom.
- **Mode OLED (`html.oled`, `L4768–L4904`):**
  - Memaksa latar `#000000`, permukaan elevated `#0A0A0E`, border tipis `#1C1C21`–`#24242E`, teks putih lembut `#E6E4EA`, elemen aktif kontras netral `#E6E4EA`/`#F2F0F7` bertinta `#09090C`.
  - **Mematikan seluruh `backdrop-filter`**, semua `box-shadow` glow, **Aurora**, dan backdrop blur player (kecuali glow radial sangat halus 28% di puncak halaman Lirik).
  - Menyertakan **anti burn-in pixel shift** (±2px acak tiap 75 detik pada bar statis).

### 7.2 Gaya Glass (`Crystal` vs `Liquid+` & Lainnya)
HTML V8.5 punya 4 preset (`Music-X`, `Frosted`, `Crystal`, `Liquid+`) + eksperimen `Hybrid Blur`:
- **`Crystal` (`L3904–L3995`):** `backdrop-filter: blur(14px) saturate(1.52)` (halaman: `8px / 1.3`), kilau faset diagonal, caustics bawah, dan **rim dispersi 3 warna RGB tipis** (`border: rgba(--glass-edge-a, 0.34)`, highlight atas `1.5px` + kiri/kanan `1px` + sudut atas).
- **`Liquid+` (`L6425–L6668`):** `backdrop-filter: blur(5–7px) saturate(1.55–1.60)` — lebih jernih & optis seperti lensa cair dengan **bevel border tinggi** (`border-top: white@70-74%`, `border: white@48%`), **dispersi warna hangat di sisi kiri (`rgba(255,145,125,0.48)`) & dingin di sisi kanan (`rgba(95,220,255,0.50)`)**, serta teks putih tegas ber-text-shadow.
- **Penjaga Performa:** Mematikan blur pada tier hemat/OLED dan mematikan blur di layar belakang saat player/lirik terbuka penuh (`body.mx-under`, `body.lyr-open`).

### 7.3 Aurora Background (`L5347–L5351`, `L6740–L6745`, `L21478–L21566`)
- Dua *radial gradient blob* di bagian atas halaman (tinggi `540px`: blob kiri atas `14% 0%` @44% alpha, blob kanan atas `94% 6%` @34% alpha).
- Warna diambil dari lagu yang sedang diputar / terakhir diputar (prioritas: palet dinamis cover → rata-rata saturasi cover → hash warna deterministik → warna default).
- Bertransisi halus `420ms` saat ganti lagu, punya animasi drift pelan `20s`, **otomatis mati total di Mode OLED atau saat toggle Aurora OFF**, dan **dijeda** saat tertutup sheet.

### 7.4 Navigasi Bawah 4 Tab & Isi Tiap Tab
Floating glass pill (`62px` tinggi, radius `37px`, 4 tab ikon + indikator pill geser, sembunyi saat keyboard/input fokus):
1. **`Beranda`:** Header (wordmark + streak + avatar), sapaan waktu (pagi/siang/sore/malam), Hero Now Playing card (cover, judul, status, tombol lirik mini, seekbar, kontrol), 3 kartu statistik cepat (`Lagu`, `Favorit`, `Menit hari ini`), `Akses cepat` (grid 2 kolom maks 6), **`Mix X`** (12 kartu + tombol `Racik ulang` + pil alasan `.mx-why`), `Artis kamu`, `Cocok untuk [waktu]`, grafik `Minggu ini`, **`Ulangi lagi`**, **`Belum diputar`**, `Terakhir diputar`, `Baru ditambah`.
2. **`Cari`:** Input pil + 4 chip cakupan (`Semua`, `Judul`, `Artis`, `Lirik`) + 6 chip filter (`Favorit`, `Punya lirik`, `Tidak ada lirik`, `Belum diputar`, `< 3 mnt`, `> 5 mnt`). Saat kosong menampilkan `Lirik acak`, `Terakhir dicari` (8 item), dan `Artis`. Hasil menyorot kecocokan judul/artis/potongan lirik.
3. **`Pustaka`:** Sub-tab `Semua Lagu` (chip Favorit, Vibe Phonk/Reguler, 6 urutan: Terbaru/A–Z/Z–A/Artis/Sering/Terpanjang, Acak, Ulang; swipe kanan untuk favorit; menu 3-titik) & `Playlist` (Smart Playlist `Untuk kamu` + playlist buatan user + drag reorder + detail playlist).
4. **`Akun`:** Profil (avatar + nama), level & XP pendengar, 4 kartu statistik (waktu dengar, putaran, streak, hari aktif), grafik 7 hari & modal 30 hari, Artis/Lagu teratas, Wawasan, kontrol tampilan cepat (`Kaca halaman`, `Aurora latar`, `Mode OLED`), ekspor/impor profil, penggunaan penyimpanan, & pintasan `Pengaturan`.

### 7.5 Rekomendasi Lokal (Exact Algorithm di `music-x-V8_5.html:21649-21774`)
Semua dihitung offline dari histori putar (`playCount` bertambah saat diputar ≥ min(15s, 40% durasi)), log 400 entri terakhir, dan sinyal selesaikan (`>=85%`) vs skip (`<30%`):
- **`Mix X` (2.0):** Maks 30 lagu (12 tampil di Beranda), skor gabungan dari: favorit (`+0.30`), afinitas artis (`+0.30`), jumlah putar logaritmik (`+0.12`), tren exponential decay paruh-waktu 21 hari (`+0.40`), kesesuaian jam melingkar Gaussian σ=2,2j + akhir pekan (`+0.30`), transisi Markov dari lagu aktif (`+0.32`), artis serupa (`+0.16`), penalti baru diputar (`-0.60` <1 hari, `-0.30` <3 hari, `-0.10` <7 hari), bonus penemuan kembali ≥14 hari (`+0.28..0.48`), lagu baru (`+0.12..0.22`), naik daun 7 hari (`+0.14`), rasio tuntas vs skip (`±0.25` / penalti skip `-0.35`), jitter seed harian (`+0.06`). Menyisipkan **slot eksplorasi** di posisi indeks `{2, 6, 11, 17}` dan menerapkan **MMR diversitas** (maks <2 lagu per artis di 12 besar) serta melampirkan pil alasan (`Favorit`, `Artis favoritmu`, `Lama tak diputar`, `Coba ini`, dll.).
- **`Ulangi lagi`:** Lagu dengan `(playCount >= 2 || favorite)` dan terakhir diputar `>= 10 hari` lalu; diurutkan berdasarkan afinitas artis → `playCount`; maks 30 (12 di Beranda).
- **`Belum diputar`:** Lagu dengan `playCount == 0`; diurutkan berdasarkan `importedAt` terbaru; maks 30 (12 di Beranda).

### 7.6 Player Modes (Mini, Semi, Full) & Fitur Semi yang Dipertahankan
- **Mini Player:** Bar mengambang di atas bottom nav (`42px` art, judul, status, Prev/Play-Pause/Next, progress bar).
- **Semi Player (AKAN DIHAPUS di Mupa):** Di HTML, Semi adalah bottom sheet `88vh` sementara antara Mini dan Cinema/Full. Fitur penting yang sebelumnya hidup di Semi dan **tetap dibawa ke Full Now Playing Mupa**: tombol 5 kontrol primer (Shuffle, Prev, Play/Pause, Next, Repeat 3-mode), seekbar + waktu, dan baris 5 aksi sekunder (**Favorit**, **EQ**, **Lirik**, **Antrean** + badge jumlah, **Sleep Timer**).
- **Antrean:** `manualQueue` berbasis ID lagu, drag-to-reorder dengan grip, tombol hapus per item, diputar lebih dulu sebelum antrean normal/shuffle.

### 7.7 Lirik + Wipe Warna Adaptif (`L4353–L4402`, `L5005–L5079`, `L17494–L17747`)
- **Format:** LRC tersinkron `[mm:ss.xx]`, Enhanced Word-level LRC `<mm:ss.xx>kata`, teks polos, tag tersemat (ID3v2 `SYLT`/`USLT`, FLAC Vorbis, Ogg/Opus, MP4 `©lyr`), dan fallback cari online (`lrclib.net`, `karalyr.com`). Jeda instrumental `>= 3s` menampilkan animasi 3 titik.
- **Mekanisme Wipe Warna Adaptif:**
  - Warna aktif (`--lrc-on`) diambil dari warna primer cover art lalu **dikunci kecerahannya di OKLCH (`L ∈ [0.87..0.97]`, `C × 1.05`)** agar cover gelap tetap punya kontras ≥ 1,5× terhadap teks belum dinyanyikan (`white @ 52%`).
  - Setiap kata/karakter (per karakter untuk CJK, per kata untuk Latin) diberi gradien horizontal ber-feather lembut 28% (`calc(p * 128% - 28%)` → `calc(p * 128%)`) yang menyapu dari kiri ke kanan seiring progres waktu kata (estimasi proporsional panjang kata jika LRC hanya level baris).
  - Auto-scroll ke posisi `36%` dari atas layar dengan pegas teredam kritis (tanpa overshoot), jeda 3 detik saat user menggulir manual, tap baris untuk seek, serta efek fokus/blur halus pada baris tidak aktif.
  - Fitur tambahan di HTML: offset lirik per lagu (`±0.1s/±1.0s`, rentang ±15s), mode Pure Lyrics fullscreen, Pinyin/Romaji, dan Kartu Kutipan Lirik.

### 7.8 Equalizer (EQ) & Pemrosesan Audio (`L9358–L9477`)
- **5 Band (`-12 dB` s/d `+12 dB`):** `60 Hz` (lowshelf), `250 Hz` (peaking Q=1), `1 kHz` (peaking Q=1), `4 kHz` (peaking Q=1), `12 kHz` (highshelf).
- **5 Preset + Custom:** `Flat [0,0,0,0,0]`, `Vocal [-2,1,4,3,0]`, `Bass [6,3,0,-1,-1]`, `Treble [-1,-1,0,3,5]`, `Warm [3,2,0,-2,-3]`.
- Tidak ada Virtualizer/Reverb/BassBoost terpisah; ada **Volume per lagu** (`20%–200%` dengan soft-clipper `tanh` di atas 100%) dan **Tempo** (`0.5×–2.0×` dengan toggle `Jaga nada`).

### 7.9 Pengaturan "Hapus Nama Artis" (`L8935–L8955`, `L9890–L9950`, `L13604–L13664`)
- **Label UI:** `Hapus Nama Artis dari Judul` — *"Rapikan format 'Artis - Judul' secara otomatis."* (default **ON**), dilengkapi chip cakupan tampilan per area (`Pustaka`, `Player`, `Beranda & Cari`).
- **Cara Kerja Non-Destruktif (Kritis untuk Engine Rust Tahap 3):**
  - Setiap file lokal **WAJIB menyimpan `originalFileName` asli secara permanen** di database.
  - Parser memisahkan string pada pemisah `-`, `–`, `—`, atau `|`, sekaligus menyaring segmen clutter modifier (`slowed`, `reverb`, `nightcore`, `official video`, `lyrics`, `320kbps`, `remastered`, dll.) agar `"Artis - Judul - Slowed"` tidak salah menaruh `"Slowed"` sebagai judul.
  - Saat toggle dimatikan/diubah, nama artis dan judul bersih **dipulihkan ulang dari `originalFileName`**; sementara mesin rekomendasi, pencarian artis, sort artis, dan pencarian lirik tetap membaca artis asli dari tag/`originalFileName` meski nama artis disembunyikan di tampilan judul.
