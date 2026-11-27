package id.rwi.scanpdfin.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import id.rwi.scanpdfin.image.ImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Satu PDF yang sedang dibuka: salinan kerja di cache (tanpa kata sandi) + asal usulnya. */
data class PdfDoc(
    val id: String = UUID.randomUUID().toString(),
    /** berkas kerja yang dirender & diedit (sudah dibuka kuncinya bila berkata sandi) */
    val file: File,
    /** URI asal (bila dibuka dari penyimpanan/app lain); null = PDF baru hasil alat */
    val sourceUri: Uri?,
    val name: String,
    val pageCount: Int,
    val hadPassword: Boolean = false,
    /** sandi yang dipakai membuka (hanya di memori), untuk mengunci lagi saat "Simpan (timpa)" */
    val password: String? = null,
    /** true bila ada perubahan yang belum disimpan */
    val dirty: Boolean = false,
)

class PdfOpenException(val needsPassword: Boolean, message: String) : Exception(message)

/** Anotasi di atas halaman, koordinat 0..1 relatif halaman (asal kiri-atas). */
sealed class Annot {
    abstract val page: Int
    abstract val id: String
    /** Gambar tempel (tanda tangan, teks, kotak penutup). [free] = ukuran bebas (tidak dikunci rasio), untuk kotak penutup. */
    data class Stamp(override val page: Int, val bitmap: Bitmap, val x: Float, val y: Float, val w: Float, val h: Float, val free: Boolean = false, override val id: String = UUID.randomUUID().toString()) : Annot()
    data class Text(override val page: Int, val text: String, val x: Float, val y: Float, val sizePt: Float, val color: Int, override val id: String = UUID.randomUUID().toString()) : Annot()
    data class Ink(override val page: Int, val points: List<Pair<Float, Float>>, val color: Int, val widthPt: Float, val alpha: Float = 1f, override val id: String = UUID.randomUUID().toString()) : Annot()
    /**
     * Ganti tulisan yang sudah ada: kotak [x,y,w,h] ditutup warna latar [bg], lalu [text] ditulis
     * dengan garis dasar di [baseline] (0..1 dari atas). [runKey] = tulisan asal yang diganti.
     */
    data class Replace(
        override val page: Int, val x: Float, val y: Float, val w: Float, val h: Float, val bg: Int,
        val text: String, val original: String, val baseline: Float, val sizePt: Float, val color: Int,
        val bold: Boolean, val serif: Boolean, val runKey: String, override val id: String = UUID.randomUUID().toString(),
    ) : Annot()
}

/** Sepotong tulisan asli di halaman (satu baris/kolom), koordinat 0..1 relatif halaman tampilan (asal kiri-atas). */
data class TextRun(
    val text: String, val x: Float, val y: Float, val w: Float, val h: Float,
    val baseline: Float, val sizePt: Float, val bold: Boolean, val serif: Boolean,
) {
    val key: String get() = "${x}_$baseline"
}

/**
 * Mesin PDF: buka (termasuk berkata sandi), render halaman (PdfRenderer), dan semua operasi (PdfBox).
 * Setiap operasi menghasilkan berkas kerja baru di cacheDir/pdf; berkas asal tidak disentuh sampai pengguna menyimpan.
 */
class PdfEngine(private val context: Context) {
    private val workDir = File(context.cacheDir, "pdf").apply { mkdirs() }
    private val renderMutex = Mutex()
    private var renderer: PdfRenderer? = null
    private var rendererFile: File? = null
    private var pfd: ParcelFileDescriptor? = null
    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    init { PDFBoxResourceLoader.init(context.applicationContext) }

    private fun newFile(prefix: String = "kerja") = File(workDir, "${prefix}_${UUID.randomUUID()}.pdf")

    fun cleanup(keep: File?) {
        workDir.listFiles()?.filter { it != keep && it.lastModified() < System.currentTimeMillis() - 2 * 3600_000 }?.forEach { it.delete() }
    }

