package id.rwi.scanpdfin.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import id.rwi.scanpdfin.data.FilterMode
import id.rwi.scanpdfin.data.Look
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min

/**
 * Model tampilan halaman (OpenCV). Diuji dulu lewat port Python pada foto dokumen nyata.
 * - Asli: apa adanya.
 * - Foto: kecerahan diratakan (CLAHE ringan) + warna sedikit dihidupkan; latar tidak diputihkan.
 * - Dokumen: kertas diputihkan (bagi dengan taksiran latar), tinta dipertegas, warna logo tetap.
 * - Hitam: versi abu-abu dari tiap model di atas.
 */
object Looks {

    fun apply(src: Bitmap, mode: FilterMode): Bitmap {
        val m = mode.normalized()
        if (m == FilterMode.ASLI) return src
        if (!EdgeDetector.init()) return fallback(src, m)
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(src, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            val out: Mat = when (m.look) {
                Look.ASLI -> if (m.color) bgr else gray3(bgr)
                Look.FOTO -> if (m.color) foto(bgr) else gray3(fotoGray(bgr))
                Look.DOKUMEN -> if (m.color) dokumen(bgr) else dokumenHitam(bgr)
            }
            val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val outRgba = Mat()
            Imgproc.cvtColor(out, outRgba, Imgproc.COLOR_BGR2RGBA)
            Utils.matToBitmap(outRgba, result)
            outRgba.release()
            if (out !== bgr) out.release()
            src.recycle()
            return result
        } catch (t: Throwable) {
            android.util.Log.w("ScanPdfin", "Filter OpenCV gagal, pakai cadangan", t)
            return fallback(src, m)
        } finally {
            rgba.release(); bgr.release()
        }
    }

    // ---------- OpenCV ----------

    private fun gray3(bgr: Mat): Mat {
        val g = Mat(); val out = Mat()
        Imgproc.cvtColor(bgr, g, Imgproc.COLOR_BGR2GRAY)
        Imgproc.cvtColor(g, out, Imgproc.COLOR_GRAY2BGR)
        g.release()
        return out
    }

    @Suppress("unused")
    private fun clahe(): CLAHE = Imgproc.createCLAHE(1.6, Size(8.0, 8.0))

