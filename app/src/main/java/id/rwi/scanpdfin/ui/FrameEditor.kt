package id.rwi.scanpdfin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.image.Quad
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Editor bingkai segi empat di atas foto.
 * - 4 pegangan sudut: tarik bebas (menempel ke sudut hasil deteksi bila [snap]).
 * - 4 pegangan tengah sisi: tarik untuk menggeser seluruh garis sisi tetap lurus (sejajar).
 * - Kaca pembesar muncul saat menarik.
 * [corners] = 8 angka 0..1 (lihat Quad). [onChange] tiap gerak, [onCommit] saat jari diangkat.
 */
@Composable
fun FrameEditor(
    image: ImageBitmap,
    corners: List<Float>,
    auto: List<Float>?,
    snap: Boolean,
    onChange: (List<Float>) -> Unit,
    onCommit: (List<Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val padPx = with(density) { 30.dp.toPx() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val rect = remember(box, image.width, image.height) { fitRect(box, image.width, image.height, padPx) }
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error

    val latestCorners by rememberUpdatedState(corners)
    val latestRect by rememberUpdatedState(rect)
    val latestAuto by rememberUpdatedState(auto)
    val latestSnap by rememberUpdatedState(snap)
    val latestOnChange by rememberUpdatedState(onChange)
    val latestOnCommit by rememberUpdatedState(onCommit)

    var active by remember { mutableIntStateOf(-1) } // 0..3 sudut, 4..7 sisi (4 = atas, 5 = kanan, 6 = bawah, 7 = kiri)
    var focus by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(Unit) {
            val cornerR = 40.dp.toPx()
            val edgeR = 34.dp.toPx()
            val snapR = 18.dp.toPx()
            var work = listOf<Offset>()
            var raw = Offset.Zero
            detectDragGestures(
                onDragStart = { o ->
                    val r = latestRect
                    work = toView(latestCorners, r)
                    val ci = work.indices.minBy { (work[it] - o).getDistance() }
                    active = if ((work[ci] - o).getDistance() <= cornerR) {
                        raw = work[ci]; ci
                    } else {
                        val mids = (0 until 4).map { (work[it] + work[(it + 1) % 4]) / 2f }
                        val ei = mids.indices.minBy { (mids[it] - o).getDistance() }
                        if ((mids[ei] - o).getDistance() <= edgeR) { raw = mids[ei]; 4 + ei } else -1
                    }
                    focus = if (active >= 0) raw else null
                },
                onDrag = { change, delta ->
                    if (active < 0) return@detectDragGestures
                    change.consume()
                    val r = latestRect
                    val pts = work.toMutableList()
                    if (active < 4) {
                        raw += delta
                        var p = clampTo(raw, r)
                        val a = latestAuto
                        if (latestSnap && a != null) {
                            val target = toView(a, r)[active]
                            if ((target - p).getDistance() < snapR) p = target
                        }
                        pts[active] = p
                        focus = p
                    } else {
                        val i = active - 4
                        val j = (i + 1) % 4
                        val dir = pts[j] - pts[i]
                        val len = dir.getDistance()
                        if (len < 1f) return@detectDragGestures
                        val normal = Offset(-dir.y / len, dir.x / len)
                        val d = delta.x * normal.x + delta.y * normal.y
                        // gerak sejajar: kedua ujung bergeser sama jauh; dibatasi agar tetap di dalam foto
                        var mv = normal * d
                        val pi = pts[i] + mv; val pj = pts[j] + mv
                        if (!inside(pi, r) || !inside(pj, r)) {
                            val ci = clampTo(pi, r) - pts[i]; val cj = clampTo(pj, r) - pts[j]
                            mv = if (ci.getDistance() < cj.getDistance()) ci else cj
                        }
                        pts[i] = pts[i] + mv
                        pts[j] = pts[j] + mv
                        focus = (pts[i] + pts[j]) / 2f
                    }
                    work = pts
                    latestOnChange(toNorm(pts, r))
                },
                onDragEnd = {
                    if (active >= 0) latestOnCommit(toNorm(work, latestRect))
                    active = -1; focus = null
                },
                onDragCancel = {
                    if (active >= 0) latestOnCommit(toNorm(work, latestRect))
                    active = -1; focus = null
                },
            )
        },
    ) {
        if (rect.width <= 0f) return@Canvas
        drawImage(
            image,
            srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
        val pts = toView(corners, rect)
        val valid = Quad.isValid(corners, image.width.toFloat() / image.height)
        val color = if (valid) primary else error

        val quad = Path().apply { moveTo(pts[0].x, pts[0].y); for (k in 1..3) lineTo(pts[k].x, pts[k].y); close() }
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(rect)
            addPath(quad)
        }
        drawPath(outside, Color.Black.copy(alpha = 0.5f))
        drawPath(quad, color, style = Stroke(2.5.dp.toPx()))

        // pegangan tengah sisi: lonjong searah garis
        val hw = 15.dp.toPx(); val hh = 5.5.dp.toPx()
        for (e in 0 until 4) {
            val a = pts[e]; val b = pts[(e + 1) % 4]
            val mid = (a + b) / 2f
            val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
            rotate(angle, pivot = mid) {
                val tl = Offset(mid.x - hw, mid.y - hh)
                drawRoundRect(if (active == 4 + e) color else Color.White, tl, Size(hw * 2, hh * 2), CornerRadius(hh))
                drawRoundRect(color, tl, Size(hw * 2, hh * 2), CornerRadius(hh), style = Stroke(2.dp.toPx()))
            }
        }
        // pegangan sudut
        val r = 11.dp.toPx()
        pts.forEachIndexed { i, p ->
            if (active == i) drawCircle(color.copy(alpha = 0.28f), r * 2.2f, p)
            drawCircle(Color.White, r, p)
            drawCircle(color, r, p, style = Stroke(2.5.dp.toPx()))
        }

        focus?.let { drawLoupe(image, rect, it, pts, color) }
    }
}

/** Kaca pembesar ×2,5 di pojok atas (berpindah sisi agar tidak tertutup jari). */
private fun DrawScope.drawLoupe(image: ImageBitmap, rect: Rect, f: Offset, pts: List<Offset>, color: Color) {
    val d = 112.dp.toPx()
    val margin = 12.dp.toPx()
    val lx = if (f.x < size.width / 2f) size.width - d - margin else margin
    val ly = if (f.y < d + margin * 3) size.height - d - margin else margin
    val scale = rect.width / image.width
    val zoom = 2.5f
    val src = (d / (scale * zoom)).roundToInt().coerceIn(8, min(image.width, image.height))
    val cx = (f.x - rect.left) / scale
    val cy = (f.y - rect.top) / scale
    val sx = (cx - src / 2f).roundToInt().coerceIn(0, image.width - src)
    val sy = (cy - src / 2f).roundToInt().coerceIn(0, image.height - src)
    fun map(p: Offset): Offset {
        val ix = (p.x - rect.left) / scale
        val iy = (p.y - rect.top) / scale
        return Offset(lx + (ix - sx) * d / src, ly + (iy - sy) * d / src)
    }
    val circle = Path().apply { addOval(Rect(Offset(lx, ly), Size(d, d))) }
    clipPath(circle) {
        drawRect(Color.Black, Offset(lx, ly), Size(d, d))
        drawImage(
            image, srcOffset = IntOffset(sx, sy), srcSize = IntSize(src, src),
            dstOffset = IntOffset(lx.roundToInt(), ly.roundToInt()), dstSize = IntSize(d.roundToInt(), d.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
        val lp = pts.map(::map)
        for (k in 0 until 4) drawLine(color, lp[k], lp[(k + 1) % 4], 2.dp.toPx())
        val c = map(f)
        val arm = 12.dp.toPx()
        drawLine(Color.White, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 1.5.dp.toPx())
        drawLine(Color.White, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 1.5.dp.toPx())
        drawCircle(color, 3.dp.toPx(), c)
    }
    drawCircle(Color.White, d / 2f, Offset(lx + d / 2f, ly + d / 2f), style = Stroke(3.dp.toPx()))
}

private fun fitRect(box: IntSize, iw: Int, ih: Int, pad: Float): Rect {
    if (box.width <= 0 || box.height <= 0 || iw <= 0 || ih <= 0) return Rect.Zero
    val s = min((box.width - 2 * pad) / iw, (box.height - 2 * pad) / ih).coerceAtLeast(0.01f)
    val w = iw * s; val h = ih * s
    val l = (box.width - w) / 2f; val t = (box.height - h) / 2f
    return Rect(l, t, l + w, t + h)
}

private fun toView(c: List<Float>, r: Rect) = (0 until 4).map { Offset(r.left + c[it * 2] * r.width, r.top + c[it * 2 + 1] * r.height) }

private fun toNorm(p: List<Offset>, r: Rect): List<Float> = p.flatMap {
    listOf(((it.x - r.left) / r.width).coerceIn(0f, 1f), ((it.y - r.top) / r.height).coerceIn(0f, 1f))
}

private fun inside(p: Offset, r: Rect) = p.x >= r.left - 0.5f && p.x <= r.right + 0.5f && p.y >= r.top - 0.5f && p.y <= r.bottom + 0.5f
private fun clampTo(p: Offset, r: Rect) = Offset(p.x.coerceIn(r.left, r.right), p.y.coerceIn(r.top, r.bottom))
