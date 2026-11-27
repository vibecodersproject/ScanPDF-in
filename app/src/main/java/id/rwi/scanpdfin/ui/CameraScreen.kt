package id.rwi.scanpdfin.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.rwi.scanpdfin.AppViewModel
import id.rwi.scanpdfin.Screen
import id.rwi.scanpdfin.data.Flash
import id.rwi.scanpdfin.image.EdgeDetector
import id.rwi.scanpdfin.image.ImageOps
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

@Composable
fun CameraScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val session by vm.session.collectAsState()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }
    // cek ulang setelah kembali dari Pengaturan HP
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val count = session?.captures?.size ?: 0
    var exitAsk by remember { mutableStateOf(false) }
    BackHandler { if (count > 0) exitAsk = true else vm.back() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) CameraContent(vm, count, onClose = { if (count > 0) exitAsk = true else vm.back() })
        else PermissionPane(
            permanentlyDenied = asked && (ctx as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false,
            onAsk = { permLauncher.launch(Manifest.permission.CAMERA) },
            onSettings = {
                runCatching {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                }
            },
            onGallery = { vm.requestPick(session?.targetDocId) },
            onClose = { vm.back() },
        )
    }

    if (exitAsk) AlertDialog(
        onDismissRequest = { exitAsk = false },
        title = { Text("Simpan $count foto?") },
        text = { Text("Foto yang belum disimpan akan hilang bila dibuang.") },
        confirmButton = { TextButton(onClick = { exitAsk = false; vm.selectCapture(0); vm.go(Screen.Crop) }) { Text("Atur & simpan") } },
        dismissButton = {
            Row {
                TextButton(onClick = { exitAsk = false }) { Text("Batal") }
                TextButton(onClick = { exitAsk = false; vm.back() }) { Text("Buang", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

private val ratio43 = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()

@Composable
private fun CameraContent(vm: AppViewModel, count: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by vm.settings.collectAsState()
    val session by vm.session.collectAsState()
    val processing by vm.processing.collectAsState()
    val scope = rememberCoroutineScope()

    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(ratio43)
            .setJpegQuality(95)
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var liveQuad by remember { mutableStateOf<List<Float>?>(null) }
    var liveAspect by remember { mutableFloatStateOf(0.75f) }
    var capturing by remember { mutableStateOf(false) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    val shutterFlash = remember { Animatable(0f) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val main = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) { onDispose { analyzerExecutor.shutdown() } }

    DisposableEffect(lifecycleOwner, settings.liveEdges) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            if (disposed) return@addListener
            val p = try { future.get() } catch (_: Exception) { vm.toast("Kamera tidak bisa dibuka"); return@addListener }
            provider = p
            val preview = Preview.Builder().setResolutionSelector(ratio43).build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val useCases = mutableListOf<UseCase>(preview, imageCapture)
            if (settings.liveEdges) {
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analyzerExecutor, DocAnalyzer { q, aspect -> main.post { liveQuad = q; liveAspect = aspect } })
                useCases += analysis
            } else liveQuad = null
            try {
                p.unbindAll()
                camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
            } catch (e: Exception) {
                vm.toast("Kamera tidak bisa dibuka: ${e.message ?: ""}")
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            disposed = true
            provider?.unbindAll()
            camera = null
        }
    }

    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
    // Selalu nyala = lampu senter menyala terus di pratinjau & saat jepret. Dinyalakan ulang tiap kamera
    // terbuka kembali (mis. setelah app ditinggal sebentar), karena lampu padam saat kamera ditutup.
    DisposableEffect(settings.flash, camera, lifecycleOwner) {
        val cam = camera
        imageCapture.flashMode = if (settings.flash == Flash.OTOMATIS) ImageCapture.FLASH_MODE_AUTO else ImageCapture.FLASH_MODE_OFF
        val torchOn = settings.flash == Flash.NYALA
        if (cam == null || !cam.cameraInfo.hasFlashUnit()) return@DisposableEffect onDispose { }
        cam.cameraControl.enableTorch(torchOn)
        val observer = androidx.lifecycle.Observer<androidx.camera.core.CameraState> { st ->
            if (st.type == androidx.camera.core.CameraState.Type.OPEN && torchOn) cam.cameraControl.enableTorch(true)
        }
        cam.cameraInfo.cameraState.observe(lifecycleOwner, observer)
        onDispose { cam.cameraInfo.cameraState.removeObserver(observer) }
    }
    LaunchedEffect(focusAt) { if (focusAt != null) { kotlinx.coroutines.delay(900); focusAt = null } }

    fun shoot() {
        if (capturing || camera == null) return
        capturing = true
        previewView.display?.rotation?.let { imageCapture.targetRotation = it }
        val f = File(ctx.cacheDir, "jepret_${SystemClock.uptimeMillis()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(f).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    scope.launch { shutterFlash.snapTo(0.8f); shutterFlash.animateTo(0f, tween(260)) }
                    vm.addCameraShot(f)
                }
                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    vm.toast("Gagal memotret: ${exception.message ?: "coba lagi"}")
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // ---- bar atas ----
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            CamAction(Icons.Outlined.Close, "Tutup", onClick = onClose)
            CamAction(
                icon = when (settings.flash) {
                    Flash.MATI -> Icons.Outlined.FlashOff
                    Flash.OTOMATIS -> Icons.Outlined.FlashAuto
                    Flash.NYALA -> Icons.Outlined.FlashlightOn
                },
                label = if (hasFlash || camera == null) "Kilat: ${settings.flash.label}" else "Tanpa kilat",
                enabled = hasFlash,
                highlight = settings.flash != Flash.MATI,
            ) {
                val next = Flash.entries[(settings.flash.ordinal + 1) % Flash.entries.size]
                vm.updateSettings { it.copy(flash = next) }
                vm.toast("Lampu kilat: ${next.label}")
            }
            CamAction(
                icon = if (settings.burst) Icons.Outlined.BurstMode else Icons.Outlined.PhotoCamera,
                label = if (settings.burst) "Beruntun" else "Satu per satu",
                highlight = settings.burst,
            ) {
                val next = !settings.burst
                vm.updateSettings { it.copy(burst = next) }
                vm.toast(if (next) "Beruntun: jepret banyak, atur bingkai setelahnya" else "Satu per satu: atur bingkai tiap jepret")
            }
        }

        // ---- pratinjau 3:4 ----
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(4.dp))) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                val primary = MaterialTheme.colorScheme.primary
                Canvas(
                    Modifier.fillMaxSize().pointerInput(camera) {
                        detectTapGestures { o ->
                            val cam = camera ?: return@detectTapGestures
                            val pt = previewView.meteringPointFactory.createPoint(o.x, o.y)
                            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
                            focusAt = o
                        }
                    },
                ) {
                    liveQuad?.let { q ->
                        val scale = max(size.width / liveAspect, size.height)
                        val dw = liveAspect * scale; val dh = scale
                        val ox = (size.width - dw) / 2f; val oy = (size.height - dh) / 2f
                        val pts = (0 until 4).map { Offset(ox + q[it * 2] * dw, oy + q[it * 2 + 1] * dh) }
                        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                        drawPath(path, primary.copy(alpha = 0.18f))
                        drawPath(path, primary, style = Stroke(3.dp.toPx()))
                    }
                    focusAt?.let { drawCircle(Color.White, 34.dp.toPx(), it, style = Stroke(2.dp.toPx())) }
                    if (shutterFlash.value > 0f) drawRect(Color.White.copy(alpha = shutterFlash.value))
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                ) {
                    Text(
                        when {
                            processing > 0 -> "Memproses foto…"
                            !settings.liveEdges -> "Ketuk layar untuk fokus"
                            liveQuad != null -> "Dokumen terdeteksi, tekan jepret"
                            else -> "Arahkan kamera ke dokumen"
                        },
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // ---- bar bawah ----
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            CamAction(Icons.Outlined.PhotoLibrary, "Galeri", modifier = Modifier.width(76.dp)) { vm.requestPick(session?.targetDocId) }
            Box(
                Modifier.size(78.dp).border(4.dp, Color.White, CircleShape).padding(7.dp).clip(CircleShape)
                    .background(if (capturing) Color.Gray else Color.White)
                    .clickable(enabled = !capturing) { shoot() },
            )
            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val last = session?.captures?.lastOrNull()
                if (last != null) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, Color.White, RoundedCornerShape(10.dp))
                            .clickable { vm.selectCapture(if (settings.burst) 0 else count - 1); vm.go(Screen.Crop) },
                    ) {
                        FileThumb(last.file, last.version, Modifier.fillMaxSize())
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                            Text("$count", Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text("Lanjut", color = Color.White, style = MaterialTheme.typography.labelSmall)
                } else Spacer(Modifier.size(52.dp))
            }
        }
    }
}

