@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package id.rwi.scanpdfin.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.Screen
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

// =====================================================================
// Beranda PDF
// =====================================================================

@Composable
fun PdfHomeScreen(vm: AppViewModel) {
    val recent by vm.pdf.recent.collectAsState()
    val docs by vm.documents.collectAsState()
    var pickScan by remember { mutableStateOf(false) }
    var defaultPdf by remember { mutableStateOf(false) }
    if (defaultPdf) DefaultPdfDialog { defaultPdf = false }

    Scaffold(
        topBar = { TopAppBar(title = { Text("PDF") }, navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { vm.pdf.pickToOpen() }, icon = { Icon(Icons.Outlined.FolderOpen, null) }, text = { Text("Buka PDF") })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)) {
            item {
                Text("Alat", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard(Icons.Outlined.FolderOpen, "Buka & baca", Modifier.weight(1f)) { vm.pdf.pickToOpen() }
                    ToolCard(Icons.AutoMirrored.Outlined.CallMerge, "Gabung PDF", Modifier.weight(1f)) { vm.pdf.startMerge() }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard(Icons.Outlined.Image, "Gambar jadi PDF", Modifier.weight(1f)) { vm.pdf.pickImagesNew() }
                    ToolCard(Icons.Outlined.DocumentScanner, "Hasil Scan ke PDF", Modifier.weight(1f)) { if (docs.isEmpty()) vm.toast("Belum ada hasil Scan") else pickScan = true }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard(Icons.Outlined.Draw, "Tanda tangan saya", Modifier.weight(1f)) { vm.go(Screen.PdfSignatures) }
                    ToolCard(Icons.Outlined.PictureAsPdf, "Jadikan pembuka PDF bawaan", Modifier.weight(1f)) { defaultPdf = true }
                }
                Spacer(Modifier.height(24.dp))
                Text("Terakhir dibuka", style = MaterialTheme.typography.titleSmall)
                if (recent.isEmpty()) Text(
                    "Belum ada. Tekan Buka PDF, atau buka berkas PDF dari aplikasi lain lalu pilih \"Scan PDF-in\".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
            }
            itemsIndexed(recent, key = { _, r -> r.uri }) { _, r ->
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = { vm.pdf.open(Uri.parse(r.uri), r.name, null, fromRecent = true) }, onLongClick = { menu = true })
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text("${r.pageCount} hal • ${formatDate(r.openedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Pilihan") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Hapus dari daftar") }, onClick = { menu = false; vm.pdf.store.remove(r.uri) })
                        }
                    }
                }
            }
        }
    }

    if (pickScan) ChoiceDialog("Pilih hasil Scan", docs.map { it.id }, "", { id -> docs.first { it.id == id }.name }, { pickScan = false }) { id ->
        pickScan = false; vm.pdf.openScanDoc(id)
    }
}

