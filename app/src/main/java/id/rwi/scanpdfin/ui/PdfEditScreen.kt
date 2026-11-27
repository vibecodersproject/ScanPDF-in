@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package id.rwi.scanpdfin.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.pdf.Annot
import id.rwi.scanpdfin.pdf.TextRun
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Tool(val label: String, val hint: String) {
    UBAH("Ubah tulisan", "Ketuk tulisan bergaris biru untuk mengubahnya"),
    PILIH("Geser", "Usap kiri/kanan untuk pindah halaman"),
    PENA("Pena", "Pena: gambar langsung di halaman"),
    STABILO("Stabilo", "Stabilo: sapukan di atas tulisan"),
    TUTUP("Tutup", "Tutup: tarik kotak di atas tulisan (hanya menutupi tampilan, teks lama masih tersimpan)"),
}

/** Bitmap putih kecil untuk kotak penutup (diregang saat digambar). */
private val whiteBox: Bitmap by lazy { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) } }

private val inkColors = listOf(Color(0xFF111111), Color(0xFF1546C0), Color(0xFFC62828))
private val highlightColor = Color(0xFFFFEB3B)

/** Edit satu halaman PDF: tempel tanda tangan/teks, coret pena, stabilo. Ditanam saat "Selesai". */
@Composable
fun PdfEditScreen(vm: AppViewModel, startPage: Int, start: String = "edit") {
    val doc by vm.pdf.doc.collectAsState()
    val d = doc
    LaunchedEffect(d == null) { if (d == null) vm.back() }
    d ?: return
    if (d.pageCount <= 0) { LaunchedEffect(Unit) { vm.back() }; return }
    var page by remember { mutableStateOf(startPage.coerceIn(0, d.pageCount - 1)) }
    val annots = remember { mutableStateListOf<Annot>() }
    var allPages by remember { mutableStateOf(setOf<String>()) } // id stempel yang dipasang di semua halaman
    var tool by remember { mutableStateOf(if (start == "edit") Tool.UBAH else Tool.PILIH) }
    var shownBmp by remember { mutableStateOf<Bitmap?>(null) }
    var replaceAsk by remember { mutableStateOf<ReplaceAsk?>(null) }
    var inkColor by remember { mutableStateOf(inkColors[0]) }
    var selected by remember { mutableStateOf<String?>(null) }
    var sigSheet by remember { mutableStateOf(start == "ttd") }
    var drawSig by remember { mutableStateOf(false) }
    var textAsk by remember { mutableStateOf(start == "teks") }
    var exitAsk by remember { mutableStateOf(false) }
    fun goPage(p: Int) { if (p in 0 until d.pageCount) { page = p; selected = null } }

    val pageSize by produceState(initialValue = 595f to 842f, d.file, page) { value = runCatching { vm.pdf.engine.pageSize(d.file, page) }.getOrDefault(595f to 842f) }
    val (pw, ph) = pageSize
    // tulisan asli halaman ini; null = masih dibaca
    val runs by produceState<List<TextRun>?>(initialValue = null, d.file, page) { value = null; value = vm.pdf.engine.textRuns(d.file, page) }

    /** Ketuk saat Ubah tulisan: ubah lagi hasil ganti, atau pilih tulisan asli di titik itu. */
    fun tapText(nx: Float, ny: Float) {
        val done = annots.lastOrNull { it.page == page && it is Annot.Replace && nx in it.x..(it.x + it.w) && ny in it.y..(it.y + it.h) } as? Annot.Replace
        if (done != null) { replaceAsk = ReplaceAsk(null, done); return }
        val slop = 0.012f
        val run = runs.orEmpty().filter { nx in (it.x - slop)..(it.x + it.w + slop) && ny in (it.y - slop)..(it.y + it.h + slop) }
            .minByOrNull { abs((it.y + it.h / 2) - ny) }
        if (run != null) replaceAsk = ReplaceAsk(run, null)
        else if (runs?.isEmpty() == true) vm.toast("Halaman ini berupa gambar (hasil scan). Pakai Tutup lalu Teks.")
    }

    fun applyReplace(ask: ReplaceAsk, text: String, sizePt: Float, bold: Boolean) {
        val old = ask.existing
        if (old != null) {
            val i = annots.indexOfFirst { it.id == old.id }
            if (i >= 0) annots[i] = old.copy(text = text, sizePt = sizePt, bold = bold)
            return
        }
        val r = ask.run ?: return
        if (text == r.text && sizePt == r.sizePt && bold == r.bold) return
        // tutup sedikit lebih lebar dari tulisan agar sisa huruf asli tidak mengintip
        val px = 1.2f / pw; val py = 1.2f / ph
        val x = (r.x - px).coerceAtLeast(0f); val y = (r.y - py).coerceAtLeast(0f)
        val w = (r.w + 2 * px).coerceAtMost(1f - x); val h = (r.h + 2 * py).coerceAtMost(1f - y)
        val (bg, fg) = shownBmp?.let { sampleColors(it, x, y, w, h) } ?: (android.graphics.Color.WHITE to android.graphics.Color.BLACK)
        annots += Annot.Replace(page, x, y, w, h, bg, text, r.text, r.baseline, sizePt, fg, bold, r.serif, r.key)
    }

    fun addStamp(bmp: Bitmap, widthNorm: Float) {
        val wN = widthNorm.coerceIn(0.02f, 0.95f)
        val hN = wN * pw * bmp.height / bmp.width / ph
        // bila kotak penutup sedang dipilih, teks diletakkan di dalam kotak itu (rata kiri, tengah vertikal)
        val box = annots.firstOrNull { it.id == selected } as? Annot.Stamp
        val (x, y) = if (box != null && box.free) (box.x + 0.005f) to (box.y + (box.h - hN) / 2f) else ((1f - wN) / 2f) to ((1f - hN) / 2f)
        val a = Annot.Stamp(page, bmp, x, y, wN, hN.coerceAtMost(0.95f))
        annots += a
        selected = a.id
        tool = Tool.PILIH
    }

    fun finish() {
        val expanded = annots.flatMap { a ->
            if (a is Annot.Stamp && a.id in allPages) (0 until d.pageCount).map { p -> a.copy(page = p) } else listOf(a)
        }
        if (expanded.isEmpty()) { vm.back(); return }
        vm.pdf.flatten(expanded) { vm.back() }
    }

    BackHandler { if (annots.isNotEmpty()) exitAsk = true else vm.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit PDF") },
                navigationIcon = { IconButton(onClick = { if (annots.isNotEmpty()) exitAsk = true else vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } },
                actions = { TextButton(onClick = { finish() }) { Text("Selesai") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp)) {
                    // pindah halaman (di bawah, sesuai saran; juga bisa usap kiri/kanan)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { goPage(page - 1) }, enabled = page > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Halaman sebelumnya") }
                        Text("Halaman ${page + 1} dari ${d.pageCount}", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { goPage(page + 1) }, enabled = page < d.pageCount - 1) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Halaman berikutnya") }
                    }
                    val sel = annots.firstOrNull { it.id == selected } as? Annot.Stamp
                    if (sel != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!sel.free) FilterChip(selected = sel.id in allPages, onClick = { allPages = if (sel.id in allPages) allPages - sel.id else allPages + sel.id }, label = { Text("Pasang di semua halaman") })
                        else TextButton(onClick = { textAsk = true }) { Icon(Icons.Outlined.TextFields, null); Text(" Ketik di atas kotak") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { annots.removeAll { it.id == sel.id }; selected = null }) { Icon(Icons.Outlined.Delete, null); Text(" Hapus") }
                    }
                    if (tool == Tool.PENA) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        inkColors.forEach { c ->
                            Box(Modifier.size(28.dp).clip(CircleShape).background(c).border(if (c == inkColor) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable { inkColor = c })
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        EditTool(Icons.Outlined.EditNote, "Ubah tulisan", tool == Tool.UBAH) { tool = if (tool == Tool.UBAH) Tool.PILIH else Tool.UBAH; selected = null }
                        EditTool(Icons.Outlined.Draw, "Tanda tangan", false) { sigSheet = true }
                        EditTool(Icons.Outlined.TextFields, "Teks", false) { textAsk = true }
                        EditTool(Icons.Outlined.Edit, "Pena", tool == Tool.PENA) { tool = if (tool == Tool.PENA) Tool.PILIH else Tool.PENA; selected = null }
                        EditTool(Icons.Outlined.BorderColor, "Stabilo", tool == Tool.STABILO) { tool = if (tool == Tool.STABILO) Tool.PILIH else Tool.STABILO; selected = null }
                        EditTool(Icons.Outlined.CropSquare, "Tutup", tool == Tool.TUTUP) { tool = if (tool == Tool.TUTUP) Tool.PILIH else Tool.TUTUP; selected = null }
                        EditTool(Icons.AutoMirrored.Outlined.Undo, "Urungkan", false) { if (annots.isNotEmpty()) { val last = annots.removeAt(annots.lastIndex); if (last.id == selected) selected = null } }
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).background(Color(0xFF3A3A3A)).padding(12.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val ratio = pw / ph
            val boxRatio = maxWidth / maxHeight
            val wDp = if (ratio > boxRatio) maxWidth else maxHeight * ratio
            val renderPx = with(density) { wDp.toPx() }.roundToInt().coerceIn(300, 2200)
            val bmp by produceState<Bitmap?>(initialValue = null, d.file, page, renderPx) { value = null; value = vm.pdf.engine.render(d.file, page, renderPx) }
            SideEffect { shownBmp = bmp }
            Box(Modifier.width(wDp).aspectRatio(ratio).background(Color.White)) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
                AnnotLayer(
                    annots = annots, page = page, pageWidthPt = pw, tool = tool, inkColor = inkColor,
                    runs = if (tool == Tool.UBAH) runs.orEmpty() else emptyList(), onTapText = { x, y -> tapText(x, y) },
                    selected = selected, onSelect = { selected = it },
                    onReplace = { a -> val i = annots.indexOfFirst { it.id == a.id }; if (i >= 0) annots[i] = a },
                    onAddInk = { annots += it },
                    onAddBox = { x, y, w, h ->
                        val box = Annot.Stamp(page, whiteBox, x, y, w, h, free = true)
                        annots += box; selected = box.id; tool = Tool.PILIH
                    },
                    onSwipe = { dir -> goPage(page + dir) },
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                val hint = when {
                    tool != Tool.UBAH -> tool.hint
                    runs == null -> "Membaca tulisan di halaman…"
                    runs!!.isEmpty() -> "Halaman ini gambar (hasil scan): tulisannya tidak terbaca. Pakai Tutup lalu Teks."
                    else -> tool.hint
                }
                Text(hint, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }

    if (sigSheet) SignaturePicker(vm, onDismiss = { sigSheet = false }, onNew = { sigSheet = false; drawSig = true }) { bmp -> sigSheet = false; addStamp(bmp, 0.35f) }
    if (drawSig) SignaturePadDialog(onDismiss = { drawSig = false }) { bmp ->
        drawSig = false
        vm.pdf.store.saveSignature(bmp)
        addStamp(bmp, 0.35f)
    }
    if (textAsk) TextStampDialog(onDismiss = { textAsk = false }) { text, sizePt, color ->
        textAsk = false
        val scale = 4f // piksel per poin agar tajam saat diperbesar
        val bmp = renderText(text, sizePt * scale, color.toArgb())
        addStamp(bmp, (bmp.width / scale) / pw)
    }
    replaceAsk?.let { ask ->
        ReplaceTextDialog(
            ask = ask,
            onDismiss = { replaceAsk = null },
            onRemoveEdit = { annots.removeAll { it.id == ask.existing?.id }; replaceAsk = null },
        ) { text, size, bold -> replaceAsk = null; applyReplace(ask, text, size, bold) }
    }
    if (exitAsk) ConfirmDialog("Buang perubahan?", "Tulisan yang diubah, tanda tangan, teks, dan coretan yang belum ditekan Selesai akan hilang.", confirmLabel = "Buang", onDismiss = { exitAsk = false }) {
        exitAsk = false; vm.back()
    }
}

/** Yang sedang diubah: tulisan asli [run] (baru) atau hasil ubah sebelumnya [existing]. */
private data class ReplaceAsk(val run: TextRun?, val existing: Annot.Replace?)

/** Warna latar (tepi kotak) dan warna tulisan (piksel paling beda dari latar) di kotak 0..1. */
private fun sampleColors(bmp: Bitmap, x: Float, y: Float, w: Float, h: Float): Pair<Int, Int> {
    val l = (x * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
    val t = (y * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
    val r = ((x + w) * bmp.width).roundToInt().coerceIn(l + 1, bmp.width)
    val b = ((y + h) * bmp.height).roundToInt().coerceIn(t + 1, bmp.height)
    fun lum(c: Int) = android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114
    // latar: median kecerahan piksel di sepanjang tepi kotak
    val edge = ArrayList<Int>()
    val stepX = max(1, (r - l) / 40); val stepY = max(1, (b - t) / 10)
    for (px in l until r step stepX) { edge += bmp.getPixel(px, t); edge += bmp.getPixel(px, b - 1) }
    for (py in t until b step stepY) { edge += bmp.getPixel(l, py); edge += bmp.getPixel(r - 1, py) }
    val bg = edge.sortedBy { lum(it) }[edge.size / 2]
    fun dist(a: Int, c: Int) = abs(android.graphics.Color.red(a) - android.graphics.Color.red(c)) + abs(android.graphics.Color.green(a) - android.graphics.Color.green(c)) + abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(c))
    var fg = android.graphics.Color.BLACK; var best = -1
    val sx = max(1, (r - l) / 120); val sy = max(1, (b - t) / 30)
    for (py in t until b step sy) for (px in l until r step sx) {
        val c = bmp.getPixel(px, py); val dd = dist(c, bg)
        if (dd > best) { best = dd; fg = c }
    }
    if (best < 60) fg = if (lum(bg) > 128_000) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    return (bg or 0xFF000000.toInt()) to (fg or 0xFF000000.toInt())
}

@Composable
private fun ReplaceTextDialog(ask: ReplaceAsk, onDismiss: () -> Unit, onRemoveEdit: () -> Unit, onDone: (String, Float, Boolean) -> Unit) {
    val original = ask.existing?.original ?: ask.run?.text ?: ""
    var text by remember { mutableStateOf(ask.existing?.text ?: original) }
    var size by remember { mutableStateOf(ask.existing?.sizePt ?: ask.run?.sizePt ?: 11f) }
    var bold by remember { mutableStateOf(ask.existing?.bold ?: ask.run?.bold ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah tulisan") },
        text = {
            Column {
                Text("Asli: $original", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it.replace("\n", " ") }, label = { Text("Tulisan baru") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ukuran", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { size = (size - 0.5f).coerceAtLeast(4f) }) { Icon(Icons.Outlined.Remove, "Perkecil huruf") }
                    Text("${if (size % 1f == 0f) size.toInt().toString() else size.toString()} pt", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { size = (size + 0.5f).coerceAtMost(72f) }) { Icon(Icons.Outlined.Add, "Perbesar huruf") }
                    Spacer(Modifier.weight(1f))
                    FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("Tebal") })
                }
                Text(
                    "Warna & latar diambil dari tulisan asli. Kosongkan untuk menghapus tulisan. Tulisan lama hanya tertutup (masih bisa dicari); untuk data rahasia, sesudahnya pakai ⋮ → Kecilkan ukuran → Kuat.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ask.existing != null) TextButton(onClick = onRemoveEdit) { Text("Kembalikan tulisan asli") }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(text.trim(), size, bold) }) { Text(if (text.isBlank()) "Hapus tulisan" else "Ganti") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun EditTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, softWrap = false)
    }
}

/** Lapisan anotasi di atas halaman: gambar, pilih, geser, ubah ukuran, coret. */
@Composable
private fun AnnotLayer(
    annots: List<Annot>, page: Int, pageWidthPt: Float, tool: Tool, inkColor: Color,
    runs: List<TextRun>, onTapText: (Float, Float) -> Unit,
    selected: String?, onSelect: (String?) -> Unit, onReplace: (Annot) -> Unit, onAddInk: (Annot.Ink) -> Unit,
    onAddBox: (Float, Float, Float, Float) -> Unit, onSwipe: (Int) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val live = remember { mutableStateListOf<Offset>() }
    val bitmaps = remember { mutableMapOf<String, ImageBitmap>() }
    val cur by rememberUpdatedState(annots)
    val sel by rememberUpdatedState(selected)
    val swipe by rememberUpdatedState(onSwipe)
    val handleR = with(LocalDensity.current) { 22.dp.toPx() }
    val swipeMin = with(LocalDensity.current) { 70.dp.toPx() }

    val tapText by rememberUpdatedState(onTapText)
    val drawMod = if (tool == Tool.UBAH) Modifier.pointerInput(page, tool) {
        detectTapGestures { o -> tapText(o.x / size.width, o.y / size.height) }
    }
    else if (tool == Tool.PILIH) Modifier
        .pointerInput(page) {
            detectTapGestures { o ->
                val hit = cur.lastOrNull { it.page == page && it is Annot.Stamp && hitStamp(it, o, size) }
                onSelect(hit?.id)
            }
        }
        .pointerInput(page) {
            var mode = 0 // 1 geser, 2 ubah ukuran, 3 usap pindah halaman
            var target: Annot.Stamp? = null
            var swipeDx = 0f
            detectDragGestures(
                onDragStart = { o ->
                    val s = cur.firstOrNull { it.id == sel } as? Annot.Stamp
                    target = null; mode = 0; swipeDx = 0f
                    if (s != null && s.page == page) {
                        val br = Offset((s.x + s.w) * size.width, (s.y + s.h) * size.height)
                        if ((br - o).getDistance() <= handleR * 1.4f) { target = s; mode = 2 }
                        else if (hitStamp(s, o, size)) { target = s; mode = 1 }
                    }
                    if (target == null) {
                        val hit = cur.lastOrNull { it.page == page && it is Annot.Stamp && hitStamp(it, o, size) } as? Annot.Stamp
                        if (hit != null) { target = hit; mode = 1; onSelect(hit.id) } else mode = 3
                    }
                },
                onDrag = { change, delta ->
                    if (mode == 3) { swipeDx += delta.x; return@detectDragGestures }
                    val t = target ?: return@detectDragGestures
                    change.consume()
                    val dx = delta.x / size.width; val dy = delta.y / size.height
                    val n = if (mode == 1) t.copy(x = (t.x + dx).coerceIn(-t.w * 0.5f, 1f - t.w * 0.5f), y = (t.y + dy).coerceIn(-t.h * 0.5f, 1f - t.h * 0.5f))
                    else if (t.free) t.copy(w = (t.w + dx).coerceIn(0.01f, 1f), h = (t.h + dy).coerceIn(0.005f, 1f))
                    else {
                        val nw = (t.w + dx).coerceIn(0.04f, 1f)
                        t.copy(w = nw, h = nw * t.h / t.w)
                    }
                    target = n
                    onReplace(n)
                },
                onDragEnd = {
                    if (mode == 3 && kotlin.math.abs(swipeDx) > swipeMin) swipe(if (swipeDx < 0) 1 else -1)
                    target = null
                },
            )
        }
    else if (tool == Tool.TUTUP) Modifier.pointerInput(page, tool) {
        // tarik dari satu sudut ke sudut lain → kotak putih penutup
        detectDragGestures(
            onDragStart = { o -> live.clear(); live += o; live += o },
            onDrag = { change, _ -> change.consume(); live[1] = change.position },
            onDragEnd = {
                if (live.size == 2) {
                    val a = live[0]; val b = live[1]
                    val x = (min(a.x, b.x) / size.width).coerceIn(0f, 1f); val y = (min(a.y, b.y) / size.height).coerceIn(0f, 1f)
                    val w = (kotlin.math.abs(b.x - a.x) / size.width).coerceIn(0f, 1f - x); val h = (kotlin.math.abs(b.y - a.y) / size.height).coerceIn(0f, 1f - y)
                    if (w > 0.01f && h > 0.005f) onAddBox(x, y, w, h)
                }
                live.clear()
            },
        )
    }
    else Modifier.pointerInput(page, tool, inkColor) {
        detectDragGestures(
            onDragStart = { o -> live.clear(); live += o },
            onDrag = { change, _ -> change.consume(); live += change.position },
            onDragEnd = {
                if (live.size >= 2) {
                    val pts = live.map { (it.x / size.width).coerceIn(0f, 1f) to (it.y / size.height).coerceIn(0f, 1f) }
                    onAddInk(
                        if (tool == Tool.STABILO) Annot.Ink(page, pts, highlightColor.toArgb(), 12f, alpha = 0.38f)
                        else Annot.Ink(page, pts, inkColor.toArgb(), 1.6f),
                    )
                }
                live.clear()
            },
        )
    }

    Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }.then(drawMod)) {
        val ptToPx = size.width / pageWidthPt
        annots.filter { it.page == page }.forEach { a ->
            when (a) {
                is Annot.Stamp -> {
                    val img = bitmaps.getOrPut(a.id + a.bitmap.hashCode()) { a.bitmap.asImageBitmap() }
                    drawImage(
                        img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset((a.x * size.width).roundToInt(), (a.y * size.height).roundToInt()),
                        dstSize = IntSize(max(1, (a.w * size.width).roundToInt()), max(1, (a.h * size.height).roundToInt())),
                    )
                    if (a.id == selected) {
                        val tl = Offset(a.x * size.width, a.y * size.height)
                        val sz = Size(a.w * size.width, a.h * size.height)
                        drawRect(Color(0xFF0F766E), tl, sz, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                        val br = tl + Offset(sz.width, sz.height)
                        drawCircle(Color.White, handleR * 0.55f, br)
                        drawCircle(Color(0xFF0F766E), handleR * 0.55f, br, style = Stroke(3.dp.toPx()))
                    }
                }
                is Annot.Ink -> drawInk(a.points.map { Offset(it.first * size.width, it.second * size.height) }, Color(a.color).copy(alpha = a.alpha), a.widthPt * ptToPx)
                is Annot.Text -> Unit
                is Annot.Replace -> {
                    drawRect(Color(a.bg), Offset(a.x * size.width, a.y * size.height), Size(a.w * size.width, a.h * size.height))
                    if (a.text.isNotBlank()) drawIntoCanvas { c ->
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = a.color; textSize = a.sizePt * ptToPx
                            typeface = android.graphics.Typeface.create(if (a.serif) android.graphics.Typeface.SERIF else android.graphics.Typeface.SANS_SERIF, if (a.bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                        }
                        c.nativeCanvas.drawText(a.text, a.x * size.width, a.baseline * size.height, p)
                    }
                }
            }
        }
        // tulisan asli yang bisa diubah: garis biru tipis (yang sudah diubah tidak ditandai lagi)
        if (runs.isNotEmpty()) {
            val done = annots.filter { it.page == page && it is Annot.Replace }.map { (it as Annot.Replace).runKey }.toSet()
            runs.filter { it.key !in done }.forEach { r ->
                val tl = Offset(r.x * size.width, r.y * size.height)
                val sz = Size(r.w * size.width, r.h * size.height)
                drawRect(Color(0x221E88E5), tl, sz)
                drawRect(Color(0x991E88E5), tl, sz, style = Stroke(1.dp.toPx()))
            }
        }
        if (live.size >= 2) {
            when (tool) {
                Tool.TUTUP -> {
                    val a = live[0]; val b = live[1]
                    val tl = Offset(min(a.x, b.x), min(a.y, b.y))
                    val sz = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
                    drawRect(Color.White, tl, sz)
                    drawRect(Color(0xFF0F766E), tl, sz, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                }
                Tool.STABILO -> drawInk(live, highlightColor.copy(alpha = 0.38f), 12f * ptToPx)
                else -> drawInk(live, inkColor, 1.6f * ptToPx)
            }
        }
    }
}

private fun DrawScope.drawInk(pts: List<Offset>, color: Color, width: Float) {
    if (pts.size < 2) return
    val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
    drawPath(p, color, style = Stroke(max(1f, width), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun hitStamp(s: Annot.Stamp, o: Offset, size: IntSize): Boolean {
    val x = o.x / size.width; val y = o.y / size.height
    return x >= s.x && x <= s.x + s.w && y >= s.y && y <= s.y + s.h
}

/** Teks → bitmap transparan (mendukung semua huruf, termasuk tanda baca Indonesia). */
private fun renderText(text: String, sizePx: Float, color: Int): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = sizePx; typeface = android.graphics.Typeface.SANS_SERIF }
    val lines = text.split('\n')
    val lineH = paint.fontSpacing
    val w = max(1, (lines.maxOf { paint.measureText(it) } + sizePx * 0.2f).roundToInt())
    val h = max(1, (lineH * lines.size + sizePx * 0.15f).roundToInt())
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    lines.forEachIndexed { i, line -> c.drawText(line, sizePx * 0.1f, -paint.ascent() + i * lineH, paint) }
    return bmp
}

// ---------------------------------------------------------------------
// Pemilih & pembuat tanda tangan
// ---------------------------------------------------------------------

@Composable
private fun SignaturePicker(vm: AppViewModel, onDismiss: () -> Unit, onNew: () -> Unit, onPick: (Bitmap) -> Unit) {
    val sigs by vm.pdf.signatures.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Pilih tanda tangan", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Box(
                        Modifier.size(150.dp, 80.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).clickable(onClick = onNew),
                        contentAlignment = Alignment.Center,
                    ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Add, null); Text("Buat baru", style = MaterialTheme.typography.labelMedium) } }
                }
                items(sigs, key = { it.name }) { f ->
                    val bmp = remember(f) { vm.pdf.store.loadSignature(f) }
                    Box(
                        Modifier.size(150.dp, 80.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { bmp?.let(onPick) },
                    ) { bmp?.let { Image(it.asImageBitmap(), "Tanda tangan", Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit) } }
                }
            }
        }
    }
}

/** Kanvas tanda tangan layar penuh. Hasil: PNG transparan dipotong pas tanda tangan. */
@Composable
fun SignaturePadDialog(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var color by remember { mutableStateOf(inkColors[0]) }
    var thick by remember { mutableStateOf(1) } // 0 tipis 1 sedang 2 tebal
    val widthDp = listOf(2.5f, 4f, 6f)[thick]
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.toPx() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Batal") }
                    Text("Buat tanda tangan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { strokes.clear() }) { Text("Hapus") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))) {
                    Text("Tanda tangan di area putih ini", Modifier.align(Alignment.BottomCenter).padding(12.dp), color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o -> current.clear(); current += o },
                                onDrag = { ch, _ -> ch.consume(); current += ch.position },
                                onDragEnd = { if (current.size > 1) strokes += current.toList(); current.clear() },
                            )
                        },
                    ) {
                        strokes.forEach { drawInk(it, color, widthPx) }
                        drawInk(current, color, widthPx)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    inkColors.forEach { c -> Box(Modifier.size(32.dp).clip(CircleShape).background(c).border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable { color = c }) }
                    Spacer(Modifier.weight(1f))
                    listOf("Tipis", "Sedang", "Tebal").forEachIndexed { i, l -> FilterChip(selected = thick == i, onClick = { thick = i }, label = { Text(l) }) }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(onClick = { exportSignature(strokes, color.toArgb(), widthPx)?.let(onSave) }, enabled = strokes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Simpan") }
                }
            }
        }
    }
}

