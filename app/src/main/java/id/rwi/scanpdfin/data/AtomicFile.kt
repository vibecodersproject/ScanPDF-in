package id.rwi.scanpdfin.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Tulis berkas secara aman: isi ditulis ke .tmp, dipaksa ke penyimpanan (fsync), lalu .tmp diganti nama
 * menimpa berkas lama dalam satu langkah. Bila app mati di tengah jalan, berkas lama tetap utuh.
 * [keepBackup] = salin versi sebelumnya ke .bak (untuk indeks JSON yang penting).
 */
object AtomicFile {
    fun write(target: File, keepBackup: Boolean = false, block: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        FileOutputStream(tmp).use { fos ->
            block(fos)
            fos.flush()
            runCatching { fos.fd.sync() }
        }
        if (keepBackup && target.exists() && target.length() > 0) runCatching { target.copyTo(File(target.path + ".bak"), overwrite = true) }
        if (!tmp.renameTo(target)) {
            // beberapa sistem berkas tidak mengizinkan menimpa lewat rename
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Gagal menyimpan ${target.name}")
        }
    }

    fun writeText(target: File, text: String, keepBackup: Boolean = false) = write(target, keepBackup) { it.write(text.toByteArray(Charsets.UTF_8)) }

    /** Baca teks; bila berkas utama hilang/rusak, coba .bak lalu .tmp. [parse] melempar bila isi rusak. */
    fun <T> readWithFallback(target: File, parse: (String) -> T): T? {
        for (f in listOf(target, File(target.path + ".bak"), File(target.path + ".tmp"))) {
            if (!f.exists() || f.length() == 0L) continue
            val r = runCatching { parse(f.readText()) }.getOrNull()
            if (r != null) return r
        }
        return null
    }
}