    /*
     * Kurva nada di bawah DIUKUR (kotak hitam) dari tangkapan layar hasil Notebloc milik user:
     * foto yang sama dalam mode Asli, Foto, dan Notebloc (26 Okt 2026), lalu diuji silang pada foto lain.
     * Tidak ada kode Notebloc yang dipakai. Skrip ukur: scratchpad tools/profile_fit.py & profile_cross.py.
     */
    private val TONE_FOTO = intArrayOf(1,1,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,3,3,3,3,3,3,4,4,5,5,6,6,6,7,8,8,9,10,11,11,12,13,14,15,15,16,17,18,19,20,21,22,23,25,26,27,27,29,29,30,31,32,33,34,35,36,37,38,39,41,42,43,45,46,47,48,50,51,52,53,54,55,56,57,58,59,61,62,63,65,66,67,68,69,71,72,73,74,75,77,78,79,80,81,83,84,85,86,88,89,90,91,93,94,95,96,98,99,100,101,102,103,104,105,106,108,109,110,111,113,114,115,116,118,119,120,122,123,124,125,127,128,129,131,132,133,135,136,137,139,140,141,143,144,145,147,148,149,151,152,153,154,156,157,158,159,161,162,163,164,166,167,169,170,171,172,174,175,176,178,179,180,181,183,184,185,187,188,189,190,192,193,195,196,197,198,200,201,202,204,205,206,207,209,210,212,213,214,215,217,218,219,220,222,223,224,226,227,228,229,231,232,233,235,236,237,239,240,241,243,244,245,246,247,248,249,250,251,252,253,253,254,254,254,254,254,254,254,254,254)
    private val TONE_DOKUMEN = intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,2,2,3,3,3,3,4,4,5,5,5,6,6,7,7,8,8,9,10,10,11,12,12,13,13,15,15,17,17,18,19,20,21,22,22,23,24,26,27,28,29,30,31,33,34,35,36,37,38,39,41,42,44,45,46,48,49,50,51,53,54,55,57,58,60,61,63,65,66,68,70,72,73,74,76,78,79,81,82,85,86,88,90,91,93,94,96,98,99,101,102,104,106,107,109,111,112,114,116,118,120,122,124,126,127,129,131,133,135,136,139,141,142,145,146,148,150,151,153,155,157,159,161,163,164,166,168,170,173,174,176,178,180,182,184,186,188,190,192,194,196,198,200,202,204,206,208,211,213,215,217,219,222,224,227,230,232,236,238,241,244,247,249,251,252,253,254,254,254,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255)

    private fun lutMat(curve: IntArray): Mat {
        val m = Mat(1, 256, CvType.CV_8UC1)
        m.put(0, 0, ByteArray(256) { curve[it].toByte() })
        return m
    }

    /** Persentil satu kanal 8-bit lewat histogram. */
    private fun percentile(ch: Mat, p: Double): Double {
        val bytes = ByteArray((ch.total()).toInt())
        ch.get(0, 0, bytes)
        val hist = IntArray(256)
        for (b in bytes) hist[b.toInt() and 0xff]++
        val target = (bytes.size * p / 100.0).toLong()
        var acc = 0L
        for (i in 0..255) { acc += hist[i]; if (acc >= target) return i.toDouble() }
        return 255.0
    }

    /**
     * Foto (meniru mode Foto Notebloc): tiap kanal diregang dari persentil 1–99 ke 40–250
     * (sekaligus menetralkan warna kekuningan lampu), lalu kurva nada hasil ukur.
     */
    private fun foto(bgr: Mat): Mat {
        // persentil dihitung dari salinan kecil agar cepat
        val small = Mat()
        val s = 600.0 / max(bgr.cols(), bgr.rows())
        if (s < 1) Imgproc.resize(bgr, small, Size(), s, s, Imgproc.INTER_AREA) else bgr.copyTo(small)
        val sch = ArrayList<Mat>(); Core.split(small, sch)
        val ch = ArrayList<Mat>(); Core.split(bgr, ch)
        for (c in 0 until 3) {
            val lo = percentile(sch[c], 1.0); val hi = percentile(sch[c], 99.0)
            val scale = (250.0 - 40.0) / max(1.0, hi - lo)
            ch[c].convertTo(ch[c], -1, scale, 40.0 - lo * scale)
        }
        sch.forEach { it.release() }; small.release()
        val out = Mat()
        Core.merge(ch, out)
        ch.forEach { it.release() }
        val lut = lutMat(TONE_FOTO)
        Core.LUT(out, lut, out)
        lut.release()
        return out
    }

    private fun fotoGray(bgr: Mat): Mat {
        val f = foto(bgr)
        val g = Mat()
        Imgproc.cvtColor(f, g, Imgproc.COLOR_BGR2GRAY)
        f.release()
        val out = Mat()
        Imgproc.cvtColor(g, out, Imgproc.COLOR_GRAY2BGR)
        g.release()
        return out
    }

    /**
     * Dokumen (meniru mode Notebloc): piksel ÷ taksiran latar × 235 (kertas rata putih, bayangan hilang),
     * lalu kurva nada hasil ukur (latar → 255, tinta pekat). Warna logo/stempel tetap.
     */
    private fun dokumen(bgr: Mat): Mat {
        val h = bgr.rows(); val w = bgr.cols()
        val k = max(31, (min(h, w) / 16) or 1)
        val small = Mat()
        Imgproc.resize(bgr, small, Size(max(1, w / 4).toDouble(), max(1, h / 4).toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        Imgproc.medianBlur(small, small, 21)
        Imgproc.GaussianBlur(small, small, Size(0.0, 0.0), k / 8.0)
        val bg = Mat()
        Imgproc.resize(small, bg, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        small.release()
        val srcF = Mat(); val bgF = Mat()
        bgr.convertTo(srcF, CvType.CV_32FC3)
        bg.convertTo(bgF, CvType.CV_32FC3, 1.0, 1.0)
        bg.release()
        val divided = Mat()
        Core.divide(srcF, bgF, divided, 235.0)
        srcF.release(); bgF.release()
        val out = Mat()
        divided.convertTo(out, CvType.CV_8UC3)
        divided.release()
        val lut = lutMat(TONE_DOKUMEN)
        Core.LUT(out, lut, out)
        lut.release()
        return out
    }

    private fun dokumenHitam(bgr: Mat): Mat {
        val d = dokumen(bgr)
        val g = Mat()
        Imgproc.cvtColor(d, g, Imgproc.COLOR_BGR2GRAY)
        d.release()
        val out = Mat()
        Imgproc.cvtColor(g, out, Imgproc.COLOR_GRAY2BGR)
        g.release()
        return out
    }

    private fun saturate(bgr: Mat, factor: Double) {
        val hsv = Mat()
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
        val ch = ArrayList<Mat>()
        Core.split(hsv, ch)
        ch[1].convertTo(ch[1], -1, factor, 0.0)
        Core.merge(ch, hsv)
        ch.forEach { it.release() }
        Imgproc.cvtColor(hsv, bgr, Imgproc.COLOR_HSV2BGR)
        hsv.release()
    }

    // ---------- cadangan tanpa OpenCV (ColorMatrix) ----------

    private fun fallback(src: Bitmap, m: FilterMode): Bitmap {
        val cm = ColorMatrix()
        when (m.look) {
            Look.ASLI -> Unit
            Look.FOTO -> cm.postConcat(contrast(1.12f, 8f))
            Look.DOKUMEN -> cm.postConcat(contrast(1.35f, 40f))
        }
        if (!m.color) cm.postConcat(ColorMatrix().apply { setSaturation(0f) })
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        src.recycle()
        return out
    }

    private fun contrast(c: Float, b: Float): ColorMatrix {
        val t = (1f - c) * 128f + b
        return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }

    @Suppress("unused")
    private val unusedScalar = Scalar(0.0)
}