/** Tombol ikon kamera dengan label di bawahnya. */
@Composable
private fun CamAction(
    icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, highlight: Boolean = false, onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = when { !enabled -> Color.White.copy(alpha = 0.35f); highlight -> Color(0xFFFFD54F); else -> Color.White }
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionPane(permanentlyDenied: Boolean, onAsk: () -> Unit, onSettings: () -> Unit, onGallery: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.NoPhotography, null, tint = Color.White, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Izinkan kamera", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (permanentlyDenied) "Izin kamera ditolak. Buka Pengaturan HP → Izin → Kamera → Izinkan, lalu kembali ke sini."
            else "Kamera dipakai untuk memotret dokumen. Foto hanya disimpan di HP ini dan tidak dikirim ke mana pun.",
            color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = if (permanentlyDenied) onSettings else onAsk, modifier = Modifier.fillMaxWidth()) {
            Text(if (permanentlyDenied) "Buka Pengaturan HP" else "Izinkan kamera")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) { Text("Impor dari galeri saja", color = Color.White) }
        TextButton(onClick = onClose) { Text("Kembali", color = Color.White.copy(alpha = 0.8f)) }
    }
}

/** Analisis pratinjau: deteksi tepi ±3–4 kali per detik untuk garis bantu. */
private class DocAnalyzer(private val onResult: (List<Float>?, Float) -> Unit) : ImageAnalysis.Analyzer {
    private var last = 0L
    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.uptimeMillis()
            if (now - last < 280) return
            last = now
            val upright = ImageOps.rotate(image.toBitmap(), image.imageInfo.rotationDegrees)
            val aspect = upright.width.toFloat() / upright.height
            val q = EdgeDetector.detect(upright, 320)
            upright.recycle()
            onResult(q, aspect)
        } catch (_: Throwable) {
        } finally {
            image.close()
        }
    }
}
