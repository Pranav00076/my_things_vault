package com.example.invoicevault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.invoicevault.data.InvoiceRepository
import com.example.invoicevault.data.InvoiceTypes
import com.example.invoicevault.data.InvoiceWithProduct

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceBrowserDialog(repository: InvoiceRepository, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var invoiceType by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Invoice vault", style = MaterialTheme.typography.headlineSmall)
                        Text("Newest invoices first", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search product, seller or invoice number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = invoiceType ?: "All categories",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Invoice category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("All categories") }, onClick = {
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
                    Text("No invoices match this search.", modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(invoices, key = { it.invoice.invoiceId }) { item -> InvoiceSearchResult(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceSearchResult(item: InvoiceWithProduct) {
    val invoice = item.invoice
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(item.product.productName, style = MaterialTheme.typography.titleMedium)
            Text("${invoice.sellerName} · #${invoice.invoiceNumber}", style = MaterialTheme.typography.bodyMedium)
            Text("${invoice.purchaseDate} · ${invoice.invoiceType} · ${invoice.totalAmount}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
