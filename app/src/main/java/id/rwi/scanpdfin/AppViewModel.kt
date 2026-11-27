package id.rwi.scanpdfin

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.rwi.scanpdfin.data.Document
import id.rwi.scanpdfin.data.FilterMode
import id.rwi.scanpdfin.data.Page
import id.rwi.scanpdfin.data.Paper
import id.rwi.scanpdfin.data.Quality
import id.rwi.scanpdfin.data.Settings
import id.rwi.scanpdfin.data.SortMode
import id.rwi.scanpdfin.export.Exporter
import id.rwi.scanpdfin.image.EdgeDetector
import id.rwi.scanpdfin.image.ImageOps
import id.rwi.scanpdfin.image.Quad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Layar yang bisa ditampilkan. Navigasi = tumpukan sederhana. */
sealed class Screen {
    data object Welcome : Screen()
    data object ScanHome : Screen()
    data object PdfHome : Screen()
    data class Doc(val id: String) : Screen()
    data class PageView(val docId: String, val startIndex: Int) : Screen()
    data object Camera : Screen()
    data object Crop : Screen()
    /** Pilih tampilan (Asli/Foto/Dokumen × Warna/Hitam) setelah atur bingkai, sebelum disimpan. */
    data object Color : Screen()
    data object Settings : Screen()
    data object About : Screen()
    /** Pembaca PDF (gulir halaman). */
    /** startPage = -1: lanjut dari halaman terakhir yang dibaca (atau halaman 1 untuk dokumen baru). */
    data class PdfReader(val startPage: Int = -1) : Screen()
    /** Kisi halaman PDF: hapus, putar, susun, sisip, ekstrak. */
    data object PdfPages : Screen()
    /**
     * Edit halaman: tanda tangan, teks, pena, stabilo, kotak penutup.
     * [start] = alat yang langsung dibuka: "edit" (bebas), "ttd" (pilih tanda tangan), "teks" (ketik teks).
     */
    data class PdfEdit(val page: Int, val start: String = "edit") : Screen()
    data object PdfMerge : Screen()
    data object PdfSignatures : Screen()
    /** Menu Kecilkan PDF (berdiri sendiri, bisa banyak berkas). */
    data object Compress : Screen()
}

/** Untuk apa berkas PDF/gambar dipilih. */
enum class PickPurpose { OPEN, MERGE, INSERT_PDF, IMAGES_NEW, IMAGES_INSERT, COMPRESS }

/** Permintaan ke Activity (hal yang butuh launcher sistem). */
sealed class Request {
    data class PickImages(val intoDocId: String?) : Request()
    data class SaveFile(val file: File, val mime: String) : Request()
    data class Share(val files: List<File>, val mime: String, val subject: String) : Request()
    data class PickPdf(val purpose: PickPurpose, val multiple: Boolean = false) : Request()
    data class PickImagesFor(val purpose: PickPurpose) : Request()
    /** Simpan PDF kerja sebagai berkas baru; hasil URI dikirim ke PdfController.onSavedAs. */
    data class SavePdf(val file: File, val name: String) : Request()
}

data class Busy(val text: String, val progress: Float? = null)

enum class ExportFormat(val label: String) { PDF("PDF"), JPG("Gambar JPG") }

/** Satu foto dalam sesi pindai: foto asli tegak + bingkai. */
data class Capture(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val corners: List<Float>,
    /** hasil deteksi otomatis (untuk tombol Deteksi ulang & tempel ke tepi); null = tidak terdeteksi */
    val auto: List<Float>?,
    /** naik setiap foto/bingkai diubah dari luar editor (putar, deteksi ulang, seluruh foto) */
    val version: Int = 0,
)

/**
 * Sesi pindai: kumpulan foto yang belum disimpan.
 * [editPageId] != null berarti mengatur ulang bingkai halaman yang sudah ada (1 foto).
 */
