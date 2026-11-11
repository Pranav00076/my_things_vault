package com.example.invoicevault.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.invoicevault.InvoiceVaultApp
import com.example.invoicevault.data.Invoice
import com.example.invoicevault.data.InvoiceTypes
import com.example.invoicevault.data.Product
import com.example.invoicevault.ocr.ExtractedInvoiceData
import com.example.invoicevault.ocr.InvoiceOcrProcessor
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * Dialog for uploading a new invoice or replacing an existing invoice document.
 * Supports:
 * - Camera capture
 * - Gallery / File picker (Images & PDFs)
 * - In-app document preview
 * - Product association
 * - Local internal storage
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceUploadDialog(
    initialProduct: Product? = null,
    availableProducts: List<Product> = emptyList(),
    existingInvoice: Invoice? = null,
    onDismiss: () -> Unit,
    onSaveSuccess: (Invoice) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as InvoiceVaultApp
    val storageManager = app.invoiceStorageManager
    val scope = rememberCoroutineScope()

    var selectedProduct by remember {
        mutableStateOf(initialProduct ?: availableProducts.firstOrNull { it.productId == existingInvoice?.productId })
    }
    var invoiceNumber by remember { mutableStateOf(existingInvoice?.invoiceNumber.orEmpty()) }
    var purchaseDate by remember {
        mutableStateOf(existingInvoice?.purchaseDate ?: initialProduct?.purchaseDate ?: java.time.LocalDate.now().toString())
    }
    var sellerName by remember {
        mutableStateOf(existingInvoice?.sellerName ?: initialProduct?.sellerName.orEmpty())
    }
    var totalAmount by remember {
        mutableStateOf(existingInvoice?.totalAmount?.toString() ?: initialProduct?.purchasePrice?.toString().orEmpty())
    }
    var invoiceType by remember {
        mutableStateOf(existingInvoice?.invoiceType ?: InvoiceTypes.TAX_INVOICE)
    }

    // Selected file state (can be an existing local file or newly picked Uri)
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var currentPreviewPath by remember { mutableStateOf(existingInvoice?.invoiceFileUri) }
    var isSaving by remember { mutableStateOf(false) }
    var isScanningOcr by remember { mutableStateOf(false) }
    var pendingOcrReview by remember { mutableStateOf<ExtractedInvoiceData?>(null) }
    var potentialDuplicate by remember { mutableStateOf<Invoice?>(null) }
    var pendingDuplicateAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }

    val purchaseDateIsValid = remember(purchaseDate) {
        runCatching { LocalDate.parse(purchaseDate.trim()) }.isSuccess
    }
    val amountIsValid = remember(totalAmount) {
        totalAmount.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true
    }

    fun saveAfterDuplicateCheck(invoice: Invoice, proceed: suspend () -> Unit) {
        if (isSaving || potentialDuplicate != null) return
        scope.launch {
            isSaving = true
            try {
                val duplicate = app.invoiceRepository.findPotentialDuplicate(invoice)
                if (duplicate != null) {
                    potentialDuplicate = duplicate
                    pendingDuplicateAction = proceed
                } else {
                    proceed()
                }
            } catch (error: Exception) {
                Toast.makeText(context, "Unable to check for duplicate: ${error.message}", Toast.LENGTH_LONG).show()
            } finally {
                isSaving = false
            }
        }
    }

    // Temporary camera capture state
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher for file picker (Images and PDFs)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            // Save to temp or directly copy for instant preview
            scope.launch {
                val res = storageManager.saveDocumentFromUri(it)
                res.onSuccess { path ->
                    currentPreviewPath = path
                }.onFailure { err ->
                    Toast.makeText(context, "Failed to read file: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Launcher for camera capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            val uri = tempCameraUri!!
            selectedFileUri = uri
            scope.launch {
                val res = storageManager.saveDocumentFromUri(uri, preferredExtension = "jpg")
                res.onSuccess { path ->
                    currentPreviewPath = path
                }.onFailure { err ->
                    Toast.makeText(context, "Camera save error: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var expandedTypeDropdown by remember { mutableStateOf(false) }
    var expandedProductDropdown by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (existingInvoice == null) "Upload Invoice" else "Edit / Replace Invoice",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Attach receipt, tax invoice or warranty bill (Image or PDF)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // Associated Product Selector
                if (initialProduct == null) {
                    Text("Associated Product", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedProductDropdown,
                        onExpandedChange = { expandedProductDropdown = !expandedProductDropdown }
                    ) {
                        OutlinedTextField(
                            value = selectedProduct?.let { "${it.productName} (${it.brand})" } ?: "Select Product",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProductDropdown) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProductDropdown,
                            onDismissRequest = { expandedProductDropdown = false }
                        ) {
                            availableProducts.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text("${prod.productName} · ${prod.brand}") },
                                    onClick = {
                                        selectedProduct = prod
                                        if (sellerName.isBlank()) sellerName = prod.sellerName
                                        if (totalAmount.isBlank()) totalAmount = prod.purchasePrice.toString()
                                        if (purchaseDate.isBlank()) purchaseDate = prod.purchaseDate
                                        expandedProductDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Linked Product", style = MaterialTheme.typography.labelSmall)
                                Text("${initialProduct.productName} (${initialProduct.brand})", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // File Upload Section
                Text("Invoice Document", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val (file, uri) = storageManager.createTempCameraUri()
                            tempCameraFile = file
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📷 Camera")
                    }

                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📁 Gallery / PDF")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Document Preview
                currentPreviewPath?.let { path ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (storageManager.isPdf(path)) "PDF Invoice" else "Image Invoice",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = storageManager.getReadableFileSize(path),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            InvoiceDocumentPreview(
                                filePath = path,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        isScanningOcr = true
                                        try {
                                            val ocrResult = InvoiceOcrProcessor.processInvoiceDocument(context, path)
                                            // Pre-fill fields in the current dialog
                                            ocrResult.invoiceNumber.value?.let { invoiceNumber = it }
                                            ocrResult.purchaseDate.value?.let { purchaseDate = it }
                                            ocrResult.sellerName.value?.let { sellerName = it }
                                            ocrResult.totalAmount.value?.let { totalAmount = it.toString() }
                                            // Open review dialog
                                            pendingOcrReview = ocrResult
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "OCR Extraction error: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isScanningOcr = false
                                        }
                                    }
                                },
                                enabled = !isScanningOcr,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isScanningOcr) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Scanning Document with ML Kit OCR...")
                                } else {
                                    Text("✨ Auto-Extract with OCR")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { InvoicePreviewHelper.openInExternalViewer(context, path) }) {
                                    Text("Open Full View")
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        storageManager.deleteDocument(path)
                                        currentPreviewPath = null
                                        selectedFileUri = null
                                    }
                                }) {
                                    Text("Remove File", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Invoice Metadata Fields
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
                    isError = purchaseDate.isNotBlank() && !purchaseDateIsValid,
                    supportingText = {
                        if (purchaseDate.isNotBlank() && !purchaseDateIsValid) {
                            Text("Enter a real date in yyyy-MM-dd format", color = MaterialTheme.colorScheme.error)
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
                    label = { Text("Total Amount *") },
                    isError = totalAmount.isNotBlank() && !amountIsValid,
                    supportingText = {
                        if (totalAmount.isNotBlank() && !amountIsValid) {
                            Text("Enter an amount greater than or equal to zero", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // Invoice Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedTypeDropdown,
                    onExpandedChange = { expandedTypeDropdown = !expandedTypeDropdown }
                ) {
                    OutlinedTextField(
                        value = invoiceType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Invoice Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
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

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !isSaving && selectedProduct != null &&
                                invoiceNumber.isNotBlank() &&
                                sellerName.isNotBlank() &&
                                purchaseDateIsValid && amountIsValid &&
                                currentPreviewPath != null,
                        onClick = {
                            val prod = selectedProduct ?: return@Button
                            val parsedAmount = totalAmount.toDoubleOrNull() ?: return@Button
                            val filePath = currentPreviewPath ?: return@Button

                            val invoice = Invoice(
                                invoiceId = existingInvoice?.invoiceId ?: 0L,
                                productId = prod.productId,
                                invoiceNumber = invoiceNumber.trim(),
                                purchaseDate = purchaseDate.trim(),
                                sellerName = sellerName.trim(),
                                totalAmount = parsedAmount,
                                invoiceFileUri = filePath,
                                invoiceType = invoiceType,
                                createdAt = existingInvoice?.createdAt ?: System.currentTimeMillis()
                            )
                            saveAfterDuplicateCheck(invoice) { onSaveSuccess(invoice) }
                        }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(if (existingInvoice == null) "Save Invoice" else "Update Invoice")
                        }
                    }
                }
            }
        }
    }

    pendingOcrReview?.let { ocrData ->
        currentPreviewPath?.let { path ->
            OcrReviewDialog(
                filePath = path,
                ocrData = ocrData,
                initialProduct = selectedProduct ?: initialProduct,
                availableProducts = availableProducts,
                onDismiss = { pendingOcrReview = null },
                onConfirmSave = { inv, newProd, updatedExistingProduct ->
                    saveAfterDuplicateCheck(inv) {
                        val finalProductId = if (newProd != null) {
                            app.productRepository.addProduct(newProd)
                        } else {
                            inv.productId
                        }
                        updatedExistingProduct?.let { app.productRepository.updateProduct(it) }
                        val finalInvoice = inv.copy(productId = finalProductId)
                        pendingOcrReview = null
                        onSaveSuccess(finalInvoice)
                    }
                }
            )
        }
    }

    potentialDuplicate?.let { duplicate ->
        AlertDialog(
            onDismissRequest = {
                potentialDuplicate = null
                pendingDuplicateAction = null
            },
            title = { Text("Possible duplicate invoice") },
            text = {
                Text("Invoice ${duplicate.invoiceNumber} from ${duplicate.sellerName} on ${duplicate.purchaseDate} has matching details. Save this invoice anyway?")
            },
            confirmButton = {
                TextButton(onClick = {
                    val action = pendingDuplicateAction
                    potentialDuplicate = null
                    pendingDuplicateAction = null
                    scope.launch {
                        try {
                            action?.invoke()
                        } catch (error: Exception) {
                            Toast.makeText(context, "Unable to save invoice: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Save anyway") }
            },
            dismissButton = {
                TextButton(onClick = {
                    potentialDuplicate = null
                    pendingDuplicateAction = null
                }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Dialog/Sheet to view, preview, replace, or delete all invoices associated with a Product.
 */
@Composable
fun ProductInvoicesDialog(
    product: Product,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as InvoiceVaultApp
    val invoiceRepo = app.invoiceRepository
    val storageManager = app.invoiceStorageManager
    val scope = rememberCoroutineScope()

    val invoices by invoiceRepo.observeInvoicesForProduct(product.productId).collectAsState(initial = emptyList())

    var showUploadDialog by remember { mutableStateOf(false) }
    var editingInvoice by remember { mutableStateOf<Invoice?>(null) }
    var previewInvoice by remember { mutableStateOf<Invoice?>(null) }
    var deletingInvoice by remember { mutableStateOf<Invoice?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(product.productName, style = MaterialTheme.typography.titleLarge)
                        Text("Invoices & Receipts (${invoices.size})", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { showUploadDialog = true }) {
                        Text("+ Add Invoice")
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (invoices.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No invoices uploaded yet.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("Upload camera photos or PDFs of receipts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(invoices, key = { it.invoiceId }) { inv ->
                            InvoiceItemCard(
                                invoice = inv,
                                onPreview = { previewInvoice = inv },
                                onEdit = { editingInvoice = inv },
                                onDelete = { deletingInvoice = inv }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    if (showUploadDialog) {
        InvoiceUploadDialog(
            initialProduct = product,
            availableProducts = listOf(product),
            onDismiss = { showUploadDialog = false },
            onSaveSuccess = { newInvoice ->
                scope.launch {
                    invoiceRepo.addInvoice(newInvoice)
                    showUploadDialog = false
                    Toast.makeText(context, "Invoice uploaded successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    editingInvoice?.let { inv ->
        InvoiceUploadDialog(
            initialProduct = product,
            availableProducts = listOf(product),
            existingInvoice = inv,
            onDismiss = { editingInvoice = null },
            onSaveSuccess = { updated ->
                scope.launch {
                    invoiceRepo.updateInvoice(updated)
                    editingInvoice = null
                    Toast.makeText(context, "Invoice updated!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    deletingInvoice?.let { inv ->
        AlertDialog(
            onDismissRequest = { deletingInvoice = null },
            title = { Text("Delete Invoice?") },
            text = { Text("Are you sure you want to delete invoice ${inv.invoiceNumber}? The document file will also be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        storageManager.deleteDocument(inv.invoiceFileUri)
                        invoiceRepo.deleteInvoice(inv)
                        deletingInvoice = null
                        Toast.makeText(context, "Invoice deleted", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingInvoice = null }) { Text("Cancel") }
            }
        )
    }

    previewInvoice?.let { inv ->
        AlertDialog(
            onDismissRequest = { previewInvoice = null },
            title = { Text("Invoice ${inv.invoiceNumber}") },
            text = {
                Column {
                    Text("Seller: ${inv.sellerName} · ${inv.purchaseDate}")
                    Text("Amount: \$${inv.totalAmount} (${inv.invoiceType})", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    InvoiceDocumentPreview(
                        filePath = inv.invoiceFileUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { InvoicePreviewHelper.openInExternalViewer(context, inv.invoiceFileUri) }) {
                    Text("Open in App")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewInvoice = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun InvoiceItemCard(
    invoice: Invoice,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            InvoiceDocumentPreview(
                filePath = invoice.invoiceFileUri,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "No. ${invoice.invoiceNumber}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${invoice.sellerName} · ${invoice.purchaseDate}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "$${invoice.totalAmount} · ${invoice.invoiceType}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onEdit) { Text("Replace / Edit", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = onDelete) { Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

