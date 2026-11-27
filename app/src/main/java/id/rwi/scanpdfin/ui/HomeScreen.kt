@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package id.rwi.scanpdfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.Screen
import id.rwi.scanpdfin.data.Document
import id.rwi.scanpdfin.data.SortMode

@Composable
fun HomeScreen(vm: AppViewModel) {
    val docs by vm.documents.collectAsState()
    val settings by vm.settings.collectAsState()
    val query by vm.search.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var mergeAsk by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf(false) }
    var shareDoc by remember { mutableStateOf<Document?>(null) }

    val shown = docs
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedWith(
            when (settings.sort) {
                SortMode.TERBARU -> compareByDescending { it.updatedAt }
                SortMode.NAMA -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortMode.DIBUAT -> compareByDescending { it.createdAt }
            },
        )
    val selecting = selected.isNotEmpty()
    // Kembali: batalkan pilih/cari dulu sebelum keluar dari daftar
    androidx.activity.compose.BackHandler(enabled = selecting || searching) {
        if (selecting) selected = emptySet() else { searching = false; vm.search.value = "" }
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} dipilih") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Outlined.Close, "Batal pilih") } },
                    actions = {
                        if (selected.size == 1) {
                            IconButton(onClick = { renameId = selected.first() }) { Icon(Icons.Outlined.DriveFileRenameOutline, "Ubah nama") }
                            IconButton(onClick = { shareDoc = docs.firstOrNull { it.id == selected.first() } }) { Icon(Icons.Outlined.Share, "Bagikan") }
                        }
                        if (selected.size >= 2) IconButton(onClick = { mergeAsk = true }) { Icon(Icons.Outlined.CallMerge, "Gabung") }
                        IconButton(onClick = { deleteAsk = true }) { Icon(Icons.Outlined.Delete, "Hapus") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            } else if (searching) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query, onValueChange = { vm.search.value = it }, singleLine = true,
                            placeholder = { Text("Cari nama dokumen") }, modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    navigationIcon = { IconButton(onClick = { searching = false; vm.search.value = "" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Tutup pencarian") } },
                )
            } else {
                TopAppBar(
                    title = { Text("Scan") },
                    navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali ke menu awal") } },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, "Cari") }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.SortByAlpha, "Urutkan") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortMode.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) },
                                        onClick = { vm.setSort(s); sortMenu = false },
                                        trailingIcon = { if (s == settings.sort) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { vm.go(Screen.Settings) }) { Icon(Icons.Outlined.Settings, "Pengaturan") }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { vm.requestPick(null) }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Outlined.AddPhotoAlternate, "Impor gambar dari galeri")
                }
                ExtendedFloatingActionButton(
                    onClick = { vm.startCamera(null) },
                    icon = { Icon(Icons.Outlined.DocumentScanner, null) },
                    text = { Text("Pindai") },
                )
            }
        },
    ) { padding ->
        if (shown.isEmpty()) {
            EmptyHome(Modifier.padding(padding), hasDocs = docs.isNotEmpty())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { doc ->
                    DocumentCard(
                        vm, doc, selected = doc.id in selected,
                        onClick = { if (selecting) selected = toggle(selected, doc.id) else vm.go(Screen.Doc(doc.id)) },
                        onLongClick = { selected = toggle(selected, doc.id) },
                    )
                }
            }
        }
    }

    renameId?.let { id ->
        val doc = docs.firstOrNull { it.id == id }
        if (doc != null) TextInputDialog("Ubah nama dokumen", doc.name, onDismiss = { renameId = null }) {
            vm.rename(id, it); renameId = null; selected = emptySet()
        } else renameId = null
    }
    if (mergeAsk) {
        val ordered = shown.filter { it.id in selected }
        TextInputDialog("Gabung ${ordered.size} dokumen", ordered.first().name, confirmLabel = "Gabung", onDismiss = { mergeAsk = false }) { name ->
            vm.merge(ordered.map { it.id }, name); mergeAsk = false; selected = emptySet()
        }
    }
    if (deleteAsk) ConfirmDialog(
        "Hapus ${selected.size} dokumen?", "Semua halaman di dalamnya ikut terhapus dan tidak bisa dikembalikan.",
        onDismiss = { deleteAsk = false },
    ) { vm.deleteDocuments(selected); deleteAsk = false; selected = emptySet() }
    shareDoc?.let { doc -> ShareSheet(vm, doc, doc.pages, onDismiss = { shareDoc = null; selected = emptySet() }) }
}

private fun toggle(set: Set<String>, id: String) = if (id in set) set - id else set + id

@Composable
private fun DocumentCard(vm: AppViewModel, doc: Document, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            val first = doc.pages.firstOrNull()
            if (first != null) PageThumb(vm, first, 400, Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)))
            else Box(Modifier.fillMaxWidth().aspectRatio(0.78f).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Text("Kosong", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(
                Icons.Filled.CheckCircle, "Terpilih", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White, CircleShape),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                "${doc.pages.size} hal • ${formatDate(doc.updatedAt)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyHome(modifier: Modifier, hasDocs: Boolean) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(if (hasDocs) "Tidak ada yang cocok" else "Belum ada dokumen", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasDocs) "Coba kata kunci lain." else "Tekan Pindai untuk memotret dokumen, atau impor gambar dari galeri. Hasilnya bisa dijadikan PDF dan dibagikan.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}
