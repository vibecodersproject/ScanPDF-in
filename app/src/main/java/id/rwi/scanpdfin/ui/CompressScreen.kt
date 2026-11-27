@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package id.rwi.scanpdfin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.CompressItem
import id.rwi.scanpdfin.CompressLevel

/** Menu Kecilkan PDF: pilih satu/banyak PDF → pilih tingkat → simpan/bagikan hasilnya. */
@Composable
fun CompressScreen(vm: AppViewModel) {
    val items by vm.pdf.compressList.collectAsState()
    var level by rememberSaveable { mutableStateOf(CompressLevel.SEDANG) }
    val done = items.isNotEmpty() && items.all { it.after != null || it.note != null }
    val results = items.filter { it.out != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kecilkan PDF") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } },
                actions = { if (items.isNotEmpty()) TextButton(onClick = { vm.pdf.clearCompress() }) { Text("Kosongkan") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.pdf.pickCompress() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tambah PDF", maxLines = 1)
                    }
                    if (done) Button(onClick = { vm.pdf.shareCompressed(results) }, enabled = results.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (results.size > 1) "Bagikan semua" else "Bagikan", maxLines = 1)
                    } else Button(onClick = { vm.pdf.runCompress(level) }, enabled = items.isNotEmpty() && !done, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Compress, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (items.size > 1) "Kecilkan ${items.size}" else "Kecilkan", maxLines = 1)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!done) {
                item {
                    Text("Tingkat", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    LevelPicker(level) { level = it }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (items.isEmpty()) "Belum ada PDF. Tekan Tambah PDF untuk memilih satu atau beberapa berkas sekaligus."
                        else "Berkas (${items.size})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            } else item {
                val before = items.sumOf { it.before }
                val after = items.sumOf { it.after ?: it.before }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Selesai", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${formatBytes(before)} → ${formatBytes(after)}" + if (before > 0 && after < before) "  (hemat ${hemat(before, after)})" else "")
                        Text("Berkas asli tidak diubah. Simpan atau bagikan hasilnya di bawah.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { vm.pdf.clearCompress(); vm.pdf.pickCompress() }) { Text("Kecilkan PDF lain") }
            }
            items(items, key = { it.id }) { CompressRow(vm, it, done) }
        }
    }
}

@Composable
private fun CompressRow(vm: AppViewModel, it: CompressItem, done: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PictureAsPdf, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    val size = when {
                        it.after != null && it.out != null -> "${formatBytes(it.before)} → ${formatBytes(it.after)}  (−${hemat(it.before, it.after)})"
                        else -> formatBytes(it.before)
                    }
                    Text("${it.pages} hal • $size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (it.out != null) Icon(Icons.Outlined.CheckCircle, "Berhasil", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
                else if (!done) IconButton(onClick = { vm.pdf.removeCompress(it.id) }) { Icon(Icons.Outlined.Close, "Keluarkan dari daftar") }
            }
            it.note?.let { n ->
                Row(Modifier.padding(top = 6.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (it.out != null) Row(Modifier.padding(top = 4.dp)) {
                TextButton(onClick = { vm.pdf.openCompressed(it) }) { Icon(Icons.Outlined.Visibility, null, Modifier.size(18.dp)); Text(" Lihat") }
                TextButton(onClick = { vm.pdf.saveCompressed(it) }) { Icon(Icons.Outlined.Save, null, Modifier.size(18.dp)); Text(" Simpan") }
                TextButton(onClick = { vm.pdf.shareCompressed(listOf(it)) }) { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Text(" Bagikan") }
            }
        }
    }
}

/** Persen hemat; tidak pernah tertulis 100% selama berkas masih berisi (mis. 99,7%). */
private fun hemat(before: Long, after: Long): String {
    if (before <= 0) return "0%"
    val p = (before - after) * 100.0 / before
    return if (p >= 99.5 || p < 10) "%.1f%%".format(java.util.Locale("id", "ID"), kotlin.math.min(p, 99.9)) else "%.0f%%".format(p)
}

/** Pilihan tingkat: Ringan / Sedang / Kuat beserta penjelasannya. */
@Composable
fun LevelPicker(level: CompressLevel, onPick: (CompressLevel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompressLevel.entries.forEach { l ->
            val sel = l == level
            Card(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(l) },
                shape = RoundedCornerShape(14.dp),
                border = if (sel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                colors = CardDefaults.cardColors(containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sel, onClick = { onPick(l) })
                    Column {
                        Text(l.label, style = MaterialTheme.typography.titleSmall)
                        Text(l.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Dialog pilih tingkat untuk PDF yang sedang dibuka di pembaca. */
@Composable
fun CompressLevelDialog(note: String, onDismiss: () -> Unit, onPick: (CompressLevel) -> Unit) {
    var level by rememberSaveable { mutableStateOf(CompressLevel.SEDANG) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kecilkan ukuran PDF") },
        text = {
            Column {
                LevelPicker(level) { level = it }
                Spacer(Modifier.height(8.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(level) }) { Text("Kecilkan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
