# Music-X-Native (Mupa)

Struktur folder kerja. Isi sendiri sebelum menjalankan agent:

- `Mochy/`            -> taruh source code Mochy di sini (hanya referensi, tidak dikirim ke hasil akhir)
- `music-x-V*.html`   -> taruh file HTML Music X (referensi fitur) di folder ROOT ini, sejajar README
- `assets/logo/`      -> logo Mupa
- `docs/`             -> diisi agent (AUDIT.md, BRAND-MAP.md, ROADMAP.md, QUESTIONS.md, PROGRESS.md)

## assets/logo/
Glyph "m" (viewBox 96x96, path sederhana tanpa gradient, aman untuk VectorDrawable):
- `mupa-glyph.svg`                    : satu path, fill=currentColor (untuk tint)
- `mupa-glyph-accent.svg`             : badan gelap #16161A + kaki oranye #FF5A3C (untuk latar TERANG)
- `mupa-glyph-accent-ondark.svg`      : badan krem #F6F4EF + kaki oranye (untuk latar GELAP, mis. foreground ikon launcher)
- `mupa-glyph-1024.png`               : PNG 1024 transparan (versi gelap)
- `mupa-glyph-accent-ondark-1024.png` : PNG 1024 transparan (versi on-dark)

Sumber/placeholder:
- `mupa-sheet-original.png`            : lembar asli (sumber kebenaran, jangan diubah)
- `mupa-lockup_from-sheet.png`, `mupa-wordmark_from-sheet_transparent.png` : crop SEMENTARA, resolusi rendah

Catatan: layer monochrome ikon launcher harus dibuat dari DUA path di mupa-glyph-accent.svg
(badan alpha 100%, kaki alpha ~55%), bukan dari mupa-glyph.svg yang satu path.

## assets/android-res/ (resource ikon siap pakai, belum diuji di build Android)
Struktur sama dengan app/src/main/res/. Salin isinya ke sana saat proyek Mupa dibuat:
- `drawable/ic_launcher_foreground.xml`  : glyph on-dark (skala 0,8, muat safe zone 66dp)
- `drawable/ic_launcher_monochrome.xml`  : badan alpha 100%, kaki alpha 55% (themed icon)
- `drawable/ic_notification.xml`         : ikon kecil notifikasi (putih saja)
- `mipmap-anydpi-v26/ic_launcher.xml` dan `ic_launcher_round.xml` : adaptive icon (background, foreground, monochrome)
- `values/mupa_icon_colors.xml`          : #16161A, #F6F4EF, #FF5A3C
Splash memakai ic_launcher_foreground + warna background #16161A.
