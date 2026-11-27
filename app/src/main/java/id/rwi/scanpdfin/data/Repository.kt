package id.rwi.scanpdfin.data

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import id.rwi.scanpdfin.image.ImageOps
import id.rwi.scanpdfin.image.Perspective
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Penyimpanan dokumen: indeks JSON (filesDir/index.json) + gambar halaman (filesDir/pages/).
 * Thumbnail di cacheDir/thumbs/ (boleh dihapus sistem kapan saja).
 */
class Repository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val indexFile = File(context.filesDir, "index.json")
    val pagesDir = File(context.filesDir, "pages").apply { mkdirs() }
    private val thumbsDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
    private val mutex = Mutex()

    private val _documents = MutableStateFlow(load())
    val documents = _documents.asStateFlow()

    private val memCache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    // ---------- muat/simpan indeks ----------

    private fun load(): List<Document> {
        // indeks utama → cadangan .bak → .tmp (bila app mati tepat saat menyimpan)
        AtomicFile.readWithFallback(indexFile) { json.decodeFromString<Index>(it).documents }?.let { return it }
        // semua rusak: simpan salinan untuk diselidiki, mulai kosong agar app tetap bisa dibuka
        if (indexFile.exists()) runCatching { indexFile.copyTo(File(context.filesDir, "index-rusak-${System.currentTimeMillis()}.json"), true) }
        return emptyList()
    }

    private fun save(docs: List<Document>) {
        AtomicFile.writeText(indexFile, json.encodeToString(Index(documents = docs)), keepBackup = true)
    }

    private suspend fun update(transform: (List<Document>) -> List<Document>) = mutex.withLock {
        val new = transform(_documents.value)
        _documents.value = new
        withContext(Dispatchers.IO) { save(new) }
    }

    fun document(id: String): Document? = _documents.value.firstOrNull { it.id == id }
    fun pageFile(page: Page): File = File(pagesDir, page.file)

    // ---------- dokumen ----------

    /** Folder sementara foto sesi pindai (sebelum disimpan ke dokumen). */
    val sessionDir: File get() = File(context.cacheDir, "sesi").apply { mkdirs() }

    /**
     * Simpan foto sesi pindai menjadi halaman: potong & luruskan sesuai bingkai, simpan juga foto aslinya.
     * [items] = (berkas foto asli tegak di sessionDir, bingkai). Mengembalikan id dokumen.
     */
    suspend fun addScannedPages(
        items: List<Pair<File, List<Float>>>,
        intoDocumentId: String?,
        filter: FilterMode,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): String? = withContext(Dispatchers.IO) {
        val pages = items.mapIndexedNotNull { i, (orig, corners) ->
            onProgress(i + 1, items.size)
            try {
                val base = UUID.randomUUID().toString()
                val origName = "orig_$base.jpg"
                val pageName = "$base.jpg"
                val full = ImageOps.decodeFile(orig, ImageOps.IMPORT_MAX_SIDE) ?: return@mapIndexedNotNull null
                val warped = Perspective.warp(full, corners)
                full.recycle()
                ImageOps.saveJpeg(warped, File(pagesDir, pageName), 92)
                warped.recycle()
                orig.copyTo(File(pagesDir, origName), overwrite = true)
                orig.delete()
                Page(file = pageName, filter = filter, original = origName, corners = corners)
            } catch (t: Throwable) {
                android.util.Log.w("ScanPdfin", "Gagal menyimpan halaman", t)
                null
            }
        }
        if (pages.isEmpty()) return@withContext null
            val now = System.currentTimeMillis()
            var resultId = intoDocumentId
            update { docs ->
                val existing = intoDocumentId?.let { id -> docs.firstOrNull { it.id == id } }
                if (existing != null) {
                    docs.map { if (it.id == existing.id) it.copy(pages = it.pages + pages, updatedAt = now) else it }
                } else {
                    val doc = Document(name = Names.defaultDocumentName(now), createdAt = now, updatedAt = now, pages = pages)
                    resultId = doc.id
                    docs + doc
                }
            }
            resultId
        }

    /**
     * Atur ulang bingkai halaman yang sudah ada. [orig] = foto asli (salinan di sessionDir, mungkin sudah diputar).
     */
    suspend fun recrop(docId: String, pageId: String, orig: File, corners: List<Float>): Boolean = withContext(Dispatchers.IO) {
        val page = document(docId)?.pages?.firstOrNull { it.id == pageId } ?: return@withContext false
        try {
            val full = ImageOps.decodeFile(orig, ImageOps.IMPORT_MAX_SIDE) ?: return@withContext false
            val warped = Perspective.warp(full, corners)
            full.recycle()
            val base = UUID.randomUUID().toString()
            val pageName = "$base.jpg"
            val origName = page.original ?: "orig_$base.jpg"
            ImageOps.saveJpeg(warped, File(pagesDir, pageName), 92)
            warped.recycle()
            orig.copyTo(File(pagesDir, origName), overwrite = true)
            orig.delete()
            val old = page
            updatePages(docId) { pages -> pages.map { if (it.id == pageId) it.copy(file = pageName, original = origName, corners = corners) else it } }
            File(pagesDir, old.file).delete()
            thumbsDir.listFiles { f -> f.name.startsWith(old.id) }?.forEach { it.delete() }
            memCache.snapshot().keys.filter { it.startsWith(old.id) }.forEach { memCache.remove(it) }
            true
        } catch (t: Throwable) {
            android.util.Log.w("ScanPdfin", "Gagal mengatur ulang bingkai", t)
            false
        }
    }

    /** Berkas foto asli halaman (atau hasil potong bila halaman lama tanpa foto asli). */
    fun originalFile(page: Page): File = page.original?.let { File(pagesDir, it) }?.takeIf { it.exists() } ?: pageFile(page)

    suspend fun rename(docId: String, newName: String) = update { docs ->
        docs.map { if (it.id == docId) it.copy(name = newName.trim().ifBlank { it.name }, updatedAt = System.currentTimeMillis()) else it }
    }

    suspend fun deleteDocuments(ids: Set<String>) {
        val victims = _documents.value.filter { it.id in ids }
        update { docs -> docs.filterNot { it.id in ids } }
        withContext(Dispatchers.IO) { victims.flatMap { it.pages }.forEach { deletePageFiles(it) } }
    }

    /** Gabung beberapa dokumen (urutan sesuai daftar) menjadi satu dokumen baru; sumber dihapus. */
    suspend fun merge(ids: List<String>, name: String): String? {
        val sources = ids.mapNotNull { document(it) }
        if (sources.size < 2) return null
        val now = System.currentTimeMillis()
        val merged = Document(name = name, createdAt = now, updatedAt = now, pages = sources.flatMap { it.pages })
        update { docs -> docs.filterNot { it.id in ids } + merged }
        return merged.id
    }

    suspend fun duplicate(docId: String): String? {
        val src = document(docId) ?: return null
        val copies = withContext(Dispatchers.IO) { src.pages.map { copyPageFiles(it) } }
        val now = System.currentTimeMillis()
        val dup = src.copy(id = UUID.randomUUID().toString(), name = "${src.name} (salinan)", createdAt = now, updatedAt = now, pages = copies)
        update { it + dup }
        return dup.id
    }

    // ---------- halaman ----------

    private suspend fun updatePages(docId: String, transform: (List<Page>) -> List<Page>) = update { docs ->
        docs.map { if (it.id == docId) it.copy(pages = transform(it.pages), updatedAt = System.currentTimeMillis()) else it }
    }

    suspend fun deletePages(docId: String, pageIds: Set<String>) {
        val victims = document(docId)?.pages?.filter { it.id in pageIds } ?: return
        updatePages(docId) { it.filterNot { p -> p.id in pageIds } }
        withContext(Dispatchers.IO) { victims.forEach { deletePageFiles(it) } }
    }

    suspend fun setFilter(docId: String, pageIds: Set<String>?, mode: FilterMode) = updatePages(docId) { pages ->
        pages.map { if (pageIds == null || it.id in pageIds) it.copy(filter = mode) else it }
    }

    suspend fun rotate(docId: String, pageId: String, delta: Int) = updatePages(docId) { pages ->
        pages.map { if (it.id == pageId) it.copy(rotation = ((it.rotation + delta) % 360 + 360) % 360) else it }
    }

    suspend fun movePage(docId: String, from: Int, to: Int) = updatePages(docId) { pages ->
        if (from !in pages.indices || to !in pages.indices || from == to) pages
        else pages.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Pindahkan/salin halaman ke dokumen lain (atau dokumen baru bila targetId null). */
    suspend fun movePagesTo(fromDocId: String, pageIds: Set<String>, targetId: String?, copy: Boolean): String? {
        val src = document(fromDocId) ?: return null
        val moving = src.pages.filter { it.id in pageIds }
        if (moving.isEmpty()) return null
        val payload = if (copy) withContext(Dispatchers.IO) { moving.map { copyPageFiles(it) } } else moving
        val now = System.currentTimeMillis()
        var resultId = targetId
        update { docs ->
            var list = if (copy) docs else docs.map { if (it.id == fromDocId) it.copy(pages = it.pages.filterNot { p -> p.id in pageIds }, updatedAt = now) else it }
            if (targetId != null && list.any { it.id == targetId }) {
                list = list.map { if (it.id == targetId) it.copy(pages = it.pages + payload, updatedAt = now) else it }
            } else {
                val doc = Document(name = Names.defaultDocumentName(now), createdAt = now, updatedAt = now, pages = payload)
                resultId = doc.id
                list = list + doc
            }
            list
        }
        return resultId
    }

    /** Salin berkas halaman (hasil + foto asli) untuk duplikat/salin ke dokumen lain. */
    private fun copyPageFiles(p: Page): Page {
        val base = UUID.randomUUID().toString()
        val fileName = "$base.jpg"
        pageFile(p).copyTo(File(pagesDir, fileName), true)
        val origName = p.original?.let { o ->
            val src = File(pagesDir, o)
            if (src.exists()) "orig_$base.jpg".also { src.copyTo(File(pagesDir, it), true) } else null
        }
        return p.copy(id = UUID.randomUUID().toString(), file = fileName, original = origName)
    }

    private fun deletePageFiles(page: Page) {
        pageFile(page).delete()
        page.original?.let { File(pagesDir, it).delete() }
        thumbsDir.listFiles { f -> f.name.startsWith(page.id) }?.forEach { it.delete() }
        memCache.snapshot().keys.filter { it.startsWith(page.id) }.forEach { memCache.remove(it) }
    }

    // ---------- thumbnail ----------

    /** Thumbnail halaman sesuai mode & rotasi; dicache di memori dan di cacheDir. */
    suspend fun thumbnail(page: Page, maxSide: Int): Bitmap? {
        val key = "${page.renderKey}-$maxSide"
        memCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val cached = File(thumbsDir, "$key.jpg")
            val bmp = if (cached.exists()) ImageOps.decodeFile(cached, maxSide)
            else ImageOps.render(pageFile(page), page.rotation, page.filter, maxSide)?.also { ImageOps.saveJpeg(it, cached, 80) }
            bmp?.also { memCache.put(key, it) }
        }
    }

    /** Render penuh untuk ekspor/tampilan besar (tidak dicache di memori). */
    suspend fun render(page: Page, maxSide: Int, mode: FilterMode = page.filter): Bitmap? = withContext(Dispatchers.IO) {
        ImageOps.render(pageFile(page), page.rotation, mode, maxSide)
    }

    fun clearThumbCache() { thumbsDir.listFiles()?.forEach { it.delete() }; memCache.evictAll() }

    fun storageBytes(): Long = pagesDir.listFiles()?.sumOf { it.length() } ?: 0L
}
