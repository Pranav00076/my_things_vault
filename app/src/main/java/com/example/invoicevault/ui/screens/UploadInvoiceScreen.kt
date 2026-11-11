package com.example.invoicevault.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.invoicevault.InvoiceVaultApp
import com.example.invoicevault.data.InvoiceTypes
import com.example.invoicevault.data.Product
import com.example.invoicevault.data.ProductRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadInvoiceScreen(
    productRepository: ProductRepository,
    onNavigateBack: () -> Unit,
    onNavigateToCameraScanner: () -> Unit,
    onDocumentReadyForPreview: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as InvoiceVaultApp
    val storageManager = app.invoiceStorageManager
    val scope = rememberCoroutineScope()

    val products by productRepository.products.collectAsState(initial = emptyList())
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedType by remember { mutableStateOf(InvoiceTypes.TAX_INVOICE) }
    var isProcessingFile by remember { mutableStateOf(false) }

    // Launcher for Gallery (images)
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isProcessingFile = true
            scope.launch {
                val res = storageManager.saveDocumentFromUri(it, preferredExtension = "jpg")
                isProcessingFile = false
                res.onSuccess { path ->
                    onDocumentReadyForPreview(path)
                }.onFailure { err ->
                    Toast.makeText(context, "Failed to read image: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Launcher for PDF Document
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isProcessingFile = true
            scope.launch {
                val res = storageManager.saveDocumentFromUri(it, preferredExtension = "pdf")
                isProcessingFile = false
                res.onSuccess { path ->
                    onDocumentReadyForPreview(path)
                }.onFailure { err ->
                    Toast.makeText(context, "Failed to read PDF: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Invoice", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
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
            Text(
                "Choose how you want to upload or scan your invoice",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            if (isProcessingFile) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Importing invoice to vault storage...")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 1. Camera Scanner Card
            UploadOptionCard(
                icon = "📷",
                title = "Camera Scanner",
                description = "Photograph a physical receipt or paper invoice",
                buttonText = "Open Camera Scanner",
                badge = "Recommended",
                badgeColor = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToCameraScanner
            )

            Spacer(Modifier.height(12.dp))

            // 2. Photo Gallery Card
            UploadOptionCard(
                icon = "🖼️",
                title = "Photo Gallery",
                description = "Choose a photo or screenshot from your device (JPEG, PNG)",
                buttonText = "Select Image from Gallery",
                onClick = { galleryPickerLauncher.launch("image/*") }
            )

            Spacer(Modifier.height(12.dp))

            // 3. PDF Document Card
            UploadOptionCard(
                icon = "📄",
                title = "PDF Invoice",
                description = "Import an e-invoice, bill, or receipt document (PDF)",
                buttonText = "Browse Files for PDF",
                badge = "Digital Invoices",
                badgeColor = MaterialTheme.colorScheme.secondary,
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
            )

            Spacer(Modifier.height(24.dp))

            // Category presets
            Text("Invoice Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InvoiceTypes.ALL.take(3).forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UploadOptionCard(
    icon: String,
    title: String,
    description: String,
    buttonText: String,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, style = MaterialTheme.typography.headlineMedium)
                if (badge != null) {
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(buttonText)
            }
        }
    }
}

