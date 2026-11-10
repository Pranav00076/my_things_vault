package com.example.invoicevault.storage

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Handles persistent local storage of invoice documents (images and PDFs)
 * in the application's secure private directory.
 */
class InvoiceStorageManager(private val context: Context) {

    private val invoiceDir: File by lazy {
        File(context.filesDir, "invoices").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Copies a document from an external content URI into private app storage.
     * Returns the absolute file path / URI string of the saved local copy.
     */
    suspend fun saveDocumentFromUri(sourceUri: Uri, preferredExtension: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val extension = preferredExtension
                    ?: getExtensionFromUri(sourceUri)
                    ?: "pdf"
                val uniqueFileName = "invoice_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
                val destinationFile = File(invoiceDir, uniqueFileName)

                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: error("Unable to open input stream for URI: $sourceUri")

                destinationFile.absolutePath
            }
        }

    /**
     * Saves raw bytes (e.g. from camera capture or OCR scanner) into private app storage.
     */
    suspend fun saveDocumentBytes(bytes: ByteArray, extension: String = "jpg"): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uniqueFileName = "invoice_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
                val destinationFile = File(invoiceDir, uniqueFileName)

                FileOutputStream(destinationFile).use { outputStream ->
                    outputStream.write(bytes)
                }

                destinationFile.absolutePath
            }
        }

    /**
     * Checks if the locally stored invoice file exists.
     */
    fun fileExists(filePathOrUri: String): Boolean {
        val file = resolveFile(filePathOrUri)
        return file?.exists() == true
    }

    /**
     * Returns the File object for an invoice file path or file URI.
     */
    fun resolveFile(filePathOrUri: String): File? {
        val path = if (filePathOrUri.startsWith("file://")) {
            Uri.parse(filePathOrUri).path
        } else {
            filePathOrUri
        }
        return path?.let { File(it) }
    }

    /**
     * Deletes the local invoice file when the invoice record is deleted.
     */
    suspend fun deleteDocument(filePathOrUri: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = resolveFile(filePathOrUri)
            file?.delete() ?: false
        }

    private fun getExtensionFromUri(uri: Uri): String? {
        return if (uri.scheme == "content") {
            val mimeType = context.contentResolver.getType(uri)
            mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        } else {
            MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        }
    }
}

