package id.rwi.scanpdfin.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.data.Page
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Notifikasi kecil semi-transparan di bawah, hilang sendiri. */
@Composable
fun ToastHost(vm: AppViewModel) {
    val msg by vm.toast.collectAsState()
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = msg != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            var shown by remember { mutableStateOf(msg ?: "") }
            if (msg != null) shown = msg!!
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Text(shown, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Lapisan sibuk (proses ekspor/impor) dengan progres. */
@Composable
fun BusyOverlay(vm: AppViewModel) {
    val busy by vm.busy.collectAsState()
    // selama proses: tombol Kembali ditahan dan layar tidak padam
    androidx.activity.compose.BackHandler(enabled = busy != null) { }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(busy != null) {
        view.keepScreenOn = busy != null
        onDispose { view.keepScreenOn = false }
    }
    AnimatedVisibility(visible = busy != null, enter = fadeIn(), exit = fadeOut()) {
        // menelan semua sentuhan agar tombol di bawahnya tidak tertekan dua kali
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp, modifier = Modifier.padding(32.dp)) {
                Column(Modifier.padding(24.dp).width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val p = busy?.progress
                    if (p == null) CircularProgressIndicator() else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Text(busy?.text ?: "", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Thumbnail halaman: skeleton saat memuat. */
@Composable
fun PageThumb(vm: AppViewModel, page: Page, maxSide: Int, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = page.renderKey, key2 = maxSide) {
        value = try { vm.repo.thumbnail(page, maxSide) } catch (_: Exception) { null }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.DocumentScanner, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(28.dp))
        }
    }
}

/** Thumbnail kecil langsung dari berkas (foto sesi pindai). */
@Composable
fun FileThumb(file: java.io.File, version: Int, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = file.path, key2 = version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { id.rwi.scanpdfin.image.ImageOps.decodeFile(file, 200) }
    }
    Box(modifier.background(Color.DarkGray)) {
        bmp?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String = "Simpan",
    placeholder: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, singleLine = true,
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String = "Hapus", destructive: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Baris pilihan (radio) sederhana untuk dialog. */
@Composable
fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (opt == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label(opt), Modifier.weight(1f).clickableNoRipple { onSelect(opt) })
                        androidx.compose.material3.RadioButton(selected = opt == selected, onClick = { onSelect(opt) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

private val dateFmt = SimpleDateFormat("d MMM yyyy, HH.mm", Locale("id", "ID"))
fun formatDate(ms: Long): String = dateFmt.format(Date(ms))

fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.0f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}
