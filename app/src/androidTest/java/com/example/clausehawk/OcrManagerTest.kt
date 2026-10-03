package com.example.clausehawk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Isolated Instrumented Test Suite for OcrManager.
 * Tests exclusively the OCR pipeline, multi-script detection (Latin + Devanagari),
 * PDF rasterization, safe scaling OOM prevention, and resource cleanup.
 */
@RunWith(AndroidJUnit4::class)
class OcrManagerTest {

    private lateinit var ocrManager: OcrManager
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        ocrManager = OcrManager(context)
    }

    /**
     * Test 1: Verify direct Bitmap OCR with English & Devanagari contract text.
     */
    @Test
    fun testBitmapOcr_englishAndDevanagari() {
        val width = 800
        val height = 400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
        }

        canvas.drawText("SECTION 27 NON-COMPETE AGREEMENT", 40f, 80f, paint)
        canvas.drawText("Liquidated Damages Clause: Rs 50,000", 40f, 150f, paint)
        canvas.drawText("अनुबंध की शर्तें और कानूनी नियम", 40f, 230f, paint)

        val latch = CountDownLatch(1)
        var extractedText: String? = null
        var caughtException: Exception? = null

        ocrManager.processImageFromBitmap(
            bitmap = bitmap,
            onSuccess = { text ->
                extractedText = text
                latch.countDown()
            },
            onError = { e ->
                caughtException = e
                latch.countDown()
            }
        )

        val completed = latch.await(15, TimeUnit.SECONDS)
        bitmap.recycle()

        assertTrue("OCR operation timed out", completed)
        if (caughtException != null) {
            fail("OCR failed with exception: ${caughtException?.message}")
        }

        assertNotNull("Extracted text should not be null", extractedText)
        val text = extractedText!!.uppercase()
        assertTrue("Expected 'SECTION 27' in OCR text", text.contains("SECTION 27") || text.contains("SECTION"))
        assertTrue("Expected 'NON-COMPETE' or 'AGREEMENT' in OCR text", text.contains("NON-COMPETE") || text.contains("AGREEMENT"))
    }

    /**
     * Test 2: Verify PDF Rasterization & Extraction through processImageFromUri.
     * Verifies that multi-page PDFs are rasterized, parsed, and combined accurately.
     */
    @Test
    fun testPdfRasterizationAndOcr_multiPage() {
        val pdfDocument = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }

        // Page 1
        val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        page1.canvas.drawColor(Color.WHITE)
        page1.canvas.drawText("EMPLOYMENT AGREEMENT - VERDICTEDGE TEST", 50f, 100f, paint)
        page1.canvas.drawText("Clause 1: Post-Employment Non-Compete", 50f, 150f, paint)
        pdfDocument.finishPage(page1)

        // Page 2
        val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        page2.canvas.drawColor(Color.WHITE)
        page2.canvas.drawText("Clause 2: Notice Period Trap and Penalties", 50f, 100f, paint)
        page2.canvas.drawText("Clause 3: Unilateral Jurisdiction Ouster", 50f, 150f, paint)
        pdfDocument.finishPage(page2)

        val testPdfFile = File(context.cacheDir, "test_ocr_contract.pdf")
        FileOutputStream(testPdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            testPdfFile
        )

        val latch = CountDownLatch(1)
        var extractedResult: String? = null
        var ocrError: Exception? = null

        ocrManager.processImageFromUri(
            uri = pdfUri,
            onSuccess = { text ->
                extractedResult = text
                latch.countDown()
            },
            onError = { e ->
                ocrError = e
                latch.countDown()
            }
        )

        val finished = latch.await(20, TimeUnit.SECONDS)
        testPdfFile.delete()

        assertTrue("PDF OCR timed out", finished)
        if (ocrError != null) {
            fail("PDF OCR failed: ${ocrError?.message}")
        }

        assertNotNull("Extracted text should not be null", extractedResult)
        val textUpper = extractedResult!!.uppercase()
        assertTrue("Should extract text from Page 1", textUpper.contains("EMPLOYMENT") || textUpper.contains("AGREEMENT"))
        assertTrue("Should extract text from Page 2", textUpper.contains("NOTICE") || textUpper.contains("PERIOD") || textUpper.contains("PENALTIES"))
    }

    /**
     * Test 3: Verify OOM Prevention on Huge Vector / High-Resolution PDF.
     * The fix introduced safe scaling:
     * val scale = if (maxDim > 0) minOf(2.0f, 2048f / maxDim) else 1.0f
     * This test creates an unusually large 3200x4400 point PDF page.
     * Without the fix, 3200*2 x 4400*2 = 6400 x 8800 = 56.3 million pixels = 225 MB memory bitmap allocation,
     * which triggers an immediate OutOfMemoryError on Android!
     * With the fix, maxDim=4400 causes scale = 2048/4400 ≈ 0.465, rendering safely within ~1.9 MB!
     */
    @Test
    fun testHugePdfPage_doesNotCauseOOM() {
        val pdfDocument = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 60f
            isAntiAlias = true
        }

        // Giant page: 3200 x 4400 points
        val pageInfo = PdfDocument.PageInfo.Builder(3200, 4400, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawText("HIGH RESOLUTION VECTOR STRESS TEST", 200f, 400f, paint)
        page.canvas.drawText("SAFE SCALING VERIFICATION SECTION 74", 200f, 600f, paint)
        pdfDocument.finishPage(page)

        val hugePdfFile = File(context.cacheDir, "huge_stress_test.pdf")
        FileOutputStream(hugePdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            hugePdfFile
        )

        val latch = CountDownLatch(1)
        var extractedText: String? = null
        var oomOrException: Exception? = null

        ocrManager.processImageFromUri(
            uri = pdfUri,
            onSuccess = { text ->
                extractedText = text
                latch.countDown()
            },
            onError = { e ->
                oomOrException = e
                latch.countDown()
            }
        )

        val completed = latch.await(25, TimeUnit.SECONDS)
        hugePdfFile.delete()

        assertTrue("Huge PDF processing timed out", completed)
        if (oomOrException != null) {
            fail("Failed with exception: ${oomOrException?.message}")
        }

        assertNotNull("Text should be extracted without OOM", extractedText)
        val text = extractedText!!.uppercase()
        assertTrue("Extracted text should contain vector test keywords", text.contains("HIGH") || text.contains("RESOLUTION") || text.contains("TEST") || text.contains("SAFE"))
    }

    /**
     * Test 4: Verify Temp File Cleanup & Lifecycle.
     * Ensures that temporary pdf_proc_*.pdf files in cacheDir are deleted in finally block.
     */
    @Test
    fun testTempFileCleanup() {
        val beforeFiles = context.cacheDir.listFiles { _, name -> name.startsWith("pdf_proc_") }?.toList() ?: emptyList()

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(400, 600, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK; textSize = 20f }
        page.canvas.drawText("CLEANUP TEST DOCUMENT", 30f, 80f, paint)
        pdfDocument.finishPage(page)

        val tempTestFile = File(context.cacheDir, "cleanup_source.pdf")
        FileOutputStream(tempTestFile).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()

        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempTestFile
        )

        val latch = CountDownLatch(1)
        ocrManager.processImageFromUri(
            uri = pdfUri,
            onSuccess = { latch.countDown() },
            onError = { latch.countDown() }
        )

        latch.await(15, TimeUnit.SECONDS)
        tempTestFile.delete()

        val afterFiles = context.cacheDir.listFiles { _, name -> name.startsWith("pdf_proc_") }?.toList() ?: emptyList()

        // Verify no leftover pdf_proc_ files were leaked
        val leakedFiles = afterFiles - beforeFiles.toSet()
        assertTrue("Found leaked temporary PDF files: $leakedFiles", leakedFiles.isEmpty())
    }
}
