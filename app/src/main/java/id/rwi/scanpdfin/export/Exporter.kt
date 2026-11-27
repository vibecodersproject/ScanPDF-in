package id.rwi.scanpdfin.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import id.rwi.scanpdfin.data.FilterMode
import id.rwi.scanpdfin.data.Names
import id.rwi.scanpdfin.data.Page
import id.rwi.scanpdfin.data.Paper
import id.rwi.scanpdfin.data.Quality
import id.rwi.scanpdfin.data.Repository
import id.rwi.scanpdfin.image.ImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.math.min

/** Ekspor halaman menjadi PDF / JPG di cacheDir/ekspor, lalu bagikan atau simpan. */
object Exporter {

    private fun exportDir(context: Context) = File(context.cacheDir, "ekspor").apply { mkdirs() }

    /** Bersihkan hasil ekspor lama (>1 hari). */
    fun cleanup(context: Context) {
        val cutoff = System.currentTimeMillis() - 24L * 3600 * 1000
        exportDir(context).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun unique(dir: File, base: String, ext: String): File {
        var f = File(dir, "$base.$ext")
        var i = 2
        while (f.exists()) { f = File(dir, "$base ($i).$ext"); i++ }
        return f
    }

    /**
     * Buat PDF. Gambar dibatasi ukurannya & dikompres JPEG sesuai kualitas (berkas kecil, tetap tajam).
     * Orientasi halaman mengikuti gambar (lanskap → halaman lanskap).
     */
    suspend fun pdf(
        context: Context,
        repo: Repository,
        pages: List<Page>,
        title: String,
        paper: Paper,
        quality: Quality,
        modeOverride: FilterMode? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val out = unique(exportDir(context), Names.safeFileName(title), "pdf")
        val docTitle = title
        // gambar halaman ditampung di berkas sementara, bukan memori: PDF ratusan halaman tidak membuat HP kehabisan memori
        PDDocument(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)).use { doc ->
            doc.documentInformation.apply {
                setTitle(docTitle)
                creator = "Scan dan PDF-in"
                producer = "Scan dan PDF-in"
                creationDate = Calendar.getInstance()
            }
            pages.forEachIndexed { i, page ->
                onProgress(i + 1, pages.size)
                // halaman yang gagal dibaca tidak dilewati diam-diam: PDF yang kurang halaman lebih berbahaya daripada gagal
                val bmp = repo.render(page, quality.maxSide, modeOverride ?: page.filter)
                    ?: throw java.io.IOException("Foto halaman ${i + 1} tidak bisa dibaca")
                val landscape = bmp.width > bmp.height
                val rect = if (paper == Paper.GAMBAR) {
                    // lebar A4, tinggi mengikuti rasio gambar
                    val w = 595.3f
                    PDRectangle(w, w * bmp.height / bmp.width)
                } else if (landscape) PDRectangle(paper.heightPt, paper.widthPt) else PDRectangle(paper.widthPt, paper.heightPt)
                val pdPage = PDPage(rect)
                doc.addPage(pdPage)
                val image = JPEGFactory.createFromImage(doc, bmp, quality.jpeg / 100f)
                val scale = min(rect.width / bmp.width, rect.height / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val x = (rect.width - dw) / 2f
                val y = (rect.height - dh) / 2f
                PDPageContentStream(doc, pdPage).use { cs -> cs.drawImage(image, x, y, dw, dh) }
                bmp.recycle()
            }
            doc.save(out)
        }
        out
    }

    /** Simpan tiap halaman sebagai JPG. */
    suspend fun jpgs(
        context: Context,
        repo: Repository,
        pages: List<Page>,
        title: String,
        quality: Quality,
        modeOverride: FilterMode? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<File> = withContext(Dispatchers.IO) {
        val dir = exportDir(context)
        val base = Names.safeFileName(title)
        pages.mapIndexedNotNull { i, page ->
            onProgress(i + 1, pages.size)
            val bmp = repo.render(page, quality.maxSide, modeOverride ?: page.filter)
                ?: throw java.io.IOException("Foto halaman ${i + 1} tidak bisa dibaca")
            val f = unique(dir, if (pages.size == 1) base else "$base - ${i + 1}", "jpg")
            ImageOps.saveJpeg(bmp, f, quality.jpeg.coerceAtLeast(70))
            bmp.recycle()
            f
        }
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun shareIntent(context: Context, files: List<File>, mime: String, subject: String): Intent {
        val uris = files.map { uriFor(context, it) }
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
        intent.type = mime
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.clipData = ClipData.newUri(context.contentResolver, subject, uris[0]).also { cd -> uris.drop(1).forEach { cd.addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, "Bagikan $subject")
    }

    /** Salin berkas ke URI tujuan yang dipilih pengguna (pemilih berkas sistem). */
    suspend fun copyTo(context: Context, file: File, target: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(target, "wt")?.use { os -> file.inputStream().use { it.copyTo(os) } } != null
        } catch (_: Exception) { false }
    }
}
