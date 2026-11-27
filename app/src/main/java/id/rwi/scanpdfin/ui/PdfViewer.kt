@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package id.rwi.scanpdfin.ui

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.rwi.scanpdfin.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/** Satu halaman pas di layar (tanpa terpotong), latar gelap. */
@Composable
private fun FitPage(vm: AppViewModel, file: File, index: Int, maxW: Dp, maxH: Dp, sharp: Float, modifier: Modifier = Modifier) {
    val ratio by produceState(initialValue = 0.707f, file, index) {
        value = runCatching { vm.pdf.engine.pageSize(file, index).let { (w, h) -> w / h } }.getOrDefault(0.707f)
    }
    val boxRatio = maxW / maxH
    val w = if (ratio > boxRatio) maxW else maxH * ratio
    val h = w / ratio
    val px = with(LocalDensity.current) { (w * sharp).toPx() }.roundToInt().coerceIn(200, 2600)
    val bmp by produceState<Bitmap?>(initialValue = null, file, index, px) { value = vm.pdf.engine.render(file, index, px) }
    Box(modifier.size(w, h).background(Color.White), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), "Halaman ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else CircularProgressIndicator(Modifier.size(28.dp))
    }
}

/**
 * Mode baca per halaman: satu halaman pas layar, usap kiri/kanan untuk pindah.
 * Cubit dua jari / ketuk dua kali untuk memperbesar; saat diperbesar, satu jari menggeser halaman.
 */
@Composable
fun PagedPdfView(vm: AppViewModel, file: File, pager: PagerState, modifier: Modifier = Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pager.currentPage) { zoom = 1f; pan = Offset.Zero }
    HorizontalPager(
        state = pager, modifier = modifier, beyondViewportPageCount = 1, userScrollEnabled = zoom <= 1.01f,
        key = { "${file.name}-$it" }, pageSpacing = 8.dp,
    ) { i ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val isCur = i == pager.currentPage
            val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
            val boxW = maxWidth; val boxH = maxHeight
            fun clampPan(p: Offset, z: Float) = Offset(p.x.coerceIn(-wPx * (z - 1) / 2, wPx * (z - 1) / 2), p.y.coerceIn(-hPx * (z - 1) / 2, hPx * (z - 1) / 2))
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(i) {
                        detectTapGestures(onDoubleTap = {
                            if (zoom > 1.01f) { zoom = 1f; pan = Offset.Zero } else zoom = 2.5f
                        })
                    }
                    .pointerInput(i) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val ev = awaitPointerEvent()
                                val fingers = ev.changes.count { it.pressed }
                                if (fingers >= 2 || zoom > 1.01f) {
                                    val z = (zoom * ev.calculateZoom()).coerceIn(1f, 5f)
                                    zoom = z
                                    pan = if (z <= 1.01f) Offset.Zero else clampPan(pan + ev.calculatePan(), z)
                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (ev.changes.any { it.pressed })
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                FitPage(
                    vm, file, i, boxW, boxH, sharp = if (isCur && zoom > 1.5f) 2f else 1f,
                    modifier = if (isCur) Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y } else Modifier,
                )
            }
        }
    }
}

/**
 * Slide show layar penuh: halaman berganti otomatis tiap [seconds] detik.
 * Ketuk sisi kiri/kanan = mundur/maju, ketuk tengah = tampilkan/sembunyikan tombol.
 */
@Composable
fun SlideShowDialog(
    vm: AppViewModel, file: File, pageCount: Int, startPage: Int, seconds: Int,
    onSeconds: (Int) -> Unit, onClose: (Int) -> Unit,
) {
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) { pageCount }
    var playing by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    var touched by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    fun close() = onClose(pager.currentPage)
    fun go(p: Int) { if (p in 0 until pageCount) scope.launch { pager.animateScrollToPage(p) } }

    // ganti halaman otomatis; berhenti di halaman terakhir. Dipicu settledPage (halaman yang sudah diam)
    // dan animasi dijalankan di scope terpisah agar tidak terputus di tengah geseran.
    LaunchedEffect(playing, pager.settledPage, seconds) {
        if (!playing) return@LaunchedEffect
        delay(seconds * 1000L)
        val p = pager.settledPage
        if (p < pageCount - 1) scope.launch { pager.animateScrollToPage(p + 1) } else { playing = false; controls = true }
    }
    // tombol hilang sendiri 3 detik setelah disentuh (selama berjalan)
    LaunchedEffect(controls, touched, playing) { if (controls && playing) { delay(3000); controls = false } }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let { w ->
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                // tutup layar penuh sampai ke area status & poni kamera, latar hitam tanpa redup
                WindowCompat.setDecorFitsSystemWindows(w, false)
                w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                w.setDimAmount(0f)
                if (android.os.Build.VERSION.SDK_INT >= 28) w.attributes = w.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                WindowCompat.getInsetsController(w, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val third = constraints.maxWidth / 3f
            // ketukan dibaca di pembungkus pager agar usap kiri/kanan tetap jalan
            HorizontalPager(
                state = pager, beyondViewportPageCount = 1, key = { "slide-${file.name}-$it" },
                modifier = Modifier.fillMaxSize().pointerInput(pageCount, third) {
                    detectTapGestures { o ->
                        touched++
                        when {
                            o.x < third -> go(pager.currentPage - 1)
                            o.x > third * 2 -> go(pager.currentPage + 1)
                            else -> controls = !controls
                        }
                    }
                },
            ) { i ->
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FitPage(vm, file, i, maxWidth, maxHeight, sharp = 1f)
                }
            }
            AnimatedVisibility(controls, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SlideBtn(Icons.Outlined.Close, "Tutup") { close() }
                    Spacer(Modifier.weight(1f))
                    Text("${pager.currentPage + 1} / $pageCount", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(56.dp))
                }
            }
            AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                Row(
                    // naik di atas garis/tombol navigasi HP (walau garisnya sedang disembunyikan) + jarak aman usap
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                        .windowInsetsPadding(
                            WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.mandatorySystemGestures)
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        )
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                ) {
                    SlideBtn(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Sebelumnya") { touched++; go(pager.currentPage - 1) }
                    SlideBtn(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Jeda" else "Putar") {
                        touched++
                        if (!playing && pager.currentPage == pageCount - 1) go(0)
                        playing = !playing
                    }
                    SlideBtn(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Berikutnya") { touched++; go(pager.currentPage + 1) }
                    val steps = listOf(3, 5, 10, 20)
                    SlideBtn(Icons.Outlined.Timer, "$seconds detik") { touched++; onSeconds(steps[(steps.indexOf(seconds) + 1) % steps.size]) }
                }
            }
            if (!playing && pager.currentPage == pageCount - 1 && controls) Surface(
                color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.Center),
            ) { Text("Slide selesai • tekan Putar untuk mengulang", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun SlideBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}
