package id.rwi.scanpdfin.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bingkai segi empat dokumen: 8 angka 0..1 (x,y) urutan kiri-atas, kanan-atas, kanan-bawah, kiri-bawah,
 * relatif terhadap lebar/tinggi foto.
 */
object Quad {
    val FULL = listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
    val INSET = listOf(0.06f, 0.06f, 0.94f, 0.06f, 0.94f, 0.94f, 0.06f, 0.94f)

    fun points(c: List<Float>) = (0 until 4).map { PointF(c[it * 2], c[it * 2 + 1]) }
    fun of(p: List<PointF>) = p.flatMap { listOf(it.x, it.y) }

    /** Urutkan 4 titik bebas menjadi kiri-atas, kanan-atas, kanan-bawah, kiri-bawah. */
    fun order(p: List<PointF>): List<Float> {
        require(p.size == 4)
        val tl = p.minBy { it.x + it.y }
        val br = p.maxBy { it.x + it.y }
        val rest = p.filter { it !== tl && it !== br }
        val (tr, bl) = if (rest.size == 2) {
            if (rest[0].x - rest[0].y > rest[1].x - rest[1].y) rest[0] to rest[1] else rest[1] to rest[0]
        } else {
            p.minBy { it.y - it.x } to p.maxBy { it.y - it.x }
        }
        return of(listOf(tl, tr, br, bl))
    }

    /** Putar 90° searah jarum jam: (x,y) → (1−y, x). */
    fun rotateCw(c: List<Float>): List<Float> = order(points(c).map { PointF(1f - it.y, it.x) })

    fun isFull(c: List<Float>) = c.zip(FULL).all { (a, b) -> abs(a - b) < 0.002f }

    /** Sah bila cembung (tidak bersilangan) dan luasnya ≥ 2% foto. [aspect] = lebar/tinggi foto. */
    fun isValid(c: List<Float>, aspect: Float = 1f): Boolean {
        val p = points(c).map { PointF(it.x * aspect, it.y) }
        var sign = 0
        for (i in 0 until 4) {
            val a = p[i]; val b = p[(i + 1) % 4]; val d = p[(i + 2) % 4]
            val cross = (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
            if (abs(cross) < 1e-6f) return false
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        var area = 0f
        for (i in 0 until 4) { val a = p[i]; val b = p[(i + 1) % 4]; area += a.x * b.y - b.x * a.y }
        return abs(area) / 2f >= 0.02f * aspect
    }
}

/** Deteksi tepi dokumen dengan OpenCV (Canny + kontur + pendekatan poligon). */
object EdgeDetector {
    @Volatile private var ready: Boolean? = null

    fun init(): Boolean {
        ready?.let { return it }
        val ok = try { OpenCVLoader.initLocal() } catch (t: Throwable) { Log.w("ScanPdfin", "OpenCV gagal dimuat", t); false }
        ready = ok
        return ok
    }

    /** Kembalikan bingkai dokumen (lihat [Quad]) atau null bila tidak yakin. */
    fun detect(src: Bitmap, maxSide: Int = 480): List<Float>? {
        if (!init()) return null
        val scale = maxSide.toFloat() / max(src.width, src.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(src, max(1, (src.width * scale).roundToInt()), max(1, (src.height * scale).roundToInt()), true) else src
        val rgba = Mat(); val gray = Mat(); val edges = Mat(); val hier = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            val med = median(gray)
            Imgproc.Canny(gray, edges, max(10.0, 0.66 * med), min(255.0, max(40.0, 1.33 * med)))
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, edges, kernel, Point(-1.0, -1.0), 2)
            kernel.release()
            Imgproc.findContours(edges, contours, hier, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val w = small.width.toDouble(); val h = small.height.toDouble()
            val total = w * h
            val sorted = contours.map { it to Imgproc.contourArea(it) }.sortedByDescending { it.second }.take(15)
            var best: List<Point>? = null
            for ((c, area) in sorted) {
                if (area < total * 0.10) break
                if (area > total * 0.985) continue // bingkai foto itu sendiri
                val c2 = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2, approx, 0.02 * peri, true)
                val pts = approx.toArray()
                c2.release(); approx.release()
                if (pts.size == 4) {
                    val mp = MatOfPoint(*pts)
                    val convex = Imgproc.isContourConvex(mp)
                    mp.release()
                    if (convex) { best = pts.toList(); break }
                }
            }
            if (best == null) {
                // cadangan: persegi panjang terkecil yang melingkupi kontur terbesar
                // Hanya diterima bila konturnya benar-benar mirip persegi panjang (isi ≥ 85% kotaknya)
                // dan tidak menutupi hampir seluruh foto — mencegah "dokumen palsu" dari latar acak.
                val (c, area) = sorted.firstOrNull { it.second <= total * 0.985 } ?: return null
                if (area < total * 0.18 || area > total * 0.80) return null
                val c2 = MatOfPoint2f(*c.toArray())
                val r = Imgproc.minAreaRect(c2)
                c2.release()
                val rectArea = r.size.width * r.size.height
                if (rectArea <= 0 || area / rectArea < 0.85) return null
                val arr = Array(4) { Point() }
                r.points(arr)
                best = arr.toList()
            }
            val norm = best.map { PointF((it.x / w).toFloat().coerceIn(0f, 1f), (it.y / h).toFloat().coerceIn(0f, 1f)) }
            val q = Quad.order(norm)
            return if (Quad.isValid(q, (w / h).toFloat())) q else null
        } catch (t: Throwable) {
            Log.w("ScanPdfin", "Deteksi tepi gagal", t)
            return null
        } finally {
            contours.forEach { it.release() }
            rgba.release(); gray.release(); edges.release(); hier.release()
            if (small !== src) small.recycle()
        }
    }

    private fun median(gray: Mat): Double {
        val bytes = ByteArray((gray.total() * gray.channels()).toInt())
        gray.get(0, 0, bytes)
        val hist = IntArray(256)
        for (b in bytes) hist[b.toInt() and 0xff]++
        var acc = 0
        val half = bytes.size / 2
        for (i in 0..255) { acc += hist[i]; if (acc >= half) return i.toDouble() }
        return 128.0
    }
}

/** Koreksi perspektif: potong segi empat lalu luruskan menjadi persegi panjang. */
object Perspective {
    private const val MAX_OUT = 4000

