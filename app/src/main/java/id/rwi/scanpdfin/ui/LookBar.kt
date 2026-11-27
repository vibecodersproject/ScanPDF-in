package id.rwi.scanpdfin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.data.FilterMode
import id.rwi.scanpdfin.data.Look

/**
 * Bar pilihan tampilan seperti pemindai rujukan: Asli · Foto · Dokumen | Warna/Hitam.
 * Tiap tombol = ikon + label; yang aktif berwarna primer.
 */
@Composable
fun LookBar(current: FilterMode, modifier: Modifier = Modifier, onChange: (FilterMode) -> Unit) {
    val cur = current.normalized()
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
            LookButton(Icons.Outlined.Image, Look.ASLI.label, cur.look == Look.ASLI) { onChange(cur.withLook(Look.ASLI)) }
            LookButton(Icons.Outlined.PhotoCamera, Look.FOTO.label, cur.look == Look.FOTO) { onChange(cur.withLook(Look.FOTO)) }
            LookButton(Icons.Outlined.Article, Look.DOKUMEN.label, cur.look == Look.DOKUMEN) { onChange(cur.withLook(Look.DOKUMEN)) }
        }
        VerticalDivider(Modifier.height(36.dp).padding(horizontal = 4.dp))
        Spacer(Modifier.width(4.dp))
        if (cur.color) LookButton(Icons.Outlined.Palette, "Warna", true) { onChange(cur.withColor(false)) }
        else LookButton(Icons.Outlined.InvertColors, "Hitam", true) { onChange(cur.withColor(true)) }
    }
}

@Composable
private fun LookButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, softWrap = false)
    }
}
