package com.example.invoicevault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.invoicevault.data.InvoiceWithProduct
import com.example.invoicevault.data.ProductRepository
import com.example.invoicevault.ui.InvoiceDocumentPreview
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDashboardScreen(
    invoiceRepository: InvoiceRepository,
    productRepository: ProductRepository,
    onNavigateToUpload: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToInvoiceDetails: (Long) -> Unit
) {
    val totalInvoiceCount by invoiceRepository.totalInvoiceCount.collectAsState(initial = 0)
    val productCount by productRepository.productCount.collectAsState(initial = 0)
    val products by productRepository.products.collectAsState(initial = emptyList())
    val recentInvoicesWithProduct by invoiceRepository.searchInvoiceDetails("", null).collectAsState(initial = emptyList())

    val totalSpent = remember(recentInvoicesWithProduct) {
        recentInvoicesWithProduct.sumOf { it.invoice.totalAmount }
    }
    val activeWarrantiesCount = remember(products) {
        products.count { it.warrantyDuration > 0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Invoice Vault", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Smart Purchases, Receipts & Warranty Archive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Metrics Summary Grid
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardMetricCard(
                        title = "Invoices",
                        value = totalInvoiceCount.toString(),
                        subtitle = "Archived bills",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardMetricCard(
                        title = "Total Spent",
                        value = String.format(Locale.US, "$%.2f", totalSpent),
                        subtitle = "Tracked purchases",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardMetricCard(
                        title = "Products",
                        value = productCount.toString(),
                        subtitle = "Vault catalog",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToProducts
                    )
                    DashboardMetricCard(
                        title = "Warranties",
                        value = activeWarrantiesCount.toString(),
                        subtitle = "Under warranty",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Actions Section
            item {
                Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToUpload,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📷 + Scan Invoice")
                    }
                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🔍 Invoices")
                    }
                    OutlinedButton(
                        onClick = onNavigateToProducts,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📦 Products")
                    }
                }
            }

            // Recent Invoices Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Invoices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (recentInvoicesWithProduct.isNotEmpty()) {
                        TextButton(onClick = onNavigateToHistory) {
                            Text("See all (${recentInvoicesWithProduct.size})")
                        }
                    }
                }
            }

            if (recentInvoicesWithProduct.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No invoices saved yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Scan paper receipts or import PDFs to start your vault",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onNavigateToUpload) {
                                Text("Upload First Invoice")
                            }
                        }
                    }
                }
            } else {
                items(recentInvoicesWithProduct.take(5), key = { it.invoice.invoiceId }) { item ->
                    RecentInvoiceCard(
                        item = item,
                        onClick = { onNavigateToInvoiceDetails(item.invoice.invoiceId) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    subtitle: String,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentInvoiceCard(
    item: InvoiceWithProduct,
    onClick: () -> Unit
) {
    val invoice = item.invoice
    val product = item.product
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
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
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(product.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${invoice.sellerName} · #${invoice.invoiceNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${invoice.purchaseDate} · ${invoice.invoiceType}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = String.format(Locale.US, "$%.2f", invoice.totalAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

