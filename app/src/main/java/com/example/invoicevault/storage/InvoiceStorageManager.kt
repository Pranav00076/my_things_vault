package com.example.invoicevault.storage

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
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
     * Creates a temporary file and associated content URI for the Camera capture intent.
     */
    fun createTempCameraUri(): Pair<File, Uri> {
        val tempFile = File.createTempFile(
            "camera_capture_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        )
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return Pair(tempFile, uri)
    }

    /**
     * Converts a private internal invoice file to a secure sharable content URI using FileProvider.
     */
    fun getContentUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Copies a document from an external content URI into private app storage.
     * Returns the absolute file path of the saved local copy.
     */
    suspend fun saveDocumentFromUri(sourceUri: Uri, preferredExtension: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val extension = preferredExtension
                    ?: getExtensionFromUri(sourceUri)
                    ?: "jpg"
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
     * Replaces an existing invoice file with a newly selected one, safely cleaning up the previous file.
     */
    suspend fun replaceDocument(
        oldFilePathOrUri: String?,
        newSourceUri: Uri,
        preferredExtension: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val saveResult = saveDocumentFromUri(newSourceUri, preferredExtension)
        if (saveResult.isSuccess && !oldFilePathOrUri.isNullOrBlank()) {
            deleteDocument(oldFilePathOrUri)
        }
        saveResult
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

    fun isPdf(filePathOrUri: String): Boolean {
        val path = filePathOrUri.lowercase(Locale.ROOT)
        return path.endsWith(".pdf")
    }

    fun isImage(filePathOrUri: String): Boolean {
        val path = filePathOrUri.lowercase(Locale.ROOT)
        return path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".png") || path.endsWith(".webp")
    }

    fun getReadableFileSize(filePathOrUri: String): String {
        val file = resolveFile(filePathOrUri) ?: return "Unknown size"
        val bytes = file.length()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        }
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
