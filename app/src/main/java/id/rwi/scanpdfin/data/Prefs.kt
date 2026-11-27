package id.rwi.scanpdfin.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pengaturan")

enum class ThemeMode(val label: String) { SISTEM("Ikut sistem"), TERANG("Terang"), GELAP("Gelap") }

enum class Paper(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4 (210 × 297 mm)", 595.3f, 841.9f),
    F4("F4 / Folio (215 × 330 mm)", 609.4f, 935.4f),
    LETTER("Letter (8,5 × 11 in)", 612f, 792f),
    LEGAL("Legal (8,5 × 14 in)", 612f, 1008f),
    GAMBAR("Sesuai ukuran gambar", 0f, 0f);
}

enum class Quality(val label: String, val maxSide: Int, val jpeg: Int) {
    RINGAN("Kecil", 1400, 60),
    SEIMBANG("Sedang", 2200, 78),
    TINGGI("Aktual (paling tajam)", 3000, 85);
}

/** Lampu kilat kamera. NYALA = lampu menyala terus sejak pratinjau (tidak menunggu tombol jepret). */
enum class Flash(val label: String) { MATI("Mati"), OTOMATIS("Otomatis saat jepret"), NYALA("Selalu nyala") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SISTEM,
    val paper: Paper = Paper.GAMBAR,
    val defaultFilter: FilterMode = FilterMode.DOKUMEN,
    val quality: Quality = Quality.TINGGI,
    val sort: SortMode = SortMode.TERBARU,
    /** kamera: false = satu per satu (atur bingkai tiap jepret), true = beruntun */
    val burst: Boolean = false,
    val flash: Flash = Flash.MATI,
    /** pegangan sudut menempel ke tepi hasil deteksi */
    val snap: Boolean = true,
    /** garis bantu tepi dokumen di pratinjau kamera */
    val liveEdges: Boolean = true,
    /** pembaca PDF: false = gulir bersambung, true = per halaman (usap kiri/kanan) */
    val readPaged: Boolean = false,
    /** slide show: detik per halaman */
    val slideSeconds: Int = 5,
)

class Prefs(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val paper = stringPreferencesKey("paper")
        val filter = stringPreferencesKey("filter")
        val quality = stringPreferencesKey("quality")
        val sort = stringPreferencesKey("sort")
        val burst = booleanPreferencesKey("burst")
        val flash = stringPreferencesKey("flash")
        val snap = booleanPreferencesKey("snap")
        val liveEdges = booleanPreferencesKey("liveEdges")
        val readPaged = booleanPreferencesKey("readPaged")
        val slideSeconds = intPreferencesKey("slideSeconds")
    }

    private inline fun <reified E : Enum<E>> parse(v: String?, default: E): E =
        v?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: default

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = parse(p[K.theme], ThemeMode.SISTEM),
            paper = parse(p[K.paper], Paper.GAMBAR),
            defaultFilter = parse(p[K.filter], FilterMode.DOKUMEN),
            quality = parse(p[K.quality], Quality.TINGGI),
            sort = parse(p[K.sort], SortMode.TERBARU),
            burst = p[K.burst] ?: false,
            // versi lama punya "SENTER" (lampu terus menyala) → kini menjadi NYALA
            flash = if (p[K.flash] == "SENTER") Flash.NYALA else parse(p[K.flash], Flash.MATI),
            snap = p[K.snap] ?: true,
            liveEdges = p[K.liveEdges] ?: true,
            readPaged = p[K.readPaged] ?: false,
            slideSeconds = p[K.slideSeconds] ?: 5,
        )
    }

    suspend fun save(s: Settings) {
        context.dataStore.edit { p ->
            p[K.theme] = s.theme.name
            p[K.paper] = s.paper.name
            p[K.filter] = s.defaultFilter.name
            p[K.quality] = s.quality.name
            p[K.sort] = s.sort.name
            p[K.burst] = s.burst
            p[K.flash] = s.flash.name
            p[K.snap] = s.snap
            p[K.liveEdges] = s.liveEdges
            p[K.readPaged] = s.readPaged
            p[K.slideSeconds] = s.slideSeconds
        }
    }
}
