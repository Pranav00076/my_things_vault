package com.example.invoicevault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.invoicevault.data.InvoiceRepository
import com.example.invoicevault.data.InvoiceTypes
import com.example.invoicevault.data.InvoiceWithProduct

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceBrowserDialog(repository: InvoiceRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var invoiceType by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<InvoiceWithProduct?>(null) }

    val resultsFlow = remember(query, invoiceType) {
        repository.searchInvoiceDetails(query.trim(), invoiceType)
    }
    val invoices by resultsFlow.collectAsState(initial = emptyList())

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxSize(0.92f).clip(RoundedCornerShape(18.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Invoice Vault", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = if (invoices.isEmpty()) "Organized by date & category" else "Showing ${invoices.size} invoice${if (invoices.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search product, category, seller or invoice number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = invoiceType ?: "All Categories",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filter by category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("All Categories") }, onClick = {
                            invoiceType = null
                            expanded = false
                        })
                        InvoiceTypes.ALL.forEach { type ->
                            DropdownMenuItem(text = { Text(type) }, onClick = {
                                invoiceType = type
                                expanded = false
                            })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (invoices.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No invoices match this search.", style = MaterialTheme.typography.bodyMedium)
                            Text("Try searching for another product name, category, or seller.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(invoices, key = { it.invoice.invoiceId }) { item ->
                            InvoiceSearchResult(item = item, onClick = { previewItem = item })
                        }
                    }
                }
            }
        }
    }

    previewItem?.let { item ->
        val inv = item.invoice
        val prod = item.product
        AlertDialog(
            onDismissRequest = { previewItem = null },
            title = { Text("Invoice #${inv.invoiceNumber}") },
            text = {
                Column {
                    Text("${prod.productName} (${prod.category})", style = MaterialTheme.typography.titleMedium)
                    Text("Seller: ${inv.sellerName} · Date: ${inv.purchaseDate}", style = MaterialTheme.typography.bodyMedium)
                    Text("Total: $${inv.totalAmount} · Type: ${inv.invoiceType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    InvoiceDocumentPreview(
                        filePath = inv.invoiceFileUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
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
                TextButton(onClick = { previewItem = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun InvoiceSearchResult(item: InvoiceWithProduct, onClick: () -> Unit) {
    val invoice = item.invoice
    val product = item.product
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InvoiceDocumentPreview(
                filePath = invoice.invoiceFileUri,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(product.productName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${invoice.sellerName} · #${invoice.invoiceNumber}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${invoice.purchaseDate} · ${invoice.invoiceType} · $${invoice.totalAmount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = product.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
