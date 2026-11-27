@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package id.rwi.scanpdfin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.ExportFormat
import id.rwi.scanpdfin.data.Document
import id.rwi.scanpdfin.data.FilterMode
import id.rwi.scanpdfin.data.Page
import id.rwi.scanpdfin.data.Paper
import id.rwi.scanpdfin.data.Quality

/**
 * Lembar pilihan sebelum berbagi/menyimpan: format, tampilan, kertas, kualitas.
 * [pages] = halaman yang akan diekspor (semua atau yang terpilih).
 */
@Composable
fun ShareSheet(vm: AppViewModel, doc: Document, pages: List<Page>, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var mode by remember { mutableStateOf<FilterMode?>(null) } // null = sesuai tiap halaman
    var paper by remember { mutableStateOf(settings.paper) }
    var quality by remember { mutableStateOf(settings.quality) }
    var name by remember { mutableStateOf(doc.name) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Bagikan", style = MaterialTheme.typography.titleMedium)
            Text(
                if (pages.size == doc.pages.size) "Semua halaman (${pages.size})" else "${pages.size} halaman terpilih",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Nama berkas") },
                suffix = { Text(if (format == ExportFormat.PDF) ".pdf" else ".jpg", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            Label("Bagikan sebagai")
            ChipRow(ExportFormat.entries, format, { it.label }) { format = it }

            Spacer(Modifier.height(12.dp))
            Label("Tampilan")
            ChipRow(listOf<FilterMode?>(null) + FilterMode.choices, mode, { it?.label ?: "Sesuai halaman" }) { mode = it }

            if (format == ExportFormat.PDF) {
                Spacer(Modifier.height(12.dp))
                Label("Ukuran kertas")
                ChipRow(Paper.entries, paper, { it.label.substringBefore(" (") }) { paper = it }
            }

            Spacer(Modifier.height(12.dp))
            Label("Ukuran berkas")
            ChipRow(Quality.entries, quality, { it.label }) { quality = it }

            Spacer(Modifier.height(20.dp))
            val fileName = name.trim().ifBlank { doc.name }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onDismiss(); vm.export(doc, pages, format, share = false, paper = paper, quality = quality, modeOverride = mode, fileName = fileName) },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(8.dp)); Text("Simpan ke…") }
                Button(
                    onClick = { onDismiss(); vm.export(doc, pages, format, share = true, paper = paper, quality = quality, modeOverride = mode, fileName = fileName) },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Bagikan") }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { opt ->
            FilterChip(selected = opt == selected, onClick = { onSelect(opt) }, label = { Text(label(opt)) })
        }
    }
}