@Composable
private fun ToolCard(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    // tinggi ikut isi agar label 2 baris tetap utuh walau ukuran huruf HP diperbesar
    Card(onClick = onClick, modifier = modifier.fillMaxHeight().heightIn(min = 84.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Sandi baru untuk salinan PDF: diketik dua kali agar salah ketik tidak mengunci PDF selamanya. */
@Composable
private fun NewPasswordDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val vt = if (show) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    val mismatch = pw2.isNotEmpty() && pw != pw2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kunci PDF dengan kata sandi") },
        text = {
            Column {
                OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("Kata sandi (min. 4 karakter)") }, visualTransformation = vt)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pw2, { pw2 = it }, singleLine = true, label = { Text("Ulangi kata sandi") }, visualTransformation = vt, isError = mismatch,
                    supportingText = { if (mismatch) Text("Kata sandi tidak sama") })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { show = !show }) {
                    androidx.compose.material3.Checkbox(checked = show, onCheckedChange = { show = it })
                    Text("Tampilkan kata sandi", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Ingat kata sandinya. Tanpa itu, PDF tidak bisa dibuka siapa pun, termasuk aplikasi ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(pw) }, enabled = pw.length >= 4 && pw == pw2) { Text("Kunci & simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Membuka PDF lain (mis. dari WhatsApp) saat PDF sekarang punya perubahan yang belum disimpan. */
@Composable
fun PendingOpenDialog(vm: AppViewModel) {
    val pending by vm.pdf.pendingOpen.collectAsState()
    pending ?: return
    AlertDialog(
        onDismissRequest = { vm.pdf.cancelPendingOpen() },
        title = { Text("Buka PDF lain?") },
        text = { Text("PDF yang sedang dibuka punya perubahan yang belum disimpan. Simpan dulu, lalu buka lagi PDF barunya, atau buang perubahannya.") },
        confirmButton = { TextButton(onClick = { vm.pdf.confirmPendingOpen(discard = false) }) { Text("Simpan dulu") } },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.pdf.cancelPendingOpen() }) { Text("Batal") }
                TextButton(onClick = { vm.pdf.confirmPendingOpen(discard = true) }) { Text("Buang & buka", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

/** Dialog kata sandi untuk PDF terkunci (dipakai di mana pun PDF dibuka). */
@Composable
fun PasswordDialog(vm: AppViewModel) {
    val ask by vm.pdf.password.collectAsState()
    val a = ask ?: return
    var pw by remember(a) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.pdf.cancelPassword() },
        title = { Text("PDF berkata sandi") },
        text = {
            Column {
                if (a.wrong) Text("Kata sandi salah, coba lagi.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("Kata sandi") }, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { vm.pdf.open(a.uri, a.name, pw) }, enabled = pw.isNotEmpty()) { Text("Buka") } },
        dismissButton = { TextButton(onClick = { vm.pdf.cancelPassword() }) { Text("Batal") } },
    )
}

// =====================================================================
// Pembaca
// =====================================================================

@Composable
fun PdfReaderScreen(vm: AppViewModel, startPage: Int) {
    val doc by vm.pdf.doc.collectAsState()
    val canUndo by vm.pdf.canUndo.collectAsState()
    val d = doc
    LaunchedEffect(d == null) { if (d == null) vm.back() }
    d ?: return
    val settings by vm.settings.collectAsState()
    val paged = settings.readPaged
    val lastIndex = (d.pageCount - 1).coerceAtLeast(0)
    // halaman patokan saat berganti mode baca / kembali dari slide show
    var anchor by remember {
        val resume = vm.pdf.readerPage?.takeIf { it.first == d.id }?.second ?: 0
        mutableIntStateOf((if (startPage >= 0) startPage else resume).coerceIn(0, lastIndex))
    }
    val countState = rememberUpdatedState(d.pageCount)
    val list = remember(paged) { LazyListState(anchor.coerceIn(0, lastIndex)) }
    val pager = remember(paged) { PagerState(anchor.coerceIn(0, lastIndex)) { countState.value } }
    val current = (if (paged) pager.currentPage else list.firstVisibleItemIndex).coerceIn(0, lastIndex)
    LaunchedEffect(d.id, current) { vm.pdf.readerPage = d.id to current }
    var slideShow by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var menu by remember { mutableStateOf(false) }
    var exitAsk by remember { mutableStateOf(false) }
    var renameAsk by remember { mutableStateOf(false) }
    var pwAsk by remember { mutableStateOf(false) }
    var compressAsk by remember { mutableStateOf(false) }
    var infoShow by remember { mutableStateOf<Map<String, String>?>(null) }
    var gotoAsk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun jumpTo(p: Int) {
        val n = p.coerceIn(0, lastIndex)
        anchor = n
        scope.launch { if (paged) pager.scrollToPage(n) else list.scrollToItem(n) }
    }
    fun setPaged(p: Boolean) {
        if (p == paged) return
        anchor = current // state baru (remember(paged)) mulai dari halaman yang sedang dibaca
        vm.updateSettings { it.copy(readPaged = p) }
        vm.toast(if (p) "Mode baca: per halaman (usap kiri/kanan)" else "Mode baca: gulir bersambung")
    }

    BackHandler { if (d.dirty) exitAsk = true else vm.pdf.close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { renameAsk = true }) {
                        Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Hal ${current + 1} dari ${d.pageCount}" + if (d.dirty) " • belum disimpan" else "",
                            style = MaterialTheme.typography.labelMedium, color = if (d.dirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = { if (d.dirty) exitAsk = true else vm.pdf.close() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Tutup PDF") } },
                actions = {
                    if (canUndo) IconButton(onClick = { vm.pdf.undo() }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Urungkan") }
                    IconButton(onClick = { if (vm.pdf.canOverwrite) menu = true else vm.pdf.saveAs() }) { Icon(Icons.Outlined.Save, "Simpan") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Lainnya") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Gulir bersambung") }, leadingIcon = { Icon(Icons.Outlined.ViewDay, null) },
                                trailingIcon = { if (!paged) Icon(Icons.Filled.CheckCircle, "Dipakai", tint = MaterialTheme.colorScheme.primary) },
                                onClick = { menu = false; setPaged(false) },
                            )
                            DropdownMenuItem(
                                text = { Text("Per halaman") }, leadingIcon = { Icon(Icons.Outlined.ViewCarousel, null) },
                                trailingIcon = { if (paged) Icon(Icons.Filled.CheckCircle, "Dipakai", tint = MaterialTheme.colorScheme.primary) },
                                onClick = { menu = false; setPaged(true) },
                            )
                            HorizontalDivider()
                            if (vm.pdf.canOverwrite) DropdownMenuItem(text = { Text("Simpan (timpa berkas asli)") }, leadingIcon = { Icon(Icons.Outlined.Save, null) }, onClick = { menu = false; vm.pdf.overwrite() })
                            DropdownMenuItem(text = { Text("Simpan sebagai PDF baru…") }, leadingIcon = { Icon(Icons.Outlined.NoteAdd, null) }, onClick = { menu = false; vm.pdf.saveAs() })
                            DropdownMenuItem(text = { Text("Simpan dengan kata sandi…") }, leadingIcon = { Icon(Icons.Outlined.Lock, null) }, onClick = { menu = false; pwAsk = true })
                            DropdownMenuItem(text = { Text("Kecilkan ukuran") }, leadingIcon = { Icon(Icons.Outlined.Compress, null) }, onClick = { menu = false; compressAsk = true })
                            DropdownMenuItem(text = { Text("Lompat ke halaman…") }, leadingIcon = { Icon(Icons.Outlined.InsertDriveFile, null) }, onClick = { menu = false; gotoAsk = true })
                            DropdownMenuItem(text = { Text("Ubah nama") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { menu = false; renameAsk = true })
                            DropdownMenuItem(text = { Text("Info berkas") }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { menu = false; scope.launch { infoShow = vm.pdf.info() } })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BarBtn(Icons.Outlined.EditNote, "Edit teks") { vm.go(Screen.PdfEdit(current, "edit")) }
                    BarBtn(Icons.Outlined.Draw, "Tanda tangan") { vm.go(Screen.PdfEdit(current, "ttd")) }
                    BarBtn(Icons.Outlined.Slideshow, "Slide show") { anchor = current; slideShow = true }
                    BarBtn(Icons.Outlined.GridView, "Halaman") { vm.go(Screen.PdfPages) }
                    BarBtn(Icons.Outlined.Share, "Bagikan") { vm.pdf.share() }
                }
            }
        },
    ) { padding ->
        if (paged) {
            PagedPdfView(vm, d.file, pager, Modifier.fillMaxSize().padding(padding).background(Color(0xFF3A3A3A)))
            return@Scaffold
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).background(Color(0xFF3A3A3A))
                .pointerInput(Unit) {
                    // cubit dua jari = perbesar; satu jari dibiarkan untuk gulir
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            if (ev.changes.count { it.pressed } >= 2) {
                                zoom = (zoom * ev.calculateZoom()).coerceIn(1f, 4f)
                                ev.changes.forEach { it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                },
        ) {
            val baseW = maxWidth
            val density = LocalDensity.current
            // tingkat render dibulatkan per 50%: saat dicubit, halaman tidak dirender ulang di setiap gerakan
            val renderZoom = (kotlin.math.ceil(zoom * 2f) / 2f).coerceAtLeast(1f)
            val widthPx = with(density) { (baseW * renderZoom).toPx() }.roundToInt()
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(
                    state = list, modifier = Modifier.width(baseW * zoom).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(d.pageCount, key = { "${d.file.name}-$it" }) { i ->
                        PdfPageImage(vm, d.file, i, (widthPx - 16).coerceIn(200, 2600), Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                    }
                }
            }
            if (zoom > 1.01f) Surface(
                color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clickable { zoom = 1f },
            ) { Text("${(zoom * 100).roundToInt()}% • ketuk untuk 100%", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall) }
        }
    }

    if (exitAsk) AlertDialog(
        onDismissRequest = { exitAsk = false },
        title = { Text("Simpan perubahan?") },
        text = { Text("Perubahan pada PDF ini belum disimpan.") },
        confirmButton = { TextButton(onClick = { exitAsk = false; vm.pdf.saveAs() }) { Text("Simpan sebagai baru") } },
        dismissButton = {
            Row {
                TextButton(onClick = { exitAsk = false }) { Text("Batal") }
                TextButton(onClick = { exitAsk = false; vm.pdf.close() }) { Text("Buang", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
    if (renameAsk) TextInputDialog("Nama PDF", d.name, onDismiss = { renameAsk = false }) { vm.pdf.rename(it); renameAsk = false }
    if (pwAsk) NewPasswordDialog(onDismiss = { pwAsk = false }) { vm.pdf.saveWithPassword(it); pwAsk = false }
    if (gotoAsk) TextInputDialog("Lompat ke halaman (1–${d.pageCount})", "", confirmLabel = "Lompat", onDismiss = { gotoAsk = false }) { t ->
        gotoAsk = false
        t.trim().toIntOrNull()?.let { n -> jumpTo(n - 1) }
    }
    if (slideShow) SlideShowDialog(
        vm, d.file, d.pageCount, anchor, settings.slideSeconds,
        onSeconds = { s -> vm.updateSettings { it.copy(slideSeconds = s) } },
    ) { last -> slideShow = false; jumpTo(last) }
    if (compressAsk) CompressLevelDialog(
        note = "Berkas asli tidak berubah sampai Anda menyimpan. Bisa dibatalkan dengan Urungkan.",
        onDismiss = { compressAsk = false },
    ) { level -> compressAsk = false; vm.pdf.compress(level) }
    infoShow?.let { info ->
        AlertDialog(
            onDismissRequest = { infoShow = null }, title = { Text("Info berkas") },
            text = { Column { info.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodyMedium) } } },
            confirmButton = { TextButton(onClick = { infoShow = null }) { Text("Tutup") } },
        )
    }
}

/** Tombol bar bawah: lebar dibagi rata, label maksimal 2 baris (rapi walau huruf HP diperbesar). */
@Composable
private fun RowScope.BarBtn(icon: ImageVector, label: String, onClick: () -> Unit) = BarBtnBase(icon, label, Modifier.weight(1f), onClick)

@Composable
private fun BarBtnBase(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

/** Gambar satu halaman PDF; kerangka abu-abu selama dirender. */
@Composable
fun PdfPageImage(vm: AppViewModel, file: File, index: Int, widthPx: Int, modifier: Modifier = Modifier) {
    val ratio by produceState(initialValue = 0.707f, file, index) {
        value = runCatching { vm.pdf.engine.pageSize(file, index).let { (w, h) -> w / h } }.getOrDefault(0.707f)
    }
    val bmp by produceState<Bitmap?>(initialValue = null, file, index, widthPx) { value = vm.pdf.engine.render(file, index, widthPx) }
    Box(modifier.aspectRatio(ratio).background(Color.White)) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = "Halaman ${index + 1}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else Box(Modifier.fillMaxSize().background(Color(0xFFEDEDED)), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
    }
}

// =====================================================================
// Kisi halaman
// =====================================================================

@Composable
fun PdfPagesScreen(vm: AppViewModel) {
    val doc by vm.pdf.doc.collectAsState()
    val canUndo by vm.pdf.canUndo.collectAsState()
    val docs by vm.documents.collectAsState()
    val d = doc
    LaunchedEffect(d == null) { if (d == null) vm.back() }
    d ?: return
    var selected by remember(d.file) { mutableStateOf(setOf<Int>()) }
    var addMenu by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf(false) }
    var pickScan by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }
    val insertAt = if (selecting) selected.max() else d.pageCount - 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selecting) "${selected.size} dipilih" else "Halaman (${d.pageCount})") },
                navigationIcon = {
                    if (selecting) IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Outlined.Close, "Batal pilih") }
                    else IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") }
                },
                actions = {
                    if (canUndo) IconButton(onClick = { vm.pdf.undo() }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Urungkan") }
                    if (!selecting) TextButton(onClick = { selected = (0 until d.pageCount).toSet() }) { Text("Pilih semua") }
                },
                colors = if (selecting) TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else TopAppBarDefaults.topAppBarColors(),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (selecting) {
                        BarBtn(Icons.AutoMirrored.Outlined.RotateLeft, "Putar kiri") { vm.pdf.rotatePages(selected, -90) }
                        BarBtn(Icons.AutoMirrored.Outlined.RotateRight, "Putar kanan") { vm.pdf.rotatePages(selected, 90) }
                        BarBtn(Icons.Outlined.ContentCut, "Jadikan PDF") { vm.pdf.extract(selected.toList()) }
                        BarBtn(Icons.Outlined.Delete, "Hapus") { deleteAsk = true }
                    } else {
                        Box(Modifier.weight(1f)) {
                            BarBtnBase(Icons.Outlined.Add, "Sisipkan", Modifier.fillMaxWidth()) { addMenu = true }
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                DropdownMenuItem(text = { Text("Halaman kosong") }, leadingIcon = { Icon(Icons.Outlined.InsertDriveFile, null) }, onClick = { addMenu = false; vm.pdf.insertBlank(insertAt) })
                                DropdownMenuItem(text = { Text("Dari PDF lain") }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { addMenu = false; vm.pdf.pickInsertPdf(insertAt) })
                                DropdownMenuItem(text = { Text("Dari gambar") }, leadingIcon = { Icon(Icons.Outlined.Image, null) }, onClick = { addMenu = false; vm.pdf.pickInsertImages(insertAt) })
                                DropdownMenuItem(text = { Text("Dari hasil Scan") }, leadingIcon = { Icon(Icons.Outlined.DocumentScanner, null) }, onClick = { addMenu = false; if (docs.isEmpty()) vm.toast("Belum ada hasil Scan") else pickScan = true })
                            }
                        }
                        BarBtn(Icons.AutoMirrored.Outlined.RotateRight, "Putar semua") { vm.pdf.rotatePages((0 until d.pageCount).toSet(), 90) }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                if (selecting) "Sisipan baru masuk setelah halaman ${insertAt + 1}. Tahan lalu geser untuk mengubah urutan."
                else "Ketuk untuk membaca. Tahan untuk memilih. Tahan lalu geser untuk mengubah urutan.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            // isi = nomor halaman asli; diubah lokal selama diseret, lalu PDF disusun ulang sekali saat dilepas
            var order by remember(d.file, d.pageCount) { mutableStateOf((0 until d.pageCount).toList()) }
            val gridState = rememberLazyGridState()
            val drag = rememberGridReorderState(gridState)
            // bila penyusunan gagal (PDF tidak berubah), urutan tampilan kembali sesuai PDF
            val busyNow by vm.busy.collectAsState()
            LaunchedEffect(busyNow == null) { if (busyNow == null && drag.draggingKey == null) order = (0 until d.pageCount).toList() }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp), contentPadding = PaddingValues(12.dp), state = gridState,
                userScrollEnabled = drag.draggingKey == null,
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.gridReorder(
                    drag,
                    onTap = { pos ->
                        val i = order.getOrNull(pos) ?: return@gridReorder
                        if (selecting) selected = if (i in selected) selected - i else selected + i else vm.replaceReader(i)
                    },
                    onLongPress = { pos -> order.getOrNull(pos)?.let { selected = selected + it } },
                    onMove = { from, to -> if (from in order.indices && to in order.indices) order = order.toMutableList().apply { add(to, removeAt(from)) } },
                    onDrop = { from, to -> selected = emptySet(); vm.pdf.movePage(from, to) },
                ),
            ) {
                gridItemsIndexed(order, key = { _, i -> "${d.file.name}-$i" }) { pos, i ->
                    val sel = i in selected
                    val key = "${d.file.name}-$i"
                    Column(Modifier.reorderItem(drag, key).then(if (drag.draggingKey == key) Modifier else Modifier.animateItem())) {
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        ) {
                            PdfPageImage(vm, d.file, i, 300, Modifier.fillMaxWidth())
                            Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = CircleShape, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                                Text("${pos + 1}", Modifier.padding(horizontal = 7.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                            }
                            if (sel) Icon(Icons.Filled.CheckCircle, "Terpilih", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.White, CircleShape))
                        }
                    }
                }
            }
        }
    }

    if (deleteAsk) ConfirmDialog("Hapus ${selected.size} halaman?", "Bisa dibatalkan dengan tombol Urungkan sebelum disimpan.", onDismiss = { deleteAsk = false }) {
        deleteAsk = false; vm.pdf.deletePages(selected); selected = emptySet()
    }
    if (pickScan) ChoiceDialog("Sisipkan hasil Scan", docs.map { it.id }, "", { id -> docs.first { it.id == id }.name }, { pickScan = false }) { id ->
        pickScan = false; vm.pdf.insertScanDoc(id, insertAt)
    }
}

