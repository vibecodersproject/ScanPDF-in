@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package id.rwi.scanpdfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.Screen

/** Layar sambutan: pilih menu Scan atau PDF. */
@Composable
fun WelcomeScreen(vm: AppViewModel) {
    val docs by vm.documents.collectAsState()
    val recent = docs.sortedByDescending { it.updatedAt }.take(8)
    Scaffold(
        topBar = {
            TopAppBar(title = {}, actions = { IconButton(onClick = { vm.go(Screen.Settings) }) { Icon(Icons.Outlined.Settings, "Pengaturan") } })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Scan dan PDF-in", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Mau mengerjakan apa?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            ModeCard(
                icon = Icons.Outlined.DocumentScanner, title = "Scan", subtitle = "Potret dokumen jadi PDF atau gambar",
                container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { vm.go(Screen.ScanHome) },
            ) {
                FilledTonalButton(onClick = { vm.go(Screen.ScanHome); vm.startCamera(null) }) {
                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Pindai sekarang")
                }
            }
            Spacer(Modifier.height(16.dp))
            ModeCard(
                icon = Icons.Outlined.PictureAsPdf, title = "PDF", subtitle = "Buka, baca, tanda tangan, atur halaman",
                container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onSurface,
                onClick = { vm.go(Screen.PdfHome) },
            )
            Spacer(Modifier.height(16.dp))
            ModeCard(
                icon = Icons.Outlined.Compress, title = "Kecilkan PDF", subtitle = "Perkecil ukuran PDF agar mudah dikirim",
                container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { vm.pdf.startCompress() },
            )

            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("Terakhir dibuka", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(recent, key = { it.id }) { doc ->
                        Column(Modifier.width(104.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.go(Screen.Doc(doc.id)) }) {
                            val first = doc.pages.firstOrNull()
                            if (first != null) PageThumb(vm, first, 300, Modifier.width(104.dp).height(136.dp).clip(RoundedCornerShape(12.dp)))
                            else Box(Modifier.width(104.dp).height(136.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                            Text(doc.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            } else Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector, title: String, subtitle: String, container: Color, content: Color,
    badge: String? = null, onClick: () -> Unit, extra: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(content.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(32.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (badge != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = content.copy(alpha = 0.12f)) {
                                Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
            if (extra != null) { Spacer(Modifier.height(14.dp)); extra() }
        }
    }
}

