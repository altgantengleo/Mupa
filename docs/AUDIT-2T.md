# Audit Fase 2T — Antrean Cerdas, Radio InnerTube & Bug "blue" (`AUDIT-2T.md`)

> **Checkpoint:** T0 (Read-only Audit + Reproduksi Bug)  
> **Branch:** `fase-2t`

---

## 1. Bagaimana Antrean Dibangun Saat Ini (Akar Masalah Bug `"blue"`)

1. **Ketuk hasil pencarian di `SearchScreen.kt:445`:**
   ```kotlin
   onClick = { onPlayTrack(uiState.results, index) }
   ```
   Seluruh `uiState.results` (semua hasil pencarian untuk kueri `"blue"`, termasuk versi Slowed, Acoustic, Live, Instrumental, Lyric Video, Cover, Remix) dikirim langsung sebagai daftar antrean!
2. **Diteruskan langsung oleh `MupaNavHost.kt:150` → `MupaApp.kt:68` → `MainActivity.kt:68`:**
   `onPlayTracks = playerViewModel::playQueue`
3. **`MupaPlayerViewModel.kt:302-345` (`playQueue(tracks, startIndex)`):**
   - Tidak mengenal konsep `QueueSource` (Radio / Artist / Playlist / Local / MixMupa).
   - Langsung menimpa `_uiState.queue = tracks` dan `ctrl.setMediaItems(mediaItems, safeIndex, 0L)`.
   - Tidak memanggil endpoint radio/up-next, tidak punya filter versi alternatif, tidak punya continuation token, dan tidak punya muat lanjutan otomatis saat sisa antrean ≤ 3.
4. **Persistensi Antrean (`playback/.../QueuePersistenceStore.kt` & `MupaPlayerViewModel.kt:227-300`):**
   - Hanya menyimpan daftar `tracks`, `currentIndex`, dan `positionMs`.
   - Belum menyimpan `QueueSource`, `continuationToken`, penanda tak berujung (`isInfinite`), maupun seed urutan shuffle.
5. **Reproduksi Bug `"blue"` (T0):**
   - Fixture JSON: `app/src/test/resources/fixtures/search_blue_versions.json` (7 item hasil cari `"blue"`: 1 lagu asli `yung kai - blue` + 6 versi lain: Slowed + Reverb, Acoustic, Live, Instrumental Karaoke, Sped Up, Official Lyric Video).
   - Pengujian: `app/src/test/kotlin/com/xeno/mupa/T0QueueBlueBugReproTest.kt` membuktikan secara konkret bahwa jalur `SearchScreen` saat ini memasukkan keenam versi sampah/alternatif `"blue"` ke dalam antrean putar.

---

## 2. Verifikasi Implementasi Radio / "Up Next" / Automix InnerTube dari Sumber Mochy

Verifikasi dilakukan langsung pada sumber `D:\mochy\Mochy-Music-main\` (tanpa menyalin kode — akan ditulis clean-room di `:streaming:innertube`):

| Komponen | Lokasi Sumber Mochy | Detail Exact Endpoint & Parameter |
|---|---|---|
| **HTTP Endpoint** | `providers/innertube/.../InnerTube.kt:375-398` | `POST https://music.youtube.com/youtubei/v1/next` dengan klien `WEB_REMIX` (`clientName = 67`) |
| **Request Payload** | `providers/innertube/.../models/body/NextBody.kt:7-15` | `NextBody(context, videoId, playlistId, playlistSetVideoId, index, params, continuation)` |
| **Song Radio `playlistId`** | `app/.../playback/queues/YouTubeQueue.kt:204-205` | `playlistId = "RDAMVM$videoId"` dengan `videoId = seedVideoId` |
| **Parsing Daftar Up Next** | `providers/innertube/.../YouTube.kt:1297-1311` | Awal: `contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content.musicQueueRenderer.content.playlistPanelRenderer` · Lanjutan: `continuationContents.playlistPanelContinuation` · Item: `contents[].playlistPanelVideoRenderer` |
| **Automix Expansion** | `providers/innertube/.../YouTube.kt:1314-1326` | Bila item terakhir adalah `automixPreviewVideoRenderer.content.automixPlaylistVideoRenderer.navigationEndpoint.watchPlaylistEndpoint`, panggil `next(watchPlaylistEndpoint)` untuk memuat daftar mix radio otomatis |
| **Continuation Token** | `providers/innertube/.../YouTube.kt:1327+` & `YouTubeQueue.kt:155-180` | `playlistPanelRenderer.continuations[].nextRadioContinuationData.continuation` (atau `nextContinuationData.continuation`) dikirim balik sebagai `continuation` di `NextBody` saat memuat halaman berikutnya |
| **Fallback Anti-Kosong** | `app/.../playback/queues/YouTubeQueue.kt:45-84, 131-151` | Jika bare `videoId` mengembalikan ≤ 1 lagu → upgrade ke `RDAMVM<id>`; jika `RDAMVM<id>` mengembalikan ≤ 1 lagu → coba bare `videoId` (untuk memicu `automixPreviewVideoRenderer`); jika masih ≤ 1 lagu → fallback ke `tabs[2].tabRenderer.endpoint.browseEndpoint` (Related) atau lagu artis terkait |

---

## 3. Rencana Implementasi Setelah Konfirmasi Provider (T1–T4)
- **T1:** `QueueSource` (sealed), `SmartQueueEngine` (filter versi, normalisasi judul, dedupe `(judul+artis)`, durasi 60s–8m, maks 2 berurutan per artis secara lunak, hindari 20 lagu terakhir), `InnerTubeProvider.getRadioNext(...)` clean-room, auto-load lanjutan saat sisa ≤ 3, persistensi sumber + continuation, **meluluskan `T0QueueBlueBugReproTest`**.
- **T2:** Shuffle berbasis seed (hanya acak item berikutnya, tidak menggeser lagu aktif, "Sebelumnya" presisi, matikan shuffle kembalikan urutan asli, penyisipan acak untuk continuation radio + hindari 2 artis sama berurutan), tombol "Acak" di header Pustaka/detail (bukan Hero), label sumber di Antrean & Now Playing (`"Radio dari 'blue'"`), dokumentasi perilaku Repeat pada antrean tak berujung.
- **T3:** Pemisahan `CatalogProvider` / `StreamProvider`, parallel/hedged catalog search & stream hedging (~800 ms), SWR cache, DNS/TLS pre-warm, pre-resolve next track & Hero Play, timeout 3s connect / 6s total, onboarding artis multi-sumber progresif, pencocokan lintas provider (skor judul+artis+durasi ±3 dtk), ditambah provider sesuai pilihan user.
- **T4:** Instrumentasi debug lokal (`timeToFirstResultMs`, `timeToAudioMs`), update `docs/STREAMING.md` (runbook tiap provider) dan `docs/TEST-DEVICE.md`.
