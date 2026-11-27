package id.rwi.scanpdfin.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class RecentPdf(val uri: String, val name: String, val pageCount: Int, val openedAt: Long)

@Serializable
private data class PdfIndex(val recent: List<RecentPdf> = emptyList())

/** Daftar PDF terakhir dibuka + tanda tangan tersimpan (PNG di filesDir/ttd). */
class PdfStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "pdf-index.json")
    private val sigDir = File(context.filesDir, "ttd").apply { mkdirs() }

    private val _recent = MutableStateFlow(load())
    val recent = _recent.asStateFlow()

    private val _signatures = MutableStateFlow(listSignatures())
    val signatures = _signatures.asStateFlow()

    private fun load(): List<RecentPdf> = id.rwi.scanpdfin.data.AtomicFile.readWithFallback(file) { json.decodeFromString<PdfIndex>(it).recent } ?: emptyList()
    private fun save() = runCatching { id.rwi.scanpdfin.data.AtomicFile.writeText(file, json.encodeToString(PdfIndex(_recent.value))) }

    fun touch(uri: Uri, name: String, pageCount: Int) {
        val s = uri.toString()
        _recent.value = (listOf(RecentPdf(s, name, pageCount, System.currentTimeMillis())) + _recent.value.filterNot { it.uri == s }).take(30)
        save()
    }

    fun remove(uri: String) { _recent.value = _recent.value.filterNot { it.uri == uri }; save() }

    // ---------- tanda tangan ----------

    private fun listSignatures(): List<File> = sigDir.listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun saveSignature(bitmap: Bitmap): File {
        val f = File(sigDir, "ttd_${System.currentTimeMillis()}.png")
        id.rwi.scanpdfin.data.AtomicFile.write(f) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _signatures.value = listSignatures()
        return f
    }

    fun deleteSignature(f: File) { f.delete(); _signatures.value = listSignatures() }

    fun loadSignature(f: File): Bitmap? = BitmapFactory.decodeFile(f.path)
}