    fun warp(src: Bitmap, c: List<Float>): Bitmap {
        if (Quad.isFull(c)) return src.copy(Bitmap.Config.ARGB_8888, false)
        val w = src.width.toDouble(); val h = src.height.toDouble()
        val p = Quad.points(c).map { Point(it.x * w, it.y * h) }
        var outW = max(dist(p[0], p[1]), dist(p[3], p[2]))
        var outH = max(dist(p[0], p[3]), dist(p[1], p[2]))
        val limit = max(outW, outH) / MAX_OUT
        if (limit > 1) { outW /= limit; outH /= limit }
        val ow = max(1, outW.roundToInt()); val oh = max(1, outH.roundToInt())
        return try {
            if (!EdgeDetector.init()) error("OpenCV tidak tersedia")
            warpCv(src, p, ow, oh)
        } catch (t: Throwable) {
            Log.w("ScanPdfin", "warpPerspective gagal, pakai Matrix", t)
            warpMatrix(src, p, ow, oh)
        }
    }

    private fun warpCv(src: Bitmap, p: List<Point>, ow: Int, oh: Int): Bitmap {
        val srcPts = MatOfPoint2f(*p.toTypedArray())
        val dstPts = MatOfPoint2f(Point(0.0, 0.0), Point(ow.toDouble(), 0.0), Point(ow.toDouble(), oh.toDouble()), Point(0.0, oh.toDouble()))
        val m = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val inMat = Mat(); val outMat = Mat()
        try {
            Utils.bitmapToMat(src, inMat)
            Imgproc.warpPerspective(inMat, outMat, m, Size(ow.toDouble(), oh.toDouble()), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
            val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outMat, out)
            return out
        } finally {
            srcPts.release(); dstPts.release(); m.release(); inMat.release(); outMat.release()
        }
    }

    /** Cadangan tanpa OpenCV: matriks perspektif bawaan Android. */
    private fun warpMatrix(src: Bitmap, p: List<Point>, ow: Int, oh: Int): Bitmap {
        val from = floatArrayOf(
            p[0].x.toFloat(), p[0].y.toFloat(), p[1].x.toFloat(), p[1].y.toFloat(),
            p[2].x.toFloat(), p[2].y.toFloat(), p[3].x.toFloat(), p[3].y.toFloat(),
        )
        val to = floatArrayOf(0f, 0f, ow.toFloat(), 0f, ow.toFloat(), oh.toFloat(), 0f, oh.toFloat())
        val m = Matrix().apply { setPolyToPoly(from, 0, to, 0, 4) }
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}