private fun exportSignature(strokes: List<List<Offset>>, color: Int, widthPx: Float): Bitmap? {
    val all = strokes.flatten()
    if (all.isEmpty()) return null
    val pad = widthPx * 2
    val minX = all.minOf { it.x } - pad; val minY = all.minOf { it.y } - pad
    val maxX = all.maxOf { it.x } + pad; val maxY = all.maxOf { it.y } + pad
    val w = max(1, (maxX - minX).roundToInt()); val h = max(1, (maxY - minY).roundToInt())
    // diperbesar 2× agar tetap tajam di PDF
    val s = 2f
    val bmp = Bitmap.createBitmap((w * s).roundToInt(), (h * s).roundToInt(), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = widthPx * s
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { st ->
        if (st.size < 2) return@forEach
        val p = android.graphics.Path()
        p.moveTo((st[0].x - minX) * s, (st[0].y - minY) * s)
        st.drop(1).forEach { p.lineTo((it.x - minX) * s, (it.y - minY) * s) }
        c.drawPath(p, paint)
    }
    return bmp
}

/** Dialog teks: isi, ukuran (pt), warna, tombol tanggal hari ini. */
@Composable
private fun TextStampDialog(onDismiss: () -> Unit, onDone: (String, Float, Color) -> Unit) {
    var text by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(12f) }
    var color by remember { mutableStateOf(inkColors[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah teks") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text("Tulisan") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    val today = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date())
                    text = if (text.isBlank()) today else "$text $today"
                }) { Text("Isi tanggal hari ini") }
                Text("Ukuran huruf", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(9f, 11f, 12f, 14f, 18f, 24f).forEach { s -> FilterChip(selected = size == s, onClick = { size = s }, label = { Text("${s.toInt()}") }) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    inkColors.forEach { c -> Box(Modifier.size(30.dp).clip(CircleShape).background(c).border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable { color = c }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(text.trimEnd(), size, color) }, enabled = text.isNotBlank()) { Text("Tempel") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Suppress("unused")
private val keepIcons = listOf(Icons.Outlined.PanTool)
@Suppress("unused")
private fun keepMin() = min(1, 2)
