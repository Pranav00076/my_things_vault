package com.example.invoicevault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.invoicevault.data.InvoiceRepository
import com.example.invoicevault.data.InvoiceTypes
import com.example.invoicevault.data.InvoiceWithProduct
import com.example.invoicevault.ui.InvoiceDocumentPreview
import java.util.Locale

enum class InvoiceSortOrder {
    DATE_NEWEST,
    DATE_OLDEST,
    AMOUNT_HIGHEST,
    AMOUNT_LOWEST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceHistoryScreen(
    invoiceRepository: InvoiceRepository,
    onNavigateBack: () -> Unit,
    onNavigateToInvoiceDetails: (Long) -> Unit,
    onNavigateToUpload: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(InvoiceSortOrder.DATE_NEWEST) }

    val rawInvoicesFlow = remember(searchQuery, selectedCategory) {
        invoiceRepository.searchInvoiceDetails(searchQuery.trim(), selectedCategory)
    }
    val rawInvoices by rawInvoicesFlow.collectAsState(initial = emptyList())

    val sortedInvoices = remember(rawInvoices, sortOrder) {
        when (sortOrder) {
            InvoiceSortOrder.DATE_NEWEST -> rawInvoices.sortedByDescending { it.invoice.purchaseDate }
            InvoiceSortOrder.DATE_OLDEST -> rawInvoices.sortedBy { it.invoice.purchaseDate }
            InvoiceSortOrder.AMOUNT_HIGHEST -> rawInvoices.sortedByDescending { it.invoice.totalAmount }
            InvoiceSortOrder.AMOUNT_LOWEST -> rawInvoices.sortedBy { it.invoice.totalAmount }
        }
    }

    val totalAmount = remember(sortedInvoices) {
        sortedInvoices.sumOf { it.invoice.totalAmount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                },
                actions = {
                    IconButton(onClick = onNavigateToUpload) {
                        Text("📷 +", style = MaterialTheme.typography.titleMedium)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search product, seller, or invoice #") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        TextButton(onClick = { searchQuery = "" }) { Text("Clear") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            // Category Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") }
                )
                InvoiceTypes.ALL.forEach { type ->
                    FilterChip(
                        selected = selectedCategory == type,
                        onClick = { selectedCategory = if (selectedCategory == type) null else type },
                        label = { Text(type) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Sort & Metrics Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${sortedInvoices.size} invoices • Total: ${String.format(Locale.US, "$%.2f", totalAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Sort Toggle Button
                TextButton(onClick = {
                    sortOrder = when (sortOrder) {
                        InvoiceSortOrder.DATE_NEWEST -> InvoiceSortOrder.AMOUNT_HIGHEST
                        InvoiceSortOrder.AMOUNT_HIGHEST -> InvoiceSortOrder.DATE_OLDEST
                        InvoiceSortOrder.DATE_OLDEST -> InvoiceSortOrder.AMOUNT_LOWEST
                        InvoiceSortOrder.AMOUNT_LOWEST -> InvoiceSortOrder.DATE_NEWEST
                    }
                }) {
                    Text(
                        text = when (sortOrder) {
                            InvoiceSortOrder.DATE_NEWEST -> "Sort: Newest ▾"
                            InvoiceSortOrder.DATE_OLDEST -> "Sort: Oldest ▾"
                            InvoiceSortOrder.AMOUNT_HIGHEST -> "Sort: Highest $ ▾"
                            InvoiceSortOrder.AMOUNT_LOWEST -> "Sort: Lowest $ ▾"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (sortedInvoices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No matching invoices found", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Try searching for another keyword or clear filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        if (searchQuery.isNotBlank() || selectedCategory != null) {
                            OutlinedButton(onClick = {
                                searchQuery = ""
                                selectedCategory = null
                            }) {
                                Text("Reset Filters")
                            }
                        } else {
                            Button(onClick = onNavigateToUpload) {
                                Text("Upload New Invoice")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedInvoices, key = { it.invoice.invoiceId }) { item ->
                        InvoiceHistoryItemCard(
                            item = item,
                            onClick = { onNavigateToInvoiceDetails(item.invoice.invoiceId) }
                        )
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceHistoryItemCard(
    item: InvoiceWithProduct,
    onClick: () -> Unit
) {
    val invoice = item.invoice
    val product = item.product

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Text(
                    text = product.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${invoice.sellerName} • #${invoice.invoiceNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${invoice.purchaseDate} • ${invoice.invoiceType}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "$%.2f", invoice.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

