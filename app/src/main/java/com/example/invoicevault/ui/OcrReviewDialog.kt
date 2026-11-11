package com.example.invoicevault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.invoicevault.data.Invoice
import com.example.invoicevault.data.InvoiceTypes
import com.example.invoicevault.data.Product
import com.example.invoicevault.data.WarrantyUnit
import com.example.invoicevault.ocr.ExtractedInvoiceData
import com.example.invoicevault.ocr.FieldConfidence
import java.time.LocalDate

/**
 * Review & Verification Screen for OCR results.
 * Allows the user to inspect every detected field, cross-reference against the document preview,
 * correct any inaccurate/unclear values, and resolve missing fields before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReviewDialog(
    filePath: String,
    ocrData: ExtractedInvoiceData,
    initialProduct: Product? = null,
    availableProducts: List<Product> = emptyList(),
    onDismiss: () -> Unit,
    onConfirmSave: (Invoice, Product?, Product?) -> Unit
) {
    val context = LocalContext.current
    val ocrProductName = ocrData.productName.value?.trim()
    val matchedProduct = remember(availableProducts, ocrProductName) {
        ocrProductName?.takeIf { it.isNotBlank() }?.let { detectedName ->
            availableProducts.firstOrNull {
                it.productName.trim().equals(detectedName, ignoreCase = true)
            }
        }
    }

    // Product Selection / Creation state
    var createNewProduct by remember {
        mutableStateOf(initialProduct == null && matchedProduct == null)
    }
    var selectedExistingProduct by remember {
        mutableStateOf(initialProduct ?: matchedProduct ?: availableProducts.firstOrNull())
    }

    // Editable Field states (pre-populated from OCR)
    var productName by remember {
        mutableStateOf(initialProduct?.productName ?: ocrData.productName.value.orEmpty())
    }
    var invoiceNumber by remember {
        mutableStateOf(ocrData.invoiceNumber.value.orEmpty())
    }
    var purchaseDate by remember {
        mutableStateOf(ocrData.purchaseDate.value ?: initialProduct?.purchaseDate ?: LocalDate.now().toString())
    }
    var sellerName by remember {
        mutableStateOf(ocrData.sellerName.value ?: initialProduct?.sellerName.orEmpty())
    }
    var totalAmount by remember {
        mutableStateOf(ocrData.totalAmount.value?.toString() ?: initialProduct?.purchasePrice?.toString().orEmpty())
    }
    var warrantyMonths by remember {
        mutableStateOf(ocrData.warrantyDurationMonths.value?.toString().orEmpty())
    }
    var warrantyVerified by remember(ocrData.warrantyDurationMonths.value) {
        mutableStateOf(ocrData.warrantyDurationMonths.value != null)
    }
    var invoiceType by remember { mutableStateOf(InvoiceTypes.TAX_INVOICE) }

    var expandedTypeDropdown by remember { mutableStateOf(false) }
    var expandedProductDropdown by remember { mutableStateOf(false) }
    var showRawText by remember { mutableStateOf(false) }

    val normalizedPurchaseDate = remember(purchaseDate) {
        if (runCatching { LocalDate.parse(purchaseDate.trim()) }.isSuccess) {
            purchaseDate.trim()
        } else {
            com.example.invoicevault.ocr.InvoiceOcrProcessor.normalizeDate(purchaseDate.trim())
        }
    }
    val dateIsValid = normalizedPurchaseDate != null

    val parsedTotalAmount = remember(totalAmount) {
        val direct = totalAmount.trim().toDoubleOrNull()
        if (direct != null && direct.isFinite() && direct >= 0.0) {
            direct
        } else {
            com.example.invoicevault.ocr.InvoiceOcrProcessor.cleanAndParseDouble(totalAmount.trim())
                ?.takeIf { it.isFinite() && it >= 0.0 }
        }
    }
    val amountIsValid = parsedTotalAmount != null

    val warrantyIsValid = remember(warrantyMonths, warrantyVerified) {
        !warrantyVerified || warrantyMonths.isBlank() || (warrantyMonths.toIntOrNull()?.let { it >= 0 } == true)
    }
    val canSave = remember(selectedExistingProduct, createNewProduct, productName, invoiceNumber, sellerName, dateIsValid, amountIsValid, warrantyIsValid) {
        val hasProduct = (createNewProduct && productName.isNotBlank()) || (!createNewProduct && selectedExistingProduct != null)
        hasProduct && invoiceNumber.isNotBlank() && sellerName.isNotBlank() && dateIsValid && amountIsValid && warrantyIsValid
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Verify OCR Extraction", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Review and adjust extracted data before saving to vault",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Confidence Summary Banner
                OcrSummaryCard(ocrData)

                Spacer(Modifier.height(14.dp))

                // Document Preview Card with Zoom / External View button
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
                            Text("Attached Document", style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { InvoicePreviewHelper.openInExternalViewer(context, filePath) }) {
                                Text("Open Full View 🔍", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        InvoiceDocumentPreview(
                            filePath = filePath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Product Association Section
                Text("1. Product Association", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))

                if (availableProducts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !createNewProduct,
                            onClick = { createNewProduct = false },
                            label = { Text("Link to Existing Product") }
                        )
                        FilterChip(
                            selected = createNewProduct,
                            onClick = { createNewProduct = true },
                            label = { Text("Create New Product") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!createNewProduct && availableProducts.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expandedProductDropdown,
                        onExpandedChange = { expandedProductDropdown = !expandedProductDropdown }
                    ) {
                        OutlinedTextField(
                            value = selectedExistingProduct?.let { "${it.productName} (${it.brand})" } ?: "Select Product",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Associated Product *") },
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
                                    text = { Text("${prod.productName} (${prod.brand})") },
                                    onClick = {
                                        selectedExistingProduct = prod
                                        expandedProductDropdown = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it },
                        label = { Text("New Product Name *") },
                        supportingText = {
                            FieldConfidenceTag(ocrData.productName.confidence, ocrData.productName.explanation)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Invoice Fields Section
                Text("2. Invoice Information", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                // Invoice Number Field
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text("Invoice / Bill Number *") },
                    supportingText = {
                        FieldConfidenceTag(ocrData.invoiceNumber.confidence, ocrData.invoiceNumber.explanation)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Purchase Date Field
                OutlinedTextField(
                    value = purchaseDate,
                    onValueChange = { purchaseDate = it },
                    label = { Text("Purchase Date (yyyy-MM-dd) *") },
                    isError = !dateIsValid,
                    supportingText = {
                        if (!dateIsValid) {
                            Text("Invalid date format. Expected yyyy-MM-dd (e.g. 2024-06-15)", color = MaterialTheme.colorScheme.error)
                        } else {
                            FieldConfidenceTag(ocrData.purchaseDate.confidence, ocrData.purchaseDate.explanation)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Seller Name Field
                OutlinedTextField(
                    value = sellerName,
                    onValueChange = { sellerName = it },
                    label = { Text("Seller / Merchant Name *") },
                    supportingText = {
                        FieldConfidenceTag(ocrData.sellerName.confidence, ocrData.sellerName.explanation)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Total Amount Field
                OutlinedTextField(
                    value = totalAmount,
                    onValueChange = { totalAmount = it },
                    label = { Text("Total Amount *") },
                    isError = !amountIsValid,
                    supportingText = {
                        if (!amountIsValid) {
                            Text("Please enter a valid amount", color = MaterialTheme.colorScheme.error)
                        } else {
                            FieldConfidenceTag(ocrData.totalAmount.confidence, ocrData.totalAmount.explanation)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Warranty Duration Field
                OutlinedTextField(
                    value = warrantyMonths,
                    onValueChange = {
                        warrantyMonths = it
                        if (it.isNotBlank() && it.toIntOrNull() != null) {
                            warrantyVerified = true
                        }
                    },
                    label = { Text("Warranty Duration (months)") },
                    isError = warrantyVerified && !warrantyIsValid,
                    supportingText = {
                        Column {
                            FieldConfidenceTag(
                                ocrData.warrantyDurationMonths.confidence,
                                ocrData.warrantyDescription ?: ocrData.warrantyDurationMonths.explanation
                            )
                            if (warrantyVerified && !warrantyIsValid) {
                                Text("Enter a whole number of months", color = MaterialTheme.colorScheme.error)
                            }
                            if (!ocrData.warrantyDescription.isNullOrBlank()) {
                                Text(ocrData.warrantyDescription, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = warrantyVerified, onCheckedChange = { warrantyVerified = it })
                    Text("I verified the warranty information against the invoice")
                }

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
                        label = { Text("Invoice Document Type") },
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

                Spacer(Modifier.height(14.dp))

                // Raw OCR Text Inspector (Expandable)
                TextButton(onClick = { showRawText = !showRawText }) {
                    Text(if (showRawText) "▲ Hide Raw Extracted Text" else "▼ View Raw Extracted OCR Text")
                }

                AnimatedVisibility(visible = showRawText) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Recognized Text Blocks:", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = ocrData.rawExtractedText.ifBlank { "No text blocks detected." },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            val parsedAmount = parsedTotalAmount ?: 0.0
                            val finalPurchaseDate = normalizedPurchaseDate ?: purchaseDate.trim()
                            val parsedWarranty = if (warrantyVerified) warrantyMonths.toIntOrNull() ?: 0 else 0
                            val verifiedWarrantyDescription = ocrData.warrantyDescription
                                ?.takeIf { warrantyVerified && it.isNotBlank() }

                            val newProduct: Product? = if (createNewProduct) {
                                Product(
                                    productId = 0L,
                                    productName = productName.trim(),
                                    brand = sellerName.trim(),
                                    category = "General",
                                    purchaseDate = finalPurchaseDate,
                                    purchasePrice = parsedAmount,
                                    sellerName = sellerName.trim(),
                                    productDescription = buildString {
                                        append("Created from invoice ${invoiceNumber.trim()}")
                                        verifiedWarrantyDescription?.let { append("\nWarranty details: $it") }
                                    },
                                    warrantyDuration = parsedWarranty,
                                    warrantyUnit = WarrantyUnit.MONTHS
                                )
                            } else null

                            val updatedExistingProduct = if (warrantyVerified && !createNewProduct) {
                                selectedExistingProduct?.let { product ->
                                    product.copy(
                                        warrantyDuration = parsedWarranty,
                                        warrantyUnit = WarrantyUnit.MONTHS,
                                        productDescription = buildString {
                                            append(product.productDescription)
                                            verifiedWarrantyDescription?.let { details ->
                                                if (!contains(details)) append("\nWarranty details: $details")
                                            }
                                        }.trim()
                                    )
                                }
                            } else null

                            val invoice = Invoice(
                                invoiceId = 0L,
                                productId = selectedExistingProduct?.productId ?: 0L,
                                invoiceNumber = invoiceNumber.trim(),
                                purchaseDate = finalPurchaseDate,
                                sellerName = sellerName.trim(),
                                totalAmount = parsedAmount,
                                invoiceFileUri = filePath,
                                invoiceType = invoiceType,
                                createdAt = System.currentTimeMillis()
                            )

                            onConfirmSave(invoice, newProduct, updatedExistingProduct)
                        }
                    ) {
                        Text("Confirm & Save Invoice")
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrSummaryCard(ocrData: ExtractedInvoiceData) {
    val detectedCount = ocrData.extractedFieldCount
    val totalCount = 6
    val cardColor = if (detectedCount >= 4) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "✨ OCR Scan Complete: $detectedCount / $totalCount fields identified",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (ocrData.hasAllCrucialFields) {
                    "All essential fields were detected. Please review each field below for accuracy before saving."
                } else {
                    "Some fields were missing or unclear. Please fill in or correct the highlighted fields."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FieldConfidenceTag(confidence: FieldConfidence, explanation: String) {
    val (color, text) = when (confidence) {
        FieldConfidence.HIGH -> Pair(Color(0xFF2E7D32), "🟢 Verified by OCR")
        FieldConfidence.MEDIUM -> Pair(Color(0xFFF57F17), "🟡 Inferred - please verify")
        FieldConfidence.LOW -> Pair(Color(0xFFE65100), "🟠 Low confidence candidate")
        FieldConfidence.MISSING -> Pair(MaterialTheme.colorScheme.error, "🔴 Not detected - manual entry required")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelSmall)
        if (explanation.isNotBlank() && confidence != FieldConfidence.MISSING) {
            Text(
                text = " ($explanation)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

