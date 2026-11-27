package id.rwi.scanpdfin

import android.content.Intent
import android.net.Uri
import id.rwi.scanpdfin.data.Names
import id.rwi.scanpdfin.export.Exporter
import id.rwi.scanpdfin.pdf.Annot
import id.rwi.scanpdfin.pdf.PdfDoc
import id.rwi.scanpdfin.pdf.PdfEngine
import id.rwi.scanpdfin.pdf.PdfOpenException
import id.rwi.scanpdfin.pdf.PdfStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Permintaan kata sandi untuk PDF terkunci. */
data class PasswordAsk(val uri: Uri, val name: String?, val wrong: Boolean)

/** Tingkat kecilkan PDF. Ringan & Sedang menjaga teks (hanya gambar di dalamnya yang dikecilkan). */
enum class CompressLevel(val label: String, val desc: String) {
    RINGAN("Ringan", "Teks tetap tajam & bisa dicari. Gambar di dalam PDF dikecilkan sedikit."),
    SEDANG("Sedang", "Teks tetap tajam & bisa dicari. Gambar dikecilkan lebih banyak, cocok untuk dikirim lewat WA/email."),
    KUAT("Kuat", "Paling kecil untuk PDF hasil scan/foto. Halaman jadi gambar: teks tidak bisa dipilih atau dicari lagi."),
}

/** Satu berkas di layar Kecilkan PDF. */
data class CompressItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val work: File,
    val pages: Int,
    val before: Long,
    val after: Long? = null,
    val out: File? = null,
    val note: String? = null,
)

/**
 * Menu PDF: buka, baca, operasi halaman, tanda tangan/teks/coretan, gabung, simpan & bagikan.
 * Setiap perubahan menghasilkan berkas kerja baru; tumpukan "urungkan" menyimpan versi sebelumnya.
 */
class PdfController(private val vm: AppViewModel, val engine: PdfEngine, val store: PdfStore) {
    private val _doc = MutableStateFlow<PdfDoc?>(null)
    val doc = _doc.asStateFlow()
    private val undoStack = ArrayDeque<PdfDoc>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _password = MutableStateFlow<PasswordAsk?>(null)
    val password = _password.asStateFlow()

    /** Daftar berkas untuk digabung (sudah disalin & dibuka kuncinya). */
    private val _mergeList = MutableStateFlow<List<PdfDoc>>(emptyList())
    val mergeList = _mergeList.asStateFlow()

    /** posisi sisip untuk permintaan pilih berkas yang sedang berjalan */
    private var insertAfter = -1

    val recent get() = store.recent
    val signatures get() = store.signatures

    // ---------- buka ----------

    fun pickToOpen() = vm.send(Request.PickPdf(PickPurpose.OPEN))

    /** Dipanggil Activity setelah pengguna memilih berkas / membuka dari app lain. */
    fun onPicked(purpose: PickPurpose, uris: List<Uri>) {
        if (uris.isEmpty()) return
        when (purpose) {
            PickPurpose.OPEN -> open(uris.first(), null, null)
            PickPurpose.MERGE -> addToMerge(uris)
            PickPurpose.INSERT_PDF -> insertPdf(uris.first())
            PickPurpose.IMAGES_NEW -> imagesToNewPdf(uris)
            PickPurpose.IMAGES_INSERT -> insertImages(uris)
            PickPurpose.COMPRESS -> addToCompress(uris)
        }
    }

    // ---------- Kecilkan PDF (menu sendiri) ----------

    private val _compressList = MutableStateFlow<List<CompressItem>>(emptyList())
    val compressList = _compressList.asStateFlow()

    fun startCompress() { vm.go(Screen.Compress); if (_compressList.value.isEmpty()) pickCompress() }
    fun pickCompress() = vm.send(Request.PickPdf(PickPurpose.COMPRESS, multiple = true))
    fun removeCompress(id: String) { _compressList.value = _compressList.value.filterNot { it.id == id } }
    fun clearCompress() { _compressList.value = emptyList() }

