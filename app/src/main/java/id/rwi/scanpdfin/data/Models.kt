package id.rwi.scanpdfin.data

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Model tampilan halaman: Asli (apa adanya), Foto (warna dipercantik), Dokumen (kertas diputihkan). */
enum class Look(val label: String) { ASLI("Asli"), FOTO("Foto"), DOKUMEN("Dokumen") }

/**
 * Tampilan halaman = model × pewarnaan (Warna / Hitam). Foto asli selalu tersimpan; ini hanya cara menampilkan/mengekspor.
 * ABU, HITAM_PUTIH, CERAH = nama lama v0.1–0.2, tetap bisa dibaca lalu dinormalkan.
 */
@Serializable
enum class FilterMode(val look: Look, val color: Boolean) {
    ASLI(Look.ASLI, true),
    FOTO(Look.FOTO, true),
    DOKUMEN(Look.DOKUMEN, true),
    ASLI_HITAM(Look.ASLI, false),
    FOTO_HITAM(Look.FOTO, false),
    DOKUMEN_HITAM(Look.DOKUMEN, false),
    ABU(Look.ASLI, false),
    HITAM_PUTIH(Look.DOKUMEN, false),
    CERAH(Look.FOTO, true);

    val label: String get() = if (color) look.label else "${look.label} · Hitam"
    fun normalized(): FilterMode = of(look, color)
    fun withLook(l: Look) = of(l, color)
    fun withColor(c: Boolean) = of(look, c)

    companion object {
        fun of(look: Look, color: Boolean): FilterMode = when (look) {
            Look.ASLI -> if (color) ASLI else ASLI_HITAM
            Look.FOTO -> if (color) FOTO else FOTO_HITAM
            Look.DOKUMEN -> if (color) DOKUMEN else DOKUMEN_HITAM
        }
        /** Pilihan yang ditampilkan ke pengguna (tanpa nama lama). */
        val choices = listOf(ASLI, FOTO, DOKUMEN, ASLI_HITAM, FOTO_HITAM, DOKUMEN_HITAM)
    }
}

@Serializable
data class Page(
    val id: String = UUID.randomUUID().toString(),
    /** nama berkas JPEG hasil potong & luruskan di folder pages/ */
    val file: String,
    val filter: FilterMode = FilterMode.DOKUMEN,
    /** 0, 90, 180, 270 searah jarum jam */
    val rotation: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** foto utuh sebelum dipotong (pages/), agar bingkai bisa diatur ulang. null = halaman lama v0.1 */
    val original: String? = null,
    /** 8 angka 0..1: kiri-atas, kanan-atas, kanan-bawah, kiri-bawah (x,y) pada foto asli */
    val corners: List<Float>? = null,
) {
    /** kunci cache thumbnail: berubah bila tampilan atau berkas berubah */
    val renderKey: String get() = "$id-${file.substringBefore('.').takeLast(8)}-${filter.name}-$rotation"
}

@Serializable
data class Document(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val pages: List<Page> = emptyList(),
)

@Serializable
data class Index(
    val version: Int = 1,
    val documents: List<Document> = emptyList(),
)

enum class SortMode(val label: String) {
    TERBARU("Terbaru diubah"), NAMA("Nama A–Z"), DIBUAT("Tanggal dibuat")
}

object Names {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale("id", "ID"))
    fun defaultDocumentName(time: Long = System.currentTimeMillis()) = "Scan ${fmt.format(Date(time))}"
    fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifBlank { "Dokumen" }
}