@Composable
private fun SmallRound(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.92f else 0.3f), contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) { Icon(icon, label) }
    }
}

// =====================================================================
// Gabung
// =====================================================================

@Composable
fun PdfMergeScreen(vm: AppViewModel) {
    val list by vm.pdf.mergeList.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Gabung PDF") }, navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.pdf.pickMerge() }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Add, null); Text(" Tambah PDF") }
                    Button(onClick = { vm.pdf.doMerge() }, enabled = list.size >= 2, modifier = Modifier.weight(1f)) { Text("Gabungkan (${list.size})") }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Urutan di bawah = urutan dalam PDF gabungan. Pakai panah untuk mengubah urutan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(list, key = { _, p -> p.id }) { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", Modifier.width(28.dp), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = Color(0xFFD32F2F))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.pageCount} halaman", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.pdf.moveMerge(i, i - 1) }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, "Naikkan") }
                    IconButton(onClick = { vm.pdf.moveMerge(i, i + 1) }, enabled = i < list.size - 1) { Icon(Icons.Outlined.ArrowDownward, "Turunkan") }
                    IconButton(onClick = { vm.pdf.removeMerge(i) }) { Icon(Icons.Outlined.Close, "Keluarkan") }
                }
                HorizontalDivider()
            }
        }
    }
}