    private fun addToCompress(uris: List<Uri>) {
        vm.scope.launch {
            var locked = 0; var broken = 0
            val added = mutableListOf<CompressItem>()
            try {
                uris.forEachIndexed { i, u ->
                    vm.setBusy(Busy("Membaca PDF ${i + 1} dari ${uris.size}…", (i + 1) / uris.size.toFloat()))
                    try {
                        val d = engine.open(u, null, null)
                        added += CompressItem(name = d.name, work = d.file, pages = d.pageCount, before = d.file.length())
                    } catch (e: PdfOpenException) { if (e.needsPassword) locked++ else broken++ } catch (_: Exception) { broken++ }
                }
            } finally { vm.setBusy(null) }
            _compressList.value = _compressList.value + added
            when {
                locked > 0 -> vm.toast("$locked PDF berkata sandi dilewati. Buka lewat menu PDF, lalu ⋮ → Kecilkan ukuran.")
                broken > 0 -> vm.toast("$broken berkas bukan PDF atau rusak, dilewati")
            }
        }
    }

    /** Kecilkan semua berkas di daftar. Hasil yang tidak lebih kecil tidak dipakai (berkas asli tetap). */
    fun runCompress(level: CompressLevel) {
        val items = _compressList.value
        if (items.isEmpty()) { vm.toast("Pilih PDF dulu"); return }
        vm.scope.launch {
            val total = items.size
            val result = mutableListOf<CompressItem>()
            try {
                items.forEachIndexed { idx, it ->
                    val label = if (total > 1) "Berkas ${idx + 1} dari $total: " else ""
                    try {
                        val out = compressFile(it.work, level) { i, n -> vm.setBusy(Busy("${label}mengecilkan halaman $i dari $n", i / n.toFloat())) }
                        val ctx = vm.getApplication<android.app.Application>()
                        val dir = File(ctx.cacheDir, "ekspor").apply { mkdirs() }
                        if (out.length() < it.before) {
                            val named = File(dir, Names.safeFileName(it.name + " (kecil)") + ".pdf")
                            out.copyTo(named, overwrite = true); out.delete()
                            result += it.copy(after = named.length(), out = named, note = null)
                        } else {
                            out.delete()
                            result += it.copy(after = it.before, out = null, note = "Sudah kecil, tidak bisa diperkecil lagi dengan tingkat ${level.label}")
                        }
                    } catch (e: Throwable) {
                        android.util.Log.w("ScanPdfin", "Kecilkan gagal", e)
                        result += it.copy(after = null, out = null, note = if (e is OutOfMemoryError) "Memori HP tidak cukup untuk berkas ini" else "Gagal: ${e.message ?: "kesalahan tak dikenal"}")
                    }
                }
            } finally { vm.setBusy(null) }
            _compressList.value = result
            val saved = result.sumOf { r -> if (r.out != null) r.before - (r.after ?: r.before) else 0L }
            vm.toast(if (saved > 0) "Hemat ${id.rwi.scanpdfin.ui.formatBytes(saved)}" else "Tidak ada yang bisa diperkecil")
        }
    }

    private suspend fun compressFile(src: File, level: CompressLevel, onProgress: (Int, Int) -> Unit): File = when (level) {
        CompressLevel.RINGAN -> engine.compressImages(src, 150, 80, onProgress)
        CompressLevel.SEDANG -> engine.compressImages(src, 110, 62, onProgress)
        CompressLevel.KUAT -> engine.compress(src, 1300, 55, onProgress)
    }

