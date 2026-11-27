@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package id.rwi.scanpdfin.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.ExportFormat
import id.rwi.scanpdfin.data.Page

@Composable
fun PageScreen(vm: AppViewModel, docId: String, startIndex: Int) {
    val docs by vm.documents.collectAsState()
    val doc = docs.firstOrNull { it.id == docId }
    LaunchedEffect(doc?.pages?.isEmpty()) { if (doc == null || doc.pages.isEmpty()) vm.back() }
    if (doc == null || doc.pages.isEmpty()) return

    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, doc.pages.lastIndex)) { doc.pages.size }
    val current = doc.pages[pager.currentPage.coerceIn(0, doc.pages.lastIndex)]
    var deleteAsk by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Halaman ${pager.currentPage + 1} dari ${doc.pages.size}") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } },
                actions = {
                    IconButton(onClick = { vm.export(doc, listOf(current), ExportFormat.JPG, share = true) }) { Icon(Icons.Outlined.Share, "Bagikan gambar ini") }
                    IconButton(onClick = { deleteAsk = true }) { Icon(Icons.Outlined.Delete, "Hapus halaman") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
                    LookBar(current.filter) { vm.setFilter(doc.id, setOf(current.id), it) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        LabeledIcon(Icons.AutoMirrored.Outlined.RotateLeft, "Putar kiri") { vm.rotate(doc.id, current.id, -90) }
                        LabeledIcon(Icons.Outlined.Crop, "Atur bingkai") { vm.editPageFrame(doc.id, current) }
                        LabeledIcon(Icons.AutoMirrored.Outlined.RotateRight, "Putar kanan") { vm.rotate(doc.id, current.id, 90) }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(padding), userScrollEnabled = !zoomed, beyondViewportPageCount = 1) { index ->
            val page = doc.pages.getOrNull(index) ?: return@HorizontalPager
            ZoomableBitmap(vm, page, onZoomChanged = { zoomed = it })
        }
    }

    if (deleteAsk) ConfirmDialog("Hapus halaman ini?", "Halaman tidak bisa dikembalikan.", onDismiss = { deleteAsk = false }) {
        deleteAsk = false; vm.deletePages(doc.id, setOf(current.id))
    }
}

@Composable
private fun LabeledIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ZoomableBitmap(vm: AppViewModel, page: Page, onZoomChanged: (Boolean) -> Unit) {
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = page.renderKey) {
        value = try { vm.repo.render(page, 2000) } catch (_: Exception) { null }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
        onZoomChanged(scale > 1.02f)
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // geser satu jari hanya saat diperbesar; pada 100% usapan diteruskan ke pager (pindah halaman)
            .transformable(state, canPan = { scale > 1.02f })
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.02f) { scale = 1f; offset = Offset.Zero; onZoomChanged(false) } else { scale = 2.5f; onZoomChanged(true) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b == null) CircularProgressIndicator()
        else Image(
            b.asImageBitmap(), contentDescription = "Halaman",
            modifier = Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            },
            contentScale = ContentScale.Fit,
        )
    }
}
