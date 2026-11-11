package com.example.invoicevault.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.invoicevault.InvoiceVaultApp
import com.example.invoicevault.data.Invoice
import com.example.invoicevault.data.InvoiceTypes
import com.example.invoicevault.data.Product
import com.example.invoicevault.ocr.InvoiceOcrProcessor
import com.example.invoicevault.ui.InvoiceDocumentPreview
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInvoiceScreen(
    invoiceId: Long,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onDeletedSuccess: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as InvoiceVaultApp
    val invoiceRepo = app.invoiceRepository
    val productRepo = app.productRepository
    val storageManager = app.invoiceStorageManager
    val scope = rememberCoroutineScope()

    val invoiceWithProductFlow = remember(invoiceId) { invoiceRepo.observeInvoiceWithProduct(invoiceId) }
    val item by invoiceWithProductFlow.collectAsState(initial = null)
    val products by productRepo.products.collectAsState(initial = emptyList())

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Invoice Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
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
            val inv = currentItem.invoice
            var invoiceNumber by remember(inv) { mutableStateOf(inv.invoiceNumber) }
            var purchaseDate by remember(inv) { mutableStateOf(inv.purchaseDate) }
            var sellerName by remember(inv) { mutableStateOf(inv.sellerName) }
            var totalAmount by remember(inv) { mutableStateOf(inv.totalAmount.toString()) }
            var invoiceType by remember(inv) { mutableStateOf(inv.invoiceType) }
            var selectedProduct by remember(inv) { mutableStateOf(currentItem.product) }
            var currentFilePath by remember(inv) { mutableStateOf(inv.invoiceFileUri) }
            var isSaving by remember { mutableStateOf(false) }

            var expandedTypeDropdown by remember { mutableStateOf(false) }
            var expandedProductDropdown by remember { mutableStateOf(false) }

            val normalizedDate = remember(purchaseDate) {
                if (runCatching { LocalDate.parse(purchaseDate.trim()) }.isSuccess) {
                    purchaseDate.trim()
                } else {
                    InvoiceOcrProcessor.normalizeDate(purchaseDate.trim())
                }
            }
            val dateIsValid = normalizedDate != null

            val parsedAmount = remember(totalAmount) {
                totalAmount.trim().toDoubleOrNull() ?: InvoiceOcrProcessor.cleanAndParseDouble(totalAmount.trim())
            }
            val amountIsValid = parsedAmount != null && parsedAmount >= 0.0

            val canSave = invoiceNumber.isNotBlank() && sellerName.isNotBlank() && dateIsValid && amountIsValid && !isSaving

            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let {
                    scope.launch {
                        val res = storageManager.replaceDocument(currentFilePath, it)
                        res.onSuccess { newPath ->
                            currentFilePath = newPath
                            Toast.makeText(context, "Document file replaced!", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, "Error replacing file: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Attached document preview card with replace action
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Attached Document", style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) }) {
                                Text("Replace File 🔄", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        InvoiceDocumentPreview(
                            filePath = currentFilePath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Product Association
                Text("Product Association", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedProductDropdown,
                    onExpandedChange = { expandedProductDropdown = !expandedProductDropdown }
                ) {
                    OutlinedTextField(
                        value = "${selectedProduct.productName} (${selectedProduct.brand})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Associated Product") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedProductDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedProductDropdown,
                        onDismissRequest = { expandedProductDropdown = false }
                    ) {
                        products.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text("${prod.productName} (${prod.brand})") },
                                onClick = {
                                    selectedProduct = prod
                                    expandedProductDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Invoice Fields
                Text("Invoice Metadata", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text("Invoice Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = purchaseDate,
                    onValueChange = { purchaseDate = it },
                    label = { Text("Purchase Date (yyyy-MM-dd) *") },
                    isError = !dateIsValid,
                    supportingText = {
                        if (!dateIsValid) {
                            Text("Enter a valid calendar date (e.g. 2025-09-01)", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sellerName,
                    onValueChange = { sellerName = it },
                    label = { Text("Seller / Merchant Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = totalAmount,
                    onValueChange = { totalAmount = it },
                    label = { Text("Total Amount ($) *") },
                    isError = !amountIsValid,
                    supportingText = {
                        if (!amountIsValid) {
                            Text("Enter a valid amount >= 0", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedTypeDropdown,
                    onExpandedChange = { expandedTypeDropdown = !expandedTypeDropdown }
                ) {
                    OutlinedTextField(
                        value = invoiceType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Invoice Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedTypeDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTypeDropdown,
                        onDismissRequest = { expandedTypeDropdown = false }
                    ) {
                        InvoiceTypes.ALL.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    invoiceType = type
                                    expandedTypeDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    enabled = canSave,
                    onClick = {
                        scope.launch {
                            isSaving = true
                            try {
                                val updatedInvoice = inv.copy(
                                    productId = selectedProduct.productId,
                                    invoiceNumber = invoiceNumber.trim(),
                                    purchaseDate = normalizedDate ?: purchaseDate.trim(),
                                    sellerName = sellerName.trim(),
                                    totalAmount = parsedAmount ?: inv.totalAmount,
                                    invoiceFileUri = currentFilePath,
                                    invoiceType = invoiceType
                                )
                                val duplicate = invoiceRepo.findPotentialDuplicate(updatedInvoice)
                                if (duplicate != null) {
                                    Toast.makeText(context, "Note: Another invoice matches #${duplicate.invoiceNumber}", Toast.LENGTH_SHORT).show()
                                }
                                invoiceRepo.updateInvoice(updatedInvoice)
                                Toast.makeText(context, "Invoice updated successfully!", Toast.LENGTH_SHORT).show()
                                onSaveSuccess()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error updating: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save Changes", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(Modifier.height(30.dp))
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Invoice?") },
                    text = { Text("Invoice #${inv.invoiceNumber} will be permanently removed from your vault. The document file on your device will also be deleted.") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                storageManager.deleteDocument(inv.invoiceFileUri)
                                invoiceRepo.deleteInvoice(inv)
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