    fun saveCompressed(item: CompressItem) { item.out?.let { vm.send(Request.SaveFile(it, "application/pdf")) } }
    fun shareCompressed(items: List<CompressItem>) {
        val files = items.mapNotNull { it.out }
        if (files.isEmpty()) { vm.toast("Belum ada hasil untuk dibagikan"); return }
        vm.send(Request.Share(files, "application/pdf", files.first().nameWithoutExtension))
    }
    /** Lihat hasil di pembaca (untuk memeriksa kualitas). */
    fun openCompressed(item: CompressItem) {
        val f = item.out ?: return
        vm.scope.launch {
            try {
                val d = engine.openFile(f, null, f.nameWithoutExtension, null)
                setDoc(d.copy(dirty = false), clearUndo = true)
                vm.go(Screen.PdfReader(0))
            } catch (e: Exception) { vm.toast("Gagal membuka: ${e.message ?: ""}") }
        }
    }

    /** PDF yang ingin dibuka saat PDF lain masih punya perubahan belum disimpan (menunggu jawaban pengguna). */
    private val _pendingOpen = MutableStateFlow<Uri?>(null)
    val pendingOpen = _pendingOpen.asStateFlow()

    /** Ada perubahan yang bisa hilang bila PDF lain dibuka sekarang? */
    private fun hasUnsaved(): Boolean = _doc.value?.dirty == true || vm.stack.value.any { it is Screen.PdfEdit }

    fun confirmPendingOpen(discard: Boolean) {
        val u = _pendingOpen.value ?: return
        _pendingOpen.value = null
        if (discard) open(u, null, null, force = true) else saveAs()
    }
    fun cancelPendingOpen() { _pendingOpen.value = null }

    fun open(uri: Uri, name: String?, password: String?, fromRecent: Boolean = false, force: Boolean = false) {
        if (!force && password == null && hasUnsaved()) { _pendingOpen.value = uri; return }
        vm.scope.launch {
            vm.setBusy(Busy("Membuka PDF…"))
            try {
                takePermission(uri)
                val d = engine.open(uri, name, password).copy(password = password)
                _password.value = null
                setDoc(d, clearUndo = true)
                store.touch(uri, d.name, d.pageCount)
                if (d.hadPassword) vm.toast("PDF bersandi dibuka. Simpan (timpa) akan mengunci lagi dengan sandi yang sama.")
                vm.popWhile { it.isPdfWork() }
                if (vm.screen != Screen.PdfHome) vm.go(Screen.PdfHome)
                vm.go(Screen.PdfReader())
            } catch (e: PdfOpenException) {
                if (e.needsPassword) _password.value = PasswordAsk(uri, name, wrong = password != null)
                else vm.toast(e.message ?: "PDF tidak bisa dibuka")
            } catch (e: SecurityException) {
                if (fromRecent) { store.remove(uri.toString()); vm.toast("Izin berkas sudah hilang, pilih ulang lewat Buka PDF") }
                else vm.toast("Tidak diizinkan membaca berkas ini")
            } catch (e: Exception) {
                vm.toast("PDF tidak bisa dibuka: ${e.message ?: ""}")
            } finally { vm.setBusy(null) }
        }
    }

    fun cancelPassword() { _password.value = null }