data class ScanSession(
    val targetDocId: String?,
    val captures: List<Capture> = emptyList(),
    val index: Int = 0,
    val editPageId: String? = null,
    /** tampilan yang dipilih di layar pewarnaan; berlaku untuk semua foto sesi ini */
    val filter: FilterMode? = null,
) {
    val current: Capture? get() = captures.getOrNull(index)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val scanApp = app as ScanApp
    val repo = scanApp.repository
    private val prefs = scanApp.prefs

    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val documents: StateFlow<List<Document>> = repo.documents

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Welcome))
    val stack = _stack.asStateFlow()
    val screen: Screen get() = _stack.value.last()

    private val _request = MutableStateFlow<Request?>(null)
    val request = _request.asStateFlow()
    fun consumeRequest() { _request.value = null }

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    private var toastJob: Job? = null

    private val _busy = MutableStateFlow<Busy?>(null)
    val busy = _busy.asStateFlow()

    private val _session = MutableStateFlow<ScanSession?>(null)
    val session = _session.asStateFlow()

    val search = MutableStateFlow("")

    /** Semua urusan menu PDF. */
    val pdf = PdfController(this, scanApp.pdfEngine, scanApp.pdfStore)

    // dipakai PdfController
    internal fun setBusy(b: Busy?) { _busy.value = b }
    /** true selama ada proses berat; dipakai sebagai penjaga agar aksi tidak jalan dobel */
    val isBusy: Boolean get() = _busy.value != null
    internal fun send(r: Request) { _request.value = r }
    internal fun replaceTop(s: Screen) { _stack.value = _stack.value.dropLast(1) + s }
    internal fun popWhile(pred: (Screen) -> Boolean) { _stack.value = _stack.value.dropLastWhile(pred).ifEmpty { listOf(Screen.Welcome) } }
    internal val scope get() = viewModelScope

    /** Dari kisi halaman PDF → pembaca di halaman [page]. */
    fun replaceReader(page: Int) {
        _stack.value = _stack.value.dropLastWhile { it == Screen.PdfPages || it is Screen.PdfReader } + Screen.PdfReader(page)
    }

    init {
        // Muat OpenCV lebih awal agar deteksi pertama tidak tersendat.
        viewModelScope.launch(Dispatchers.Default) { EdgeDetector.init() }
        // sesi pindai hanya ada di memori: foto sementara dari sesi lama (app sempat ditutup sistem) dibuang
        viewModelScope.launch(Dispatchers.IO) {
            repo.sessionDir.listFiles()?.forEach { runCatching { it.delete() } }
            scanApp.pdfEngine.cleanup(null)
            java.io.File(getApplication<Application>().cacheDir, "ekspor").listFiles()
                ?.filter { it.lastModified() < System.currentTimeMillis() - 24 * 3600_000L }?.forEach { runCatching { it.delete() } }
        }
    }

    /** Naik setiap sesi pindai dibuang/diganti; foto yang selesai diproses untuk sesi lama diabaikan. */
    private var sessionToken = 0

    /** Jumlah jepretan yang sedang diproses (untuk tanda "Memproses…" di kamera). */
    private val _processing = MutableStateFlow(0)
    val processing = _processing.asStateFlow()

    // ---------- navigasi ----------
    fun go(s: Screen) { _stack.value = _stack.value + s }
    fun back(): Boolean {
        if (_stack.value.size <= 1) return false
        val leaving = _stack.value.last()
        _stack.value = _stack.value.dropLast(1)
        // keluar dari editor/kamera tanpa layar sesi tersisa → sesi dibuang
        if (leaving.isScanScreen() && _stack.value.none { it.isScanScreen() }) clearSession()
        return true
    }
    private fun Screen.isScanScreen() = this == Screen.Camera || this == Screen.Crop || this == Screen.Color
    fun backToWelcome() { _stack.value = listOf(Screen.Welcome) }
    private fun popScanScreens() { _stack.value = _stack.value.dropLastWhile { it.isScanScreen() } }
    val inCameraFlow: Boolean get() = _stack.value.contains(Screen.Camera)

    // ---------- umpan balik ----------
    fun toast(msg: String) {
        toastJob?.cancel()
        _toast.value = msg
        toastJob = viewModelScope.launch { delay(2400); _toast.value = null }
    }

    // ---------- pengaturan ----------
    fun updateSettings(transform: (Settings) -> Settings) = viewModelScope.launch { prefs.save(transform(settings.value)) }
    fun setSort(sort: SortMode) = updateSettings { it.copy(sort = sort) }

    // ---------- sesi pindai ----------
    private fun clearSession() {
        sessionToken++
        _session.value?.let { s -> viewModelScope.launch(Dispatchers.IO) { s.captures.forEach { it.file.delete() } } }
        _session.value = null
    }

    /** Buka kamera untuk dokumen baru (null) atau menambah ke dokumen yang ada. */
    fun startCamera(targetDocId: String?) {
        val cur = _session.value
        // sesi sisa untuk dokumen lain/atur ulang bingkai tidak boleh terbawa
        if (cur != null && (cur.editPageId != null || cur.targetDocId != targetDocId)) clearSession()
        if (_session.value == null) _session.value = ScanSession(targetDocId)
        go(Screen.Camera)
    }

    fun requestPick(intoDocId: String?) { _request.value = Request.PickImages(intoDocId) }

    private suspend fun detectFor(file: File): List<Float>? = withContext(Dispatchers.Default) {
        val bmp = ImageOps.decodeFile(file, 1000) ?: return@withContext null
        try { EdgeDetector.detect(bmp) } finally { bmp.recycle() }
    }

    /** Foto dari kamera (berkas sementara, mungkin ber-EXIF) → foto tegak di sesi + deteksi tepi. */
    fun addCameraShot(tmp: File) {
        val token = sessionToken
        _processing.value++
        viewModelScope.launch {
            try {
                val target = File(repo.sessionDir, "${UUID.randomUUID()}.jpg")
                val ok = withContext(Dispatchers.IO) {
                    ImageOps.importToFile(getApplication(), Uri.fromFile(tmp), target).also { tmp.delete() }
                }
                if (!ok) { toast("Foto gagal disimpan, coba lagi"); return@launch }
                val auto = detectFor(target)
                // kamera sudah ditutup/sesi dibuang selama foto diproses → foto ini tidak dipakai
                val s = _session.value
                if (token != sessionToken || s == null) { withContext(Dispatchers.IO) { target.delete() }; return@launch }
                val cap = Capture(file = target, corners = auto ?: Quad.INSET, auto = auto)
                _session.value = s.copy(captures = s.captures + cap, index = s.captures.size)
                if (!settings.value.burst && screen == Screen.Camera) go(Screen.Crop)
            } finally { _processing.value-- }
        }
    }

    /** Gambar dari galeri/app lain → sesi → editor bingkai. */
    fun importToSession(uris: List<Uri>, intoDocId: String?) {
        if (uris.isEmpty()) return
        if (isBusy) return
        val token = sessionToken
        viewModelScope.launch {
            _busy.value = Busy(if (uris.size == 1) "Membaca gambar…" else "Membaca ${uris.size} gambar…", 0f)
            val caps = try {
                uris.mapIndexedNotNull { i, uri ->
                    _busy.value = Busy("Membaca gambar ${i + 1} dari ${uris.size}", (i + 1) / uris.size.toFloat())
                    val target = File(repo.sessionDir, "${UUID.randomUUID()}.jpg")
                    val ok = withContext(Dispatchers.IO) { ImageOps.importToFile(getApplication(), uri, target) }
                    if (!ok) null else {
                        val auto = detectFor(target)
                        Capture(file = target, corners = auto ?: Quad.FULL, auto = auto)
                    }
                }
            } finally { _busy.value = null }
            if (caps.isEmpty()) { toast("Gambar tidak bisa dibaca"); return@launch }
            if (token != sessionToken && _session.value != null) {
                // sesi lain dimulai selama gambar dibaca: gambar ini tidak dicampur ke sesi itu
                withContext(Dispatchers.IO) { caps.forEach { it.file.delete() } }; return@launch
            }
            if (caps.size < uris.size) toast("${uris.size - caps.size} gambar dilewati karena tidak bisa dibaca")
            val s = _session.value?.takeIf { it.editPageId == null } ?: ScanSession(intoDocId)
            _session.value = s.copy(captures = s.captures + caps, index = s.captures.size)
            if (screen != Screen.Crop) go(Screen.Crop)
        }
    }

    fun selectCapture(i: Int) { _session.value = _session.value?.let { it.copy(index = i.coerceIn(0, (it.captures.size - 1).coerceAtLeast(0))) } }

    private fun editCapture(i: Int, transform: (Capture) -> Capture) {
        val s = _session.value ?: return
        _session.value = s.copy(captures = s.captures.mapIndexed { idx, c -> if (idx == i) transform(c) else c })
    }

    /** Dari editor (tarik pegangan): tidak menaikkan versi agar editor tidak dimuat ulang. */
    fun setCorners(i: Int, corners: List<Float>) = editCapture(i) { it.copy(corners = corners) }
    fun redetect(i: Int) = editCapture(i) { c ->
        if (c.auto == null) toast("Tepi dokumen tidak terdeteksi, atur manual")
        c.copy(corners = c.auto ?: Quad.INSET, version = c.version + 1)
    }
    fun fullFrame(i: Int) = editCapture(i) { it.copy(corners = Quad.FULL, version = it.version + 1) }

    private val rotating = mutableSetOf<String>()

    fun rotateCapture(i: Int) {
        val c = _session.value?.captures?.getOrNull(i) ?: return
        // ketuk Putar beruntun pada foto yang sama ditunggu satu per satu (tidak menulis berkas bersamaan)
        if (!rotating.add(c.id)) return
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    val bmp = ImageOps.decodeFile(c.file, ImageOps.IMPORT_MAX_SIDE) ?: return@withContext false
                    val r = ImageOps.rotate(bmp, 90)
                    ImageOps.saveJpeg(r, c.file, 92)
                    r.recycle()
                    true
                }
                // dicari lewat id: foto lain bisa sudah dihapus selama proses putar
                val s = _session.value
                if (ok && s != null) _session.value = s.copy(captures = s.captures.map {
                    if (it.id == c.id) it.copy(corners = Quad.rotateCw(it.corners), auto = it.auto?.let(Quad::rotateCw), version = it.version + 1) else it
                })
            } finally { rotating.remove(c.id) }
        }
    }

    /** Hapus foto dari sesi. Mengembalikan true bila sesi jadi kosong. */
    fun removeCapture(i: Int): Boolean {
        val s = _session.value ?: return true
        val c = s.captures.getOrNull(i) ?: return s.captures.isEmpty()
        viewModelScope.launch(Dispatchers.IO) { c.file.delete() }
        val rest = s.captures.filterIndexed { idx, _ -> idx != i }
        _session.value = s.copy(captures = rest, index = i.coerceAtMost((rest.size - 1).coerceAtLeast(0)))
        return rest.isEmpty()
    }

    /** "Ulangi foto"/"Foto lagi": kembali ke kamera. */
    fun backToCamera() {
        _stack.value = _stack.value.dropLastWhile { it == Screen.Crop || it == Screen.Color }
        if (screen != Screen.Camera) go(Screen.Camera)
    }

    /** Dari editor bingkai → layar pewarnaan. */
    fun goColor() {
        val s = _session.value ?: return
        if (s.filter == null) _session.value = s.copy(filter = settings.value.defaultFilter.normalized())
        go(Screen.Color)
    }
    fun setSessionFilter(f: FilterMode) { _session.value = _session.value?.copy(filter = f) }

    /** Simpan semua foto sesi menjadi halaman. */
    fun commitSession() {
        val s = _session.value ?: return
        if (isBusy) return
        if (s.captures.isEmpty()) { popScanScreens(); clearSession(); return }
        _busy.value = Busy("Meluruskan halaman…", 0f)
        viewModelScope.launch {
            try {
                if (s.editPageId != null && s.targetDocId != null) {
                    val c = s.captures.first()
                    val ok = repo.recrop(s.targetDocId, s.editPageId, c.file, c.corners)
                    _session.value = null
                    popScanScreens()
                    toast(if (ok) "Bingkai diperbarui" else "Gagal memperbarui bingkai")
                    return@launch
                }
                val id = repo.addScannedPages(s.captures.map { it.file to it.corners }, s.targetDocId, s.filter ?: settings.value.defaultFilter.normalized()) { i, n ->
                    _busy.value = Busy("Meluruskan halaman $i dari $n", i / n.toFloat())
                }
                _session.value = null
                popScanScreens()
                if (id == null) { toast("Halaman gagal disimpan"); return@launch }
                val n = s.captures.size
                toast(if (n == 1) "1 halaman disimpan" else "$n halaman disimpan")
                if (s.targetDocId == null) go(Screen.Doc(id))
            } finally { _busy.value = null }
        }
    }

    /** Atur ulang bingkai halaman yang sudah tersimpan. */
    fun editPageFrame(docId: String, page: Page) {
        viewModelScope.launch {
            clearSession()
            val src = repo.originalFile(page)
            val copy = File(repo.sessionDir, "${UUID.randomUUID()}.jpg")
            val ok = withContext(Dispatchers.IO) { runCatching { src.copyTo(copy, true); true }.getOrDefault(false) }
            if (!ok) { toast("Foto halaman tidak ditemukan"); return@launch }
            val auto = detectFor(copy)
            val corners = if (page.original != null) page.corners ?: Quad.FULL else Quad.FULL
            _session.value = ScanSession(targetDocId = docId, captures = listOf(Capture(file = copy, corners = corners, auto = auto)), editPageId = page.id)
            go(Screen.Crop)
        }
    }

    // ---------- dokumen ----------
    fun rename(docId: String, name: String) = viewModelScope.launch { repo.rename(docId, name); toast("Nama diubah") }
    fun deleteDocuments(ids: Set<String>) = viewModelScope.launch {
        repo.deleteDocuments(ids)
        toast(if (ids.size == 1) "Dokumen dihapus" else "${ids.size} dokumen dihapus")
        val sc = screen
        if (sc is Screen.Doc && sc.id in ids) back()
    }
    fun merge(ids: List<String>, name: String) = viewModelScope.launch {
        val id = repo.merge(ids, name)
        if (id != null) { toast("Dokumen digabung"); go(Screen.Doc(id)) }
    }
    fun duplicate(docId: String) = viewModelScope.launch { repo.duplicate(docId)?.let { toast("Salinan dibuat") } }

    // ---------- halaman ----------
    fun deletePages(docId: String, ids: Set<String>) = viewModelScope.launch {
        repo.deletePages(docId, ids); toast(if (ids.size == 1) "Halaman dihapus" else "${ids.size} halaman dihapus")
    }
    fun setFilter(docId: String, ids: Set<String>?, mode: FilterMode) = viewModelScope.launch { repo.setFilter(docId, ids, mode) }
    fun rotate(docId: String, pageId: String, delta: Int) = viewModelScope.launch { repo.rotate(docId, pageId, delta) }
    fun movePage(docId: String, from: Int, to: Int) = viewModelScope.launch { repo.movePage(docId, from, to) }
    fun movePagesTo(fromDocId: String, ids: Set<String>, targetId: String?, copy: Boolean) = viewModelScope.launch {
        repo.movePagesTo(fromDocId, ids, targetId, copy)
        toast(if (copy) "Halaman disalin" else "Halaman dipindahkan")
    }

    // ---------- ekspor ----------
    fun export(
        doc: Document,
        pages: List<Page>,
        format: ExportFormat,
        share: Boolean,
        paper: Paper = settings.value.paper,
        quality: Quality = settings.value.quality,
        modeOverride: FilterMode? = null,
        fileName: String = doc.name,
    ) {
        if (pages.isEmpty()) { toast("Tidak ada halaman"); return }
        if (isBusy) return
        _busy.value = Busy("Menyiapkan…", 0f)
        val ctx: Context = getApplication()
        val title = fileName.trim().ifBlank { doc.name }
        viewModelScope.launch {
            try {
                val progress: (Int, Int) -> Unit = { i, n -> _busy.value = Busy("Menyiapkan halaman $i dari $n", i / n.toFloat()) }
                _busy.value = Busy("Menyiapkan…", 0f)
                when (format) {
                    ExportFormat.PDF -> {
                        val f = Exporter.pdf(ctx, repo, pages, title, paper, quality, modeOverride, progress)
                        toast("PDF siap (${id.rwi.scanpdfin.ui.formatBytes(f.length())})")
                        _request.value = if (share) Request.Share(listOf(f), "application/pdf", title) else Request.SaveFile(f, "application/pdf")
                    }
                    ExportFormat.JPG -> {
                        val files = Exporter.jpgs(ctx, repo, pages, title, quality, modeOverride, progress)
                        if (share) _request.value = Request.Share(files, "image/jpeg", title)
                        else if (files.size == 1) _request.value = Request.SaveFile(files[0], "image/jpeg")
                        else {
                            _request.value = Request.Share(files, "image/jpeg", title)
                            toast("Banyak gambar: pilih Files/Drive di lembar bagikan untuk menyimpan")
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                toast("Memori HP tidak cukup. Coba kualitas lebih kecil di Pengaturan, atau bagi jadi beberapa PDF.")
            } catch (e: Exception) {
                toast("Gagal mengekspor: ${e.message ?: "kesalahan tak dikenal"}")
            } finally { _busy.value = null }
        }
    }

    fun saveTo(file: File, target: Uri) = viewModelScope.launch {
        val ok = Exporter.copyTo(getApplication(), file, target)
        toast(if (ok) "Berkas disimpan" else "Gagal menyimpan berkas")
    }

    fun clearCache() { repo.clearThumbCache(); toast("Cache dibersihkan") }
    fun storageBytes() = repo.storageBytes()
}
