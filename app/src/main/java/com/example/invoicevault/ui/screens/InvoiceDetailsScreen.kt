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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.invoicevault.InvoiceVaultApp
import com.example.invoicevault.data.InvoiceRepository
import com.example.invoicevault.ui.InvoiceDocumentPreview
import com.example.invoicevault.ui.InvoicePreviewHelper
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailsScreen(
    invoiceId: Long,
    invoiceRepository: InvoiceRepository,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onDeletedSuccess: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as InvoiceVaultApp
    val storageManager = app.invoiceStorageManager
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val itemFlow = remember(invoiceId) { invoiceRepository.observeInvoiceWithProduct(invoiceId) }
    val item by itemFlow.collectAsState(initial = null)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { onNavigateToEdit(invoiceId) }) {
                        Text("Edit")
                    }
                }
            )
        }
    ) { padding ->
        val currentItem = item
        if (currentItem == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val invoice = currentItem.invoice
            val product = currentItem.product

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Large Document Preview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        InvoiceDocumentPreview(
                            filePath = invoice.invoiceFileUri,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(bottomStart = 12.dp),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            TextButton(onClick = { InvoicePreviewHelper.openInExternalViewer(context, invoice.invoiceFileUri) }) {
                                Text("Open Full View 🔍", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Invoice Amount & Number Hero Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total Amount", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    String.format(Locale.US, "$%.2f", invoice.totalAmount),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = invoice.invoiceType,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Invoice #${invoice.invoiceNumber}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = {
                                clipboardManager.setText(AnnotatedString(invoice.invoiceNumber))
                                Toast.makeText(context, "Invoice number copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Copy #")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Billing & Transaction Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Transaction Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        DetailRow("Seller / Merchant", invoice.sellerName)
                        DetailRow("Purchase Date", invoice.purchaseDate)
                        DetailRow("Storage Location", storageManager.getReadableFileSize(invoice.invoiceFileUri))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Associated Product & Warranty Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Associated Product & Warranty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        DetailRow("Product Name", product.productName)
                        DetailRow("Brand", product.brand)
                        DetailRow("Category", product.category)
                        DetailRow(
                            "Warranty Duration",
                            if (product.warrantyDuration > 0) "${product.warrantyDuration} ${product.warrantyUnit.name.lowercase().replaceFirstChar { it.uppercase(Locale.ROOT) }}" else "No warranty recorded"
                        )
                        if (product.productDescription.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Description & Notes:", style = MaterialTheme.typography.labelMedium)
                            Text(product.productDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onNavigateToEdit(invoiceId) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Edit Details")
                    }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(0.8f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Delete")
                    }
                }

                Spacer(Modifier.height(30.dp))
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Invoice?") },
                    text = { Text("Are you sure you want to delete invoice #${invoice.invoiceNumber}? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                storageManager.deleteDocument(invoice.invoiceFileUri)
                                invoiceRepository.deleteInvoice(invoice)
                                showDeleteConfirm = false
                                Toast.makeText(context, "Invoice deleted", Toast.LENGTH_SHORT).show()
                                onDeletedSuccess()
                            }
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