    // ---------- buka ----------

    /** Salin URI ke berkas kerja. Bila berkata sandi dan [password] null → PdfOpenException(needsPassword=true). */
    suspend fun open(uri: Uri, displayName: String?, password: String?): PdfDoc = withContext(Dispatchers.IO) {
        val raw = newFile("asal")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { input.copyTo(it) } } ?: throw PdfOpenException(false, "Berkas tidak bisa dibaca")
            val d = openFile(raw, uri, displayName ?: queryName(uri) ?: "Dokumen.pdf", password)
            // salinan "asal" tidak dipakai lagi bila berkas kerja sudah dibuat terpisah
            if (d.file != raw) raw.delete()
            d
        } catch (e: Throwable) { raw.delete(); throw e }
    }

    suspend fun openFile(raw: File, sourceUri: Uri?, name: String, password: String?): PdfDoc = withContext(Dispatchers.IO) {
        val doc = try {
            PDDocument.load(raw, password ?: "")
        } catch (e: InvalidPasswordException) {
            throw PdfOpenException(true, if (password == null) "PDF berkata sandi" else "Kata sandi salah")
        } catch (e: Exception) {
            throw PdfOpenException(false, "PDF rusak atau bukan PDF: ${e.message ?: ""}")
        }
        doc.use { d ->
            val encrypted = d.isEncrypted
            val work = if (encrypted || !raw.name.startsWith("kerja")) {
                d.isAllSecurityToBeRemoved = true
                newFile().also { d.save(it) }
            } else raw
            PdfDoc(file = work, sourceUri = sourceUri, name = name.removeSuffix(".pdf").removeSuffix(".PDF"), pageCount = d.numberOfPages, hadPassword = encrypted)
        }
    }

    private fun queryName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }

    // ---------- render ----------

    private fun ensureRenderer(file: File): PdfRenderer {
        if (rendererFile == file && renderer != null) return renderer!!
        closeRendererLocked()
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd = fd
        renderer = PdfRenderer(fd)
        rendererFile = file
        return renderer!!
    }

    /** Hanya dipanggil saat memegang renderMutex. */
    private fun closeRendererLocked() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        renderer = null; pfd = null; rendererFile = null
    }

    /** Tutup renderer setelah render yang sedang berjalan selesai (tidak menutup di tengah render). */
    suspend fun closeRendererSafely() = renderMutex.withLock { withContext(Dispatchers.IO) { closeRendererLocked() } }

    /** Ukuran halaman dalam poin (lebar, tinggi) setelah rotasi. */
    suspend fun pageSize(file: File, index: Int): Pair<Float, Float> = renderMutex.withLock {
        withContext(Dispatchers.IO) {
            val r = ensureRenderer(file)
            r.openPage(index).use { p -> p.width.toFloat() to p.height.toFloat() }
        }
    }

    /** Render halaman selebar [widthPx] piksel (latar putih). Dicache di memori. */
    suspend fun render(file: File, index: Int, widthPx: Int): Bitmap? {
        val key = "${file.name}-$index-$widthPx"
        cache.get(key)?.let { return it }
        return renderMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val r = ensureRenderer(file)
                    r.openPage(index).use { p ->
                        val w = max(1, widthPx)
                        val h = max(1, (w * p.height / p.width.toFloat()).toInt())
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        cache.put(key, bmp)
                        bmp
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("ScanPdfin", "Render PDF gagal", t); null
                }
            }
        }
    }

    fun clearCache() = cache.evictAll()

    // ---------- operasi halaman (PdfBox) ----------

    private inline fun transform(src: File, prefix: String = "kerja", block: (PDDocument) -> Unit): File {
        val out = newFile(prefix)
        PDDocument.load(src).use { d -> block(d); d.save(out) }
        return out
    }

    suspend fun deletePages(src: File, indices: Set<Int>): File = withContext(Dispatchers.IO) {
        transform(src) { d -> indices.sortedDescending().forEach { if (it in 0 until d.numberOfPages) d.removePage(it) } }
    }

    suspend fun rotatePages(src: File, indices: Set<Int>, delta: Int): File = withContext(Dispatchers.IO) {
        transform(src) { d -> indices.forEach { i -> d.getPage(i).let { p -> p.rotation = ((p.rotation + delta) % 360 + 360) % 360 } } }
    }

    /** Susun ulang: [order] = urutan indeks lama. */
    suspend fun reorder(src: File, order: List<Int>): File = withContext(Dispatchers.IO) {
        // diubah di dokumen yang sama: bookmark, formulir isian, metadata, dan tautan tetap ada
        transform(src) { d ->
            val pages = (0 until d.numberOfPages).map { d.getPage(it) }
            setPageOrder(d, order.map { pages[it] })
        }
    }

    /** Ganti urutan halaman dokumen [d] menjadi [pages] (objek halaman yang sama, bukan salinan). */
    private fun setPageOrder(d: PDDocument, pages: List<PDPage>) {
        (0 until d.numberOfPages).map { d.getPage(it) }.forEach { d.pages.remove(it) }
        pages.forEach { d.pages.add(it) }
    }

    /** Ambil halaman terpilih menjadi PDF baru (pisah / ekstrak). */
    suspend fun extract(src: File, indices: List<Int>, prefix: String = "pisah"): File = withContext(Dispatchers.IO) {
        val out = newFile(prefix)
        PDDocument.load(src).use { d ->
            PDDocument().use { n ->
                indices.forEach { n.importPage(d.getPage(it)) }
                n.save(out)
            }
        }
        out
    }

    /** Sisipkan semua halaman [other] (PDF) setelah indeks [after] (-1 = di awal). */
    suspend fun insertPdf(src: File, other: File, after: Int): File = withContext(Dispatchers.IO) {
        val out = newFile()
        PDDocument.load(src).use { d ->
            PDDocument.load(other).use { o ->
                // halaman disisipkan ke dokumen yang sama agar struktur PDF asal (bookmark, formulir) tetap
                val own = (0 until d.numberOfPages).map { d.getPage(it) }
                val added = (0 until o.numberOfPages).map { d.importPage(o.getPage(it)) }
                val at = (after + 1).coerceIn(0, own.size)
                setPageOrder(d, own.take(at) + added + own.drop(at))
                d.save(out)
            }
        }
        out
    }

    /** Sisipkan gambar sebagai halaman (ukuran halaman mengikuti gambar, lebar A4). */
    suspend fun insertImages(src: File?, uris: List<Uri>, after: Int, jpegQuality: Int = 85): File = withContext(Dispatchers.IO) {
        val imgPdf = newFile("gambar")
        PDDocument().use { n ->
            uris.forEach { uri ->
                val bmp = ImageOps.decodeUri(context, uri, 3000) ?: return@forEach
                val w = 595.3f
                val page = PDPage(PDRectangle(w, w * bmp.height / bmp.width))
                n.addPage(page)
                val img = JPEGFactory.createFromImage(n, bmp, jpegQuality / 100f)
                PDPageContentStream(n, page).use { cs -> cs.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
                bmp.recycle()
            }
            n.save(imgPdf)
        }
        if (src == null) imgPdf else insertPdf(src, imgPdf, after).also { imgPdf.delete() }
    }

    suspend fun insertBlank(src: File, after: Int): File = withContext(Dispatchers.IO) {
        transform(src) { d ->
            val own = (0 until d.numberOfPages).map { d.getPage(it) }
            val ref = own[after.coerceIn(0, own.size - 1)].mediaBox
            val blank = PDPage(PDRectangle(ref.width, ref.height))
            val at = (after + 1).coerceIn(0, own.size)
            setPageOrder(d, own.take(at) + blank + own.drop(at))
        }
    }

    /** Gabung beberapa PDF (urutan daftar). Berkas berkata sandi harus sudah dibuka lebih dulu. */
    suspend fun merge(files: List<File>): File = withContext(Dispatchers.IO) {
        val out = newFile("gabung")
        val m = PDFMergerUtility()
        files.forEach { m.addSource(it) }
        m.destinationFileName = out.path
        m.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly())
        out
    }

    /** Kompres: tiap halaman dirender ulang menjadi JPEG (teks jadi gambar). */
    suspend fun compress(src: File, maxSide: Int, jpegQuality: Int, onProgress: (Int, Int) -> Unit): File = withContext(Dispatchers.IO) {
        val out = newFile("kompres")
        val n = PDDocument()
        try {
            val count = PDDocument.load(src).use { it.numberOfPages }
            for (i in 0 until count) {
                onProgress(i + 1, count)
                val (pw, ph) = pageSize(src, i)
                val widthPx = if (pw >= ph) maxSide else (maxSide * pw / ph).toInt()
                val bmp = render(src, i, widthPx) ?: throw java.io.IOException("Halaman ${i + 1} tidak bisa dibaca")
                val page = PDPage(PDRectangle(pw, ph))
                n.addPage(page)
                val img = JPEGFactory.createFromImage(n, bmp, jpegQuality / 100f)
                PDPageContentStream(n, page).use { cs -> cs.drawImage(img, 0f, 0f, pw, ph) }
            }
            n.save(out)
        } finally { n.close() }
        out
    }

    /**
     * Kompres "teks tetap": hanya gambar di dalam PDF yang dikecilkan (maks [dpi] relatif ukuran halaman)
     * dan dikodekan ulang ke JPEG [quality]. Teks, garis, dan font tidak disentuh, jadi tetap tajam & bisa dicari.
     * Gambar bertransparansi, hitam-putih 1-bit, atau yang tidak bisa dibaca dibiarkan. Gambar hanya diganti
     * bila hasilnya memang lebih kecil.
     */
    suspend fun compressImages(src: File, dpi: Int, quality: Int, onProgress: (Int, Int) -> Unit): File = withContext(Dispatchers.IO) {
        val out = newFile("kompres")
        PDDocument.load(src).use { d ->
            // gambar yang sama dipakai banyak halaman cukup diproses sekali
            val done = HashMap<com.tom_roush.pdfbox.cos.COSBase, com.tom_roush.pdfbox.pdmodel.graphics.PDXObject>()
            val visitedForms = HashSet<com.tom_roush.pdfbox.cos.COSBase>()
            fun shrink(res: com.tom_roush.pdfbox.pdmodel.PDResources?, maxW: Int, maxH: Int, depth: Int) {
                if (res == null || depth > 6) return
                for (name in res.xObjectNames.toList()) {
                    val x = runCatching { res.getXObject(name) }.getOrNull() ?: continue
                    val key = x.cosObject
                    when (x) {
                        is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject ->
                            if (visitedForms.add(key)) shrink(x.resources, maxW, maxH, depth + 1)
                        is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject -> {
                            val prev = done[key]
                            if (prev != null) { res.put(name, prev); continue }
                            val replaced = runCatching { recompress(d, x, maxW, maxH, quality) }.getOrNull()
                            if (replaced != null) { done[key] = replaced; res.put(name, replaced) } else done[key] = x
                        }
                    }
                }
            }
            val n = d.numberOfPages
            for (i in 0 until n) {
                onProgress(i + 1, n)
                val p = d.getPage(i)
                val box = p.cropBox
                val maxW = max(64, (box.width / 72f * dpi).toInt())
                val maxH = max(64, (box.height / 72f * dpi).toInt())
                // gambar bisa dipakai miring/diputar: batas sisi panjang = sisi panjang halaman
                val side = max(maxW, maxH)
                shrink(p.resources, side, side, 0)
            }
            d.save(out)
        }
        out
    }

    /** Gambar baru yang lebih kecil, atau null bila sebaiknya dibiarkan. */
    private fun recompress(d: PDDocument, img: com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject, maxW: Int, maxH: Int, quality: Int): com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject? {
        if (img.isStencil || img.bitsPerComponent <= 1) return null
        if (runCatching { img.softMask != null || img.mask != null }.getOrDefault(true)) return null
        val w = img.width; val h = img.height
        if (w < 48 || h < 48) return null
        val oldLen = (img.cosObject as? com.tom_roush.pdfbox.cos.COSStream)?.length ?: return null
        val scale = min(1f, min(maxW / w.toFloat(), maxH / h.toFloat()))
        // sudah JPEG dan tidak perlu diperkecil: kode ulang jarang menghemat, lewati
        if (scale >= 0.98f && img.suffix == "jpg" && quality >= 75) return null
        // baca dengan subsampling agar gambar raksasa tidak menghabiskan memori
        val sub = max(1, (1f / scale).toInt())
        val raw = img.getImage(null, sub) ?: return null
        val tw = max(1, (w * scale).toInt()); val th = max(1, (h * scale).toInt())
        val bmp = if (raw.width > tw * 1.05f || raw.height > th * 1.05f) Bitmap.createScaledBitmap(raw, tw, th, true).also { if (it !== raw) raw.recycle() } else raw
        // JPEG tidak punya transparansi: ratakan ke latar putih
        val flat = if (bmp.hasAlpha()) {
            Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888).also { c ->
                Canvas(c).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
                bmp.recycle()
            }
        } else bmp
        val next = JPEGFactory.createFromImage(d, flat, quality / 100f)
        flat.recycle()
        val newLen = (next.cosObject as? com.tom_roush.pdfbox.cos.COSStream)?.length ?: return null
        return if (newLen < oldLen * 0.9) next else null
    }

    suspend fun protect(src: File, userPassword: String, ownerPassword: String = userPassword): File = withContext(Dispatchers.IO) {
        val out = newFile("kunci")
        PDDocument.load(src).use { d ->
            val ap = AccessPermission()
            val policy = StandardProtectionPolicy(ownerPassword, userPassword, ap).apply { encryptionKeyLength = 256; permissions = ap }
            d.protect(policy)
            d.save(out)
        }
        out
    }

    // ---------- tulisan asli (untuk Ubah tulisan) ----------

    /**
     * Baca tulisan di satu halaman dan pecah per baris/kolom. Halaman hasil scan (gambar) tidak punya
     * lapisan tulisan → daftar kosong.
     */
    suspend fun textRuns(file: File, index: Int): List<TextRun> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(file).use { d ->
                val page = d.getPage(index)
                val rot = ((page.rotation % 360) + 360) % 360
                val crop = page.cropBox
                val vw = if (rot == 90 || rot == 270) crop.height else crop.width
                val vh = if (rot == 90 || rot == 270) crop.width else crop.height
                val runs = mutableListOf<TextRun>()
                val stripper = object : PDFTextStripper() {
                    override fun writeString(text: String?, positions: MutableList<TextPosition>?) {
                        val list = positions?.filter { (rot != 0 || it.dir == 0f) && !it.unicode.isNullOrEmpty() } ?: return
                        var cur = mutableListOf<TextPosition>()
                        fun flush() {
                            if (cur.isNotEmpty()) makeRun(cur, vw, vh)?.let { runs += it }
                            cur = mutableListOf()
                        }
                        for (tp in list) {
                            val prev = cur.lastOrNull()
                            if (prev != null) {
                                val size = fontSize(prev)
                                val gap = tp.x - (prev.x + prev.width)
                                if (gap > size * 1.6f || gap < -size * 0.6f || abs(tp.y - prev.y) > size * 0.5f) flush()
                            }
                            cur += tp
                        }
                        flush()
                    }
                }
                stripper.sortByPosition = true
                stripper.startPage = index + 1
                stripper.endPage = index + 1
                stripper.getText(d)
                runs
            }
        }.getOrElse { android.util.Log.w("ScanPdfin", "Baca tulisan gagal", it); emptyList() }
    }

    private fun fontSize(tp: TextPosition): Float {
        val s = tp.fontSizeInPt
        return if (s >= 2f) s else max(4f, tp.heightDir / 0.7f)
    }

    private fun makeRun(list: List<TextPosition>, vw: Float, vh: Float): TextRun? {
        val sb = StringBuilder()
        var prev: TextPosition? = null
        for (tp in list) {
            val p = prev
            if (p != null) {
                val gap = tp.x - (p.x + p.width)
                if (gap > fontSize(p) * 0.18f && !sb.endsWith(" ") && tp.unicode != " ") sb.append(' ')
            }
            sb.append(tp.unicode)
            prev = tp
        }
        val text = sb.toString().trim()
        if (text.isEmpty()) return null
        val size = (list.maxOf { fontSize(it) } * 2).roundToInt() / 2f
        val x0 = list.minOf { it.x }
        val x1 = list.maxOf { it.x + it.width }
        val base = list.map { it.y }.sorted()[list.size / 2]
        val top = base - size * 0.80f
        val bottom = base + size * 0.24f
        val fontName = list.firstNotNullOfOrNull { runCatching { it.font?.name }.getOrNull() }?.lowercase() ?: ""
        val bold = listOf("bold", "black", "heavy", "semibold", "demi").any { it in fontName }
        val serif = !("sans" in fontName) && listOf("times", "serif", "roman", "georgia", "garamond", "cambria", "bookman", "minion").any { it in fontName }
        return TextRun(
            text = text, x = (x0 / vw).coerceIn(0f, 1f), y = (top / vh).coerceIn(0f, 1f),
            w = ((x1 - x0) / vw).coerceIn(0.002f, 1f), h = ((bottom - top) / vh).coerceIn(0.002f, 1f),
            baseline = (base / vh).coerceIn(0f, 1f), sizePt = size, bold = bold, serif = serif,
        )
    }

    private fun replaceFont(bold: Boolean, serif: Boolean): PDFont = when {
        serif && bold -> PDType1Font.TIMES_BOLD
        serif -> PDType1Font.TIMES_ROMAN
        bold -> PDType1Font.HELVETICA_BOLD
        else -> PDType1Font.HELVETICA
    }

    /** Buang huruf yang tidak ada di huruf standar PDF (mis. emoji) agar penulisan tidak gagal. */
    private fun safeText(font: PDFont, s: String): String =
        s.map { c -> if (runCatching { font.encode(c.toString()) }.isSuccess) c else if (c == '’' || c == '‘') '\'' else '?' }.joinToString("")

    /** Ratakan anotasi ke isi halaman. */
    suspend fun flatten(src: File, annots: List<Annot>): File = withContext(Dispatchers.IO) {
        if (annots.isEmpty()) return@withContext src
        transform(src) { d ->
            annots.groupBy { it.page }.forEach { (pi, list) ->
                val page = d.getPage(pi)
                // cropBox = bagian yang tampil di layar (dirender PdfRenderer); biasanya sama dengan mediaBox
                val box = page.cropBox
                val rot = ((page.rotation % 360) + 360) % 360
                // ukuran halaman seperti yang dilihat pengguna (setelah rotasi)
                val vw = if (rot == 90 || rot == 270) box.height else box.width
                val vh = if (rot == 90 || rot == 270) box.width else box.height
                PDPageContentStream(d, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    // transformasi: koordinat tampilan (0..vw, 0..vh, asal kiri-bawah) → ruang halaman asli
                    // ruang tampilan → ruang halaman: geser ke pojok mediaBox, lalu putar balik rotasi halaman
                    cs.transform(com.tom_roush.pdfbox.util.Matrix(1f, 0f, 0f, 1f, box.lowerLeftX, box.lowerLeftY))
                    when (rot) {
                        90 -> cs.transform(com.tom_roush.pdfbox.util.Matrix(0f, 1f, -1f, 0f, box.width, 0f))
                        180 -> cs.transform(com.tom_roush.pdfbox.util.Matrix(-1f, 0f, 0f, -1f, box.width, box.height))
                        270 -> cs.transform(com.tom_roush.pdfbox.util.Matrix(0f, -1f, 1f, 0f, 0f, box.height))
                    }
                    list.forEach { a ->
                        when (a) {
                            is Annot.Stamp -> {
                                val img = LosslessFactory.createFromImage(d, a.bitmap)
                                cs.drawImage(img, a.x * vw, vh - (a.y + a.h) * vh, a.w * vw, a.h * vh)
                            }
                            is Annot.Text -> {
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, a.sizePt)
                                cs.setNonStrokingColor(Color.red(a.color), Color.green(a.color), Color.blue(a.color))
                                cs.newLineAtOffset(a.x * vw, vh - a.y * vh - a.sizePt)
                                a.text.split('\n').forEachIndexed { li, line ->
                                    if (li > 0) cs.newLineAtOffset(0f, -a.sizePt * 1.2f)
                                    cs.showText(line.filter { it.code < 256 })
                                }
                                cs.endText()
                            }
                            is Annot.Ink -> {
                                if (a.points.size < 2) return@forEach
                                if (a.alpha < 1f) {
                                    val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = a.alpha }
                                    cs.setGraphicsStateParameters(gs)
                                }
                                cs.setStrokingColor(Color.red(a.color), Color.green(a.color), Color.blue(a.color))
                                cs.setLineWidth(a.widthPt)
                                cs.setLineCapStyle(1); cs.setLineJoinStyle(1)
                                val p0 = a.points[0]
                                cs.moveTo(p0.first * vw, vh - p0.second * vh)
                                a.points.drop(1).forEach { (x, y) -> cs.lineTo(x * vw, vh - y * vh) }
                                cs.stroke()
                                if (a.alpha < 1f) cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { strokingAlphaConstant = 1f })
                            }
                            is Annot.Replace -> {
                                cs.setNonStrokingColor(Color.red(a.bg), Color.green(a.bg), Color.blue(a.bg))
                                cs.addRect(a.x * vw, vh - (a.y + a.h) * vh, a.w * vw, a.h * vh)
                                cs.fill()
                                val line = a.text.replace('\n', ' ')
                                if (line.isNotBlank()) {
                                    val font = replaceFont(a.bold, a.serif)
                                    cs.beginText()
                                    cs.setFont(font, a.sizePt)
                                    cs.setNonStrokingColor(Color.red(a.color), Color.green(a.color), Color.blue(a.color))
                                    cs.newLineAtOffset(a.x * vw, vh - a.baseline * vh)
                                    cs.showText(safeText(font, line))
                                    cs.endText()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Tulis berkas kerja ke URI tujuan. */
    suspend fun writeTo(file: File, target: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(target, "wt")?.use { os -> file.inputStream().use { it.copyTo(os) } } != null
        } catch (_: Exception) { false }
    }

    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) { runCatching { PDDocument.load(file).use { it.numberOfPages } }.getOrDefault(0) }

    /** Info ringkas berkas. */
    suspend fun info(file: File): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(file).use { d ->
                val i = d.documentInformation
                val (w, h) = d.getPage(0).mediaBox.let { it.width to it.height }
                linkedMapOf(
                    "Halaman" to d.numberOfPages.toString(),
                    "Ukuran halaman 1" to "${(w / 72 * 25.4).toInt()} × ${(h / 72 * 25.4).toInt()} mm",
                    "Judul" to (i.title ?: "-"),
                    "Pembuat" to (i.creator ?: i.producer ?: "-"),
                    "Ukuran berkas" to "${file.length() / 1024} KB",
                )
            }
        }.getOrDefault(linkedMapOf("Info" to "tidak terbaca"))
    }

    @Suppress("unused")
    private fun unused() = min(0, 1)
}
