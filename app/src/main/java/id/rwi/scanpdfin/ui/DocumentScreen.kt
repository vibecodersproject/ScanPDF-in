@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package id.rwi.scanpdfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.ExportFormat
import id.rwi.scanpdfin.Screen
import id.rwi.scanpdfin.data.FilterMode
import id.rwi.scanpdfin.data.Page

@Composable
fun DocumentScreen(vm: AppViewModel, docId: String) {
    val docs by vm.documents.collectAsState()
    val doc = docs.firstOrNull { it.id == docId }
    LaunchedEffect(doc == null) { if (doc == null) vm.back() }
    doc ?: return

    var selected by remember { mutableStateOf(setOf<String>()) }
    var reorder by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var deleteDoc by remember { mutableStateOf(false) }
    var deletePages by remember { mutableStateOf(false) }
    var modeFor by remember { mutableStateOf<Set<String>?>(null) } // null tidak tampil; emptySet = semua
    var showMode by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf<List<Page>?>(null) }

    val selecting = selected.isNotEmpty()
    val selectedPages = doc.pages.filter { it.id in selected }
    // tombol Kembali membatalkan mode pilih/susun dulu, baru keluar dari dokumen
    androidx.activity.compose.BackHandler(enabled = selecting || reorder) { selected = emptySet(); reorder = false }

    Scaffold(
        topBar = {
            if (selecting) TopAppBar(
                title = { Text("${selected.size} halaman dipilih") },
                navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Outlined.Close, "Batal pilih") } },
                actions = {
                    IconButton(onClick = { modeFor = selected; showMode = true }) { Icon(Icons.Outlined.Tune, "Ganti tampilan") }
                    IconButton(onClick = { moveTo = true }) { Icon(Icons.Outlined.DriveFileMove, "Pindahkan ke dokumen lain") }
                    IconButton(onClick = { share = selectedPages }) { Icon(Icons.Outlined.Share, "Bagikan halaman terpilih") }
                    IconButton(onClick = { deletePages = true }) { Icon(Icons.Outlined.Delete, "Hapus halaman") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) else TopAppBar(
                title = {
                    Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { rename = true })
                },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } },
                actions = {
                    if (reorder) FilledTonalButton(onClick = { reorder = false }, modifier = Modifier.padding(end = 8.dp)) { Text("Selesai susun") }
                    else IconButton(onClick = { rename = true }) { Icon(Icons.Outlined.DriveFileRenameOutline, "Ubah nama") }
                },
            )
        },
        bottomBar = {
            if (!selecting && !reorder) BottomAppBar {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BarAction(Icons.Outlined.AddPhotoAlternate, "Galeri") { vm.requestPick(doc.id) }
                    BarAction(Icons.Outlined.PhotoCamera, "Kamera") { vm.startCamera(doc.id) }
                    BarAction(Icons.Outlined.Share, "Bagikan") { share = doc.pages }
                    Box {
                        BarAction(Icons.Outlined.MoreVert, "Lainnya") { addMenu = true }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text("Simpan PDF ke…") }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { addMenu = false; vm.export(doc, doc.pages, ExportFormat.PDF, share = false) })
                            DropdownMenuItem(text = { Text("Susun ulang halaman") }, leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) }, onClick = { addMenu = false; reorder = true })
                            DropdownMenuItem(text = { Text("Tampilan semua halaman") }, leadingIcon = { Icon(Icons.Outlined.Tune, null) }, onClick = { addMenu = false; modeFor = emptySet(); showMode = true })
                            DropdownMenuItem(text = { Text("Ubah nama") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { addMenu = false; rename = true })
                            DropdownMenuItem(text = { Text("Duplikat dokumen") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { addMenu = false; vm.duplicate(doc.id) })
                            DropdownMenuItem(text = { Text("Hapus dokumen", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { addMenu = false; deleteDoc = true })
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (doc.pages.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Dokumen ini kosong", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Tambahkan halaman lewat tombol Kamera atau Galeri di bawah.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Column(Modifier.fillMaxSize().padding(padding)) {
            if (doc.pages.size > 1) Text(
                "Tahan lalu geser halaman untuk mengubah urutan. Tahan tanpa digeser untuk memilih.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
            // urutan lokal selama diseret; disimpan sekali saat dilepas
            var order by remember(doc.pages) { mutableStateOf(doc.pages) }
            val gridState = rememberLazyGridState()
            val drag = rememberGridReorderState(gridState)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                state = gridState,
                // selama halaman diseret, gulir daftar dimatikan agar geser naik/turun memindah halaman
                userScrollEnabled = drag.draggingKey == null,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().gridReorder(
                    drag,
                    onTap = { i ->
                        val page = order.getOrNull(i) ?: return@gridReorder
                        if (selecting) selected = if (page.id in selected) selected - page.id else selected + page.id
                        else if (!reorder) vm.go(Screen.PageView(doc.id, doc.pages.indexOfFirst { it.id == page.id }.coerceAtLeast(0)))
                    },
                    onLongPress = { i -> if (!reorder) order.getOrNull(i)?.let { selected = selected + it.id } },
                    onMove = { from, to -> if (from in order.indices && to in order.indices) order = order.toMutableList().apply { add(to, removeAt(from)) } },
                    onDrop = { from, to -> vm.movePage(doc.id, from, to) },
                ),
            ) {
                itemsIndexed(order, key = { _, p -> p.id }) { index, page ->
                    PageTile(
                        vm, page, index, selected = page.id in selected, reorder = reorder,
                        canLeft = index > 0, canRight = index < order.lastIndex,
                        onMove = { delta -> vm.movePage(doc.id, index, index + delta) },
                        modifier = Modifier.reorderItem(drag, page.id).then(if (drag.draggingKey == page.id) Modifier else Modifier.animateItem()),
                    )
                }
            }
        }
    }

    if (rename) TextInputDialog("Ubah nama dokumen", doc.name, onDismiss = { rename = false }) { vm.rename(doc.id, it); rename = false }
    if (deleteDoc) ConfirmDialog("Hapus dokumen ini?", "${doc.pages.size} halaman ikut terhapus dan tidak bisa dikembalikan.", onDismiss = { deleteDoc = false }) {
        deleteDoc = false; vm.deleteDocuments(setOf(doc.id))
    }
    if (deletePages) ConfirmDialog("Hapus ${selected.size} halaman?", "Halaman yang dihapus tidak bisa dikembalikan.", onDismiss = { deletePages = false }) {
        vm.deletePages(doc.id, selected); deletePages = false; selected = emptySet()
    }
    if (showMode) {
        val target = modeFor
        val current = (if (target.isNullOrEmpty()) doc.pages.firstOrNull()?.filter ?: FilterMode.DOKUMEN else selectedPages.firstOrNull()?.filter ?: FilterMode.DOKUMEN).normalized()
        ChoiceDialog(
            if (target.isNullOrEmpty()) "Tampilan semua halaman" else "Tampilan ${target.size} halaman",
            FilterMode.choices, current, { it.label }, onDismiss = { showMode = false },
        ) { mode ->
            vm.setFilter(doc.id, if (target.isNullOrEmpty()) null else target, mode); showMode = false; selected = emptySet()
        }
    }
    if (moveTo) {
        val others = docs.filter { it.id != doc.id }
        ChoiceDialog("Pindahkan ${selected.size} halaman ke", listOf<String?>(null) + others.map { it.id }, "", { id -> if (id == null) "Dokumen baru" else others.first { it.id == id }.name }, onDismiss = { moveTo = false }) { target ->
            vm.movePagesTo(doc.id, selected, target, copy = false); moveTo = false; selected = emptySet()
        }
    }
    share?.let { pages -> ShareSheet(vm, doc, pages, onDismiss = { share = null; selected = emptySet() }) }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PageTile(
    vm: AppViewModel, page: Page, index: Int, selected: Boolean, reorder: Boolean,
    canLeft: Boolean, canRight: Boolean,
    onMove: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    // ketuk/tahan/seret ditangani kisi (gridReorder) agar kotak bisa dipindah dengan jari
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            PageThumb(vm, page, 360, Modifier.fillMaxSize().padding(if (selected) 6.dp else 0.dp).clip(RoundedCornerShape(10.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            Surface(
                color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = CircleShape,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            ) { Text("${index + 1}", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) }
            if (selected) Icon(Icons.Filled.CheckCircle, "Terpilih", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.White, CircleShape))
            if (reorder) Row(Modifier.align(Alignment.BottomCenter).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoveButton(Icons.Outlined.KeyboardArrowLeft, "Geser ke kiri", canLeft) { onMove(-1) }
                MoveButton(Icons.Outlined.KeyboardArrowRight, "Geser ke kanan", canRight) { onMove(1) }
            }
        }
        Text(page.filter.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
    }
}

@Composable
private fun MoveButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.9f else 0.3f), contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) { Icon(icon, label) }
    }
}
