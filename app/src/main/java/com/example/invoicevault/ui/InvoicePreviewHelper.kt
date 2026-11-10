package com.example.invoicevault.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.invoicevault.InvoiceVaultApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object InvoicePreviewHelper {

    suspend fun loadPreviewBitmap(filePath: String, maxDimension: Int = 1024): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext null

            try {
                if (filePath.lowercase().endsWith(".pdf")) {
                    renderPdfFirstPage(file, maxDimension)
                } else {
                    decodeSampledBitmap(file, maxDimension)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun renderPdfFirstPage(file: File, maxDimension: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                page = renderer.openPage(0)
                val scale = minOf(
                    1.0f,
                    maxDimension.toFloat() / maxOf(page.width, page.height)
                )
                val destWidth = (page.width * scale).toInt().coerceAtLeast(100)
                val destHeight = (page.height * scale).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                // White background for PDFs
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } else null
        } finally {
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }

    private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        var inSampleSize = 1
        if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    fun openInExternalViewer(context: Context, filePath: String) {
        val storageManager = (context.applicationContext as InvoiceVaultApp).invoiceStorageManager
        val file = storageManager.resolveFile(filePath) ?: return
        if (!file.exists()) return

        val contentUri = storageManager.getContentUriForFile(file)
        val mimeType = if (storageManager.isPdf(filePath)) "application/pdf" else "image/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open Invoice Document"))
    }
}

/**
 * Preview component for Invoices supporting both images and PDFs.
 */
@Composable
fun InvoiceDocumentPreview(
    filePath: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(filePath) { mutableStateOf(true) }
    val isPdf = remember(filePath) { filePath.lowercase().endsWith(".pdf") }

    LaunchedEffect(filePath) {
        isLoading = true
        bitmap = InvoicePreviewHelper.loadPreviewBitmap(filePath)
        isLoading = false
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = if (isPdf) "PDF Document Preview" else "Image Preview",
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (isPdf) "📄 PDF Document" else "🖼️ Image Document",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Preview unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // PDF Badge overlay
        if (isPdf && bitmap != null) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp, topEnd = 0.dp, bottomStart = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
            ) {
                Text(
                    text = "PDF",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

