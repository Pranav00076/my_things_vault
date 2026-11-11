package com.example.invoicevault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.invoicevault.InvoiceVaultApp
import com.example.invoicevault.ocr.InvoiceOcrProcessor
import com.example.invoicevault.ui.InvoiceDocumentPreview
import com.example.invoicevault.ui.InvoicePreviewHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicePreviewScreen(
    filePath: String,
    onNavigateBack: () -> Unit,
    onNavigateToOcrReview: (String) -> Unit,
    onNavigateToManualEntry: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as InvoiceVaultApp
    val storageManager = app.invoiceStorageManager
    val scope = rememberCoroutineScope()

    var isRunningOcr by remember { mutableStateOf(false) }
    var unreadableError by remember { mutableStateOf<String?>(null) }

    val file = remember(filePath) { File(filePath) }
    val isPdf = remember(filePath) { storageManager.isPdf(filePath) }
    val fileSize = remember(filePath) { storageManager.getReadableFileSize(filePath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice Preview", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { InvoicePreviewHelper.openInExternalViewer(context, filePath) }) {
                        Text("Open Full 🔍")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Document Card Preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InvoiceDocumentPreview(
                        filePath = filePath,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // File Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isPdf) "PDF Document" else "Image Receipt",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Size: $fileSize • ${file.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isPdf) "PDF" else "IMG",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // OCR Extraction Action
            if (isRunningOcr) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Running On-Device OCR...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Extracting invoice number, date, seller & totals", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            isRunningOcr = true
                            try {
                                val ocrData = InvoiceOcrProcessor.processInvoiceDocument(context, filePath)
                                isRunningOcr = false
                                if (ocrData.rawExtractedText.isBlank() || ocrData.extractedFieldCount == 0) {
                                    unreadableError = "No text could be extracted from this document. The image may be too blurry, dark, or low-resolution. Please retake or enter details manually."
                                } else {
                                    onNavigateToOcrReview(filePath)
                                }
                            } catch (e: Exception) {
                                isRunningOcr = false
                                unreadableError = "Error reading document: ${e.message}. Please try again or enter details manually."
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("✨ Auto-Extract with OCR", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { onNavigateToManualEntry(filePath) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Enter Details Manually")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Unreadable Document Alert Dialog
    unreadableError?.let { msg ->
        AlertDialog(
            onDismissRequest = { unreadableError = null },
            title = { Text("⚠️ Unreadable Document") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    unreadableError = null
                    onNavigateToManualEntry(filePath)
                }) {
                    Text("Enter Manually")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    unreadableError = null
                    onNavigateBack()
                }) {
                    Text("Retake Photo")
                }
            }
        )
    }
}

