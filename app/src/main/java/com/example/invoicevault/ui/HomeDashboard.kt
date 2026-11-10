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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.example.invoicevault.InvoiceVaultApp
import com.example.invoicevault.data.Product
import com.example.invoicevault.data.ProductRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun HomeDashboard(repository: ProductRepository) {
    val context = LocalContext.current
    val app = context.applicationContext as InvoiceVaultApp
    val invoiceRepo = app.invoiceRepository

    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(repository))
    val products by vm.products.collectAsState()
    val count by vm.count.collectAsState()
    val invoiceCount by invoiceRepo.totalInvoiceCount.collectAsState(initial = 0)
    val coroutineScope = rememberCoroutineScope()

    var showAddProduct by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }
    var managingInvoicesProduct by remember { mutableStateOf<Product?>(null) }
    var showUploadInvoiceDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Invoice Vault", style = MaterialTheme.typography.headlineMedium)
        Text("Your purchases, invoices and warranties in one place", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAddProduct = true }) { Text("Add product") }
            OutlinedButton(
                onClick = { showUploadInvoiceDialog = true },
                enabled = products.isNotEmpty()
            ) {
                Text("Upload invoice")
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard("Products", count.toString(), Modifier.weight(1f))
            SummaryCard("Invoices", invoiceCount.toString(), Modifier.weight(1f))
            SummaryCard("Warranties", "Coming soon", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Text("Recent products", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (products.isEmpty()) {
            Text("No products yet. Add your first purchase to start your vault.", modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.productId }) { product ->
                    ProductRow(
                        product = product,
                        onEdit = { editingProduct = product },
                        onDelete = { deletingProduct = product },
                        onManageInvoices = { managingInvoicesProduct = product }
                    )
                }
            }
        }
    }
    if (showAddProduct) {
        ProductDialog(title = "Add product", onDismiss = { showAddProduct = false }, onSave = { product ->
            vm.addProduct(product)
            showAddProduct = false
        })
    }
    editingProduct?.let { product ->
        ProductDialog(title = "Edit product", initial = product, onDismiss = { editingProduct = null }, onSave = {
            vm.updateProduct(it)
            editingProduct = null
        })
    }
    deletingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Delete product?") },
            text = { Text("${product.productName} and all associated invoices will be removed.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteProduct(product); deletingProduct = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingProduct = null }) { Text("Cancel") } },
        )
    }

    managingInvoicesProduct?.let { product ->
        ProductInvoicesDialog(
            product = product,
            onDismiss = { managingInvoicesProduct = null }
        )
    }

    if (showUploadInvoiceDialog) {
        InvoiceUploadDialog(
            availableProducts = products,
            onDismiss = { showUploadInvoiceDialog = false },
            onSaveSuccess = { newInvoice ->
                coroutineScope.launch {
                    invoiceRepo.addInvoice(newInvoice)
                    showUploadInvoiceDialog = false
                }
            }
        )
    }
}

@Composable
private fun ProductDialog(title: String, initial: Product? = null, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.productName.orEmpty()) }
    var brand by remember(initial) { mutableStateOf(initial?.brand.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category.orEmpty()) }
    var date by remember(initial) { mutableStateOf(initial?.purchaseDate.orEmpty()) }
    var price by remember(initial) { mutableStateOf(initial?.purchasePrice?.toString().orEmpty()) }
    var seller by remember(initial) { mutableStateOf(initial?.sellerName.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.productDescription.orEmpty()) }
    var warranty by remember(initial) { mutableStateOf(initial?.warrantyDuration?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Product name") }, singleLine = true)
                OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Purchase date (yyyy-MM-dd)") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Price") }, singleLine = true)
                OutlinedTextField(seller, { seller = it }, label = { Text("Seller") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                OutlinedTextField(warranty, { warranty = it }, label = { Text("Warranty duration (months)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedPrice = price.toDoubleOrNull()
                val parsedWarranty = warranty.toIntOrNull() ?: 0
                val validDate = runCatching { java.time.LocalDate.parse(date) }.isSuccess
                if (name.isNotBlank() && validDate && parsedPrice != null && parsedPrice >= 0 && parsedWarranty >= 0) {
                    onSave(Product(productId = initial?.productId ?: 0, productName = name.trim(), brand = brand.trim(), category = category.trim(), purchaseDate = date, purchasePrice = parsedPrice, sellerName = seller.trim(), productDescription = description.trim(), productImageUri = initial?.productImageUri, warrantyDuration = parsedWarranty, warrantyUnit = com.example.invoicevault.data.WarrantyUnit.MONTHS))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ProductRow(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageInvoices: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(product.productName, style = MaterialTheme.typography.titleMedium)
            Text("${product.brand} · ${product.category}", style = MaterialTheme.typography.bodyMedium)
            Text("Purchased ${product.purchaseDate} · ${product.purchasePrice}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onManageInvoices) { Text("Invoices") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private class DashboardViewModel(repository: ProductRepository) : ViewModel() {
    private val repository = repository
    val products = repository.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val count = repository.productCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    fun addProduct(product: Product) { viewModelScope.launch { repository.addProduct(product) } }
    fun updateProduct(product: Product) { viewModelScope.launch { repository.updateProduct(product) } }
    fun deleteProduct(product: Product) { viewModelScope.launch { repository.deleteProduct(product) } }

    companion object {
        fun factory(repository: ProductRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repository) as T
        }
    }
}
