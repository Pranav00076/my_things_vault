package com.example.invoicevault.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.invoicevault.InvoiceVaultApp
import com.example.invoicevault.data.Invoice
import com.example.invoicevault.data.InvoiceTypes
import com.example.invoicevault.data.Product
import com.example.invoicevault.data.WarrantyUnit
import com.example.invoicevault.ocr.ExtractedInvoiceData
import com.example.invoicevault.ocr.FieldConfidence
import com.example.invoicevault.ocr.InvoiceOcrProcessor
import com.example.invoicevault.ui.InvoiceDocumentPreview
import com.example.invoicevault.ui.InvoicePreviewHelper
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReviewScreen(
    filePath: String,
    onNavigateBack: () -> Unit,
    onSaveSuccess: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as InvoiceVaultApp
    val invoiceRepo = app.invoiceRepository
    val productRepo = app.productRepository
    val scope = rememberCoroutineScope()

    val availableProducts by productRepo.products.collectAsState(initial = emptyList())

    var isLoadingOcr by remember { mutableStateOf(true) }
    var ocrData by remember { mutableStateOf<ExtractedInvoiceData?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Run OCR extraction on start
    LaunchedEffect(filePath) {
        isLoadingOcr = true
        try {
            val result = InvoiceOcrProcessor.processInvoiceDocument(context, filePath)
            ocrData = result
        } catch (e: Exception) {
            Toast.makeText(context, "OCR analysis error: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            isLoadingOcr = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify OCR Extraction", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        if (isLoadingOcr) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Analyzing invoice with on-device ML Kit...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Reading text blocks, amounts, and dates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (ocrData != null) {
            OcrReviewForm(
                modifier = Modifier.padding(padding),
                filePath = filePath,
                ocrData = ocrData!!,
                availableProducts = availableProducts,
                onSave = { invoice, newProduct, updatedExistingProduct ->
                    scope.launch {
                        isSaving = true
                        try {
                            val duplicate = invoiceRepo.findPotentialDuplicate(invoice)
                            if (duplicate != null) {
                                Toast.makeText(context, "Duplicate invoice detected: #${duplicate.invoiceNumber} from ${duplicate.sellerName}", Toast.LENGTH_LONG).show()
                            }
                            val finalProductId = if (newProduct != null) {
                                productRepo.addProduct(newProduct)
                            } else {
                                invoice.productId
                            }
                            updatedExistingProduct?.let { productRepo.updateProduct(it) }
                            val savedInvoiceId = invoiceRepo.addInvoice(invoice.copy(productId = finalProductId))
                            Toast.makeText(context, "Invoice saved to vault!", Toast.LENGTH_SHORT).show()
                            onSaveSuccess(savedInvoiceId)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isSaving = false
                        }
                    }
                }
            )
        } else {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to process document.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onNavigateBack) { Text("Go Back") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrReviewForm(
    modifier: Modifier = Modifier,
    filePath: String,
    ocrData: ExtractedInvoiceData,
    availableProducts: List<Product>,
    onSave: (Invoice, Product?, Product?) -> Unit
) {
    val context = LocalContext.current
    val detectedProductName = ocrData.productName.value?.trim()
    val matchedProduct = remember(availableProducts, detectedProductName) {
        detectedProductName?.takeIf { it.isNotBlank() }?.let { name ->
            availableProducts.firstOrNull { it.productName.trim().equals(name, ignoreCase = true) }
        }
    }

    var createNewProduct by remember {
        mutableStateOf(matchedProduct == null && (availableProducts.isEmpty() || detectedProductName != null))
    }
    var selectedExistingProduct by remember {
        mutableStateOf(matchedProduct ?: availableProducts.firstOrNull())
    }

    var productName by remember { mutableStateOf(ocrData.productName.value.orEmpty()) }
    var invoiceNumber by remember { mutableStateOf(ocrData.invoiceNumber.value.orEmpty()) }
    var purchaseDate by remember { mutableStateOf(ocrData.purchaseDate.value ?: LocalDate.now().toString()) }
    var sellerName by remember { mutableStateOf(ocrData.sellerName.value.orEmpty()) }
    var totalAmount by remember { mutableStateOf(ocrData.totalAmount.value?.toString().orEmpty()) }
    var warrantyMonths by remember { mutableStateOf(ocrData.warrantyDurationMonths.value?.toString().orEmpty()) }
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
            InvoiceOcrProcessor.normalizeDate(purchaseDate.trim())
        }
    }
    val dateIsValid = normalizedPurchaseDate != null

    val parsedTotalAmount = remember(totalAmount) {
        val direct = totalAmount.trim().toDoubleOrNull()
        if (direct != null && direct.isFinite() && direct >= 0.0) {
            direct
        } else {
            InvoiceOcrProcessor.cleanAndParseDouble(totalAmount.trim())
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Summary banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "✨ OCR Detection: ${ocrData.extractedFieldCount} / 6 fields detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text("Verify each extracted field against the document preview below", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Document preview box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Scanned Receipt", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { InvoicePreviewHelper.openInExternalViewer(context, filePath) }) {
                        Text("Open Full View 🔍", style = MaterialTheme.typography.labelSmall)
                    }
                }
                InvoiceDocumentPreview(
                    filePath = filePath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Product Link Section
        Text("1. Product Association", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        if (availableProducts.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !createNewProduct,
                    onClick = { createNewProduct = false },
                    label = { Text("Link Existing") }
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
                    label = { Text("Linked Product *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedProductDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
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
                label = { Text("Product Name *") },
                supportingText = {
                    FieldBadge(ocrData.productName.confidence, ocrData.productName.explanation)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))

        // Invoice Fields
        Text("2. Invoice Data", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = invoiceNumber,
            onValueChange = { invoiceNumber = it },
            label = { Text("Invoice / Receipt Number *") },
            supportingText = { FieldBadge(ocrData.invoiceNumber.confidence, ocrData.invoiceNumber.explanation) },
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
                    Text("Invalid calendar date. Expected yyyy-MM-dd (e.g. 2025-09-01)", color = MaterialTheme.colorScheme.error)
                } else {
                    FieldBadge(ocrData.purchaseDate.confidence, ocrData.purchaseDate.explanation)
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
            supportingText = { FieldBadge(ocrData.sellerName.confidence, ocrData.sellerName.explanation) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = totalAmount,
            onValueChange = { totalAmount = it },
            label = { Text("Total Amount *") },
            isError = !amountIsValid,
            supportingText = {
                if (!amountIsValid) {
                    Text("Please enter a valid amount", color = MaterialTheme.colorScheme.error)
                } else {
                    FieldBadge(ocrData.totalAmount.confidence, ocrData.totalAmount.explanation)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = warrantyMonths,
            onValueChange = {
                warrantyMonths = it
                if (it.isNotBlank() && it.toIntOrNull() != null) {
                    warrantyVerified = true
                }
            },
            label = { Text("Warranty Duration (months)") },
            supportingText = {
                Column {
                    FieldBadge(ocrData.warrantyDurationMonths.confidence, ocrData.warrantyDescription ?: ocrData.warrantyDurationMonths.explanation)
                    if (ocrData.warrantyDescription != null) {
                        Text(ocrData.warrantyDescription, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = warrantyVerified, onCheckedChange = { warrantyVerified = it })
            Text("Verified warranty information against invoice", style = MaterialTheme.typography.bodySmall)
        }

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

        Spacer(Modifier.height(14.dp))

        TextButton(onClick = { showRawText = !showRawText }) {
            Text(if (showRawText) "▲ Hide OCR Text" else "▼ View Raw OCR Text")
        }

        AnimatedVisibility(visible = showRawText) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = ocrData.rawExtractedText.ifBlank { "No text blocks detected." },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

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

                onSave(invoice, newProduct, updatedExistingProduct)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Confirm & Save to Vault", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun FieldBadge(confidence: FieldConfidence, explanation: String) {
    val (color, text) = when (confidence) {
        FieldConfidence.HIGH -> Pair(Color(0xFF16A34A), "🟢 High confidence")
        FieldConfidence.MEDIUM -> Pair(Color(0xFFD97706), "🟡 Inferred")
        FieldConfidence.LOW -> Pair(Color(0xFFEA580C), "🟠 Low confidence")
        FieldConfidence.MISSING -> Pair(MaterialTheme.colorScheme.error, "🔴 Not detected")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall)
        if (explanation.isNotBlank() && confidence != FieldConfidence.MISSING) {
            Text(" • $explanation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

