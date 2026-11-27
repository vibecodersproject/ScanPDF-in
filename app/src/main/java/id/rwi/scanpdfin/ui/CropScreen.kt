@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package id.rwi.scanpdfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Replay
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.Screen
import id.rwi.scanpdfin.image.ImageOps
import id.rwi.scanpdfin.image.Quad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CropScreen(vm: AppViewModel) {
    val session by vm.session.collectAsState()
    val settings by vm.settings.collectAsState()
    val s = session
    LaunchedEffect(s == null || s.captures.isEmpty()) {
        if ((s == null || s.captures.isEmpty()) && vm.screen == Screen.Crop) vm.back()
    }
    val cap = s?.current ?: return
    val index = s.index
    val n = s.captures.size
    val isEdit = s.editPageId != null
    val fromCamera = vm.inCameraFlow
    var deleteAsk by remember { mutableStateOf(false) }

    val bmp by produceState<ImageBitmap?>(initialValue = null, cap.file.path, cap.version) {
        value = null
        value = withContext(Dispatchers.IO) { ImageOps.decodeFile(cap.file, 2000)?.asImageBitmap() }
    }
    var corners by remember(cap.id, cap.version) { mutableStateOf(cap.corners) }
    val b = bmp
    val valid = b != null && Quad.isValid(corners, b.width.toFloat() / b.height)

    fun keep() = vm.setCorners(index, corners)

    // keluar dari Atur bingkai (bukan dari kamera) membuang semua foto sesi: tanya dulu
    var leaveAsk by remember { mutableStateOf(false) }
    fun leave() { keep(); if (!fromCamera && !isEdit && n > 0) leaveAsk = true else vm.back() }
    androidx.activity.compose.BackHandler { leave() }
    if (leaveAsk) ConfirmDialog(
        if (n == 1) "Buang foto ini?" else "Buang $n foto?",
        "Foto belum disimpan. Tekan Lanjut untuk menyimpannya.",
        confirmLabel = "Buang", onDismiss = { leaveAsk = false },
    ) { leaveAsk = false; vm.back() }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isEdit) "Atur bingkai halaman" else "Atur bingkai")
                        if (n > 1) Text("Foto ${index + 1} dari $n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } },
                actions = {
                    if (n > 1) {
                        IconButton(onClick = { keep(); vm.selectCapture(index - 1) }, enabled = index > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Foto sebelumnya") }
                        IconButton(onClick = { keep(); vm.selectCapture(index + 1) }, enabled = index < n - 1) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Foto berikutnya") }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Tool(Icons.Outlined.AutoFixHigh, "Deteksi ulang") { vm.redetect(index) }
                        Tool(Icons.Outlined.Fullscreen, "Seluruh foto") { vm.fullFrame(index) }
                        Tool(Icons.AutoMirrored.Outlined.RotateRight, "Putar") { keep(); vm.rotateCapture(index) }
                        if (!isEdit) {
                            if (fromCamera && n == 1) Tool(Icons.Outlined.Replay, "Ulangi foto") { vm.removeCapture(index); vm.backToCamera() }
                            else Tool(Icons.Outlined.Delete, "Hapus foto") { deleteAsk = true }
                        }
                    }
                    if (!valid && b != null) Text(
                        "Bingkai bersilangan atau terlalu kecil. Rapikan sudutnya dulu.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            isEdit -> Button(onClick = { keep(); vm.commitSession() }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Simpan") }
                            fromCamera -> {
                                OutlinedButton(onClick = { keep(); vm.backToCamera() }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Foto lagi") }
                                Button(onClick = { keep(); vm.goColor() }, enabled = valid, modifier = Modifier.weight(1f)) { Text(if (n > 1) "Lanjut ($n)" else "Lanjut") }
                            }
                            index < n - 1 -> {
                                OutlinedButton(onClick = { keep(); vm.goColor() }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Lanjut ($n)") }
                                Button(onClick = { keep(); vm.selectCapture(index + 1) }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Berikutnya") }
                            }
                            else -> Button(onClick = { keep(); vm.goColor() }, enabled = valid, modifier = Modifier.weight(1f)) { Text(if (n > 1) "Lanjut ($n)" else "Lanjut") }
                        }
                    }
                    if (n > 1 && !isEdit) Text(
                        "Foto yang tidak dibuka memakai bingkai otomatis.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        },
    ) { padding ->
        // petunjuk di baris sendiri di bawah foto, jadi tidak pernah menutupi bingkai/pegangan
        Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFF121212))) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (b == null) CircularProgressIndicator()
                else FrameEditor(
                    image = b, corners = corners, auto = cap.auto, snap = settings.snap,
                    onChange = { corners = it },
                    onCommit = { corners = it; vm.setCorners(index, it) },
                )
            }
            Text(
                "Tarik sudut, atau tarik tengah garis untuk menggeser sisi",
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    if (deleteAsk) ConfirmDialog("Hapus foto ini?", "Foto ini tidak ikut disimpan.", onDismiss = { deleteAsk = false }) {
        deleteAsk = false
        val empty = vm.removeCapture(index)
        if (empty) { if (fromCamera) vm.backToCamera() else vm.back() }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp).width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}