    private fun takePermission(uri: Uri) {
        val cr = vm.getApplication<android.app.Application>().contentResolver
        runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .recoverCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun Screen.isPdfWork() = this is Screen.PdfReader || this == Screen.PdfPages || this is Screen.PdfEdit

    /** Halaman terakhir yang dibaca (id dokumen → halaman), agar kembali dari Edit/Halaman tidak lompat ke awal. */
    var readerPage: Pair<String, Int>? = null

    private fun setDoc(d: PdfDoc, clearUndo: Boolean = false) {
        // dokumen baru menggantikan yang lama: berkas kerja lama tidak dipakai lagi
        if (clearUndo) releaseWorkFiles(keep = d.file)
        _doc.value = d
        _canUndo.value = undoStack.isNotEmpty()
        engine.clearCache()
    }

    /** Tutup PDF (setelah konfirmasi bila ada perubahan). */
    fun close() {
        releaseWorkFiles()
        _doc.value = null
        _canUndo.value = false
        vm.scope.launch { engine.closeRendererSafely() }
        vm.popWhile { it.isPdfWork() }
    }

    /** Hapus berkas kerja PDF yang ditinggalkan (dokumen + riwayat urungkan), kecuali yang masih dipakai daftar lain. */
    private fun releaseWorkFiles(keep: File? = null) {
        val inUse = (_mergeList.value.map { it.file } + _compressList.value.flatMap { listOfNotNull(it.work, it.out) } + listOfNotNull(keep)).toSet()
        val old = (listOfNotNull(_doc.value) + undoStack).map { it.file }.filter { it !in inUse }
        undoStack.clear()
        if (old.isNotEmpty()) vm.scope.launch(Dispatchers.IO) { old.forEach { runCatching { it.delete() } } }
    }

    private fun pushUndo(d: PdfDoc) {
        undoStack.addLast(d)
        while (undoStack.size > 15) {
            val gone = undoStack.removeFirst()
            if (undoStack.none { it.file == gone.file } && _doc.value?.file != gone.file) vm.scope.launch(Dispatchers.IO) { runCatching { gone.file.delete() } }
        }
    }

    // ---------- operasi (semua lewat apply) ----------

    private fun apply(label: String, op: suspend (File) -> File, pageCountAfter: (suspend (File) -> Int)? = null, after: (PdfDoc) -> Unit = {}) {
        val cur = _doc.value ?: return
        // penjaga ketukan dobel: satu operasi pada satu waktu
        if (vm.isBusy) return
        vm.setBusy(Busy(label))
        vm.scope.launch {
            try {
                val out = op(cur.file)
                val n = pageCountAfter?.invoke(out) ?: engine.pageCount(out)
                if (n <= 0) { out.delete(); vm.toast("PDF tidak boleh kosong"); return@launch }
                pushUndo(cur)
                val next = cur.copy(file = out, pageCount = n, dirty = true)
                setDoc(next)
                after(next)
            } catch (e: Throwable) {
                android.util.Log.w("ScanPdfin", "Operasi PDF gagal", e)
                vm.toast(if (e is OutOfMemoryError) "Memori HP tidak cukup untuk operasi ini" else "Gagal: ${e.message ?: "kesalahan tak dikenal"}")
            } finally { vm.setBusy(null) }
        }
    }

    fun undo() {
        if (vm.isBusy) return
        val cur = _doc.value ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        // nama & tujuan simpan terakhir dipertahankan; hanya isi PDF yang dikembalikan
        setDoc(prev.copy(sourceUri = cur.sourceUri, name = cur.name, password = cur.password, dirty = true))
        vm.toast("Perubahan terakhir dibatalkan")
    }

    fun deletePages(indices: Set<Int>) {
        val d = _doc.value ?: return
        if (indices.size >= d.pageCount) { vm.toast("Minimal harus tersisa 1 halaman"); return }
        apply("Menghapus halaman…", { engine.deletePages(it, indices) }) { vm.toast(if (indices.size == 1) "1 halaman dihapus" else "${indices.size} halaman dihapus") }
    }

    fun rotatePages(indices: Set<Int>, delta: Int) = apply("Memutar halaman…", { engine.rotatePages(it, indices, delta) })

    fun movePage(from: Int, to: Int) {
        val d = _doc.value ?: return
        if (from == to || to !in 0 until d.pageCount) return
        val order = (0 until d.pageCount).toMutableList().apply { add(to, removeAt(from)) }
        apply("Menyusun halaman…", { engine.reorder(it, order) })
    }

    fun insertBlank(after: Int) = apply("Menambah halaman kosong…", { engine.insertBlank(it, after) }) { vm.toast("Halaman kosong ditambahkan") }

    fun pickInsertPdf(after: Int) { insertAfter = after; vm.send(Request.PickPdf(PickPurpose.INSERT_PDF)) }
    fun pickInsertImages(after: Int) { insertAfter = after; vm.send(Request.PickImagesFor(PickPurpose.IMAGES_INSERT)) }

    private fun insertPdf(uri: Uri) {
        val at = insertAfter
        vm.scope.launch {
            vm.setBusy(Busy("Membaca PDF…"))
            val other = try { engine.open(uri, null, null) } catch (e: PdfOpenException) {
                vm.setBusy(null)
                vm.toast(if (e.needsPassword) "PDF itu berkata sandi; buka dulu lewat Buka PDF lalu simpan tanpa sandi" else (e.message ?: "Gagal")); return@launch
            } catch (e: Exception) {
                vm.setBusy(null)
                vm.toast("Berkas tidak bisa dibaca: ${e.message ?: ""}"); return@launch
            }
            vm.setBusy(null)
            apply("Menyisipkan PDF…", { engine.insertPdf(it, other.file, at) }) { vm.toast("${other.pageCount} halaman disisipkan") }
        }
    }

    private fun insertImages(uris: List<Uri>) {
        val at = insertAfter
        apply("Menyisipkan gambar…", { engine.insertImages(it, uris, at) }) { vm.toast("${uris.size} gambar disisipkan") }
    }

    /** Sisipkan dokumen hasil Scan (seluruh halamannya) setelah [after]. */
    fun insertScanDoc(docId: String, after: Int) {
        val sd = vm.repo.document(docId) ?: return
        val ctx = vm.getApplication<android.app.Application>()
        apply("Menyisipkan hasil Scan…", { src ->
            val tmp = Exporter.pdf(ctx, vm.repo, sd.pages, sd.name, vm.settings.value.paper, vm.settings.value.quality)
            engine.insertPdf(src, tmp, after).also { tmp.delete() }
        }) { vm.toast("${sd.pages.size} halaman Scan disisipkan") }
    }

    /** Tanda tangan / teks / coretan → ditanam permanen ke halaman. */
    fun flatten(annots: List<Annot>, onDone: () -> Unit = {}) {
        if (annots.isEmpty()) { onDone(); return }
        apply("Menempelkan ke halaman…", { engine.flatten(it, annots) }) { onDone() }
    }

    // ---------- PDF baru dari alat ----------

    private fun imagesToNewPdf(uris: List<Uri>) {
        vm.scope.launch {
            vm.setBusy(Busy("Membuat PDF dari ${uris.size} gambar…"))
            try {
                val f = engine.insertImages(null, uris, -1)
                val n = engine.pageCount(f)
                if (n <= 0) { f.delete(); vm.toast("Gambar tidak bisa dibaca"); return@launch }
                if (n < uris.size) vm.toast("${uris.size - n} gambar tidak bisa dibaca, dilewati")
                setDoc(PdfDoc(file = f, sourceUri = null, name = Names.defaultDocumentName().replace("Scan", "Gambar"), pageCount = n, dirty = true), clearUndo = true)
                vm.popWhile { it.isPdfWork() }
                vm.go(Screen.PdfReader())
            } catch (e: Exception) { vm.toast("Gagal membuat PDF: ${e.message ?: ""}") } finally { vm.setBusy(null) }
        }
    }

    fun pickImagesNew() = vm.send(Request.PickImagesFor(PickPurpose.IMAGES_NEW))

    /** Hasil Scan → buka di menu PDF (untuk tanda tangan dll.). */
    fun openScanDoc(docId: String) {
        val sd = vm.repo.document(docId) ?: return
        val ctx = vm.getApplication<android.app.Application>()
        vm.scope.launch {
            vm.setBusy(Busy("Menyiapkan PDF…"))
            try {
                val tmp = Exporter.pdf(ctx, vm.repo, sd.pages, sd.name, vm.settings.value.paper, vm.settings.value.quality)
                val d = engine.openFile(tmp, null, sd.name, null)
                setDoc(d.copy(dirty = true), clearUndo = true)
                vm.go(Screen.PdfReader())
            } catch (e: Exception) { vm.toast("Gagal: ${e.message ?: ""}") } finally { vm.setBusy(null) }
        }
    }

    // ---------- gabung ----------

    fun startMerge() { _mergeList.value = emptyList(); vm.go(Screen.PdfMerge); pickMerge() }
    fun pickMerge() = vm.send(Request.PickPdf(PickPurpose.MERGE, multiple = true))

    private fun addToMerge(uris: List<Uri>) {
        vm.scope.launch {
            vm.setBusy(Busy("Membaca ${uris.size} PDF…"))
            val added = mutableListOf<PdfDoc>()
            var locked = 0; var broken = 0
            try {
                for (u in uris) {
                    try { added += engine.open(u, null, null) } catch (e: PdfOpenException) { if (e.needsPassword) locked++ else broken++ } catch (_: Exception) { broken++ }
                }
            } finally { vm.setBusy(null) }
            _mergeList.value = _mergeList.value + added
            if (locked > 0) vm.toast("$locked PDF berkata sandi dilewati")
            else if (broken > 0) vm.toast("$broken berkas tidak bisa dibaca, dilewati")
        }
    }

    fun moveMerge(from: Int, to: Int) {
        val l = _mergeList.value
        if (to !in l.indices) return
        _mergeList.value = l.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun removeMerge(i: Int) { _mergeList.value = _mergeList.value.filterIndexed { idx, _ -> idx != i } }

    fun doMerge() {
        val list = _mergeList.value
        if (list.size < 2) { vm.toast("Pilih minimal 2 PDF"); return }
        vm.scope.launch {
            vm.setBusy(Busy("Menggabungkan ${list.size} PDF…"))
            try {
                val f = engine.merge(list.map { it.file })
                setDoc(PdfDoc(file = f, sourceUri = null, name = "Gabungan ${list.first().name}", pageCount = engine.pageCount(f), dirty = true), clearUndo = true)
                _mergeList.value = emptyList()
                vm.replaceTop(Screen.PdfReader())
                vm.toast("${list.size} PDF digabung")
            } catch (e: Exception) { vm.toast("Gagal menggabungkan: ${e.message ?: ""}") } finally { vm.setBusy(null) }
        }
    }

    // ---------- simpan & bagikan ----------

    private suspend fun namedCopy(d: PdfDoc, suffix: String = ""): File = withContext(Dispatchers.IO) {
        val dir = File(vm.getApplication<android.app.Application>().cacheDir, "ekspor").apply { mkdirs() }
        val f = File(dir, Names.safeFileName(d.name + suffix) + ".pdf")
        d.file.copyTo(f, overwrite = true)
        f
    }

    fun saveAs() {
        val d = _doc.value ?: return
        vm.scope.launch { vm.send(Request.SavePdf(namedCopy(d), d.name)) }
    }

    /** Hasil pemilih berkas "Simpan sebagai". */
    fun onSavedAs(target: Uri, file: File) {
        vm.scope.launch {
            val ok = engine.writeTo(file, target)
            if (!ok) { vm.toast("Gagal menyimpan berkas"); return@launch }
            takePermission(target)
            _doc.value?.let { d ->
                val n = d.copy(sourceUri = target, dirty = false)
                _doc.value = n
                store.touch(target, d.name, d.pageCount)
            }
            vm.toast("PDF disimpan")
        }
    }

    val canOverwrite: Boolean get() = _doc.value?.sourceUri != null

    fun overwrite() {
        val d = _doc.value ?: return
        val uri = d.sourceUri ?: run { saveAs(); return }
        if (vm.isBusy) return
        vm.setBusy(Busy("Menyimpan…"))
        vm.scope.launch {
            val ok = try {
                // PDF yang aslinya bersandi dikunci lagi dengan sandi yang sama, agar sandinya tidak hilang diam-diam
                val pw = d.password
                if (d.hadPassword && pw != null) {
                    val locked = engine.protect(d.file, pw)
                    engine.writeTo(locked, uri).also { locked.delete() }
                } else engine.writeTo(d.file, uri)
            } catch (_: Exception) { false } finally { vm.setBusy(null) }
            if (ok) { _doc.value = d.copy(dirty = false); vm.toast(if (d.hadPassword) "Berkas asli diperbarui (tetap bersandi)" else "Berkas asli diperbarui") }
            else vm.toast("Berkas asli tidak bisa ditimpa (hanya-baca). Pakai Simpan sebagai baru.")
        }
    }

    fun share() {
        val d = _doc.value ?: return
        vm.scope.launch { vm.send(Request.Share(listOf(namedCopy(d)), "application/pdf", d.name)) }
    }

    fun rename(name: String) { _doc.value = _doc.value?.copy(name = name.trim().ifBlank { _doc.value!!.name }) }

    /** Simpan salinan berkata sandi (berkas kerja tetap tanpa sandi agar bisa dibaca). */
    fun saveWithPassword(pw: String) {
        val d = _doc.value ?: return
        if (pw.length < 4) { vm.toast("Kata sandi minimal 4 karakter"); return }
        if (vm.isBusy) return
        vm.scope.launch {
            vm.setBusy(Busy("Mengunci PDF…"))
            try {
                val locked = engine.protect(d.file, pw)
                val named = File(locked.parentFile, Names.safeFileName(d.name + " (terkunci)") + ".pdf")
                locked.copyTo(named, overwrite = true); locked.delete()
                vm.send(Request.SaveFile(named, "application/pdf"))
                vm.toast("Ingat kata sandinya; tanpa itu PDF tidak bisa dibuka")
            } catch (e: Exception) { vm.toast("Gagal mengunci: ${e.message ?: ""}") } finally { vm.setBusy(null) }
        }
    }

    /** Kecilkan PDF yang sedang dibuka. Bila hasilnya tidak lebih kecil, PDF dibiarkan seperti semula. */
    fun compress(level: CompressLevel) {
        val cur = _doc.value ?: return
        val before = cur.file.length()
        vm.scope.launch {
            vm.setBusy(Busy("Mengecilkan…", 0f))
            try {
                val out = compressFile(cur.file, level) { i, n -> vm.setBusy(Busy("Mengecilkan halaman $i dari $n", i / n.toFloat())) }
                if (out.length() >= before) {
                    out.delete()
                    vm.toast("PDF ini sudah kecil; tingkat ${level.label} tidak bisa memperkecilnya lagi")
                    return@launch
                }
                val n = engine.pageCount(out)
                if (n != cur.pageCount) { out.delete(); vm.toast("Sebagian halaman gagal dikecilkan; PDF dibiarkan seperti semula"); return@launch }
                pushUndo(cur)
                setDoc(cur.copy(file = out, dirty = true))
                vm.toast("${id.rwi.scanpdfin.ui.formatBytes(before)} → ${id.rwi.scanpdfin.ui.formatBytes(out.length())}")
            } catch (e: Throwable) {
                vm.toast(if (e is OutOfMemoryError) "Memori HP tidak cukup untuk PDF ini" else "Gagal: ${e.message ?: ""}")
            } finally { vm.setBusy(null) }
        }
    }

    /** Ambil halaman terpilih jadi PDF baru → simpan. */
    fun extract(indices: List<Int>) {
        val d = _doc.value ?: return
        vm.scope.launch {
            vm.setBusy(Busy("Memisahkan halaman…"))
            try {
                val f = engine.extract(d.file, indices.sorted())
                val named = File(f.parentFile, Names.safeFileName("${d.name} (hal ${indices.sorted().joinToString(",") { (it + 1).toString() }.take(30)})") + ".pdf")
                f.copyTo(named, overwrite = true); f.delete()
                vm.send(Request.SaveFile(named, "application/pdf"))
            } catch (e: Exception) { vm.toast("Gagal: ${e.message ?: ""}") } finally { vm.setBusy(null) }
        }
    }

    suspend fun info(): Map<String, String> = _doc.value?.let { engine.info(it.file) } ?: emptyMap()
}
