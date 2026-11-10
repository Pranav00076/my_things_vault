package com.example.invoicevault.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing Invoices in the local Room database.
 */
class InvoiceRepository(private val dao: InvoiceDao) {

    val allInvoices: Flow<List<Invoice>> = dao.observeAll()
    val totalInvoiceCount: Flow<Int> = dao.observeCount()

    fun observeInvoice(invoiceId: Long): Flow<Invoice?> = dao.observeById(invoiceId)

    suspend fun getInvoice(invoiceId: Long): Invoice? = dao.getById(invoiceId)

    fun observeInvoicesForProduct(productId: Long): Flow<List<Invoice>> =
        dao.observeInvoicesForProduct(productId)

    suspend fun getInvoicesForProduct(productId: Long): List<Invoice> =
        dao.getInvoicesForProduct(productId)

    fun observeProductWithInvoices(productId: Long): Flow<ProductWithInvoices?> =
        dao.observeProductWithInvoices(productId)

    fun observeInvoiceWithProduct(invoiceId: Long): Flow<InvoiceWithProduct?> =
        dao.observeInvoiceWithProduct(invoiceId)

    fun observeAllProductsWithInvoices(): Flow<List<ProductWithInvoices>> =
        dao.observeAllProductsWithInvoices()

    fun searchInvoices(query: String): Flow<List<Invoice>> =
        dao.searchInvoices(query)

    suspend fun addInvoice(invoice: Invoice): Long = dao.insert(invoice)

    suspend fun addInvoices(invoices: List<Invoice>): List<Long> = dao.insertAll(invoices)

    suspend fun updateInvoice(invoice: Invoice): Boolean = dao.update(invoice) > 0

    suspend fun deleteInvoice(invoice: Invoice): Boolean = dao.delete(invoice) > 0

    suspend fun deleteInvoice(invoiceId: Long): Boolean = dao.deleteById(invoiceId) > 0

    suspend fun deleteInvoicesForProduct(productId: Long): Boolean =
        dao.deleteByProductId(productId) > 0
}

