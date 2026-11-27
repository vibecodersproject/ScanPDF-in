package id.rwi.scanpdfin

import android.app.Application
import id.rwi.scanpdfin.data.Prefs
import id.rwi.scanpdfin.data.Repository
import id.rwi.scanpdfin.export.Exporter

class ScanApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var pdfEngine: id.rwi.scanpdfin.pdf.PdfEngine
        private set
    lateinit var pdfStore: id.rwi.scanpdfin.pdf.PdfStore
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        prefs = Prefs(this)
        pdfEngine = id.rwi.scanpdfin.pdf.PdfEngine(this)
        pdfStore = id.rwi.scanpdfin.pdf.PdfStore(this)
        Exporter.cleanup(this)
        pdfEngine.cleanup(null)
    }
}
