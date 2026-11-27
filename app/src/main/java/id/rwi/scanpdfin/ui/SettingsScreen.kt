@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package id.rwi.scanpdfin.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.Screen
import id.rwi.scanpdfin.data.FilterMode
import id.rwi.scanpdfin.data.Paper
import id.rwi.scanpdfin.data.Quality
import id.rwi.scanpdfin.data.ThemeMode

private enum class Dlg { NONE, THEME, PAPER, FILTER, QUALITY }

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    var dlg by remember { mutableStateOf(Dlg.NONE) }
    var defaultPdf by remember { mutableStateOf(false) }
    val storage = remember { vm.storageBytes() }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Pengaturan") }, navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Tampilan")
            Item("Tema", s.theme.label) { dlg = Dlg.THEME }

            Section("Kamera & bingkai")
            Item("Tampilan bawaan halaman baru", s.defaultFilter.label) { dlg = Dlg.FILTER }
            SwitchItem("Mode beruntun", "Jepret banyak dulu, atur bingkai setelahnya. Bila mati: atur bingkai tiap habis jepret.", s.burst) { v -> vm.updateSettings { it.copy(burst = v) } }
            SwitchItem("Garis bantu tepi di kamera", "Tampilkan bingkai dokumen terdeteksi saat membidik.", s.liveEdges) { v -> vm.updateSettings { it.copy(liveEdges = v) } }
            SwitchItem("Tempel ke tepi terdeteksi", "Sudut bingkai menempel ke tepi kertas saat ditarik mendekat.", s.snap) { v -> vm.updateSettings { it.copy(snap = v) } }

            Section("Ekspor PDF")
            Item("Ukuran kertas bawaan", s.paper.label) { dlg = Dlg.PAPER }
            Item("Kualitas bawaan", s.quality.label) { dlg = Dlg.QUALITY }

            Section("PDF")
            Item("Jadikan pembuka PDF bawaan", "Agar PDF dari File Saya / WhatsApp langsung terbuka di aplikasi ini") { defaultPdf = true }

            Section("Penyimpanan")
            Item("Gambar halaman tersimpan", formatBytes(storage)) {}
            Item("Bersihkan cache thumbnail", "Thumbnail dibuat ulang saat dibutuhkan") { vm.clearCache() }

            Section("Lainnya")
            Item("Tentang Scan dan PDF-in", "Versi, lisensi, privasi") { vm.go(Screen.About) }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (defaultPdf) DefaultPdfDialog { defaultPdf = false }

    when (dlg) {
        Dlg.NONE -> Unit
        Dlg.THEME -> ChoiceDialog("Tema", ThemeMode.entries, s.theme, { it.label }, { dlg = Dlg.NONE }) { v -> vm.updateSettings { it.copy(theme = v) }; dlg = Dlg.NONE }
        Dlg.PAPER -> ChoiceDialog("Ukuran kertas", Paper.entries, s.paper, { it.label }, { dlg = Dlg.NONE }) { v -> vm.updateSettings { it.copy(paper = v) }; dlg = Dlg.NONE }
        Dlg.FILTER -> ChoiceDialog("Tampilan bawaan", FilterMode.choices, s.defaultFilter.normalized(), { it.label }, { dlg = Dlg.NONE }) { v -> vm.updateSettings { it.copy(defaultFilter = v) }; dlg = Dlg.NONE }
        Dlg.QUALITY -> ChoiceDialog("Kualitas", Quality.entries, s.quality, { it.label }, { dlg = Dlg.NONE }) { v -> vm.updateSettings { it.copy(quality = v) }; dlg = Dlg.NONE }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Item(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchItem(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun AboutScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "-" }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Tentang") }, navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Scan dan PDF-in", style = MaterialTheme.typography.headlineSmall)
            Text("Versi $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("Pemindai dokumen dan alat PDF. Tanpa iklan, tanpa login, tanpa internet. Semua dokumen tersimpan hanya di HP ini dan tidak dikirim ke mana pun.")
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Privasi", style = MaterialTheme.typography.titleMedium)
            Text("Aplikasi hanya meminta izin kamera, dan tidak meminta izin internet maupun penyimpanan. Foto dan dokumen disimpan di penyimpanan pribadi aplikasi; berkas hanya keluar bila Anda sendiri menyimpan atau membagikannya. Tidak ada pelacak, analitik, atau iklan.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Text("Pustaka sumber terbuka", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            listOf(
                "PdfBox-Android (Tom Roush) — Apache License 2.0",
                "Apache PDFBox — Apache License 2.0",
                "Bouncy Castle — MIT License",
                "AndroidX, Jetpack Compose, Material 3 — Apache License 2.0",
                "Kotlin, kotlinx.coroutines, kotlinx.serialization — Apache License 2.0",
                "CameraX (AndroidX) — Apache License 2.0",
                "OpenCV — Apache License 2.0",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(20.dp))
            Text("Masukan", style = MaterialTheme.typography.titleMedium)
            Text("Ada saran atau menemukan masalah? Kirim lewat WhatsApp atau email.", style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { open(ctx, "https://wa.me/6282234905381?text=Halo,%20saya%20pengguna%20Scan%20dan%20PDF-in") }) { Text("WhatsApp") }
                TextButton(onClick = { open(ctx, "mailto:rizkiismadani@gmail.com?subject=Scan%20dan%20PDF-in") }) { Text("Email") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Android tidak mengizinkan aplikasi menjadikan dirinya bawaan secara langsung.
 * Dialog ini menjelaskan langkahnya dan membuka halaman pengaturan yang tepat.
 */
@Composable
fun DefaultPdfDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pembuka PDF bawaan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Cara paling mudah:", style = MaterialTheme.typography.titleSmall)
                Text("1. Buka File Saya (atau WhatsApp), ketuk sebuah berkas PDF.\n2. Pilih \"Scan PDF-in\".\n3. Ketuk \"Selalu\". Mulai sekarang PDF langsung terbuka di sini.")
                Spacer(Modifier.height(10.dp))
                Text("Kalau Scan PDF-in tidak muncul / langsung terbuka di aplikasi lain:", style = MaterialTheme.typography.titleSmall)
                Text("Aplikasi lain sudah dijadikan bawaan. Buka Pengaturan HP → Aplikasi → (aplikasi PDF lain itu) → Buka secara default → Hapus default. Lalu ulangi cara di atas.")
                Spacer(Modifier.height(10.dp))
                Text("Tombol di bawah membuka halaman \"Buka secara default\" milik Scan PDF-in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pkg = Uri.parse("package:${ctx.packageName}")
                val intent = if (android.os.Build.VERSION.SDK_INT >= 31) Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
                else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                onDismiss()
            }) { Text("Buka pengaturan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

private fun open(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