// =====================================================================
// Tanda tangan tersimpan
// =====================================================================

@Composable
fun PdfSignaturesScreen(vm: AppViewModel) {
    val sigs by vm.pdf.signatures.collectAsState()
    var draw by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf<File?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Tanda tangan saya") }, navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { draw = true }, icon = { Icon(Icons.Outlined.Draw, null) }, text = { Text("Buat baru") }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Tanda tangan disimpan hanya di HP ini. Pakai di menu PDF → Tanda tangan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (sigs.isEmpty()) Text("Belum ada tanda tangan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sigs, key = { it.name }) { f ->
                    Box(Modifier.aspectRatio(2f).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
                        val bmp = remember(f) { vm.pdf.store.loadSignature(f) }
                        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
                        IconButton(onClick = { deleteAsk = f }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Outlined.Delete, "Hapus tanda tangan", tint = Color.Gray) }
                    }
                }
            }
        }
    }
    if (draw) SignaturePadDialog(onDismiss = { draw = false }) { bmp -> vm.pdf.store.saveSignature(bmp); draw = false; vm.toast("Tanda tangan disimpan") }
    deleteAsk?.let { f -> ConfirmDialog("Hapus tanda tangan ini?", "Tanda tangan yang sudah ditempel di PDF tidak ikut terhapus.", onDismiss = { deleteAsk = null }) { vm.pdf.store.deleteSignature(f); deleteAsk = null } }
}

@Suppress("unused")
private val keepFilledTonal: @Composable () -> Unit = { FilledTonalButton(onClick = {}) {} }
@Suppress("unused")
private val keepAlign = TextAlign.Center
