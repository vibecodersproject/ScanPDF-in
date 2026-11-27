<h1 align="center">Scan PDF-in</h1>

<p align="center">
  <b>Aplikasi pemindai dokumen & alat PDF Android buatan anak Indonesia 🇮🇩 — open source, gratis, dibangun dengan bantuan AI.</b><br>
  Pindai dokumen, ubah tulisan di PDF, tanda tangan, atur halaman, dan kecilkan PDF langsung di HP.<br>
  <b>Tanpa login · tanpa iklan · tanpa internet · hanya izin kamera.</b>
</p>

<p align="center">
  <a href="https://vibecoder-rizki.rizkiismadani.workers.dev/#scanpdfin"><b>⬇️ Unduh APK</b></a> ·
  <a href="#fitur">Fitur</a> ·
  <a href="#membangun-dari-kode-sumber">Bangun dari kode</a> ·
  <a href="#english">English</a>
</p>

---

## Kenapa Scan PDF-in?

- **Karya anak bangsa.** Dirancang dan dibuat di Indonesia oleh seorang *vibe coder*, yaitu orang yang membangun aplikasi dengan menjelaskan kebutuhannya kepada AI. Seluruh tampilannya berbahasa Indonesia.
- **Dibangun bersama AI.** Kodenya (Kotlin + Jetpack Compose) ditulis dengan bantuan asisten AI (Claude dari Anthropic), lalu diaudit dan diuji.
- **Privasi penuh.** Aplikasi tidak meminta izin internet sama sekali, jadi dokumen Anda tidak bisa keluar dari HP. Data juga tidak ikut cadangan otomatis Google.
- **Tanpa iklan, tanpa akun, gratis**, dengan lisensi Apache-2.0.
- **Berdiri di atas proyek open source**: CameraX, OpenCV, dan PdfBox-Android.

## Fitur

Tiga menu utama: **Scan**, **PDF**, dan **Kecilkan PDF**.

| | |
|---|---|
| 📷 **Pindai dokumen** | Tepi kertas terdeteksi sendiri dan bisa dirapikan. Pilih tampilan Asli, Foto, atau Dokumen, berwarna atau hitam putih. Hasil tiap foto bisa digeser dan dicek satu per satu. |
| 🔦 **Lampu selalu nyala** | Lampu kamera bisa menyala sejak kamera dibuka, jadi dokumen terlihat jelas sebelum dipotret. |
| ✏️ **Ubah tulisan di PDF** | Ketuk tulisan di PDF lalu ganti; ukuran, tebal, dan warna tulisan aslinya ikut. Pada PDF hasil scan, tutup tulisan lama lalu ketik di atasnya. |
| ✍️ **Tanda tangan & coretan** | Simpan tanda tangan, tempel di halaman, tambah teks, pena, dan stabilo. |
| 📑 **Atur halaman** | Tahan lalu geser untuk mengurutkan. Sisipkan, putar, hapus, pisahkan halaman, dan gabungkan beberapa PDF jadi satu. |
| 📖 **Baca & slide show** | Mode gulir atau per halaman, slide show layar penuh, perbesar dengan cubit dua jari. |
| 🗜️ **Kecilkan PDF** | Banyak berkas sekaligus. Tingkat Ringan dan Sedang hanya mengecilkan gambar, jadi teks tetap tajam dan bisa dicari; cocok untuk dikirim lewat WhatsApp. |
| 🔒 **Kunci PDF** | Simpan salinan berkata sandi (AES-256). |
| 📂 **Pembuka PDF** | Bisa dipilih saat membuka PDF dari aplikasi lain, atau dijadikan pembuka bawaan. |

## Unduh

APK Android tersedia di **[situs Vibe Coder's Project](https://vibecoder-rizki.rizkiismadani.workers.dev/#scanpdfin)**. Aplikasi dipasang di luar Play Store: buka berkas APK, lalu izinkan pemasangan dari sumber ini.

> Versi saat ini 0.5.0 (uji coba). Ubah tulisan hanya berlaku untuk PDF yang tulisannya bisa dibaca, bukan PDF berupa gambar. Tingkat Kecilkan **Kuat** mengubah halaman menjadi gambar, sehingga teksnya tidak bisa dicari lagi.

## Membangun dari kode sumber

Butuh **JDK 17** dan **Android SDK** (API 35). Buka folder ini di Android Studio, atau lewat terminal:

```bash
./gradlew assembleDebug
```

APK hasil build ada di `app/build/outputs/apk/`. Untuk build release yang ditandatangani, salin `keystore.properties.example` menjadi `keystore.properties` lalu isi data kunci Anda sendiri. Berkas itu dan kuncinya tidak ikut ke repositori.

### Struktur kode

| Folder (`app/src/main/java/id/rwi/scanpdfin/`) | Isi |
|---|---|
| `ui/` | Layar Jetpack Compose: kamera, atur bingkai, warna, dokumen, pembaca & editor PDF, kecilkan, pengaturan |
| `image/` | Deteksi tepi dokumen (OpenCV) dan filter tampilan Asli/Foto/Dokumen buatan sendiri |
| `pdf/` | Mesin PDF (PdfBox-Android): baca tulisan, ubah tulisan, halaman, kompres, sandi |
| `data/` | Penyimpanan lokal: indeks JSON yang ditulis atomik, preferensi |
| `export/` | Ekspor ke PDF dan JPG |

## Terima kasih kepada proyek open source

- [**PdfBox-Android**](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0): baca, sunting, dan buat PDF
- [**OpenCV**](https://opencv.org/) (Apache-2.0): deteksi tepi dokumen
- [**AndroidX CameraX, Jetpack Compose, Material 3**](https://developer.android.com/jetpack) (Apache-2.0)
- [**Kotlin**](https://kotlinlang.org/) & kotlinx (Apache-2.0)

## Lisensi

**Apache-2.0**. Lihat [LICENSE](LICENSE). Bebas dipakai, dipelajari, diubah, dan dibagikan, termasuk untuk keperluan komersial, dengan tetap mencantumkan lisensi dan pemberitahuan hak cipta.

## Pembuat

Dibuat oleh **Rizki Wahyu Ismadani**, vibe coder dari Indonesia, dengan bantuan AI.
Aplikasi lain: [Vibe Coder's Project](https://vibecoder-rizki.rizkiismadani.workers.dev/), termasuk [GarudaPDF](https://github.com/vibecodersproject/GarudaPDF) untuk Windows.
Saran dan laporan bug: **Issues** di repositori ini, atau menu Pengaturan di aplikasi.

---

## English

**Scan PDF-in** is a free, open-source Android document scanner and PDF toolkit, **made in Indonesia 🇮🇩 by a vibe coder with the help of AI**. It asks for the camera permission only and has **no internet permission at all**: no account, no ads, no tracking.

Scan documents with automatic edge detection and clean-up filters. Edit text inside PDFs, sign, and annotate. Reorder, insert, rotate, split, and merge pages. Compress many PDFs at once while keeping text sharp, and password-protect files (AES-256). The interface is in Indonesian.

Built with Kotlin, Jetpack Compose, CameraX, OpenCV, and PdfBox-Android. Licensed under **Apache-2.0**.
