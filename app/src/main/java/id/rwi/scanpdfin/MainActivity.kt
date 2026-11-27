package id.rwi.scanpdfin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import id.rwi.scanpdfin.export.Exporter
import id.rwi.scanpdfin.ui.AboutScreen
import id.rwi.scanpdfin.ui.AppTheme
import id.rwi.scanpdfin.ui.BusyOverlay
import id.rwi.scanpdfin.ui.CameraScreen
import id.rwi.scanpdfin.ui.ColorScreen
import id.rwi.scanpdfin.ui.CompressScreen
import id.rwi.scanpdfin.ui.CropScreen
import id.rwi.scanpdfin.ui.DocumentScreen
import id.rwi.scanpdfin.ui.HomeScreen
import id.rwi.scanpdfin.ui.PageScreen
import id.rwi.scanpdfin.ui.PasswordDialog
import id.rwi.scanpdfin.ui.PendingOpenDialog
import id.rwi.scanpdfin.ui.PdfEditScreen
import id.rwi.scanpdfin.ui.PdfHomeScreen
import id.rwi.scanpdfin.ui.PdfMergeScreen
import id.rwi.scanpdfin.ui.PdfPagesScreen
import id.rwi.scanpdfin.ui.PdfReaderScreen
import id.rwi.scanpdfin.ui.PdfSignaturesScreen
import id.rwi.scanpdfin.ui.SettingsScreen
import id.rwi.scanpdfin.ui.ToastHost
import id.rwi.scanpdfin.ui.WelcomeScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private var pickTarget: String? = null
    private var pendingSave: File? = null

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) vm.importToSession(uris, pickTarget)
        pickTarget = null
    }

    /** Hasil pemilih "Simpan ke…": tulis berkas; bila berkas sumber hilang, hapus berkas kosong yang sudah dibuat sistem. */
    private fun onSaveTarget(uri: Uri?) {
        val f = pendingSave
        pendingSave = null
        if (uri == null) return
        if (f != null && f.exists()) vm.saveTo(f, uri)
        else {
            runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, uri) }
            vm.toast("Berkas sudah tidak tersedia, silakan ulangi")
        }
    }
    // jenis berkas yang benar agar Drive/Files memberi ekstensi & ikon yang tepat
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { onSaveTarget(it) }
    private val savePdfFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { onSaveTarget(it) }
    private val saveJpgLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { onSaveTarget(it) }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // bila sistem mematikan app saat pemilih berkas terbuka, tujuan pemilihan tetap diingat
        outState.putString("pickTarget", pickTarget)
        outState.putString("pdfPurpose", pdfPurpose.name)
        outState.putString("pendingSave", pendingSave?.path)
        outState.putString("pendingPdfSave", pendingPdfSave?.path)
    }

    private fun restorePickState(b: Bundle) {
        pickTarget = b.getString("pickTarget")
        pdfPurpose = b.getString("pdfPurpose")?.let { n -> PickPurpose.entries.firstOrNull { it.name == n } } ?: PickPurpose.OPEN
        pendingSave = b.getString("pendingSave")?.let(::File)
        pendingPdfSave = b.getString("pendingPdfSave")?.let(::File)
    }

    // ---- menu PDF ----
    private var pdfPurpose = PickPurpose.OPEN
    private var pendingPdfSave: File? = null

    private val openPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pdf.onPicked(pdfPurpose, listOf(uri))
    }
    private val openPdfsLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.pdf.onPicked(pdfPurpose, uris)
    }
    private val pdfImagesLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) vm.pdf.onPicked(pdfPurpose, uris)
    }
    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = pendingPdfSave
        pendingPdfSave = null
        if (uri == null) return@registerForActivityResult
        if (f != null && f.exists()) vm.pdf.onSavedAs(uri, f)
        else {
            runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, uri) }
            vm.toast("PDF sudah tidak tersedia, silakan buka dan simpan ulang")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIncoming(intent) else restorePickState(savedInstanceState)

        setContent {
            val settings by vm.settings.collectAsState()
            AppTheme(mode = settings.theme) {
                val stack by vm.stack.collectAsState()
                val screen = stack.last()
                val request by vm.request.collectAsState()

                LaunchedEffect(request) {
                    when (val r = request) {
                        null -> Unit
                        is Request.PickImages -> {
                            pickTarget = r.intoDocId
                            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            vm.consumeRequest()
                        }
                        is Request.SaveFile -> {
                            pendingSave = r.file
                            when (r.mime) {
                                "application/pdf" -> savePdfFileLauncher.launch(r.file.name)
                                "image/jpeg" -> saveJpgLauncher.launch(r.file.name)
                                else -> saveLauncher.launch(r.file.name)
                            }
                            vm.consumeRequest()
                        }
                        is Request.Share -> {
                            try { startActivity(Exporter.shareIntent(this@MainActivity, r.files, r.mime, r.subject)) }
                            catch (_: Exception) { vm.toast("Tidak ada aplikasi untuk membagikan") }
                            vm.consumeRequest()
                        }
                        is Request.PickPdf -> {
                            pdfPurpose = r.purpose
                            try {
                                if (r.multiple) openPdfsLauncher.launch(arrayOf("application/pdf")) else openPdfLauncher.launch(arrayOf("application/pdf"))
                            } catch (_: Exception) { vm.toast("Pemilih berkas tidak tersedia") }
                            vm.consumeRequest()
                        }
                        is Request.PickImagesFor -> {
                            pdfPurpose = r.purpose
                            pdfImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            vm.consumeRequest()
                        }
                        is Request.SavePdf -> {
                            pendingPdfSave = r.file
                            savePdfLauncher.launch(r.file.name)
                            vm.consumeRequest()
                        }
                    }
                }

                BackHandler(enabled = stack.size > 1) { vm.back() }

                Box(Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.Welcome -> WelcomeScreen(vm)
                        Screen.ScanHome -> HomeScreen(vm)
                        Screen.PdfHome -> PdfHomeScreen(vm)
                        is Screen.Doc -> DocumentScreen(vm, screen.id)
                        is Screen.PageView -> PageScreen(vm, screen.docId, screen.startIndex)
                        Screen.Camera -> CameraScreen(vm)
                        Screen.Crop -> CropScreen(vm)
                        Screen.Color -> ColorScreen(vm)
                        Screen.Settings -> SettingsScreen(vm)
                        Screen.About -> AboutScreen(vm)
                        is Screen.PdfReader -> PdfReaderScreen(vm, screen.startPage)
                        Screen.PdfPages -> PdfPagesScreen(vm)
                        is Screen.PdfEdit -> PdfEditScreen(vm, screen.page, screen.start)
                        Screen.PdfMerge -> PdfMergeScreen(vm)
                        Screen.PdfSignatures -> PdfSignaturesScreen(vm)
                        Screen.Compress -> CompressScreen(vm)
                    }
                    PasswordDialog(vm)
                    PendingOpenDialog(vm)
                    BusyOverlay(vm)
                    ToastHost(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** Gambar yang dibagikan dari app lain → langsung ke editor bingkai (layar sambutan dilewati). */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        // PDF dibuka dari app lain ("Buka dengan" / dibagikan) → langsung ke pembaca
        val pdfUri: Uri? = when (intent.action) {
            // semua VIEW yang sampai ke sini sudah lolos penyaring PDF di manifest; isi berkas diperiksa saat dibuka
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.takeIf { intent.type == "application/pdf" }
            else -> null
        }
        if (pdfUri != null) {
            if (vm.screen == Screen.Welcome) vm.go(Screen.PdfHome)
            vm.pdf.open(pdfUri, null, null)
            return
        }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            if (vm.screen == Screen.Welcome) vm.go(Screen.ScanHome)
            vm.importToSession(uris, null)
        }
    }
}
