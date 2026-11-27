package id.rwi.scanpdfin.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import id.rwi.scanpdfin.data.FilterMode
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

/** Semua pengolahan gambar: muat, putar, filter, simpan. Tanpa pustaka native. */
object ImageOps {

    /** Ukuran sisi terpanjang saat gambar masuk ke app (menjaga penyimpanan tetap wajar). */
    const val IMPORT_MAX_SIDE = 3000

    // ---------- muat ----------

    fun decodeUri(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        // Catatan: decodeStream dengan inJustDecodeBounds SELALU mengembalikan null (hanya mengisi ukuran),
        // jadi keberhasilan dicek dari outWidth, bukan dari nilai kembalian. (Bug v0.1.0: semua impor gagal.)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val exifRotation = resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
        return limit(rotate(raw, exifRotation), maxSide)
    }

    fun decodeFile(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(file.path, opts) ?: return null
        return limit(raw, maxSide)
    }

    private fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
        if (maxSide <= 0) return 1
        var s = 1
        var side = max(w, h)
        while (side / 2 >= maxSide) { side /= 2; s *= 2 }
        return s
    }

    private fun exifRotation(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) { 0 }

    /** Kecilkan bila sisi terpanjang melebihi maxSide. */
    fun limit(src: Bitmap, maxSide: Int): Bitmap {
        if (maxSide <= 0) return src
        val side = max(src.width, src.height)
        if (side <= maxSide) return src
        val scale = maxSide.toFloat() / side
        val out = Bitmap.createScaledBitmap(src, max(1, (src.width * scale).toInt()), max(1, (src.height * scale).toInt()), true)
        if (out !== src) src.recycle()
        return out
    }

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val m = Matrix().apply { postRotate(d.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    // ---------- simpan ----------

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int) {
        // aman: foto lama tidak hilang bila app mati di tengah penyimpanan
        id.rwi.scanpdfin.data.AtomicFile.write(file) { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) throw java.io.IOException("Gagal menulis JPEG") }
    }

    /** Impor gambar dari luar: luruskan EXIF, batasi ukuran, simpan JPEG. */
    fun importToFile(context: Context, uri: Uri, target: File): Boolean = try {
        val bmp = decodeUri(context, uri, IMPORT_MAX_SIDE)
        if (bmp == null) false else {
            saveJpeg(bmp, target, 92)
            bmp.recycle()
            true
        }
    } catch (e: Exception) {
        // izin URI kedaluwarsa / berkas rusak / memori habis: lewati gambar ini, jangan tutup app
        android.util.Log.w("ScanPdfin", "Impor gagal: $uri", e)
        false
    } catch (e: OutOfMemoryError) {
        android.util.Log.w("ScanPdfin", "Impor kehabisan memori: $uri", e)
        false
    }

    // ---------- render halaman ----------

    /** Muat berkas halaman, putar, lalu terapkan mode. */
    fun render(file: File, rotation: Int, mode: FilterMode, maxSide: Int): Bitmap? {
        val base = decodeFile(file, maxSide) ?: return null
        val rotated = rotate(base, rotation)
        return applyFilter(rotated, mode)
    }

    /** Terapkan model tampilan (lihat [Looks]). Bitmap sumber didaur ulang bila hasilnya bitmap baru. */
    fun applyFilter(src: Bitmap, mode: FilterMode): Bitmap = Looks.apply(src, mode)
}
