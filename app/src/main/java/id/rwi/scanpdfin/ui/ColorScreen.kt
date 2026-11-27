@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package id.rwi.scanpdfin.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import id.rwi.scanpdfin.Capture
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.Screen
import id.rwi.scanpdfin.data.FilterMode
import id.rwi.scanpdfin.image.ImageOps
import id.rwi.scanpdfin.image.Perspective
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Layar pewarnaan: pratinjau hasil potong + pilihan Asli/Foto/Dokumen × Warna/Hitam, lalu simpan. */
@Composable
fun ColorScreen(vm: AppViewModel) {
    val session by vm.session.collectAsState()
    val s = session
    LaunchedEffect(s == null || s.captures.isEmpty()) { if ((s == null || s.captures.isEmpty()) && vm.screen == Screen.Color) vm.back() }
    s?.current ?: return
    val filter = s.filter ?: FilterMode.DOKUMEN
    val n = s.captures.size
    // geser kiri/kanan untuk melihat hasil tiap foto; tombol Atur batas/Putar berlaku untuk foto yang tampil
    val pager = rememberPagerState(initialPage = s.index.coerceIn(0, n - 1)) { n }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != s.index) vm.selectCapture(pager.settledPage) }
    LaunchedEffect(s.index, n) { if (s.index in 0 until n && s.index != pager.currentPage) pager.scrollToPage(s.index) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (n > 1) "Tampilan ($n halaman)" else "Tampilan") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali ke bingkai") } },
                actions = { IconButton(onClick = { vm.commitSession() }) { Icon(Icons.Outlined.Check, "Simpan") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
                    LookBar(filter) { vm.setSessionFilter(it) }
                    // ikon di atas tulisan, satu baris: tetap rapi walau ukuran huruf HP diperbesar
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val pad = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        val shape = RoundedCornerShape(14.dp)
                        OutlinedButton(onClick = { vm.selectCapture(pager.currentPage); vm.back() }, modifier = Modifier.weight(1f).fillMaxHeight(), shape = shape, contentPadding = pad) {
                            ToolLabel(Icons.Outlined.Crop, "Atur batas")
                        }
                        OutlinedButton(onClick = { vm.rotateCapture(pager.currentPage) }, modifier = Modifier.weight(1f).fillMaxHeight(), shape = shape, contentPadding = pad) {
                            ToolLabel(Icons.AutoMirrored.Outlined.RotateRight, "Putar")
                        }
                        Button(onClick = { vm.commitSession() }, modifier = Modifier.weight(1f).fillMaxHeight(), shape = shape, contentPadding = pad) {
                            ToolLabel(Icons.Outlined.Check, if (n > 1) "Simpan $n" else "Simpan")
                        }
                    }
                    if (n > 1) Text(
                        "Geser gambar untuk melihat hasil tiap halaman. Tampilan berlaku untuk semua $n halaman; bisa diubah per halaman nanti.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF2B2B2B))) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { s.captures.getOrNull(it)?.id ?: it }) { i ->
                val cap = s.captures.getOrNull(i)
                if (cap != null) CapturePreview(cap, filter)
            }
            if (n > 1) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                ) { Text("Halaman ${pager.currentPage + 1} dari $n", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }
                // tombol panah untuk yang tidak tahu bisa digeser
                if (pager.currentPage > 0) PagerArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Halaman sebelumnya", Modifier.align(Alignment.CenterStart)) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }
                if (pager.currentPage < n - 1) PagerArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Halaman berikutnya", Modifier.align(Alignment.CenterEnd)) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (n <= 12) repeat(n) { k ->
                        Box(Modifier.size(if (k == pager.currentPage) 9.dp else 7.dp).clip(CircleShape).background(Color.White.copy(alpha = if (k == pager.currentPage) 0.95f else 0.45f)))
                    }
                }
            }
        }
    }
}

/** Hasil satu foto: diluruskan sekali per bingkai, lalu tiap mode dicache selama halaman ini tampil. */
@Composable
private fun CapturePreview(cap: Capture, filter: FilterMode) {
    var warped by remember(cap.id, cap.version, cap.corners) { mutableStateOf<Bitmap?>(null) }
    val cache = remember(cap.id, cap.version, cap.corners) { mutableMapOf<FilterMode, Bitmap>() }
    var shown by remember(cap.id, cap.version, cap.corners) { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf(true) }
    var failed by remember(cap.id, cap.version) { mutableStateOf(false) }

    LaunchedEffect(cap.id, cap.version, cap.corners) {
        working = true
        warped = withContext(Dispatchers.Default) {
            runCatching {
                val full = ImageOps.decodeFile(cap.file, 1400) ?: return@runCatching null
                Perspective.warp(full, cap.corners).also { if (it !== full) full.recycle() }
            }.getOrNull()
        }
        failed = warped == null
        if (failed) working = false
    }
    LaunchedEffect(warped, filter) {
        val w = warped ?: return@LaunchedEffect
        val key = filter.normalized()
        cache[key]?.let { shown = it; working = false; return@LaunchedEffect }
        working = true
        val out = withContext(Dispatchers.Default) {
            val copy = w.copy(Bitmap.Config.ARGB_8888, false)
            ImageOps.applyFilter(copy, key)
        }
        // simpan maks. 2 mode per halaman agar memori tetap ringan saat banyak foto
        if (cache.size >= 2) cache.keys.firstOrNull { it != key }?.let { cache.remove(it) }
        cache[key] = out
        shown = out
        working = false
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val b = shown
        if (b != null) Image(b.asImageBitmap(), contentDescription = "Pratinjau hasil", modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 36.dp), contentScale = ContentScale.Fit)
        if (failed) Text("Foto ini tidak bisa dibaca", color = Color.White)
        else if (working) CircularProgressIndicator()
    }
}

@Composable
private fun PagerArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White, shape = CircleShape, modifier = modifier.padding(4.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { Icon(icon, label) }
    }
}

@Composable
private fun ToolLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null)
        Text(label, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
}
